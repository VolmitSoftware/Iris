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

package com.volmit.iris.util.data.palette;

public final class QuartPos {
    public static final int BITS = 2;

    public static final int SIZE = 4;

    private static final int SECTION_TO_QUARTS_BITS = 2;

    public static int fromBlock(int var0) {
        return var0 >> 2;
    }

    public static int toBlock(int var0) {
        return var0 << 2;
    }

    public static int fromSection(int var0) {
        return var0 << 2;
    }

    public static int toSection(int var0) {
        return var0 >> 2;
    }
}
