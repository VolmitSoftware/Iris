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

package art.arcane.iris.generation.cave.field;

/**
 * Where a cave holds pockets of the dimension's fluid: a smooth noise read above a cutoff that rises towards the
 * fluid height, so pockets fill the deepest parts of a cave first. The noise is stretched to twice its horizontal
 * size vertically, the way the caves this replaces read theirs.
 */
public final class AquiferPockets {
    /** The spacing of the pockets' noise horizontally, in blocks: the SIMPLEX style at zoom 1. */
    private static final double POCKET_SPACING_BLOCKS = 52.6D;
    private static final double POCKET_VERTICAL_SPACING_BLOCKS = POCKET_SPACING_BLOCKS * 2D;
    private static final double CUTOFF_AT_FLUID_HEIGHT = 0.35D;
    private static final double CUTOFF_RISE_PER_DEPTH_SPAN = 0.2D;
    private static final double DEPTH_SPAN_BLOCKS = 48D;
    private static final double MOST_DEPTH_SPANS = 1.5D;
    private static final String NOISE_NAME = "aquifer.pockets";

    private final Field3 pockets;

    public AquiferPockets(NoiseFactory noise, long seed) {
        this.pockets = new CaveNoise(noise, seed).named(NOISE_NAME).smooth(POCKET_SPACING_BLOCKS, POCKET_VERTICAL_SPACING_BLOCKS);
    }

    /** Whether the block at height {@code y} below fluid height {@code fluidHeight} lies in a pocket of fluid. */
    public boolean holdsFluidAt(int x, int y, int z, int fluidHeight) {
        double depthSpans = Math.max(0D, Math.min(MOST_DEPTH_SPANS, (fluidHeight - y) / DEPTH_SPAN_BLOCKS));
        double cutoff = CUTOFF_AT_FLUID_HEIGHT + (depthSpans * CUTOFF_RISE_PER_DEPTH_SPAN);
        return pockets.at(x, y, z) > cutoff;
    }
}
