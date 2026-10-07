package art.arcane.iris.generation.noise;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;

public final class IrisNoiseGeneratorCacheTest {
    private static final long GENERATOR_SALT = 33_955_677L;
    private static final long CONFIGURED_SEED = 7L;
    private static final int OCTAVES = 3;

    @Test
    public void callerSeedsDoNotShareAFirstWinnerGenerator() {
        GeneratorFixture fixture = new GeneratorFixture();

        assertSame(fixture.firstGenerator, fixture.generator.getGenerator(11L, fixture.data, fixture.engine));
        assertSame(fixture.secondGenerator, fixture.generator.getGenerator(29L, fixture.data, fixture.engine));
        assertSame(fixture.firstGenerator, fixture.generator.getGenerator(11L, fixture.data, fixture.engine));
    }

    @Test
    public void reverseInitializationOrderKeepsSeedAssignments() {
        GeneratorFixture fixture = new GeneratorFixture();

        assertSame(fixture.secondGenerator, fixture.generator.getGenerator(29L, fixture.data, fixture.engine));
        assertSame(fixture.firstGenerator, fixture.generator.getGenerator(11L, fixture.data, fixture.engine));
    }

    @Test
    public void concurrentInitializationKeepsSeedAssignments() throws Exception {
        GeneratorFixture fixture = new GeneratorFixture();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CNG> first = executor.submit(() -> {
                assertTrue(start.await(5L, TimeUnit.SECONDS));
                return fixture.generator.getGenerator(11L, fixture.data, fixture.engine);
            });
            Future<CNG> second = executor.submit(() -> {
                assertTrue(start.await(5L, TimeUnit.SECONDS));
                return fixture.generator.getGenerator(29L, fixture.data, fixture.engine);
            });
            start.countDown();

            assertSame(fixture.firstGenerator, first.get(5L, TimeUnit.SECONDS));
            assertSame(fixture.secondGenerator, second.get(5L, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void heightResolvesEngineOnceAcrossFracturesAndSurfaceCorners() {
        IrisData data = mock(IrisData.class);
        Engine engine = mock(Engine.class);
        when(data.getEngine()).thenReturn(engine);
        IrisNoiseGenerator child = new IrisNoiseGenerator().setStyle(NoiseStyle.SIMPLEX.style());
        IrisNoiseGenerator layer = new IrisNoiseGenerator().setStyle(NoiseStyle.SIMPLEX.style())
                .setFracture(new KList<>(child));
        IrisGenerator generator = new IrisGenerator().setComposite(new KList<>(layer, child))
                .setSurfaceDetail(0.5D);
        generator.setLoader(data);

        double first = generator.getHeight(13D, -21D, 71L);
        verify(data, times(1)).getEngine();
        assertEquals(Double.doubleToRawLongBits(first),
                Double.doubleToRawLongBits(generator.getHeight(13D, -21D, 71L)));
        verify(data, times(2)).getEngine();
    }

    @Test
    public void changedEngineUsesDistinctConstructionAndRestoresPreviousGenerator() {
        IrisData data = mock(IrisData.class);
        Engine firstEngine = mock(Engine.class);
        Engine secondEngine = mock(Engine.class);
        IrisGeneratorStyle style = mock(IrisGeneratorStyle.class);
        CNG first = mock(CNG.class);
        CNG second = mock(CNG.class);
        IrisNoiseGenerator layer = new IrisNoiseGenerator().setStyle(style);
        when(data.getEngine()).thenReturn(firstEngine, secondEngine, firstEngine);
        when(style.createForLayer(any(RNG.class), same(data), eq(1), same(firstEngine))).thenReturn(first);
        when(style.createForLayer(any(RNG.class), same(data), eq(1), same(secondEngine))).thenReturn(second);

        assertSame(first, layer.getGenerator(11L, data, data.getEngine()));
        assertSame(second, layer.getGenerator(11L, data, data.getEngine()));
        assertSame(first, layer.getGenerator(11L, data, data.getEngine()));
        verify(style, times(1)).createForLayer(any(RNG.class), same(data), eq(1), same(firstEngine));
        verify(style, times(1)).createForLayer(any(RNG.class), same(data), eq(1), same(secondEngine));
    }

    @Test
    public void disabledNoiseDoesNotResolveEngine() {
        IrisData data = mock(IrisData.class);
        IrisNoiseGenerator layer = new IrisNoiseGenerator(false).setOffsetY(0.37D);
        assertEquals(0.37D, layer.getNoise(11L, 13D, -21D, data), 0D);
        verify(data, never()).getEngine();
    }

    private static final class GeneratorFixture {
        private final IrisData data = mock(IrisData.class);
        private final Engine engine = mock(Engine.class);
        private final IrisGeneratorStyle style = mock(IrisGeneratorStyle.class);
        private final CNG firstGenerator = mock(CNG.class);
        private final CNG secondGenerator = mock(CNG.class);
        private final IrisNoiseGenerator generator = new IrisNoiseGenerator()
                .setStyle(style)
                .setSeed(CONFIGURED_SEED)
                .setOctaves(OCTAVES);

        private GeneratorFixture() {
            when(data.getEngine()).thenReturn(engine);
            Map<Long, CNG> generators = Map.of(
                    11L + GENERATOR_SALT - CONFIGURED_SEED, firstGenerator,
                    29L + GENERATOR_SALT - CONFIGURED_SEED, secondGenerator
            );
            when(style.createForLayer(any(RNG.class), same(data), eq(OCTAVES), same(engine))).thenAnswer(invocation -> {
                RNG rng = invocation.getArgument(0);
                return generators.get(rng.getSeed());
            });
        }
    }
}
