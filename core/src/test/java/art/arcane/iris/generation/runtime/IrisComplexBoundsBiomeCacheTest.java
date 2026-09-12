package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisInterpolator;
import art.arcane.volmlib.util.interpolation.NoiseBounds;
import art.arcane.volmlib.util.interpolation.NoiseBoundsProvider;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

public class IrisComplexBoundsBiomeCacheTest {
    @Test
    public void adjacentPassesReuseSamplesWithoutChangingInterpolationBits() throws Exception {
        IrisBiome first = new IrisBiome();
        IrisBiome second = new IrisBiome();
        AtomicInteger reads = new AtomicInteger();
        ProceduralStream<IrisBiome> stream = ProceduralStream.of((x, z) -> {
            reads.incrementAndGet();
            return x + z < 0D ? first : second;
        }, Interpolated.of(value -> 0D, value -> first));
        IrisInterpolator interpolator = new IrisInterpolator();
        Fixture fixture = new Fixture(stream);
        fixture.setBounds(interpolator, first, -15.25D, 63.5D);
        fixture.setBounds(interpolator, second, 7.75D, 127.25D);
        int uncachedReads = 0;

        for (int z = -32; z <= 32; z += 4) {
            for (int x = -32; x <= 32; x += 4) {
                AtomicInteger expectedReads = new AtomicInteger();
                NoiseBounds expected = interpolator.interpolateBounds(x, z, (xx, zz) -> {
                    expectedReads.incrementAndGet();
                    return xx + zz < 0D ? new NoiseBounds(-15.25D, 63.5D) : new NoiseBounds(7.75D, 127.25D);
                });
                assertBits(expected, fixture.sample(interpolator, x, z));
                uncachedReads += expectedReads.get();
            }
        }

        assertTrue(reads.get() < uncachedReads / 4);
    }

    @Test
    public void replacingBiomeStreamInvalidatesRetainedSamples() throws Exception {
        IrisBiome first = new IrisBiome();
        IrisBiome second = new IrisBiome();
        Fixture fixture = new Fixture(constant(first));
        IrisInterpolator interpolator = new IrisInterpolator();
        fixture.setBounds(interpolator, first, -10D, 20D);
        fixture.setBounds(interpolator, second, 50D, 80D);

        assertBits(new NoiseBounds(-10D, 20D), fixture.sample(interpolator, 0, 0));
        fixture.complex.setBaseBiomeStream(constant(second));
        assertBits(new NoiseBounds(50D, 80D), fixture.sample(interpolator, 0, 0));
    }

    @Test
    public void newComplexDoesNotReusePreviousComplexSamples() throws Exception {
        IrisBiome first = new IrisBiome();
        IrisBiome second = new IrisBiome();
        Fixture firstFixture = new Fixture(constant(first));
        Fixture secondFixture = new Fixture(constant(second));
        IrisInterpolator interpolator = new IrisInterpolator();
        firstFixture.setBounds(interpolator, first, -10D, 20D);
        secondFixture.setBounds(interpolator, second, 50D, 80D);

        assertBits(new NoiseBounds(-10D, 20D), firstFixture.sample(interpolator, 0, 0));
        assertBits(new NoiseBounds(50D, 80D), secondFixture.sample(interpolator, 0, 0));
        assertBits(new NoiseBounds(-10D, 20D), firstFixture.sample(interpolator, 0, 0));
    }

    @Test
    public void nestedPassDoesNotReplaceOuterGeneratorBounds() throws Exception {
        IrisBiome biome = new IrisBiome();
        Fixture fixture = new Fixture(constant(biome));
        IrisInterpolator nested = new IrisInterpolator().setHorizontalScale(19D);
        fixture.setBounds(nested, biome, 50D, 80D);
        IrisInterpolator outer = new IrisInterpolator() {
            @Override
            public NoiseBounds interpolateBounds(double x, double z, NoiseBoundsProvider provider) {
                NoiseBounds before = provider.noise(x, z);
                try {
                    assertBits(new NoiseBounds(50D, 80D), fixture.sample(nested, x, z));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
                assertBits(before, provider.noise(x, z));
                return before;
            }
        };
        fixture.setBounds(outer, biome, -10D, 20D);

        assertBits(new NoiseBounds(-10D, 20D), fixture.sample(outer, 0D, 0D));
        assertBits(new NoiseBounds(-10D, 20D), fixture.sample(outer, 0D, 0D));
    }

    @Test
    public void coordinateMemoKeepsExactKeysAndBoundedStorage() throws Exception {
        Class<?> type = Class.forName("art.arcane.iris.generation.runtime.IrisComplex$CoordinateBiomeCache");
        Constructor<?> constructor = type.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        Object cache = constructor.newInstance(4096);
        Method get = type.getDeclaredMethod("get", double.class, double.class);
        Method put = type.getDeclaredMethod("put", double.class, double.class, IrisBiome.class);
        get.setAccessible(true);
        put.setAccessible(true);
        IrisBiome first = new IrisBiome();
        IrisBiome second = new IrisBiome();
        double[][] coordinates = {{0D, -0D}, {-0D, 0D}, {-0.25D, 0.75D}, {-0.75D, 0.25D},
                {0.125D, 0.125D}, {Math.nextUp(0.125D), 0.125D}, {1e12D, -1e12D}};

        for (double[] coordinate : coordinates) {
            put.invoke(cache, coordinate[0], coordinate[1], first);
            assertSame(first, get.invoke(cache, coordinate[0], coordinate[1]));
            assertNull(get.invoke(cache, Math.nextUp(coordinate[0]), coordinate[1]));
        }
        for (int i = 0; i < 100_000; i++) {
            put.invoke(cache, (double) i, (double) -i, second);
            assertSame(second, get.invoke(cache, (double) i, (double) -i));
        }
        for (double[] coordinate : coordinates) {
            Object result = get.invoke(cache, coordinate[0], coordinate[1]);
            assertTrue(result == null || result == first);
        }
        Field values = type.getDeclaredField("values");
        values.setAccessible(true);
        assertEquals(4096, ((IrisBiome[]) values.get(cache)).length);
    }

    private static ProceduralStream<IrisBiome> constant(IrisBiome biome) {
        return ProceduralStream.of((x, z) -> biome, Interpolated.of(value -> 0D, value -> biome));
    }

    private static void assertBits(NoiseBounds expected, NoiseBounds actual) {
        assertEquals(Double.doubleToLongBits(expected.min()), Double.doubleToLongBits(actual.min()));
        assertEquals(Double.doubleToLongBits(expected.max()), Double.doubleToLongBits(actual.max()));
    }

    private static final class Fixture {
        private final IrisComplex complex = mock(IrisComplex.class, CALLS_REAL_METHODS);
        private final Map<IrisInterpolator, IdentityHashMap<IrisBiome, Object>> bounds = new HashMap<>();
        private final Object cache;
        private final Method sample;
        private final Constructor<?> boundsConstructor;

        private Fixture(ProceduralStream<IrisBiome> stream) throws Exception {
            complex.setBaseBiomeStream(stream);
            Field field = IrisComplex.class.getDeclaredField("generatorBounds");
            field.setAccessible(true);
            field.set(complex, bounds);
            Class<?> cacheClass = Class.forName("art.arcane.iris.generation.runtime.IrisComplex$GridBoundsCache");
            Constructor<?> constructor = cacheClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            cache = constructor.newInstance();
            sample = IrisComplex.class.getDeclaredMethod("sampleBoundsRaw", cacheClass, Engine.class,
                    IrisInterpolator.class, IrisGenerator[].class, double.class, double.class);
            sample.setAccessible(true);
            boundsConstructor = Class.forName("art.arcane.iris.generation.runtime.IrisComplex$GeneratorBounds")
                    .getDeclaredConstructor(double.class, double.class);
            boundsConstructor.setAccessible(true);
        }

        private void setBounds(IrisInterpolator interpolator, IrisBiome biome, double min, double max) throws Exception {
            bounds.computeIfAbsent(interpolator, ignored -> new IdentityHashMap<>())
                    .put(biome, boundsConstructor.newInstance(min, max));
        }

        private NoiseBounds sample(IrisInterpolator interpolator, double x, double z) throws ReflectiveOperationException {
            return (NoiseBounds) sample.invoke(complex, cache, null, interpolator, new IrisGenerator[0], x, z);
        }
    }
}
