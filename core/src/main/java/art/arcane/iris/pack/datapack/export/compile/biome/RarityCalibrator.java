package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.function.DoubleBinaryOperator;

public final class RarityCalibrator {
    private static final int SAMPLES = 8192;
    private static final double WINDOW_FEATURES = 96.0;
    private static final double MAX_WINDOW_BLOCKS = 4_000_000.0;
    private static final long POSITION_SEED = 0x5EED_B10EL;
    private static final long WORLD_SEED = 0x1215_B10EL;

    private final NoiseRegistry registry;
    private final DataVersion target;

    public RarityCalibrator(NoiseRegistry registry, DataVersion target) {
        this.registry = Objects.requireNonNull(registry);
        this.target = Objects.requireNonNull(target);
    }

    public Calibration calibrate(DoubleBinaryOperator iris, Field compiled, double featureBlocks, long salt) {
        Objects.requireNonNull(iris);
        Objects.requireNonNull(compiled);
        double window = Math.min(MAX_WINDOW_BLOCKS, Math.max(1.0, featureBlocks) * WINDOW_FEATURES);
        Map<String, NoiseDef> noises = noiseDefinitions();
        SplittableRandom positions = new SplittableRandom(POSITION_SEED ^ salt);
        double[] irisSamples = new double[SAMPLES];
        double[] compiledSamples = new double[SAMPLES];
        for (int index = 0; index < SAMPLES; index++) {
            int x = (int) Math.floor((positions.nextDouble() - 0.5) * window);
            int z = (int) Math.floor((positions.nextDouble() - 0.5) * window);
            irisSamples[index] = iris.applyAsDouble(x, z);
            compiledSamples[index] = compiled instanceof Field.Const constant
                    ? constant.value()
                    : FieldEvaluator.sample(compiled, noises, WORLD_SEED, target, x, 0, z);
        }
        Arrays.sort(irisSamples);
        Arrays.sort(compiledSamples);
        return new Calibration(irisSamples, compiledSamples);
    }

    private Map<String, NoiseDef> noiseDefinitions() {
        List<NoiseDef> definitions = registry.definitions();
        Map<String, NoiseDef> noises = new HashMap<>(definitions.size() * 2);
        for (NoiseDef definition : definitions) {
            noises.put(registry.namespace() + ":" + definition.id(), definition);
        }
        return noises;
    }

    public static final class Calibration {
        private final double[] iris;
        private final double[] compiled;

        private Calibration(double[] iris, double[] compiled) {
            this.iris = iris;
            this.compiled = compiled;
        }

        public double cumulative(double irisValue) {
            int low = 0;
            int high = iris.length;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (iris[middle] < irisValue) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            return low / (double) iris.length;
        }

        public double threshold(double probability) {
            return quantile(compiled, probability);
        }

        public double irisQuantile(double probability) {
            return quantile(iris, probability);
        }

        public List<Double> thresholds(List<Double> irisCuts) {
            List<Double> thresholds = new ArrayList<>(irisCuts.size());
            for (double cut : irisCuts) {
                thresholds.add(threshold(cumulative(cut)));
            }
            return thresholds;
        }

        private static double quantile(double[] sorted, double probability) {
            double position = Math.clamp(probability, 0.0, 1.0) * sorted.length - 0.5;
            if (position <= 0.0) {
                return sorted[0];
            }
            if (position >= sorted.length - 1) {
                return sorted[sorted.length - 1];
            }
            int lower = (int) Math.floor(position);
            double fraction = position - lower;
            return sorted[lower] + fraction * (sorted[lower + 1] - sorted[lower]);
        }
    }
}
