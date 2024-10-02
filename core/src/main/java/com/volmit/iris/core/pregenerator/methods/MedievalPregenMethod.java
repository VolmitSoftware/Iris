/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MedievalPregenMethod implements PregeneratorMethod {
    private final World world;
    private final KList<CompletableFuture<?>> futures;
    private final Map<Chunk, Long> lastUse;

    public MedievalPregenMethod(World world) {
        this.world = world;
        futures = new KList<>();
        this.lastUse = new KMap<>();
    }

    private void waitForChunks() {
        for (CompletableFuture<?> i : futures) {
            try {
                i.get();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        futures.clear();
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
                    if (lastUseTime != null && M.ms() - lastUseTime >= 10) {
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

    @Override
    public void init() {
        unloadAndSaveAllChunks();
    }

    @Override
    public void close() {
        unloadAndSaveAllChunks();
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
    public String getMethod(int x, int z) {
        return "Medieval";
    }

    @Override
    public void generateChunk(int x, int z, PregenListener listener) {
        if (futures.size() > IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism())) {
            waitForChunks();
        }

        listener.onChunkGenerating(x, z);
        futures.add(J.sfut(() -> {
            Chunk c = world.getChunkAt(x, z);
            lastUse.put(c, M.ms());
            listener.onChunkGenerated(x, z);
            listener.onChunkCleaned(x, z);
        }));
    }

    @Override
    public Mantle getMantle() {
        if (IrisToolbelt.isIrisWorld(world)) {
            return IrisToolbelt.access(world).getEngine().getMantle().getMantle();
        }

        return null;
    }

    @Override
    public World getWorld() {
        return world;
    }
}
