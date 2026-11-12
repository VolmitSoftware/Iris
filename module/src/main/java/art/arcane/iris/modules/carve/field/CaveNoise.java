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

import com.volmit.noise.NoiseFactory;
import com.volmit.noise.NoiseSampler;
import com.volmit.noise.NoiseStyle;

/** Builds the noise fields of cave shapes, sized in blocks instead of in a noise style's zoom. */
final class CaveNoise {
    private static final String STYLE = "SIMPLEX";
    /** Measured on the host's SIMPLEX style at zoom 1: the mean distance in blocks between two sign changes along a line. */
    private static final double SIMPLEX_ZERO_SPACING_BLOCKS = 80.6D;
    /** Measured on the same style: how much its value typically changes per block, times the spacing above. */
    private static final double SIMPLEX_SLOPE_TIMES_SPACING = 2.43D;
    /** The features of a field's finer second layer are this much smaller than its own. */
    private static final double DETAIL_SHRINK = 2.7D;
    /** Below this size a detail layer is finer than the sampling lattice can carry. */
    private static final double MIN_DETAIL_SPACING_BLOCKS = 12D;

    private final NoiseFactory noise;
    private final long seed;
    private int fieldsBuilt;

    CaveNoise(NoiseFactory noise, long seed) {
        this.noise = noise;
        this.seed = seed;
    }

    /** How much a field with this spacing typically changes over one block. */
    static double typicalSlope(double spacing) {
        return SIMPLEX_SLOPE_TIMES_SPACING / spacing;
    }

    /** A field whose sign changes about every {@code spacing} blocks horizontally and {@code verticalSpacing} vertically. */
    Field3 smooth(double spacing, double verticalSpacing) {
        NoiseSampler sampler = nextSampler();
        double horizontal = SIMPLEX_ZERO_SPACING_BLOCKS / spacing;
        double vertical = SIMPLEX_ZERO_SPACING_BLOCKS / verticalSpacing;
        return (x, y, z) -> sampler.signed(x * horizontal, y * vertical, z * horizontal);
    }

    /** {@link #smooth} with a finer layer of {@code detailWeight} times its strength on top, where the lattice can carry one. */
    Field3 detailed(double spacing, double verticalSpacing, double detailWeight) {
        Field3 base = smooth(spacing, verticalSpacing);
        double detailSpacing = spacing / DETAIL_SHRINK;
        double detailVerticalSpacing = verticalSpacing / DETAIL_SHRINK;
        if (Math.min(detailSpacing, detailVerticalSpacing) < MIN_DETAIL_SPACING_BLOCKS) {
            return base;
        }
        Field3 detail = smooth(detailSpacing, detailVerticalSpacing);
        double normalization = 1D / (1D + detailWeight);
        return (x, y, z) -> (base.at(x, y, z) + (detail.at(x, y, z) * detailWeight)) * normalization;
    }

    /** A field that only varies over X and Z, changing sign about every {@code spacing} blocks. */
    Field3 columns(double spacing) {
        NoiseSampler sampler = nextSampler();
        double scale = SIMPLEX_ZERO_SPACING_BLOCKS / spacing;
        return (x, y, z) -> sampler.signed(x * scale, z * scale);
    }

    private NoiseSampler nextSampler() {
        fieldsBuilt++;
        return noise.sampler(NoiseStyle.of(STYLE), seed + (fieldsBuilt * 1_000_003L));
    }
}
