/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.pregenerator.methods;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.core.IrisPaperLikeBackendMode;
import art.arcane.iris.core.IrisRuntimeSchedulerMode;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.pregenerator.PregenDiagnostics;
import art.arcane.iris.core.pregenerator.PregenListener;
import art.arcane.iris.core.pregenerator.PregenMantleBackpressure;
import art.arcane.iris.core.pregenerator.PregeneratorMethod;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.math.M;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.common.scheduling.J;
import io.papermc.lib.PaperLib;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AsyncPregenMethod implements PregeneratorMethod {
    // THREAD_COUNT records the pool's original size while boosted; BOOST_HOLDERS counts live
    // jobs holding the boost. Pairing them per instance stops a dying job (whose close can
    // land minutes after its replacement started) from shrinking the pool under the live job.
    private static final AtomicInteger THREAD_COUNT = new AtomicInteger();
    private static final AtomicInteger BOOST_HOLDERS = new AtomicInteger();
    private static final int ADAPTIVE_SLOW_REQUEST_STEP = 3;
    private static final int ADAPTIVE_RECOVERY_INTERVAL = 8;
    private static final long CLOSE_DRAIN_WARNING_SECONDS = 60L;
    private static final long FLUSH_TIMEOUT_SECONDS = 120L;
    private final World world;
    private final IrisRuntimeSchedulerMode runtimeSchedulerMode;
    private final IrisPaperLikeBackendMode paperLikeBackendMode;
    private final boolean foliaRuntime;
    private final String backendMode;
    private final int workerPoolThreads;
    private final int runtimeCpuThreads;
    private final int effectiveWorkerThreads;
    private final int recommendedRuntimeConcurrencyCap;
    private final Method directChunkAtAsyncUrgentMethod;
    private final Method directChunkAtAsyncMethod;
    private final String chunkAccessMode;
    private final ChunkRequestExecutor executor;
    private final Executor slowRequestExecutor;
    private final Semaphore semaphore;
    private final int threads;
    private final int slowRequestWarningSeconds;
    private final int slowRequestWarnIntervalMs;
    private final boolean urgent;
    private final ConcurrentHashMap<Long, AtomicInteger> regionPending;
    private final ConcurrentHashMap<Long, Queue<Chunk>> regionChunks;
    private final Queue<CompletableFuture<Void>> pendingEvictions;
    private final KSet<Long> drainedRegions;
    private final KSet<Long> evictedRegions;
    private volatile int evictionWindowRegions;
    private volatile int boundsMinRegionX;
    private volatile int boundsMinRegionZ;
    private volatile int boundsMaxRegionX;
    private volatile int boundsMaxRegionZ;
    private final AtomicInteger adaptiveInFlightLimit;
    private final int adaptiveMinInFlightLimit;
    private final AtomicInteger slowRequestStreak = new AtomicInteger();
    private final AtomicLong lastSlowRequestLogAt = new AtomicLong(0L);
    private final AtomicLong lastFailedReleaseLogAt = new AtomicLong(0L);
    private final AtomicInteger suppressedSlowRequestLogs = new AtomicInteger();
    private final AtomicLong lastAdaptiveLogAt = new AtomicLong(0L);
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong lastProgressAt = new AtomicLong(M.ms());
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicBoolean holdsWorkerBoost = new AtomicBoolean();
    private final Object permitMonitor = new Object();
    private volatile Engine metricsEngine;
    private volatile Mantle cachedMantle;
    private final PregenMantleBackpressure backpressure;

    public AsyncPregenMethod(World world, int unusedThreads) {
        this(world, false);
    }

    private AsyncPregenMethod(World world, boolean strictSerial) {
        if (!PaperLib.isPaper()) {
            throw new UnsupportedOperationException("Cannot use PaperAsync on non paper!");
        }

        this.world = world;
        IrisSettings.IrisSettingsPregen pregen = IrisSettings.get().getPregen();
        this.runtimeSchedulerMode = IrisRuntimeSchedulerMode.resolve(pregen);
        this.foliaRuntime = runtimeSchedulerMode == IrisRuntimeSchedulerMode.FOLIA;
        ChunkAsyncMethodSelection chunkAsyncMethodSelection = resolveChunkAsyncMethodSelection(world);
        this.directChunkAtAsyncUrgentMethod = chunkAsyncMethodSelection.urgentMethod();
        this.directChunkAtAsyncMethod = chunkAsyncMethodSelection.standardMethod();
        this.chunkAccessMode = chunkAsyncMethodSelection.mode();
        int detectedWorkerPoolThreads = resolveWorkerPoolThreads();
        int detectedCpuThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
        int configuredWorldGenThreads = Math.max(1, IrisSettings.get().getConcurrency().getWorldGenThreads());
        int workerThreadsForCap = foliaRuntime
                ? resolveFoliaConcurrencyWorkerThreads(detectedWorkerPoolThreads, detectedCpuThreads, configuredWorldGenThreads)
                : resolvePaperLikeConcurrencyWorkerThreads(detectedWorkerPoolThreads, detectedCpuThreads, configuredWorldGenThreads);
        if (foliaRuntime) {
            this.paperLikeBackendMode = IrisPaperLikeBackendMode.AUTO;
            this.backendMode = "folia-region";
            this.executor = new FoliaRegionExecutor();
        } else {
            this.paperLikeBackendMode = resolvePaperLikeBackendMode(pregen);
            if (paperLikeBackendMode == IrisPaperLikeBackendMode.SERVICE) {
                this.executor = new ServiceExecutor();
                this.backendMode = "paper-service";
            } else {
                this.executor = new TicketExecutor();
                this.backendMode = "paper-ticket";
            }
        }
        int configuredThreads = foliaRuntime
                ? computeFoliaRecommendedCap(workerThreadsForCap)
                : computePaperLikeRecommendedCap(workerThreadsForCap);
        this.threads = selectConcurrencyCap(configuredThreads, strictSerial);
        this.workerPoolThreads = detectedWorkerPoolThreads;
        this.runtimeCpuThreads = detectedCpuThreads;
        this.effectiveWorkerThreads = workerThreadsForCap;
        this.recommendedRuntimeConcurrencyCap = configuredThreads;
        this.semaphore = new Semaphore(this.threads, true);
        this.slowRequestWarningSeconds = pregen.getChunkLoadTimeoutSeconds();
        this.slowRequestExecutor = CompletableFuture.delayedExecutor(this.slowRequestWarningSeconds, TimeUnit.SECONDS);
        this.slowRequestWarnIntervalMs = pregen.getTimeoutWarnIntervalMs();
        this.urgent = false;
        this.regionPending = new ConcurrentHashMap<>();
        this.regionChunks = new ConcurrentHashMap<>();
        this.pendingEvictions = new ConcurrentLinkedQueue<>();
        this.drainedRegions = new KSet<>();
        this.evictedRegions = new KSet<>();
        this.evictionWindowRegions = -1;
        this.boundsMinRegionX = Integer.MIN_VALUE;
        this.boundsMinRegionZ = Integer.MIN_VALUE;
        this.boundsMaxRegionX = Integer.MAX_VALUE;
        this.boundsMaxRegionZ = Integer.MAX_VALUE;
        this.adaptiveInFlightLimit = new AtomicInteger(computeInitialInFlightLimit(
                this.threads,
                this.effectiveWorkerThreads));
        this.adaptiveMinInFlightLimit = Math.max(4, Math.min(16, Math.max(1, this.threads / 4)));
        int pregenWorldHeight = world.getMaxHeight() - world.getMinHeight();
        this.backpressure = new PregenMantleBackpressure(
                this::resolveMantle,
                pregen.getEffectiveResidentTectonicPlates(pregenWorldHeight),
                pregen.getMantleBackpressureWaitMs(),
                pregen.getMantleBackpressureTimeoutMs(),
                this::lowerAdaptiveInFlightLimit,
                this::metricsSnapshot,
                this::isCancelled);
    }

    public static AsyncPregenMethod strictSerial(World world) {
        return new AsyncPregenMethod(world, true);
    }

    private IrisPaperLikeBackendMode resolvePaperLikeBackendMode(IrisSettings.IrisSettingsPregen pregen) {
        IrisPaperLikeBackendMode configuredMode = pregen.getPaperLikeBackendMode();
        if (configuredMode != IrisPaperLikeBackendMode.AUTO) {
            return configuredMode;
        }

        return IrisPaperLikeBackendMode.TICKET;
    }

    private int resolveWorkerPoolThreads() {
        try {
            Class<?> moonriseCommonClass = Class.forName("ca.spottedleaf.moonrise.common.util.MoonriseCommon");
            java.lang.reflect.Field workerPoolField = moonriseCommonClass.getDeclaredField("WORKER_POOL");
            Object workerPool = workerPoolField.get(null);
            Object coreThreads = workerPool.getClass().getDeclaredMethod("getCoreThreads").invoke(workerPool);
            if (coreThreads instanceof Thread[] threadsArray) {
                return threadsArray.length;
            }
        } catch (Throwable e) {
            PregenDiagnostics.probeFailed("moonrise worker pool size", e);
        }

        return -1;
    }

    private static long rkey(int rx, int rz) {
        return (((long) rx) << 32) | (rz & 0xFFFFFFFFL);
    }

    private int evictionWindow() {
        int cached = evictionWindowRegions;
        if (cached > 0) {
            return cached;
        }

        Engine engine = resolveMetricsEngine();
        if (engine == null) {
            return 2;
        }

        try {
            int radius = engine.getMantle().getRadius();
            int resolved = radius > 0 ? Math.max(1, (int) Math.ceil(radius / 32.0)) : 2;
            evictionWindowRegions = resolved;
            return resolved;
        } catch (Throwable e) {
            PregenDiagnostics.probeFailed("mantle radius for eviction window", e);
            return 2;
        }
    }

    private void onChunkCompleted(int x, int z, Chunk chunk) {
        if (chunk == null) {
            return;
        }

        try {
            long rk = rkey(x >> 5, z >> 5);
            regionChunks.computeIfAbsent(rk, k -> new ConcurrentLinkedQueue<>()).add(chunk);
            AtomicInteger pending = regionPending.get(rk);
            if (pending != null && pending.decrementAndGet() == 0) {
                onRegionDrained(rk);
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    private void onChunkFailedToLoad(int x, int z) {
        try {
            long rk = rkey(x >> 5, z >> 5);
            AtomicInteger pending = regionPending.get(rk);
            if (pending != null && pending.decrementAndGet() == 0) {
                onRegionDrained(rk);
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }

        long now = M.ms();
        long last = lastFailedReleaseLogAt.get();
        if (now - last >= slowRequestWarnIntervalMs && lastFailedReleaseLogAt.compareAndSet(last, now)) {
            IrisLogging.warn("Released region slot for failed chunk at " + x + "," + z + ". " + metricsSnapshot());
        }
    }

    @Override
    public void onRegionBounds(int minRegionX, int minRegionZ, int maxRegionX, int maxRegionZ) {
        boundsMinRegionX = minRegionX;
        boundsMinRegionZ = minRegionZ;
        boundsMaxRegionX = maxRegionX;
        boundsMaxRegionZ = maxRegionZ;
    }

    @Override
    public void onPregenStart(int centerBlockX, int centerBlockZ) {
        Engine engine = resolveMetricsEngine();
        if (engine == null || engine.getComplex() == null || engine.getComplex().getHydrologyRuntime() == null) {
            return;
        }
        engine.getComplex().getHydrologyRuntime().preparePregeneration(centerBlockX, centerBlockZ);
    }

    private boolean inBounds(int rx, int rz) {
        return rx >= boundsMinRegionX && rx <= boundsMaxRegionX && rz >= boundsMinRegionZ && rz <= boundsMaxRegionZ;
    }

    @Override
    public void onRegionSubmitted(int regionX, int regionZ) {
        try {
            long rk = rkey(regionX, regionZ);
            AtomicInteger pending = regionPending.get(rk);
            if (pending == null || pending.decrementAndGet() == 0) {
                onRegionDrained(rk);
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    private void onRegionDrained(long rk) {
        if (!drainedRegions.add(rk)) {
            return;
        }

        int w = evictionWindow();
        int rx = (int) (rk >> 32);
        int rz = (int) rk;
        for (int dx = -w; dx <= w; dx++) {
            for (int dz = -w; dz <= w; dz++) {
                int cx = rx + dx;
                int cz = rz + dz;
                long candidate = rkey(cx, cz);
                if (evictedRegions.contains(candidate)) {
                    continue;
                }
                if (!drainedRegions.contains(candidate)) {
                    continue;
                }
                if (allNeighborsDrained(cx, cz, w)) {
                    evictRegion(candidate);
                }
            }
        }
    }

    private boolean allNeighborsDrained(int rx, int rz, int w) {
        for (int dx = -w; dx <= w; dx++) {
            for (int dz = -w; dz <= w; dz++) {
                int nx = rx + dx;
                int nz = rz + dz;
                if (!inBounds(nx, nz)) {
                    continue;
                }

                if (!drainedRegions.contains(rkey(nx, nz))) {
                    return false;
                }
            }
        }

        return true;
    }

    private CompletableFuture<Void> evictRegion(long c) {
        if (IrisToolbelt.isServerStopping()) {
            return CompletableFuture.completedFuture(null);
        }
        if (!evictedRegions.add(c)) {
            return CompletableFuture.completedFuture(null);
        }

        regionPending.remove(c);
        Queue<Chunk> chunks = regionChunks.remove(c);
        if (chunks == null || chunks.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> eviction;
        if (foliaRuntime) {
            eviction = evictFoliaRegion(chunks);
        } else {
            eviction = J.sfut(() -> {
                for (Chunk chunk : chunks) {
                    if (chunk != null) {
                        unloadChunkSafely(chunk.getX(), chunk.getZ());
                    }
                }

                INMS.get().flushChunkIO(world);
            });
        }
        return trackEviction(eviction);
    }

    private CompletableFuture<Void> evictFoliaRegion(Queue<Chunk> chunks) {
        List<CompletableFuture<Void>> unloads = new ArrayList<>(chunks.size());
        Chunk anchor = null;
        for (Chunk chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            if (anchor == null) {
                anchor = chunk;
            }

            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();
            unloads.add(J.runRegionFuture(world, chunkX, chunkZ, () -> unloadChunkSafely(chunkX, chunkZ)));
        }

        CompletableFuture<Void> unloaded = CompletableFuture.allOf(unloads.toArray(CompletableFuture[]::new));
        if (anchor == null) {
            return unloaded;
        }

        int anchorX = anchor.getX();
        int anchorZ = anchor.getZ();
        return unloaded.thenCompose(ignored -> J.runRegionFuture(
                world,
                anchorX,
                anchorZ,
                () -> INMS.get().flushChunkIO(world)));
    }

    private CompletableFuture<Void> trackEviction(CompletableFuture<Void> eviction) {
        pendingEvictions.add(eviction);
        eviction.whenComplete((ignored, failure) -> {
            pendingEvictions.remove(eviction);
            if (failure != null) {
                IrisLogging.reportError(failure);
            }
        });
        return eviction;
    }

    private void unloadChunkSafely(int cx, int cz) {
        try {
            world.removePluginChunkTicket(cx, cz, BukkitPlatform.plugin());
        } catch (Throwable e) {
            IrisLogging.reportError("Async pregen could not release the plugin chunk ticket at " + cx + "," + cz
                    + " in world " + world.getName() + "; the chunk stays pinned in memory.", e);
        }

        try {
            if (!INMS.get().saveAndUnloadChunk(world, cx, cz)) {
                world.unloadChunk(cx, cz, true);
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    private void flushAllRemainingChunks() {
        if (IrisToolbelt.isServerStopping()) {
            return;
        }
        List<Long> keys = new ArrayList<>(regionChunks.keySet());
        List<CompletableFuture<Void>> evictions = new ArrayList<>(keys.size() + pendingEvictions.size());
        for (Long regionKey : keys) {
            evictions.add(evictRegion(regionKey));
        }
        evictions.addAll(pendingEvictions);

        CompletableFuture<Void> evictionsComplete = CompletableFuture.allOf(
                evictions.toArray(CompletableFuture[]::new));
        CompletableFuture<Void> settledEvictions = evictionsComplete.handle((ignored, failure) -> null);
        CompletableFuture<Void> flush = foliaRuntime
                ? settledEvictions
                : settledEvictions.thenCompose(ignored -> IrisToolbelt.isServerStopping()
                        ? CompletableFuture.completedFuture(null)
                        : J.sfut(() -> INMS.get().flushChunkIO(world)));

        long started = System.nanoTime();
        long timeoutNanos = TimeUnit.SECONDS.toNanos(FLUSH_TIMEOUT_SECONDS);
        try {
            while (!IrisToolbelt.isServerStopping()) {
                long remaining = timeoutNanos - (System.nanoTime() - started);
                if (remaining <= 0L) {
                    throw new TimeoutException();
                }
                try {
                    flush.get(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100L)), TimeUnit.NANOSECONDS);
                    return;
                } catch (TimeoutException e) {
                    if (System.nanoTime() - started >= timeoutNanos) {
                        throw e;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            IrisLogging.warn("Interrupted while flushing pregen chunks for " + world.getName() + ".");
        } catch (TimeoutException e) {
            IrisLogging.warn("Pregen chunk flush for " + world.getName() + " did not finish in " + FLUSH_TIMEOUT_SECONDS
                    + "s, continuing shutdown. " + keys.size() + " region(s) queued.");
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    private Chunk onChunkFutureFailure(int x, int z, Throwable throwable) {
        IrisLogging.reportError("Failed async pregen chunk load at " + x + "," + z + ".", throwable);

        try {
            IrisLogging.warn("Async pregen state at the failed chunk " + x + "," + z + ". " + metricsSnapshot());
        } catch (Throwable e) {
            PregenDiagnostics.probeFailed("pregen metrics snapshot", e);
        }

        return null;
    }

    private void completeChunk(int x, int z, PregenListener listener, Chunk chunk, Throwable throwable) {
        boolean success = false;
        try {
            if (throwable != null) {
                onChunkFutureFailure(x, z, throwable);
                onChunkFailedToLoad(x, z);
                listener.onChunkFailed(x, z);
            } else if (chunk == null) {
                onChunkFailedToLoad(x, z);
                listener.onChunkFailed(x, z);
            } else {
                listener.onChunkGenerated(x, z);
                cleanupMantleChunksCoveredBy(x, z, listener);
                onChunkCompleted(x, z, chunk);
                success = true;
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        } finally {
            try {
                markFinished(success);
            } finally {
                semaphore.release();
            }
        }
    }

    private void onSlowRequest(int x, int z) {
        if (closing.get()) {
            return;
        }

        int streak = slowRequestStreak.incrementAndGet();
        if (streak % ADAPTIVE_SLOW_REQUEST_STEP == 0) {
            lowerAdaptiveInFlightLimit();
        }

        long now = M.ms();
        long last = lastSlowRequestLogAt.get();
        if (now - last < slowRequestWarnIntervalMs || !lastSlowRequestLogAt.compareAndSet(last, now)) {
            suppressedSlowRequestLogs.incrementAndGet();
            return;
        }

        int suppressed = suppressedSlowRequestLogs.getAndSet(0);
        String suppressedText = suppressed <= 0 ? "" : " suppressed=" + suppressed;
        // Slow chunk loads are what the adaptive in flight limit exists to absorb, and this line is already
        // interval throttled with a suppression count. It reports the throttle working, not a fault.
        IrisLogging.debug("Async pregen chunk load at " + x + "," + z
                + " is still pending after " + slowRequestWarningSeconds + "s."
                + " adaptiveLimit=" + adaptiveInFlightLimit.get()
                + suppressedText + " " + metricsSnapshot());
    }

    private void onSuccess() {
        int streak = slowRequestStreak.get();
        if (streak > 0) {
            int newStreak = Math.max(0, streak - 2);
            slowRequestStreak.compareAndSet(streak, newStreak);
            if (newStreak > 0) {
                return;
            }
        }

        if ((completed.get() & (ADAPTIVE_RECOVERY_INTERVAL - 1)) == 0L) {
            raiseAdaptiveInFlightLimit();
        }
    }

    private void lowerAdaptiveInFlightLimit() {
        while (true) {
            int current = adaptiveInFlightLimit.get();
            if (current <= adaptiveMinInFlightLimit) {
                return;
            }

            int next = Math.max(adaptiveMinInFlightLimit, current - 1);
            if (adaptiveInFlightLimit.compareAndSet(current, next)) {
                logAdaptiveLimit("decrease", next);
                notifyPermitWaiters();
                return;
            }
        }
    }

    private void raiseAdaptiveInFlightLimit() {
        while (true) {
            int current = adaptiveInFlightLimit.get();
            if (current >= threads) {
                return;
            }

            int next = nextAdaptiveInFlightLimit(current, threads);
            if (adaptiveInFlightLimit.compareAndSet(current, next)) {
                logAdaptiveLimit("increase", next);
                notifyPermitWaiters();
                return;
            }
        }
    }

    private void logAdaptiveLimit(String mode, int value) {
        long now = M.ms();
        long last = lastAdaptiveLogAt.get();
        if (now - last < 5000L) {
            return;
        }

        if (lastAdaptiveLogAt.compareAndSet(last, now)) {
            IrisLogging.debug("Async pregen adaptive limit " + mode + " -> " + value + " " + metricsSnapshot());
        }
    }

    static int computePaperLikeRecommendedCap(int workerThreads) {
        int normalizedWorkers = Math.max(1, workerThreads);
        int recommendedCap = normalizedWorkers * 8;
        if (recommendedCap < 16) {
            return 16;
        }

        // A request spends most of its life queued behind Paper's status pipeline, so a large
        // worker pool needs well over a hundred requests in flight before its workers stay busy.
        if (recommendedCap > 256) {
            return 256;
        }

        return recommendedCap;
    }

    static int selectConcurrencyCap(int recommendedCap, boolean strictSerial) {
        return strictSerial ? 1 : Math.max(1, recommendedCap);
    }

    static int computeInitialInFlightLimit(int concurrencyCap, int workerThreads) {
        int initial = Math.max(4, Math.max(1, workerThreads));
        return Math.min(Math.max(1, concurrencyCap), initial);
    }

    static int nextAdaptiveInFlightLimit(int current, int concurrencyCap) {
        return Math.min(Math.max(1, concurrencyCap), Math.max(1, current) + 1);
    }

    static int resolvePaperLikeConcurrencyWorkerThreads(int detectedWorkerPoolThreads, int detectedCpuThreads, int configuredWorldGenThreads) {
        int provisionedWorkerThreads = Math.max(1, configuredWorldGenThreads);
        if (detectedWorkerPoolThreads > 0) {
            return Math.max(detectedWorkerPoolThreads, provisionedWorkerThreads);
        }

        return Math.max(provisionedWorkerThreads, detectedCpuThreads);
    }

    static <T> CompletableFuture<T> observeSlowRequest(CompletableFuture<T> future, Executor executor, Runnable onSlowRequest) {
        executor.execute(() -> {
            if (!future.isDone()) {
                onSlowRequest.run();
            }
        });
        return future;
    }

    static int computeFoliaRecommendedCap(int workerThreads) {
        int normalizedWorkers = Math.max(1, workerThreads);
        int recommendedCap = normalizedWorkers * 8;
        if (recommendedCap < 64) {
            return 64;
        }

        if (recommendedCap > 192) {
            return 192;
        }

        return recommendedCap;
    }

    static int resolveFoliaConcurrencyWorkerThreads(int detectedWorkerPoolThreads, int detectedCpuThreads, int configuredWorldGenThreads) {
        return Math.max(detectedCpuThreads, Math.max(configuredWorldGenThreads, Math.max(1, detectedWorkerPoolThreads)));
    }

    private String metricsSnapshot() {
        long stalledFor = Math.max(0L, M.ms() - lastProgressAt.get());
        return "world=" + world.getName()
                + " permits=" + semaphore.availablePermits() + "/" + threads
                + " adaptiveLimit=" + adaptiveInFlightLimit.get()
                + " inFlight=" + inFlight.get()
                + " submitted=" + submitted.get()
                + " completed=" + completed.get()
                + " failed=" + failed.get()
                + " stalledForMs=" + stalledFor;
    }

    private void markSubmitted() {
        submitted.incrementAndGet();
        inFlight.incrementAndGet();
    }

    private void markFinished(boolean success) {
        if (success) {
            completed.incrementAndGet();
            onSuccess();
        } else {
            failed.incrementAndGet();
        }

        lastProgressAt.set(M.ms());
        int after = inFlight.decrementAndGet();
        if (after < 0) {
            inFlight.compareAndSet(after, 0);
        }
        notifyPermitWaiters();
    }

    private void notifyPermitWaiters() {
        synchronized (permitMonitor) {
            permitMonitor.notifyAll();
        }
    }

    private void recordAdaptiveWait(long waitedMs) {
        Engine engine = resolveMetricsEngine();
        if (engine != null) {
            engine.getMetrics().getPregenWaitAdaptive().put(waitedMs);
        }
    }

    private void recordPermitWait(long waitedMs) {
        Engine engine = resolveMetricsEngine();
        if (engine != null) {
            engine.getMetrics().getPregenWaitPermit().put(waitedMs);
        }
    }

    private void cleanupMantleChunksCoveredBy(int x, int z, PregenListener listener) {
        Engine engine = resolveMetricsEngine();
        if (engine != null) {
            try {
                engine.getMantle().cleanupChunksCoveredBy(x, z, true, listener::onChunkCleaned);
            } catch (Throwable e) {
                IrisLogging.reportError("Async pregen mantle cleanup failed at chunk " + x + "," + z
                        + " in world " + world.getName() + "; tectonic plates for that chunk stay resident.", e);
            }
        }
    }

    private Engine resolveMetricsEngine() {
        Engine cachedEngine = metricsEngine;
        if (cachedEngine != null) {
            return cachedEngine;
        }

        if (!IrisToolbelt.isIrisWorld(world)) {
            return null;
        }

        try {
            Engine resolvedEngine = IrisToolbelt.access(world).getEngine();
            if (resolvedEngine != null) {
                metricsEngine = resolvedEngine;
            }
            return resolvedEngine;
        } catch (Throwable e) {
            PregenDiagnostics.probeFailed("engine access for world " + world.getName(), e);
            return null;
        }
    }

    private Mantle resolveMantle() {
        Mantle cached = cachedMantle;
        if (cached != null) {
            return cached;
        }

        Mantle resolved = getMantle();
        if (resolved != null) {
            cachedMantle = resolved;
        }
        return resolved;
    }

    @Override
    public void init() {
        IrisLogging.info("Async pregen init: world=" + world.getName()
                + ", mode=" + runtimeSchedulerMode.name().toLowerCase(Locale.ROOT)
                + ", backend=" + backendMode
                + ", chunkAccess=" + chunkAccessMode
                + ", threads=" + threads
                + ", adaptiveLimit=" + adaptiveInFlightLimit.get()
                + ", workerPoolThreads=" + workerPoolThreads
                + ", cpuThreads=" + runtimeCpuThreads
                + ", effectiveWorkerThreads=" + effectiveWorkerThreads
                + ", recommendedCap=" + recommendedRuntimeConcurrencyCap
                + ", urgent=" + urgent
                + ", slowWarning=" + slowRequestWarningSeconds + "s");
        if (workerPoolThreads > 0 && holdsWorkerBoost.compareAndSet(false, true)) {
            acquireWorkerThreadBoost();
        }
    }

    @Override
    public String getMethod(int x, int z) {
        return "Async";
    }

    @Override
    public boolean isAsyncChunkMode() {
        return true;
    }

    @Override
    public void close() {
        closing.set(true);
        notifyPermitWaiters();

        // A stop request interrupts the pregen worker; shield the drain and flush so chunks still hit disk.
        boolean interrupted = Thread.interrupted();
        try {
            interrupted |= awaitDrain(
                    semaphore,
                    threads,
                    CLOSE_DRAIN_WARNING_SECONDS,
                    TimeUnit.SECONDS,
                    () -> IrisLogging.warn("Async pregen is still draining outstanding chunks. " + metricsSnapshot())
            );

            flushAllRemainingChunks();
            executor.shutdown();
            if (holdsWorkerBoost.compareAndSet(true, false)) {
                releaseWorkerThreadBoost();
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static boolean awaitDrain(
            Semaphore semaphore,
            int permits,
            long warningInterval,
            TimeUnit timeUnit,
            Runnable onWait
    ) {
        boolean interrupted = false;
        while (true) {
            try {
                if (semaphore.tryAcquire(permits, warningInterval, timeUnit)) {
                    return interrupted;
                }
                onWait.run();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
    }

    private boolean isCancelled() {
        return closing.get() || Thread.currentThread().isInterrupted();
    }

    @Override
    public void save() {
    }

    @Override
    public boolean supportsRegions(int x, int z, PregenListener listener) {
        return false;
    }

    @Override
    public void generateRegion(int x, int z, PregenListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void generateChunk(int x, int z, PregenListener listener) {
        listener.onChunkGenerating(x, z);
        backpressure.enforceMantleBudget();
        backpressure.awaitHeapHeadroom();
        if (isCancelled()) {
            return;
        }

        try {
            long waitStart = M.ms();
            synchronized (permitMonitor) {
                while (inFlight.get() >= adaptiveInFlightLimit.get()) {
                    if (isCancelled()) {
                        return;
                    }

                    permitMonitor.wait(500L);
                }
            }
            long adaptiveWait = Math.max(0L, M.ms() - waitStart);
            if (adaptiveWait > 0L) {
                recordAdaptiveWait(adaptiveWait);
            }

            long permitWaitStart = M.ms();
            while (!semaphore.tryAcquire(1, TimeUnit.SECONDS)) {
                if (isCancelled()) {
                    return;
                }
            }
            long permitWait = Math.max(0L, M.ms() - permitWaitStart);
            if (permitWait > 0L) {
                recordPermitWait(permitWait);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        regionPending.computeIfAbsent(rkey(x >> 5, z >> 5), k -> new AtomicInteger(1)).incrementAndGet();
        markSubmitted();
        executor.generate(x, z, listener);
    }

    private CompletableFuture<Chunk> requestChunkAsync(int x, int z) {
        Throwable failure = null;

        if (directChunkAtAsyncUrgentMethod != null) {
            try {
                return invokeChunkFuture(directChunkAtAsyncUrgentMethod, x, z, true, urgent);
            } catch (Throwable e) {
                failure = e;
            }
        }

        if (directChunkAtAsyncMethod != null) {
            try {
                return invokeChunkFuture(directChunkAtAsyncMethod, x, z, true, urgent);
            } catch (Throwable e) {
                if (failure == null) {
                    failure = e;
                }
            }
        }

        try {
            CompletableFuture<Chunk> future = PaperLib.getChunkAtAsync(world, x, z, true, urgent);
            if (future != null) {
                return future;
            }
        } catch (Throwable e) {
            if (failure == null) {
                failure = e;
            }
        }

        if (failure == null) {
            failure = new IllegalStateException("Chunk async access returned no future.");
        }

        return CompletableFuture.failedFuture(new IllegalStateException("Failed to request async chunk " + x + "," + z + " in world " + world.getName(), failure));
    }

    private CompletableFuture<Chunk> requestMonitoredChunkAsync(int x, int z) {
        return observeSlowRequest(requestChunkAsync(x, z), slowRequestExecutor, () -> onSlowRequest(x, z));
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Chunk> invokeChunkFuture(Method method, int x, int z, boolean generate, boolean urgentRequest) throws Throwable {
        Object result;
        try {
            if (method.getParameterCount() == 4) {
                result = method.invoke(world, x, z, generate, urgentRequest);
            } else {
                result = method.invoke(world, x, z, generate);
            }
        } catch (InvocationTargetException e) {
            throw e.getCause() == null ? e : e.getCause();
        }

        if (result instanceof CompletableFuture<?>) {
            return (CompletableFuture<Chunk>) result;
        }

        throw new IllegalStateException("Chunk async method returned a non-future result.");
    }

    private static ChunkAsyncMethodSelection resolveChunkAsyncMethodSelection(World world) {
        if (world == null) {
            return new ChunkAsyncMethodSelection(null, null, "paperlib");
        }

        Class<?> worldClass = world.getClass();
        Method urgentMethod = resolveChunkAsyncMethod(worldClass, int.class, int.class, boolean.class, boolean.class);
        Method standardMethod = resolveChunkAsyncMethod(worldClass, int.class, int.class, boolean.class);
        if (urgentMethod != null) {
            return new ChunkAsyncMethodSelection(urgentMethod, standardMethod, "world#getChunkAtAsync(int,int,boolean,boolean)");
        }
        if (standardMethod != null) {
            return new ChunkAsyncMethodSelection(null, standardMethod, "world#getChunkAtAsync(int,int,boolean)");
        }
        return new ChunkAsyncMethodSelection(null, null, "paperlib");
    }

    private static Method resolveChunkAsyncMethod(Class<?> worldClass, Class<?>... parameterTypes) {
        try {
            return worldClass.getMethod("getChunkAtAsync", parameterTypes);
        } catch (NoSuchMethodException ignored) {
        }

        try {
            return World.class.getMethod("getChunkAtAsync", parameterTypes);
        } catch (NoSuchMethodException ignored) {
        }

        return null;
    }

    @Override
    public Mantle getMantle() {
        if (IrisToolbelt.isIrisWorld(world)) {
            return IrisToolbelt.access(world).getEngine().getMantle().getMantle();
        }

        return null;
    }

    public static void acquireWorkerThreadBoost() {
        if (BOOST_HOLDERS.getAndIncrement() > 0) {
            return;
        }
        increaseWorkerThreads();
    }

    public static void releaseWorkerThreadBoost() {
        if (BOOST_HOLDERS.decrementAndGet() > 0) {
            return;
        }
        resetWorkerThreads();
    }

    private static void increaseWorkerThreads() {
        THREAD_COUNT.updateAndGet(i -> {
            if (i > 0) {
                return i;
            }

            int adjusted = IrisSettings.get().getConcurrency().getWorldGenThreads();
            try {
                Field field = Class.forName("ca.spottedleaf.moonrise.common.util.MoonriseCommon").getDeclaredField("WORKER_POOL");
                Object pool = field.get(null);
                int threads = ((Thread[]) pool.getClass().getDeclaredMethod("getCoreThreads").invoke(pool)).length;
                if (threads >= adjusted) {
                    return 0;
                }

                pool.getClass().getDeclaredMethod("adjustThreadCount", int.class).invoke(pool, adjusted);
                return threads;
            } catch (Throwable e) {
                IrisLogging.warn("Failed to increase worker threads, if you are on paper or a fork of it please increase it manually to " + adjusted);
                IrisLogging.warn("For more information see https://docs.papermc.io/paper/reference/global-configuration#chunk_system_worker_threads");
                if (e instanceof InvocationTargetException) {
                    IrisLogging.reportError(e);
                }
            }
            return 0;
        });
    }

    private static void resetWorkerThreads() {
        THREAD_COUNT.updateAndGet(i -> {
            if (i == 0) {
                return 0;
            }

            try {
                Field field = Class.forName("ca.spottedleaf.moonrise.common.util.MoonriseCommon").getDeclaredField("WORKER_POOL");
                Object pool = field.get(null);
                Method method = pool.getClass().getDeclaredMethod("adjustThreadCount", int.class);
                method.invoke(pool, i);
                return 0;
            } catch (Throwable e) {
                IrisLogging.reportError(e);
                IrisLogging.error("Failed to reset worker threads");
            }
            return i;
        });
    }

    private interface ChunkRequestExecutor {
        void generate(int x, int z, PregenListener listener);
        default void shutdown() {}
    }

    private class FoliaRegionExecutor implements ChunkRequestExecutor {
        @Override
        public void generate(int x, int z, PregenListener listener) {
            try {
                requestMonitoredChunkAsync(x, z)
                        .whenComplete((chunk, throwable) -> completeChunk(x, z, listener, chunk, throwable));
                return;
            } catch (Throwable e) {
                PregenDiagnostics.probeFailed("folia direct chunk request at " + x + "," + z
                        + ", falling back to a region task", e);
            }

            Runnable regionTask = () -> {
                try {
                    requestMonitoredChunkAsync(x, z)
                            .whenComplete((chunk, throwable) -> completeChunk(x, z, listener, chunk, throwable));
                } catch (Throwable e) {
                    completeChunk(x, z, listener, null, e);
                }
            };

            if (!J.runRegion(world, x, z, regionTask)) {
                completeChunk(x, z, listener, null,
                        new IllegalStateException("Failed to schedule Folia region pregen task at " + x + "," + z + ". " + metricsSnapshot()));
            }
        }
    }

    private class ServiceExecutor implements ChunkRequestExecutor {
        private final ExecutorService service = new MultiBurst("Iris Async Pregen");

        public void generate(int x, int z, PregenListener listener) {
            try {
                service.submit(() -> {
                    try {
                        Chunk i = requestMonitoredChunkAsync(x, z).get();
                        completeChunk(x, z, listener, i, null);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        completeChunk(x, z, listener, null, e);
                    } catch (Throwable e) {
                        completeChunk(x, z, listener, null, e);
                    }
                });
            } catch (Throwable e) {
                completeChunk(x, z, listener, null, e);
            }
        }

        @Override
        public void shutdown() {
            service.shutdown();
        }
    }

    private class TicketExecutor implements ChunkRequestExecutor {
        @Override
        public void generate(int x, int z, PregenListener listener) {
            try {
                requestMonitoredChunkAsync(x, z)
                        .whenComplete((chunk, throwable) -> completeChunk(x, z, listener, chunk, throwable));
            } catch (Throwable e) {
                completeChunk(x, z, listener, null, e);
            }
        }
    }

    private record ChunkAsyncMethodSelection(Method urgentMethod, Method standardMethod, String mode) {
    }
}
