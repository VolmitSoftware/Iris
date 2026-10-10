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

package art.arcane.iris.engine.framework.structure.fit;

import com.volmit.block.BlockState;
import com.volmit.vanilla.TemplateCells;

import java.util.HashMap;
import java.util.Map;

/** The lowest template cell of each column of a piece, the cells a structure stands on. */
final class TemplateFootprint {
    private static final int PACKED_HORIZONTAL_BITS = 26;
    private static final int PACKED_VERTICAL_BITS = 12;
    private static final long PACKED_HORIZONTAL_MASK = (1L << PACKED_HORIZONTAL_BITS) - 1L;
    private static final long PACKED_VERTICAL_MASK = (1L << PACKED_VERTICAL_BITS) - 1L;
    private static final int PACKED_X_OFFSET = PACKED_VERTICAL_BITS + PACKED_HORIZONTAL_BITS;
    private static final int PACKED_Z_OFFSET = PACKED_VERTICAL_BITS;

    private TemplateFootprint() {
    }

    /** The lowest cell of every column, removed blocks included. */
    static Map<Long, Cell> lowestCells(TemplateCells cells, FitPiece piece) {
        Map<Long, Cell> lowest = new HashMap<>();
        for (int cell = 0; cell < cells.size(); cell++) {
            keepLowest(lowest, cells, piece, cell);
        }
        return lowest;
    }

    /** The lowest cell of every column that a structure can stand on. */
    static Map<Long, Cell> lowestSolidCells(TemplateCells cells, FitPiece piece, FitBlocks blocks) {
        Map<Long, Cell> lowest = new HashMap<>();
        for (int cell = 0; cell < cells.size(); cell++) {
            if (cells.removed(cell) || !blocks.isSolidBase(cells.state(cell))) {
                continue;
            }
            keepLowest(lowest, cells, piece, cell);
        }
        return lowest;
    }

    private static void keepLowest(Map<Long, Cell> lowest, TemplateCells cells, FitPiece piece, int cell) {
        int y = piece.cellY(cells, cell);
        long column = columnKey(cells.x(cell), cells.z(cell));
        Cell current = lowest.get(column);
        if (current == null || y < current.y()) {
            lowest.put(column, new Cell(cells.x(cell), y, cells.z(cell), cells.state(cell)));
        }
    }

    static long columnKey(int x, int z) {
        return (long) x << 32 ^ z & 0xffffffffL;
    }

    /** A block position packed the way the platform packs positions, so sets of them sort the platform's way. */
    static long packedPosition(int x, int y, int z) {
        return ((long) x & PACKED_HORIZONTAL_MASK) << PACKED_X_OFFSET
                | ((long) y & PACKED_VERTICAL_MASK)
                | ((long) z & PACKED_HORIZONTAL_MASK) << PACKED_Z_OFFSET;
    }

    static int packedX(long packed) {
        return (int) (packed << (64 - PACKED_X_OFFSET - PACKED_HORIZONTAL_BITS) >> (64 - PACKED_HORIZONTAL_BITS));
    }

    static int packedY(long packed) {
        return (int) (packed << (64 - PACKED_VERTICAL_BITS) >> (64 - PACKED_VERTICAL_BITS));
    }

    static int packedZ(long packed) {
        return (int) (packed << (64 - PACKED_Z_OFFSET - PACKED_HORIZONTAL_BITS) >> (64 - PACKED_HORIZONTAL_BITS));
    }

    /** A template cell at its world position; no state for a block the processors removed. */
    record Cell(int x, int y, int z, BlockState state) {
        boolean blocker() {
            return state == null;
        }
    }
}
