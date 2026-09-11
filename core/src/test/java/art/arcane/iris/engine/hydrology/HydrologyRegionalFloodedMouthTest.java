package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.policy.SurfaceRiverPolicy;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class HydrologyRegionalFloodedMouthTest {
    @Test
    public void aLongDryPrefixEndsBeforeItsConnectedFloodedLandTail() throws Exception {
        HydrologyTerrainSampler terrain = coast(2201, 2206);
        HydrologyRegionalRoute.Refinement result = validate(terrain,
                List.of(new HydrologyPoint(0, 65, 0), new HydrologyPoint(8192, 65, 0)));

        assertNull(result.toString(), result.rejection());
        assertEquals(new HydrologyPoint(2200, 63, 0), result.oceanEntry().landward());
        assertEquals(new HydrologyPoint(2206, 63, 0), result.oceanEntry().receiving());
        assertEquals(new HydrologyPoint(2200, 65, 0), result.points().getLast());
    }

    @Test
    public void floodedTailDoesNotCountTowardTheRegionalMinimumLength() throws Exception {
        assertNotNull(validate(coast(2048, 2200), straight()).rejection());
        assertNull(validate(coast(2049, 2200), straight()).rejection());
    }

    @Test
    public void aDrySillBeforeTheReceivingOceanRejectsTheTail() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> x == 2204 ? HydrologyTerrainSample.openLand(63, 0D, "sill")
                : coast(2201, 2206).sample(x, z);
        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED,
                validate(terrain, straight()).rejection());
    }

    @Test
    public void unavailableTailTerrainCannotProveTheSeaConnection() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> x == 2204 ? null : coast(2201, 2206).sample(x, z);
        assertNotNull(validate(terrain, straight()).rejection());
    }

    @Test
    public void aFloodedTailWithoutKnownSeaCannotBecomeAMouth() throws Exception {
        assertEquals(HydrologyCandidateRejection.SURFACE_MOUTH_DISCONNECTED,
                validate(coast(2201, 4000), straight()).rejection());
    }

    @Test
    public void aDifferentFluidProfileInterruptsTheReceivingPath() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> x == 2204 ? terrain(false, 60, "lava", null)
                : coast(2201, 2206).sample(x, z);
        assertEquals(HydrologyCandidateRejection.POLICY_EXCLUDED, validate(terrain, straight()).rejection());
    }

    @Test
    public void aConfinedReceivingTailCannotCrossItsBoundary() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> x >= 2204 ? terrain(x >= 2206, 60, "default", null)
                : terrain(false, x >= 2201 ? 60 : 65, "default", "coast");
        assertEquals(HydrologyCandidateRejection.CONFINED_NO_OUTLET, validate(terrain, straight()).rejection());
    }

    @Test
    public void diagonalWetSamplesRequireAnActualCardinalWaterConnection() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> x >= 2201 && z == x - 2200
                ? terrain(x >= 2203, 60, "default", null) : HydrologyTerrainSample.openLand(65, 0D, "land");
        assertEquals(HydrologyCandidateRejection.SURFACE_MOUTH_DISCONNECTED, validate(terrain,
                List.of(new HydrologyPoint(0, 65, 0), new HydrologyPoint(2200, 65, 0),
                        new HydrologyPoint(2203, 60, 3))).rejection());
    }

    @Test
    public void aCurvedFloodedTailCannotCertifyADryPersistedMouthChord() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> {
            boolean wet = z == 0 && x >= 2201 && x <= 2204 || x == 2204 && z >= 0 && z <= 4
                    || z == 4 && x >= 2204;
            return wet ? terrain(x >= 2208, 60, "default", null) : HydrologyTerrainSample.openLand(65, 0D, "land");
        };
        assertEquals(HydrologyCandidateRejection.SURFACE_MOUTH_DISCONNECTED, validate(terrain,
                List.of(new HydrologyPoint(0, 65, 0), new HydrologyPoint(2204, 60, 0),
                        new HydrologyPoint(2204, 60, 4), new HydrologyPoint(2208, 60, 4))).rejection());
    }

    private static List<HydrologyPoint> straight() {
        return List.of(new HydrologyPoint(0, 65, 0), new HydrologyPoint(3000, 65, 0));
    }

    private static HydrologyTerrainSampler coast(int firstWet, int firstOcean) {
        return (x, z) -> x >= firstOcean ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(x >= firstWet ? 60 : 65, 0D, "land");
    }

    private static HydrologyRegionalRoute.Refinement validate(HydrologyTerrainSampler terrain,
                                                               List<HydrologyPoint> points) throws Exception {
        HydrologyPlannerSettings base = HydrologyRegionalPlannerTest.settings(false, 1);
        HydrologyPlannerSettings.Routing routing = base.routing();
        HydrologyPlannerSettings.Regional regional = routing.regional();
        HydrologyPlannerSettings settings = new HydrologyPlannerSettings(base.seaLevel(),
                new HydrologyPlannerSettings.Routing(routing.tileSize(), routing.sampleSpacing(), routing.maximumRouteNodes(),
                        4096, routing.minimumSurfaceCourseLength(), routing.minimumUndergroundCourseLength(),
                        routing.valleyPreference(), routing.uphillPenalty(), routing.slopePenalty(), routing.confluenceAttraction(),
                        routing.lengthPreference(), routing.tributaries(), new HydrologyPlannerSettings.Regional(true,
                        regional.sampleSpacing(), 2048, regional.maximumTrunks(), regional.maximumCachedBasins(),
                        regional.maximumCachedStations(), false, 0D, regional.maximumCoastalIncision())),
                base.surface(), base.hydraulics(), base.underground(), base.outlets(), base.geometry(), base.deepFluids(),
                base.surfacePools(), base.widestShoreBiomeWidth(), base.seaCaves(), base.surfacePolicyBounds());
        HydrologyRegionalRoute route = new HydrologyRegionalRoute(new HydrologyPlanner(1L, settings, terrain));
        Method validate = HydrologyRegionalRoute.class.getDeclaredMethod("validate", List.class, String.class,
                boolean.class, HydrologyTerrainSampler.class);
        validate.setAccessible(true);
        return (HydrologyRegionalRoute.Refinement) validate.invoke(route, points, "default", false, terrain);
    }

    private static HydrologyTerrainSample terrain(boolean ocean, int height, String profile, String confines) {
        return new HydrologyTerrainSample(height, 0D, ocean, false, height - 32, height - 30,
                !ocean, true, !ocean, false, false, false,
                0D, 1D, 1D, 1D, 1D, 1D, 1D, 1D,
                "land", "land", "land", "land", "land", "land", List.of(profile), List.of(),
                Double.NaN, confines, Double.NaN, true, SurfaceRiverPolicy.INHERIT);
    }
}
