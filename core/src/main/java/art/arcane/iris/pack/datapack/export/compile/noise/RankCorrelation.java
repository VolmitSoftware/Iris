package art.arcane.iris.pack.datapack.export.compile.noise;

import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

final class RankCorrelation {
    private static final double HALF_CORRELATION = 0.5;
    private static final double MINIMUM_CORRELATION = 0.03;
    private static final double MINIMUM_STRUCTURE = 1.0e-4;
    private static final double STRUCTURE_FLOOR = 1.0e-6;
    private static final double RANK_VARIOGRAM_SCALE = 6.0;

    private RankCorrelation() {
    }

    static double[] profile(DoubleBinaryOperator field, double[] lags, int basePoints, double span, boolean integral, long seed) {
        int stride = 1 + 2 * lags.length;
        double[] values = new double[basePoints * stride];
        SplittableRandom random = new SplittableRandom(seed);
        for (int point = 0; point < basePoints; point++) {
            double x = coordinate(random, span, integral);
            double z = coordinate(random, span, integral);
            int offset = point * stride;
            values[offset] = field.applyAsDouble(x, z);
            for (int lag = 0; lag < lags.length; lag++) {
                values[offset + 1 + 2 * lag] = field.applyAsDouble(x + lags[lag], z);
                values[offset + 2 + 2 * lag] = field.applyAsDouble(x, z + lags[lag]);
            }
        }
        double[] ranks = ranks(values);
        double[] correlations = new double[lags.length];
        for (int lag = 0; lag < lags.length; lag++) {
            double sum = 0.0;
            for (int point = 0; point < basePoints; point++) {
                int offset = point * stride;
                double base = ranks[offset];
                double alongX = base - ranks[offset + 1 + 2 * lag];
                double alongZ = base - ranks[offset + 2 + 2 * lag];
                sum += alongX * alongX + alongZ * alongZ;
            }
            correlations[lag] = 1.0 - RANK_VARIOGRAM_SCALE * sum / (2.0 * basePoints);
        }
        return correlations;
    }

    static double[] geometricLags(double minimum, double maximum, int count, boolean integral) {
        double ratio = Math.pow(maximum / minimum, 1.0 / (count - 1));
        TreeSet<Double> lags = new TreeSet<>();
        double lag = minimum;
        for (int index = 0; index < count; index++) {
            lags.add(integral ? Math.max(1.0, Math.rint(lag)) : lag);
            lag *= ratio;
        }
        double[] result = new double[lags.size()];
        int index = 0;
        for (double value : lags) {
            result[index++] = value;
        }
        return result;
    }

    static double halfCorrelationLag(double[] lags, double[] correlations) {
        for (int index = 0; index < lags.length; index++) {
            if (correlations[index] < HALF_CORRELATION) {
                if (index == 0) {
                    return lags[0];
                }
                double before = correlations[index - 1];
                double fraction = (before - HALF_CORRELATION) / (before - correlations[index]);
                return Math.exp(Math.log(lags[index - 1]) + fraction * (Math.log(lags[index]) - Math.log(lags[index - 1])));
            }
        }
        return lags[lags.length - 1];
    }

    static double structure(double[] lags, double[] correlations, double lag) {
        if (lag <= lags[0]) {
            double first = structure(correlations[0]);
            return Math.exp(Math.log(first) + 2.0 * (Math.log(lag) - Math.log(lags[0])));
        }
        if (lag >= lags[lags.length - 1]) {
            return structure(correlations[correlations.length - 1]);
        }
        int upper = Arrays.binarySearch(lags, lag);
        if (upper >= 0) {
            return structure(correlations[upper]);
        }
        upper = -upper - 1;
        int lower = upper - 1;
        double fraction = (Math.log(lag) - Math.log(lags[lower])) / (Math.log(lags[upper]) - Math.log(lags[lower]));
        double lowerLog = Math.log(structure(correlations[lower]));
        double upperLog = Math.log(structure(correlations[upper]));
        return Math.exp(lowerLog + fraction * (upperLog - lowerLog));
    }

    static int[] window(double[] correlations) {
        int count = 0;
        int[] window = new int[correlations.length];
        for (int index = 0; index < correlations.length; index++) {
            if (correlations[index] >= MINIMUM_CORRELATION && 1.0 - correlations[index] >= MINIMUM_STRUCTURE) {
                window[count++] = index;
            }
        }
        if (count == 0) {
            for (int index = 0; index < correlations.length; index++) {
                window[count++] = index;
            }
        }
        return Arrays.copyOf(window, count);
    }

    static double structureError(double expectedStructure, double actualStructure) {
        double difference = Math.log(Math.max(expectedStructure, STRUCTURE_FLOOR)) - Math.log(Math.max(actualStructure, STRUCTURE_FLOOR));
        return difference * difference;
    }

    static double structure(double correlation) {
        return Math.max(STRUCTURE_FLOOR, 1.0 - correlation);
    }

    static double goldenMinimum(DoubleUnaryOperator objective, double lower, double upper, int iterations) {
        double ratio = (Math.sqrt(5.0) - 1.0) / 2.0;
        double left = upper - ratio * (upper - lower);
        double right = lower + ratio * (upper - lower);
        double leftValue = objective.applyAsDouble(left);
        double rightValue = objective.applyAsDouble(right);
        for (int iteration = 0; iteration < iterations; iteration++) {
            if (leftValue <= rightValue) {
                upper = right;
                right = left;
                rightValue = leftValue;
                left = upper - ratio * (upper - lower);
                leftValue = objective.applyAsDouble(left);
            } else {
                lower = left;
                left = right;
                leftValue = rightValue;
                right = lower + ratio * (upper - lower);
                rightValue = objective.applyAsDouble(right);
            }
        }
        return leftValue <= rightValue ? left : right;
    }

    private static double coordinate(SplittableRandom random, double span, boolean integral) {
        double value = (random.nextDouble() * 2.0 - 1.0) * span;
        return integral ? Math.floor(value) : value;
    }

    private static double[] ranks(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double[] ranks = new double[values.length];
        double count = values.length;
        for (int index = 0; index < values.length; index++) {
            double value = values[index];
            int first = firstIndex(sorted, value);
            int last = lastIndex(sorted, value);
            ranks[index] = (first + last + 1) / (2.0 * count);
        }
        return ranks;
    }

    private static int firstIndex(double[] sorted, double value) {
        int low = 0;
        int high = sorted.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (sorted[middle] < value) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static int lastIndex(double[] sorted, double value) {
        int low = 0;
        int high = sorted.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (sorted[middle] <= value) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low - 1;
    }
}
