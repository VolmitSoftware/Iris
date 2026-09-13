package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisGeneratorSurfaceDetailTest {
    @Test
    public void defaultUsesTheOriginalSampleOnce() {
        AtomicInteger samples = new AtomicInteger();
        IrisGenerator generator = generator((seed, x, z) -> {
            samples.incrementAndGet();
            return x * 0.01D + z * 0.02D;
        });

        assertEquals(0.07D, generator.getHeight(1, 3, 27L), 1e-12D);
        assertEquals(1, samples.get());
    }

    @Test
    public void halvesSubgridDetailAndPreservesLatticeHeights() {
        IrisGenerator generator = generator((seed, x, z) ->
                (int) x % 6 == 0 && (int) z % 6 == 0 ? 0.2D : 0.8D);
        generator.setSurfaceDetail(0.5D);

        for (int z = -12; z <= 12; z++) {
            for (int x = -12; x <= 12; x++) {
                double expected = x % 6 == 0 && z % 6 == 0 ? 0.2D : 0.5D;
                assertEquals(expected, generator.getHeight(x, z, 27L), 1e-12D);
            }
        }
    }

    @Test
    public void preservesPlanarSlopesAtNegativeAndFractionalCoordinates() {
        IrisGenerator generator = generator((seed, x, z) -> 0.5D + x * 0.01D + z * 0.02D);
        generator.setSurfaceDetail(0.5D);

        for (double z = -4.25D; z < 4D; z += 0.5D) {
            for (double x = -4.75D; x < 4D; x += 0.5D) {
                assertEquals(0.5D + x * 0.01D + z * 0.02D,
                        generator.getHeight(x, z, 27L), 1e-12D);
            }
        }
    }

    @Test
    public void reusesLatticeSamplesDuringDenseGeneration() {
        AtomicInteger samples = new AtomicInteger();
        IrisGenerator generator = generator((seed, x, z) -> {
            samples.incrementAndGet();
            return 0.5D;
        });
        generator.setSurfaceDetail(0.5D);

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(0.5D, generator.getHeight(x, z, 27L), 0D);
            }
        }

        assertEquals(263, samples.get());
        assertTrue(samples.get() < 256 * 1.1D);
    }

    @Test
    public void fourBlockPreviewSamplingReusesCoarseCorners() {
        AtomicInteger samples = new AtomicInteger();
        IrisGenerator generator = generator((seed, x, z) -> {
            samples.incrementAndGet();
            return 0.5D;
        });
        generator.setSurfaceDetail(0.5D);

        for (int z = -16; z < 16; z += 4) {
            for (int x = -16; x < 16; x += 4) {
                generator.getHeight(x, z, 27L);
            }
        }

        assertEquals(91, samples.get());
    }

    @Test
    public void cachedLatticeHeightsRemainIsolatedBySeed() {
        IrisGenerator generator = generator((seed, x, z) -> seed * 0.000001D);
        generator.setSurfaceDetail(0.5D);
        double first = generator.getHeight(1, 1, 27L);
        double second = generator.getHeight(1, 1, 28L);

        assertEquals(0.000001D, second - first, 1e-12D);
        assertEquals(first, generator.getHeight(1, 1, 27L), 0D);
    }

    @Test
    public void cachedLatticeHeightsRemainIsolatedByEngine() {
        Engine firstEngine = mock(Engine.class);
        Engine secondEngine = mock(Engine.class);
        AtomicReference<Engine> activeEngine = new AtomicReference<>(firstEngine);
        IrisData data = mock(IrisData.class);
        when(data.getEngine()).thenAnswer(ignored -> activeEngine.get());
        IrisGenerator generator = new IrisGenerator().setSurfaceDetail(0.5D).setComposite(new KList<>(
                new ContextNoise(loader -> loader.getEngine() == firstEngine ? 0.2D : 0.8D)));
        generator.setLoader(data);

        assertEquals(0.2D, generator.getHeight(0, 0, 27L), 1e-12D);
        assertEquals(0.2D, generator.getHeight(1, 1, 27L), 1e-12D);
        activeEngine.set(secondEngine);
        assertEquals(0.8D, generator.getHeight(0, 0, 27L), 1e-12D);
        assertEquals(0.8D, generator.getHeight(1, 1, 27L), 1e-12D);
        activeEngine.set(firstEngine);
        assertEquals(0.2D, generator.getHeight(1, 1, 27L), 1e-12D);
    }

    @Test
    public void cachedLatticeHeightsRemainIsolatedByLoader() {
        Engine engine = mock(Engine.class);
        IrisData firstData = mock(IrisData.class);
        IrisData secondData = mock(IrisData.class);
        when(firstData.getEngine()).thenReturn(engine);
        when(secondData.getEngine()).thenReturn(engine);
        IrisGenerator generator = new IrisGenerator().setSurfaceDetail(0.5D).setComposite(new KList<>(
                new ContextNoise(loader -> loader == firstData ? 0.2D : 0.8D)));
        generator.setLoader(firstData);

        assertEquals(0.2D, generator.getHeight(0, 0, 27L), 1e-12D);
        assertEquals(0.2D, generator.getHeight(1, 1, 27L), 1e-12D);
        generator.setLoader(secondData);
        assertEquals(0.8D, generator.getHeight(0, 0, 27L), 1e-12D);
        assertEquals(0.8D, generator.getHeight(1, 1, 27L), 1e-12D);
        generator.setLoader(firstData);
        assertEquals(0.2D, generator.getHeight(1, 1, 27L), 1e-12D);
    }

    @Test
    public void rescalingClearsCachedLatticeHeights() {
        IrisGenerator generator = generator((seed, x, z) -> x * 0.01D + z * 0.02D);
        generator.setSurfaceDetail(0.5D);
        assertEquals(0.06D, generator.getHeight(2, 2, 27L), 1e-12D);

        generator.rescale(2D);

        assertEquals(0.12D, generator.getHeight(2, 2, 27L), 1e-12D);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonfiniteDetail() {
        generator((seed, x, z) -> 0D).setSurfaceDetail(Double.NaN).getHeight(1, 1, 27L);
    }

    private static IrisGenerator generator(Sampler sampler) {
        return new IrisGenerator().setComposite(new KList<>(new SampledNoise(sampler)));
    }

    private interface Sampler {
        double sample(long seed, double x, double z);
    }

    private static final class SampledNoise extends IrisNoiseGenerator {
        private final Sampler sampler;

        private SampledNoise(Sampler sampler) {
            this.sampler = sampler;
        }

        @Override
        public double getNoise(long superSeed, double x, double z, IrisData data) {
            return sampler.sample(superSeed, x, z);
        }
    }

    private static final class ContextNoise extends IrisNoiseGenerator {
        private final ToDoubleFunction<IrisData> sampler;

        private ContextNoise(ToDoubleFunction<IrisData> sampler) {
            this.sampler = sampler;
        }

        @Override
        public double getNoise(long superSeed, double x, double z, IrisData data) {
            return sampler.applyAsDouble(data);
        }
    }
}
