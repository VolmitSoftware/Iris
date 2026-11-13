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

import art.arcane.iris.modules.carve.CaveValue;

/**
 * A {@link CaveValue} ready to read at a place: the same number everywhere, or the number its noise picks there. The
 * noise's middle picks the middle of the range and the value reaches min and max half a unit either side of it, so a
 * range has stretches at each end as well as the slopes between them.
 */
final class ValueField {
    private static final double MIDDLE_SHARE = 0.5D;

    private final double min;
    private final double max;
    private final Field3 noise;

    private ValueField(double min, double max, Field3 noise) {
        this.min = min;
        this.max = max;
        this.noise = noise;
    }

    static ValueField constant(double value) {
        return new ValueField(value, value, null);
    }

    /**
     * {@code value} read in 3D, under its own seed of {@code name}. Without a style it changes over about
     * {@code changeBlocks} blocks in every direction.
     */
    static ValueField inBlocks(CaveValue value, CaveNoise caveNoise, String name, double changeBlocks) {
        if (!value.varies()) {
            return constant(value.min());
        }
        CaveNoise noise = caveNoise.named(name);
        Field3 field = value.style().map(noise::inBlocks).orElseGet(() -> noise.smooth(changeBlocks, changeBlocks));
        return new ValueField(value.min(), value.max(), field);
    }

    /** {@link #inBlocks} for a value that only varies over X and Z. */
    static ValueField inColumns(CaveValue value, CaveNoise caveNoise, String name, double changeBlocks) {
        if (!value.varies()) {
            return constant(value.min());
        }
        CaveNoise noise = caveNoise.named(name);
        Field3 field = value.style().map(noise::columnsInBlocks).orElseGet(() -> noise.columns(changeBlocks));
        return new ValueField(value.min(), value.max(), field);
    }

    /** The share of the way from min to max that a noise value picks, from 0 to 1. */
    static double shareAt(double signedNoise) {
        return Math.max(0D, Math.min(1D, MIDDLE_SHARE + signedNoise));
    }

    boolean varies() {
        return noise != null;
    }

    double min() {
        return min;
    }

    double max() {
        return max;
    }

    double middle() {
        return (min + max) / 2D;
    }

    double lowest() {
        return Math.min(min, max);
    }

    double highest() {
        return Math.max(min, max);
    }

    double at(double x, double y, double z) {
        if (noise == null) {
            return min;
        }
        return min + ((max - min) * shareAt(noise.at(x, y, z)));
    }

    /** How far from min towards max the value is here, from 0 to 1; 0 where it does not vary. */
    double share(double x, double y, double z) {
        return noise == null ? 0D : shareAt(noise.at(x, y, z));
    }

    /** {@link #at} for a value read in columns. */
    double at(double x, double z) {
        return at(x, 0D, z);
    }
}
