package art.arcane.iris.engine.object;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisDimensionFocusRegionTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private IrisData data;
    private ResourceLoader<IrisBiome> biomes;
    private ResourceLoader<IrisRegion> regions;
    private IrisDimension dimension;

    @Before
    @SuppressWarnings("unchecked")
    public void preparePack() {
        data = mock(IrisData.class);
        biomes = mock(ResourceLoader.class);
        regions = mock(ResourceLoader.class);
        when(data.getBiomeLoader()).thenReturn(biomes);
        when(data.getRegionLoader()).thenReturn(regions);
        when(data.getDataFolder()).thenReturn(temporary.getRoot());
        when(regions.getFolderName()).thenReturn("regions");
        dimension = new IrisDimension().setRegions(new KList<>("tropical"));
        dimension.setLoadKey("overworld");
    }

    @Test(timeout = 2000L)
    public void resolvesNestedChildrenCarvingAndFloodedCavesThroughCycles() {
        IrisRegion tropical = region("tropical").setLandBiomes(new KList<>("wilds"));
        biome("wilds").setChildren(new KList<>("highlands"));
        IrisBiome highlands = biome("highlands").setChildren(new KList<>("wilds"))
                .setCarvingBiome("chalk-gardens")
                .setRiverPolicy(new IrisRiverPolicy().setFloodedCaveBiomes(new KList<>("flooded")));
        IrisBiome chalk = biome("chalk-gardens").setChildren(new KList<>("chalk-child"));
        IrisBiome chalkChild = biome("chalk-child");
        IrisBiome flooded = biome("flooded").setChildren(new KList<>("flooded-child"));
        IrisBiome floodedChild = biome("flooded-child").setCarvingBiome("wilds");

        for (IrisBiome focus : List.of(highlands, chalk, chalkChild, flooded, floodedChild)) {
            assertSame(tropical, dimension.resolveFocusRegion(focus, () -> data));
        }
        Set<String> natural = tropical.getNaturalBiomes(() -> data).stream()
                .map(IrisBiome::getLoadKey).collect(Collectors.toSet());
        assertEquals(Set.of("wilds", "highlands", "chalk-gardens", "chalk-child"), natural);
    }

    @Test
    public void directCaveAndRegionFloodedReferencesKeepTheirOwner() {
        IrisRegion tropical = region("tropical").setCaveBiomes(new KList<>("cave"))
                .setRiverPolicy(new IrisRiverPolicy().setFloodedCaveBiomes(new KList<>("flooded")));

        assertSame(tropical, dimension.resolveFocusRegion(biome("cave"), () -> data));
        assertSame(tropical, dimension.resolveFocusRegion(biome("flooded"), () -> data));
    }

    @Test
    public void directOwnerTakesPrecedenceOverAnotherRegionsChildReference() {
        region("tropical").setLandBiomes(new KList<>("wilds"));
        biome("wilds").setChildren(new KList<>("highlands"));
        IrisRegion direct = region("direct").setLandBiomes(new KList<>("highlands"));
        dimension.setRegions(new KList<>("tropical", "direct"));

        assertSame(direct, dimension.resolveFocusRegion(biome("highlands"), () -> data));
    }

    @Test
    public void unownedFocusIsNeutralAndStableAcrossDimensionInstances() {
        region("tropical").setLandBiomes(new KList<>("unrelated"));
        biome("unrelated");
        IrisBiome focus = biome("standalone");
        IrisRegion first = dimension.resolveFocusRegion(focus, () -> data);
        IrisDimension reopened = new IrisDimension().setRegions(new KList<>("tropical"));
        reopened.setLoadKey("overworld");
        IrisRegion second = reopened.resolveFocusRegion(focus, () -> data);

        assertEquals(first.getLoadKey(), second.getLoadKey());
        assertEquals(new KList<>("standalone"), first.getLandBiomes());
        assertEquals(new KList<>("standalone"), first.getSeaBiomes());
        assertEquals(new KList<>("standalone"), first.getShoreBiomes());
        assertEquals(new KList<>(), first.getCaveBiomes());
        assertSame(data, first.getLoader());
        assertFalse(first.getLoadFile().exists());
        reopened.setLoadKey("underworld");
        assertNotEquals(first.getLoadKey(), reopened.resolveFocusRegion(focus, () -> data).getLoadKey());
        assertNotEquals(first.getLoadKey(), dimension.resolveFocusRegion(biome("other"), () -> data).getLoadKey());
    }

    private IrisBiome biome(String key) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        when(biomes.load(key)).thenReturn(biome);
        return biome;
    }

    private IrisRegion region(String key) {
        IrisRegion region = new IrisRegion();
        region.setLoadKey(key);
        when(regions.load(key)).thenReturn(region);
        return region;
    }
}
