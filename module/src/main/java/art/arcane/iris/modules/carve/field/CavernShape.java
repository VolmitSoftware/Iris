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

/**
 * Caverns: the rooms where one slow field rises above a threshold. Pillars are columns where a second field, which
 * only varies across X and Z, peaks: there the room has to be more open to be carved, so a pillar stands from floor
 * to ceiling and widens towards both, where the room is least open.
 */
final class CavernShape extends LatticeShape {
    private static final int ROOMS = 0;
    /** A room is the part of a feature above the threshold, which is narrower than the feature itself. */
    private static final double FEATURE_BLOCKS_PER_ROOM_BLOCK = 1.6D;
    /** A finer layer keeps room walls from looking like smooth bubbles. */
    private static final double WALL_DETAIL = 0.3D;
    /** Closing a cavern takes a little more than its most open spot, so nothing is left at the very edge of its limits. */
    private static final double CLOSING_MARGIN = 1.1D;
    /** Pillars are two features apart: one peak, one trough. */
    private static final double PILLAR_FEATURES_PER_SPACING = 0.5D;
    /** How much more ground a pillar covers at floor and ceiling than at its middle. */
    private static final double PILLAR_FOOT_AREA = 4D;
    private static final double LARGEST_PILLAR_AREA_SHARE = 0.3D;

    private final Field3 rooms;
    private final double threshold;
    private final double mostOpen;
    private final Field3 pillars;
    private final double pillarMiddle;
    private final double pillarFoot;

    CavernShape(CaveNoise noise, CaveCavern cavern) {
        double span = cavern.size() * FEATURE_BLOCKS_PER_ROOM_BLOCK;
        double height = cavern.height() * FEATURE_BLOCKS_PER_ROOM_BLOCK;
        this.rooms = noise.detailed(span, height, WALL_DETAIL);
        FieldStats stats = FieldStats.measure(rooms, span);
        this.threshold = stats.valueAboveShare(cavern.coverage());
        this.mostOpen = Math.max(0D, stats.highest() - threshold);
        if (cavern.pillars().isEmpty()) {
            this.pillars = null;
            this.pillarMiddle = Double.POSITIVE_INFINITY;
            this.pillarFoot = Double.POSITIVE_INFINITY;
            return;
        }
        CavePillars settings = cavern.pillars().get();
        double featureBlocks = settings.spacing() * PILLAR_FEATURES_PER_SPACING;
        this.pillars = noise.columns(featureBlocks);
        FieldStats pillarStats = FieldStats.measure(pillars, featureBlocks);
        double middleAreaShare = Math.min(LARGEST_PILLAR_AREA_SHARE, pillarAreaShare(settings));
        this.pillarMiddle = pillarStats.valueAboveShare(middleAreaShare);
        this.pillarFoot = pillarStats.valueAboveShare(Math.min(1D, middleAreaShare * PILLAR_FOOT_AREA));
    }

    private static double pillarAreaShare(CavePillars pillars) {
        double radius = pillars.thickness() / 2D;
        return (Math.PI * radius * radius) / ((double) pillars.spacing() * pillars.spacing());
    }

    @Override
    protected int fieldCount() {
        return 1;
    }

    @Override
    protected void readFields(int x, int y, int z, double[] values) {
        values[ROOMS] = rooms.at(x, y, z);
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
        return open > pillarStrength(pass, column) * mostOpen;
    }

    /** How strongly the column is a pillar: 0 outside one, 1 in its middle, in between on the foot it widens into. */
    private double pillarStrength(Pass pass, int column) {
        double[] strengths = pass.columnValues();
        double known = strengths[column];
        if (!Double.isNaN(known)) {
            return known;
        }
        double value = pillars.at(pass.lattice().originX() + (column >> 4), 0D, pass.lattice().originZ() + (column & 15));
        double intoPillar = Math.max(0D, Math.min(1D, (value - pillarFoot) / (pillarMiddle - pillarFoot)));
        double strength = intoPillar * intoPillar;
        strengths[column] = strength;
        return strength;
    }
}
