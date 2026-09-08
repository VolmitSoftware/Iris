package art.arcane.iris.engine.object;

import art.arcane.iris.core.compat.CompatAction;
import art.arcane.iris.core.compat.CompatFinding;
import art.arcane.iris.core.compat.CompatRegistry;
import art.arcane.iris.core.compat.CompatStatus;
import art.arcane.iris.core.compat.ContentGate;
import art.arcane.iris.core.compat.PackCompatReport;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.Map;

import static art.arcane.iris.engine.object.PackCompatFixtures.MISSING_BLOCK;
import static art.arcane.iris.engine.object.PackCompatFixtures.biome;
import static art.arcane.iris.engine.object.PackCompatFixtures.excludeBlock;
import static art.arcane.iris.engine.object.PackCompatFixtures.find;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
    public void evaluateCompatDoesNotExcludeRegionThatNeverDeclaredLandBiomes() {
        PackCompatReport report = new PackCompatReport();
        IrisData data = dataWith(report);
        IrisRegion region = PackCompatFixtures.region("empty");
        region.setLoader(data);

        assertFalse(region.evaluateCompat(gate(report)).excluded());
        assertTrue(report.isEmpty());
    }
}
