package art.arcane.iris.engine;

import art.arcane.iris.engine.object.terrain.IrisGenerator;
import art.arcane.volmlib.util.math.M;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisComplexNonFiniteHeightTest {
    private static final double[] NON_FINITE = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

    @Test
    public void finiteBoundsAndNoiseKeepTheAverageUnchanged() {
        CountingGenerator first = new CountingGenerator("a", 0.25D);
        CountingGenerator second = new CountingGenerator("b", 3D);
        double expected = (M.lerp(-9D, 12D, 0.25D) + M.lerp(-9D, 12D, 3D)) / 2;

        double actual = IrisComplex.averageGeneratorHeights(new IrisGenerator[]{first, second}, -9D, 12D, 1D, 2D, 7L);

        assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(actual));
    }

    @Test
    public void nonFiniteBoundsFailBeforeSamplingWithColumnAndGeneratorDetails() {
        for (double invalid : NON_FINITE) {
            for (double[] bounds : new double[][]{{invalid, 1D}, {0D, invalid}, {invalid, invalid}}) {
                CountingGenerator generator = new CountingGenerator("diagnostic/terrain", 0.3D);
                IllegalStateException failure = assertThrows(IllegalStateException.class,
                        () -> IrisComplex.averageGeneratorHeights(new IrisGenerator[]{generator},
                                bounds[0], bounds[1], 671D, 2437D, 7331L));
                assertTrue(failure.getMessage().contains("671.0,2437.0"));
                assertTrue(failure.getMessage().contains("generator=diagnostic/terrain"));
                assertTrue(failure.getMessage().contains("bounds=" + bounds[0] + ".." + bounds[1]));
                assertEquals(0, generator.calls);
            }
        }
    }

    @Test
    public void nonFiniteNoiseFailsAtItsFirstSampleWithObservedValue() {
        for (double invalid : NON_FINITE) {
            CountingGenerator valid = new CountingGenerator("valid", 0.3D);
            CountingGenerator broken = new CountingGenerator("diagnostic/cliffs", invalid);
            CountingGenerator unsampled = new CountingGenerator("unsampled", 0.5D);
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> IrisComplex.averageGeneratorHeights(new IrisGenerator[]{valid, broken, unsampled},
                            0D, 44D, 671D, 2437D, 7331L));
            assertTrue(failure.getMessage().contains("671.0,2437.0"));
            assertTrue(failure.getMessage().contains("generator=diagnostic/cliffs"));
            assertTrue(failure.getMessage().contains("noise=" + invalid));
            assertTrue(failure.getMessage().contains("bounds=0.0..44.0"));
            assertEquals(1, valid.calls);
            assertEquals(1, broken.calls);
            assertEquals(0, unsampled.calls);
        }
    }

    private static final class CountingGenerator extends IrisGenerator {
        private final double noise;
        private int calls;

        private CountingGenerator(String loadKey, double noise) {
            this.noise = noise;
            setLoadKey(loadKey);
        }

        @Override
        public double getHeight(double x, double z, long seed) {
            calls++;
            return noise;
        }
    }
}
