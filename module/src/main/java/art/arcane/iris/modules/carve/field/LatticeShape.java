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

import java.util.Arrays;

/**
 * A cave shape that reads smooth fields on a chunk's lattice and decides block by block from their interpolated
 * values. The fields are interpolated, never the decision, so a passage thinner than a lattice cell survives: its
 * fields still cross zero between the points.
 */
abstract class LatticeShape {
    private static final ThreadLocal<Pass> PASS = ThreadLocal.withInitial(Pass::new);
    private static final double CELL_SHARE = 1D / ChunkLattice.CELL_BLOCKS;

    /** How many fields the shape reads. {@link Pass#cell} numbers them from 0. */
    protected abstract int fieldCount();

    /** Reads every field of the shape at one block into {@code values}, one value per field. */
    protected abstract void readFields(int x, int y, int z, double[] values);

    /** Whether the shape can carve anywhere in the cell the pass has loaded, whatever the limits allow. */
    protected abstract boolean mayCarveIn(Pass pass);

    /** Whether the shape carves the block at these shares of the way across the loaded cell. */
    protected abstract boolean carvesAt(Pass pass, double shareX, double shareY, double shareZ, double openness, int column);

    final void carve(ChunkLattice lattice, CaveLimits limits, CarveSink sink) {
        Pass pass = PASS.get();
        pass.begin(lattice, this);
        for (int cellY = 0; cellY < lattice.cellsY(); cellY++) {
            int bottomY = lattice.baseY() + (cellY * ChunkLattice.CELL_BLOCKS);
            if (bottomY > limits.highestY() || bottomY + ChunkLattice.CELL_BLOCKS <= limits.lowestY()) {
                continue;
            }
            for (int cellX = 0; cellX < ChunkLattice.CELLS_PER_CHUNK_AXIS; cellX++) {
                for (int cellZ = 0; cellZ < ChunkLattice.CELLS_PER_CHUNK_AXIS; cellZ++) {
                    if (limits.cellIsClosed(cellX, cellZ, bottomY)) {
                        continue;
                    }
                    pass.load(cellX, cellY, cellZ);
                    if (mayCarveIn(pass)) {
                        carveCell(pass, limits, sink, cellX, bottomY, cellZ);
                    }
                }
            }
        }
    }

    private void carveCell(Pass pass, CaveLimits limits, CarveSink sink, int cellX, int bottomY, int cellZ) {
        for (int stepY = 0; stepY < ChunkLattice.CELL_BLOCKS; stepY++) {
            int y = bottomY + stepY;
            if (y < limits.lowestY() || y > limits.highestY()) {
                continue;
            }
            for (int stepX = 0; stepX < ChunkLattice.CELL_BLOCKS; stepX++) {
                int localX = (cellX * ChunkLattice.CELL_BLOCKS) + stepX;
                for (int stepZ = 0; stepZ < ChunkLattice.CELL_BLOCKS; stepZ++) {
                    int localZ = (cellZ * ChunkLattice.CELL_BLOCKS) + stepZ;
                    int column = (localX << 4) | localZ;
                    double openness = limits.openness(column, y);
                    if (openness > 0D && carvesAt(pass, stepX * CELL_SHARE, stepY * CELL_SHARE, stepZ * CELL_SHARE, openness, column)) {
                        sink.carve(localX, y, localZ);
                    }
                }
            }
        }
    }

    /** One chunk's carving by one shape on one thread: the sampled fields, the cell being decided and a value per column. */
    static final class Pass {
        private static final int CHUNK_COLUMNS = 256;
        private static final int CELL_VALUES = 12;

        private ChunkLattice lattice;
        private double[] pointValues = new double[0];
        private double[][] values = new double[0][];
        private LatticeCell[] cells = new LatticeCell[0];
        private int fieldCount;
        private final double[] columnValues = new double[CHUNK_COLUMNS];
        private final double[] cellValues = new double[CELL_VALUES];

        private void begin(ChunkLattice lattice, LatticeShape shape) {
            this.lattice = lattice;
            this.fieldCount = shape.fieldCount();
            if (values.length < fieldCount) {
                values = Arrays.copyOf(values, fieldCount);
                cells = Arrays.copyOf(cells, fieldCount);
                pointValues = new double[fieldCount];
            }
            int points = lattice.pointCount();
            for (int field = 0; field < fieldCount; field++) {
                if (values[field] == null || values[field].length < points) {
                    values[field] = new double[points];
                }
                if (cells[field] == null) {
                    cells[field] = new LatticeCell();
                }
            }
            lattice.forEachPoint((index, x, y, z) -> {
                shape.readFields(x, y, z, pointValues);
                for (int field = 0; field < fieldCount; field++) {
                    values[field][index] = pointValues[field];
                }
            });
            Arrays.fill(columnValues, Double.NaN);
        }

        private void load(int cellX, int cellY, int cellZ) {
            for (int field = 0; field < fieldCount; field++) {
                cells[field].load(values[field], lattice, cellX, cellY, cellZ);
            }
        }

        LatticeCell cell(int field) {
            return cells[field];
        }

        ChunkLattice lattice() {
            return lattice;
        }

        /** A value the shape keeps per column of the chunk for the length of the pass; NaN until the shape sets it. */
        double[] columnValues() {
            return columnValues;
        }

        /** Values the shape works out in {@link LatticeShape#mayCarveIn} for the blocks of the loaded cell. */
        double[] cellValues() {
            return cellValues;
        }
    }
}
