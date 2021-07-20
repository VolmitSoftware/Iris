/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.pregenerator.PregeneratorMethod;
import com.volmit.iris.engine.parallel.MultiBurst;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.scheduling.J;
import io.papermc.lib.PaperLib;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class PaperAsyncPregenMethod implements PregeneratorMethod {
    private final World world;
    private final MultiBurst burst;
    private final KList<CompletableFuture<?>> future;

    public PaperAsyncPregenMethod(World world, int threads)
    {
        if(!PaperLib.isPaper())
        {
            throw new UnsupportedOperationException("Cannot use PaperAsync on non paper!");
        }

        this.world = world;
        burst = new MultiBurst("Iris PaperAsync Pregenerator", 6, threads);
        future = new KList<>(1024);
    }

    private void unloadAndSaveAllChunks() {
        try {
            J.sfut(() -> {
                for(Chunk i : world.getLoadedChunks())
                {
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
            PaperLib.getChunkAtAsync(world, x, z, true).get();
            listener.onChunkGenerated(x, z);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void waitForChunks()
    {
        for(CompletableFuture<?> i : future)
        {
            try {
                i.get();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        future.clear();
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
        burst.shutdownAndAwait();
        unloadAndSaveAllChunks();
    }

    @Override
    public void save() {
        waitForChunks();
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
        if(future.size() > 16)
        {
            waitForChunks();
        }

        listener.onChunkGenerating(x, z);
        future.add(burst.complete(() -> completeChunk(x, z, listener)));
    }
}
