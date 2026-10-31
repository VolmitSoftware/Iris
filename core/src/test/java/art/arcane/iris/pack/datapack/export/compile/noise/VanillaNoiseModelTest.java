package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class VanillaNoiseModelTest {
    private static final int POINTS = 4096;
    private static final long WORLD_SEED = 8_675_309L;
    private static final String NOISE_ID = "iris_export:model_test";
    private static final List<DataVersion> TARGETS = WorldgenExportTargets.SUPPORTED;
    private static final List<Double> FBM = List.of(1.0D, 1.0D, 0.5D, 0.25D);
    private static final double OCTAVE_DEVIATION = 0.2702247831245211D;
    private static final double PERLIN_BOUND = 2.0D;

    @Test
    public void identicalInputsSampleIdentically() {
        for (DataVersion target : TARGETS) {
            VanillaNoiseModel first = VanillaNoiseModel.create(NOISE_ID, -8, FBM, WORLD_SEED, target);
            VanillaNoiseModel second = VanillaNoiseModel.create(NOISE_ID, -8, FBM, WORLD_SEED, target);
            double[][] points = points(11L);
            for (double[] point : points) {
                assertEquals(Double.doubleToRawLongBits(first.sample(point[0], point[1], point[2])),
                        Double.doubleToRawLongBits(second.sample(point[0], point[1], point[2])));
            }
        }
    }

    @Test
    public void seedAndIdentifierSelectDifferentFields() {
        for (DataVersion target : TARGETS) {
            VanillaNoiseModel base = VanillaNoiseModel.create(NOISE_ID, -8, FBM, WORLD_SEED, target);
            VanillaNoiseModel reseeded = VanillaNoiseModel.create(NOISE_ID, -8, FBM, WORLD_SEED + 1L, target);
            VanillaNoiseModel renamed = VanillaNoiseModel.create("iris_export:model_test_b", -8, FBM, WORLD_SEED, target);
            assertTrue(maxDifference(base, reseeded) > 0.0D);
            assertTrue(maxDifference(base, renamed) > 0.0D);
        }
    }

    @Test
    public void unqualifiedIdentifierHashesAsDefaultNamespace() {
        for (DataVersion target : TARGETS) {
            VanillaNoiseModel bare = VanillaNoiseModel.create("model_test", -8, FBM, WORLD_SEED, target);
            VanillaNoiseModel qualified = VanillaNoiseModel.create("minecraft:model_test", -8, FBM, WORLD_SEED, target);
            assertEquals(0.0D, maxDifference(bare, qualified), 0.0D);
        }
    }

    @Test
    public void zeroAmplitudeOctavesAreSkipped() {
        for (DataVersion target : TARGETS) {
            VanillaNoiseModel padded = VanillaNoiseModel.create(NOISE_ID, -9, List.of(0.0D, 0.0D, 1.0D), WORLD_SEED, target);
            VanillaNoiseModel single = VanillaNoiseModel.create(NOISE_ID, -7, List.of(1.0D), WORLD_SEED, target);
            double scale = fittedScale(padded, single);
            assertTrue(scale > 0.0D);
            for (double[] point : points(23L)) {
                double expected = scale * single.sample(point[0], point[1], point[2]);
                assertEquals(expected, padded.sample(point[0], point[1], point[2]), 1.0E-6D);
            }
        }
    }

    @Test
    public void zeroAmplitudesSampleZero() {
        for (DataVersion target : TARGETS) {
            VanillaNoiseModel silent = VanillaNoiseModel.create(NOISE_ID, -6, List.of(0.0D, 0.0D), WORLD_SEED, target);
            for (double[] point : points(31L)) {
                assertEquals(0.0D, silent.sample(point[0], point[1], point[2]), 0.0D);
            }
        }
    }

    @Test
    public void modernSamplesStayWithinDeclaredRange() {
        VanillaNoiseModel model = VanillaNoiseModel.create(NOISE_ID, -8, FBM, WORLD_SEED, DataVersion.V26_3);
        double amplitudeSum = 0.0D;
        double squaredDeviation = 0.0D;
        for (int index = 0; index < FBM.size(); index++) {
            double amplitude = FBM.get(index) / Math.pow(2.0D, index);
            amplitudeSum += amplitude;
            squaredDeviation += Math.pow(OCTAVE_DEVIATION * amplitude, 2.0D);
        }
        double normalization = (amplitudeSum / 3.0D) / (Math.sqrt(squaredDeviation) * Math.sqrt(2.0D));
        assertWithinRange(model, 2.0D * PERLIN_BOUND * normalization * amplitudeSum * (1.0D + 1.0E-6D));
    }

    @Test
    public void modernSamplesAreSinglePrecision() {
        VanillaNoiseModel model = VanillaNoiseModel.create(NOISE_ID, -8, FBM, WORLD_SEED, DataVersion.V26_3);
        for (double[] point : points(43L)) {
            double value = model.sample(point[0], point[1], point[2]);
            assertEquals(value, (float) value, 0.0D);
        }
    }

    @Test
    public void baseAmplitudeScalesOutput() {
        VanillaNoiseModel.OctaveProfile unit = new VanillaNoiseModel.OctaveProfile(-8, FBM.size(), FBM, 1.0D,
                VanillaNoiseModel.Normalization.ENABLED);
        VanillaNoiseModel.OctaveProfile doubled = new VanillaNoiseModel.OctaveProfile(-8, FBM.size(), FBM, 2.0D,
                VanillaNoiseModel.Normalization.ENABLED);
        VanillaNoiseModel first = VanillaNoiseModel.create(NOISE_ID, unit, WORLD_SEED);
        VanillaNoiseModel second = VanillaNoiseModel.create(NOISE_ID, doubled, WORLD_SEED);
        for (double[] point : points(59L)) {
            assertEquals(2.0D * first.sample(point[0], point[1], point[2]), second.sample(point[0], point[1], point[2]), 1.0E-6D);
        }
    }

    @Test
    public void mappedProfileUsesUnitAmplitudeWithoutNormalization() {
        VanillaNoiseModel.OctaveProfile profile = VanillaNoiseModel.OctaveProfile.fromAmplitudes(-8, FBM);
        assertEquals(-8, profile.baseOctave());
        assertEquals(FBM.size(), profile.octaveCount());
        assertEquals(FBM, profile.amplitudeModifiers());
        assertEquals(1.0D, profile.baseAmplitude(), 0.0D);
        assertEquals(VanillaNoiseModel.Normalization.DISABLED, profile.normalization());
    }

    @Test
    public void modernDefinitionsOutsideTheCodecAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> VanillaNoiseModel.create(NOISE_ID, 33, List.of(1.0D), WORLD_SEED, DataVersion.V26_3));
        assertThrows(IllegalArgumentException.class,
                () -> VanillaNoiseModel.create(NOISE_ID, -33, List.of(1.0D), WORLD_SEED, DataVersion.V26_3));
        assertThrows(IllegalArgumentException.class,
                () -> VanillaNoiseModel.create(NOISE_ID, -8, List.of(1.0D, -0.5D), WORLD_SEED, DataVersion.V26_3));
        assertThrows(IllegalArgumentException.class,
                () -> VanillaNoiseModel.create(NOISE_ID, -8, List.of(), WORLD_SEED, DataVersion.V26_3));
        assertThrows(IllegalArgumentException.class,
                () -> VanillaNoiseModel.create(NOISE_ID, -8, Collections.nCopies(33, 1.0D), WORLD_SEED,
                        DataVersion.V26_3));
        assertThrows(IllegalArgumentException.class,
                () -> new VanillaNoiseModel.OctaveProfile(-8, 2, List.of(1.0D), 1.0D, VanillaNoiseModel.Normalization.ENABLED));
        assertThrows(IllegalArgumentException.class,
                () -> new VanillaNoiseModel.OctaveProfile(-8, 1, List.of(), 0.0D, VanillaNoiseModel.Normalization.ENABLED));
    }

    @Test
    public void unsupportedTargetsAreRejected() {
        for (DataVersion older : List.of(DataVersion.V1_21_1, DataVersion.V26_2)) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> VanillaNoiseModel.create(NOISE_ID, -8, FBM, WORLD_SEED, older));
            assertTrue(failure.getMessage().contains(older.getVersion()));
        }
    }

    private static void assertWithinRange(VanillaNoiseModel model, double bound) {
        double peak = 0.0D;
        for (double[] point : points(37L)) {
            double value = model.sample(point[0], point[1], point[2]);
            assertTrue(Double.isFinite(value));
            peak = Math.max(peak, Math.abs(value));
        }
        assertTrue("peak " + peak + " exceeds " + bound, peak <= bound);
        assertTrue("peak " + peak + " is degenerate", peak > bound * 0.05D);
    }

    private static double fittedScale(VanillaNoiseModel target, VanillaNoiseModel reference) {
        double product = 0.0D;
        double square = 0.0D;
        for (double[] point : points(61L)) {
            double referenceValue = reference.sample(point[0], point[1], point[2]);
            product += target.sample(point[0], point[1], point[2]) * referenceValue;
            square += referenceValue * referenceValue;
        }
        return product / square;
    }

    private static double maxDifference(VanillaNoiseModel first, VanillaNoiseModel second) {
        double difference = 0.0D;
        for (double[] point : points(67L)) {
            difference = Math.max(difference,
                    Math.abs(first.sample(point[0], point[1], point[2]) - second.sample(point[0], point[1], point[2])));
        }
        return difference;
    }

    private static double[][] points(long salt) {
        SplittableRandom random = new SplittableRandom(salt);
        double[][] points = new double[POINTS][];
        for (int index = 0; index < POINTS; index++) {
            points[index] = new double[]{random.nextDouble(-60_000.0D, 60_000.0D), random.nextDouble(-64.0D, 320.0D),
                    random.nextDouble(-60_000.0D, 60_000.0D)};
        }
        return points;
    }
}
