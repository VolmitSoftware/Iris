package art.arcane.iris.engine.object.world.dimension;

import art.arcane.iris.builtin.floating.FloatingChildBiome;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.builtin.carve.CarvingBiomeSettings;
import art.arcane.iris.builtin.carve.CarvingEntry;
import art.arcane.iris.builtin.carve.CarvingSettings;
import art.arcane.iris.builtin.floating.FloatingChildSettings;
import art.arcane.iris.core.modules.BuiltInPack;
import com.google.gson.Gson;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.IrisFeature;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisDimensionReachableBiomesTest {
    @Test
    @SuppressWarnings("unchecked")
    public void includesExactRecursiveGenerationClosure() {
        CarvingEntry deepBand = entry("global-deep-band", true, "deep-root");
        CarvingEntry disabledBand = entry("disabled-band", false, "disabled-deep");
        CarvingEntry floatingCarvingEntry = entry("floating-carving-entry", false, "entry-floating-carve");
        IrisDimension dimension = new IrisDimension().setRegions(new KList<>("reachable", "missing"));
        dimension.setModuleSettings(BuiltInPack.settings(new CarvingSettings(List.of(deepBand, disabledBand, floatingCarvingEntry))));
        IrisRegion reachable = new IrisRegion()
                .setLandBiomes(new KList<>("parent", "shared"))
                .setSeaBiomes(new KList<>("shared"));
        IrisBiome parent = biome("parent", carvingBiome("carve"), floatingChildren(floating("floating-target", "direct-floating-carve")))
                .setChildren(new KList<>("child", "shared"));
        IrisBiome child = biome("child").setChildren(new KList<>("parent"));
        IrisBiome shared = biome("shared");
        IrisBiome carve = biome("carve");
        IrisBiome floatingTarget = biome("floating-target", carvingBiome("floating-carve"))
                .setChildren(new KList<>("floating-child"));
        IrisBiome floatingChild = biome("floating-child", floatingChildren(floating("nested-floating", "floating-carving-entry")));
        IrisBiome floatingCarve = biome("floating-carve");
        IrisBiome directFloatingCarve = biome("direct-floating-carve");
        IrisBiome entryFloatingCarve = biome("entry-floating-carve");
        IrisBiome shadowedFloatingCarve = biome("floating-carving-entry");
        IrisBiome nestedFloating = biome("nested-floating", floatingChildren(floating("parent")));
        IrisBiome deepRoot = biome("deep-root").setChildren(new KList<>("deep-child"));
        IrisBiome deepChild = biome("deep-child", carvingBiome("deep-carve"));
        IrisBiome deepCarve = biome("deep-carve", floatingChildren(floating("deep-floating")));
        IrisBiome deepFloating = biome("deep-floating").setChildren(new KList<>("deep-root"));
        IrisBiome disabledDeep = biome("disabled-deep");
        IrisBiome unused = biome("unused");

        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisRegion> regionLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        when(data.getRegionLoader()).thenReturn(regionLoader);
        when(data.getBiomeLoader()).thenReturn(biomeLoader);
        when(regionLoader.load("reachable")).thenReturn(reachable);
        when(biomeLoader.load("parent")).thenReturn(parent);
        when(biomeLoader.load("child")).thenReturn(child);
        when(biomeLoader.load("shared")).thenReturn(shared);
        when(biomeLoader.load("carve")).thenReturn(carve);
        when(biomeLoader.load("floating-target")).thenReturn(floatingTarget);
        when(biomeLoader.load("floating-child")).thenReturn(floatingChild);
        when(biomeLoader.load("floating-carve")).thenReturn(floatingCarve);
        when(biomeLoader.load("direct-floating-carve")).thenReturn(directFloatingCarve);
        when(biomeLoader.load("entry-floating-carve")).thenReturn(entryFloatingCarve);
        when(biomeLoader.load("floating-carving-entry")).thenReturn(shadowedFloatingCarve);
        when(biomeLoader.load("nested-floating")).thenReturn(nestedFloating);
        when(biomeLoader.load("deep-root")).thenReturn(deepRoot);
        when(biomeLoader.load("deep-child")).thenReturn(deepChild);
        when(biomeLoader.load("deep-carve")).thenReturn(deepCarve);
        when(biomeLoader.load("deep-floating")).thenReturn(deepFloating);
        when(biomeLoader.load("disabled-deep")).thenReturn(disabledDeep);
        when(biomeLoader.load("unused")).thenReturn(unused);

        KList<IrisBiome> biomes = dimension.getReachableBiomes(() -> data);
        Set<String> keys = biomes.stream().map(IrisBiome::getLoadKey).collect(Collectors.toSet());

        assertEquals(Set.of(
                "parent", "child", "shared", "carve",
                "floating-target", "floating-child", "floating-carve", "direct-floating-carve",
                "entry-floating-carve", "nested-floating",
                "deep-root", "deep-child", "deep-carve", "deep-floating"
        ), keys);
        assertEquals(keys.size(), biomes.size());
    }

    @Test(timeout = 1000L)
    @SuppressWarnings("unchecked")
    public void terminatesMixedDependencyCyclesWithoutReloadingBiomes() {
        IrisDimension dimension = new IrisDimension().setRegions(new KList<>("reachable"));
        IrisRegion reachable = new IrisRegion().setLandBiomes(new KList<>("a"));
        IrisBiome a = biome("a").setChildren(new KList<>("b"));
        IrisBiome b = biome("b", carvingBiome("c"));
        IrisBiome c = biome("c", floatingChildren(floating("d", "a")));
        IrisBiome d = biome("d", floatingChildren(floating("b")))
                .setChildren(new KList<>("a"));

        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisRegion> regionLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        when(data.getRegionLoader()).thenReturn(regionLoader);
        when(data.getBiomeLoader()).thenReturn(biomeLoader);
        when(regionLoader.load("reachable")).thenReturn(reachable);
        when(biomeLoader.load("a")).thenReturn(a);
        when(biomeLoader.load("b")).thenReturn(b);
        when(biomeLoader.load("c")).thenReturn(c);
        when(biomeLoader.load("d")).thenReturn(d);

        KList<IrisBiome> biomes = dimension.getReachableBiomes(() -> data);
        Set<String> keys = biomes.stream().map(IrisBiome::getLoadKey).collect(Collectors.toSet());

        assertEquals(Set.of("a", "b", "c", "d"), keys);
        assertEquals(keys.size(), biomes.size());
        verify(biomeLoader, times(1)).load("a");
        verify(biomeLoader, times(1)).load("b");
        verify(biomeLoader, times(1)).load("c");
        verify(biomeLoader, times(1)).load("d");
    }

    private IrisBiome biome(String loadKey, IrisFeature... settings) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(loadKey);
        biome.setModuleSettings(BuiltInPack.settings(settings));
        return biome;
    }

    private CarvingBiomeSettings carvingBiome(String key) {
        return new CarvingBiomeSettings(key);
    }

    private FloatingChildSettings floatingChildren(FloatingChildBiome... children) {
        return new FloatingChildSettings(List.of(children));
    }

    private CarvingEntry entry(String id, boolean enabled, String biome) {
        return new Gson().fromJson("{\"id\": \"" + id + "\", \"enabled\": " + enabled + ", \"biome\": \"" + biome + "\"}", CarvingEntry.class);
    }

    private FloatingChildBiome floating(String biomeKey) {
        return TestSettings.with(new FloatingChildBiome(), "biome", biomeKey);
    }

    private FloatingChildBiome floating(String biomeKey, String carvingKey) {
        return TestSettings.with(new FloatingChildBiome(), "biome", biomeKey, "carving", carvingKey);
    }
}
