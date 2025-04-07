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
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncPregenMethod implements PregeneratorMethod {
    private static final AtomicInteger THREAD_COUNT = new AtomicInteger();
    private final World world;
    private final MultiBurst burst;
    private final Semaphore semaphore;
    private final Map<Chunk, Long> lastUse;

    public AsyncPregenMethod(World world, int threads) {
        if (!PaperLib.isPaper()) {
            throw new UnsupportedOperationException("Cannot use PaperAsync on non paper!");
        }

        this.world = world;
        burst = new MultiBurst("Iris Async Pregen", Thread.MIN_PRIORITY);
        semaphore = new Semaphore(256);
        this.lastUse = new KMap<>();
    }

    private void unloadAndSaveAllChunks() {
        try {
            J.sfut(() -> {
                if (world == null) {
                    Iris.warn("World was null somehow...");
                    return;
                }

                for (Chunk i : new ArrayList<>(lastUse.keySet())) {
                    Long lastUseTime = lastUse.get(i);
                    if (!i.isLoaded() || (lastUseTime != null && M.ms() - lastUseTime >= 10000)) {
                        i.unload();
                        lastUse.remove(i);
                    }
                }
                world.save();
            }).get();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void completeChunk(int x, int z, PregenListener listener) {
        try {
            PaperLib.getChunkAtAsync(world, x, z, true).thenAccept((i) -> {
                lastUse.put(i, M.ms());
                listener.onChunkGenerated(x, z);
                listener.onChunkCleaned(x, z);
            }).get();
        } catch (InterruptedException ignored) {
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            semaphore.release();
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
        semaphore.acquireUninterruptibly(256);
        unloadAndSaveAllChunks();
        burst.close();
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
        burst.complete(() -> completeChunk(x, z, listener));
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
                Iris.warn("Failed to increase worker threads, please increase it manually to " + adjusted);
                Iris.warn("For more information see https://docs.papermc.io/paper/reference/global-configuration#chunk_system_worker_threads");
                if (e instanceof InvocationTargetException) e.printStackTrace();
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
                Iris.error("Failed to reset worker threads");
                e.printStackTrace();
            }
            return i;
        });
    }
}
