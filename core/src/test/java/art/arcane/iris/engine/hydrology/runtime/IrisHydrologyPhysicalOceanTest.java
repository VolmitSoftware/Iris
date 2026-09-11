package art.arcane.iris.engine.hydrology.runtime;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.hydrology.HydraulicChannelProfile;
import art.arcane.iris.engine.hydrology.HydraulicSegment;
import art.arcane.iris.engine.hydrology.HydrologyColumnLayer;
import art.arcane.iris.engine.hydrology.HydrologyFeatureType;
import art.arcane.iris.engine.hydrology.HydrologyGeometrySampler;
import art.arcane.iris.engine.hydrology.HydrologyPoint;
import art.arcane.iris.engine.hydrology.HydrologyRoutingTerrainSampler;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSample;
import art.arcane.iris.engine.hydrology.RiverCourse;
import art.arcane.iris.engine.hydrology.RiverCourseType;
import art.arcane.iris.engine.hydrology.surface.SurfaceCourseBuilder;
import art.arcane.iris.engine.hydrology.surface.SurfaceCourseResult;
import art.arcane.iris.engine.hydrology.surface.SurfaceFootprint;
import art.arcane.iris.engine.hydrology.surface.SurfaceFootprintCompiler;
import art.arcane.iris.engine.hydrology.surface.SurfaceLayerColumn;
import art.arcane.iris.engine.hydrology.surface.SurfaceTerminal;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.IrisRiverProfile;
import art.arcane.volmlib.util.collection.KList;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisHydrologyPhysicalOceanTest {
    private static final int SEA_LEVEL = 306;
    private static IrisSettings previousSettings;

    @BeforeClass
    public static void useDefaultSettings() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
    }

    @AfterClass
    public static void restoreSettings() {
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void detailedAndFastQueriesAgreeOnTheActualOceanWaterlineInEitherOrder() throws Exception {
        for (boolean classifyFirst : new boolean[]{true, false}) {
            Map<Integer, Integer> sampledHeights = new HashMap<>();
            try (IrisHydrologyRuntime runtime = runtime((x, z) -> {
                sampledHeights.merge(x, 1, Integer::sum);
                return switch (x) {
                    case 0 -> 308D;
                    case 1 -> 306D;
                    case 2 -> 305D;
                    default -> 305.6D;
                };
            }, (x, z) -> true)) {
                IrisHydrologyRoutingTerrainSampler sampler = sampler(runtime);
                for (int x = 0; x < 4; x++) {
                    HydrologyRoutingTerrainSampler.NaturalClassification expected = x == 2
                            ? HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN
                            : HydrologyRoutingTerrainSampler.NaturalClassification.LAND;
                    if (classifyFirst) {
                        assertEquals(expected, sampler.classifyNatural(x, 0));
                    }
                    HydrologyTerrainSample terrain = sampler.sampleBasisWithoutSlope(x, 0);
                    assertEquals(x == 2, terrain.ocean());
                    assertEquals(x != 2, terrain.transitAllowed());
                    assertEquals(expected, sampler.classifyNatural(x, 0));
                    assertEquals(1, sampledHeights.get(x).intValue());
                }
            }
        }
    }

    @Test
    public void aMouthCrossesDryOceanIntentShoreAndReachesOnlyTheSubmergedReservoir() throws Exception {
        try (IrisHydrologyRuntime runtime = runtime((x, z) -> x >= 480 ? 305D
                : x == 479 ? 306D : x >= 400 ? 308D : 310D, (x, z) -> x >= 400)) {
            IrisHydrologyRoutingTerrainSampler sampler = sampler(runtime);
            assertEquals(HydrologyRoutingTerrainSampler.NaturalClassification.LAND, sampler.classifyNatural(400, 0));
            assertEquals(HydrologyRoutingTerrainSampler.NaturalClassification.LAND, sampler.classifyNatural(479, 0));
            assertEquals(HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN, sampler.classifyNatural(480, 0));
            HydrologyGeometrySampler geometry = request -> switch (request.field()) {
                case SURFACE_WIDTH -> 6;
                case SURFACE_DEPTH -> 3;
                default -> request.minimum();
            };
            SurfaceCourseResult result = new SurfaceCourseBuilder(runtime.settings().surface(),
                    sampler::sampleBasisWithoutSlope, geometry, SEA_LEVEL).build(17L, 42L, "water",
                    List.of(new HydrologyPoint(0, 310, 0), new HydrologyPoint(480, SEA_LEVEL, 0)),
                    SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL, 384);
            assertNull(result.toString(), result.rejection());
            ArrayList<HydraulicSegment> segments = new ArrayList<>(result.segments());
            segments.add(new HydraulicSegment(99L, 42L, HydrologyFeatureType.MOUTH, SEA_LEVEL, SEA_LEVEL,
                    result.lastWidth(), result.lastDepth(), false, false,
                    List.of(result.pathEnd(), new HydrologyPoint(480, SEA_LEVEL, 0)),
                    HydraulicChannelProfile.uniform(result.lastWidth(), result.lastDepth())));
            RiverCourse course = new RiverCourse(42L, RiverCourseType.SURFACE, OptionalLong.of(1L),
                    OptionalLong.of(2L), "water", 1, List.of(), segments);
            SurfaceFootprint footprint = new SurfaceFootprintCompiler(runtime.settings(),
                    sampler::sampleBasisWithoutSlope, geometry).compile(course);
            assertNull(footprint.toString(), footprint.rejection());
            Map<Integer, SurfaceLayerColumn> center = new HashMap<>();
            for (SurfaceLayerColumn column : footprint.columns()) {
                if (column.z() == 0) {
                    center.put(column.x(), column);
                }
                if (column.terrain().ocean()) {
                    assertFalse(column.layer().terrainOwned());
                    assertFalse(column.layer().fluidOwned());
                }
            }
            for (int x = 400; x < 480; x++) {
                SurfaceLayerColumn column = center.get(x);
                assertNotNull("Dry ocean-intent channel at " + x, column);
                HydrologyColumnLayer layer = column.layer();
                assertTrue("Owned water at " + x, layer.channel() && layer.fluidOwned());
                assertTrue(layer.bedY() < layer.fluidHeadY());
            }
            assertEquals(SEA_LEVEL, center.get(479).layer().fluidHeadY());
            assertTrue(sampler.sampleBasisWithoutSlope(480, 0).naturalHeight() < SEA_LEVEL);
        }
    }

    @SuppressWarnings("unchecked")
    private static IrisHydrologyRuntime runtime(IrisHydrologyNaturalHeightProvider heights,
                                                IrisHydrologyNaturalOceanClassifier oceanIntent) {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisBiome> loader = mock(ResourceLoader.class);
        when(data.getBiomeLoader()).thenReturn(loader);
        when(loader.getPossibleKeys()).thenReturn(new String[0]);
        when(loader.loadAll(any(String[].class))).thenReturn(new KList<>());
        IrisDimension dimension = new IrisDimension().setRegions(new KList<>());
        dimension.setFluidHeight(SEA_LEVEL + dimension.getMinHeight());
        dimension.setLoadKey("coast");
        dimension.setLoader(data);
        dimension.getHydrology().getRivers().setProfiles(new KList<>(new IrisRiverProfile().setId("water")));
        IrisRegion region = new IrisRegion();
        region.setLoadKey("region");
        IrisBiome shore = new IrisBiome();
        shore.setLoadKey("shore");
        return new IrisHydrologyRuntime(new IrisHydrologyRuntimeContext(17L, 768, dimension, data,
                (x, z, height) -> new IrisHydrologyNaturalSample(height, oceanIntent.isOcean(x, z), shore, region),
                heights, (x, z) -> "coast", oceanIntent, footprint -> null, () -> false));
    }

    private static IrisHydrologyRoutingTerrainSampler sampler(IrisHydrologyRuntime runtime) throws Exception {
        Field field = IrisHydrologyRuntime.class.getDeclaredField("routingTerrainSampler");
        field.setAccessible(true);
        return (IrisHydrologyRoutingTerrainSampler) field.get(runtime);
    }
}
