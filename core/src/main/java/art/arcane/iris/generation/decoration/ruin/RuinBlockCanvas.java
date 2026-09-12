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

package art.arcane.iris.generation.decoration.ruin;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.Vector3i;

import java.util.HashMap;
import java.util.Map;

final class RuinBlockCanvas {
    enum Role {
        STRUCTURE,
        ACCENT
    }

    static final class Cell {
        private Role role;
        private boolean structural;
        private PlatformBlockState accentData;

        Cell(Role role, boolean structural) {
            this.role = role;
            this.structural = structural;
        }

        Role role() {
            return role;
        }

        boolean structural() {
            return structural;
        }

        PlatformBlockState accentData() {
            return accentData;
        }

        void promote(Role next, boolean structuralNext) {
            this.role = next;
            this.structural = this.structural || structuralNext;
        }

        void makeAccent(PlatformBlockState data) {
            this.role = Role.ACCENT;
            this.structural = false;
            this.accentData = data;
        }
    }

    private final Map<Vector3i, Cell> cells = new HashMap<>();

    Map<Vector3i, Cell> cells() {
        return cells;
    }

    void set(int x, int y, int z, Role role, boolean structural) {
        Vector3i key = new Vector3i(x, y, z);
        Cell existing = cells.get(key);
        if (existing == null) {
            cells.put(key, new Cell(role, structural));
        } else {
            existing.promote(role, structural);
        }
    }

    void accent(int x, int y, int z, PlatformBlockState data) {
        if (data == null) {
            return;
        }
        cells.put(new Vector3i(x, y, z), accentCell(data));
    }

    private static Cell accentCell(PlatformBlockState data) {
        Cell cell = new Cell(Role.ACCENT, false);
        cell.makeAccent(data);
        return cell;
    }

    boolean has(int x, int y, int z) {
        return cells.containsKey(new Vector3i(x, y, z));
    }

    Cell get(int x, int y, int z) {
        return cells.get(new Vector3i(x, y, z));
    }

    void remove(int x, int y, int z) {
        cells.remove(new Vector3i(x, y, z));
    }
}
