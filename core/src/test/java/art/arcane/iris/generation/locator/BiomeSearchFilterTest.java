package art.arcane.iris.generation.locator;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.IrisRiverPolicy;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.generation.image.IrisImageMapApplication;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.subterrain.SubterrainRoom;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;
import java.util.function.BiFunction;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BiomeSearchFilterTest {
    private static final int SURFACE_Y = 200;
    private static final int CAVE_Y = 20;

    private Engine engine;
    private IrisDimension dimension;
    private IrisComplex complex;
    private ResourceLoader<IrisBiome> biomeLoader;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        engine = mock(Engine.class);
        dimension = mock(IrisDimension.class);
        complex = mock(IrisComplex.class);
        IrisData data = mock(IrisData.class);
        biomeLoader = (ResourceLoader<IrisBiome>) mock(ResourceLoader.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getData()).thenReturn(data);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.answersFromNaturalTerrain(anyInt(), anyInt())).thenReturn(true);
        when(data.getBiomeLoader()).thenReturn(biomeLoader);
        when(dimension.getCarving()).thenReturn(new KList<>());
        when(dimension.getSubterrainFeatures()).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        when(complex.getLandBiomeStream()).thenReturn(constantStream(biome("land_root")));
        when(complex.getSeaBiomeStream()).thenReturn(constantStream(biome("sea_root")));
        when(complex.getShoreBiomeStream()).thenReturn(constantStream(biome("shore_root")));
        when(complex.getCaveBiomeStream()).thenReturn(constantStream(biome("other_cave")));
    }

    @Test
    public void surfaceTargetIsAdmittedOnlyInsideItsHostRegionWithoutTerrainHeight() {
        IrisRegion host = region("host");
        host.setLandBiomes(keys("land_root"));
        IrisBiome root = biome("land_root");
        root.setChildren(keys("target"));
        stubBiome(root);
        stubBiome(biome("target"));
        stubRegions(host);
        IrisRegion foreign = region("foreign");
        when(complex.getRegionStream()).thenReturn(positionalStream((x, z) -> x < 0D ? foreign : host, host));

        BiomeSearchFilter filter = BiomeSearchFilter.of(engine, Set.of("target"));

        assertTrue(filter.column(100, 100).admits(SURFACE_Y));
        assertTrue(filter.column(100, 100).admits(CAVE_Y));
        assertFalse(filter.column(-100, 100).admits(SURFACE_Y));
        assertFalse(filter.column(-100, 100).admits(CAVE_Y));
        verify(complex, never()).naturalSurfaceBiome(anyInt(), anyInt());
        verify(complex, never()).naturalTrueHeight(anyInt(), anyInt());
        verify(complex, never()).getNaturalHeightStream();
        verify(engine, never()).getHostSurfaceBiome(anyInt(), anyInt());
    }

    @Test
    public void caveTargetIsAdmittedOnlyBelowTheCaveSwitch() {
        IrisRegion host = region("host");
        host.setCaveBiomes(keys("cavey"));
        stubBiome(biome("cavey"));
        stubRegions(host);
        when(complex.getRegionStream()).thenReturn(constantStream(host));
        when(complex.getCaveBiomeStream()).thenReturn(positionalStream(
                (x, z) -> x >= 512D ? biome("cavey") : biome("other_cave"), biome("other_cave")));

        BiomeSearchFilter filter = BiomeSearchFilter.of(engine, Set.of("cavey"));

        assertTrue(filter.column(600, 0).admits(CAVE_Y));
        assertFalse(filter.column(600, 0).admits(SURFACE_Y));
        assertFalse(filter.column(0, 0).admits(CAVE_Y));
    }

    @Test
    public void imageMappedSurfaceDecidesTheSurfaceBiome() {
        IrisRegion host = region("host");
        host.setLandBiomes(keys("land_root"));
        stubBiome(biome("land_root"));
        stubRegions(host);
        when(complex.getRegionStream()).thenReturn(constantStream(host));
        IrisImageMapRuntime maps = mock(IrisImageMapRuntime.class);
        when(maps.has(IrisImageMapApplication.BIOME)).thenReturn(true);
        when(maps.sampleBiome(anyDouble(), anyDouble())).thenAnswer(invocation ->
                invocation.<Double>getArgument(0) >= 64D ? biome("mapped") : null);
        when(complex.getImageMapRuntime()).thenReturn(maps);

        BiomeSearchFilter mapped = BiomeSearchFilter.of(engine, Set.of("mapped"));
        BiomeSearchFilter natural = BiomeSearchFilter.of(engine, Set.of("land_root"));

        assertTrue(mapped.column(100, 0).admits(SURFACE_Y));
        assertFalse(mapped.column(0, 0).admits(SURFACE_Y));
        assertTrue(natural.column(0, 0).admits(SURFACE_Y));
        assertFalse(natural.column(100, 0).admits(SURFACE_Y));
    }

    @Test
    public void plannedHydrologyColumnsAreAdmittedForRiverPolicyBiomes() {
        stubRegions();
        when(complex.getRegionStream()).thenReturn(constantStream(region("host")));
        when(complex.getHydrologyRuntime()).thenReturn(mock(IrisHydrologyRuntime.class));
        when(dimension.getRiverPolicy()).thenReturn(new IrisRiverPolicy().setSurfaceBiomes(keys("river_root")));
        IrisBiome river = biome("river_root");
        river.setChildren(keys("target"));
        stubBiome(river);
        when(engine.answersFromNaturalTerrain(32, 32)).thenReturn(false);

        BiomeSearchFilter filter = BiomeSearchFilter.of(engine, Set.of("target"));

        assertTrue(filter.column(32, 32).admits(SURFACE_Y));
        assertFalse(filter.column(0, 0).admits(SURFACE_Y));
    }

    @Test
    public void hydrologyIsNotConsultedForBiomesNoRiverPolicyPlaces() {
        stubRegions();
        when(complex.getRegionStream()).thenReturn(constantStream(region("host")));
        when(complex.getHydrologyRuntime()).thenReturn(mock(IrisHydrologyRuntime.class));
        when(dimension.getRiverPolicy()).thenReturn(new IrisRiverPolicy().setSurfaceBiomes(keys("river_root")));
        stubBiome(biome("river_root"));
        when(engine.answersFromNaturalTerrain(anyInt(), anyInt())).thenReturn(false);

        BiomeSearchFilter filter = BiomeSearchFilter.of(engine, Set.of("target"));

        assertFalse(filter.column(32, 32).admits(SURFACE_Y));
        verify(engine, never()).answersFromNaturalTerrain(anyInt(), anyInt());
    }

    @Test
    public void columnsWithoutHydrologyStayFilteredWhenNotAnsweringFromNaturalTerrain() {
        stubRegions();
        when(complex.getRegionStream()).thenReturn(constantStream(region("host")));
        when(engine.answersFromNaturalTerrain(anyInt(), anyInt())).thenReturn(false);

        BiomeSearchFilter filter = BiomeSearchFilter.of(engine, Set.of("target"));

        assertFalse(filter.column(32, 32).admits(SURFACE_Y));
    }

    @Test
    public void focusedAndStackedEnginesAreNeverFiltered() {
        stubRegions();
        when(complex.getRegionStream()).thenReturn(constantStream(region("host")));
        when(complex.getFocusBiome()).thenReturn(biome("focus"));

        assertTrue(BiomeSearchFilter.of(engine, Set.of("target")).column(0, 0).admits(SURFACE_Y));

        when(complex.getFocusBiome()).thenReturn(null);
        when(engine.getDimensionStackContext()).thenReturn(mock(DimensionStackContext.class));

        assertTrue(BiomeSearchFilter.of(engine, Set.of("target")).column(0, 0).admits(SURFACE_Y));
    }

    @Test
    public void subterrainFeatureTargetIsAdmittedWhereTheFeatureOwnsTheSample() {
        stubRegions();
        when(complex.getRegionStream()).thenReturn(constantStream(region("host")));
        when(dimension.getSubterrainFeatures()).thenReturn(new KList<>(
                new IrisSubterrainFeature().setId("room").setBiome("hall")));
        SubterrainPlanner planner = mock(SubterrainPlanner.class);
        when(planner.sample(anyInt(), anyInt(), anyInt())).thenReturn(SubterrainCell.OUTSIDE);
        when(planner.sample(0, 56, 0)).thenReturn(new SubterrainCell(SubterrainCell.Kind.AIR, "",
                new SubterrainRoom("room", "hall", null, 0, 56, 0, 0, 56, 0, 50, 60, 0D, 0,
                        false, false, SubterrainCell.Kind.AIR)));
        when(complex.getSubterrainPlanner()).thenReturn(planner);

        BiomeSearchFilter filter = BiomeSearchFilter.of(engine, Set.of("hall"));

        assertTrue(filter.column(0, 0).admits(120));
        assertFalse(filter.column(0, 0).admits(SURFACE_Y));
        verify(engine, never()).getSubterrainBiome(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void carvingTargetIsAdmittedWhereTheCarvingResolvesIt() {
        stubRegions();
        when(complex.getRegionStream()).thenReturn(constantStream(region("host")));
        IrisDimensionCarvingEntry entry = new IrisDimensionCarvingEntry();
        entry.setId("deep");
        entry.setBiome("carved");
        stubBiome(biome("carved"));
        when(dimension.getCarving()).thenReturn(new KList<>(entry));

        BiomeSearchFilter filter = BiomeSearchFilter.of(engine, Set.of("carved"));
        BiomeSearchFilter unrelated = BiomeSearchFilter.of(engine, Set.of("elsewhere"));

        assertTrue(filter.column(0, 0).admits(CAVE_Y));
        assertFalse(filter.column(0, 0).admits(SURFACE_Y));
        assertFalse(unrelated.column(0, 0).admits(CAVE_Y));
    }

    private static IrisBiome biome(String key) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        return biome;
    }

    private static IrisRegion region(String key) {
        IrisRegion region = new IrisRegion();
        region.setLoadKey(key);
        return region;
    }

    private static KList<String> keys(String... values) {
        KList<String> list = new KList<>();
        for (String value : values) {
            list.add(value);
        }
        return list;
    }

    private static <T> ProceduralStream<T> constantStream(T value) {
        return ProceduralStream.of((x, z) -> value, Interpolated.of(a -> 0D, a -> value));
    }

    private static <T> ProceduralStream<T> positionalStream(BiFunction<Double, Double, T> function, T fallback) {
        return ProceduralStream.of(function::apply, Interpolated.of(a -> 0D, a -> fallback));
    }

    private void stubRegions(IrisRegion... regions) {
        KList<IrisRegion> list = new KList<>();
        for (IrisRegion region : regions) {
            list.add(region);
        }
        when(dimension.getAllRegions(engine)).thenReturn(list);
    }

    private void stubBiome(IrisBiome biome) {
        when(biomeLoader.load(biome.getLoadKey())).thenReturn(biome);
    }
}
