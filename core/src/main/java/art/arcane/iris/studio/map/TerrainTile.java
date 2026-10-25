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

import java.util.List;

/**
 * A square of terrain sampled in one go: {@link #COLUMNS} by {@link #COLUMNS} columns, each {@code 2^level} blocks
 * wide. Tiles of a level line up edge to edge, and each one covers exactly four of the level below, so finer tiles can
 * stand in for parts of a coarser one.
 */
record TerrainTile(int level, int tileX, int tileZ) {
    static final int COLUMNS = 128;

    static TerrainTile containing(double worldX, double worldZ, int level) {
        long sizeBlocks = sizeBlocks(level);
        return new TerrainTile(level, (int) Math.floorDiv((long) Math.floor(worldX), sizeBlocks),
                (int) Math.floorDiv((long) Math.floor(worldZ), sizeBlocks));
    }

    static long sizeBlocks(int level) {
        return (long) COLUMNS << level;
    }

    int step() {
        return 1 << level;
    }

    long sizeBlocks() {
        return sizeBlocks(level);
    }

    long originX() {
        return tileX * sizeBlocks();
    }

    long originZ() {
        return tileZ * sizeBlocks();
    }

    TerrainGrid grid() {
        return new TerrainGrid((int) originX(), (int) originZ(), COLUMNS, COLUMNS, step());
    }

    List<TerrainTile> children() {
        int finer = level - 1;
        int childX = tileX * 2;
        int childZ = tileZ * 2;
        return List.of(new TerrainTile(finer, childX, childZ), new TerrainTile(finer, childX + 1, childZ),
                new TerrainTile(finer, childX, childZ + 1), new TerrainTile(finer, childX + 1, childZ + 1));
    }

    /**
     * This tile's columns that a finer tile inside it covers.
     */
    ColumnArea columnsUnder(TerrainTile finer) {
        int fromColumn = (int) ((finer.originX() - originX()) / step());
        int fromRow = (int) ((finer.originZ() - originZ()) / step());
        int across = (int) (finer.sizeBlocks() / step());
        return new ColumnArea(fromColumn, fromRow, fromColumn + across, fromRow + across);
    }
}
