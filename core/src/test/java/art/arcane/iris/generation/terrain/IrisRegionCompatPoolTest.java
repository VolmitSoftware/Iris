package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.testsupport.PackCompatFixtures;

import art.arcane.iris.pack.validation.CompatAction;
import art.arcane.iris.pack.validation.CompatFinding;
import art.arcane.iris.pack.validation.CompatRegistry;
import art.arcane.iris.pack.validation.CompatStatus;
import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.validation.PackCompatReport;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.Map;

import static art.arcane.iris.testsupport.PackCompatFixtures.MISSING_BLOCK;
import static art.arcane.iris.testsupport.PackCompatFixtures.biome;
import static art.arcane.iris.testsupport.PackCompatFixtures.excludeBlock;
import static art.arcane.iris.testsupport.PackCompatFixtures.find;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisRegionCompatPoolTest {
    @SuppressWarnings("unchecked")
    private static IrisData dataWith(PackCompatReport report, IrisBiome... biomes) {
        IrisData data = PackCompatFixtures.data(report);
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        when(data.getBiomeLoader()).thenReturn(biomeLoader);
        for (IrisBiome biome : biomes) {
            when(biomeLoader.load(biome.getLoadKey())).thenReturn(biome);
        }
        return data;
    }

    private static ContentGate gate(PackCompatReport report) {
        return new ContentGate(null, Map.of(), report);
    }

    @Test
    public void realLandBiomesDropsExcludedReferenceAndRecordsFinding() {
        PackCompatReport report = new PackCompatReport();
        IrisBiome plains = biome("land/plains");
        IrisBiome sulfur = excludeBlock(biome("cave/sulfur-grotto"));
        IrisData data = dataWith(report, plains, sulfur);
        IrisRegion region = PackCompatFixtures.region("overworld");
        region.setLoader(data);
        region.setLandBiomes(new KList<>("land/plains", "cave/sulfur-grotto"));

        KList<IrisBiome> pool = region.getRealLandBiomes(() -> data);

        assertEquals(1, pool.size());
        assertEquals("land/plains", pool.get(0).getLoadKey());
        CompatFinding dropped = find(report, CompatAction.DROPPED, "region", "overworld");
        assertNotNull(dropped);
        assertEquals(CompatRegistry.BLOCK, dropped.registry());
        assertEquals(MISSING_BLOCK, dropped.key());
        assertEquals("landBiomes[1] cave/sulfur-grotto", dropped.detail());
    }

    @Test
    public void realPoolsSkipUnloadableReferences() {
        PackCompatReport report = new PackCompatReport();
        IrisBiome shore = biome("shore/sand");
        IrisData data = dataWith(report, shore);
        IrisRegion region = PackCompatFixtures.region("overworld");
        region.setLoader(data);
        region.setSeaBiomes(new KList<>("sea/gone"));
        region.setShoreBiomes(new KList<>("shore/sand"));
        region.setCaveBiomes(new KList<>("cave/gone"));

        assertTrue(region.getRealSeaBiomes(() -> data).isEmpty());
        assertEquals(1, region.getRealShoreBiomes(() -> data).size());
        assertTrue(region.getRealCaveBiomes(() -> data).isEmpty());
        assertTrue(report.isEmpty());
    }

    @Test
    public void naturalBiomesSkipExcludedBiomesAndTheirChildren() {
        PackCompatReport report = new PackCompatReport();
        IrisBiome plains = biome("land/plains");
        IrisBiome sulfur = excludeBlock(biome("cave/sulfur-grotto"));
        sulfur.setChildren(new KList<>("cave/sulfur-deep"));
        IrisBiome sulfurDeep = biome("cave/sulfur-deep");
        IrisData data = dataWith(report, plains, sulfur, sulfurDeep);
        IrisRegion region = PackCompatFixtures.region("overworld");
        region.setLoader(data);
        region.setLandBiomes(new KList<>("land/plains"));
        region.setCaveBiomes(new KList<>("cave/sulfur-grotto"));

        KList<IrisBiome> natural = region.getNaturalBiomes(() -> data);

        assertEquals(1, natural.size());
        assertEquals("land/plains", natural.get(0).getLoadKey());
    }

    @Test
    public void evaluateCompatExcludesRegionWithNoLandBiomesLeft() {
        PackCompatReport report = new PackCompatReport();
        IrisBiome sulfur = excludeBlock(biome("cave/sulfur-flats"));
        IrisData data = dataWith(report, sulfur);
        IrisRegion region = PackCompatFixtures.region("sulfur-lands");
        region.setLoader(data);
        region.setLandBiomes(new KList<>("cave/sulfur-flats"));

        CompatStatus status = region.evaluateCompat(gate(report));

        assertTrue(status.excluded());
        CompatFinding cascade = find(report, CompatAction.EXCLUDED, "region", "sulfur-lands");
        assertNotNull(cascade);
        assertEquals(MISSING_BLOCK, cascade.key());
        assertEquals(CompatRegistry.BLOCK, cascade.registry());
        assertEquals("no land biomes remain", cascade.detail());
    }

    @Test
    public void evaluateCompatKeepsRegionWithSurvivingLandBiome() {
        PackCompatReport report = new PackCompatReport();
        IrisBiome plains = biome("land/plains");
        IrisBiome sulfur = excludeBlock(biome("cave/sulfur-flats"));
        IrisData data = dataWith(report, plains, sulfur);
        IrisRegion region = PackCompatFixtures.region("overworld");
        region.setLoader(data);
        region.setLandBiomes(new KList<>("land/plains", "cave/sulfur-flats"));

        assertFalse(region.evaluateCompat(gate(report)).excluded());
    }

    @Test
    public void evaluateCompatExcludesRegionWithNoShoreBiomesLeft() {
        PackCompatReport report = new PackCompatReport();
        IrisBiome plains = biome("land/plains");
        IrisBiome beach = PackCompatFixtures.exclude(biome("swamp/shore/beach"),
                CompatRegistry.BLOCK, "minecraft:firefly_bush");
        IrisData data = dataWith(report, plains, beach);
        IrisRegion region = PackCompatFixtures.region("swamp");
        region.setLoader(data);
        region.setLandBiomes(new KList<>("land/plains"));
        region.setShoreBiomes(new KList<>("swamp/shore/beach"));

        CompatStatus status = region.evaluateCompat(gate(report));

        assertTrue(status.excluded());
        assertSame(plains, region.getRealLandBiomes(() -> data).getFirst());
        CompatFinding cascade = find(report, CompatAction.EXCLUDED, "region", "swamp");
        assertNotNull(cascade);
        assertEquals("minecraft:firefly_bush", cascade.key());
        assertEquals("no shore biomes remain", cascade.detail());
        CompatFinding dropped = find(report, CompatAction.DROPPED, "region", "swamp");
        assertNotNull(dropped);
        assertEquals("shoreBiomes[0] swamp/shore/beach", dropped.detail());
    }

    @Test
    public void evaluateCompatExcludesRegionWithNoSeaBiomesLeft() {
        PackCompatReport report = new PackCompatReport();
        IrisBiome plains = biome("land/plains");
        IrisBiome sea = excludeBlock(biome("sea/sulfur"));
        IrisData data = dataWith(report, plains, sea);
        IrisRegion region = PackCompatFixtures.region("overworld");
        region.setLoader(data);
        region.setLandBiomes(new KList<>("land/plains"));
        region.setSeaBiomes(new KList<>("sea/sulfur"));

        assertTrue(region.evaluateCompat(gate(report)).excluded());
        CompatFinding cascade = find(report, CompatAction.EXCLUDED, "region", "overworld");
        assertNotNull(cascade);
        assertEquals(MISSING_BLOCK, cascade.key());
        assertEquals("no sea biomes remain", cascade.detail());
    }

    @Test
    public void evaluateCompatPreservesSurvivingSurfacePoolOrder() {
        PackCompatReport report = new PackCompatReport();
        IrisBiome plains = biome("land/plains");
        IrisBiome sea = biome("sea/ocean");
        IrisBiome sand = biome("shore/sand");
        IrisBiome gravel = biome("shore/gravel");
        IrisBiome sulfur = excludeBlock(biome("shore/sulfur"));
        IrisData data = dataWith(report, plains, sea, sand, gravel, sulfur);
        IrisRegion region = PackCompatFixtures.region("overworld");
        region.setLoader(data);
        region.setLandBiomes(new KList<>("land/plains"));
        region.setSeaBiomes(new KList<>("shore/sulfur", "sea/ocean"));
        region.setShoreBiomes(new KList<>("shore/sand", "shore/sulfur", "shore/gravel"));

        assertFalse(region.evaluateCompat(gate(report)).excluded());
        KList<IrisBiome> shores = region.getRealShoreBiomes(() -> data);
        assertEquals(2, shores.size());
        assertSame(sand, shores.get(0));
        assertSame(gravel, shores.get(1));
        assertSame(sea, region.getRealSeaBiomes(() -> data).getFirst());
    }

    @Test
    public void evaluateCompatAllowsOmittedSurfacePoolsAndDepletedCavePool() {
        PackCompatReport report = new PackCompatReport();
        IrisBiome plains = biome("land/plains");
        IrisBiome sulfur = excludeBlock(biome("cave/sulfur"));
        IrisData data = dataWith(report, plains, sulfur);
        IrisRegion region = PackCompatFixtures.region("highlands");
        region.setLoader(data);
        region.setLandBiomes(new KList<>("land/plains"));
        region.setCaveBiomes(new KList<>("cave/sulfur"));

        assertFalse(region.evaluateCompat(gate(report)).excluded());
        assertTrue(region.getRealSeaBiomes(() -> data).isEmpty());
        assertTrue(region.getRealShoreBiomes(() -> data).isEmpty());
        assertTrue(region.getRealCaveBiomes(() -> data).isEmpty());
    }

    @Test
    public void evaluateCompatDoesNotMisattributeUnresolvedShorePoolToLandDrop() {
        PackCompatReport report = new PackCompatReport();
        IrisBiome plains = biome("land/plains");
        IrisBiome sulfur = excludeBlock(biome("land/sulfur"));
        IrisData data = dataWith(report, plains, sulfur);
        IrisRegion region = PackCompatFixtures.region("overworld");
        region.setLoader(data);
        region.setLandBiomes(new KList<>("land/plains", "land/sulfur"));
        region.setShoreBiomes(new KList<>("shore/missing"));

        assertFalse(region.evaluateCompat(gate(report)).excluded());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void dimensionDropsRegionWhoseDeclaredShorePoolWasExcluded() {
        PackCompatReport report = new PackCompatReport();
        IrisBiome plains = biome("land/plains");
        IrisBiome beach = excludeBlock(biome("shore/sulfur"));
        IrisData data = dataWith(report, plains, beach);
        IrisRegion swamp = PackCompatFixtures.region("swamp");
        swamp.setLoader(data);
        swamp.setLandBiomes(new KList<>("land/plains"));
        swamp.setShoreBiomes(new KList<>("shore/sulfur"));
        swamp.setCompat(swamp.evaluateCompat(gate(report)));
        IrisRegion highlands = PackCompatFixtures.region("highlands");
        ResourceLoader<IrisRegion> regions = mock(ResourceLoader.class);
        when(data.getRegionLoader()).thenReturn(regions);
        when(regions.load("swamp")).thenReturn(swamp);
        when(regions.load("highlands")).thenReturn(highlands);
        IrisDimension dimension = new IrisDimension().setRegions(new KList<>("swamp", "highlands"));
        dimension.setLoadKey("overworld");
        dimension.setLoader(data);

        assertFalse(dimension.evaluateCompat(gate(report)).excluded());
        KList<IrisRegion> surviving = dimension.getAllRegions(() -> data);
        assertEquals(1, surviving.size());
        assertSame(highlands, surviving.getFirst());
        CompatFinding drop = find(report, CompatAction.DROPPED, "dimension", "overworld");
        assertNotNull(drop);
        assertEquals("regions[0] swamp", drop.detail());
    }

    @Test
    public void evaluateCompatDoesNotExcludeRegionThatNeverDeclaredLandBiomes() {
        PackCompatReport report = new PackCompatReport();
        IrisData data = dataWith(report);
        IrisRegion region = PackCompatFixtures.region("empty");
        region.setLoader(data);

        assertFalse(region.evaluateCompat(gate(report)).excluded());
        assertTrue(report.isEmpty());
    }
}
