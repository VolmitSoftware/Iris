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

/**
 * A block volume that knows, per column, the highest y it holds a value for. Every cell above that y reads as
 * unwritten, so column scans can stop there without changing what they observe.
 */
public interface ColumnExtent {
    int highestStoredY(int x, int z);

    static int highestStoredY(Hunk<?> hunk, int x, int z) {
        return hunk instanceof ColumnExtent extent ? extent.highestStoredY(x, z) : hunk.getHeight() - 1;
    }
}
