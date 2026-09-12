package art.arcane.iris.generation.simd;

import org.junit.Assume;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SimdKernelsTest {
    private static final double[] BOUNDARY_VALUES = {
            0D, 0.5D, -0.5D, 1.5D, -1.5D, 2.5D, -2.5D, 63.5D, -63.5D, 64.49999D, -64.49999D,
            0.4999999D, -0.4999999D, 255.5D, -255.5D, 319.999D, -64D, 2031.5D, -2031.5D, 0.0001D, -0.0001D
    };

    private static double[] terrainLikeValues(int length) {
        Random random = new Random(42L);
        double[] values = new double[length];
        for (int index = 0; index < length; index++) {
            values[index] = (random.nextDouble() - 0.5D) * 8192D;
        }

        return values;
    }

    @Test
    public void scalarRoundToIntMatchesMathRound() {
        ScalarSimdKernels kernels = new ScalarSimdKernels();
        int[] rounded = new int[BOUNDARY_VALUES.length];
        kernels.roundToInt(BOUNDARY_VALUES, rounded, BOUNDARY_VALUES.length);
        for (int index = 0; index < BOUNDARY_VALUES.length; index++) {
            assertEquals("index " + index + " value " + BOUNDARY_VALUES[index], (int) Math.round(BOUNDARY_VALUES[index]), rounded[index]);
        }
    }

    @Test
    public void scalarSumAndMaxMatchManualLoop() {
        ScalarSimdKernels kernels = new ScalarSimdKernels();
        double[] values = terrainLikeValues(256);
        double expectedSum = 0D;
        double expectedMax = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            expectedSum += value;
            if (value > expectedMax) {
                expectedMax = value;
            }
        }

        assertEquals(expectedSum, kernels.sum(values, values.length), 0D);
        assertEquals(expectedMax, kernels.max(values, values.length), 0D);
    }

    @Test
    public void vectorRoundToIntMatchesScalarOnBoundaries() {
        SimdKernels vector = requireVectorKernels();
        ScalarSimdKernels scalar = new ScalarSimdKernels();
        int[] vectorRounded = new int[BOUNDARY_VALUES.length];
        int[] scalarRounded = new int[BOUNDARY_VALUES.length];
        vector.roundToInt(BOUNDARY_VALUES, vectorRounded, BOUNDARY_VALUES.length);
        scalar.roundToInt(BOUNDARY_VALUES, scalarRounded, BOUNDARY_VALUES.length);
        for (int index = 0; index < BOUNDARY_VALUES.length; index++) {
            assertEquals("index " + index + " value " + BOUNDARY_VALUES[index], scalarRounded[index], vectorRounded[index]);
        }
    }

    @Test
    public void vectorRoundToIntMatchesScalarOnTerrainSweep() {
        SimdKernels vector = requireVectorKernels();
        ScalarSimdKernels scalar = new ScalarSimdKernels();
        for (int length : new int[]{256, 251, 1, 7}) {
            double[] values = terrainLikeValues(length);
            int[] vectorRounded = new int[length];
            int[] scalarRounded = new int[length];
            vector.roundToInt(values, vectorRounded, length);
            scalar.roundToInt(values, scalarRounded, length);
            for (int index = 0; index < length; index++) {
                assertEquals("length " + length + " index " + index + " value " + values[index], scalarRounded[index], vectorRounded[index]);
            }
        }
    }

    @Test
    public void vectorSumMatchesScalarExactly() {
        // Zero tolerance on purpose: sum feeds generation-path ranking, so any kernel that
        // reassociates FP addition must fail here instead of shipping a parity break.
        SimdKernels vector = requireVectorKernels();
        ScalarSimdKernels scalar = new ScalarSimdKernels();
        for (int length : new int[]{256, 251, 1, 7}) {
            double[] values = terrainLikeValues(length);
            double expected = scalar.sum(values, length);
            double actual = vector.sum(values, length);
            assertEquals("length " + length, expected, actual, 0D);
        }
    }

    @Test
    public void vectorMaxMatchesScalarExactly() {
        SimdKernels vector = requireVectorKernels();
        ScalarSimdKernels scalar = new ScalarSimdKernels();
        for (int length : new int[]{256, 251, 1, 7}) {
            double[] values = terrainLikeValues(length);
            assertEquals("length " + length, scalar.max(values, length), vector.max(values, length), 0D);
        }
    }

    @Test
    public void vectorMaxOfAllZeroWeightsIsZero() {
        SimdKernels vector = requireVectorKernels();
        double[] weights = new double[256];
        assertEquals(0D, vector.max(weights, weights.length), 0D);
        assertEquals(0D, vector.sum(weights, weights.length), 0D);
    }

    private static SimdKernels requireVectorKernels() {
        Assume.assumeTrue("jdk.incubator.vector module not present in test JVM", SimdSupport.isVectorModulePresent());
        SimdKernels vector = SimdSupport.createVectorKernels();
        assertNotNull("vector kernels failed to instantiate despite module being present", vector);
        return vector;
    }
}
