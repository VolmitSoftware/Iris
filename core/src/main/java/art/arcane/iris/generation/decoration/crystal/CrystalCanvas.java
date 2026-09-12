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

package art.arcane.iris.generation.decoration.crystal;

import art.arcane.volmlib.util.math.Vector3i;

import java.util.HashMap;
import java.util.Map;

public final class CrystalCanvas {
    private final Map<Vector3i, CrystalRole> cells = new HashMap<>();

    public void set(int x, int y, int z, CrystalRole role) {
        Vector3i key = new Vector3i(x, y, z);
        CrystalRole existing = cells.get(key);
        if (existing == null || rank(role) > rank(existing)) {
            cells.put(key, role);
        }
    }

    public boolean has(int x, int y, int z) {
        return cells.containsKey(new Vector3i(x, y, z));
    }

    public Map<Vector3i, CrystalRole> getCells() {
        return cells;
    }

    private static int rank(CrystalRole role) {
        return switch (role) {
            case BASE -> 0;
            case SHARD -> 1;
            case TIP -> 2;
        };
    }
}
