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
import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.pregenerator.PregeneratorMethod;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.J;
import io.papermc.lib.PaperLib;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Future;

public class AsyncPregenMethod implements PregeneratorMethod {
    private final World world;
    private final MultiBurst burst;
    private final KList<Future<?>> future;
    private final Map<Chunk, Long> lastUse;

    public AsyncPregenMethod(World world, int threads) {
        if (!PaperLib.isPaper()) {
            throw new UnsupportedOperationException("Cannot use PaperAsync on non paper!");
        }

        this.world = world;
        burst = new MultiBurst("Iris Async Pregen", Thread.MIN_PRIORITY);
        future = new KList<>(256);
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
                    if (lastUseTime != null && M.ms() - lastUseTime >= 10000) {
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
        }
    }

    private void waitForChunksPartial(int maxWaiting) {
        while (future.size() > maxWaiting) {
            try {
                Future<?> i = future.remove(0);

                if (i == null) {
                    continue;
                }

                i.get();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private void waitForChunks() {
        for (Future<?> i : future.copy()) {
            if (i == null) {
                continue;
            }

            try {
                i.get();
                future.remove(i);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void init() {
        unloadAndSaveAllChunks();
    }

    @Override
    public String getMethod(int x, int z) {
        return "Async";
    }

    @Override
    public void close() {
        waitForChunks();
        unloadAndSaveAllChunks();
        burst.close();
    }

    @Override
    public void save() {
        waitForChunksPartial(256);
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
        if (future.size() > 256) {
            waitForChunksPartial(256);
        }
        future.add(burst.complete(() -> completeChunk(x, z, listener)));
    }

    @Override
    public Mantle getMantle() {
        if (IrisToolbelt.isIrisWorld(world)) {
            return IrisToolbelt.access(world).getEngine().getMantle().getMantle();
        }

        return null;
    }
}
