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

package art.arcane.iris.generation.decoration.formation;

import art.arcane.volmlib.util.math.Vector3i;

import java.util.HashMap;
import java.util.Map;

public final class FormationCanvas {
    public enum Role {
        BODY,
        CAP
    }

    private final Map<Vector3i, Role> cells = new HashMap<>();

    public void setBody(int x, int y, int z) {
        Vector3i key = new Vector3i(x, y, z);
        if (cells.get(key) == Role.CAP) {
            return;
        }
        cells.put(key, Role.BODY);
    }

    public void setCap(int x, int y, int z) {
        cells.put(new Vector3i(x, y, z), Role.CAP);
    }

    public void remove(int x, int y, int z) {
        cells.remove(new Vector3i(x, y, z));
    }

    public boolean has(int x, int y, int z) {
        return cells.containsKey(new Vector3i(x, y, z));
    }

    public Map<Vector3i, Role> getCells() {
        return cells;
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }
}
