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

import art.arcane.iris.modules.carve.CaveTunnel;

/**
 * Tunnels: the lines along which two of three fields are both zero. Each field is zero on winding sheets through the
 * rock and two sheets meet in a line, which never ends on its own, so tunnels are long. Every line runs inside two
 * sheets and sooner or later crosses the third, and there all three lines meet: that is a junction, and it is what
 * joins the tunnels into one network.
 *
 * <p>A tunnel is every block within its radius of a line. The distance is worked out in blocks from how steeply the
 * fields change in the cell, so a tunnel keeps the width the pack asks for wherever the noise is steep or shallow.
 * The fields are read at gently shifted places, which makes a tunnel's path wiggle without ever breaking it.
 */
final class TunnelShape extends LatticeShape {
    private static final int SHEETS = 3;
    private static final int WIDTH = SHEETS;
    private static final int PRESENCE = SHEETS + 1;
    private static final int[] PAIR_FIRST = {0, 0, 1};
    private static final int[] PAIR_SECOND = {1, 2, 2};
    private static final int PAIRS = PAIR_FIRST.length;
    private static final int VALUES_PER_PAIR = 4;
    private static final int FIRST_SQUARED = 0;
    private static final int FIRST_TIMES_SECOND = 1;
    private static final int SECOND_SQUARED = 2;
    private static final int PAIR_IS_NEAR = 3;
    private static final double NEAR = 1D;
    private static final double FAR = 0D;
    /** A path shifts by this share of the tunnel's stretch, and changes direction this many times per stretch. */
    private static final double WIGGLE_SHARE_OF_STRETCH = 0.06D;
    private static final double WIGGLES_PER_STRETCH = 3D;
    private static final double SMALLEST_WIGGLE_SPACING_BLOCKS = 16D;
    private static final double VERTICAL_WIGGLE_SHARE = 0.5D;
    private static final double WIDTH_CHANGE_STRETCHES = 1.5D;
    private static final double WIDTH_SPREAD = 1D;
    private static final double PRESENCE_STRETCHES = 3D;
    /** Field value over which tunnels widen from nothing to full size at the edge of an area that has them. */
    private static final double PRESENCE_EDGE = 0.15D;
    /** A tunnel narrower than this is not a passage any more, so it ends instead. */
    private static final double NARROWEST_RADIUS_BLOCKS = 1D;
    /** A cell's slope is an average, so a cell is only ruled out when the line is clearly further than the widest tunnel. */
    private static final double CELL_REACH_SLACK = 1.5D;
    /**
     * Where a field is nearly flat, a small value says little about how far its sheet is, and the tunnel would swell
     * into a blob. A field counts as changing at least this share of its typical slope.
     */
    private static final double LEAST_SLOPE_SHARE = 0.45D;
    /** Where two sheets run nearly side by side the line between them is ill-defined; they count as crossing at least this squarely. */
    private static final double LEAST_CROSSING = 0.03D;

    private final Field3[] sheets = new Field3[SHEETS];
    private final Field3 width;
    private final Field3 presence;
    private final Field3 wiggleX;
    private final Field3 wiggleY;
    private final Field3 wiggleZ;
    private final double wiggleBlocks;
    private final double narrowRadius;
    private final double wideRadius;
    private final double leastSteepness;
    private final double presenceThreshold;

    TunnelShape(CaveNoise noise, CaveTunnel tunnel) {
        double stretch = tunnel.stretch();
        double verticalStretch = stretch / Math.max(1D, tunnel.flatness());
        for (int sheet = 0; sheet < SHEETS; sheet++) {
            sheets[sheet] = noise.smooth(stretch, verticalStretch);
        }
        double wiggleSpacing = Math.max(SMALLEST_WIGGLE_SPACING_BLOCKS, stretch / WIGGLES_PER_STRETCH);
        this.wiggleX = noise.smooth(wiggleSpacing, wiggleSpacing);
        this.wiggleY = noise.smooth(wiggleSpacing, wiggleSpacing);
        this.wiggleZ = noise.smooth(wiggleSpacing, wiggleSpacing);
        this.wiggleBlocks = stretch * WIGGLE_SHARE_OF_STRETCH;
        this.width = noise.smooth(stretch * WIDTH_CHANGE_STRETCHES, stretch * WIDTH_CHANGE_STRETCHES);
        this.narrowRadius = Math.min(tunnel.width().min(), tunnel.width().max()) / 2D;
        this.wideRadius = Math.max(tunnel.width().min(), tunnel.width().max()) / 2D;
        double leastSlope = CaveNoise.typicalSlope(stretch) * LEAST_SLOPE_SHARE;
        this.leastSteepness = leastSlope * leastSlope;
        if (tunnel.coverage() >= 1D) {
            this.presence = null;
            this.presenceThreshold = Double.NEGATIVE_INFINITY;
            return;
        }
        double presenceStretch = stretch * PRESENCE_STRETCHES;
        this.presence = noise.smooth(presenceStretch, presenceStretch);
        this.presenceThreshold = FieldStats.measure(presence, presenceStretch).valueAboveShare(tunnel.coverage());
    }

    @Override
    protected int fieldCount() {
        return presence == null ? SHEETS + 1 : SHEETS + 2;
    }

    @Override
    protected void readFields(int x, int y, int z, double[] values) {
        double shiftedX = x + (wiggleX.at(x, y, z) * wiggleBlocks);
        double shiftedY = y + (wiggleY.at(x, y, z) * wiggleBlocks * VERTICAL_WIGGLE_SHARE);
        double shiftedZ = z + (wiggleZ.at(x, y, z) * wiggleBlocks);
        for (int sheet = 0; sheet < SHEETS; sheet++) {
            values[sheet] = sheets[sheet].at(shiftedX, shiftedY, shiftedZ);
        }
        values[WIDTH] = width.at(x, y, z);
        if (presence != null) {
            values[PRESENCE] = presence.at(x, y, z);
        }
    }

    @Override
    protected boolean mayCarveIn(Pass pass) {
        if (presence != null && pass.cell(PRESENCE).highest() <= presenceThreshold) {
            return false;
        }
        double reach = wideRadius * CELL_REACH_SLACK;
        double[] pairValues = pass.cellValues();
        boolean anyPairNear = false;
        for (int pair = 0; pair < PAIRS; pair++) {
            boolean near = preparePair(pass.cell(PAIR_FIRST[pair]), pass.cell(PAIR_SECOND[pair]), reach, pairValues, pair * VALUES_PER_PAIR);
            pairValues[(pair * VALUES_PER_PAIR) + PAIR_IS_NEAR] = near ? NEAR : FAR;
            anyPairNear |= near;
        }
        return anyPairNear;
    }

    /**
     * Works out, for the line where two sheets meet, the terms that turn the two field values at a block of the cell
     * into its squared distance from that line in blocks. Answers false when the line is out of reach of the whole cell.
     */
    private boolean preparePair(LatticeCell first, LatticeCell second, double reach, double[] pairValues, int offset) {
        double firstSteepness = Math.max(squaredSlope(first), leastSteepness);
        double secondSteepness = Math.max(squaredSlope(second), leastSteepness);
        if (isFurtherThan(first.distanceFromZero(), firstSteepness, reach) || isFurtherThan(second.distanceFromZero(), secondSteepness, reach)) {
            return false;
        }
        double together = (first.slopeX() * second.slopeX()) + (first.slopeY() * second.slopeY()) + (first.slopeZ() * second.slopeZ());
        double crossing = Math.max((firstSteepness * secondSteepness) - (together * together), firstSteepness * secondSteepness * LEAST_CROSSING);
        pairValues[offset + FIRST_SQUARED] = secondSteepness / crossing;
        pairValues[offset + FIRST_TIMES_SECOND] = (-2D * together) / crossing;
        pairValues[offset + SECOND_SQUARED] = firstSteepness / crossing;
        return true;
    }

    private static double squaredSlope(LatticeCell cell) {
        return (cell.slopeX() * cell.slopeX()) + (cell.slopeY() * cell.slopeY()) + (cell.slopeZ() * cell.slopeZ());
    }

    /** Whether a sheet whose field stays {@code fieldDistance} from zero in the cell is more than {@code blocks} away. */
    private static boolean isFurtherThan(double fieldDistance, double squaredSlope, double blocks) {
        return fieldDistance * fieldDistance > blocks * blocks * squaredSlope;
    }

    @Override
    protected boolean carvesAt(Pass pass, double shareX, double shareY, double shareZ, double openness, int column) {
        double radius = radiusAt(pass, shareX, shareY, shareZ) * openness;
        if (radius < NARROWEST_RADIUS_BLOCKS) {
            return false;
        }
        double squaredRadius = radius * radius;
        double[] pairValues = pass.cellValues();
        for (int pair = 0; pair < PAIRS; pair++) {
            int offset = pair * VALUES_PER_PAIR;
            if (pairValues[offset + PAIR_IS_NEAR] == FAR) {
                continue;
            }
            double first = pass.cell(PAIR_FIRST[pair]).at(shareX, shareY, shareZ);
            double second = pass.cell(PAIR_SECOND[pair]).at(shareX, shareY, shareZ);
            double squaredBlocksFromLine = (pairValues[offset + FIRST_SQUARED] * first * first)
                    + (pairValues[offset + FIRST_TIMES_SECOND] * first * second)
                    + (pairValues[offset + SECOND_SQUARED] * second * second);
            if (squaredBlocksFromLine < squaredRadius) {
                return true;
            }
        }
        return false;
    }

    private double radiusAt(Pass pass, double shareX, double shareY, double shareZ) {
        double widthShare = clampToShare(0.5D + (pass.cell(WIDTH).at(shareX, shareY, shareZ) * WIDTH_SPREAD));
        double radius = narrowRadius + ((wideRadius - narrowRadius) * widthShare);
        if (presence == null) {
            return radius;
        }
        double intoArea = (pass.cell(PRESENCE).at(shareX, shareY, shareZ) - presenceThreshold) / PRESENCE_EDGE;
        return radius * clampToShare(intoArea);
    }

    private static double clampToShare(double value) {
        return Math.max(0D, Math.min(1D, value));
    }
}
