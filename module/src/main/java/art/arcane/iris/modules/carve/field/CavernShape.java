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

import art.arcane.iris.modules.carve.CaveCavern;
import art.arcane.iris.modules.carve.CavePillars;
import com.volmit.noise.NoiseStyle;

/**
 * Caverns: the rooms where one slow field rises above a threshold. Pillars stand where a second field peaks: there the
 * room has to be more open to be carved, so a pillar widens towards floor and ceiling, where the room is least open.
 * The pillar field varies only across X and Z unless the pack lets pillars change with height; then it is read in 3D
 * on the lattice, and a pillar whose field fades out mid-room narrows and breaks off, leaving stubs that flare into
 * floor and ceiling.
 *
 * <p>A value that varies moves the threshold it sets point by point: the lattice then carries the room field less its
 * threshold, and the pillar thresholds, so blocks interpolate them like any field. A plain number keeps them constant.
 */
final class CavernShape extends LatticeShape {
    private static final int ROOMS = 0;
    private static final int NOT_READ = -1;
    /** A room is the part of a feature above the threshold, which is narrower than the feature itself. */
    private static final double FEATURE_BLOCKS_PER_ROOM_BLOCK = 1.6D;
    /** Closing a cavern takes a little more than its most open spot, so nothing is left at the very edge of its limits. */
    private static final double CLOSING_MARGIN = 1.1D;
    /** Pillars are two features apart: one peak, one trough. */
    private static final double PILLAR_FEATURES_PER_SPACING = 0.5D;
    private static final double LARGEST_PILLAR_AREA_SHARE = 0.3D;
    private static final double NO_PILLAR = 0D;
    private static final double FULL_PILLAR = 1D;
    /** Where the threshold is worked out at the lattice points, a block subtracts nothing more. */
    private static final double THRESHOLD_WORKED_OUT_AT_POINTS = 0D;

    private final Field3 rooms;
    private final FieldStats roomStats;
    private final ValueField coverage;
    private final double threshold;
    private final double mostOpen;
    private final Field3 pillars;
    private final boolean pillarsChangeWithHeight;
    private final FieldStats pillarStats;
    private final ValueField pillarSpacing;
    private final ValueField pillarThickness;
    private final ValueField pillarFlare;
    private final boolean pillarSizeVaries;
    private final double pillarMiddle;
    private final double pillarFoot;
    private final int mostOpenField;
    private final int pillarField;
    private final int pillarMiddleField;
    private final int pillarFootField;
    private final int fieldCount;

    CavernShape(CaveNoise noise, CaveCavern cavern) {
        double valueChangeBlocks = cavern.size().middle() * CaveCavern.VALUE_CHANGE_SIZES;
        ValueField size = ValueField.inBlocks(cavern.size(), noise, "cavern.size", valueChangeBlocks);
        ValueField height = ValueField.inColumns(cavern.height(), noise, "cavern.height", valueChangeBlocks);
        ValueField roughness = ValueField.inBlocks(cavern.roughness(), noise, "cavern.roughness", valueChangeBlocks);
        this.coverage = ValueField.inBlocks(cavern.coverage(), noise, "cavern.coverage", valueChangeBlocks);
        this.rooms = roomField(noise, cavern, size, height, roughness);
        this.roomStats = FieldStats.measure(rooms, size.middle() * FEATURE_BLOCKS_PER_ROOM_BLOCK);
        this.threshold = coverage.varies() ? THRESHOLD_WORKED_OUT_AT_POINTS : roomStats.valueAboveShare(coverage.min());
        this.mostOpen = Math.max(0D, roomStats.highest() - threshold);
        int nextField = ROOMS + 1;
        this.mostOpenField = coverage.varies() ? nextField++ : NOT_READ;
        if (cavern.pillars().isEmpty()) {
            this.pillars = null;
            this.pillarsChangeWithHeight = false;
            this.pillarStats = null;
            this.pillarSpacing = null;
            this.pillarThickness = null;
            this.pillarFlare = null;
            this.pillarSizeVaries = false;
            this.pillarMiddle = Double.POSITIVE_INFINITY;
            this.pillarFoot = Double.POSITIVE_INFINITY;
            this.pillarField = NOT_READ;
            this.pillarMiddleField = NOT_READ;
            this.pillarFootField = NOT_READ;
            this.fieldCount = nextField;
            return;
        }
        CavePillars settings = cavern.pillars().get();
        this.pillarSpacing = ValueField.inColumns(settings.spacing(), noise, "cavern.pillars.spacing", valueChangeBlocks);
        this.pillarThickness = ValueField.inColumns(settings.thickness(), noise, "cavern.pillars.thickness", valueChangeBlocks);
        this.pillarFlare = ValueField.inColumns(settings.flare(), noise, "cavern.pillars.flare", valueChangeBlocks);
        this.pillarSizeVaries = pillarSpacing.varies() || pillarThickness.varies() || pillarFlare.varies();
        this.pillarsChangeWithHeight = settings.vertical().isPresent();
        ValueField vertical = settings.vertical()
                .map(value -> ValueField.inColumns(value, noise, "cavern.pillars.vertical", valueChangeBlocks))
                .orElseGet(() -> ValueField.constant(0D));
        this.pillars = pillarField(noise, settings, pillarSpacing, vertical);
        this.pillarStats = FieldStats.measure(pillars, pillarSpacing.middle() * PILLAR_FEATURES_PER_SPACING);
        this.pillarMiddle = pillarSizeVaries ? Double.NaN : pillarMiddleAt(0D, 0D);
        this.pillarFoot = pillarSizeVaries ? Double.NaN : pillarFootAt(0D, 0D);
        this.pillarField = pillarsChangeWithHeight ? nextField++ : NOT_READ;
        boolean thresholdsAtPoints = pillarsChangeWithHeight && pillarSizeVaries;
        this.pillarMiddleField = thresholdsAtPoints ? nextField++ : NOT_READ;
        this.pillarFootField = thresholdsAtPoints ? nextField++ : NOT_READ;
        this.fieldCount = nextField;
    }

    private static Field3 roomField(CaveNoise noise, CaveCavern cavern, ValueField size, ValueField height, ValueField roughness) {
        NoiseStyle style = cavern.style().orElse(null);
        return VaryingScale.of(noise, size, height, "cavern.rooms", (fieldNoise, roomSize, roomHeight) -> {
            double span = roomSize * FEATURE_BLOCKS_PER_ROOM_BLOCK;
            double featureHeight = roomHeight * FEATURE_BLOCKS_PER_ROOM_BLOCK;
            return style == null ? fieldNoise.detailed(span, featureHeight, roughness) : fieldNoise.scaledTo(style, span, featureHeight);
        });
    }

    private static Field3 pillarField(CaveNoise noise, CavePillars settings, ValueField spacing, ValueField vertical) {
        NoiseStyle style = settings.style().orElse(null);
        boolean straight = settings.vertical().isEmpty();
        return VaryingScale.of(noise, spacing, vertical, "cavern.pillars", (fieldNoise, pillarSpacing, verticalBlocks) -> {
            double featureBlocks = pillarSpacing * PILLAR_FEATURES_PER_SPACING;
            if (straight) {
                return style == null ? fieldNoise.columns(featureBlocks) : fieldNoise.columnsScaledTo(style, featureBlocks);
            }
            return style == null ? fieldNoise.smooth(featureBlocks, verticalBlocks) : fieldNoise.scaledTo(style, featureBlocks, verticalBlocks);
        });
    }

    /** The pillar field value above which a column is the middle of a pillar. */
    private double pillarMiddleAt(double x, double z) {
        return pillarStats.valueAboveShare(middleAreaShareAt(x, z));
    }

    /** The pillar field value above which a column is on the foot a pillar widens into towards floor and ceiling. */
    private double pillarFootAt(double x, double z) {
        return pillarStats.valueAboveShare(Math.min(1D, middleAreaShareAt(x, z) * pillarFlare.at(x, z)));
    }

    private double middleAreaShareAt(double x, double z) {
        double radius = pillarThickness.at(x, z) / 2D;
        double spacing = pillarSpacing.at(x, z);
        return Math.min(LARGEST_PILLAR_AREA_SHARE, (Math.PI * radius * radius) / (spacing * spacing));
    }

    @Override
    protected int fieldCount() {
        return fieldCount;
    }

    @Override
    protected void readFields(int x, int y, int z, double[] values) {
        double room = rooms.at(x, y, z);
        if (mostOpenField == NOT_READ) {
            values[ROOMS] = room;
        } else {
            double pointThreshold = roomStats.valueAboveShare(coverage.at(x, y, z));
            values[ROOMS] = room - pointThreshold;
            values[mostOpenField] = Math.max(0D, roomStats.highest() - pointThreshold);
        }
        if (pillarField != NOT_READ) {
            values[pillarField] = pillars.at(x, y, z);
        }
        if (pillarMiddleField != NOT_READ) {
            values[pillarMiddleField] = pillarMiddleAt(x, z);
            values[pillarFootField] = pillarFootAt(x, z);
        }
    }

    @Override
    protected boolean mayCarveIn(Pass pass) {
        return pass.cell(ROOMS).highest() > threshold;
    }

    @Override
    protected boolean carvesAt(Pass pass, double shareX, double shareY, double shareZ, double openness, int column) {
        double mostOpenHere = mostOpenField == NOT_READ ? mostOpen : pass.cell(mostOpenField).at(shareX, shareY, shareZ);
        double open = pass.cell(ROOMS).at(shareX, shareY, shareZ) - threshold - ((1D - openness) * mostOpenHere * CLOSING_MARGIN);
        if (open <= 0D) {
            return false;
        }
        if (pillars == null) {
            return true;
        }
        double strength = pillarsChangeWithHeight
                ? pointPillarStrength(pass, shareX, shareY, shareZ)
                : columnPillarStrength(pass, column);
        return open > strength * mostOpenHere;
    }

    private double pointPillarStrength(Pass pass, double shareX, double shareY, double shareZ) {
        double value = pass.cell(pillarField).at(shareX, shareY, shareZ);
        if (pillarMiddleField == NOT_READ) {
            return pillarStrength(value, pillarMiddle, pillarFoot);
        }
        return pillarStrength(value, pass.cell(pillarMiddleField).at(shareX, shareY, shareZ), pass.cell(pillarFootField).at(shareX, shareY, shareZ));
    }

    /** {@link #pillarStrength} of a straight pillar, read once per column of the chunk. */
    private double columnPillarStrength(Pass pass, int column) {
        double[] strengths = pass.columnValues();
        double known = strengths[column];
        if (!Double.isNaN(known)) {
            return known;
        }
        double x = pass.lattice().originX() + (column >> 4);
        double z = pass.lattice().originZ() + (column & 15);
        double value = pillars.at(x, 0D, z);
        double strength = pillarSizeVaries
                ? pillarStrength(value, pillarMiddleAt(x, z), pillarFootAt(x, z))
                : pillarStrength(value, pillarMiddle, pillarFoot);
        strengths[column] = strength;
        return strength;
    }

    /** How strongly a pillar field value is a pillar: 0 outside one, 1 in its middle, in between on the foot it widens into. */
    private static double pillarStrength(double value, double middle, double foot) {
        if (value >= middle) {
            return FULL_PILLAR;
        }
        double footWidth = middle - foot;
        if (footWidth <= 0D) {
            return NO_PILLAR;
        }
        double intoPillar = Math.max(0D, Math.min(1D, (value - foot) / footWidth));
        return intoPillar * intoPillar;
    }
}
