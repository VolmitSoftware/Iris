package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.Objects;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisGeneratorStyleSeedTest {
    private static final long WORLD_SEED = 7_777_123L;
    private static final long OTHER_WORLD_SEED = -90_210L;
    private static final long STYLE_SEED = 510L;
    private static final long FRACTURE_SEED = 77L;
    private static final long CALLER_SEED = 4242L;
    private static final int GRID_SIZE = 36;
    private static final double GRID_STEP_BLOCKS = 53.7D;

    @Test
    public void seededStyleIgnoresTheCallerRandom() {
        IrisData data = dataOfWorld(WORLD_SEED);
        IrisGeneratorStyle style = seededSimplex(STYLE_SEED);

        assertArrayEquals(samples(style.createNoCache(new RNG(1L), data)), samples(style.createNoCache(new RNG(2L), data)), 0D);
        assertSame(style.create(new RNG(1L), data), style.create(new RNG(2L), data));
        assertSame(style.create(1L, data), style.create(new RNG(3L), data));
    }

    @Test
    public void separateStyleInstancesWithTheSameSeedShareOneNoise() {
        IrisData data = dataOfWorld(WORLD_SEED);
        CNG first = seededSimplex(STYLE_SEED).setZoom(2.5D).createNoCache(new RNG(1L), data);
        CNG second = seededSimplex(STYLE_SEED).setZoom(2.5D).createNoCache(new RNG(2L), data);
        CNG otherSeed = seededSimplex(STYLE_SEED + 1).setZoom(2.5D).createNoCache(new RNG(1L), data);

        assertArrayEquals(samples(first), samples(second), 0D);
        assertFalse(java.util.Arrays.equals(samples(first), samples(otherSeed)));
    }

    @Test
    public void seededStyleFollowsTheWorldSeed() {
        IrisGeneratorStyle style = seededSimplex(STYLE_SEED);

        double[] world = samples(style.createNoCache(new RNG(1L), dataOfWorld(WORLD_SEED)));
        double[] otherWorld = samples(style.createNoCache(new RNG(1L), dataOfWorld(OTHER_WORLD_SEED)));

        assertFalse(java.util.Arrays.equals(world, otherWorld));
    }

    @Test
    public void generatorCacheNeverHandsBackAnotherWorldsNoise() {
        IrisGeneratorStyle style = seededSimplex(STYLE_SEED);
        IrisData world = dataOfWorld(WORLD_SEED);
        IrisData otherWorld = dataOfWorld(OTHER_WORLD_SEED);

        CNG first = style.create(new RNG(1L), world);
        CNG second = style.create(new RNG(1L), otherWorld);

        assertFalse(java.util.Arrays.equals(samples(first), samples(second)));
        assertSame(first, style.create(new RNG(1L), world));
    }

    @Test
    public void seededStyleEqualsTheUnseededCopyBuiltWithTheRolledRandom() {
        for (IrisData data : new IrisData[]{dataOfWorld(WORLD_SEED), dataWithoutEngine()}) {
            long worldSeed = worldSeedOf(data);
            IrisGeneratorStyle seeded = seededSimplex(STYLE_SEED).setZoom(3D).setExponent(1.5D);
            IrisGeneratorStyle unseeded = simplex().setZoom(3D).setExponent(1.5D);

            CNG expected = unseeded.createNoCache(new RNG(rolled(worldSeed, STYLE_SEED)), data);

            assertArrayEquals(samples(expected), samples(seeded.createNoCache(new RNG(CALLER_SEED), data)), 0D);
            assertArrayEquals(samples(expected), samples(seeded.create(new RNG(CALLER_SEED), data)), 0D);
            assertArrayEquals(samples(expected), samples(seeded.createForLayer(new RNG(CALLER_SEED), data, 1)), 0D);
        }
    }

    @Test
    public void seededStyleWithPlainFractureEqualsTheUnseededCopy() {
        for (IrisData data : new IrisData[]{dataOfWorld(WORLD_SEED), dataWithoutEngine()}) {
            long worldSeed = worldSeedOf(data);
            IrisGeneratorStyle seeded = seededSimplex(STYLE_SEED).setFracture(plainFracture());
            IrisGeneratorStyle unseeded = simplex().setFracture(plainFracture());

            CNG expected = unseeded.createNoCache(new RNG(rolled(worldSeed, STYLE_SEED)), data);

            assertArrayEquals(samples(expected), samples(seeded.createNoCache(new RNG(CALLER_SEED), data)), 0D);
        }
    }

    @Test
    public void seededFractureRollsOnItsOwnUnderAnUnseededParent() {
        for (IrisData data : new IrisData[]{dataOfWorld(WORLD_SEED), dataWithoutEngine()}) {
            long worldSeed = worldSeedOf(data);
            IrisGeneratorStyle seededFracture = plainFracture().setSeed(FRACTURE_SEED);
            IrisGeneratorStyle actual = simplex().setFracture(seededFracture);

            CNG expected = simplex().createNoCache(new RNG(CALLER_SEED), data);
            expected.fractureWith(plainFracture().createNoCache(new RNG(rolled(worldSeed, FRACTURE_SEED)), data), seededFracture.getMultiplier());

            assertArrayEquals(samples(expected), samples(actual.createNoCache(new RNG(CALLER_SEED), data)), 0D);
            assertArrayEquals(samples(actual.createNoCache(new RNG(1L), data)), samples(actual.createNoCache(new RNG(1L), data)), 0D);
        }
    }

    @Test
    public void seededFractureInsideASeededStyleRollsFromItsOwnSeed() {
        for (IrisData data : new IrisData[]{dataOfWorld(WORLD_SEED), dataWithoutEngine()}) {
            long worldSeed = worldSeedOf(data);
            IrisGeneratorStyle seededFracture = plainFracture().setSeed(FRACTURE_SEED);
            IrisGeneratorStyle actual = seededSimplex(STYLE_SEED).setFracture(seededFracture);

            CNG expected = simplex().createNoCache(new RNG(rolled(worldSeed, STYLE_SEED)), data);
            expected.fractureWith(plainFracture().createNoCache(new RNG(rolled(worldSeed, FRACTURE_SEED)), data), seededFracture.getMultiplier());

            assertArrayEquals(samples(expected), samples(actual.createNoCache(new RNG(CALLER_SEED), data)), 0D);
            assertArrayEquals(samples(expected), samples(actual.create(new RNG(CALLER_SEED), data)), 0D);
        }
    }

    @Test
    public void rollForPinsTheRolledRandomSeed() {
        IrisGeneratorStyle style = seededSimplex(510L);
        assertEquals(1582317604285492835L, style.rollFor(new RNG(1L), engineOfWorld(0L)).getSeed());
        assertEquals(1582317604285492835L, style.rollFor(new RNG(2L), null).getSeed());

        IrisGeneratorStyle negativeSeed = seededSimplex(-7L);
        assertEquals(-8111716878092126206L, negativeSeed.rollFor(new RNG(1L), engineOfWorld(123456789L)).getSeed());
    }

    @Test
    public void rollForKeepsTheCallerRandomOfAnUnseededStyle() {
        RNG caller = new RNG(CALLER_SEED);

        assertSame(caller, simplex().rollFor(caller, engineOfWorld(WORLD_SEED)));
        assertSame(caller, simplex().rollFor(caller, null));
        assertFalse(simplex().isSeeded());
        assertTrue(seededSimplex(0L).isSeeded());
    }

    @Test
    public void unseededStylesHashExactlyAsBeforeTheSeedField() {
        int simplexHash = Objects.hash((Object) null, 0, 1D, 0, 1D, 0, 1D, 1D, 0D, "SIMPLEX");
        int fractureHash = Objects.hash((Object) null, 0, 1D, 0, 1D, 0, 1D, 1D, 0D, "STATIC");

        assertEquals(simplexHash, simplex().prebakeSignature());
        assertEquals(Objects.hash((Object) null, 0, 1D, fractureHash, 1D, 0, 1D, 1D, 0D, "SIMPLEX"),
                simplex().setFracture(new IrisGeneratorStyle(NoiseStyle.STATIC)).prebakeSignature());
        assertEquals(Objects.hash("expr", 0, 2D, 0, 3D, 8, 4D, 5D, 0.5D, null),
                new IrisGeneratorStyle().setStyle(null).setExpression("expr").setMultiplier(2D).setExponent(3D)
                        .setCacheSize(8).setZoom(4D).setCellularZoom(5D).setCellularFrequency(0.5D).prebakeSignature());
    }

    @Test
    public void seedChangesTheSignatureOnlyWhenSet() {
        int unseeded = simplex().prebakeSignature();

        assertNotEquals(unseeded, seededSimplex(STYLE_SEED).prebakeSignature());
        assertNotEquals(seededSimplex(STYLE_SEED).prebakeSignature(), seededSimplex(STYLE_SEED + 1).prebakeSignature());
        assertEquals(seededSimplex(STYLE_SEED).prebakeSignature(), seededSimplex(STYLE_SEED).prebakeSignature());
        assertNotEquals(unseeded, simplex().setFracture(plainFracture().setSeed(FRACTURE_SEED)).prebakeSignature());
    }

    @Test
    public void seedReadsFromJsonAndDefaultsToNull() {
        Gson gson = new Gson();

        assertEquals(Long.valueOf(510L), gson.fromJson("{\"seed\": 510}", IrisGeneratorStyle.class).getSeed());
        assertEquals(Long.valueOf(-9_007_199_254_740_993L), gson.fromJson("{\"seed\": -9007199254740993}", IrisGeneratorStyle.class).getSeed());
        assertNull(gson.fromJson("{\"zoom\": 2}", IrisGeneratorStyle.class).getSeed());
        assertNull(new IrisGeneratorStyle().getSeed());
    }

    @Test
    public void seededChildStyleIgnoresTheChildScale() {
        IrisBiome biome = biomeWithChildStyle(seededSimplex(STYLE_SEED), dataOfWorld(WORLD_SEED));

        double[] unscaled = samples(biome.getChildrenGenerator(CALLER_SEED, 1D));

        assertArrayEquals(unscaled, samples(biome.getChildrenGenerator(CALLER_SEED, 4D)), 0D);
        assertArrayEquals(unscaled, samples(biome.getChildrenGenerator(CALLER_SEED, 0.25D)), 0D);
        assertArrayEquals(unscaled, samples(biome.getChildrenGenerator(new RNG(5L), 123, 9D)), 0D);
    }

    @Test
    public void unseededChildStyleStillScales() {
        IrisBiome biome = biomeWithChildStyle(simplex(), dataOfWorld(WORLD_SEED));

        double[] unscaled = samples(biome.getChildrenGenerator(CALLER_SEED, 1D));
        double[] scaled = samples(biome.getChildrenGenerator(CALLER_SEED, 4D));

        assertFalse(java.util.Arrays.equals(unscaled, scaled));
    }

    @Test
    public void seededCarvingChildStyleIgnoresTheShrinkFactor() {
        IrisData data = dataOfWorld(WORLD_SEED);

        double[] unscaled = samples(carvingEntry(seededSimplex(STYLE_SEED), 1D).getChildrenGenerator(CALLER_SEED, data));

        assertArrayEquals(unscaled, samples(carvingEntry(seededSimplex(STYLE_SEED), 4D).getChildrenGenerator(CALLER_SEED, data)), 0D);
        assertFalse(java.util.Arrays.equals(
                samples(carvingEntry(simplex(), 1D).getChildrenGenerator(CALLER_SEED, data)),
                samples(carvingEntry(simplex(), 4D).getChildrenGenerator(CALLER_SEED, data))));
    }

    private static IrisDimensionCarvingEntry carvingEntry(IrisGeneratorStyle childStyle, double shrinkFactor) {
        return new IrisDimensionCarvingEntry().setId("deep-caves").setChildStyle(childStyle).setChildShrinkFactor(shrinkFactor);
    }

    private static IrisBiome biomeWithChildStyle(IrisGeneratorStyle childStyle, IrisData data) {
        IrisBiome biome = new IrisBiome().setChildStyle(childStyle);
        biome.setLoader(data);
        return biome;
    }

    private static IrisGeneratorStyle simplex() {
        return new IrisGeneratorStyle(NoiseStyle.SIMPLEX);
    }

    private static IrisGeneratorStyle seededSimplex(long seed) {
        return simplex().setSeed(seed);
    }

    private static IrisGeneratorStyle plainFracture() {
        return new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setZoom(2D).setMultiplier(30D);
    }

    private static Engine engineOfWorld(long worldSeed) {
        Engine engine = mock(Engine.class);
        when(engine.getSeedManager()).thenReturn(new SeedManager(worldSeed));
        return engine;
    }

    private static IrisData dataOfWorld(long worldSeed) {
        IrisData data = mock(IrisData.class);
        Engine engine = engineOfWorld(worldSeed);
        when(data.getEngine()).thenReturn(engine);
        return data;
    }

    private static IrisData dataWithoutEngine() {
        return mock(IrisData.class);
    }

    private static long worldSeedOf(IrisData data) {
        Engine engine = data.getEngine();
        return engine == null ? 0L : engine.getSeedManager().getSeed();
    }

    private static long rolled(long worldSeed, long styleSeed) {
        return mix(worldSeed ^ mix(styleSeed));
    }

    private static long mix(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private static double[] samples(CNG generator) {
        double[] sampled = new double[GRID_SIZE * GRID_SIZE * 2];
        int cursor = 0;
        for (int xIndex = 0; xIndex < GRID_SIZE; xIndex++) {
            for (int zIndex = 0; zIndex < GRID_SIZE; zIndex++) {
                double x = (xIndex - GRID_SIZE / 2) * GRID_STEP_BLOCKS + 0.31D;
                double z = (zIndex - GRID_SIZE / 2) * GRID_STEP_BLOCKS - 0.77D;
                sampled[cursor++] = generator.noise(x, z);
                sampled[cursor++] = generator.noise(x, 41.5D, z);
            }
        }
        return sampled;
    }
}
