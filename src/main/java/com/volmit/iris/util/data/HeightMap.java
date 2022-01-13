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

package com.volmit.iris.util.data;

import java.util.Arrays;

public class HeightMap {
    private final byte[] height;

    public HeightMap() {
        height = new byte[256];
        Arrays.fill(height, Byte.MIN_VALUE);
    }

    public void setHeight(int x, int z, int h) {
        height[x * 16 + z] = (byte) (h + Byte.MIN_VALUE);
    }

    public int getHeight(int x, int z) {
        return height[x * 16 + z] - Byte.MIN_VALUE;
    }
}
