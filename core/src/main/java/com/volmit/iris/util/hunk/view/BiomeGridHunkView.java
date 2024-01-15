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

package com.volmit.iris.util.hunk.view;

import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.data.chunk.LinkedTerrainChunk;
import com.volmit.iris.util.hunk.Hunk;
import lombok.Getter;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;

@SuppressWarnings("ClassCanBeRecord")
public class BiomeGridHunkView implements Hunk<Biome> {
    @Getter
    private final BiomeGrid chunk;
    private final int minHeight;
    private final int maxHeight;
    private int highest = -1000;

    public BiomeGridHunkView(BiomeGrid chunk, int minHeight, int maxHeight) {
        this.chunk = chunk;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
    }

    @Override
    public int getWidth() {
        return 16;
    }

    @Override
    public int getDepth() {
        return 16;
    }

    @Override
    public int getHeight() {
        return maxHeight - minHeight;
    }

    @Override
    public void setRaw(int x, int y, int z, Biome t) {
        chunk.setBiome(x, y + minHeight, z, t);

        if (y > highest) {
            highest = y;
        }
    }

    @Override
    public Biome getRaw(int x, int y, int z) {
        return chunk.getBiome(x, y + minHeight, z);
    }

    public void forceBiomeBaseInto(int x, int y, int z, Object somethingVeryDirty) {
        if (chunk instanceof LinkedTerrainChunk) {
            INMS.get().forceBiomeInto(x, y + minHeight, z, somethingVeryDirty, ((LinkedTerrainChunk) chunk).getRawBiome());
            return;
        }
        INMS.get().forceBiomeInto(x, y + minHeight, z, somethingVeryDirty, chunk);
    }
}
