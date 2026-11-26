package art.arcane.iris.generation.biome;

import art.arcane.iris.testsupport.MockPackData;
import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        Engine engine = engine(1337L);
        IrisBiome paperFirst = biome();
        IrisBiome moddedFirst = biome();

        CNG paperGenerator = paperFirst.getBiomeGenerator(new RNG(8457289L), engine);
        CNG moddedGenerator = moddedFirst.getBiomeGenerator(new RNG(-991245L), engine);

        assertEquals(paperGenerator.noise(SAMPLE_X, SAMPLE_Z), moddedGenerator.noise(SAMPLE_X, SAMPLE_Z), 0D);
    }

    @Test
    public void distinctEnginesKeepDistinctBiomeGenerators() {
        IrisBiome biome = biome();

        CNG first = biome.getBiomeGenerator(new RNG(1L), engine(1337L));
        CNG second = biome.getBiomeGenerator(new RNG(1L), engine(7331L));

        assertNotSame(first, second);
        assertNotEquals(first.noise(SAMPLE_X, SAMPLE_Z), second.noise(SAMPLE_X, SAMPLE_Z), 0D);
    }

    @Test
    public void sameSeedEnginesBuildEqualButOwnBiomeGenerators() {
        IrisBiome biome = biome();
        Engine firstEngine = engine(1337L);

        CNG first = biome.getBiomeGenerator(new RNG(11L), firstEngine);
        CNG second = biome.getBiomeGenerator(new RNG(99L), engine(1337L));

        assertSame(first, biome.getBiomeGenerator(new RNG(99L), firstEngine));
        assertNotSame(first, second);
        assertEquals(first.noise(SAMPLE_X, SAMPLE_Z), second.noise(SAMPLE_X, SAMPLE_Z), 0D);
    }

    @Test
    public void engineLessToolingUsesSuppliedRngSeed() {
        IrisBiome biome = biome();

        CNG first = biome.getBiomeGenerator(new RNG(11L), null);
        CNG sameSeed = biome.getBiomeGenerator(new RNG(11L), null);
        CNG second = biome.getBiomeGenerator(new RNG(99L), null);

        assertSame(first, sameSeed);
        assertNotSame(first, second);
        assertNotEquals(first.noise(SAMPLE_X, SAMPLE_Z), second.noise(SAMPLE_X, SAMPLE_Z), 0D);
    }

    @Test
    public void concurrentEnginesKeepTheirOwnGenerators() throws Exception {
        Engine firstEngine = engine(1337L);
        Engine secondEngine = engine(7331L);
        IrisBiome biome = biome();
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
        IrisBiome biome = biome();
        CNG first = biome.getBiomeGenerator(new RNG(0L), null);

        for (long seed = 1L; seed <= 8L; seed++) {
            biome.getBiomeGenerator(new RNG(seed), null);
        }

        assertNotSame(first, biome.getBiomeGenerator(new RNG(0L), null));
    }

    private IrisBiome biome() {
        IrisBiome biome = new IrisBiome().setName("Scatter Test");
        biome.setLoader(MockPackData.create());
        return biome;
    }

    private Engine engine(long biomeSeed) {
        SeedManager seedManager = mock(SeedManager.class);
        when(seedManager.getBiome()).thenReturn(biomeSeed);
        Engine engine = mock(Engine.class);
        when(engine.getSeedManager()).thenReturn(seedManager);
        PackRuntime runtime = PackRuntime.forEngine(engine, null);
        when(engine.getPackRuntime()).thenReturn(runtime);
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
