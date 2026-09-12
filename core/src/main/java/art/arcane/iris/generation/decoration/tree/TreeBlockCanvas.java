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

package art.arcane.iris.generation.decoration.tree;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TreeBlockCanvas {
    public record Vec(int x, int y, int z) {
    }

    public enum Role {
        TRUNK, SECONDARY_TRUNK, LEAF, SECONDARY_LEAF, DECORATOR
    }

    public enum Axis {
        NONE, X, Y, Z
    }

    public record Cell(Role role, Axis axis, boolean exposed, int decoratorIndex, String facing) {
    }

    private final Map<Vec, Cell> cells = new HashMap<>();
    private final Set<Vec> trunk = new HashSet<>();
    private final Set<Vec> leaf = new HashSet<>();

    public boolean has(int x, int y, int z) {
        return cells.containsKey(new Vec(x, y, z));
    }

    public Cell get(int x, int y, int z) {
        return cells.get(new Vec(x, y, z));
    }

    public void setTrunk(int x, int y, int z, Role role, Axis axis) {
        Vec v = new Vec(x, y, z);
        cells.put(v, new Cell(role, axis, false, -1, null));
        trunk.add(v);
        leaf.remove(v);
    }

    public boolean setLeaf(int x, int y, int z, Role role) {
        Vec v = new Vec(x, y, z);
        if (cells.containsKey(v)) {
            return false;
        }
        cells.put(v, new Cell(role, Axis.NONE, false, -1, null));
        leaf.add(v);
        return true;
    }

    public boolean setDecor(int x, int y, int z, int decoratorIndex, String facing) {
        Vec v = new Vec(x, y, z);
        if (cells.containsKey(v)) {
            return false;
        }
        cells.put(v, new Cell(Role.DECORATOR, Axis.NONE, false, decoratorIndex, facing));
        return true;
    }

    public void markExposed(int x, int y, int z) {
        Vec v = new Vec(x, y, z);
        Cell c = cells.get(v);
        if (c != null && !c.exposed()) {
            cells.put(v, new Cell(c.role(), c.axis(), true, c.decoratorIndex(), c.facing()));
        }
    }

    public Map<Vec, Cell> getCells() {
        return cells;
    }

    public Set<Vec> getTrunk() {
        return trunk;
    }

    public Set<Vec> getLeaf() {
        return leaf;
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }
}
