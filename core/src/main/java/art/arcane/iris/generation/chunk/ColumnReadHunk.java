/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.generation.chunk;

import art.arcane.volmlib.util.hunk.Hunk;

public interface ColumnReadHunk<T> {
    void readColumn(int x, int z, int yFromInclusive, int yToInclusive, T[] target, int targetOffset);

    @SuppressWarnings("unchecked")
    static <T> void readColumn(Hunk<T> hunk, int x, int z, int yFromInclusive, int yToInclusive, T[] target,
                                int targetOffset) {
        if (hunk instanceof ColumnReadHunk<?> reads) {
            ((ColumnReadHunk<T>) reads).readColumn(x, z, yFromInclusive, yToInclusive, target, targetOffset);
            return;
        }
        for (int y = yFromInclusive; y <= yToInclusive; y++) {
            target[targetOffset + y - yFromInclusive] = hunk.getRaw(x, y, z);
        }
    }
}
