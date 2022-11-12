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

import com.volmit.iris.util.hunk.storage.AtomicHunk;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

@SuppressWarnings("ClassCanBeRecord")
public class ChunkDataHunkHolder extends AtomicHunk<BlockData> {
    private static final BlockData AIR = Material.AIR.createBlockData();
    private final ChunkData chunk;

    public ChunkDataHunkHolder(ChunkData chunk) {
        super(16, chunk.getMaxHeight() - chunk.getMinHeight(), 16);
        this.chunk = chunk;
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
        return chunk.getMaxHeight() - chunk.getMinHeight();
    }

    @Override
    public BlockData getRaw(int x, int y, int z) {
        BlockData b = super.getRaw(x, y, z);

        return b != null ? b : AIR;
    }

    public void apply() {
        for (int i = 0; i < getHeight(); i++) {
            for (int j = 0; j < getWidth(); j++) {
                for (int k = 0; k < getDepth(); k++) {
                    BlockData b = super.getRaw(j, i, k);

                    if (b != null) {
                        chunk.setBlock(j, i + chunk.getMinHeight(), k, b);
                    }
                }
            }
        }
    }
}
