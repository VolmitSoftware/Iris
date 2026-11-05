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
package art.arcane.iris.engine.procedural.part;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The blocks the parts of one variant claim, before materials turn them into block data: each with the role of the
 * part that placed it, how far along that part it sits, and the axis a log there would follow.
 */
final class PartCanvas {
    /** Enough for any sensible object; a typo asking for more stops growing instead of baking for minutes. */
    static final int MAXIMUM_BLOCKS = 1_500_000;

    enum Axis {
        NONE, X, Y, Z;

        /** The axis a log along {@code direction} lies on, leaning toward upright as trees do. */
        static Axis along(double[] direction) {
            double x = Math.abs(direction[0]);
            double y = Math.abs(direction[1]) * 1.8;
            double z = Math.abs(direction[2]);
            if (y >= x && y >= z) {
                return Y;
            }
            return x >= z ? X : Z;
        }
    }

    /** @param part the {@link PartOutline} id of the part that placed the block */
    record Cell(int role, float along, Axis axis, int part) {
    }

    /** The end of a stem, where tip decorations go. */
    record Tip(int x, int y, int z, double[] heading, int role) {
    }

    private final Map<Long, Cell> cells = new HashMap<>();
    private final List<Tip> tips = new ArrayList<>();
    private final List<String> roles = new ArrayList<>();
    private boolean full;

    int role(String name) {
        int index = roles.indexOf(name);
        if (index >= 0) {
            return index;
        }
        roles.add(name);
        return roles.size() - 1;
    }

    String roleName(int role) {
        return roles.get(role);
    }

    void place(int x, int y, int z, PartMode mode, Cell cell) {
        long key = key(x, y, z);
        switch (mode) {
            case ADD -> {
                if (!full || cells.containsKey(key)) {
                    cells.put(key, cell);
                }
            }
            case FILL -> {
                if (!full && !cells.containsKey(key)) {
                    cells.put(key, cell);
                }
            }
            case CARVE -> cells.remove(key);
        }
        full = cells.size() >= MAXIMUM_BLOCKS;
    }

    boolean has(int x, int y, int z) {
        return cells.containsKey(key(x, y, z));
    }

    Cell get(long key) {
        return cells.get(key);
    }

    Map<Long, Cell> cells() {
        return Collections.unmodifiableMap(cells);
    }

    void addTip(Tip tip) {
        tips.add(tip);
    }

    List<Tip> tips() {
        return Collections.unmodifiableList(tips);
    }

    boolean isEmpty() {
        return cells.isEmpty();
    }

    boolean isFull() {
        return full;
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF)) | ((long) (y & 0x1FFFFF) << 21) | ((long) (z & 0x1FFFFF) << 42);
    }

    static int x(long key) {
        return signed((int) (key & 0x1FFFFF));
    }

    static int y(long key) {
        return signed((int) ((key >>> 21) & 0x1FFFFF));
    }

    static int z(long key) {
        return signed((int) ((key >>> 42) & 0x1FFFFF));
    }

    private static int signed(int value) {
        return (value << 11) >> 11;
    }
}
