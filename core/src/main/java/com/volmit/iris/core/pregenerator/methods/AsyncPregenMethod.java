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

package com.volmit.iris.core.pregenerator.methods;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.pregenerator.PregeneratorMethod;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.J;
import io.papermc.lib.PaperLib;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncPregenMethod implements PregeneratorMethod {
    private static final AtomicInteger THREAD_COUNT = new AtomicInteger();
    private final World world;
    private final Executor executor;
    private final Semaphore semaphore;
    private final int threads;
    private final boolean urgent;
    private final Map<Chunk, Long> lastUse;

    public AsyncPregenMethod(World world, int unusedThreads) {
        if (!PaperLib.isPaper()) {
            throw new UnsupportedOperationException("Cannot use PaperAsync on non paper!");
        }

        this.world = world;
        this.executor = IrisSettings.get().getPregen().isUseTicketQueue() ? new TicketExecutor() : new ServiceExecutor();
        this.threads = IrisSettings.get().getPregen().getMaxConcurrency();
        this.semaphore = new Semaphore(this.threads, true);
        this.urgent = IrisSettings.get().getPregen().useHighPriority;
        this.lastUse = new KMap<>();
    }

    private void unloadAndSaveAllChunks() {
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

    @Override
    public void init() {
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
            semaphore.acquire();
        } catch (InterruptedException e) {
            return;
        }
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

    private class ServiceExecutor implements Executor {
        private final ExecutorService service = IrisSettings.get().getPregen().isUseVirtualThreads() ?
                Executors.newVirtualThreadPerTaskExecutor() :
                new MultiBurst("Iris Async Pregen");

        public void generate(int x, int z, PregenListener listener) {
            service.submit(() -> {
                try {
                    PaperLib.getChunkAtAsync(world, x, z, true, urgent).thenAccept((i) -> {
                        listener.onChunkGenerated(x, z);
                        listener.onChunkCleaned(x, z);
                        if (i == null) return;
                        lastUse.put(i, M.ms());
                    }).get();
                } catch (InterruptedException ignored) {
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                } finally {
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
                    .exceptionally(e -> {
                        Iris.reportError(e);
                        e.printStackTrace();
                        return null;
                    })
                    .thenAccept(i -> {
                        semaphore.release();
                        listener.onChunkGenerated(x, z);
                        listener.onChunkCleaned(x, z);
                        if (i == null) return;
                        lastUse.put(i, M.ms());
                    });
        }
    }
}
