package art.arcane.iris.generation.terrain;

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

public final class IrisMaterialPaletteGeneratorCacheTest {
    private static final long GENERATOR_SALT = -23_498_896L;

    @Test
    public void callerSeedsDoNotShareAFirstWinnerGenerator() {
        GeneratorFixture fixture = new GeneratorFixture();

        assertSame(fixture.firstGenerator, fixture.palette.getLayerGenerator(new RNG(11L), fixture.data));
        assertSame(fixture.secondGenerator, fixture.palette.getLayerGenerator(new RNG(29L), fixture.data));
        assertSame(fixture.firstGenerator, fixture.palette.getLayerGenerator(new RNG(11L), fixture.data));
    }

    @Test
    public void reverseInitializationOrderKeepsSeedAssignments() {
        GeneratorFixture fixture = new GeneratorFixture();

        assertSame(fixture.secondGenerator, fixture.palette.getLayerGenerator(new RNG(29L), fixture.data));
        assertSame(fixture.firstGenerator, fixture.palette.getLayerGenerator(new RNG(11L), fixture.data));
    }

    @Test
    public void concurrentInitializationKeepsSeedAssignments() throws Exception {
        GeneratorFixture fixture = new GeneratorFixture();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CNG> first = executor.submit(() -> {
                assertTrue(start.await(5L, TimeUnit.SECONDS));
                return fixture.palette.getLayerGenerator(new RNG(11L), fixture.data);
            });
            Future<CNG> second = executor.submit(() -> {
                assertTrue(start.await(5L, TimeUnit.SECONDS));
                return fixture.palette.getLayerGenerator(new RNG(29L), fixture.data);
            });
            start.countDown();

            assertSame(fixture.firstGenerator, first.get(5L, TimeUnit.SECONDS));
            assertSame(fixture.secondGenerator, second.get(5L, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class GeneratorFixture {
        private final IrisData data = mock(IrisData.class);
        private final Engine engine = mock(Engine.class);
        private final IrisGeneratorStyle style = mock(IrisGeneratorStyle.class);
        private final CNG firstGenerator = mock(CNG.class);
        private final CNG secondGenerator = mock(CNG.class);
        private final IrisMaterialPalette palette = new IrisMaterialPalette().qclear().setStyle(style);

        private GeneratorFixture() {
            when(data.getEngine()).thenReturn(engine);
            Map<Long, CNG> generators = Map.of(
                    11L + GENERATOR_SALT, firstGenerator,
                    29L + GENERATOR_SALT, secondGenerator
            );
            when(style.create(any(RNG.class), same(data), same(engine))).thenAnswer(invocation -> {
                RNG rng = invocation.getArgument(0);
                return generators.get(rng.getSeed());
            });
        }
    }
}
