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

import java.util.Arrays;

/**
 * What a field's values look like, measured once on fixed sample points so every server measures the same: which value
 * a given share of the world lies above. Shapes turn a share of the underground into a field threshold with it.
 */
final class FieldStats {
    private static final int SAMPLES = 4096;
    /** Samples spread over this many features, far enough apart to be independent. */
    private static final double SPAN_IN_FEATURES = 200D;
    private static final double STEP_X = 0.7548776662466927D;
    private static final double STEP_Y = 0.5698402909980532D;
    private static final double STEP_Z = 0.8191725133961645D;

    private final double[] sortedValues;

    private FieldStats(double[] sortedValues) {
        this.sortedValues = sortedValues;
    }

    /** Measures {@code field}, whose features are about {@code featureBlocks} blocks apart. */
    static FieldStats measure(Field3 field, double featureBlocks) {
        double span = featureBlocks * SPAN_IN_FEATURES;
        double[] values = new double[SAMPLES];
        for (int sample = 0; sample < SAMPLES; sample++) {
            values[sample] = field.at(coordinate(sample, STEP_X, span), coordinate(sample, STEP_Y, span), coordinate(sample, STEP_Z, span));
        }
        Arrays.sort(values);
        return new FieldStats(values);
    }

    private static double coordinate(int sample, double step, double span) {
        double fraction = (sample * step) % 1D;
        return (fraction - 0.5D) * span;
    }

    /** The value that {@code share} of the world lies above, from 0 to 1. */
    double valueAboveShare(double share) {
        double clamped = Math.max(0D, Math.min(1D, share));
        int index = (int) Math.round((1D - clamped) * (sortedValues.length - 1));
        return sortedValues[index];
    }

    double highest() {
        return sortedValues[sortedValues.length - 1];
    }
}
