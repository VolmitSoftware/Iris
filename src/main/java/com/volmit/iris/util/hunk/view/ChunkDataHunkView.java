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

import com.volmit.iris.util.data.B;
import com.volmit.iris.util.hunk.Hunk;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

@SuppressWarnings("ClassCanBeRecord")
public class ChunkDataHunkView implements Hunk<BlockData> {
    private static final BlockData AIR = B.getAir();
    private final ChunkData chunk;

    public ChunkDataHunkView(ChunkData chunk) {
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
    public void set(int x1, int y1, int z1, int x2, int y2, int z2, BlockData t) {
        if (t == null) {
            return;
        }

        chunk.setRegion(x1, y1 + chunk.getMinHeight(), z1, x2, y2 + chunk.getMinHeight(), z2, t);
    }


    public BlockData get(int x, int y, int z) {
        return getRaw(x, y, z);
    }

    public void set(int x, int y, int z, BlockData t) {
        setRaw(x, y, z, t);
    }

    @Override
    public void setRaw(int x, int y, int z, BlockData t) {
        if (t == null) {
            return;
        }

        try {

            chunk.setBlock(x, y + chunk.getMinHeight(), z, t);
        } catch (Throwable ignored) {

        }
    }

    @Override
    public BlockData getRaw(int x, int y, int z) {
        try {

            return chunk.getBlockData(x, y + chunk.getMinHeight(), z);
        } catch (Throwable e) {

        }

        return AIR;
    }
}
