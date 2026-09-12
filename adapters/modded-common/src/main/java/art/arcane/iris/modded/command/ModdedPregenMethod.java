/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded.command;

import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.world.pregen.PregenListener;
import art.arcane.iris.world.pregen.PregenMantleBackpressure;
import art.arcane.iris.world.pregen.PregeneratorMethod;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.modded.ModdedGenPool;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ModdedPregenMethod implements PregeneratorMethod {
    private static final TicketType PREGEN_TICKET = new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    private static final int ADAPTIVE_TIMEOUT_STEP = 3;
    private static final long ADAPTIVE_RECOVERY_INTERVAL = 64L;
    private static final long FINAL_SAVE_TIMEOUT_MILLIS = 10_000L;
    private static final long FINAL_SAVE_POLL_MILLIS = 50L;
    private static final AtomicBoolean SERVER_DEAD_LOGGED = new AtomicBoolean();

    private final ServerLevel level;
    private final Engine engine;
    private final boolean sync;
    private final int maxInFlight;
    private final int minInFlight;
    private final Semaphore semaphore;
    private final Object permitMonitor = new Object();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger inFlightPeak = new AtomicInteger();
    private final AtomicInteger adaptiveLimit;
    private final AtomicInteger timeoutStreak = new AtomicInteger();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicBoolean finalSaveOnServerThread = new AtomicBoolean(false);
    private final AtomicBoolean finalSaveDeferred = new AtomicBoolean(false);
    private final AtomicBoolean finalSaveCompleted = new AtomicBoolean(false);
    private final AtomicReference<FinalSaveRequest> queuedFinalSave = new AtomicReference<>();
    private final AtomicBoolean stallHintLogged = new AtomicBoolean(false);
    private final AtomicBoolean failureDetailLogged = new AtomicBoolean(false);
    private final int timeoutSeconds;
    private final PregenMantleBackpressure backpressure;
    private final PauseWhenEmptyGuard pauseGuard;

    public ModdedPregenMethod(ServerLevel level, Engine engine) {
        this(level, engine, false);
    }

    public ModdedPregenMethod(ServerLevel level, Engine engine, boolean sync) {
        this.level = level;
        this.engine = engine;
        this.sync = sync;
        this.pauseGuard = new PauseWhenEmptyGuard(level.getServer());
        IrisSettings.IrisSettingsPregen pregen = IrisSettings.get().getPregen();
        this.maxInFlight = Math.max(8, pregen.getModdedPregenInFlight());
        this.minInFlight = Math.max(4, Math.min(16, maxInFlight / 4));
        this.semaphore = new Semaphore(maxInFlight, true);
        this.adaptiveLimit = new AtomicInteger(sync ? 1 : maxInFlight);
        this.timeoutSeconds = Math.max(120, pregen.getChunkLoadTimeoutSeconds());
        this.backpressure = new PregenMantleBackpressure(
                this::getMantle,
                pregen.getEffectiveResidentTectonicPlates(engine.getHeight()),
                pregen.getMantleBackpressureWaitMs(),
                pregen.getMantleBackpressureTimeoutMs(),
                () -> {
                },
                () -> "dim=" + level.dimension().identifier());
    }

    @Override
    public void init() {
        pauseGuard.suspend();
        ModdedIrisLog.info("Iris modded pregen init: dim={} mode={} inFlightCap={} timeout={}s workerPool={} chunkSystem={}",
                level.dimension().identifier(),
                sync ? "sync" : "async",
                sync ? 1 : maxInFlight,
                timeoutSeconds,
                describeWorkerPool(),
                ModdedGenPool.describeChunkSystem());
        if (!sync && !ModdedGenPool.parallelChunkSystem()) {
            ModdedIrisLog.info("Iris pregen note: this loader uses the vanilla main-thread chunk system, which caps pregen throughput. For Bukkit-level speed on Fabric install C2ME (Concurrent Chunk Management Engine); on servers use Paper.");
        }
    }

    @Override
    public void close() {
        try {
            if (!sync) {
                try {
                    semaphore.tryAcquire(maxInFlight, 5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            ModdedIrisLog.info("Iris modded pregen done: dim={} completed={} peakInFlight={} finalLimit={}",
                    level.dimension().identifier(), completed.get(), inFlightPeak.get(), adaptiveLimit.get());
            if (deferFinalSaveIfRequested()) {
                return;
            }
            saveLevel(true);
        } finally {
            pauseGuard.restore();
        }
    }

    @Override
    public void save() {
        saveLevel(false);
    }

    boolean deferFinalSaveToServerThread() {
        if (!level.getServer().isSameThread()) {
            return false;
        }
        finalSaveOnServerThread.set(true);
        return true;
    }

    void completeDeferredFinalSave() {
        requireServerThreadForFinalSave();
        cancelQueuedFinalSave();
        if (!finalSaveCompleted.get()) {
            saveLevelOnServerThread();
            finalSaveCompleted.set(true);
        }
        finalSaveDeferred.set(false);
        finalSaveOnServerThread.set(false);
    }

    void cancelDeferredFinalSave() {
        requireServerThreadForFinalSave();
        finalSaveOnServerThread.set(false);
        if (finalSaveDeferred.get() || queuedFinalSave.get() != null) {
            cancelQueuedFinalSave();
            if (!finalSaveCompleted.get()) {
                saveLevelOnServerThread();
                finalSaveCompleted.set(true);
            }
            finalSaveDeferred.set(false);
        }
    }

    boolean hasPendingFinalSave() {
        return finalSaveOnServerThread.get()
                || finalSaveDeferred.get()
                || queuedFinalSave.get() != null;
    }

    private void saveLevel(boolean wait) {
        if (wait) {
            saveFinalLevel();
            return;
        }
        MinecraftServer server = level.getServer();
        if (server.isSameThread()) {
            saveLevelOnServerThread();
            return;
        }
        server.execute(this::saveLevelOnServerThread);
    }

    private void saveFinalLevel() {
        MinecraftServer server = level.getServer();
        if (server.isSameThread()) {
            saveLevelOnServerThread();
            finalSaveCompleted.set(true);
            return;
        }
        if (deferFinalSaveIfRequested()) {
            return;
        }

        FinalSaveRequest request = new FinalSaveRequest();
        if (!queuedFinalSave.compareAndSet(null, request)) {
            throw new IllegalStateException("Iris pregen final save is already queued for "
                    + level.dimension().identifier());
        }
        try {
            server.execute(() -> executeFinalSave(request));
        } catch (RuntimeException | Error failure) {
            queuedFinalSave.compareAndSet(request, null);
            request.fail(failure);
            throw failure;
        }
        awaitFinalSave(request);
    }

    private void executeFinalSave(FinalSaveRequest request) {
        if (!request.claim()) {
            return;
        }
        try {
            saveLevelOnServerThread();
            finalSaveCompleted.set(true);
            request.complete();
        } catch (RuntimeException | Error failure) {
            request.fail(failure);
            throw failure;
        } finally {
            queuedFinalSave.compareAndSet(request, null);
        }
    }

    private void awaitFinalSave(FinalSaveRequest request) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FINAL_SAVE_TIMEOUT_MILLIS);
        while (true) {
            if (deferFinalSaveIfRequested()) {
                return;
            }
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0L) {
                ModdedIrisLog.warn("Iris pregen level save did not complete in time for {}", level.dimension().identifier());
                return;
            }
            long waitMillis = Math.max(1L, Math.min(FINAL_SAVE_POLL_MILLIS,
                    TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
            try {
                request.completion().get(waitMillis, TimeUnit.MILLISECONDS);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (TimeoutException e) {
                continue;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                ModdedIrisLog.error("Iris pregen level save failed for {}", level.dimension().identifier(), cause);
                throw new IllegalStateException("Iris pregen level save failed for "
                        + level.dimension().identifier(), cause);
            }
        }
    }

    private boolean deferFinalSaveIfRequested() {
        if (!finalSaveOnServerThread.get()) {
            return false;
        }
        finalSaveDeferred.set(true);
        if (finalSaveOnServerThread.get()) {
            return true;
        }
        finalSaveDeferred.set(false);
        return false;
    }

    private void cancelQueuedFinalSave() {
        FinalSaveRequest request = queuedFinalSave.getAndSet(null);
        if (request != null) {
            request.cancel();
        }
    }

    private void saveLevelOnServerThread() {
        level.save(null, false, false);
    }

    private void requireServerThreadForFinalSave() {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Iris pregen final save must run on the Minecraft server thread for "
                    + level.dimension().identifier());
        }
    }

    @Override
    public boolean supportsRegions(int x, int z, PregenListener listener) {
        return false;
    }

    @Override
    public String getMethod(int x, int z) {
        return "Modded";
    }

    @Override
    public boolean isAsyncChunkMode() {
        return !sync;
    }

    @Override
    public void generateRegion(int x, int z, PregenListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void generateChunk(int x, int z, PregenListener listener) {
        backpressure.apply();
        if (sync) {
            generateChunkSync(x, z, listener);
            return;
        }
        generateChunkAsync(x, z, listener);
    }

    private void generateChunkSync(int x, int z, PregenListener listener) {
        listener.onChunkGenerating(x, z);
        ChunkPos pos = new ChunkPos(x, z);
        CompletableFuture<?> loadFuture = CompletableFuture
                .supplyAsync(() -> level.getChunkSource().addTicketAndLoadWithRadius(PREGEN_TICKET, pos, 0), level.getServer())
                .thenCompose((CompletableFuture<?> inner) -> inner);
        markSubmitted();
        try {
            Object result = loadFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            if (result instanceof ChunkResult<?> chunkResult && !chunkResult.isSuccess()) {
                ModdedIrisLog.warn("Iris pregen chunk {},{} returned no chunk: {}", x, z, chunkResult.getError());
                listener.onChunkFailed(x, z);
                return;
            }
            markCompleted();
            listener.onChunkGenerated(x, z);
            cleanupMantleChunksCoveredBy(x, z, listener);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException | ExecutionException e) {
            if (e instanceof TimeoutException) {
                noteStallHint();
            }
            logChunkFailure(x, z, e);
            listener.onChunkFailed(x, z);
        } finally {
            markFinished();
            level.getServer().execute(() -> level.getChunkSource().removeTicketWithRadius(PREGEN_TICKET, pos, 0));
        }
    }

    private void generateChunkAsync(int x, int z, PregenListener listener) {
        // A stopped server executes submitted tasks inline and its chunk futures never complete; without
        // this abort the pregen thread spins hot against the dead chunk source until the JVM dies.
        if (level.getServer().isStopped() || !level.getServer().isRunning()) {
            if (SERVER_DEAD_LOGGED.compareAndSet(false, true)) {
                ModdedIrisLog.error("Iris pregen aborting: the server is no longer running (dim={})", level.dimension().identifier());
            }
            listener.onChunkFailed(x, z);
            ModdedPregenJob.stop();
            return;
        }
        listener.onChunkGenerating(x, z);
        try {
            synchronized (permitMonitor) {
                while (inFlight.get() >= adaptiveLimit.get()) {
                    permitMonitor.wait(500L);
                }
            }
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        markSubmitted();

        ChunkPos pos = new ChunkPos(x, z);
        CompletableFuture<?> loadFuture = CompletableFuture
                .supplyAsync(() -> level.getChunkSource().addTicketAndLoadWithRadius(PREGEN_TICKET, pos, 0), level.getServer())
                .thenCompose((CompletableFuture<?> inner) -> inner);

        loadFuture.orTimeout(timeoutSeconds, TimeUnit.SECONDS).whenComplete((Object result, Throwable error) -> {
            level.getServer().execute(() -> level.getChunkSource().removeTicketWithRadius(PREGEN_TICKET, pos, 0));
            try {
                if (error != null) {
                    if (unwrap(error) instanceof TimeoutException) {
                        onTimeout();
                    }
                    logChunkFailure(x, z, error);
                    listener.onChunkFailed(x, z);
                    return;
                }
                if (result instanceof ChunkResult<?> chunkResult && !chunkResult.isSuccess()) {
                    ModdedIrisLog.warn("Iris pregen chunk {},{} returned no chunk: {}", x, z, chunkResult.getError());
                    listener.onChunkFailed(x, z);
                    return;
                }
                onSuccess();
                markCompleted();
                listener.onChunkGenerated(x, z);
                cleanupMantleChunksCoveredBy(x, z, listener);
            } finally {
                markFinished();
                semaphore.release();
            }
        });
    }

    private void markSubmitted() {
        int current = inFlight.incrementAndGet();
        inFlightPeak.accumulateAndGet(current, Math::max);
    }

    private void logChunkFailure(int x, int z, Throwable failure) {
        Throwable cause = unwrap(failure);
        if (failureDetailLogged.compareAndSet(false, true)) {
            ModdedIrisLog.warn("Iris pregen chunk {},{} failed; first failure follows", x, z, cause);
            return;
        }
        ModdedIrisLog.warn("Iris pregen chunk {},{} failed: {}", x, z, cause.toString());
    }

    private void markFinished() {
        inFlight.decrementAndGet();
        if (sync) {
            return;
        }
        synchronized (permitMonitor) {
            permitMonitor.notifyAll();
        }
    }

    private void markCompleted() {
        completed.incrementAndGet();
    }

    private void onTimeout() {
        noteStallHint();
        if (timeoutStreak.incrementAndGet() % ADAPTIVE_TIMEOUT_STEP == 0) {
            adjustAdaptiveLimit(-1);
        }
    }

    /**
     * First timeout of a job explains itself if the server is able to stop ticking under us. Without
     * this a paused server just produces a wall of identical chunk timeouts.
     */
    private void noteStallHint() {
        if (stallHintLogged.compareAndSet(false, true)) {
            pauseGuard.logStallHint();
        }
    }

    private void onSuccess() {
        int streak = timeoutStreak.get();
        if (streak > 0) {
            timeoutStreak.compareAndSet(streak, Math.max(0, streak - 2));
            return;
        }
        if ((completed.get() & (ADAPTIVE_RECOVERY_INTERVAL - 1L)) == 0L) {
            adjustAdaptiveLimit(1);
        }
    }

    private void adjustAdaptiveLimit(int direction) {
        while (true) {
            int current = adaptiveLimit.get();
            int next;
            if (direction < 0) {
                next = Math.max(minInFlight, current - 1);
            } else {
                int deficit = maxInFlight - current;
                int step = deficit > (maxInFlight / 2) ? Math.max(2, maxInFlight / 8) : 1;
                next = Math.min(maxInFlight, current + step);
            }
            if (next == current) {
                return;
            }
            if (adaptiveLimit.compareAndSet(current, next)) {
                synchronized (permitMonitor) {
                    permitMonitor.notifyAll();
                }
                return;
            }
        }
    }

    private void cleanupMantleChunksCoveredBy(int x, int z, PregenListener listener) {
        try {
            engine.getMantle().cleanupChunksCoveredBy(x, z, true, listener::onChunkCleaned);
        } catch (Throwable e) {
            ModdedIrisLog.debug("Iris pregen mantle cleanup skipped for {},{}: {}", x, z, e.toString());
        }
    }

    private String describeWorkerPool() {
        Executor exec = level.getServer().executor;
        if (exec == null) {
            return "unknown";
        }
        if (exec instanceof ThreadPoolExecutor tpe) {
            return "ThreadPoolExecutor(core=" + tpe.getCorePoolSize() + ",max=" + tpe.getMaximumPoolSize() + ")";
        }
        if (exec instanceof ForkJoinPool fjp) {
            return "ForkJoinPool(parallelism=" + fjp.getParallelism() + ")";
        }
        return exec.getClass().getSimpleName();
    }

    private static Throwable unwrap(Throwable error) {
        return error != null && error.getCause() != null ? error.getCause() : error;
    }

    @Override
    public Mantle getMantle() {
        return engine.getMantle().getMantle();
    }

    /**
     * A dedicated server with {@code pause-when-empty-seconds > 0} returns from
     * {@code MinecraftServer#tickServer} before {@code tickChildren} once it has been empty for that
     * long (26.2 only keeps {@code tickConnection} plus the task/chunk-poll window alive). That
     * freezes every per-tick Iris service - world manager, scheduler, protocol sync, pregen HUD - and
     * the loader's own generation hooks for the whole job, and console pregen on a default
     * server.properties is always empty. The guard zeroes the setting for the duration of the job
     * through the vanilla public accessors
     * ({@code DedicatedServer#pauseWhenEmptySeconds}/{@code #setPauseWhenEmptySeconds}, both widened
     * to public by Mojang for the management API) and restores the previous value on completion or
     * abort. No reflection and no access widener, identical on all three loaders. An integrated
     * (singleplayer) server never pauses on empty - {@code MinecraftServer#pauseWhenEmptySeconds}
     * returns 0 there - so it is skipped silently.
     *
     * <p>A crash or a kill during the job would otherwise leave the setting at 0 for the rest of the install,
     * so suspending also arms a JVM shutdown hook that restores the previous value. The hook and
     * {@link #restore()} share the same atomic, so whichever runs first wins and the other is a no-op; a
     * normal restore also unregisters the hook. The setting is only ever restored in memory - nothing rewrites
     * server.properties, so operator edits made during the job survive.
     */
    private static final class PauseWhenEmptyGuard {
        private static final int NOT_SUSPENDED = -1;

        private final MinecraftServer server;
        private final AtomicInteger suspendedFrom = new AtomicInteger(NOT_SUSPENDED);
        private final AtomicReference<Thread> crashRestoreHook = new AtomicReference<>();

        private PauseWhenEmptyGuard(MinecraftServer server) {
            this.server = server;
        }

        private void suspend() {
            if (!(server instanceof DedicatedServer dedicated)) {
                return;
            }
            int current;
            try {
                current = dedicated.pauseWhenEmptySeconds();
            } catch (Throwable e) {
                ModdedIrisLog.warn("Iris pregen could not read pause-when-empty-seconds: {}", e.toString());
                return;
            }
            if (current <= 0) {
                return;
            }
            try {
                dedicated.setPauseWhenEmptySeconds(0);
            } catch (Throwable e) {
                refuse(current, e.toString());
                return;
            }
            int applied;
            try {
                applied = dedicated.pauseWhenEmptySeconds();
            } catch (Throwable e) {
                refuse(current, e.toString());
                return;
            }
            if (applied != 0) {
                refuse(current, "still " + applied + "s after the write");
                return;
            }
            suspendedFrom.set(current);
            armCrashRestore();
            ModdedIrisLog.info("Iris pregen: suspending pause-when-empty (was {}s), restored when the job ends", current);
        }

        private void restore() {
            disarmCrashRestore();
            restoreOnce("restored");
        }

        private void restoreOnce(String what) {
            int previous = suspendedFrom.getAndSet(NOT_SUSPENDED);
            if (previous == NOT_SUSPENDED || !(server instanceof DedicatedServer dedicated)) {
                return;
            }
            try {
                dedicated.setPauseWhenEmptySeconds(previous);
                ModdedIrisLog.info("Iris pregen: {} pause-when-empty ({}s)", what, previous);
            } catch (Throwable e) {
                ModdedIrisLog.error("Iris pregen could not restore pause-when-empty-seconds={}: {}. Set pause-when-empty-seconds={} in server.properties.",
                        previous, e.toString(), previous);
            }
        }

        private void armCrashRestore() {
            Thread hook = new Thread(() -> restoreOnce("restored on shutdown"), "iris-pregen-pause-restore");
            if (!crashRestoreHook.compareAndSet(null, hook)) {
                return;
            }
            try {
                Runtime.getRuntime().addShutdownHook(hook);
            } catch (IllegalStateException shuttingDown) {
                crashRestoreHook.compareAndSet(hook, null);
            }
        }

        private void disarmCrashRestore() {
            Thread hook = crashRestoreHook.getAndSet(null);
            if (hook == null) {
                return;
            }
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException shuttingDown) {
                // Already inside shutdown; the hook itself restores the value.
            }
        }

        /**
         * True when the server can still stop ticking under a running job.
         */
        private boolean pauseStillArmed() {
            if (suspendedFrom.get() != NOT_SUSPENDED || !(server instanceof DedicatedServer dedicated)) {
                return false;
            }
            try {
                return dedicated.pauseWhenEmptySeconds() > 0 && server.getPlayerCount() == 0;
            } catch (Throwable e) {
                return false;
            }
        }

        private void logStallHint() {
            if (!pauseStillArmed()) {
                return;
            }
            ModdedIrisLog.error("Iris pregen is timing out on an empty server while pause-when-empty-seconds is active: the paused server stops ticking. Set pause-when-empty-seconds=0 in server.properties, or keep a player online while pregenerating.");
        }

        private void refuse(int current, String reason) {
            ModdedIrisLog.error("Iris pregen could not suspend pause-when-empty-seconds={} ({}). The server stops ticking once empty, which stalls pregen: set pause-when-empty-seconds=0 in server.properties, or keep a player online while pregenerating.",
                    current, reason);
        }
    }

    private static final class FinalSaveRequest {
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private final AtomicBoolean active = new AtomicBoolean(true);

        private boolean claim() {
            return active.compareAndSet(true, false);
        }

        private void complete() {
            completion.complete(null);
        }

        private void fail(Throwable failure) {
            active.set(false);
            completion.completeExceptionally(failure);
        }

        private void cancel() {
            active.set(false);
            completion.complete(null);
        }

        private CompletableFuture<Void> completion() {
            return completion;
        }
    }
}
