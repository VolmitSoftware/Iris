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
 * One field's values at the eight corners of a lattice cell, and the value interpolated between them at any block of
 * the cell. An interpolated value never leaves the range of the corners, so a shape can rule a whole cell out from
 * {@link #lowest()} and {@link #highest()} without looking at its blocks.
 */
final class LatticeCell {
    /** A face holds four corners, and opposite faces are one cell apart. */
    private static final double SLOPE_PER_FACE_SUM = 1D / (4D * ChunkLattice.CELL_BLOCKS);

    private double lowWestNorth;
    private double lowWestSouth;
    private double lowEastNorth;
    private double lowEastSouth;
    private double highWestNorth;
    private double highWestSouth;
    private double highEastNorth;
    private double highEastSouth;

    void load(double[] values, ChunkLattice lattice, int cellX, int cellY, int cellZ) {
        lowWestNorth = values[lattice.pointIndex(cellX, cellY, cellZ)];
        lowWestSouth = values[lattice.pointIndex(cellX, cellY, cellZ + 1)];
        lowEastNorth = values[lattice.pointIndex(cellX + 1, cellY, cellZ)];
        lowEastSouth = values[lattice.pointIndex(cellX + 1, cellY, cellZ + 1)];
        highWestNorth = values[lattice.pointIndex(cellX, cellY + 1, cellZ)];
        highWestSouth = values[lattice.pointIndex(cellX, cellY + 1, cellZ + 1)];
        highEastNorth = values[lattice.pointIndex(cellX + 1, cellY + 1, cellZ)];
        highEastSouth = values[lattice.pointIndex(cellX + 1, cellY + 1, cellZ + 1)];
    }

    double lowest() {
        return Math.min(Math.min(Math.min(lowWestNorth, lowWestSouth), Math.min(lowEastNorth, lowEastSouth)),
                Math.min(Math.min(highWestNorth, highWestSouth), Math.min(highEastNorth, highEastSouth)));
    }

    double highest() {
        return Math.max(Math.max(Math.max(lowWestNorth, lowWestSouth), Math.max(lowEastNorth, lowEastSouth)),
                Math.max(Math.max(highWestNorth, highWestSouth), Math.max(highEastNorth, highEastSouth)));
    }

    /** How far every value of the cell stays from zero; 0 when the cell may cross it. */
    double distanceFromZero() {
        double lowest = lowest();
        if (lowest > 0D) {
            return lowest;
        }
        double highest = highest();
        return highest < 0D ? -highest : 0D;
    }

    /** How much the value changes per block towards higher X, averaged over the cell. */
    double slopeX() {
        return ((lowEastNorth + lowEastSouth + highEastNorth + highEastSouth)
                - (lowWestNorth + lowWestSouth + highWestNorth + highWestSouth)) * SLOPE_PER_FACE_SUM;
    }

    /** How much the value changes per block upwards, averaged over the cell. */
    double slopeY() {
        return ((highWestNorth + highWestSouth + highEastNorth + highEastSouth)
                - (lowWestNorth + lowWestSouth + lowEastNorth + lowEastSouth)) * SLOPE_PER_FACE_SUM;
    }

    /** How much the value changes per block towards higher Z, averaged over the cell. */
    double slopeZ() {
        return ((lowWestSouth + lowEastSouth + highWestSouth + highEastSouth)
                - (lowWestNorth + lowEastNorth + highWestNorth + highEastNorth)) * SLOPE_PER_FACE_SUM;
    }

    /** The value at a block of the cell, each coordinate as its share of the way across the cell, from 0 to 1. */
    double at(double shareX, double shareY, double shareZ) {
        double low = across(lowWestNorth, lowWestSouth, lowEastNorth, lowEastSouth, shareX, shareZ);
        double high = across(highWestNorth, highWestSouth, highEastNorth, highEastSouth, shareX, shareZ);
        return low + ((high - low) * shareY);
    }

    private static double across(double westNorth, double westSouth, double eastNorth, double eastSouth, double shareX, double shareZ) {
        double west = westNorth + ((westSouth - westNorth) * shareZ);
        double east = eastNorth + ((eastSouth - eastNorth) * shareZ);
        return west + ((east - west) * shareX);
    }
}
