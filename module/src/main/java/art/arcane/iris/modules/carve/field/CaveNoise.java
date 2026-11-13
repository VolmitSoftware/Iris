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

import java.util.OptionalDouble;

/**
 * Builds the noise fields of cave shapes, sized in blocks instead of in a noise style's zoom. Every field takes the
 * next seed of the cave, whichever way it is built, so changing how one field is built leaves the others alone.
 */
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
    /** A pack style is rescaled until its measured feature spacing is within this share of the one asked for. */
    private static final double SCALING_TOLERANCE = 0.1D;
    private static final int MOST_SCALING_ROUNDS = 6;
    private static final Field3 LEVEL = (x, y, z) -> 0D;

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
        NoiseSampler sampler = nextSampler(NoiseStyle.of(STYLE));
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
        NoiseSampler sampler = nextSampler(NoiseStyle.of(STYLE));
        double scale = SIMPLEX_ZERO_SPACING_BLOCKS / spacing;
        return (x, y, z) -> sampler.signed(x * scale, z * scale);
    }

    /** A pack's style read in blocks as the pack wrote it, its zoom included. A flat style gives a level field at 0. */
    Field3 inBlocks(NoiseStyle style) {
        if (style.isFlat()) {
            nextSeed();
            return LEVEL;
        }
        NoiseSampler sampler = nextSampler(style);
        return sampler::signed;
    }

    /** A pack's style rescaled so its features lie about {@code spacing} blocks apart horizontally and {@code verticalSpacing} vertically. */
    Field3 scaledTo(NoiseStyle style, double spacing, double verticalSpacing) {
        if (style.isFlat()) {
            nextSeed();
            return LEVEL;
        }
        NoiseSampler sampler = scaledToSpacing(nextSampler(style), style, spacing);
        double vertical = spacing / verticalSpacing;
        return (x, y, z) -> sampler.signed(x, y * vertical, z);
    }

    /** {@link #scaledTo} for a field that only varies over X and Z. */
    Field3 columnsScaledTo(NoiseStyle style, double spacing) {
        if (style.isFlat()) {
            nextSeed();
            return LEVEL;
        }
        NoiseSampler sampler = scaledToSpacing(nextSampler(style), style, spacing);
        return (x, y, z) -> sampler.signed(x, z);
    }

    /** How far apart the features of {@code field}, read from {@code style} in blocks, roughly lie. */
    static double featureBlocks(Field3 field, NoiseStyle style) {
        double guess = simplexLikeFeatureBlocks(style.zoom());
        return FieldStats.featureBlocks(field, guess).orElse(guess);
    }

    /**
     * Rescales {@code sampler} until its measured feature spacing matches {@code spacing}, starting from where a style
     * built like SIMPLEX would land. The scale is folded into the noise, so a fracture still moves it in blocks.
     */
    private static NoiseSampler scaledToSpacing(NoiseSampler sampler, NoiseStyle style, double spacing) {
        double factor = simplexLikeFeatureBlocks(style.zoom()) / spacing;
        for (int round = 0; round < MOST_SCALING_ROUNDS; round++) {
            NoiseSampler scaled = sampler.scaled(factor);
            OptionalDouble measured = FieldStats.featureBlocks((x, y, z) -> scaled.signed(x, y, z), spacing);
            if (measured.isEmpty()) {
                return scaled;
            }
            double measuredShare = measured.getAsDouble() / spacing;
            if (Math.abs(measuredShare - 1D) <= SCALING_TOLERANCE) {
                return scaled;
            }
            factor *= measuredShare;
        }
        return sampler.scaled(factor);
    }

    private static double simplexLikeFeatureBlocks(double zoom) {
        return SIMPLEX_ZERO_SPACING_BLOCKS * zoom;
    }

    private NoiseSampler nextSampler(NoiseStyle style) {
        return noise.sampler(style, nextSeed());
    }

    private long nextSeed() {
        fieldsBuilt++;
        return seed + (fieldsBuilt * 1_000_003L);
    }
}
