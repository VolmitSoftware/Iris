package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.testsupport.KeyedBlockState;
import art.arcane.iris.testsupport.RunningEnginePackData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
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

public class IrisOreSeedCohesionTest {
    private static final long[] SEEDS = {101L, -73L, 90181L};
    private static final int SAMPLES = 4096;

    @Test
    public void oreFieldsKeepPinnedOutputAcrossSeedOrderAndWarmup() {
        IrisData data = RunningEnginePackData.create();
        IrisOreGenerator shared = generator();
        long[] actual = new long[SEEDS.length];
        for (int i = SEEDS.length - 1; i >= 0; i--) {
            IrisOreGenerator cold = generator();
            long[] expected = sample(cold, new RNG(SEEDS[i]), data, false);
            shared.warm(new RNG(SEEDS[(i + 1) % SEEDS.length]), data);
            assertArrayEquals(expected, sample(shared, new RNG(SEEDS[i]), data, true));
            RNG consumed = new RNG(SEEDS[i]);
            for (int draw = 0; draw < 128; draw++) {
                consumed.nextLong();
            }
            assertArrayEquals(expected, sample(generator(), consumed, data, true));
            actual[i] = hash(expected);
        }
        assertEquals(Arrays.toString(new long[]{8469391283002463309L, -3738838309875838243L, 8458758241968211373L}), Arrays.toString(actual));
    }

    @Test
    public void concurrentOreFirstUseKeepsSeedsIndependent() throws Exception {
        IrisData data = RunningEnginePackData.create();
        IrisOreGenerator shared = generator();
        List<long[]> expected = new ArrayList<>();
        for (long seed : SEEDS) {
            expected.add(sample(generator(), new RNG(seed), data, false));
        }
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<long[]>> tasks = new ArrayList<>();
            for (int index = 0; index < 18; index++) {
                long seed = SEEDS[index % SEEDS.length];
                tasks.add(() -> sample(shared, new RNG(seed), data, true));
            }
            List<Future<long[]>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
            for (int index = 0; index < futures.size(); index++) {
                assertArrayEquals(expected.get(index % SEEDS.length), futures.get(index).get());
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static IrisOreGenerator generator() {
        KList<NativeBlockState> states = new KList<>(
                new KeyedBlockState("minecraft:iron_ore"), new KeyedBlockState("minecraft:gold_ore"));
        IrisMaterialPalette palette = new IrisMaterialPalette() {
            @Override
            public KList<NativeBlockState> getBlockData(IrisData data) {
                return states;
            }
        };
        palette.setStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX));
        return new IrisOreGenerator().setPalette(palette).setThreshold(0.57D)
                .setRange(new IrisRange(0D, 256D))
                .setChanceStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX));
    }

    private static long[] sample(IrisOreGenerator generator, RNG rng, IrisData data, boolean reverse) {
        long[] output = new long[SAMPLES];
        for (int index = 0; index < SAMPLES; index++) {
            int sample = reverse ? SAMPLES - index - 1 : index;
            NativeBlockState block = generator.generate(sample * 13 - 901,
                    sample % 257, sample * 7 - 713, rng, data);
            output[sample] = block == null ? 0L : block.key().hashCode();
        }
        return output;
    }

    private static long hash(long[] values) {
        long hash = 0xcbf29ce484222325L;
        for (long value : values) {
            hash = (hash ^ value) * 0x100000001b3L;
        }
        return hash;
    }
}
