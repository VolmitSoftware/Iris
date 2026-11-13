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
 */
final class CavernShape extends LatticeShape {
    private static final int ROOMS = 0;
    private static final int PILLARS = 1;
    /** A room is the part of a feature above the threshold, which is narrower than the feature itself. */
    private static final double FEATURE_BLOCKS_PER_ROOM_BLOCK = 1.6D;
    /** A finer layer keeps room walls from looking like smooth bubbles. */
    private static final double WALL_DETAIL = 0.3D;
    /** Closing a cavern takes a little more than its most open spot, so nothing is left at the very edge of its limits. */
    private static final double CLOSING_MARGIN = 1.1D;
    /** Pillars are two features apart: one peak, one trough. */
    private static final double PILLAR_FEATURES_PER_SPACING = 0.5D;
    private static final double LARGEST_PILLAR_AREA_SHARE = 0.3D;
    private static final double NO_PILLAR = 0D;
    private static final double FULL_PILLAR = 1D;

    private final Field3 rooms;
    private final double threshold;
    private final double mostOpen;
    private final Field3 pillars;
    private final boolean pillarsChangeWithHeight;
    private final double pillarMiddle;
    private final double pillarFoot;

    CavernShape(CaveNoise noise, CaveCavern cavern) {
        double span = cavern.size() * FEATURE_BLOCKS_PER_ROOM_BLOCK;
        double height = cavern.height() * FEATURE_BLOCKS_PER_ROOM_BLOCK;
        this.rooms = cavern.style()
                .map(style -> noise.scaledTo(style, span, height))
                .orElseGet(() -> noise.detailed(span, height, WALL_DETAIL));
        FieldStats stats = FieldStats.measure(rooms, span);
        this.threshold = stats.valueAboveShare(cavern.coverage());
        this.mostOpen = Math.max(0D, stats.highest() - threshold);
        if (cavern.pillars().isEmpty()) {
            this.pillars = null;
            this.pillarsChangeWithHeight = false;
            this.pillarMiddle = Double.POSITIVE_INFINITY;
            this.pillarFoot = Double.POSITIVE_INFINITY;
            return;
        }
        CavePillars settings = cavern.pillars().get();
        double featureBlocks = settings.spacing() * PILLAR_FEATURES_PER_SPACING;
        this.pillarsChangeWithHeight = settings.vertical().isPresent();
        this.pillars = pillarField(noise, settings, featureBlocks);
        FieldStats pillarStats = FieldStats.measure(pillars, featureBlocks);
        double middleAreaShare = Math.min(LARGEST_PILLAR_AREA_SHARE, pillarAreaShare(settings));
        this.pillarMiddle = pillarStats.valueAboveShare(middleAreaShare);
        this.pillarFoot = pillarStats.valueAboveShare(Math.min(1D, middleAreaShare * settings.flare()));
    }

    private static Field3 pillarField(CaveNoise noise, CavePillars settings, double featureBlocks) {
        NoiseStyle style = settings.style().orElse(null);
        if (settings.vertical().isEmpty()) {
            return style == null ? noise.columns(featureBlocks) : noise.columnsScaledTo(style, featureBlocks);
        }
        double vertical = settings.vertical().getAsInt();
        return style == null ? noise.smooth(featureBlocks, vertical) : noise.scaledTo(style, featureBlocks, vertical);
    }

    private static double pillarAreaShare(CavePillars pillars) {
        double radius = pillars.thickness() / 2D;
        return (Math.PI * radius * radius) / ((double) pillars.spacing() * pillars.spacing());
    }

    @Override
    protected int fieldCount() {
        return pillarsChangeWithHeight ? 2 : 1;
    }

    @Override
    protected void readFields(int x, int y, int z, double[] values) {
        values[ROOMS] = rooms.at(x, y, z);
        if (pillarsChangeWithHeight) {
            values[PILLARS] = pillars.at(x, y, z);
        }
    }

    @Override
    protected boolean mayCarveIn(Pass pass) {
        return pass.cell(ROOMS).highest() > threshold;
    }

    @Override
    protected boolean carvesAt(Pass pass, double shareX, double shareY, double shareZ, double openness, int column) {
        double open = pass.cell(ROOMS).at(shareX, shareY, shareZ) - threshold - ((1D - openness) * mostOpen * CLOSING_MARGIN);
        if (open <= 0D) {
            return false;
        }
        if (pillars == null) {
            return true;
        }
        double strength = pillarsChangeWithHeight
                ? pillarStrength(pass.cell(PILLARS).at(shareX, shareY, shareZ))
                : columnPillarStrength(pass, column);
        return open > strength * mostOpen;
    }

    /** {@link #pillarStrength} of a straight pillar, read once per column of the chunk. */
    private double columnPillarStrength(Pass pass, int column) {
        double[] strengths = pass.columnValues();
        double known = strengths[column];
        if (!Double.isNaN(known)) {
            return known;
        }
        double value = pillars.at(pass.lattice().originX() + (column >> 4), 0D, pass.lattice().originZ() + (column & 15));
        double strength = pillarStrength(value);
        strengths[column] = strength;
        return strength;
    }

    /** How strongly a pillar field value is a pillar: 0 outside one, 1 in its middle, in between on the foot it widens into. */
    private double pillarStrength(double value) {
        if (value >= pillarMiddle) {
            return FULL_PILLAR;
        }
        double footWidth = pillarMiddle - pillarFoot;
        if (footWidth <= 0D) {
            return NO_PILLAR;
        }
        double intoPillar = Math.max(0D, Math.min(1D, (value - pillarFoot) / footWidth));
        return intoPillar * intoPillar;
    }
}
