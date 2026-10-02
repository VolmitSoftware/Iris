package art.arcane.iris.generation.noise;

import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class NoiseSeedCohesionTest {
    private static final long[] SEEDS = {813753L, -935731L, Long.MIN_VALUE};
    private static final double[] COORDINATES = {-29_999_983.25D, -513D, -64D, -1D, 0D, 0.125D, 17D, 64D, 8192D, 29_999_983.25D};

    @Test
    public void allStylesRetainPinnedSamples() {
        long hash = 0xcbf29ce484222325L;
        for (NoiseStyle style : NoiseStyle.values()) {
            for (long seed : SEEDS) {
                for (double sample : samples(style.create(new RNG(seed)), false)) {
                    hash = (hash ^ Double.doubleToLongBits(sample)) * 0x100000001b3L;
                }
            }
        }
        assertEquals("All noise style samples", 5059606393854142913L, hash);
    }

    @Test(timeout = 30000)
    public void allStylesIgnoreSamplingOrderAndConcurrentReaders() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            for (NoiseStyle style : NoiseStyle.values()) {
                CNG generator = style.create(new RNG(SEEDS[0]));
                double[] expected = samples(generator, false);
                assertArrayEquals(style.name(), expected, samples(generator, true), 0D);
                List<Future<double[]>> results = new ArrayList<>(4);
                for (int index = 0; index < 4; index++) {
                    boolean reversed = index % 2 == 0;
                    results.add(workers.submit(() -> samples(generator, reversed)));
                }
                for (Future<double[]> result : results) {
                    assertArrayEquals(style.name(), expected, result.get(5L, TimeUnit.SECONDS), 0D);
                }
            }
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    public void generatorCacheEvictionRetainsSamples() {
        for (NoiseStyle style : NoiseStyle.values()) {
            IrisGeneratorStyle configured = style.style();
            double[] expected = samples(configured.create(new RNG(SEEDS[0]), null), false);
            for (long seed = 0; seed < 9; seed++) {
                configured.create(new RNG(seed), null);
            }
            assertArrayEquals(style.name(), expected,
                    samples(configured.create(new RNG(SEEDS[0]), null), true), 0D);
        }
    }

    private static double[] samples(CNG generator, boolean reversed) {
        double[] sampled = new double[COORDINATES.length * 3];
        for (int cursor = 0; cursor < COORDINATES.length; cursor++) {
            int index = reversed ? COORDINATES.length - 1 - cursor : cursor;
            double x = COORDINATES[index];
            double z = x * -0.713D;
            sampled[index * 3] = generator.noise(x);
            sampled[index * 3 + 1] = generator.noise(x, z);
            sampled[index * 3 + 2] = generator.noise(x, 81.25D, z);
        }
        return sampled;
    }
}
