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
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Chunk;
import org.bukkit.World;

public class MedievalPregenMethod implements PregeneratorMethod {
    private final World world;

    public MedievalPregenMethod(World world)
    {
        this.world = world;
    }

    private void unloadAndSaveAllChunks() {
        J.s(() -> {
            for(Chunk i : world.getLoadedChunks())
            {
                i.unload(true);
            }
        });
    }

    @Override
    public void init() {
        unloadAndSaveAllChunks();
    }

    @Override
    public void close() {
        unloadAndSaveAllChunks();
        world.save();
    }

    @Override
    public void save() {
        unloadAndSaveAllChunks();
        world.save();
    }

    @Override
    public boolean supportsRegions(int x, int z) {
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
    public void generateChunk(int x, int z) {
        world.getChunkAt(x, z);
    }
}
