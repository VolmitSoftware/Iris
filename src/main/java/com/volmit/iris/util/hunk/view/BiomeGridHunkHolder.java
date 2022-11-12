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
import com.volmit.iris.util.hunk.storage.AtomicHunk;
import lombok.Getter;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;

@SuppressWarnings("ClassCanBeRecord")
public class BiomeGridHunkHolder extends AtomicHunk<Biome> {
    @Getter
    private final BiomeGrid chunk;
    private final int minHeight;
    private final int maxHeight;

    public BiomeGridHunkHolder(BiomeGrid chunk, int minHeight, int maxHeight) {
        super(16, maxHeight - minHeight, 16);
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

    public void apply() {
        for (int i = 0; i < getHeight(); i++) {
            for (int j = 0; j < getWidth(); j++) {
                for (int k = 0; k < getDepth(); k++) {
                    Biome b = super.getRaw(j, i, k);

                    if (b != null) {
                        chunk.setBiome(j, i + minHeight, k, b);
                    }
                }
            }
        }
    }

    @Override
    public Biome getRaw(int x, int y, int z) {
        Biome b = super.getRaw(x, y, z);

        return b != null ? b : Biome.PLAINS;
    }

    public void forceBiomeBaseInto(int x, int y, int z, Object somethingVeryDirty) {
        if (chunk instanceof LinkedTerrainChunk) {
            INMS.get().forceBiomeInto(x, y + minHeight, z, somethingVeryDirty, ((LinkedTerrainChunk) chunk).getRawBiome());
            return;
        }
        INMS.get().forceBiomeInto(x, y + minHeight, z, somethingVeryDirty, chunk);
    }
}
