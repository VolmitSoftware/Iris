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

package art.arcane.iris.studio.map;

import java.util.Collection;

/**
 * The part of a tile left out for finer tiles inside it to fill. Finer tiles are at most {@link #MOST_LEVELS_FINER}
 * levels finer, so a hole always follows the edges of square cells of {@link #CELL_COLUMNS} columns, and no face crosses
 * one: whether a face shows is the same along all of it, which lets the graphics card decide it per face, every frame.
 */
final class TerrainHole {
    static final int MOST_LEVELS_FINER = 4;
    static final int CELLS_ACROSS = 1 << MOST_LEVELS_FINER;
    static final int CELL_COLUMNS = TerrainTile.COLUMNS / CELLS_ACROSS;
    static final int WORDS = CELLS_ACROSS * CELLS_ACROSS / Integer.SIZE;
    static final TerrainHole NONE = new TerrainHole(new int[WORDS]);
    private static final int ALL_CELLS_OF_WORD = -1;

    // One bit per cell, row by row.
    private final int[] hiddenCells;

    private TerrainHole(int[] hiddenCells) {
        this.hiddenCells = hiddenCells;
    }

    /**
     * The hole in {@code tile} that these finer tiles inside it fill. A tile finer than a cell can't be cut out, so it
     * leaves no hole and only overlaps the coarser ground.
     */
    static TerrainHole of(TerrainTile tile, Collection<TerrainTile> fillers) {
        if (fillers.isEmpty()) {
            return NONE;
        }
        int[] hidden = new int[WORDS];
        for (TerrainTile filler : fillers) {
            ColumnArea area = tile.columnsUnder(filler);
            if (area.toColumn() - area.fromColumn() < CELL_COLUMNS) {
                continue;
            }
            for (int row = area.fromRow(); row < area.toRow(); row += CELL_COLUMNS) {
                for (int column = area.fromColumn(); column < area.toColumn(); column += CELL_COLUMNS) {
                    int cell = cellOf(column, row);
                    hidden[cell / Integer.SIZE] |= 1 << (cell % Integer.SIZE);
                }
            }
        }
        return new TerrainHole(hidden);
    }

    private static int cellOf(int column, int row) {
        return row / CELL_COLUMNS * CELLS_ACROSS + column / CELL_COLUMNS;
    }

    // Columns beyond the tile's edge are never hidden: the tile's own edge sides already reach the floor.
    boolean hides(int column, int row) {
        if (column < 0 || row < 0 || column >= TerrainTile.COLUMNS || row >= TerrainTile.COLUMNS) {
            return false;
        }
        int cell = cellOf(column, row);
        return (hiddenCells[cell / Integer.SIZE] & 1 << (cell % Integer.SIZE)) != 0;
    }

    boolean hidesAll() {
        for (int word : hiddenCells) {
            if (word != ALL_CELLS_OF_WORD) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a face shows with this hole: none of a hidden column's do, and a column next to the hole shows its hole
     * wall, reaching down to the floor, instead of its usual side. The graphics card's face shader decides the same.
     */
    boolean shows(BlockFaces faces, int face) {
        int column = faces.firstColumn(face);
        int row = faces.firstRow(face);
        if (hides(column, row)) {
            return false;
        }
        BlockFaces.Facing facing = faces.facing(face);
        if (facing == BlockFaces.Facing.TOP) {
            return true;
        }
        return faces.isHoleWall(face) == hides(column + facing.normalX, row + facing.normalZ);
    }

    int[] hiddenCells() {
        return hiddenCells.clone();
    }
}
