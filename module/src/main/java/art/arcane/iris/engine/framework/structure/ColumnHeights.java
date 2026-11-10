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

package art.arcane.iris.engine.framework.structure;

/**
 * A height for each world column (keys packed from X and Z), which only grows. It visits its columns in the order the
 * open-addressing table of the primitive map this replaces did, because the order in which a structure's foundations
 * are written to the mantle is part of what a world generated: a column map of the standard library would order them
 * differently.
 */
public final class ColumnHeights {
    private static final long PHI = 0x9E3779B97F4A7C15L;
    private static final int INITIAL_SLOTS = 32;
    private static final float LOAD_FACTOR = 0.75f;

    /** Receives one column and its height. */
    @FunctionalInterface
    public interface ColumnVisitor {
        void visit(long column, int height);
    }

    private final int missing;
    private long[] keys = new long[INITIAL_SLOTS + 1];
    private int[] heights = new int[INITIAL_SLOTS + 1];
    private int slots = INITIAL_SLOTS;
    private int mask = INITIAL_SLOTS - 1;
    private int maxFill = maxFill(INITIAL_SLOTS);
    private boolean hasColumnZero;
    private int size;

    /** @param missing what {@link #get} answers for a column that has no height */
    public ColumnHeights(int missing) {
        this.missing = missing;
    }

    public int size() {
        return size;
    }

    public boolean containsKey(long column) {
        if (column == 0L) {
            return hasColumnZero;
        }
        int slot = slotOf(column);
        while (keys[slot] != 0L) {
            if (keys[slot] == column) {
                return true;
            }
            slot = (slot + 1) & mask;
        }
        return false;
    }

    public int get(long column) {
        if (column == 0L) {
            return hasColumnZero ? heights[slots] : missing;
        }
        int slot = slotOf(column);
        while (keys[slot] != 0L) {
            if (keys[slot] == column) {
                return heights[slot];
            }
            slot = (slot + 1) & mask;
        }
        return missing;
    }

    public void put(long column, int height) {
        if (column == 0L) {
            if (!hasColumnZero) {
                hasColumnZero = true;
                grow();
            }
            heights[slots] = height;
            return;
        }
        int slot = slotOf(column);
        while (keys[slot] != 0L) {
            if (keys[slot] == column) {
                heights[slot] = height;
                return;
            }
            slot = (slot + 1) & mask;
        }
        keys[slot] = column;
        heights[slot] = height;
        grow();
    }

    /** Visits the columns in table order: the column 0 first, then the slots from the last to the first. */
    public void forEach(ColumnVisitor visitor) {
        int remaining = size;
        if (hasColumnZero) {
            visitor.visit(0L, heights[slots]);
            remaining--;
        }
        for (int slot = slots - 1; slot >= 0 && remaining > 0; slot--) {
            if (keys[slot] != 0L) {
                visitor.visit(keys[slot], heights[slot]);
                remaining--;
            }
        }
    }

    private void grow() {
        if (size++ >= maxFill) {
            rehash(slotsFor(size + 1));
        }
    }

    private void rehash(int newSlots) {
        long[] oldKeys = keys;
        int[] oldHeights = heights;
        int oldSlots = slots;
        int newMask = newSlots - 1;
        long[] newKeys = new long[newSlots + 1];
        int[] newHeights = new int[newSlots + 1];
        int source = oldSlots;
        for (int remaining = size - (hasColumnZero ? 1 : 0); remaining-- != 0; ) {
            while (oldKeys[--source] == 0L) {
                continue;
            }
            int slot = hash(oldKeys[source], newMask);
            while (newKeys[slot] != 0L) {
                slot = (slot + 1) & newMask;
            }
            newKeys[slot] = oldKeys[source];
            newHeights[slot] = oldHeights[source];
        }
        newHeights[newSlots] = oldHeights[oldSlots];
        keys = newKeys;
        heights = newHeights;
        slots = newSlots;
        mask = newMask;
        maxFill = maxFill(newSlots);
    }

    private int slotOf(long column) {
        return hash(column, mask);
    }

    private static int hash(long column, int tableMask) {
        long mixed = column * PHI;
        mixed ^= mixed >>> 32;
        return (int) (mixed ^ (mixed >>> 16)) & tableMask;
    }

    private static int maxFill(int tableSlots) {
        return Math.min((int) Math.ceil(tableSlots * LOAD_FACTOR), tableSlots - 1);
    }

    /** The table size for {@code expected} entries: the next power of two of {@code expected / loadFactor}, at least 2. */
    private static int slotsFor(int expected) {
        long wanted = Math.max(2L, (long) Math.ceil(expected / LOAD_FACTOR));
        return (int) Long.highestOneBit(wanted - 1) << 1;
    }
}
