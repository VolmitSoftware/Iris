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

package art.arcane.iris.generation.mantle;

import java.util.Arrays;

final class CaveDensityLattice {
    static final int CORNERS = 9;

    final CaveDensityGrid.Column[] densityColumns = new CaveDensityGrid.Column[CORNERS];
    final CaveDensityGrid.Column[] warpColumns = new CaveDensityGrid.Column[CORNERS];
    private double[] values = new double[0];
    private byte[] rowCells = new byte[0];
    private short[] rowCorners = new short[0];
    private int minimumY;
    private int rows;

    void reset(int minimumY, int rows) {
        this.minimumY = minimumY;
        this.rows = rows;
        if (rowCells.length < rows) {
            rowCells = new byte[rows];
            rowCorners = new short[rows];
        }
        if (values.length < rows * CORNERS) {
            values = new double[rows * CORNERS];
        }
        Arrays.fill(rowCells, 0, rows, (byte) 0);
        Arrays.fill(rowCorners, 0, rows, (short) 0);
    }

    void markRow(int y, int cells, int corners) {
        int row = y - minimumY;
        rowCells[row] = (byte) cells;
        rowCorners[row] = (short) corners;
    }

    int cells(int y) {
        return rowCells[y - minimumY];
    }

    boolean needs(int corner, int y) {
        return (rowCorners[y - minimumY] & (1 << corner)) != 0;
    }

    double density(int corner, int y) {
        return values[corner * rows + (y - minimumY)];
    }

    void store(int corner, int y, double density) {
        values[corner * rows + (y - minimumY)] = density;
    }

    void releaseReferences() {
        Arrays.fill(densityColumns, null);
        Arrays.fill(warpColumns, null);
    }
}
