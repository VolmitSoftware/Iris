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

package art.arcane.iris.core.modules;

import com.volmit.geometry.BlockBox;
import com.volmit.mantle.CarvedVoid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The carved voids found in an area, to be read back in {@link com.volmit.mantle.MantleLayer#visit}'s order: ascending X,
 * then Y, then Z. The slices give them in no order, and a chunk holds thousands, so an area of a few chunks is laid out
 * in a flat grid of void kinds, which is read in order without sorting; a larger area packs each void into a number
 * that sorts.
 */
final class CarvedCells {
    /** The cells above which an area is sorted instead of laid out. */
    private static final int GRID_LIMIT_CELLS = 1 << 22;
    private static final int MAX_KINDS = 255;
    private static final int X_BITS = 18;
    private static final int Y_BITS = 15;
    private static final int Z_BITS = 18;
    private static final int KIND_BITS = 8;

    /** What a grid reuses from one visit to the next; a visit inside a visit lays its own grid out. */
    static final class Scratch {
        private byte[] kinds = new byte[0];
        private boolean inUse;
    }

    /** Where a void lies in the area, relative to its first block. */
    @FunctionalInterface
    interface CellVisitor {
        void accept(int relativeX, int relativeY, int relativeZ, CarvedVoid value);
    }

    private final int width;
    private final int height;
    private final int depth;
    private final byte[] grid;
    private final Scratch borrowed;
    private final List<CarvedVoid> kinds = new ArrayList<>(List.of(CarvedVoid.OPEN, CarvedVoid.FLUID, CarvedVoid.LAVA, CarvedVoid.AIR));
    private final Map<CarvedVoid, Integer> kindsWithBiome = new HashMap<>();
    private long[] sortable;
    private int sortableSize;

    CarvedCells(BlockBox area, Scratch scratch) {
        this.width = area.maxX() - area.minX();
        this.height = area.maxY() - area.minY();
        this.depth = area.maxZ() - area.minZ();
        long cells = (long) width * height * depth;
        if (cells > GRID_LIMIT_CELLS) {
            requireSortable(width, height, depth);
            this.grid = null;
            this.borrowed = null;
            this.sortable = new long[256];
            return;
        }
        if (scratch.inUse) {
            this.grid = new byte[(int) cells];
            this.borrowed = null;
            return;
        }
        if (scratch.kinds.length < cells) {
            scratch.kinds = new byte[(int) cells];
        }
        Arrays.fill(scratch.kinds, 0, (int) cells, (byte) 0);
        scratch.inUse = true;
        this.grid = scratch.kinds;
        this.borrowed = scratch;
    }

    void add(int relativeX, int relativeY, int relativeZ, CarvedVoid value) {
        int kind = kindOf(value);
        if (grid != null) {
            grid[(relativeX * height + relativeY) * depth + relativeZ] = (byte) kind;
            return;
        }
        if (sortableSize == sortable.length) {
            sortable = Arrays.copyOf(sortable, sortableSize * 2);
        }
        sortable[sortableSize++] = ((long) relativeX << (Y_BITS + Z_BITS + KIND_BITS))
                | ((long) relativeY << (Z_BITS + KIND_BITS))
                | ((long) relativeZ << KIND_BITS)
                | kind;
    }

    void forEachInOrder(CellVisitor visitor) {
        if (grid != null) {
            try {
                forEachInGrid(visitor);
            } finally {
                release();
            }
            return;
        }
        Arrays.sort(sortable, 0, sortableSize);
        for (int index = 0; index < sortableSize; index++) {
            long cell = sortable[index];
            visitor.accept((int) (cell >>> (Y_BITS + Z_BITS + KIND_BITS)),
                    (int) ((cell >>> (Z_BITS + KIND_BITS)) & mask(Y_BITS)),
                    (int) ((cell >>> KIND_BITS) & mask(Z_BITS)),
                    kinds.get((int) (cell & mask(KIND_BITS)) - 1));
        }
    }

    /** Gives the borrowed grid back; a visit does it by itself, a collection that failed must do it. */
    void release() {
        if (borrowed != null) {
            borrowed.inUse = false;
        }
    }

    private void forEachInGrid(CellVisitor visitor) {
        int cell = 0;
        for (int relativeX = 0; relativeX < width; relativeX++) {
            for (int relativeY = 0; relativeY < height; relativeY++) {
                for (int relativeZ = 0; relativeZ < depth; relativeZ++, cell++) {
                    int kind = grid[cell];
                    if (kind != 0) {
                        visitor.accept(relativeX, relativeY, relativeZ, kinds.get((kind & 0xff) - 1));
                    }
                }
            }
        }
    }

    /** The kind of a void, counted from 1: the four plain fills, then the voids that show a biome in the order they were found. */
    private int kindOf(CarvedVoid value) {
        if (value.biome().isEmpty()) {
            return value.fill().ordinal() + 1;
        }
        return kindsWithBiome.computeIfAbsent(value, ignored -> {
            if (kinds.size() >= MAX_KINDS) {
                throw new IllegalStateException("An area holds more kinds of carved void than a view reads at once, " + MAX_KINDS);
            }
            kinds.add(value);
            return kinds.size();
        });
    }

    private static void requireSortable(int width, int height, int depth) {
        if (width > mask(X_BITS) || height > mask(Y_BITS) || depth > mask(Z_BITS)) {
            throw new IllegalArgumentException("An area of " + width + " by " + height + " by " + depth
                    + " blocks is larger than a view of the carved voids reads at once");
        }
    }

    private static long mask(int bits) {
        return (1L << bits) - 1;
    }
}
