package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationCacheWarmer;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.KeyedBlockState;
import art.arcane.iris.testsupport.RunningEnginePackData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisDecoratorSeedCohesionTest {
    private static final long[] SEEDS = {101L, -73L, 90181L};
    private static final int COLUMNS = 96;

    @Test
    public void decorationKeepsPinnedOutputAcrossColumnOrderAndWarmup() {
        long[] expected = {151633654196388515L, -2981136988801910995L, -8591254808024440278L};
        long[] actual = new long[SEEDS.length];
        for (int i = 0; i < SEEDS.length; i++) {
            Fixture cold = new Fixture(SEEDS[i], decorator(), RunningEnginePackData.create());
            long[] baseline = cold.generate(false);
            Fixture reverse = new Fixture(SEEDS[i], decorator(), RunningEnginePackData.create());
            Fixture warm = new Fixture(SEEDS[i], decorator(), RunningEnginePackData.create());
            GenerationCacheWarmer.warm(warm.engine);
            assertArrayEquals(baseline, reverse.generate(true));
            assertArrayEquals(baseline, warm.generate(true));
            actual[i] = hash(baseline);
        }
        assertEquals(Arrays.toString(expected), Arrays.toString(actual));
    }

    @Test
    public void concurrentFirstUseKeepsIndependentSeedResults() throws Exception {
        IrisData data = RunningEnginePackData.create();
        IrisDecorator shared = decorator();
        List<long[]> expected = new ArrayList<>();
        for (long seed : SEEDS) {
            expected.add(new Fixture(seed, decorator(), data).generate(false));
        }
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<long[]>> tasks = new ArrayList<>();
            for (int round = 0; round < 24; round++) {
                long seed = SEEDS[round % SEEDS.length];
                tasks.add(() -> new Fixture(seed, shared, data).generate(true));
            }
            List<Future<long[]>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
            for (int i = 0; i < futures.size(); i++) {
                assertArrayEquals("task=" + i, expected.get(i % SEEDS.length), futures.get(i).get());
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static IrisDecorator decorator() {
        KList<NativeBlockState> body = new KList<>(
                new KeyedBlockState("minecraft:stone"), new KeyedBlockState("minecraft:dirt"));
        KList<NativeBlockState> top = new KList<>(
                new KeyedBlockState("minecraft:granite"), new KeyedBlockState("minecraft:andesite"));
        IrisDecorator decorator = new IrisDecorator() {
            @Override
            public KList<NativeBlockState> getBlockData(IrisData data) {
                return body;
            }

            @Override
            public KList<NativeBlockState> getBlockDataTops(IrisData data) {
                return top;
            }
        };
        return decorator.setPartOf(IrisDecorationPart.SEA_FLOOR).setChance(0.61D).setForcePlace(true)
                .setStackMin(2).setStackMax(7)
                .setStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setZoom(7D))
                .setVariance(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setZoom(4D))
                .setHeightVariance(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setZoom(9D));
    }

    private static long hash(long[] values) {
        long hash = 0xcbf29ce484222325L;
        for (long value : values) {
            hash = (hash ^ value) * 0x100000001b3L;
        }
        return hash;
    }

    private static final class Fixture {
        private final Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        private final IrisBiome biome = mock(IrisBiome.class);
        private final IrisSeaFloorDecorator placement;

        private Fixture(long seed, IrisDecorator decorator, IrisData data) {
            when(engine.getData()).thenReturn(data);
            when(engine.getHeight()).thenReturn(32);
            when(engine.getSeedManager().getComponent()).thenReturn(seed);
            when(engine.getAllBiomes()).thenReturn(new KList<>(biome));
            when(engine.getDimension().getAllRegions(engine)).thenReturn(new KList<>());
            when(engine.getDimension().getOres()).thenReturn(new KList<>());
            when(biome.getLoadKey()).thenReturn("fixture");
            when(biome.getObjects()).thenReturn(new KList<>());
            when(biome.getOres()).thenReturn(new KList<>());
            when(biome.getProceduralObjects()).thenReturn(null);
            when(biome.getDecorators()).thenReturn(new KList<>(decorator));
            when(biome.getDecoratorBucket(IrisDecorationPart.SEA_FLOOR)).thenReturn(new IrisDecorator[]{decorator});
            placement = new IrisSeaFloorDecorator(engine);
        }

        private long[] generate(boolean reverse) {
            long[] output = new long[COLUMNS];
            for (int index = 0; index < COLUMNS; index++) {
                int column = reverse ? COLUMNS - index - 1 : index;
                Hunk<NativeBlockState> hunk = Hunk.newArrayHunk(1, 32, 1);
                int x = column * 29 - 1312;
                int z = column * 17 - 781;
                placement.decorate(0, 0, x, x + 1, x - 1, z, z + 1, z - 1, hunk, biome, 4, 32);
                long hash = 0xcbf29ce484222325L;
                for (int y = 0; y < 32; y++) {
                    NativeBlockState block = hunk.get(0, y, 0);
                    hash = (hash ^ (block == null ? 0 : block.key().hashCode())) * 0x100000001b3L;
                }
                output[column] = hash;
            }
            return output;
        }
    }
}
