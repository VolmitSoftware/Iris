package art.arcane.iris.pack.datapack.export.compile.decoration;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.compile.noise.VanillaNoiseModel;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;

import java.util.Arrays;
import java.util.List;

final class DecoratorCoverage {
    static final int PATTERN_OCTAVE = 0;
    static final List<Double> PATTERN_AMPLITUDES = List.of(1.0);
    private static final long STYLE_SEED = 0x0DEC_0A7EL;
    private static final String PATTERN_NOISE = "iris_export:decorator_pattern";
    private static final int GRID = 128;
    private static final double STEP_X = 211.0;
    private static final double STEP_Z = 157.0;
    private static final int[] LAGS = {1, 2, 4, 8, 16, 32, 64, 128};
    private static final double HALF_EXCESS = 0.5;
    private static final double MIN_PATCH = 2.0;
    private static final int MIN_HITS = 64;
    private static final double VANILLA_SCALE = 1.0 / 64.0;
    private static final double VANILLA_Y = 64.0;
    private static final double MAX_THRESHOLD = 1.0;

    private DecoratorCoverage() {
    }

    static Coverage measure(IrisGeneratorStyle style, double chance, IrisData data) {
        CNG generator = style.createNoCache(new RNG(STYLE_SEED), data);
        double zoom = style.getZoom();
        return measure((x, z) -> generator.fitDouble(0.0, 1.0, x / zoom, z / zoom) <= chance);
    }

    static Pattern pattern(Coverage coverage, DataVersion target) {
        VanillaNoiseModel model = VanillaNoiseModel.create(PATTERN_NOISE, PATTERN_OCTAVE, PATTERN_AMPLITUDES, 0L, target);
        double[] sorted = new double[GRID * GRID];
        int index = 0;
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                sorted[index++] = model.sample(i * STEP_X * VANILLA_SCALE, VANILLA_Y * VANILLA_SCALE, j * STEP_Z * VANILLA_SCALE);
            }
        }
        Arrays.sort(sorted);
        double position = coverage.share() * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = Math.min(sorted.length - 1, lower + 1);
        double threshold = sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower);
        double clamped = Math.max(-MAX_THRESHOLD, Math.min(MAX_THRESHOLD, threshold));
        Coverage vanilla = measure((x, z) -> model.sample(x * VANILLA_SCALE, VANILLA_Y * VANILLA_SCALE, z * VANILLA_SCALE) < clamped);
        return new Pattern(clamped, VANILLA_SCALE * vanilla.patch() / coverage.patch());
    }

    private static Coverage measure(Gate gate) {
        int hits = 0;
        int[] both = new int[LAGS.length];
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                double x = i * STEP_X;
                double z = j * STEP_Z;
                if (!gate.passes(x, z)) {
                    continue;
                }
                hits++;
                for (int lag = 0; lag < LAGS.length; lag++) {
                    both[lag] += gate.passes(x + LAGS[lag], z) ? 1 : 0;
                    both[lag] += gate.passes(x, z + LAGS[lag]) ? 1 : 0;
                }
            }
        }
        double share = (double) hits / (GRID * GRID);
        if (hits < MIN_HITS || share >= 1.0) {
            return new Coverage(share, 0.0);
        }
        return new Coverage(share, patch(both, hits, share));
    }

    private static double patch(int[] both, int hits, double share) {
        double previousLag = 0.0;
        double previousExcess = 1.0;
        for (int lag = 0; lag < LAGS.length; lag++) {
            double excess = ((double) both[lag] / (2.0 * hits) - share) / (1.0 - share);
            if (excess < HALF_EXCESS) {
                double fraction = (previousExcess - HALF_EXCESS) / (previousExcess - excess);
                if (previousLag == 0.0) {
                    return LAGS[lag] * fraction;
                }
                double low = Math.log(previousLag);
                return Math.exp(low + (Math.log(LAGS[lag]) - low) * fraction);
            }
            previousLag = LAGS[lag];
            previousExcess = excess;
        }
        return LAGS[LAGS.length - 1];
    }

    record Coverage(double share, double patch) {
        boolean patterned() {
            return share > 0.0 && share < 1.0 && patch >= MIN_PATCH;
        }
    }

    record Pattern(double threshold, double scale) {
    }

    @FunctionalInterface
    private interface Gate {
        boolean passes(double x, double z);
    }
}
