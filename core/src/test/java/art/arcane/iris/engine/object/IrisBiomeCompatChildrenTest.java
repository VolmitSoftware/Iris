package art.arcane.iris.engine.object;

import art.arcane.iris.core.compat.CompatAction;
import art.arcane.iris.core.compat.CompatFinding;
import art.arcane.iris.core.compat.PackCompatReport;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static art.arcane.iris.engine.object.PackCompatFixtures.MISSING_BLOCK;
import static art.arcane.iris.engine.object.PackCompatFixtures.biome;
import static art.arcane.iris.engine.object.PackCompatFixtures.excludeBlock;
import static art.arcane.iris.engine.object.PackCompatFixtures.find;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisBiomeCompatChildrenTest {
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

    @Test
    public void realChildrenDropsExcludedChildAndRecordsFinding() {
        PackCompatReport report = new PackCompatReport();
        IrisBiome parent = biome("land/plains");
        IrisBiome hills = biome("land/hills");
        IrisBiome sulfur = excludeBlock(biome("land/sulfur-flats"));
        parent.setChildren(new KList<>("land/hills", "land/sulfur-flats"));
        IrisData data = dataWith(report, parent, hills, sulfur);
        parent.setLoader(data);

        KList<IrisBiome> children = parent.getRealChildren(() -> data);

        assertEquals(1, children.size());
        assertEquals("land/hills", children.get(0).getLoadKey());
        CompatFinding dropped = find(report, CompatAction.DROPPED, "biome", "land/plains");
        assertNotNull(dropped);
        assertEquals(MISSING_BLOCK, dropped.key());
        assertEquals("children[1] land/sulfur-flats", dropped.detail());
    }

    @Test
    public void realChildrenSkipsUnloadableChild() {
        PackCompatReport report = new PackCompatReport();
        IrisBiome parent = biome("land/plains");
        parent.setChildren(new KList<>("land/gone"));
        IrisData data = dataWith(report, parent);
        parent.setLoader(data);

        assertTrue(parent.getRealChildren(() -> data).isEmpty());
    }

    @Test
    public void floatingChildBiomeFallsBackToParentWhenTargetExcluded() {
        PackCompatReport report = new PackCompatReport();
        IrisBiome parent = biome("land/plains");
        IrisBiome sulfur = excludeBlock(biome("sky/sulfur-isles"));
        IrisData data = dataWith(report, parent, sulfur);
        parent.setLoader(data);
        IrisFloatingChildBiomes floating = new IrisFloatingChildBiomes().setBiome("sky/sulfur-isles");

        assertSame(parent, floating.getRealBiome(parent, data));
        CompatFinding dropped = find(report, CompatAction.DROPPED, "floating child biome", "land/plains");
        assertNotNull(dropped);
        assertEquals(MISSING_BLOCK, dropped.key());
    }

    @Test
    public void carvingEntryResolvesNullWhenBiomeExcluded() {
        PackCompatReport report = new PackCompatReport();
        IrisBiome sulfur = excludeBlock(biome("cave/sulfur-grotto"));
        IrisData data = dataWith(report, sulfur);
        IrisDimensionCarvingEntry entry = new IrisDimensionCarvingEntry()
                .setId("deep-band")
                .setBiome("cave/sulfur-grotto");

        assertNull(entry.getRealBiome(data));
        CompatFinding dropped = find(report, CompatAction.DROPPED, "carving entry", "deep-band");
        assertNotNull(dropped);
        assertEquals(MISSING_BLOCK, dropped.key());
    }
}
