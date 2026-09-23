package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisBiomeLayerHeightCacheTest {
    private static final long TERRAIN_SEED = 7191L;
    private static final long CARVE_SEED = -872315L;
    private static final long FLOATING_SEED = (TERRAIN_SEED ^ 0x7EB0A73F1DCE514DL)
            + (0x7A4E ^ "magnetics/glass-shard".hashCode());

    @Test
    public void layerHeightUsesEachCallerSeedInEitherOrder() {
        IrisBiomePaletteLayer layer = layer();
        assertNoise(layer().getHeightGenerator(new RNG(CARVE_SEED), null),
                layer.getHeightGenerator(new RNG(CARVE_SEED), null));
        assertNoise(layer().getHeightGenerator(new RNG(FLOATING_SEED), null),
                layer.getHeightGenerator(new RNG(FLOATING_SEED), null));
        IrisBiomePaletteLayer reversed = layer();
        assertNoise(layer.getHeightGenerator(new RNG(FLOATING_SEED), null),
                reversed.getHeightGenerator(new RNG(FLOATING_SEED), null));
        assertNoise(layer.getHeightGenerator(new RNG(CARVE_SEED), null),
                reversed.getHeightGenerator(new RNG(CARVE_SEED), null));
    }

    @Test
    public void sharedSurfaceCarveAndFloatingBiomeIgnoresWarmOrder() {
        IrisBiome biome = biome();
        long[] seeds = {FLOATING_SEED, CARVE_SEED, TERRAIN_SEED, FLOATING_SEED + 733L};
        for (long seed : seeds) {
            assertGenerators(biome().getLayerHeightGenerators(new RNG(seed), null),
                    biome.getLayerHeightGenerators(new RNG(seed), null));
        }
        IrisBiome reversed = biome();
        for (int i = seeds.length - 1; i >= 0; i--) {
            assertGenerators(biome.getLayerHeightGenerators(new RNG(seeds[i]), null),
                    reversed.getLayerHeightGenerators(new RNG(seeds[i]), null));
        }
        assertNotEquals(biome.getLayerHeightGenerators(new RNG(TERRAIN_SEED), null).get(0).noise(13, -29),
                biome.getLayerHeightGenerators(new RNG(FLOATING_SEED), null).get(0).noise(13, -29), 0D);
    }

    @Test
    public void concurrentSurfaceCarveAndFloatingCallersMatchIsolatedBiomes() throws Exception {
        IrisBiome shared = biome();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        long[] seeds = {TERRAIN_SEED, CARVE_SEED, FLOATING_SEED, FLOATING_SEED + 733L};
        try {
            for (int worker = 0; worker < 8; worker++) {
                long seed = seeds[worker % seeds.length];
                KList<CNG> expected = biome().getLayerHeightGenerators(new RNG(seed), null);
                tasks.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    for (int sample = 0; sample < 30; sample++) {
                        assertGenerators(expected, shared.getLayerHeightGenerators(new RNG(seed), null));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void runtimeAndDataIdentitiesDoNotReuseHeightGenerators() {
        IrisBiome biome = biome();
        IrisData data = mock(IrisData.class);
        IrisData otherData = mock(IrisData.class);
        Engine first = mock(Engine.class);
        Engine second = mock(Engine.class);
        when(data.getEngine()).thenReturn(first);
        when(otherData.getEngine()).thenReturn(first);
        RNG rng = new RNG(TERRAIN_SEED);
        KList<CNG> original = biome.getLayerHeightGenerators(rng, data);
        assertSame(original, biome.getLayerHeightGenerators(rng, data));
        KList<CNG> other = biome.getLayerHeightGenerators(rng, otherData);
        assertNotSame(original, other);
        assertNotSame(original.get(0), other.get(0));
        assertGenerators(original, other);
        when(data.getEngine()).thenReturn(second);
        KList<CNG> replacement = biome.getLayerHeightGenerators(rng, data);
        assertNotSame(original, replacement);
        assertNotSame(original.get(0), replacement.get(0));
        assertGenerators(original, replacement);
        when(data.getEngine()).thenReturn(first);
        assertSame(original, biome.getLayerHeightGenerators(rng, data));
    }

    @Test
    public void materialBandsMatchIsolatedGenerationAcrossSurfaceSeaAndCeiling() {
        NativeBlockState first = mock(NativeBlockState.class);
        NativeBlockState second = mock(NativeBlockState.class);
        IrisBiome shared = materialBiome(first, second);
        IrisDimension dimension = new IrisDimension();
        long[] seeds = {FLOATING_SEED, TERRAIN_SEED, CARVE_SEED, FLOATING_SEED + 733L};
        for (long seed : seeds) {
            IrisBiome isolated = materialBiome(first, second);
            RNG rng = new RNG(seed);
            for (int x = -65; x <= 65; x += 7) {
                assertEquals(isolated.generateLayers(dimension, x, -x, rng, 64, 120, null, null),
                        shared.generateLayers(dimension, x, -x, rng, 64, 120, null, null));
                assertEquals(isolated.generateCeilingLayers(dimension, x, -x, rng, 64, 120, null, null),
                        shared.generateCeilingLayers(dimension, x, -x, rng, 64, 120, null, null));
                assertEquals(isolated.generateSeaLayers(x, -x, rng, 64, null),
                        shared.generateSeaLayers(x, -x, rng, 64, null));
            }
        }
    }

    @Test
    public void evictionRecomputesTheSameNoise() {
        IrisBiome biome = biome();
        KList<CNG> original = biome.getLayerHeightGenerators(new RNG(TERRAIN_SEED), null);
        for (int seed = 0; seed < 96; seed++) {
            biome.getLayerHeightGenerators(new RNG(seed), null);
        }
        KList<CNG> recomputed = biome.getLayerHeightGenerators(new RNG(TERRAIN_SEED), null);
        assertNotSame(original, recomputed);
        assertNotSame(original.get(0), recomputed.get(0));
        assertGenerators(original, recomputed);
    }

    private static IrisBiomePaletteLayer layer() {
        return new IrisBiomePaletteLayer().zero().setMinHeight(1).setMaxHeight(12);
    }

    private static IrisBiome biome() {
        return new IrisBiome().setLayers(new KList<>(layer(), layer().setMinHeight(3).setMaxHeight(5)));
    }

    private static IrisBiome materialBiome(NativeBlockState first, NativeBlockState second) {
        IrisBiomePaletteLayer upper = IrisBiomeCeilingLayerTest.layer(first, 1).setMaxHeight(5);
        IrisBiomePaletteLayer lower = IrisBiomeCeilingLayerTest.layer(second, 3).setMaxHeight(12);
        KList<IrisBiomePaletteLayer> layers = new KList<>(upper, lower);
        return new IrisBiome().setLayers(layers).setCaveCeilingLayers(layers).setSeaLayers(layers);
    }

    private static void assertGenerators(KList<CNG> expected, KList<CNG> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertNoise(expected.get(index), actual.get(index));
        }
    }

    private static void assertNoise(CNG expected, CNG actual) {
        for (int x = -97; x <= 97; x += 13) {
            assertEquals(expected.noise(x, x * -3.25), actual.noise(x, x * -3.25), 0D);
        }
    }
}
