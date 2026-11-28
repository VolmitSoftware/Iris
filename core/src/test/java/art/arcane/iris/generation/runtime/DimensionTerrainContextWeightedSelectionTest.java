package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeEntry;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.terrain.IrisRegionEntry;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.pack.validation.PackCompatReport;
import art.arcane.iris.testsupport.PackCompatFixtures;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static art.arcane.iris.testsupport.PackCompatFixtures.biome;
import static art.arcane.iris.testsupport.PackCompatFixtures.region;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DimensionTerrainContextWeightedSelectionTest {
    private static final int NOISE_STEPS = 1000;

    private final Map<String, IrisRegion> regions = new HashMap<>();
    private final Map<String, IrisBiome> biomes = new HashMap<>();
    private final IrisData data = dataWithLoaders();
    private final double[] noise = new double[1];
    private final ProceduralStream<Double> noiseStream = ProceduralStream.ofDouble((x, z) -> noise[0]);

    @Test
    public void weightedRegionsSplitTheNoiseByWeightInListOrder() {
        IrisRegion heavy = addRegion("heavy", 1);
        IrisRegion light = addRegion("light", 1);
        IrisDimension dimension = new IrisDimension().setRegions(
                new KList<>(new IrisRegionEntry("heavy", 3, true), IrisRegionEntry.plain("light")));
        ProceduralStream<IrisRegion> selected = DimensionTerrainContext.selectRegions(dimension, data, noiseStream);

        noise[0] = 0.75D;
        assertSame(heavy, selected.get(0D, 0D));
        noise[0] = Math.nextUp(0.75D);
        assertSame(light, selected.get(0D, 0D));
        noise[0] = -1D;
        assertSame(heavy, selected.get(0D, 0D));
        noise[0] = 2D;
        assertSame(light, selected.get(0D, 0D));
    }

    @Test
    public void regionListsWithoutObjectsStillSplitTheNoiseByRarity() {
        IrisRegion common = addRegion("common", 1);
        IrisRegion rare = addRegion("rare", 5);
        IrisRegion middle = addRegion("middle", 2);
        IrisDimension dimension = new IrisDimension().setRegions(IrisRegionEntry.plainList("common", "rare", "middle"));
        ProceduralStream<IrisRegion> selected = DimensionTerrainContext.selectRegions(dimension, data, noiseStream);
        ProceduralStream<IrisRegion> legacy = noiseStream.selectRarity(new KList<>(common, rare, middle));

        for (int step = -50; step <= NOISE_STEPS + 50; step++) {
            noise[0] = step / (double) NOISE_STEPS;
            assertSame("noise " + noise[0], legacy.get(7D, -9D), selected.get(7D, -9D));
        }
    }

    @Test
    public void weightedBiomesSplitTheNoiseByWeightAndKeepTheListType() {
        IrisBiome wide = addBiome("wide", 1);
        IrisBiome narrow = addBiome("narrow", 1);
        IrisRegion region = region("lands").setLandBiomes(
                new KList<>(new IrisBiomeEntry("wide", 2, true), IrisBiomeEntry.plain("narrow")));
        ProceduralStream<IrisBiome> selected = DimensionTerrainContext.selectBiomes(noiseStream, data, region, InferredType.LAND);

        noise[0] = 0.6D;
        assertSame(wide, selected.get(0D, 0D));
        noise[0] = 0.7D;
        assertSame(narrow, selected.get(0D, 0D));
        assertSame(InferredType.LAND, selected.get(0D, 0D).getInferredType());
    }

    @Test
    public void biomeListsWithoutObjectsStillSplitTheNoiseByRarity() {
        IrisBiome first = addBiome("first", 4);
        IrisBiome second = addBiome("second", 1);
        IrisBiome third = addBiome("third", 3);
        IrisRegion region = region("lands").setSeaBiomes(IrisBiomeEntry.plainList("first", "second", "third"));
        ProceduralStream<IrisBiome> selected = DimensionTerrainContext.selectBiomes(noiseStream, data, region, InferredType.SEA);
        ProceduralStream<IrisBiome> legacy = noiseStream.selectRarity(new KList<>(
                first.withInferredType(InferredType.SEA), second.withInferredType(InferredType.SEA),
                third.withInferredType(InferredType.SEA)));

        for (int step = -50; step <= NOISE_STEPS + 50; step++) {
            noise[0] = step / (double) NOISE_STEPS;
            assertSame("noise " + noise[0], legacy.get(1D, 2D), selected.get(1D, 2D));
        }
    }

    private IrisRegion addRegion(String key, int rarity) {
        IrisRegion added = region(key);
        added.setRarity(rarity);
        regions.put(key, added);
        return added;
    }

    private IrisBiome addBiome(String key, int rarity) {
        IrisBiome added = biome(key);
        added.setRarity(rarity);
        biomes.put(key, added);
        return added;
    }

    @SuppressWarnings("unchecked")
    private IrisData dataWithLoaders() {
        IrisData mocked = PackCompatFixtures.data(new PackCompatReport());
        ResourceLoader<IrisRegion> regionLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        when(mocked.getRegionLoader()).thenReturn(regionLoader);
        when(mocked.getBiomeLoader()).thenReturn(biomeLoader);
        when(regionLoader.load(anyString())).thenAnswer(call -> regions.get(call.<String>getArgument(0)));
        when(biomeLoader.load(anyString())).thenAnswer(call -> biomes.get(call.<String>getArgument(0)));
        when(regionLoader.loadAll(any(KList.class))).thenAnswer(call -> loadAll(regions, call.getArgument(0)));
        when(biomeLoader.loadAll(any(KList.class))).thenAnswer(call -> loadAll(biomes, call.getArgument(0)));
        return mocked;
    }

    private static <T> KList<T> loadAll(Map<String, T> loaded, KList<String> keys) {
        KList<T> found = new KList<>();
        for (String key : keys) {
            T value = loaded.get(key);
            if (value != null) {
                found.add(value);
            }
        }
        return found;
    }
}
