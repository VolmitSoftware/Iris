package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class IrisBiomePaletteLayerGeneratorCacheTest {
    private static final int SIGNATURE = 7;
    private static final long LAYER_SIGNATURE = 2L;

    @Test
    public void callerSeedsDoNotShareAFirstWinnerGenerator() {
        GeneratorFixture fixture = new GeneratorFixture();

        assertSame(fixture.carveGenerator,
                fixture.layer.getLayerGenerator(new RNG(11L), SIGNATURE, fixture.data));
        assertSame(fixture.postGenerator,
                fixture.layer.getLayerGenerator(new RNG(29L), SIGNATURE, fixture.data));
        assertSame(fixture.carveGenerator,
                fixture.layer.getLayerGenerator(new RNG(11L), SIGNATURE, fixture.data));
    }

    @Test
    public void reverseInitializationOrderKeepsSeedAssignments() {
        GeneratorFixture fixture = new GeneratorFixture();

        assertSame(fixture.postGenerator,
                fixture.layer.getLayerGenerator(new RNG(29L), SIGNATURE, fixture.data));
        assertSame(fixture.carveGenerator,
                fixture.layer.getLayerGenerator(new RNG(11L), SIGNATURE, fixture.data));
    }

    @Test
    public void concurrentInitializationKeepsSeedAssignments() throws Exception {
        GeneratorFixture fixture = new GeneratorFixture();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CNG> carve = executor.submit(() -> {
                assertTrue(start.await(5L, TimeUnit.SECONDS));
                return fixture.layer.getLayerGenerator(new RNG(11L), SIGNATURE, fixture.data);
            });
            Future<CNG> post = executor.submit(() -> {
                assertTrue(start.await(5L, TimeUnit.SECONDS));
                return fixture.layer.getLayerGenerator(new RNG(29L), SIGNATURE, fixture.data);
            });
            start.countDown();

            assertSame(fixture.carveGenerator, carve.get(5L, TimeUnit.SECONDS));
            assertSame(fixture.postGenerator, post.get(5L, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class GeneratorFixture {
        private final IrisData data = mock(IrisData.class);
        private final Engine engine = mock(Engine.class);
        private final IrisGeneratorStyle style = mock(IrisGeneratorStyle.class);
        private final CNG carveGenerator = mock(CNG.class);
        private final CNG postGenerator = mock(CNG.class);
        private final IrisBiomePaletteLayer layer = new IrisBiomePaletteLayer().zero().setStyle(style);

        private GeneratorFixture() {
            when(data.getEngine()).thenReturn(engine);
            Map<Long, CNG> generators = Map.of(
                    11L + SIGNATURE + LAYER_SIGNATURE, carveGenerator,
                    29L + SIGNATURE + LAYER_SIGNATURE, postGenerator
            );
            when(style.create(any(RNG.class), same(data), same(engine))).thenAnswer(invocation -> {
                RNG rng = invocation.getArgument(0);
                return generators.get(rng.getSeed());
            });
        }
    }
}
