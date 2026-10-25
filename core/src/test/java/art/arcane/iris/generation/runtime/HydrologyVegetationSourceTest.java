package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.decoration.HydrologyVegetation;
import art.arcane.iris.generation.decoration.IrisDecorationPart;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlantBlockState;
import art.arcane.iris.testsupport.PlantPlatform;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hunk.Hunk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class HydrologyVegetationSourceTest {
    private static final Set<String> KNOWN = Set.of("minecraft:air", "minecraft:dirt", "minecraft:fern", "minecraft:sugar_cane",
            "minecraft:short_grass", "minecraft:dead_bush", "minecraft:poppy", "minecraft:dandelion");

    private final IrisData data = mock(IrisData.class);
    private PlantPlatform platform;

    @Before
    public void bindBlocks() {
        platform = PlantPlatform.bind(KNOWN);
    }

    @After
    public void restoreBlocks() {
        platform.close();
    }

    @Test
    public void theBiomePolicyWinsOverTheRegionTheDimensionAndTheTierAndPondLists() {
        IrisHydrology config = new IrisHydrology();
        config.getTiers().get(1).setDecorators(new KList<>(shore("minecraft:fern")));
        config.getPonds().setDecorators(new KList<>(shore("minecraft:short_grass")));
        HydrologySettings settings = HydrologySettings.compile(config, 1L, 63, 0, 256);
        HydrologyTier river = settings.tiers().get(1);
        HydrologyTier stream = settings.tiers().get(0);
        IrisDimension dimension = new IrisDimension();
        IrisRegion plain = new IrisRegion();
        IrisRegion marsh = new IrisRegion();
        marsh.setHydrologyPolicy(policy(shore("minecraft:poppy")));
        IrisBiome meadow = new IrisBiome();
        IrisBiome bog = new IrisBiome();
        bog.setHydrologyPolicy(policy(shore("minecraft:dandelion")));
        IrisBiome desert = new IrisBiome();
        desert.setHydrologyPolicy(policy());
        HydrologyVegetationSource source = HydrologyVegetationSource.create(dimension, settings, data, List.of(plain, marsh),
                List.of(meadow, bog, desert));

        assertEquals("minecraft:fern", grown(source.decorators(river, meadow, plain)));
        assertEquals("minecraft:air", grown(source.decorators(stream, meadow, plain)));
        assertEquals("minecraft:short_grass", grown(source.decorators(null, meadow, plain)));
        assertEquals("minecraft:poppy", grown(source.decorators(river, meadow, marsh)));
        assertEquals("minecraft:poppy", grown(source.decorators(null, meadow, marsh)));
        assertEquals("minecraft:dandelion", grown(source.decorators(river, bog, marsh)));
        assertEquals("minecraft:dandelion", grown(source.decorators(null, bog, plain)));
        assertSame(HydrologyVegetation.Decorators.EMPTY, source.decorators(river, desert, marsh));

        dimension.setHydrologyPolicy(policy(shore("minecraft:dead_bush")));
        HydrologyVegetationSource dimensional = HydrologyVegetationSource.create(dimension, settings, data, List.of(plain, marsh),
                List.of(meadow, bog, desert));
        assertEquals("minecraft:dead_bush", grown(dimensional.decorators(river, meadow, plain)));
        assertEquals("minecraft:dead_bush", grown(dimensional.decorators(null, meadow, plain)));
        assertEquals("minecraft:poppy", grown(dimensional.decorators(river, meadow, marsh)));
        assertEquals("minecraft:dandelion", grown(dimensional.decorators(river, bog, marsh)));
        assertSame(HydrologyVegetation.Decorators.EMPTY, HydrologyVegetationSource.NONE.decorators(river, bog, marsh));
    }

    @Test
    public void onlyANonEmptyListAnywhereCountsAsPlanted() {
        IrisRegion region = new IrisRegion();
        IrisBiome biome = new IrisBiome();
        assertFalse(HydrologyVegetationSource.NONE.planted());
        assertFalse(planted(new IrisHydrology(), new IrisDimension(), region, biome));
        IrisDimension emptyDimension = new IrisDimension();
        emptyDimension.setHydrologyPolicy(policy());
        IrisRegion emptyRegion = new IrisRegion();
        emptyRegion.setHydrologyPolicy(policy());
        IrisBiome emptyBiome = new IrisBiome();
        emptyBiome.setHydrologyPolicy(policy());
        IrisHydrology emptyLists = new IrisHydrology();
        emptyLists.getPonds().setDecorators(new KList<>());
        emptyLists.getTiers().get(0).setDecorators(new KList<>());
        assertFalse(planted(emptyLists, emptyDimension, emptyRegion, emptyBiome));

        IrisHydrology tier = new IrisHydrology();
        tier.getTiers().get(2).setDecorators(new KList<>(shore("minecraft:fern")));
        assertTrue(planted(tier, new IrisDimension(), region, biome));
        IrisHydrology pond = new IrisHydrology();
        pond.getPonds().setDecorators(new KList<>(shore("minecraft:fern")));
        assertTrue(planted(pond, new IrisDimension(), region, biome));
        IrisDimension dimension = new IrisDimension();
        dimension.setHydrologyPolicy(policy(shore("minecraft:poppy")));
        assertTrue(planted(new IrisHydrology(), dimension, region, biome));
        IrisRegion plantedRegion = new IrisRegion();
        plantedRegion.setHydrologyPolicy(policy(shore("minecraft:poppy")));
        assertTrue(planted(new IrisHydrology(), new IrisDimension(), plantedRegion, biome));
        IrisBiome plantedBiome = new IrisBiome();
        plantedBiome.setHydrologyPolicy(policy(shore("minecraft:dandelion")));
        assertTrue(planted(new IrisHydrology(), new IrisDimension(), region, plantedBiome));
    }

    private boolean planted(IrisHydrology config, IrisDimension dimension, IrisRegion region, IrisBiome biome) {
        HydrologySettings settings = HydrologySettings.compile(config, 1L, 63, 0, 256);
        return HydrologyVegetationSource.create(dimension, settings, data, List.of(region), List.of(biome)).planted();
    }

    @Test
    public void concurrentFirstUseOfAnUnseenBiomePolicyCompilesItOnce() throws Exception {
        HydrologySettings settings = HydrologySettings.compile(new IrisHydrology(), 1L, 63, 0, 256);
        IrisRegion plain = new IrisRegion();
        HydrologyVegetationSource source = HydrologyVegetationSource.create(new IrisDimension(), settings, data, List.of(plain), List.of());
        IrisBiome unseen = new IrisBiome();
        unseen.setHydrologyPolicy(policy(shore("minecraft:dandelion")));
        int threads = 8;
        CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<HydrologyVegetation.Decorators>> results = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return source.decorators(settings.tiers().get(1), unseen, plain);
                }));
            }
            HydrologyVegetation.Decorators first = results.get(0).get(30, TimeUnit.SECONDS);
            for (Future<HydrologyVegetation.Decorators> result : results) {
                assertSame(first, result.get(30, TimeUnit.SECONDS));
            }
            assertEquals("minecraft:dandelion", grown(first));
            assertSame(first, source.decorators(null, unseen, plain));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void anUnknownBlockInAnyListFailsTheRuntimeBeforeGeneration() {
        HydrologySettings plain = HydrologySettings.compile(new IrisHydrology(), 1L, 63, 0, 256);
        IrisBiome bog = new IrisBiome();
        bog.setLoadKey("bog");
        bog.setHydrologyPolicy(policy(shore("minecraft:reeds")));
        IllegalArgumentException biome = assertThrows(IllegalArgumentException.class,
                () -> HydrologyVegetationSource.create(new IrisDimension(), plain, data, List.of(), List.of(bog)));
        assertTrue(biome.getMessage(), biome.getMessage().startsWith("Biome 'bog' hydrologyPolicy.decorators[0].palette[0] names the block 'minecraft:reeds'"));

        IrisHydrology tiers = new IrisHydrology();
        tiers.getTiers().get(2).setDecorators(new KList<>(shore("minecraft:cattail")));
        HydrologySettings unknownTier = HydrologySettings.compile(tiers, 1L, 63, 0, 256);
        IllegalArgumentException tier = assertThrows(IllegalArgumentException.class,
                () -> HydrologyVegetationSource.create(new IrisDimension(), unknownTier, data, List.of(), List.of()));
        assertTrue(tier.getMessage(), tier.getMessage().startsWith("Hydrology tier 'major' decorators[0].palette[0]"));

        IrisHydrology ponds = new IrisHydrology();
        ponds.getPonds().setDecorators(new KList<>(shore("minecraft:cattail")));
        HydrologySettings unknownPond = HydrologySettings.compile(ponds, 1L, 63, 0, 256);
        IllegalArgumentException pond = assertThrows(IllegalArgumentException.class,
                () -> HydrologyVegetationSource.create(new IrisDimension(), unknownPond, data, List.of(), List.of()));
        assertTrue(pond.getMessage(), pond.getMessage().startsWith("Hydrology ponds.decorators[0].palette[0]"));
    }

    private String grown(HydrologyVegetation.Decorators decorators) {
        Hunk<NativeBlockState> chunk = Hunk.newArrayHunk(16, 96, 16);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 96; y++) {
                    chunk.set(x, y, z, PlantBlockState.of(y <= 60 ? "minecraft:dirt" : "minecraft:air"));
                }
            }
        }
        HydrologyVegetation.Water water = new HydrologyVegetation.Water() {
            @Override
            public int ground(int x, int z) {
                return 60;
            }

            @Override
            public int head(int x, int z) {
                return 40;
            }

            @Override
            public double slope(int x, int z) {
                return 0D;
            }

            @Override
            public HydrologyVegetation.Decorators decorators(int x, int z) {
                return decorators;
            }
        };
        new HydrologyVegetation(3L, null).decorate(chunk, 8, 8, 8, 8, WaterRole.SHORE, water, data);
        return ((PlantBlockState) chunk.get(8, 61, 8)).block();
    }

    private static IrisHydrologyPolicy policy(IrisDecorator... decorators) {
        return new IrisHydrologyPolicy().setDecorators(new KList<>(decorators));
    }

    private static IrisDecorator shore(String block) {
        return new IrisDecorator().setPartOf(IrisDecorationPart.SHORE_LINE).setChance(1D).setPalette(new KList<>(new IrisBlockData(block)));
    }
}
