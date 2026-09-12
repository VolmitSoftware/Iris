package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisInterpolator;
import art.arcane.volmlib.util.interpolation.NoiseBounds;
import art.arcane.volmlib.util.interpolation.NoiseBoundsProvider;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

@RunWith(Enclosed.class)
public class IrisComplexGridBoundsCacheTest {
    public static class CornerCache {
    @Test
        public void gridBoundsCacheIsIsolatedPerComplex() throws Exception {
            IrisComplex first = createComplex();
            IrisComplex second = createComplex();
            Method cornerBounds = cornerBoundsMethod();

            long firstPacked = invokeCornerBounds(first, cornerBounds, new CountingInterpolator(1.25D, 2.5D), 64, -32);
            long secondPacked = invokeCornerBounds(second, cornerBounds, new CountingInterpolator(10.25D, 20.5D), 64, -32);

            assertNotEquals(firstPacked, secondPacked);
            assertEquals(1.25F, unpackLow(firstPacked), 0D);
            assertEquals(2.5F, unpackHigh(firstPacked), 0D);
            assertEquals(10.25F, unpackLow(secondPacked), 0D);
            assertEquals(20.5F, unpackHigh(secondPacked), 0D);
        }

    @Test
        public void gridBoundsCacheReusesCornersWithinSameComplex() throws Exception {
            IrisComplex complex = createComplex();
            Method cornerBounds = cornerBoundsMethod();
            CountingInterpolator interpolator = new CountingInterpolator(3.5D, 7.25D);

            long firstPacked = invokeCornerBounds(complex, cornerBounds, interpolator, 128, 96);
            long secondPacked = invokeCornerBounds(complex, cornerBounds, interpolator, 128, 96);

            assertEquals(firstPacked, secondPacked);
            assertEquals(1, interpolator.getInvocations());
        }

    @Test
        public void nonFiniteCornerBoundsAreNeverCached() throws Exception {
            IrisComplex complex = createComplex();
            Method cornerBounds = cornerBoundsMethod();
            CountingInterpolator interpolator = new CountingInterpolator(Double.NaN, 7.25D);

            long firstPacked = invokeCornerBounds(complex, cornerBounds, interpolator, 128, 96);
            long secondPacked = invokeCornerBounds(complex, cornerBounds, interpolator, 128, 96);

            assertTrue(Double.isNaN(unpackLow(firstPacked)));
            assertTrue(Double.isNaN(unpackLow(secondPacked)));
            assertEquals(2, interpolator.getInvocations());
        }

    @Test
        public void alignedGridSampleUsesOnlyTheContributingCorner() throws Exception {
            IrisComplex complex = createComplex();
            Method gridSampleBounds = gridSampleBoundsMethod();
            CountingInterpolator interpolator = new CountingInterpolator(3.5D, 7.25D);

            NoiseBounds bounds = invokeGridSampleBounds(complex, gridSampleBounds, interpolator, 64D, -32D);

            assertEquals(3.5F, bounds.min(), 0D);
            assertEquals(7.25F, bounds.max(), 0D);
            assertEquals(1, interpolator.getInvocations());
        }
    }

    @RunWith(Parameterized.class)
    public static class LegacyBilerpParity {
        @Parameters(name = "x={0} z={1}")
        public static Collection<Object[]> samples() {
            return List.of(
                    new Object[]{67D, -32D, 2},
                    new Object[]{-32D, -29D, 2},
                    new Object[]{-29D, 67D, 4}
            );
        }

        private final double x;
        private final double z;
        private final int expectedInvocations;

        public LegacyBilerpParity(double x, double z, int expectedInvocations) {
            this.x = x;
            this.z = z;
            this.expectedInvocations = expectedInvocations;
        }

        @Test
        public void gridSampleMatchesLegacyBilerpBitForBit() throws Exception {
            IrisComplex complex = createComplex();
            Method gridSampleBounds = gridSampleBoundsMethod();
            CoordinateInterpolator interpolator = new CoordinateInterpolator();

            NoiseBounds actual = invokeGridSampleBounds(complex, gridSampleBounds, interpolator, x, z);
            NoiseBounds expected = legacyGridSampleBounds(x, z);

            assertBoundsBitsEqual(expected, actual);
            assertEquals(expectedInvocations, interpolator.getInvocations());
        }
    }

    private static IrisComplex createComplex() throws Exception {
        IrisComplex complex = mock(IrisComplex.class, CALLS_REAL_METHODS);

        Field generatorBounds = IrisComplex.class.getDeclaredField("generatorBounds");
        generatorBounds.setAccessible(true);
        generatorBounds.set(complex, new HashMap<>());

        Class<?> cacheClass = Class.forName("art.arcane.iris.generation.runtime.IrisComplex$GridBoundsCache");
        Constructor<?> cacheConstructor = cacheClass.getDeclaredConstructor();
        cacheConstructor.setAccessible(true);
        ThreadLocal<Object> cache = ThreadLocal.withInitial(() -> newCache(cacheConstructor));
        Field gridBoundsCache = IrisComplex.class.getDeclaredField("gridBoundsCache");
        gridBoundsCache.setAccessible(true);
        gridBoundsCache.set(complex, cache);
        return complex;
    }

    private static Object newCache(Constructor<?> constructor) {
        try {
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Method cornerBoundsMethod() throws Exception {
        Class<?> cacheClass = Class.forName("art.arcane.iris.generation.runtime.IrisComplex$GridBoundsCache");
        Method method = IrisComplex.class.getDeclaredMethod(
                "cornerBounds",
                cacheClass,
                Engine.class,
                IrisInterpolator.class,
                int.class,
                IrisGenerator[].class,
                int.class,
                int.class
        );
        method.setAccessible(true);
        return method;
    }

    private static Method gridSampleBoundsMethod() throws Exception {
        Method method = IrisComplex.class.getDeclaredMethod(
                "gridSampleBounds",
                Engine.class,
                IrisInterpolator.class,
                int.class,
                IrisGenerator[].class,
                double.class,
                double.class
        );
        method.setAccessible(true);
        return method;
    }

    private static long invokeCornerBounds(IrisComplex complex, Method method, IrisInterpolator interpolator, int x, int z) throws Exception {
        Field gridBoundsCache = IrisComplex.class.getDeclaredField("gridBoundsCache");
        gridBoundsCache.setAccessible(true);
        ThreadLocal<?> cache = (ThreadLocal<?>) gridBoundsCache.get(complex);
        return (long) method.invoke(complex, cache.get(), null, interpolator, 0, new IrisGenerator[0], x, z);
    }

    private static NoiseBounds invokeGridSampleBounds(
            IrisComplex complex,
            Method method,
            IrisInterpolator interpolator,
            double x,
            double z
    ) throws Exception {
        return (NoiseBounds) method.invoke(complex, null, interpolator, 0, new IrisGenerator[0], x, z);
    }

    private static NoiseBounds legacyGridSampleBounds(double x, double z) {
        int grid = 4;
        int xi = (int) Math.floor(x);
        int zi = (int) Math.floor(z);
        int mask = grid - 1;
        int gx = xi & ~mask;
        int gz = zi & ~mask;
        double fx = (x - gx) / grid;
        double fz = (z - gz) / grid;
        NoiseBounds b00 = packedCoordinateBounds(gx, gz);
        NoiseBounds b10 = packedCoordinateBounds(gx + grid, gz);
        NoiseBounds b01 = packedCoordinateBounds(gx, gz + grid);
        NoiseBounds b11 = packedCoordinateBounds(gx + grid, gz + grid);
        return new NoiseBounds(
                legacyBiLerp(b00.min(), b10.min(), b01.min(), b11.min(), fx, fz),
                legacyBiLerp(b00.max(), b10.max(), b01.max(), b11.max(), fx, fz)
        );
    }

    private static NoiseBounds packedCoordinateBounds(int x, int z) {
        return new NoiseBounds(
                (float) CoordinateInterpolator.low(x, z),
                (float) CoordinateInterpolator.high(x, z)
        );
    }

    private static double legacyBiLerp(
            double v00,
            double v10,
            double v01,
            double v11,
            double fx,
            double fz
    ) {
        double a = v00 + ((v10 - v00) * fx);
        double b = v01 + ((v11 - v01) * fx);
        return a + ((b - a) * fz);
    }

    private static void assertBoundsBitsEqual(NoiseBounds expected, NoiseBounds actual) {
        assertEquals(Double.doubleToRawLongBits(expected.min()), Double.doubleToRawLongBits(actual.min()));
        assertEquals(Double.doubleToRawLongBits(expected.max()), Double.doubleToRawLongBits(actual.max()));
    }

    private static float unpackLow(long packed) {
        return Float.intBitsToFloat((int) (packed >>> 32));
    }

    private static float unpackHigh(long packed) {
        return Float.intBitsToFloat((int) packed);
    }

    private static final class CountingInterpolator extends IrisInterpolator {
        private final NoiseBounds bounds;
        private final AtomicInteger invocations = new AtomicInteger();

        private CountingInterpolator(double low, double high) {
            bounds = new NoiseBounds(low, high);
        }

        @Override
        public NoiseBounds interpolateBounds(double x, double z, NoiseBoundsProvider provider) {
            invocations.incrementAndGet();
            return bounds;
        }

        private int getInvocations() {
            return invocations.get();
        }
    }

    private static final class CoordinateInterpolator extends IrisInterpolator {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public NoiseBounds interpolateBounds(double x, double z, NoiseBoundsProvider provider) {
            invocations.incrementAndGet();
            return new NoiseBounds(low(x, z), high(x, z));
        }

        private static double low(double x, double z) {
            return x * 0.125D - z * 0.0625D - 7.75D;
        }

        private static double high(double x, double z) {
            return x * -0.03125D + z * 0.1875D + 12.5D;
        }

        private int getInvocations() {
            return invocations.get();
        }
    }
}
