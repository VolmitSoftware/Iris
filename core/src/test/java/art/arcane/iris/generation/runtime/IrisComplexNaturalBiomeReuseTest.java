package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisComplexNaturalBiomeReuseTest {
    @Test
    public void reusesBaseSelectionAndRetainsHeightDependentShoreSelection() throws Exception {
        Fixture fixture = new Fixture();
        assertSame(fixture.land, fixture.sample(90D));
        assertEquals(1, fixture.cachedCalls.get());
        assertEquals(0, fixture.directCalls.get());
        assertSame(fixture.shore, fixture.sample(63D));
        assertEquals(1, fixture.directCalls.get());
        assertSame(fixture.sea, fixture.sample(40D));
        assertEquals(2, fixture.directCalls.get());
    }

    @Test
    public void mappedBiomesBypassBothSelections() throws Exception {
        Fixture fixture = new Fixture();
        IrisBiome mapped = new IrisBiome();
        doReturn(mapped).when(fixture.images).sampleBiome(anyDouble(), anyDouble());
        assertSame(mapped, fixture.sample(90D));
        assertEquals(0, fixture.cachedCalls.get());
        assertEquals(0, fixture.directCalls.get());
    }

    @Test
    public void disabledReuseAndBoundChunkContextsKeepDirectSampling() throws Exception {
        Fixture fixture = new Fixture();
        fixture.setReuse(false);
        assertSame(fixture.land, fixture.sample(90D));
        fixture.setReuse(true);
        try (IrisContext.Scope ignored = IrisContext.open(mock(Engine.class), 1L, mock(ChunkContext.class))) {
            assertSame(fixture.land, fixture.sample(90D));
        }
        assertEquals(0, fixture.cachedCalls.get());
        assertEquals(2, fixture.directCalls.get());
        try (IrisContext.Scope ignored = IrisContext.open(mock(Engine.class), 1L, null)) {
            assertSame(fixture.land, fixture.sample(90D));
        }
        assertEquals(1, fixture.cachedCalls.get());
    }

    @Test
    public void allActiveSelectionExpressionsAndFracturesDisableReuse() {
        IrisDimension dimension = new IrisDimension();
        IrisBiome leaf = new IrisBiome();
        assertTrue(IrisComplex.hasFixedNaturalBiomeNoise(dimension, List.of(leaf)));
        for (IrisGeneratorStyle style : List.of(dimension.getRegionStyle(), dimension.getContinentalStyle(),
                dimension.getLandBiomeStyle(), dimension.getSeaBiomeStyle())) {
            style.setFracture(new IrisGeneratorStyle().setExpression("context"));
            assertFalse(IrisComplex.hasFixedNaturalBiomeNoise(dimension, List.of(leaf)));
            style.setFracture(null);
        }
        leaf.setChildStyle(new IrisGeneratorStyle().setExpression("context"));
        assertTrue(IrisComplex.hasFixedNaturalBiomeNoise(dimension, List.of(leaf)));
        leaf.setChildren(new KList<>("child"));
        assertFalse(IrisComplex.hasFixedNaturalBiomeNoise(dimension, List.of(leaf)));
        leaf.setChildStyle(new IrisGeneratorStyle().setFracture(new IrisGeneratorStyle().setExpression("context")));
        assertFalse(IrisComplex.hasFixedNaturalBiomeNoise(dimension, List.of(leaf)));
    }

    private static final class Fixture {
        private final IrisComplex complex = mock(IrisComplex.class, CALLS_REAL_METHODS);
        private final IrisImageMapRuntime images = mock(IrisImageMapRuntime.class);
        private final IrisRegion region = mock(IrisRegion.class);
        private final IrisBiome land = new IrisBiome().setInferredType(InferredType.LAND);
        private final IrisBiome sea = new IrisBiome().setInferredType(InferredType.SEA);
        private final IrisBiome shore = new IrisBiome().setInferredType(InferredType.SHORE);
        private final AtomicInteger cachedCalls = new AtomicInteger();
        private final AtomicInteger directCalls = new AtomicInteger();
        private final Method sample;

        private Fixture() throws Exception {
            complex.setImageMapRuntime(images);
            complex.setFluidHeight(63D);
            setReuse(true);
            complex.setBaseBiomeStream(counted(land, cachedCalls));
            doReturn(2D).when(region).getShoreHeight(anyDouble(), anyDouble());
            Field streams = IrisComplex.class.getDeclaredField("inferredBiomeStreams");
            streams.setAccessible(true);
            streams.set(complex, IrisComplex.compileInferredBiomeStreams(List.of(region),
                    (ignored, type) -> counted(switch (type) {
                        case SEA -> sea;
                        case SHORE -> shore;
                        default -> land;
                    }, directCalls)));
            sample = IrisComplex.class.getDeclaredMethod("sampleNaturalBiome", InferredType.class,
                    IrisRegion.class, double.class, int.class, int.class);
            sample.setAccessible(true);
        }

        private void setReuse(boolean enabled) throws Exception {
            Field reuse = IrisComplex.class.getDeclaredField("reuseNaturalBaseBiome");
            reuse.setAccessible(true);
            reuse.setBoolean(complex, enabled);
        }

        private IrisBiome sample(double height) throws Exception {
            return (IrisBiome) sample.invoke(complex, InferredType.LAND, region, height, -17, 262144);
        }

        private static ProceduralStream<IrisBiome> counted(IrisBiome biome, AtomicInteger calls) {
            return ProceduralStream.of((x, z) -> {
                calls.incrementAndGet();
                return biome;
            }, Interpolated.of(value -> 0D, value -> biome));
        }
    }
}
