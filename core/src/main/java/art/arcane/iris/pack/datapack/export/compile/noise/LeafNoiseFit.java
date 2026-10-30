package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.noise.NoiseType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.function.DoubleBinaryOperator;

public final class LeafNoiseFit {
    static final long FIT_SEED = 0x5EED_1EAFL;
    private static final String FIT_NOISE_ID = "iris_fit:leaf";
    private static final int MARGINAL_SAMPLES = 65_536;
    private static final int PROFILE_POINTS = 4096;
    private static final int INSTANCES = 16;
    private static final long INSTANCE_STRIDE = 0x9E37_79B9L;
    private static final double IRIS_SPAN = 1 << 20;
    private static final double VANILLA_SPAN = 1 << 15;
    private static final double SEARCH_RATIO = 3.0;
    private static final int SEARCH_ITERATIONS = 40;
    private static final double[] IRIS_LAGS = RankCorrelation.geometricLags(1.0, 4096.0, 72, true);
    private static final double[] VANILLA_LAGS = RankCorrelation.geometricLags(0.002, 64.0, 160, false);
    private static final double[] TAIL_AMPLITUDES = {0.0, 0.25, 0.5, 0.75, 1.0};
    private static final int TOPOLOGY_SIZE = 256;
    private static final double TOPOLOGY_PIXELS_PER_HALF = 4.0;
    private static final double TOPOLOGY_WEIGHT = 0.05;
    private static final double TOPOLOGY_ORIGIN = 4096.0;
    private static final double LOWER_TAIL = 0.15;
    private static final double UPPER_TAIL = 0.85;

    private LeafNoiseFit() {
    }

    public static FittedLeaf fit(NoiseType type, int octaves, DataVersion target) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
        if (NoiseLeaves.isCellular(type) || type == NoiseType.FLAT) {
            throw new IllegalArgumentException("Noise type " + type + " is not a smooth leaf");
        }
        int effectiveOctaves = NoiseLeaves.effectiveOctaves(type, octaves);
        double coordinateScale = type.getCoordinateScale();
        CNG generator = new CNG(new RNG(FIT_SEED), type, 1.0, effectiveOctaves);
        DoubleBinaryOperator iris = (x, z) -> generator.noise(x * coordinateScale, z * coordinateScale);
        double[] irisMarginal = marginal(iris, IRIS_SPAN, FIT_SEED, MARGINAL_SAMPLES);
        double[] irisProfile = RankCorrelation.profile(iris, IRIS_LAGS, PROFILE_POINTS, IRIS_SPAN, true, FIT_SEED + 1L);
        double irisHalf = RankCorrelation.halfCorrelationLag(IRIS_LAGS, irisProfile);
        int[] window = RankCorrelation.window(irisProfile);
        double spacing = irisHalf / TOPOLOGY_PIXELS_PER_HALF;
        int irisFragments = fragments((x, z) -> iris.applyAsDouble(TOPOLOGY_ORIGIN + x * spacing, TOPOLOGY_ORIGIN + z * spacing));
        Candidate best = null;
        for (List<Double> profile : profiles(type, effectiveOctaves)) {
            Candidate candidate = candidate(profile, target, irisProfile, irisHalf, window, spacing, irisFragments);
            if (best == null || candidate.error() < best.error()) {
                best = candidate;
            }
        }
        Objects.requireNonNull(best, "candidate");
        List<Field.Knot> distribution = ValueDistributionFit.fit(best.marginal(), irisMarginal);
        NoiseDef noise = new NoiseDef(leafId(type, effectiveOctaves), 0, best.amplitudes());
        return new FittedLeaf(noise, best.frequency() / coordinateScale, distribution);
    }

    static String leafId(NoiseType type, int octaves) {
        return type.name().toLowerCase(Locale.ROOT) + "_" + octaves;
    }

    private static List<List<Double>> profiles(NoiseType type, int octaves) {
        List<Double> base = NoiseLeaves.isOctaveSum(type) ? Collections.nCopies(octaves, 1.0) : List.of(1.0);
        List<List<Double>> profiles = new ArrayList<>(TAIL_AMPLITUDES.length);
        for (double tail : TAIL_AMPLITUDES) {
            List<Double> profile = new ArrayList<>(base);
            if (tail > 0.0) {
                profile.add(tail);
            }
            profiles.add(List.copyOf(profile));
        }
        return profiles;
    }

    private static Candidate candidate(List<Double> amplitudes, DataVersion target, double[] irisProfile, double irisHalf, int[] window,
                                       double spacing, int irisFragments) {
        Ensemble ensemble = ensemble(amplitudes, target);
        double vanillaHalf = RankCorrelation.halfCorrelationLag(VANILLA_LAGS, ensemble.profile());
        double guess = Math.log(vanillaHalf / irisHalf);
        double logFrequency = RankCorrelation.goldenMinimum(
                candidate -> error(irisProfile, window, ensemble.profile(), Math.exp(candidate)),
                guess - Math.log(SEARCH_RATIO), guess + Math.log(SEARCH_RATIO), SEARCH_ITERATIONS);
        double frequency = Math.exp(logFrequency);
        double topology = Math.log(Math.max(1.0, fragments(ensemble.instances(), spacing * frequency)) / Math.max(1, irisFragments));
        double score = error(irisProfile, window, ensemble.profile(), frequency) + TOPOLOGY_WEIGHT * topology * topology;
        return new Candidate(amplitudes, frequency, score, ensemble.marginal());
    }

    private static Ensemble ensemble(List<Double> amplitudes, DataVersion target) {
        double[] marginal = new double[0];
        double[] profile = new double[VANILLA_LAGS.length];
        List<DoubleBinaryOperator> instances = new ArrayList<>(INSTANCES);
        for (int instance = 0; instance < INSTANCES; instance++) {
            VanillaNoiseModel model = VanillaNoiseModel.create(FIT_NOISE_ID, 0, amplitudes, FIT_SEED + instance * INSTANCE_STRIDE, target);
            DoubleBinaryOperator vanilla = (x, z) -> model.sample(x, 0.0, z);
            instances.add(vanilla);
            marginal = concat(marginal, marginal(vanilla, VANILLA_SPAN, FIT_SEED + 2L + instance, MARGINAL_SAMPLES / INSTANCES));
            double[] sampled = RankCorrelation.profile(vanilla, VANILLA_LAGS, PROFILE_POINTS / INSTANCES, VANILLA_SPAN, false, FIT_SEED + 3L + instance);
            for (int index = 0; index < profile.length; index++) {
                profile[index] += sampled[index] / INSTANCES;
            }
        }
        return new Ensemble(List.copyOf(instances), marginal, profile);
    }

    private static double fragments(List<DoubleBinaryOperator> instances, double pixel) {
        double total = 0.0;
        for (DoubleBinaryOperator vanilla : instances) {
            total += fragments((x, z) -> vanilla.applyAsDouble(TOPOLOGY_ORIGIN + x * pixel, TOPOLOGY_ORIGIN + z * pixel));
        }
        return total / instances.size();
    }

    private static double error(double[] irisProfile, int[] window, double[] vanillaProfile, double frequency) {
        double sum = 0.0;
        for (int index : window) {
            double expected = RankCorrelation.structure(irisProfile[index]);
            double actual = RankCorrelation.structure(VANILLA_LAGS, vanillaProfile, IRIS_LAGS[index] * frequency);
            sum += RankCorrelation.structureError(expected, actual);
        }
        return sum / window.length;
    }

    private static double[] marginal(DoubleBinaryOperator field, double span, long seed, int count) {
        SplittableRandom random = new SplittableRandom(seed);
        double[] samples = new double[count];
        for (int index = 0; index < samples.length; index++) {
            double x = (random.nextDouble() * 2.0 - 1.0) * span;
            double z = (random.nextDouble() * 2.0 - 1.0) * span;
            samples[index] = field.applyAsDouble(x, z);
        }
        return samples;
    }

    private static double[] concat(double[] first, double[] second) {
        double[] joined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    private static int fragments(DoubleBinaryOperator field) {
        double[] values = new double[TOPOLOGY_SIZE * TOPOLOGY_SIZE];
        for (int z = 0; z < TOPOLOGY_SIZE; z++) {
            for (int x = 0; x < TOPOLOGY_SIZE; x++) {
                values[z * TOPOLOGY_SIZE + x] = field.applyAsDouble(x, z);
            }
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double lower = ValueDistributionFit.quantile(sorted, LOWER_TAIL);
        double upper = ValueDistributionFit.quantile(sorted, UPPER_TAIL);
        return components(values, upper, true) + components(values, lower, false);
    }

    private static int components(double[] values, double threshold, boolean above) {
        boolean[] visited = new boolean[values.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int count = 0;
        for (int start = 0; start < values.length; start++) {
            if (visited[start] || !inside(values[start], threshold, above)) {
                continue;
            }
            count++;
            visited[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int cell = queue.poll();
                int x = cell % TOPOLOGY_SIZE;
                int z = cell / TOPOLOGY_SIZE;
                visit(values, visited, queue, x > 0 ? cell - 1 : -1, threshold, above);
                visit(values, visited, queue, x < TOPOLOGY_SIZE - 1 ? cell + 1 : -1, threshold, above);
                visit(values, visited, queue, z > 0 ? cell - TOPOLOGY_SIZE : -1, threshold, above);
                visit(values, visited, queue, z < TOPOLOGY_SIZE - 1 ? cell + TOPOLOGY_SIZE : -1, threshold, above);
            }
        }
        return count;
    }

    private static void visit(double[] values, boolean[] visited, ArrayDeque<Integer> queue, int cell, double threshold, boolean above) {
        if (cell >= 0 && !visited[cell] && inside(values[cell], threshold, above)) {
            visited[cell] = true;
            queue.add(cell);
        }
    }

    private static boolean inside(double value, double threshold, boolean above) {
        return above ? value > threshold : value < threshold;
    }

    public record FittedLeaf(NoiseDef noise, double xzScale, List<Field.Knot> distribution) {
        public FittedLeaf {
            Objects.requireNonNull(noise, "noise");
            distribution = List.copyOf(distribution);
            if (!(xzScale > 0.0) || !Double.isFinite(xzScale)) {
                throw new IllegalArgumentException("Fitted leaf xz scale must be positive and finite, got " + xzScale);
            }
        }

        public Field.Curve curve(Field input) {
            return new Field.Curve(input, distribution);
        }
    }

    private record Candidate(List<Double> amplitudes, double frequency, double error, double[] marginal) {
    }

    private record Ensemble(List<DoubleBinaryOperator> instances, double[] marginal, double[] profile) {
    }
}
