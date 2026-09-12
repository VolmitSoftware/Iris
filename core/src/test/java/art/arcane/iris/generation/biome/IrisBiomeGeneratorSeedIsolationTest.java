package art.arcane.iris.generation.biome;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisBiomeGeneratorSeedIsolationTest {
    private static final double SAMPLE_X = 128D;
    private static final double SAMPLE_Z = -64D;

    @Test
    public void engineSeedMakesPaperStyleFirstCallOrderIrrelevant() {
        IrisData data = dataWithActiveEngine(new AtomicReference<>(engine(1337L)));
        IrisBiome paperFirst = biome(data);
        IrisBiome moddedFirst = biome(data);

        CNG paperGenerator = paperFirst.getBiomeGenerator(new RNG(8457289L));
        CNG moddedGenerator = moddedFirst.getBiomeGenerator(new RNG(-991245L));

        assertEquals(paperGenerator.noise(SAMPLE_X, SAMPLE_Z), moddedGenerator.noise(SAMPLE_X, SAMPLE_Z), 0D);
    }

    @Test
    public void distinctEnginesKeepDistinctBiomeGenerators() {
        Engine firstEngine = engine(1337L);
        Engine secondEngine = engine(7331L);
        IrisBiome biome = biome(dataWithActiveEngine(new AtomicReference<>(secondEngine)));

        CNG first = biome.getBiomeGenerator(new RNG(1L), firstEngine);
        CNG second = biome.getBiomeGenerator(new RNG(1L), secondEngine);

        assertNotSame(first, second);
        assertNotEquals(first.noise(SAMPLE_X, SAMPLE_Z), second.noise(SAMPLE_X, SAMPLE_Z), 0D);
    }

    @Test
    public void sameSeedEnginesSharingBiomeReuseBiomeGenerator() {
        Engine firstEngine = engine(1337L);
        Engine secondEngine = engine(1337L);
        IrisBiome biome = biome(dataWithActiveEngine(new AtomicReference<>(secondEngine)));

        CNG first = biome.getBiomeGenerator(new RNG(11L), firstEngine);
        CNG second = biome.getBiomeGenerator(new RNG(99L), secondEngine);

        assertSame(first, second);
    }

    @Test
    public void engineLessToolingUsesSuppliedRngSeed() {
        IrisBiome biome = biome(null);

        CNG first = biome.getBiomeGenerator(new RNG(11L));
        CNG sameSeed = biome.getBiomeGenerator(new RNG(11L));
        CNG second = biome.getBiomeGenerator(new RNG(99L));

        assertSame(first, sameSeed);
        assertNotSame(first, second);
        assertNotEquals(first.noise(SAMPLE_X, SAMPLE_Z), second.noise(SAMPLE_X, SAMPLE_Z), 0D);
    }

    @Test
    public void concurrentExactEnginesIgnoreLoaderEngine() throws Exception {
        Engine firstEngine = engine(1337L);
        Engine secondEngine = engine(7331L);
        IrisBiome biome = biome(dataWithActiveEngine(new AtomicReference<>(secondEngine)));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CNG> firstFuture = executor.submit(() -> repeatedlyResolve(biome, firstEngine, start));
            Future<CNG> secondFuture = executor.submit(() -> repeatedlyResolve(biome, secondEngine, start));
            start.countDown();

            CNG first = firstFuture.get();
            CNG second = secondFuture.get();
            assertNotSame(first, second);
            assertSame(first, biome.getBiomeGenerator(new RNG(99L), firstEngine));
            assertSame(second, biome.getBiomeGenerator(new RNG(99L), secondEngine));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void biomeGeneratorCacheEvictsOldSeeds() {
        IrisBiome biome = biome(null);
        CNG first = biome.getBiomeGenerator(new RNG(0L));

        for (long seed = 1L; seed <= 8L; seed++) {
            biome.getBiomeGenerator(new RNG(seed));
        }

        assertNotSame(first, biome.getBiomeGenerator(new RNG(0L)));
    }

    private IrisBiome biome(IrisData data) {
        IrisBiome biome = new IrisBiome().setName("Scatter Test");
        biome.setLoader(data);
        return biome;
    }

    private IrisData dataWithActiveEngine(AtomicReference<Engine> activeEngine) {
        IrisData data = mock(IrisData.class);
        when(data.getEngine()).thenAnswer(ignored -> activeEngine.get());
        return data;
    }

    private Engine engine(long biomeSeed) {
        SeedManager seedManager = mock(SeedManager.class);
        when(seedManager.getBiome()).thenReturn(biomeSeed);
        Engine engine = mock(Engine.class);
        when(engine.getSeedManager()).thenReturn(seedManager);
        return engine;
    }

    private CNG repeatedlyResolve(IrisBiome biome, Engine engine, CountDownLatch start) throws InterruptedException {
        start.await();
        CNG generator = biome.getBiomeGenerator(new RNG(1L), engine);
        for (int index = 0; index < 100; index++) {
            assertSame(generator, biome.getBiomeGenerator(new RNG(index + 2L), engine));
        }
        return generator;
    }
}
