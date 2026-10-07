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

package art.arcane.iris.modded;


import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeChunkSystemProbe;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeChunkSystemProbe.ChunkSystem;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the Iris generation pool and the single decision of whether this loader already runs chunk
 * generation on its own worker threads.
 *
 * <p>The parallel-chunk-system probe is a two-stage check: {@link Class#forName} presence gates it
 * (never a version string), then the mod's own configuration is read reflectively to confirm the
 * feature is actually enabled. Any reflection failure resolves to "not parallel", which keeps
 * generation on the Iris pool - the safe side, since an extra hop only costs throughput while a
 * missing hop serializes generation on the loader's chunk threads.
 */
public final class ModdedGenPool {
    private static final long SHUTDOWN_DRAIN_MILLIS = 2_000L;
    private static final AtomicInteger GEN_THREAD_SEQ = new AtomicInteger();
    private static final ChunkSystem CHUNK_SYSTEM = detectChunkSystem();
    private static ExecutorService generationPool = createGenPool();

    private ModdedGenPool() {
    }

    /**
     * True when the loader's chunk system already generates off the server thread, so Iris must not
     * add its own pool hop.
     */
    public static boolean parallelChunkSystem() {
        return CHUNK_SYSTEM.parallel();
    }

    /**
     * Terse description of the detected chunk system, for one-line diagnostics.
     */
    public static String describeChunkSystem() {
        return CHUNK_SYSTEM.description();
    }

    static synchronized ExecutorService pool() {
        if (generationPool.isShutdown()) {
            throw new RejectedExecutionException("Iris gen pool is shut down");
        }
        return generationPool;
    }

    static synchronized void start() {
        if (!generationPool.isShutdown()) {
            return;
        }
        if (!generationPool.isTerminated()) {
            throw new IllegalStateException("Iris gen pool cannot restart while prior workers are still active");
        }
        generationPool = createGenPool();
    }

    static void shutdown() {
        ExecutorService pool;
        synchronized (ModdedGenPool.class) {
            pool = generationPool;
            if (pool.isTerminated()) {
                return;
            }
            pool.shutdown();
        }
        try {
            if (pool.awaitTermination(SHUTDOWN_DRAIN_MILLIS, TimeUnit.MILLISECONDS)) {
                return;
            }
            ModdedIrisLog.debug("Iris gen pool did not drain in {}ms, forcing shutdown", SHUTDOWN_DRAIN_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.shutdownNow();
    }

    private static ChunkSystem detectChunkSystem() {
        ChunkSystem detected = new NativeChunkSystemProbe(ModdedGenPool.class.getClassLoader(), ModdedIrisLog::debug).detect();
        ModdedIrisLog.info("Iris chunk system: {} (parallel={}, generation on {})",
                detected.description(),
                detected.parallel() ? "yes" : "no",
                detected.parallel() ? "loader threads" : "Iris gen pool");
        return detected;
    }

    private static ExecutorService createGenPool() {
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                threads, threads, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "Iris ModGen-" + GEN_THREAD_SEQ.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

}
