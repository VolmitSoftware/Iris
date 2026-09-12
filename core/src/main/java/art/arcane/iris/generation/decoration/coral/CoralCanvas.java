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

package art.arcane.iris.generation.decoration.coral;

import java.util.HashMap;
import java.util.Map;

public final class CoralCanvas {
    public enum Role {
        STRUCTURE,
        TIP
    }

    private static final long BIAS = 1L << 20;
    private static final long MASK = (1L << 21) - 1L;

    private final Map<Long, Role> cells = new HashMap<>();

    public void set(int x, int y, int z, Role role) {
        if (y < 0) {
            return;
        }
        long key = encode(x, y, z);
        Role existing = cells.get(key);
        if (existing == Role.TIP && role == Role.STRUCTURE) {
            return;
        }
        cells.put(key, role);
    }

    public Map<Long, Role> getCells() {
        return cells;
    }

    public static long encode(int x, int y, int z) {
        long ex = (x + BIAS) & MASK;
        long ey = (y + BIAS) & MASK;
        long ez = (z + BIAS) & MASK;
        return (ex << 42) | (ey << 21) | ez;
    }

    public static int[] decode(long key) {
        int ez = (int) ((key & MASK) - BIAS);
        int ey = (int) (((key >> 21) & MASK) - BIAS);
        int ex = (int) (((key >> 42) & MASK) - BIAS);
        return new int[]{ex, ey, ez};
    }
}
