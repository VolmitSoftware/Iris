package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.loading.IrisData;
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
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class IrisBiomeFieldSeedCohesionTest {
    @Test
    public void childrenGeneratorsSeparateSeedsSignaturesAndScales() {
        IrisData data = mock(IrisData.class);
        IrisBiome shared = biome(data);
        for (long seed : new long[]{29L, 11L, 29L}) {
            for (int signature : new int[]{2137, 91}) {
                for (double scale : new double[]{4D, 0.25D}) {
                    assertArrayEquals(samples(biome(data).getChildrenGenerator(new RNG(seed), signature, scale)),
                            samples(shared.getChildrenGenerator(new RNG(seed), signature, scale)), 0D);
                }
            }
        }
        CNG first = shared.getChildrenGenerator(new RNG(29L), 2137, 4D);
        assertSame(first, shared.getChildrenGenerator(new RNG(29L), 2137, 4D));
    }

    @Test(timeout = 15000)
    public void concurrentFloatingFieldsAndEvictionRetainSeedAssignments() throws Exception {
        IrisData data = mock(IrisData.class);
        IrisFloatingChildBiomes shared = new IrisFloatingChildBiomes();
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Future<double[]>> results = new ArrayList<>(16);
            for (int index = 0; index < 16; index++) {
                long seed = index;
                results.add(workers.submit(() -> floatingSamples(shared, seed, data)));
            }
            for (int index = 0; index < results.size(); index++) {
                double[] expected = floatingSamples(new IrisFloatingChildBiomes(), index, data);
                assertArrayEquals(expected, results.get(index).get(5L, TimeUnit.SECONDS), 0D);
                assertArrayEquals(expected, floatingSamples(shared, index, data), 0D);
            }
        } finally {
            workers.shutdownNow();
        }
    }

    private static IrisBiome biome(IrisData data) {
        IrisBiome biome = new IrisBiome().setChildStyle(NoiseStyle.SIMPLEX.style());
        biome.setLoader(data);
        return biome;
    }

    private static double[] floatingSamples(IrisFloatingChildBiomes entry, long seed, IrisData data) {
        double x = -71.25D;
        double z = 145.75D;
        return new double[]{
                entry.getFootprintCng(seed, data).noise(x, z),
                entry.getPickerCng(seed, data).noise(x, z),
                entry.getAltitudeCng(seed, data).noise(x, z),
                entry.getTopShapeCng(seed, data).noise(x, z),
                entry.getBottomCng(seed, data).noise(x, z),
                entry.getEdgeTaperProfile(seed, data).fade(4, -71, 145)
        };
    }

    private static double[] samples(CNG generator) {
        double[] sampled = new double[16];
        for (int index = 0; index < sampled.length; index++) {
            sampled[index] = generator.noise(index * 23.25D - 128D, index * -19.75D + 64D);
        }
        return sampled;
    }
}
