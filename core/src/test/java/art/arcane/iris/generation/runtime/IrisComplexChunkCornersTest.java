package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisInterpolator;
import art.arcane.volmlib.util.interpolation.NoiseBounds;
import art.arcane.volmlib.util.interpolation.NoiseBoundsProvider;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

public class IrisComplexChunkCornersTest {
    @Test
    public void chunkCornerHeightsMatchPerGroupGridSamplingBitForBit() throws Exception {
        for (int step : new int[]{2, 4, 8, 16}) {
            IrisInterpolator[] interpolators = {
                    new PatchInterpolator(0, 1D),
                    new PatchInterpolator(1, -3D),
                    new ZeroInterpolator(true),
                    new PatchInterpolator(2, 0.5D),
                    new ZeroInterpolator(false)
            };
            IrisGenerator[][] generators = {
                    {new WaveGenerator(1D), new WaveGenerator(0.5D)},
                    {new WaveGenerator(-0.25D)},
                    {new WaveGenerator(2D)},
                    {new WaveGenerator(0.75D), new WaveGenerator(-1D), new WaveGenerator(0.125D)},
                    {new WaveGenerator(4D)}
            };
            IrisComplex candidate = createComplex(step, interpolators, generators);
            IrisComplex reference = createComplex(step, interpolators, generators);
            SplittableRandom random = new SplittableRandom(step * 31L);
            for (int sample = 0; sample < 60_000; sample++) {
                double x = coordinate(random);
                double z = coordinate(random);
                assertEquals("step " + step + " at " + x + "," + z,
                        Double.doubleToRawLongBits(expectedHeight(reference, interpolators, generators, x, z)),
                        Double.doubleToRawLongBits(interpolatedHeight(candidate, x, z)));
            }
        }
    }

    @Test
    public void silentCellsNeverEvaluateTheirGenerators() throws Exception {
        WaveGenerator quiet = new WaveGenerator(1D);
        WaveGenerator loud = new WaveGenerator(1D);
        IrisInterpolator[] interpolators = {new ZeroInterpolator(true), new PatchInterpolator(0, 2D)};
        IrisGenerator[][] generators = {{quiet}, {loud}};
        IrisComplex complex = createComplex(4, interpolators, generators);

        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                interpolatedHeight(complex, x, z);
            }
        }

        assertEquals(0, quiet.invocations);
        assertTrue(loud.invocations > 0);
    }

    @Test
    public void sparseSamplesFetchOnlyTheCornersTheirColumnsUse() throws Exception {
        CountingInterpolator counting = new CountingInterpolator();
        IrisInterpolator[] interpolators = {counting};
        IrisGenerator[][] generators = {{new WaveGenerator(1D)}};
        IrisComplex complex = createComplex(4, interpolators, generators);

        for (int sample = 0; sample < 32; sample++) {
            interpolatedHeight(complex, sample * 64D, sample * -64D);
        }
        assertEquals(32, counting.calls);

        interpolatedHeight(complex, 2048D + 1D, 0D);
        assertEquals(34, counting.calls);
        interpolatedHeight(complex, 4096D, 1D);
        assertEquals(36, counting.calls);
        interpolatedHeight(complex, 8192D + 1D, 1D);
        assertEquals(40, counting.calls);
    }

    @Test
    public void nestedHeightSamplesLeaveTheOuterChunkBlockIntact() throws Exception {
        ReentrantGenerator reentrant = new ReentrantGenerator();
        IrisInterpolator[] interpolators = {new ZeroFreeInterpolator(), new PatchInterpolator(4, -2D)};
        IrisGenerator[][] generators = {{reentrant}, {new WaveGenerator(0.5D)}};
        IrisComplex candidate = createComplex(4, interpolators, generators);
        IrisComplex reference = createComplex(4, interpolators, generators);
        SplittableRandom random = new SplittableRandom(99L);
        for (int sample = 0; sample < 4_000; sample++) {
            double x = coordinate(random);
            double z = coordinate(random);
            reentrant.target = reference;
            double expected = expectedHeight(reference, interpolators, generators, x, z);
            reentrant.target = candidate;
            assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(interpolatedHeight(candidate, x, z)));
        }
    }

    @Test
    public void nonFiniteCornersFailTheSameWayAndAreRetried() throws Exception {
        NaNCornerInterpolator interpolator = new NaNCornerInterpolator();
        IrisInterpolator[] interpolators = {interpolator};
        IrisGenerator[][] generators = {{new WaveGenerator(1D)}};
        IrisComplex complex = createComplex(4, interpolators, generators);

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                interpolatedHeight(complex, 2D, 2D);
                fail("A non-finite corner must fail the column");
            } catch (InvocationTargetException expected) {
                assertTrue(expected.getCause() instanceof IllegalStateException);
            }
        }
        assertEquals(2, interpolator.nanCalls);
        IrisComplex reference = createComplex(4, interpolators, generators);
        assertEquals(Double.doubleToRawLongBits(expectedHeight(reference, interpolators, generators, 0D, 0D)),
                Double.doubleToRawLongBits(interpolatedHeight(complex, 0D, 0D)));
    }

    private static double coordinate(SplittableRandom random) {
        int base = random.nextInt(-80, 80);
        return switch (random.nextInt(4)) {
            case 0 -> base;
            case 1 -> base + random.nextInt(4) * 0.25D;
            case 2 -> base + random.nextDouble();
            default -> base * 1031 + random.nextInt(-3, 3);
        };
    }

    private static double expectedHeight(
            IrisComplex complex,
            IrisInterpolator[] interpolators,
            IrisGenerator[][] generators,
            double x,
            double z
    ) throws Exception {
        Method interpolate = IrisComplex.class.getDeclaredMethod("interpolateGenerators", Engine.class,
                IrisInterpolator.class, int.class, IrisGenerator[].class, double.class, double.class, long.class);
        interpolate.setAccessible(true);
        double height = 0;
        for (int group = 0; group < interpolators.length; group++) {
            height += (double) interpolate.invoke(complex, null, interpolators[group], group, generators[group], x, z, 31L);
        }
        return height;
    }

    private static double interpolatedHeight(IrisComplex complex, double x, double z) throws Exception {
        Method method = IrisComplex.class.getDeclaredMethod("getInterpolatedHeight", Engine.class,
                double.class, double.class, long.class);
        method.setAccessible(true);
        return (double) method.invoke(complex, null, x, z, 31L);
    }

    private static IrisComplex createComplex(int step, IrisInterpolator[] interpolators, IrisGenerator[][] generators) throws Exception {
        IrisComplex complex = mock(IrisComplex.class, CALLS_REAL_METHODS);
        setField(complex, "biomeBoundsSamplingStep", step);
        setField(complex, "generatorBounds", new HashMap<>());
        setField(complex, "biomeBuffetGenerators", new HashMap<>());
        setField(complex, "frozenInterpolators", interpolators);
        setField(complex, "frozenGenerators", generators);
        Class<?> cacheClass = Class.forName("art.arcane.iris.generation.runtime.IrisComplex$GridBoundsCache");
        Constructor<?> cacheConstructor = cacheClass.getDeclaredConstructor();
        cacheConstructor.setAccessible(true);
        setField(complex, "gridBoundsCache", ThreadLocal.withInitial(() -> newCache(cacheConstructor)));
        return complex;
    }

    private static Object newCache(Constructor<?> constructor) {
        try {
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(IrisComplex complex, String name, Object value) throws Exception {
        Field field = IrisComplex.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(complex, value);
    }

    private static final class PatchInterpolator extends IrisInterpolator {
        private final int salt;
        private final double scale;

        private PatchInterpolator(int salt, double scale) {
            this.salt = salt;
            this.scale = scale;
        }

        @Override
        public NoiseBounds interpolateBounds(double x, double z, NoiseBoundsProvider provider) {
            long cell = Math.floorDiv((long) x, 24L) * 73_856_093L ^ Math.floorDiv((long) z, 24L) * 19_349_663L ^ salt * 83_492_791L;
            if (Math.floorMod(cell, 3L) != 0L) {
                return new NoiseBounds(0D, 0D);
            }
            double low = (x * 0.0625D - z * 0.03125D) * scale;
            return new NoiseBounds(low, low + 3.75D * scale + Math.floorMod(cell, 5L));
        }
    }

    private static final class ZeroFreeInterpolator extends IrisInterpolator {
        @Override
        public NoiseBounds interpolateBounds(double x, double z, NoiseBoundsProvider provider) {
            return new NoiseBounds(x * 0.01D - 1D, z * 0.01D + 3D);
        }
    }

    private static final class ZeroInterpolator extends IrisInterpolator {
        private final boolean negative;

        private ZeroInterpolator(boolean negative) {
            this.negative = negative;
        }

        @Override
        public NoiseBounds interpolateBounds(double x, double z, NoiseBoundsProvider provider) {
            return negative ? new NoiseBounds(-0D, 0D) : new NoiseBounds(0D, -0D);
        }
    }

    private static final class CountingInterpolator extends IrisInterpolator {
        private int calls;

        @Override
        public NoiseBounds interpolateBounds(double x, double z, NoiseBoundsProvider provider) {
            calls++;
            return new NoiseBounds(x * 0.001D, x * 0.001D + 2D);
        }
    }

    private static final class NaNCornerInterpolator extends IrisInterpolator {
        private int nanCalls;

        @Override
        public NoiseBounds interpolateBounds(double x, double z, NoiseBoundsProvider provider) {
            if (x == 4D && z == 4D) {
                nanCalls++;
                return new NoiseBounds(Double.NaN, 1D);
            }
            return new NoiseBounds(0.25D, 0.5D);
        }
    }

    private static final class ReentrantGenerator extends IrisGenerator {
        private IrisComplex target;
        private boolean nested;

        @Override
        public double getHeight(double x, double z, long seed) {
            if (nested) {
                return Math.cos(x * 0.13D) * 0.5D;
            }
            nested = true;
            try {
                return (interpolatedHeight(target, x + 4099D, z - 2053D) + interpolatedHeight(target, x - 8191D, z + 1031D)) * 0.001D;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                nested = false;
            }
        }
    }

    private static final class WaveGenerator extends IrisGenerator {
        private final double amplitude;
        private int invocations;

        private WaveGenerator(double amplitude) {
            this.amplitude = amplitude;
        }

        @Override
        public double getHeight(double x, double z, long seed) {
            invocations++;
            return Math.sin(x * 0.37D + seed) * Math.cos(z * 0.21D) * amplitude;
        }
    }
}
