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
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.J;
import io.papermc.lib.PaperLib;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.Objects;
import java.util.concurrent.Future;

public class AsyncPregenMethod implements PregeneratorMethod {
    private final World world;
    private final MultiBurst burst;
    private final KList<Future<?>> future;

    public AsyncPregenMethod(World world, int threads) {
        if (!PaperLib.isPaper()) {
            throw new UnsupportedOperationException("Cannot use PaperAsync on non paper!");
        }

        this.world = world;
        burst = MultiBurst.burst;
        future = new KList<>(1024);
    }

    private void unloadAndSaveAllChunks() {
        try {
            J.sfut(() -> {
                if (world == null) {
                    Iris.warn("World was null somehow...");
                    return;
                }

                for (Chunk i : world.getLoadedChunks()) {
                    i.unload(true);
                }
                world.save();
            }).get();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void completeChunk(int x, int z, PregenListener listener) {
        try {
            future.add(PaperLib.getChunkAtAsync(world, x, z, true).thenApply((i) -> {
                if (i == null) {

                }

                listener.onChunkGenerated(x, z);
                listener.onChunkCleaned(x, z);
                return 0;
            }));
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void waitForChunksPartial(int maxWaiting) {
        future.removeWhere(Objects::isNull);

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

        future.removeWhere(Objects::isNull);
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
