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

package com.volmit.iris.engine.lighting;

import com.bergerkiller.bukkit.common.collections.BlockFaceSet;

/**
 * Maps {@link BlockFaceSet} values to a 16x16x16 area of blocks
 */
public class BlockFaceSetSection {
    private final byte[] _maskData = new byte[4096];

    public void set(int x, int y, int z, BlockFaceSet faces) {
        _maskData[(y << 8) | (z << 4) | x] = (byte) faces.mask();
    }

    public BlockFaceSet get(int x, int y, int z) {
        return BlockFaceSet.byMask(_maskData[(y << 8) | (z << 4) | x]);
    }
}
