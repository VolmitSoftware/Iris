package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.noise.FastNoiseDouble;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleBinaryOperator;

final class IrisSampleFit {
    private static final long FIT_SEED = 1_337L;
    private static final String FIT_NOISE = "iris_export:cell_leaf";
    private static final double VANILLA_PLANE = 4.375D;
    private static final int ROWS = 32;
    private static final int STYLE_SEEDS = 4;
    private static final int VANILLA_SEEDS = 8;
    private static final long SEED_STRIDE = 7_919L;
    private static final int ROW_LENGTH = 1024;
    private static final int LAGS = 256;
    private static final int MARGINAL_AXIS = 64;
    private static final double STYLE_STEP = 1.0D;
    private static final double STYLE_ROW_SPACING = 211.0D;
    private static final double STYLE_MARGINAL_SPACING = 37.0D;
    private static final double UNIT_STEP = 0.025D;
    private static final double UNIT_ROW_SPACING = 7.31D;
    private static final double UNIT_MARGINAL_SPACING = 1.37D;
    private static final double CORRELATION_THRESHOLD = 0.5D;
    private static final double ROW_SHEAR = 1.618033988749895D;
    private static final Map<String, Profile> PROFILES = new ConcurrentHashMap<>();

    private IrisSampleFit() {
    }

    static Profile style(NoiseStyle style) {
        return PROFILES.computeIfAbsent("style/" + style.name(), ignored -> styleProfile(style));
    }

    static Profile simplexSlice() {
        return PROFILES.computeIfAbsent("simplex_slice", ignored -> simplexSliceProfile());
    }

    static Profile vanilla(DataVersion target) {
        return PROFILES.computeIfAbsent("vanilla/" + target.name(), ignored -> vanillaProfile(target));
    }

    static double[] quantiles(double[] sorted, int intervals) {
        double[] quantiles = new double[intervals + 1];
        for (int index = 0; index <= intervals; index++) {
            quantiles[index] = quantile(sorted, (double) index / intervals);
        }
        return quantiles;
    }

    private static Profile styleProfile(NoiseStyle style) {
        List<DoubleBinaryOperator> samplers = new ArrayList<>(STYLE_SEEDS);
        for (int index = 0; index < STYLE_SEEDS; index++) {
            CNG generator = style.create(new RNG(seed(index)));
            samplers.add(generator::noise);
        }
        return measure(samplers, STYLE_STEP, STYLE_ROW_SPACING, STYLE_MARGINAL_SPACING);
    }

    private static Profile simplexSliceProfile() {
        List<DoubleBinaryOperator> samplers = new ArrayList<>(STYLE_SEEDS);
        for (int index = 0; index < STYLE_SEEDS; index++) {
            FastNoiseDouble simplex = new FastNoiseDouble(new RNG(seed(index)).lmax());
            simplex.setFrequency(1.0D);
            samplers.add((x, z) -> 0.5D + 0.5D * simplex.GetSimplex(x, 0.0D, z));
        }
        return measure(samplers, UNIT_STEP, UNIT_ROW_SPACING, UNIT_MARGINAL_SPACING);
    }

    private static Profile vanillaProfile(DataVersion target) {
        List<DoubleBinaryOperator> samplers = new ArrayList<>(VANILLA_SEEDS);
        for (int index = 0; index < VANILLA_SEEDS; index++) {
            VanillaNoiseModel model = VanillaNoiseModel.create(FIT_NOISE, 0, List.of(1.0D), seed(index), target);
            samplers.add((x, z) -> model.sample(x, VANILLA_PLANE, z));
        }
        return measure(samplers, UNIT_STEP, UNIT_ROW_SPACING, UNIT_MARGINAL_SPACING);
    }

    private static long seed(int index) {
        return FIT_SEED + SEED_STRIDE * index;
    }

    private static Profile measure(List<DoubleBinaryOperator> samplers, double step, double rowSpacing, double marginalSpacing) {
        double[] marginal = new double[MARGINAL_AXIS * MARGINAL_AXIS];
        for (int row = 0; row < MARGINAL_AXIS; row++) {
            DoubleBinaryOperator sampler = samplers.get(row % samplers.size());
            for (int column = 0; column < MARGINAL_AXIS; column++) {
                marginal[row * MARGINAL_AXIS + column] = sampler.applyAsDouble(column * marginalSpacing, row * marginalSpacing);
            }
        }
        Arrays.sort(marginal);
        return new Profile(correlationLength(samplers, step, rowSpacing), deviation(marginal), marginal);
    }

    private static double correlationLength(List<DoubleBinaryOperator> samplers, double step, double rowSpacing) {
        double[][] rows = new double[ROWS][ROW_LENGTH + LAGS];
        double sum = 0.0D;
        for (int row = 0; row < ROWS; row++) {
            DoubleBinaryOperator sampler = samplers.get(row % samplers.size());
            for (int column = 0; column < ROW_LENGTH + LAGS; column++) {
                rows[row][column] = sampler.applyAsDouble(row * rowSpacing * ROW_SHEAR + column * step, row * rowSpacing);
            }
            for (int column = 0; column < ROW_LENGTH; column++) {
                sum += rows[row][column];
            }
        }
        double mean = sum / (ROWS * ROW_LENGTH);
        double previous = 1.0D;
        double variance = covariance(rows, mean, 0);
        if (variance <= 0.0D) {
            return LAGS * step;
        }
        for (int lag = 1; lag <= LAGS; lag++) {
            double correlation = covariance(rows, mean, lag) / variance;
            if (correlation < CORRELATION_THRESHOLD) {
                double fraction = (previous - CORRELATION_THRESHOLD) / (previous - correlation);
                return (lag - 1 + fraction) * step;
            }
            previous = correlation;
        }
        return LAGS * step;
    }

    private static double covariance(double[][] rows, double mean, int lag) {
        double sum = 0.0D;
        for (double[] row : rows) {
            for (int column = 0; column < ROW_LENGTH; column++) {
                sum += (row[column] - mean) * (row[column + lag] - mean);
            }
        }
        return sum / (ROWS * ROW_LENGTH);
    }

    private static double deviation(double[] samples) {
        double sum = 0.0D;
        for (double sample : samples) {
            sum += sample;
        }
        double mean = sum / samples.length;
        double squares = 0.0D;
        for (double sample : samples) {
            squares += (sample - mean) * (sample - mean);
        }
        return Math.sqrt(squares / samples.length);
    }

    private static double quantile(double[] sorted, double fraction) {
        double position = fraction * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = Math.min(sorted.length - 1, lower + 1);
        double weight = position - lower;
        return sorted[lower] + (sorted[upper] - sorted[lower]) * weight;
    }

    record Profile(double correlationLength, double deviation, double[] sorted) {
        double quantile(double fraction) {
            return IrisSampleFit.quantile(sorted, fraction);
        }

        double[] quantiles(int intervals) {
            return IrisSampleFit.quantiles(sorted, intervals);
        }
    }
}
