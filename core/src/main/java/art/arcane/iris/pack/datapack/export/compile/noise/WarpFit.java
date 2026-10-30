package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleFunction;

public final class WarpFit {
    private static final int BASE_POINTS = 2048;
    private static final int INSTANCES = 16;
    private static final long INSTANCE_STRIDE = 0x9E37_79B9L;
    private static final double SPAN = 1 << 18;
    private static final double[] SURVEY_LAGS = RankCorrelation.geometricLags(1.0, 65_536.0, 48, true);
    private static final double FIT_LAG_MINIMUM = 0.25;
    private static final double FIT_LAG_MAXIMUM = 4.0;
    private static final int FIT_LAG_COUNT = 16;
    private static final double SEARCH_RATIO = 2.0;
    private static final int SEARCH_ITERATIONS = 18;
    private static final int MARGINAL_SAMPLES = 65_536;

    private WarpFit() {
    }

    public static LevelFit fit(DoubleBinaryOperator iris, DoubleFunction<Field> lowered, NoiseRegistry scratch, DataVersion target) {
        Objects.requireNonNull(iris, "iris");
        Objects.requireNonNull(lowered, "lowered");
        Objects.requireNonNull(scratch, "scratch");
        Objects.requireNonNull(target, "target");
        double[] survey = RankCorrelation.profile(iris, SURVEY_LAGS, BASE_POINTS, SPAN, true, LeafNoiseFit.FIT_SEED + 11L);
        double half = RankCorrelation.halfCorrelationLag(SURVEY_LAGS, survey);
        double[] lags = RankCorrelation.geometricLags(Math.max(1.0, half * FIT_LAG_MINIMUM), Math.max(2.0, half * FIT_LAG_MAXIMUM), FIT_LAG_COUNT, true);
        double[] irisProfile = RankCorrelation.profile(iris, lags, BASE_POINTS, SPAN, true, LeafNoiseFit.FIT_SEED + 13L);
        int[] window = RankCorrelation.window(irisProfile);
        double logMultiplier = RankCorrelation.goldenMinimum(
                candidate -> error(lowered.apply(Math.exp(candidate)), scratch, target, lags, irisProfile, window),
                -Math.log(SEARCH_RATIO), Math.log(SEARCH_RATIO), SEARCH_ITERATIONS);
        double multiplier = Math.exp(logMultiplier);
        Field fitted = lowered.apply(multiplier);
        double[] loweredMarginal = new double[0];
        for (int instance = 0; instance < INSTANCES; instance++) {
            double[] sampled = marginal(sampler(fitted, scratch, target, instance), MARGINAL_SAMPLES / INSTANCES, instance);
            double[] joined = Arrays.copyOf(loweredMarginal, loweredMarginal.length + sampled.length);
            System.arraycopy(sampled, 0, joined, loweredMarginal.length, sampled.length);
            loweredMarginal = joined;
        }
        List<Field.Knot> distribution = ValueDistributionFit.fit(loweredMarginal, marginal(iris, MARGINAL_SAMPLES, INSTANCES));
        return new LevelFit(multiplier, distribution);
    }

    private static double error(Field field, NoiseRegistry scratch, DataVersion target, double[] lags, double[] irisProfile, int[] window) {
        double[] profile = new double[lags.length];
        for (int instance = 0; instance < INSTANCES; instance++) {
            double[] sampled = RankCorrelation.profile(sampler(field, scratch, target, instance), lags, BASE_POINTS / INSTANCES, SPAN, true,
                    LeafNoiseFit.FIT_SEED + 13L + instance);
            for (int index = 0; index < profile.length; index++) {
                profile[index] += sampled[index] / INSTANCES;
            }
        }
        double sum = 0.0;
        for (int index : window) {
            sum += RankCorrelation.structureError(RankCorrelation.structure(irisProfile[index]), RankCorrelation.structure(profile[index]));
        }
        return sum / window.length;
    }

    private static DoubleBinaryOperator sampler(Field field, NoiseRegistry scratch, DataVersion target, int instance) {
        Map<String, NoiseDef> noises = scratch.byReference();
        long worldSeed = LeafNoiseFit.FIT_SEED + instance * INSTANCE_STRIDE;
        return (x, z) -> FieldEvaluator.sample(field, noises, worldSeed, target, (int) x, 0, (int) z);
    }

    private static double[] marginal(DoubleBinaryOperator field, int count, int stream) {
        SplittableRandom random = new SplittableRandom(LeafNoiseFit.FIT_SEED + 17L + stream);
        double[] samples = new double[count];
        for (int index = 0; index < samples.length; index++) {
            double x = Math.floor((random.nextDouble() * 2.0 - 1.0) * SPAN);
            double z = Math.floor((random.nextDouble() * 2.0 - 1.0) * SPAN);
            samples[index] = field.applyAsDouble(x, z);
        }
        return samples;
    }

    public record LevelFit(double multiplier, List<Field.Knot> distribution) {
        public LevelFit {
            if (!(multiplier > 0.0) || !Double.isFinite(multiplier)) {
                throw new IllegalArgumentException("Level frequency multiplier must be positive and finite, got " + multiplier);
            }
            distribution = List.copyOf(distribution);
        }
    }
}
