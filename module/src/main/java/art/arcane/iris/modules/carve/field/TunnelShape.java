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
import art.arcane.iris.modules.carve.CaveTunnelCoverage;
import art.arcane.iris.modules.carve.CaveValue;
import com.volmit.noise.NoiseStyle;

/**
 * Tunnels: the lines along which two of three fields are both zero. Each field is zero on winding sheets through the
 * rock and two sheets meet in a line, which never ends on its own, so tunnels are long. Every line runs inside two
 * sheets and sooner or later crosses the third, and there all three lines meet: that is a junction, and it is what
 * joins the tunnels into one network.
 *
 * <p>A tunnel is every block within its radius of a line. The distance is worked out in blocks from how steeply the
 * fields change in the cell, so a tunnel keeps the width the pack asks for wherever the noise is steep or shallow.
 * An oval tunnel measures the distance with the vertical axis stretched by its width over its height, which keeps its
 * width and gives it its height. The fields are read at warped places, which makes a tunnel's path bend without ever
 * breaking it.
 */
final class TunnelShape extends LatticeShape {
    private static final int SHEETS = 3;
    private static final int NOT_READ = -1;
    private static final int[] PAIR_FIRST = {0, 0, 1};
    private static final int[] PAIR_SECOND = {1, 2, 2};
    private static final int PAIRS = PAIR_FIRST.length;
    /** A round tunnel keeps, per pair of sheets, the three terms of its squared distance from their line and whether it is near. */
    private static final int ROUND_VALUES_PER_PAIR = 4;
    private static final int FIRST_SQUARED = 0;
    private static final int FIRST_TIMES_SECOND = 1;
    private static final int SECOND_SQUARED = 2;
    private static final int PAIR_IS_NEAR = 3;
    /** An oval tunnel keeps each sheet's slope along the three axes, then whether each pair is near. */
    private static final int OVAL_SLOPE_X = 0;
    private static final int OVAL_SLOPE_Y = 1;
    private static final int OVAL_SLOPE_Z = 2;
    private static final int OVAL_VALUES_PER_SHEET = 3;
    private static final int OVAL_PAIR_IS_NEAR = SHEETS * OVAL_VALUES_PER_SHEET;
    /** An oval tunnel's terms depend on its height over its width at the block, so they are worked out block by block here. */
    private static final ThreadLocal<double[]> OVAL_TERMS = ThreadLocal.withInitial(() -> new double[PAIR_IS_NEAR]);
    private static final double NEAR = 1D;
    private static final double FAR = 0D;
    private static final double ROUND = 1D;
    /** Without a warp, a path shifts by this share of the horizontal spacing, and changes direction this many times per spacing. */
    private static final double DEFAULT_WARP_SHARE_OF_SPACING = 0.06D;
    private static final double DEFAULT_WARPS_PER_SPACING = 3D;
    private static final double SMALLEST_DEFAULT_WARP_SPACING_BLOCKS = 16D;
    /** Without a coverage style, the areas with tunnels are this many horizontal spacings across. */
    private static final double DEFAULT_PRESENCE_SPACINGS = 3D;
    /**
     * Field value over which tunnels widen from nothing to full size at the edge of an area that has them, at a
     * softness of 1. A softness of 0 still keeps the least edge, so the share never divides by zero.
     */
    private static final double SOFTEST_PRESENCE_EDGE = 0.5D;
    private static final double LEAST_PRESENCE_EDGE = 0.005D;
    /** Where the share of an area's tunnels is worked out at the lattice points, a block divides by nothing and subtracts nothing. */
    private static final double PRESENCE_WORKED_OUT_AT_POINTS = 0D;
    private static final double EDGE_WORKED_OUT_AT_POINTS = 1D;
    /** A tunnel narrower or lower than this is not a passage any more, so it ends instead. */
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
    private final Field3 warpX;
    private final Field3 warpY;
    private final Field3 warpZ;
    private final double warpBlocks;
    private final ValueField verticalWarp;
    private final Field3 width;
    private final double narrowRadius;
    private final double wideRadius;
    private final TunnelHeight heightKind;
    private final boolean oval;
    private final double lowRadius;
    private final double tallRadius;
    private final ValueField height;
    private final Field3 presence;
    private final FieldStats presenceStats;
    private final ValueField presenceShare;
    private final ValueField presenceSoftness;
    private final boolean presenceWorkedOutAtPoints;
    private final double presenceThreshold;
    private final double presenceEdge;
    private final int widthField;
    private final int heightField;
    private final int presenceField;
    private final int fieldCount;
    private final double leastSteepness;

    /** How tall a tunnel is against how wide. */
    private enum TunnelHeight {
        /** As tall as it is wide. */
        ROUND,
        /** Between its low and tall height, as far as it is between its narrow and wide width. */
        FOLLOWS_WIDTH,
        /** The height its own noise picks. */
        OWN_NOISE
    }

    TunnelShape(CaveNoise noise, CaveTunnel tunnel) {
        double spacing = tunnel.spacing().horizontal();
        double verticalSpacing = tunnel.spacing().vertical();
        double valueChangeBlocks = spacing * CaveTunnel.VALUE_CHANGE_SPACINGS;
        for (int sheet = 0; sheet < SHEETS; sheet++) {
            sheets[sheet] = noise.smooth(spacing, verticalSpacing);
        }
        this.verticalWarp = ValueField.inBlocks(tunnel.verticalWarp(), noise, "tunnel.verticalWarp", valueChangeBlocks);
        NoiseStyle warp = tunnel.warp().orElse(null);
        if (warp == null) {
            double warpSpacing = Math.max(SMALLEST_DEFAULT_WARP_SPACING_BLOCKS, spacing / DEFAULT_WARPS_PER_SPACING);
            this.warpX = noise.smooth(warpSpacing, warpSpacing);
            this.warpY = noise.smooth(warpSpacing, warpSpacing);
            this.warpZ = noise.smooth(warpSpacing, warpSpacing);
            this.warpBlocks = spacing * DEFAULT_WARP_SHARE_OF_SPACING;
        } else {
            this.warpX = noise.inBlocks(warp);
            this.warpY = noise.inBlocks(warp);
            this.warpZ = noise.inBlocks(warp);
            this.warpBlocks = warp.isFlat() ? 0D : warp.multiplier() / 2D;
        }
        CaveValue widthValue = tunnel.width();
        double widthChangeBlocks = spacing * CaveTunnel.WIDTH_CHANGE_SPACINGS;
        this.width = widthValue.style().map(noise::inBlocks).orElseGet(() -> noise.smooth(widthChangeBlocks, widthChangeBlocks));
        this.narrowRadius = widthValue.lowest() / 2D;
        this.wideRadius = widthValue.highest() / 2D;
        CaveValue heightValue = tunnel.height().orElse(null);
        this.heightKind = heightKindOf(heightValue);
        this.oval = heightKind != TunnelHeight.ROUND;
        this.lowRadius = heightValue == null ? 0D : heightValue.lowest() / 2D;
        this.tallRadius = heightValue == null ? 0D : heightValue.highest() / 2D;
        this.height = heightKind == TunnelHeight.OWN_NOISE
                ? ValueField.inBlocks(heightValue, noise, "tunnel.height", widthChangeBlocks)
                : null;
        double leastSlope = CaveNoise.typicalSlope(spacing) * LEAST_SLOPE_SHARE;
        this.leastSteepness = leastSlope * leastSlope;
        CaveTunnelCoverage coverage = tunnel.coverage();
        this.presenceShare = ValueField.inBlocks(coverage.share(), noise, "tunnel.coverage.share", valueChangeBlocks);
        this.presenceSoftness = ValueField.inBlocks(coverage.softness(), noise, "tunnel.coverage.softness", valueChangeBlocks);
        this.presenceWorkedOutAtPoints = presenceShare.varies() || presenceSoftness.varies();
        boolean heightReadsTheWidthNoise = heightKind == TunnelHeight.FOLLOWS_WIDTH && heightValue.varies();
        boolean readsWidth = widthValue.varies() || heightReadsTheWidthNoise;
        this.widthField = readsWidth ? SHEETS : NOT_READ;
        int nextField = readsWidth ? SHEETS + 1 : SHEETS;
        this.heightField = height == null ? NOT_READ : nextField++;
        if (presenceShare.lowest() >= 1D) {
            this.presence = null;
            this.presenceStats = null;
            this.presenceThreshold = Double.NEGATIVE_INFINITY;
            this.presenceEdge = EDGE_WORKED_OUT_AT_POINTS;
            this.presenceField = NOT_READ;
            this.fieldCount = nextField;
            return;
        }
        double presenceSpacing = spacing * DEFAULT_PRESENCE_SPACINGS;
        NoiseStyle presenceStyle = coverage.style().orElse(null);
        this.presence = presenceStyle == null ? noise.smooth(presenceSpacing, presenceSpacing) : noise.inBlocks(presenceStyle);
        double presenceFeatureBlocks = presenceStyle == null ? presenceSpacing : CaveNoise.featureBlocks(presence, presenceStyle);
        this.presenceStats = FieldStats.measure(presence, presenceFeatureBlocks);
        this.presenceThreshold = presenceWorkedOutAtPoints ? PRESENCE_WORKED_OUT_AT_POINTS : presenceStats.valueAboveShare(presenceShare.min());
        this.presenceEdge = presenceWorkedOutAtPoints ? EDGE_WORKED_OUT_AT_POINTS : presenceEdgeOf(presenceSoftness.min());
        this.presenceField = nextField++;
        this.fieldCount = nextField;
    }

    private static TunnelHeight heightKindOf(CaveValue height) {
        if (height == null) {
            return TunnelHeight.ROUND;
        }
        return height.varies() && height.style().isPresent() ? TunnelHeight.OWN_NOISE : TunnelHeight.FOLLOWS_WIDTH;
    }

    private static double presenceEdgeOf(double softness) {
        return Math.max(LEAST_PRESENCE_EDGE, softness * SOFTEST_PRESENCE_EDGE);
    }

    @Override
    protected int fieldCount() {
        return fieldCount;
    }

    @Override
    protected void readFields(int x, int y, int z, double[] values) {
        readSheets(x, y, z, values);
        if (widthField != NOT_READ) {
            values[widthField] = width.at(x, y, z);
        }
        if (heightField != NOT_READ) {
            values[heightField] = height.at(x, y, z);
        }
        if (presenceField != NOT_READ) {
            values[presenceField] = presenceAt(x, y, z);
        }
    }

    /**
     * The presence field, or where share or softness vary, how far into an area with tunnels the point lies in
     * edges: the threshold and edge are only known point by point then.
     */
    private double presenceAt(int x, int y, int z) {
        double value = presence.at(x, y, z);
        if (!presenceWorkedOutAtPoints) {
            return value;
        }
        double threshold = presenceStats.valueAboveShare(presenceShare.at(x, y, z));
        return (value - threshold) / presenceEdgeOf(presenceSoftness.at(x, y, z));
    }

    private void readSheets(int x, int y, int z, double[] values) {
        if (warpBlocks == 0D) {
            for (int sheet = 0; sheet < SHEETS; sheet++) {
                values[sheet] = sheets[sheet].at(x, y, z);
            }
            return;
        }
        double warpedX = x + (warpX.at(x, y, z) * warpBlocks);
        double warpedY = y + (warpY.at(x, y, z) * warpBlocks * verticalWarp.at(x, y, z));
        double warpedZ = z + (warpZ.at(x, y, z) * warpBlocks);
        for (int sheet = 0; sheet < SHEETS; sheet++) {
            values[sheet] = sheets[sheet].at(warpedX, warpedY, warpedZ);
        }
    }

    @Override
    protected boolean mayCarveIn(Pass pass) {
        if (presence != null && pass.cell(presenceField).highest() <= presenceThreshold) {
            return false;
        }
        double reach = Math.max(wideRadius, tallRadius) * CELL_REACH_SLACK;
        double[] cellValues = pass.cellValues();
        if (oval) {
            keepSlopes(pass, cellValues);
        }
        boolean anyPairNear = false;
        for (int pair = 0; pair < PAIRS; pair++) {
            LatticeCell first = pass.cell(PAIR_FIRST[pair]);
            LatticeCell second = pass.cell(PAIR_SECOND[pair]);
            boolean near = isNear(first, reach) && isNear(second, reach);
            if (near && !oval) {
                keepTerms(first, second, ROUND, cellValues, pair * ROUND_VALUES_PER_PAIR);
            }
            cellValues[nearIndex(pair)] = near ? NEAR : FAR;
            anyPairNear |= near;
        }
        return anyPairNear;
    }

    private int nearIndex(int pair) {
        return oval ? OVAL_PAIR_IS_NEAR + pair : (pair * ROUND_VALUES_PER_PAIR) + PAIR_IS_NEAR;
    }

    /** Whether the sheet of a field may come within {@code reach} blocks of the cell. */
    private boolean isNear(LatticeCell cell, double reach) {
        double fieldDistance = cell.distanceFromZero();
        double squaredSlope = Math.max(squaredSlope(cell, ROUND), leastSteepness);
        return fieldDistance * fieldDistance <= reach * reach * squaredSlope;
    }

    private static void keepSlopes(Pass pass, double[] cellValues) {
        for (int sheet = 0; sheet < SHEETS; sheet++) {
            LatticeCell cell = pass.cell(sheet);
            int offset = sheet * OVAL_VALUES_PER_SHEET;
            cellValues[offset + OVAL_SLOPE_X] = cell.slopeX();
            cellValues[offset + OVAL_SLOPE_Y] = cell.slopeY();
            cellValues[offset + OVAL_SLOPE_Z] = cell.slopeZ();
        }
    }

    /**
     * Works out, for the line where two sheets meet, the terms that turn the two field values at a block into its
     * squared distance from that line in blocks, with the vertical axis squeezed by {@code verticalScale}.
     */
    private void keepTerms(LatticeCell first, LatticeCell second, double verticalScale, double[] terms, int offset) {
        keepTerms(first.slopeX(), first.slopeY(), first.slopeZ(), second.slopeX(), second.slopeY(), second.slopeZ(), verticalScale, terms, offset);
    }

    private void keepTerms(double firstX, double firstY, double firstZ, double secondX, double secondY, double secondZ,
                           double verticalScale, double[] terms, int offset) {
        double squaredScale = verticalScale * verticalScale;
        double firstSteepness = Math.max((firstX * firstX) + (squaredScale * firstY * firstY) + (firstZ * firstZ), leastSteepness);
        double secondSteepness = Math.max((secondX * secondX) + (squaredScale * secondY * secondY) + (secondZ * secondZ), leastSteepness);
        double together = (firstX * secondX) + (squaredScale * firstY * secondY) + (firstZ * secondZ);
        double crossing = Math.max((firstSteepness * secondSteepness) - (together * together), firstSteepness * secondSteepness * LEAST_CROSSING);
        terms[offset + FIRST_SQUARED] = secondSteepness / crossing;
        terms[offset + FIRST_TIMES_SECOND] = (-2D * together) / crossing;
        terms[offset + SECOND_SQUARED] = firstSteepness / crossing;
    }

    private static double squaredSlope(LatticeCell cell, double verticalScale) {
        double squaredScale = verticalScale * verticalScale;
        return (cell.slopeX() * cell.slopeX()) + (squaredScale * cell.slopeY() * cell.slopeY()) + (cell.slopeZ() * cell.slopeZ());
    }

    @Override
    protected boolean carvesAt(Pass pass, double shareX, double shareY, double shareZ, double openness, int column) {
        double widthShare = widthShareAt(pass, shareX, shareY, shareZ);
        double sizeShare = presenceShareAt(pass, shareX, shareY, shareZ);
        double radius = ((narrowRadius + ((wideRadius - narrowRadius) * widthShare)) * sizeShare) * openness;
        if (radius < NARROWEST_RADIUS_BLOCKS) {
            return false;
        }
        if (!oval) {
            return isWithinRoundTunnel(pass, shareX, shareY, shareZ, radius);
        }
        double heightRadius = (fullHeightRadiusAt(pass, shareX, shareY, shareZ, widthShare) * sizeShare) * openness;
        if (heightRadius < NARROWEST_RADIUS_BLOCKS) {
            return false;
        }
        return isWithinOvalTunnel(pass, shareX, shareY, shareZ, radius, heightRadius);
    }

    private boolean isWithinRoundTunnel(Pass pass, double shareX, double shareY, double shareZ, double radius) {
        double squaredRadius = radius * radius;
        double[] terms = pass.cellValues();
        for (int pair = 0; pair < PAIRS; pair++) {
            int offset = pair * ROUND_VALUES_PER_PAIR;
            if (terms[offset + PAIR_IS_NEAR] == FAR) {
                continue;
            }
            if (squaredBlocksFromLine(pass, pair, terms, offset, shareX, shareY, shareZ) < squaredRadius) {
                return true;
            }
        }
        return false;
    }

    /**
     * Measures with the vertical axis stretched by the tunnel's width over its height, where the oval is a circle as
     * wide as the tunnel: a field's upward slope shrinks by the same share.
     */
    private boolean isWithinOvalTunnel(Pass pass, double shareX, double shareY, double shareZ, double radius, double heightRadius) {
        double squaredRadius = radius * radius;
        double verticalScale = heightRadius / radius;
        double[] cellValues = pass.cellValues();
        double[] terms = OVAL_TERMS.get();
        for (int pair = 0; pair < PAIRS; pair++) {
            if (cellValues[OVAL_PAIR_IS_NEAR + pair] == FAR) {
                continue;
            }
            int first = PAIR_FIRST[pair] * OVAL_VALUES_PER_SHEET;
            int second = PAIR_SECOND[pair] * OVAL_VALUES_PER_SHEET;
            keepTerms(cellValues[first + OVAL_SLOPE_X], cellValues[first + OVAL_SLOPE_Y], cellValues[first + OVAL_SLOPE_Z],
                    cellValues[second + OVAL_SLOPE_X], cellValues[second + OVAL_SLOPE_Y], cellValues[second + OVAL_SLOPE_Z],
                    verticalScale, terms, 0);
            if (squaredBlocksFromLine(pass, pair, terms, 0, shareX, shareY, shareZ) < squaredRadius) {
                return true;
            }
        }
        return false;
    }

    private static double squaredBlocksFromLine(Pass pass, int pair, double[] terms, int offset, double shareX, double shareY, double shareZ) {
        double first = pass.cell(PAIR_FIRST[pair]).at(shareX, shareY, shareZ);
        double second = pass.cell(PAIR_SECOND[pair]).at(shareX, shareY, shareZ);
        return (terms[offset + FIRST_SQUARED] * first * first)
                + (terms[offset + FIRST_TIMES_SECOND] * first * second)
                + (terms[offset + SECOND_SQUARED] * second * second);
    }

    /** How far between its narrow and its wide width the tunnel is at the block, from 0 to 1; 0 where the width is fixed. */
    private double widthShareAt(Pass pass, double shareX, double shareY, double shareZ) {
        if (widthField == NOT_READ) {
            return 0D;
        }
        return ValueField.shareAt(pass.cell(widthField).at(shareX, shareY, shareZ));
    }

    /** Half the height of an oval tunnel at the block, before it narrows at the edges of its areas and limits. */
    private double fullHeightRadiusAt(Pass pass, double shareX, double shareY, double shareZ, double widthShare) {
        if (heightKind == TunnelHeight.OWN_NOISE) {
            return pass.cell(heightField).at(shareX, shareY, shareZ) / 2D;
        }
        return lowRadius + ((tallRadius - lowRadius) * widthShare);
    }

    /** How far the block lies into an area with tunnels: 1 inside, falling to 0 over the edge. */
    private double presenceShareAt(Pass pass, double shareX, double shareY, double shareZ) {
        if (presence == null) {
            return 1D;
        }
        return clampToShare((pass.cell(presenceField).at(shareX, shareY, shareZ) - presenceThreshold) / presenceEdge);
    }

    private static double clampToShare(double value) {
        return Math.max(0D, Math.min(1D, value));
    }
}
