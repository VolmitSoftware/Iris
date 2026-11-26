package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisGeneratorSurfaceDetailTest {
    private final PackRuntime runtime = PackRuntime.standalone(null);

    @Test
    public void defaultUsesTheOriginalSampleOnce() {
        AtomicInteger samples = new AtomicInteger();
        IrisGenerator generator = generator((seed, x, z) -> {
            samples.incrementAndGet();
            return x * 0.01D + z * 0.02D;
        });

        assertEquals(0.07D, generator.getHeight(runtime, 1, 3, 27L), 1e-12D);
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
                assertEquals(expected, generator.getHeight(runtime, x, z, 27L), 1e-12D);
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
                        generator.getHeight(runtime, x, z, 27L), 1e-12D);
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
                assertEquals(0.5D, generator.getHeight(runtime, x, z, 27L), 0D);
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
                generator.getHeight(runtime, x, z, 27L);
            }
        }

        assertEquals(91, samples.get());
    }

    @Test
    public void repeatedCellSamplingMatchesColdCacheRawBits() {
        Sampler source = (seed, x, z) -> Math.sin(x * 0.017D) + Math.cos(z * 0.031D);
        IrisGenerator warm = generator(source).setSurfaceDetail(0.5D);
        IrisGenerator cold = generator(source).setSurfaceDetail(0.5D);
        double[][] points = {
                {-6D, -6D}, {-5.75D, -5.5D}, {-4.25D, -4.5D}, {-3D, -3D},
                {-0.25D, -0.125D}, {0D, 0D}, {1.25D, 1.75D}, {5.5D, 5.75D},
                {6D, 6D}, {6.25D, 6.5D}, {8191.25D, -4096.75D}
        };

        for (double[] point : points) {
            long expected = Double.doubleToRawLongBits(cold.getHeight(PackRuntime.standalone(null), point[0], point[1], 27L));
            for (int repeat = 0; repeat < 4; repeat++) {
                assertEquals(expected, Double.doubleToRawLongBits(warm.getHeight(runtime, point[0], point[1], 27L)));
            }
        }
    }

    @Test
    public void repeatedLatticeSamplesRefreshSeedChanges() {
        AtomicInteger samples = new AtomicInteger();
        IrisGenerator generator = generator((seed, x, z) -> {
            samples.incrementAndGet();
            return seed * 0.000001D;
        }).setSurfaceDetail(0.5D);

        double first = generator.getHeight(runtime, 0, 0, 27L);
        assertEquals(Double.doubleToRawLongBits(first), Double.doubleToRawLongBits(generator.getHeight(runtime, 0, 0, 27L)));
        assertEquals(1, samples.get());

        double second = generator.getHeight(runtime, 0, 0, 28L);
        assertEquals(0.000001D, second - first, 1e-12D);
        assertEquals(Double.doubleToRawLongBits(second), Double.doubleToRawLongBits(generator.getHeight(runtime, 0, 0, 28L)));
        assertEquals(2, samples.get());

        assertEquals(Double.doubleToRawLongBits(first), Double.doubleToRawLongBits(generator.getHeight(runtime, 0, 0, 27L)));
        assertEquals(3, samples.get());
    }

    @Test
    public void collidingLatticeSamplesRecomputeWithoutReturningAnotherCoordinate() {
        AtomicInteger originSamples = new AtomicInteger();
        IrisGenerator generator = generator((seed, x, z) -> {
            if (x == 0D && z == 0D) {
                originSamples.incrementAndGet();
            }
            return x * 0.00001D;
        }).setSurfaceDetail(0.5D);

        assertEquals(0L, Double.doubleToRawLongBits(generator.getHeight(runtime, 0, 0, 27L)));
        assertEquals(0L, Double.doubleToRawLongBits(generator.getHeight(runtime, 0, 0, 27L)));
        assertEquals(1, originSamples.get());
        assertEquals(Double.doubleToRawLongBits(71754D * 0.00001D),
                Double.doubleToRawLongBits(generator.getHeight(runtime, 71754, 0, 27L)));
        assertEquals(0L, Double.doubleToRawLongBits(generator.getHeight(runtime, 0, 0, 27L)));
        assertEquals(2, originSamples.get());
        assertEquals(0L, Double.doubleToRawLongBits(generator.getHeight(runtime, 0, 0, 27L)));
        assertEquals(2, originSamples.get());
    }

    @Test(timeout = 15000)
    public void concurrentCollisionsAndSeedChangesPreserveRawHeightBits() throws Exception {
        Sampler source = (seed, x, z) -> seed * 0.000001D + x * 0.00001D - z * 0.00003D;
        IrisGenerator generator = generator(source).setSurfaceDetail(0.5D);
        IrisGenerator reference = generator(source);
        double[] xs = {0D, 71754D};
        long[] seeds = {27L, 28L};
        long[][] expected = new long[xs.length][seeds.length];
        for (int coordinate = 0; coordinate < xs.length; coordinate++) {
            for (int seed = 0; seed < seeds.length; seed++) {
                expected[coordinate][seed] = Double.doubleToRawLongBits(reference.getHeight(runtime, xs[coordinate], 0D, seeds[seed]));
            }
        }
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int task = 0; task < 16; task++) {
                int offset = task;
                tasks.add(pool.submit(() -> {
                    for (int sample = 0; sample < 64; sample++) {
                        int coordinate = (sample + offset) & 1;
                        int seed = (sample / 2 + offset) & 1;
                        assertEquals(expected[coordinate][seed],
                                Double.doubleToRawLongBits(generator.getHeight(runtime, xs[coordinate], 0D, seeds[seed])));
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void cachedLatticeHeightsRemainIsolatedBySeed() {
        IrisGenerator generator = generator((seed, x, z) -> seed * 0.000001D);
        generator.setSurfaceDetail(0.5D);
        double first = generator.getHeight(runtime, 1, 1, 27L);
        double second = generator.getHeight(runtime, 1, 1, 28L);

        assertEquals(0.000001D, second - first, 1e-12D);
        assertEquals(first, generator.getHeight(runtime, 1, 1, 27L), 0D);
    }

    @Test
    public void cachedLatticeHeightsBelongToTheirRuntime() {
        IrisData firstData = mock(IrisData.class);
        IrisData secondData = mock(IrisData.class);
        PackRuntime first = PackRuntime.standalone(firstData);
        PackRuntime second = PackRuntime.standalone(secondData);
        IrisGenerator generator = new IrisGenerator().setSurfaceDetail(0.5D).setComposite(new KList<>(
                new ContextNoise(scope -> scope.data() == firstData ? 0.2D : 0.8D)));

        assertEquals(0.2D, generator.getHeight(first, 0, 0, 27L), 1e-12D);
        assertEquals(0.2D, generator.getHeight(first, 1, 1, 27L), 1e-12D);
        assertEquals(0.8D, generator.getHeight(second, 0, 0, 27L), 1e-12D);
        assertEquals(0.8D, generator.getHeight(second, 1, 1, 27L), 1e-12D);
        assertEquals(0.2D, generator.getHeight(first, 1, 1, 27L), 1e-12D);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonfiniteDetail() {
        generator((seed, x, z) -> 0D).setSurfaceDetail(Double.NaN).getHeight(runtime, 1, 1, 27L);
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
        public double getNoise(long superSeed, double x, double z, PackRuntime runtime) {
            return sampler.sample(superSeed, x, z);
        }
    }

    private static final class ContextNoise extends IrisNoiseGenerator {
        private final ToDoubleFunction<PackRuntime> sampler;

        private ContextNoise(ToDoubleFunction<PackRuntime> sampler) {
            this.sampler = sampler;
        }

        @Override
        public double getNoise(long superSeed, double x, double z, PackRuntime runtime) {
            return sampler.applyAsDouble(runtime);
        }
    }
}
