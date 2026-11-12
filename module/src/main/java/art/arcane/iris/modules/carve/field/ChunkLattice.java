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

package art.arcane.iris.modules.carve.field;

/**
 * The points one chunk samples its cave fields at: every fourth block along each axis, over the heights a cave may
 * carve. A shape reads its fields at these points only and interpolates between them, which is where the carver's
 * speed comes from. Points sit at multiples of four in world coordinates, so neighboring chunks sample the very same
 * points along their shared border and their caves meet without a seam.
 */
public final class ChunkLattice {
    public static final int CELL_BLOCKS = 4;
    public static final int CELLS_PER_CHUNK_AXIS = 16 / CELL_BLOCKS;
    private static final int POINTS_PER_AXIS = CELLS_PER_CHUNK_AXIS + 1;
    private static final int POINTS_PER_LAYER = POINTS_PER_AXIS * POINTS_PER_AXIS;

    private final int originX;
    private final int originZ;
    private final int baseY;
    private final int cellsY;

    private ChunkLattice(int originX, int originZ, int baseY, int cellsY) {
        this.originX = originX;
        this.originZ = originZ;
        this.baseY = baseY;
        this.cellsY = cellsY;
    }

    /** The lattice of the chunk whose first block is {@code originX}, {@code originZ}, covering heights {@code minY} to {@code maxY}. */
    public static ChunkLattice covering(int originX, int originZ, int minY, int maxY) {
        int baseY = Math.floorDiv(minY, CELL_BLOCKS) * CELL_BLOCKS;
        int cellsY = Math.max(1, Math.floorDiv(maxY - baseY, CELL_BLOCKS) + 1);
        return new ChunkLattice(originX, originZ, baseY, cellsY);
    }

    public int originX() {
        return originX;
    }

    public int originZ() {
        return originZ;
    }

    public int baseY() {
        return baseY;
    }

    public int cellsY() {
        return cellsY;
    }

    public int pointCount() {
        return POINTS_PER_LAYER * (cellsY + 1);
    }

    /** Takes the lattice's points one by one, each with its place in a list of {@link #pointCount()} values. */
    @FunctionalInterface
    interface PointReader {
        void read(int index, int x, int y, int z);
    }

    void forEachPoint(PointReader reader) {
        int index = 0;
        for (int layer = 0; layer <= cellsY; layer++) {
            int y = baseY + (layer * CELL_BLOCKS);
            for (int pointX = 0; pointX < POINTS_PER_AXIS; pointX++) {
                int x = originX + (pointX * CELL_BLOCKS);
                for (int pointZ = 0; pointZ < POINTS_PER_AXIS; pointZ++) {
                    reader.read(index++, x, y, originZ + (pointZ * CELL_BLOCKS));
                }
            }
        }
    }

    int pointIndex(int pointX, int layer, int pointZ) {
        return (layer * POINTS_PER_LAYER) + (pointX * POINTS_PER_AXIS) + pointZ;
    }
}
