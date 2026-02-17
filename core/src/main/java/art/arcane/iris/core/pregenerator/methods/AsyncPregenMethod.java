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

import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.pregenerator.PregenListener;
import art.arcane.iris.core.pregenerator.PregeneratorMethod;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.math.M;
import art.arcane.iris.util.parallel.MultiBurst;
import art.arcane.iris.util.scheduling.J;
import io.papermc.lib.PaperLib;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AsyncPregenMethod implements PregeneratorMethod {
    private static final AtomicInteger THREAD_COUNT = new AtomicInteger();
    private static final int FOLIA_MAX_CONCURRENCY = 32;
    private static final long CHUNK_LOAD_TIMEOUT_SECONDS = 15L;
    private final World world;
    private final Executor executor;
    private final Semaphore semaphore;
    private final int threads;
    private final boolean urgent;
    private final Map<Chunk, Long> lastUse;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong lastProgressAt = new AtomicLong(M.ms());
    private final AtomicLong lastPermitWaitLog = new AtomicLong(0L);

    public AsyncPregenMethod(World world, int unusedThreads) {
        if (!PaperLib.isPaper()) {
            throw new UnsupportedOperationException("Cannot use PaperAsync on non paper!");
        }

        this.world = world;
        if (J.isFolia()) {
            this.executor = new FoliaRegionExecutor();
        } else {
            boolean useTicketQueue = IrisSettings.get().getPregen().isUseTicketQueue();
            this.executor = useTicketQueue ? new TicketExecutor() : new ServiceExecutor();
        }
        int configuredThreads = IrisSettings.get().getPregen().getMaxConcurrency();
        if (J.isFolia()) {
            configuredThreads = Math.min(configuredThreads, FOLIA_MAX_CONCURRENCY);
        }
        this.threads = Math.max(1, configuredThreads);
        this.semaphore = new Semaphore(this.threads, true);
        this.urgent = IrisSettings.get().getPregen().useHighPriority;
        this.lastUse = new ConcurrentHashMap<>();
    }

    private void unloadAndSaveAllChunks() {
        if (J.isFolia()) {
            // Folia requires world/chunk mutations to be region-owned; periodic global unload/save is unsafe.
            lastUse.clear();
            return;
        }

        try {
            J.sfut(() -> {
                if (world == null) {
                    Iris.warn("World was null somehow...");
                    return;
                }

                long minTime = M.ms() - 10_000;
                lastUse.entrySet().removeIf(i -> {
                    final Chunk chunk = i.getKey();
                    final Long lastUseTime = i.getValue();
                    if (!chunk.isLoaded() || lastUseTime == null)
                        return true;
                    if (lastUseTime < minTime) {
                        chunk.unload();
                        return true;
                    }
                    return false;
                });
                world.save();
            }).get();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private Chunk onChunkFutureFailure(int x, int z, Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }

        if (root instanceof java.util.concurrent.TimeoutException) {
            Iris.warn("Timed out async pregen chunk load at " + x + "," + z + " after " + CHUNK_LOAD_TIMEOUT_SECONDS + "s. " + metricsSnapshot());
        } else {
            Iris.warn("Failed async pregen chunk load at " + x + "," + z + ". " + metricsSnapshot());
        }

        Iris.reportError(throwable);
        return null;
    }

    private String metricsSnapshot() {
        long stalledFor = Math.max(0L, M.ms() - lastProgressAt.get());
        return "world=" + world.getName()
                + " permits=" + semaphore.availablePermits() + "/" + threads
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
        } else {
            failed.incrementAndGet();
        }

        lastProgressAt.set(M.ms());
        int after = inFlight.decrementAndGet();
        if (after < 0) {
            inFlight.compareAndSet(after, 0);
        }
    }

    private void logPermitWaitIfNeeded(int x, int z, long waitedMs) {
        long now = M.ms();
        long last = lastPermitWaitLog.get();
        if (now - last < 5000L) {
            return;
        }

        if (lastPermitWaitLog.compareAndSet(last, now)) {
            Iris.warn("Async pregen waiting for permit at chunk " + x + "," + z + " waitedMs=" + waitedMs + " " + metricsSnapshot());
        }
    }

    @Override
    public void init() {
        Iris.info("Async pregen init: world=" + world.getName()
                + ", mode=" + (J.isFolia() ? "folia" : "paper")
                + ", threads=" + threads
                + ", urgent=" + urgent
                + ", timeout=" + CHUNK_LOAD_TIMEOUT_SECONDS + "s");
        unloadAndSaveAllChunks();
        increaseWorkerThreads();
    }

    @Override
    public String getMethod(int x, int z) {
        return "Async";
    }

    @Override
    public void close() {
        semaphore.acquireUninterruptibly(threads);
        unloadAndSaveAllChunks();
        executor.shutdown();
        resetWorkerThreads();
    }

    @Override
    public void save() {
        unloadAndSaveAllChunks();
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
        try {
            long waitStart = M.ms();
            while (!semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                logPermitWaitIfNeeded(x, z, Math.max(0L, M.ms() - waitStart));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        markSubmitted();
        executor.generate(x, z, listener);
    }

    @Override
    public Mantle getMantle() {
        if (IrisToolbelt.isIrisWorld(world)) {
            return IrisToolbelt.access(world).getEngine().getMantle().getMantle();
        }

        return null;
    }

    public static void increaseWorkerThreads() {
        THREAD_COUNT.updateAndGet(i -> {
            if (i > 0) return 1;
            var adjusted = IrisSettings.get().getConcurrency().getWorldGenThreads();
            try {
                var field = Class.forName("ca.spottedleaf.moonrise.common.util.MoonriseCommon").getDeclaredField("WORKER_POOL");
                var pool = field.get(null);
                var threads = ((Thread[]) pool.getClass().getDeclaredMethod("getCoreThreads").invoke(pool)).length;
                if (threads >= adjusted) return 0;

                pool.getClass().getDeclaredMethod("adjustThreadCount", int.class).invoke(pool, adjusted);
                return threads;
            } catch (Throwable e) {
                Iris.warn("Failed to increase worker threads, if you are on paper or a fork of it please increase it manually to " + adjusted);
                Iris.warn("For more information see https://docs.papermc.io/paper/reference/global-configuration#chunk_system_worker_threads");
                if (e instanceof InvocationTargetException) {
                    Iris.reportError(e);
                    e.printStackTrace();
                }
            }
            return 0;
        });
    }

    public static void resetWorkerThreads() {
        THREAD_COUNT.updateAndGet(i -> {
            if (i == 0) return 0;
            try {
                var field = Class.forName("ca.spottedleaf.moonrise.common.util.MoonriseCommon").getDeclaredField("WORKER_POOL");
                var pool = field.get(null);
                var method = pool.getClass().getDeclaredMethod("adjustThreadCount", int.class);
                method.invoke(pool, i);
                return 0;
            } catch (Throwable e) {
                Iris.reportError(e);
                Iris.error("Failed to reset worker threads");
                e.printStackTrace();
            }
            return i;
        });
    }

    private interface Executor {
        void generate(int x, int z, PregenListener listener);
        default void shutdown() {}
    }

    private class FoliaRegionExecutor implements Executor {
        @Override
        public void generate(int x, int z, PregenListener listener) {
            if (!J.runRegion(world, x, z, () -> PaperLib.getChunkAtAsync(world, x, z, true, urgent)
                    .orTimeout(CHUNK_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .whenComplete((chunk, throwable) -> {
                        boolean success = false;
                        try {
                            if (throwable != null) {
                                onChunkFutureFailure(x, z, throwable);
                                return;
                            }

                            listener.onChunkGenerated(x, z);
                            listener.onChunkCleaned(x, z);
                            if (chunk != null) {
                                lastUse.put(chunk, M.ms());
                            }
                            success = true;
                        } catch (Throwable e) {
                            Iris.reportError(e);
                            e.printStackTrace();
                        } finally {
                            markFinished(success);
                            semaphore.release();
                        }
                    }))) {
                markFinished(false);
                semaphore.release();
                Iris.warn("Failed to schedule Folia region pregen task at " + x + "," + z + ". " + metricsSnapshot());
            }
        }
    }

    private class ServiceExecutor implements Executor {
        private final ExecutorService service = IrisSettings.get().getPregen().isUseVirtualThreads() ?
                Executors.newVirtualThreadPerTaskExecutor() :
                new MultiBurst("Iris Async Pregen");

        public void generate(int x, int z, PregenListener listener) {
            service.submit(() -> {
                boolean success = false;
                try {
                    Chunk i = PaperLib.getChunkAtAsync(world, x, z, true, urgent)
                            .orTimeout(CHUNK_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .exceptionally(e -> onChunkFutureFailure(x, z, e))
                            .get();

                    listener.onChunkGenerated(x, z);
                    listener.onChunkCleaned(x, z);
                    if (i == null) {
                        return;
                    }
                    lastUse.put(i, M.ms());
                    success = true;
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                } finally {
                    markFinished(success);
                    semaphore.release();
                }
            });
        }

        @Override
        public void shutdown() {
            service.shutdown();
        }
    }

    private class TicketExecutor implements Executor {
        @Override
        public void generate(int x, int z, PregenListener listener) {
            PaperLib.getChunkAtAsync(world, x, z, true, urgent)
                    .orTimeout(CHUNK_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .exceptionally(e -> onChunkFutureFailure(x, z, e))
                    .thenAccept(i -> {
                        boolean success = false;
                        try {
                            listener.onChunkGenerated(x, z);
                            listener.onChunkCleaned(x, z);
                            if (i != null) {
                                lastUse.put(i, M.ms());
                            }
                            success = true;
                        } finally {
                            markFinished(success);
                            semaphore.release();
                        }
                    });
        }
    }
}
