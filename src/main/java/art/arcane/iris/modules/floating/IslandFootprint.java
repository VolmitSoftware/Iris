/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modules.floating;

/**
 * Where an object stands, in its own block positions: the columns holding a solid block ({@link #column} packs each), the
 * tallest of them (most solid blocks, the lowest bottom on a tie) and the lowest solid block of that column.
 */
public record IslandFootprint(int tallestX, int tallestZ, int lowestSolidY, long[] columns) {
    /** The column {@code x}, {@code z} packed as {@link #columns} holds it. */
    public static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int columnX(long column) {
        return (int) (column >> 32);
    }

    public static int columnZ(long column) {
        return (int) (column & 0xFFFFFFFFL);
    }
}
