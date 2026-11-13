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
import java.util.OptionalDouble;

/**
 * What a field's values look like, measured once on fixed sample points so every server measures the same: which value
 * a given share of the world lies above, and how far apart its features lie. Shapes turn a share of the underground
 * into a field threshold with it, and a pack's noise style into a size in blocks.
 */
final class FieldStats {
    private static final int SAMPLES = 4096;
    /** Samples spread over this many features, far enough apart to be independent. */
    private static final double SPAN_IN_FEATURES = 200D;
    private static final double STEP_X = 0.7548776662466927D;
    private static final double STEP_Y = 0.5698402909980532D;
    private static final double STEP_Z = 0.8191725133961645D;
    private static final int LINES = 64;
    private static final int LINE_SAMPLES = 128;
    private static final double LINE_STEPS_PER_FEATURE = 16D;
    private static final int FEWEST_CROSSINGS = 16;

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

    /**
     * How far apart the features of {@code field} lie in blocks, measured as the mean distance between two crossings
     * of its middle value along horizontal lines. Lines are read in steps of a fraction of {@code expectedBlocks}, so a
     * field far finer than expected measures too coarse; measuring again at the answer corrects that. Empty for a field
     * that hardly crosses its middle, such as a level one.
     */
    static OptionalDouble featureBlocks(Field3 field, double expectedBlocks) {
        double step = expectedBlocks / LINE_STEPS_PER_FEATURE;
        double span = expectedBlocks * SPAN_IN_FEATURES;
        double[] values = new double[LINES * LINE_SAMPLES];
        for (int line = 0; line < LINES; line++) {
            double startX = coordinate(line, STEP_X, span);
            double y = coordinate(line, STEP_Y, span);
            double startZ = coordinate(line, STEP_Z, span);
            boolean alongX = (line & 1) == 0;
            for (int sample = 0; sample < LINE_SAMPLES; sample++) {
                double along = sample * step;
                values[(line * LINE_SAMPLES) + sample] = alongX ? field.at(startX + along, y, startZ) : field.at(startX, y, startZ + along);
            }
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double middle = sorted[sorted.length / 2];
        int crossings = 0;
        for (int index = 1; index < values.length; index++) {
            boolean sameLine = index % LINE_SAMPLES != 0;
            if (sameLine && (values[index - 1] >= middle) != (values[index] >= middle)) {
                crossings++;
            }
        }
        if (crossings < FEWEST_CROSSINGS) {
            return OptionalDouble.empty();
        }
        double measuredLength = LINES * (LINE_SAMPLES - 1) * step;
        return OptionalDouble.of(measuredLength / crossings);
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
