package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisFloatingChildBiomes;
import art.arcane.iris.generation.hydrology.IrisDeepFluidConfig;
import art.arcane.iris.generation.hydrology.IrisRiverPolicy;
import art.arcane.iris.testsupport.PackCompatFixtures;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisDimensionReachableBiomesTest {
    @Test
    @SuppressWarnings("unchecked")
    public void includesOutOfPoolFocusBiomeAndRegion() {
        IrisDimension dimension = new IrisDimension()
                .setRegions(new KList<>("declared"))
                .setFocus("focused-biome")
                .setFocusRegion("focused-region");
        IrisRegion declared = new IrisRegion().setLandBiomes(new KList<>("declared-biome"));
        IrisRegion focused = new IrisRegion().setLandBiomes(new KList<>("focused-region-biome"));
        declared.setLoadKey("declared");
        focused.setLoadKey("focused-region");
        IrisBiome declaredBiome = biome("declared-biome");
        IrisBiome focusedBiome = biome("focused-biome");
        IrisBiome focusedRegionBiome = biome("focused-region-biome");

        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisRegion> regionLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        when(data.getRegionLoader()).thenReturn(regionLoader);
        when(data.getBiomeLoader()).thenReturn(biomeLoader);
        when(regionLoader.load("declared")).thenReturn(declared);
        when(regionLoader.load("focused-region")).thenReturn(focused);
        when(biomeLoader.load("declared-biome")).thenReturn(declaredBiome);
        when(biomeLoader.load("focused-biome")).thenReturn(focusedBiome);
        when(biomeLoader.load("focused-region-biome")).thenReturn(focusedRegionBiome);

        Set<String> regionKeys = dimension.getAllRegions(() -> data).stream()
                .map(IrisRegion::getLoadKey)
                .collect(Collectors.toSet());
        Set<String> biomeKeys = dimension.getReachableBiomes(() -> data).stream()
                .map(IrisBiome::getLoadKey)
                .collect(Collectors.toSet());

        assertEquals(Set.of("declared", "focused-region"), regionKeys);
        assertEquals(Set.of("declared-biome", "focused-biome", "focused-region-biome"), biomeKeys);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void includesExactRecursiveGenerationClosure() {
        IrisDimensionCarvingEntry deepBand = new IrisDimensionCarvingEntry()
                .setId("global-deep-band")
                .setBiome("deep-root");
        IrisDimensionCarvingEntry disabledBand = new IrisDimensionCarvingEntry()
                .setId("disabled-band")
                .setEnabled(false)
                .setBiome("disabled-deep");
        IrisDimensionCarvingEntry floatingCarvingEntry = new IrisDimensionCarvingEntry()
                .setId("floating-carving-entry")
                .setEnabled(false)
                .setBiome("entry-floating-carve");
        IrisDimension dimension = new IrisDimension().setRegions(new KList<>("reachable", "missing"));
        dimension.setCarving(new KList<>(deepBand, disabledBand, floatingCarvingEntry));
        IrisRegion reachable = new IrisRegion()
                .setLandBiomes(new KList<>("parent", "shared"))
                .setSeaBiomes(new KList<>("shared"));
        IrisBiome parent = biome("parent")
                .setChildren(new KList<>("child", "shared"))
                .setCarvingBiome("carve")
                .setFloatingChildBiomes(new KList<>(floating("floating-target", "direct-floating-carve")));
        IrisBiome child = biome("child").setChildren(new KList<>("parent"));
        IrisBiome shared = biome("shared");
        IrisBiome carve = biome("carve");
        IrisBiome floatingTarget = biome("floating-target")
                .setChildren(new KList<>("floating-child"))
                .setCarvingBiome("floating-carve");
        IrisBiome floatingChild = biome("floating-child")
                .setFloatingChildBiomes(new KList<>(floating("nested-floating", "floating-carving-entry")));
        IrisBiome floatingCarve = biome("floating-carve");
        IrisBiome directFloatingCarve = biome("direct-floating-carve");
        IrisBiome entryFloatingCarve = biome("entry-floating-carve");
        IrisBiome shadowedFloatingCarve = biome("floating-carving-entry");
        IrisBiome nestedFloating = biome("nested-floating")
                .setFloatingChildBiomes(new KList<>(floating("parent")));
        IrisBiome deepRoot = biome("deep-root").setChildren(new KList<>("deep-child"));
        IrisBiome deepChild = biome("deep-child").setCarvingBiome("deep-carve");
        IrisBiome deepCarve = biome("deep-carve")
                .setFloatingChildBiomes(new KList<>(floating("deep-floating")));
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
        IrisBiome b = biome("b").setCarvingBiome("c");
        IrisBiome c = biome("c").setFloatingChildBiomes(new KList<>(floating("d", "a")));
        IrisBiome d = biome("d")
                .setChildren(new KList<>("a"))
                .setFloatingChildBiomes(new KList<>(floating("b")));

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

    @Test
    @SuppressWarnings("unchecked")
    public void excludesUnavailableCavePoolFromReachableClosure() {
        IrisDimension dimension = new IrisDimension().setRegions(new KList<>("reachable"));
        IrisRegion reachable = new IrisRegion()
                .setLandBiomes(new KList<>("plains"))
                .setCaveBiomes(new KList<>("carving/sulfur"));
        IrisBiome plains = biome("plains");
        IrisBiome sulfur = PackCompatFixtures.excludeBlock(biome("carving/sulfur"))
                .setChildren(new KList<>("carving/sulfur-hollows"));

        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisRegion> regionLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        when(data.getRegionLoader()).thenReturn(regionLoader);
        when(data.getBiomeLoader()).thenReturn(biomeLoader);
        when(regionLoader.load("reachable")).thenReturn(reachable);
        when(biomeLoader.load("plains")).thenReturn(plains);
        when(biomeLoader.load("carving/sulfur")).thenReturn(sulfur);

        Set<String> keys = dimension.getReachableBiomes(() -> data).stream()
                .map(IrisBiome::getLoadKey)
                .collect(Collectors.toSet());

        assertEquals(Set.of("plains"), keys);
        verify(biomeLoader, never()).load("carving/sulfur-hollows");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void includesEveryBiomeReferencedByEnabledPolicyHierarchy() {
        IrisDimension dimension = new IrisDimension()
                .setRegions(new KList<>("reachable"))
                .setRiverPolicy(new IrisRiverPolicy()
                        .setSurfaceBiomes(new KList<>("dimension-channel")));
        dimension.getHydrology().getRivers().setEnabled(true);
        IrisRegion reachable = new IrisRegion()
                .setLandBiomes(new KList<>("root"))
                .setRiverPolicy(new IrisRiverPolicy()
                        .setMouthBiomes(new KList<>("region-mouth")));
        IrisBiome root = biome("root").setRiverPolicy(new IrisRiverPolicy()
                .setShoreBiomes(new KList<>("biome-shore")));
        IrisBiome dimensionChannel = biome("dimension-channel")
                .setChildren(new KList<>("dimension-child"));
        IrisBiome dimensionChild = biome("dimension-child");
        IrisBiome regionMouth = biome("region-mouth").setRiverPolicy(new IrisRiverPolicy()
                .setBankBiomes(new KList<>("nested-dry")));
        IrisBiome biomeShore = biome("biome-shore");
        IrisBiome nestedDry = biome("nested-dry");

        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisRegion> regionLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        when(data.getRegionLoader()).thenReturn(regionLoader);
        when(data.getBiomeLoader()).thenReturn(biomeLoader);
        when(regionLoader.load("reachable")).thenReturn(reachable);
        when(biomeLoader.load("root")).thenReturn(root);
        when(biomeLoader.load("dimension-channel")).thenReturn(dimensionChannel);
        when(biomeLoader.load("dimension-child")).thenReturn(dimensionChild);
        when(biomeLoader.load("region-mouth")).thenReturn(regionMouth);
        when(biomeLoader.load("biome-shore")).thenReturn(biomeShore);
        when(biomeLoader.load("nested-dry")).thenReturn(nestedDry);

        Set<String> keys = dimension.getReachableBiomes(() -> data).stream()
                .map(IrisBiome::getLoadKey)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "root",
                "dimension-channel",
                "dimension-child",
                "region-mouth",
                "biome-shore",
                "nested-dry"
        ), keys);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void excludesPolicyOnlyBiomesWhenRiverHydrologyIsDisabled() {
        IrisDimension dimension = new IrisDimension()
                .setRegions(new KList<>("reachable"))
                .setRiverPolicy(new IrisRiverPolicy()
                        .setSurfaceBiomes(new KList<>("dimension-channel")));
        IrisRegion reachable = new IrisRegion()
                .setLandBiomes(new KList<>("root"))
                .setRiverPolicy(new IrisRiverPolicy()
                        .setMouthBiomes(new KList<>("region-mouth")));
        IrisBiome root = biome("root").setRiverPolicy(new IrisRiverPolicy()
                .setShoreBiomes(new KList<>("biome-shore")));

        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisRegion> regionLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        when(data.getRegionLoader()).thenReturn(regionLoader);
        when(data.getBiomeLoader()).thenReturn(biomeLoader);
        when(regionLoader.load("reachable")).thenReturn(reachable);
        when(biomeLoader.load("root")).thenReturn(root);

        Set<String> keys = dimension.getReachableBiomes(() -> data).stream()
                .map(IrisBiome::getLoadKey)
                .collect(Collectors.toSet());

        assertEquals(Set.of("root"), keys);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void includesPolicyBiomesForActiveDeepOnlyHydrology() {
        IrisDimension dimension = new IrisDimension()
                .setRegions(new KList<>("reachable"))
                .setRiverPolicy(new IrisRiverPolicy()
                        .setFloodedCaveBiomes(new KList<>("dimension-flooded")));
        dimension.getHydrology().getRivers().setEnabled(false);
        dimension.getHydrology().getDeepFluids().add(new IrisDeepFluidConfig()
                .setDensity(1D)
                .setContainedPools(true));
        IrisRegion reachable = new IrisRegion()
                .setLandBiomes(new KList<>("root"))
                .setRiverPolicy(new IrisRiverPolicy()
                        .setBankBiomes(new KList<>("region-dry")));
        IrisBiome root = biome("root").setRiverPolicy(new IrisRiverPolicy()
                .setFloodedCaveBiomes(new KList<>("biome-flooded")));

        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisRegion> regionLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        when(data.getRegionLoader()).thenReturn(regionLoader);
        when(data.getBiomeLoader()).thenReturn(biomeLoader);
        when(regionLoader.load("reachable")).thenReturn(reachable);
        when(biomeLoader.load("root")).thenReturn(root);
        when(biomeLoader.load("dimension-flooded")).thenReturn(biome("dimension-flooded"));
        when(biomeLoader.load("region-dry")).thenReturn(biome("region-dry"));
        when(biomeLoader.load("biome-flooded")).thenReturn(biome("biome-flooded"));

        Set<String> keys = dimension.getReachableBiomes(() -> data).stream()
                .map(IrisBiome::getLoadKey)
                .collect(Collectors.toSet());

        assertEquals(Set.of("root", "dimension-flooded", "region-dry", "biome-flooded"), keys);
    }

    private IrisBiome biome(String loadKey) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(loadKey);
        return biome;
    }

    private IrisFloatingChildBiomes floating(String biomeKey) {
        return new IrisFloatingChildBiomes().setBiome(biomeKey);
    }

    private IrisFloatingChildBiomes floating(String biomeKey, String carvingKey) {
        return floating(biomeKey).setCarving(carvingKey);
    }
}
