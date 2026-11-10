package art.arcane.iris.engine.object.world.biome;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.terrain.IrisGenerator;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.util.common.data.DataProvider;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisBiomeGeneratorLinkTest {
    private static final String GENERATOR_KEY = "overlay";
    private static final int MIN_BLOCKS = -4;
    private static final int MAX_BLOCKS = 12;
    private static final long SEED = 571093L + 77L;

    @Test
    public void heightLerpsBetweenMinAndMaxByGeneratorOutput() {
        assertEquals(MIN_BLOCKS, link().getHeight(provider(fixed(0D, new AtomicLong())), 0D, 0D, SEED), 0D);
        assertEquals(4D, link().getHeight(provider(fixed(0.5D, new AtomicLong())), 0D, 0D, SEED), 1.0E-12D);
        assertEquals(MAX_BLOCKS, link().getHeight(provider(fixed(1D, new AtomicLong())), 0D, 0D, SEED), 0D);
    }

    @Test
    public void generatorOutputIsClampedToUnitRangeBeforeTheLerp() {
        assertEquals(MIN_BLOCKS, link().getHeight(provider(fixed(-3D, new AtomicLong())), 0D, 0D, SEED), 0D);
        assertEquals(MAX_BLOCKS, link().getHeight(provider(fixed(2.5D, new AtomicLong())), 0D, 0D, SEED), 0D);
    }

    @Test
    public void generatorSamplesWithTheSeedItIsGiven() {
        AtomicLong sampledSeed = new AtomicLong();

        link().getHeight(provider(fixed(0.5D, sampledSeed)), 0D, 0D, SEED);

        assertEquals(SEED, sampledSeed.get());
    }

    @Test
    public void missingGeneratorGivesFlatMinimumOverlay() {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisGenerator> loader = mock(ResourceLoader.class);
        when(data.getGeneratorLoader()).thenReturn(loader);

        assertEquals(MIN_BLOCKS, link().getHeight(() -> data, 12D, 34D, SEED), 0D);
    }

    @Test
    public void dimensionDecodesOverlayNoiseAsGeneratorLinks() {
        String json = "{\"overlayNoise\": [{\"generator\": \"hills\", \"min\": -2, \"max\": 9}]}";

        IrisDimension dimension = new Gson().fromJson(json, IrisDimension.class);

        assertEquals(1, dimension.getOverlayNoise().size());
        IrisBiomeGeneratorLink link = dimension.getOverlayNoise().get(0);
        assertEquals("hills", link.getGenerator());
        assertEquals(-2, link.getMin());
        assertEquals(9, link.getMax());
    }

    @Test
    public void dimensionWithoutOverlayNoiseHasNone() {
        assertTrue(new IrisDimension().getOverlayNoise().isEmpty());
    }

    private IrisBiomeGeneratorLink link() {
        return new IrisBiomeGeneratorLink(GENERATOR_KEY, MIN_BLOCKS, MAX_BLOCKS);
    }

    private DataProvider provider(IrisGenerator generator) {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisGenerator> loader = mock(ResourceLoader.class);
        when(loader.load(GENERATOR_KEY)).thenReturn(generator);
        when(data.getGeneratorLoader()).thenReturn(loader);
        return () -> data;
    }

    private IrisGenerator fixed(double height, AtomicLong sampledSeed) {
        return new IrisGenerator() {
            @Override
            public double getHeight(double x, double z, long seed) {
                sampledSeed.set(seed);
                return height;
            }
        };
    }
}
