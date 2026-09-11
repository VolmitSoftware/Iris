package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.policy.SurfaceRiverPolicy;
import art.arcane.iris.engine.hydrology.surface.SurfaceCourseBuilder;
import art.arcane.iris.engine.hydrology.surface.SurfaceCourseResult;
import art.arcane.iris.engine.hydrology.surface.SurfaceTerminal;
import org.junit.Test;

import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalMouthRefinementTest {
    @Test
    public void certifiedFloodedLandRetainsTheInletBudgetAtTheActualDryShore() throws Exception {
        HydrologyPlannerSettings settings = HydrologyRegionalPlannerTest.settings(false, 1);
        HydrologyTerrainSampler terrain = (x, z) -> x >= 880 ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(x >= 800 ? 60 : x >= 768 ? 80 : 65, 0D, "land");
        HydrologyPoint landward = new HydrologyPoint(799, 63, 0);
        RiverOutlet outlet = new RiverOutlet(1L, HydrologyFeatureType.MOUTH, 1L, landward,
                new HydrologyPoint(880, 63, 0), 63, true);
        HydrologyTerrainSampler receiver = HydrologyOceanReceiver.forOutlet(settings, terrain, outlet);
        HydrologyPlanner planner = new HydrologyPlanner(1L, settings, terrain);
        HydrologyRegionalRoute route = new HydrologyRegionalRoute(planner);
        List<HydrologyPoint> path = List.of(new HydrologyPoint(0, 65, 0), new HydrologyPoint(767, 65, 0),
                new HydrologyPoint(768, 80, 0), landward);
        Method validate = HydrologyRegionalRoute.class.getDeclaredMethod("validate", List.class, String.class,
                boolean.class, HydrologyTerrainSampler.class);
        validate.setAccessible(true);
        HydrologyRegionalRoute.Refinement accepted = (HydrologyRegionalRoute.Refinement) validate.invoke(
                route, path, "default", false, receiver);
        HydrologyRegionalRoute.Refinement unproved = (HydrologyRegionalRoute.Refinement) validate.invoke(
                route, path, "default", false, terrain);

        assertNull(accepted.toString(), accepted.rejection());
        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, unproved.rejection());
        assertTrue(new HydrologyRegionalTerrainRefiner(planner).receivingTerminal(landward, "default", false, receiver));
        assertFalse(terrain.sample(800, 0).ocean());
        assertTrue(receiver.receivingWater(800, 0, 63));
    }

    @Test
    public void downstreamOceanEntryReplacesTheLaterCoastalSample() {
        HydrologyTerrainSample ocean = terrain(true, 60, "default", null);
        HydrologyRegionalRoute.Refinement result = refine(new Scenario(768, 1024, 512), ocean, null, false);

        assertNull(result.toString(), result.rejection());
        assertNotNull(result.oceanEntry());
        assertEquals(new HydrologyPoint(767, 63, 0), result.oceanEntry().landward());
        assertEquals(new HydrologyPoint(768, 63, 0), result.oceanEntry().receiving());
        assertEquals(767, result.points().getLast().x());
        assertTrue(result.points().stream().allMatch(point -> point.x() < 768));
        assertEquals(result, refine(new Scenario(768, 1024, 512), ocean, null, false));
    }

    @Test
    public void firstReceivingSeaCanBeFarFromTheOriginalCoarseOutlet() {
        HydrologyTerrainSample ocean = terrain(true, 60, "default", null);
        HydrologyRegionalRoute.Refinement accepted = refine(new Scenario(2560, 4096, 2048), ocean, null, false);
        HydrologyRegionalRoute.Refinement tooEarly = refine(new Scenario(1792, 4096, 2048), ocean, null, false);
        HydrologyRegionalRoute.Refinement oneBlockShort = refine(new Scenario(2048, 4096, 2048), ocean, null, false);

        assertNull(accepted.toString(), accepted.rejection());
        assertEquals(new HydrologyPoint(2560, 63, 0), accepted.oceanEntry().receiving());
        assertTrue(4096 - accepted.oceanEntry().receiving().x() > 512);
        assertNotNull(tooEarly.rejection());
        assertNull(tooEarly.oceanEntry());
        assertNotNull(oneBlockShort.rejection());
        assertNull(oneBlockShort.oceanEntry());
    }

    @Test
    public void anEarlierOceanBarrierCannotTruncateARegionalCourse() {
        HydrologyRegionalRoute.Refinement result = refine(new Scenario(400, 1024, 512), terrain(true, 60, "default", null), null, false);

        assertNotNull(result.rejection());
        assertNull(result.oceanEntry());
    }

    @Test
    public void dryOceanColumnsCannotServeAsReceivingWater() {
        for (int height : new int[]{63, 64}) {
            HydrologyRegionalRoute.Refinement result = refine(new Scenario(768, 1024, 512), terrain(true, height, "default", null), null, false);
            assertNotNull(result.rejection());
            assertNull(result.oceanEntry());
        }
    }

    @Test
    public void receivingWaterMustMatchTheFluidAndConfinedArea() {
        HydrologyRegionalRoute.Refinement mismatch = refine(new Scenario(768, 1024, 512), terrain(true, 60, "lava", null), null, false);
        HydrologyRegionalRoute.Refinement confined = refine(new Scenario(768, 1024, 512), terrain(true, 60, "default", "ocean"), "land", false);

        assertNotNull(mismatch.rejection());
        assertNull(mismatch.oceanEntry());
        assertNotNull(confined.rejection());
        assertNull(confined.oceanEntry());
    }

    @Test
    public void coastalEntryMustAlsoPermitDrainageBackIntoTheChannel() {
        HydrologyTerrainSample ocean = terrain(true, 60, "default", "ocean");
        assertNull(refine(new Scenario(768, 1024, 512), ocean, null, false).rejection());
        HydrologyRegionalRoute.Refinement coastal = refine(new Scenario(768, 1024, 512), ocean, null, true);

        assertNotNull(coastal.rejection());
        assertNull(coastal.oceanEntry());
    }

    @Test
    public void bendsAfterTheActualMouthDoNotRejectTheExposedPrefix() throws Exception {
        HydrologyPlannerSettings settings = HydrologyRegionalPlannerTest.settings(false, 1);
        HydrologyPlanner planner = new HydrologyPlanner(15L, settings,
                (x, z) -> x >= 768 ? HydrologyTerrainSample.ocean(60, "ocean")
                        : HydrologyTerrainSample.openLand(65, 0D, "land"));
        HydrologyRegionalRoute route = new HydrologyRegionalRoute(planner);
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (int x = 0; x <= 800; x += 4) {
            points.add(new HydrologyPoint(x, 65, 0));
        }
        for (int z = 4; z <= 128; z += 4) {
            points.add(new HydrologyPoint(800, 65, z));
        }
        Method validate = HydrologyRegionalRoute.class.getDeclaredMethod("validate", List.class, String.class, boolean.class, HydrologyTerrainSampler.class);
        validate.setAccessible(true);
        HydrologyRegionalRoute.Refinement result = (HydrologyRegionalRoute.Refinement) validate.invoke(route, points, "default", false, planner.sampler);

        assertNull(result.toString(), result.rejection());
        assertEquals(new HydrologyPoint(768, 63, 0), result.oceanEntry().receiving());
        Method round = HydrologyRegionalRoute.class.getDeclaredMethod("roundBends", List.class, double.class);
        Method resample = HydrologyRegionalRoute.class.getDeclaredMethod("resample", List.class);
        Method rounded = HydrologyRegionalRoute.class.getDeclaredMethod("validateRounded", List.class, String.class, boolean.class, HydrologyTerrainSampler.class);
        round.setAccessible(true);
        resample.setAccessible(true);
        rounded.setAccessible(true);
        HydrologyRegionalRoute.Refinement continuous = (HydrologyRegionalRoute.Refinement) rounded.invoke(route,
                resample.invoke(route, round.invoke(route, points, 8D)), "default", false, planner.sampler);
        assertNull(continuous.toString(), continuous.rejection());
        assertEquals(new HydrologyPoint(768, 63, 0), continuous.oceanEntry().receiving());
    }

    @Test
    public void anUnusedSegmentTailCannotExceedTheAcceptedMouthLengthBudget() throws Exception {
        HydrologyPlannerSettings settings = HydrologyRegionalPlannerTest.settings(false, 1);
        HydrologyPlanner planner = new HydrologyPlanner(15L, settings,
                (x, z) -> x >= 1536 ? HydrologyTerrainSample.ocean(60, "ocean")
                        : HydrologyTerrainSample.openLand(65, 0D, "land"));
        HydrologyRegionalRoute route = new HydrologyRegionalRoute(planner);
        Method validate = HydrologyRegionalRoute.class.getDeclaredMethod("validate", List.class, String.class, boolean.class, HydrologyTerrainSampler.class);
        validate.setAccessible(true);
        HydrologyRegionalRoute.Refinement result = (HydrologyRegionalRoute.Refinement) validate.invoke(route,
                List.of(new HydrologyPoint(0, 65, 0), new HydrologyPoint(4096, 65, 0)), "default", false, planner.sampler);

        assertNull(result.toString(), result.rejection());
        assertEquals(new HydrologyPoint(1536, 63, 0), result.oceanEntry().receiving());
        HydrologyPlanner inland = new HydrologyPlanner(15L, settings,
                (x, z) -> HydrologyTerrainSample.openLand(65, 0D, "land"));
        HydrologyRegionalRoute.Refinement overlong = (HydrologyRegionalRoute.Refinement) validate.invoke(new HydrologyRegionalRoute(inland),
                List.of(new HydrologyPoint(0, 65, 0), new HydrologyPoint(4096, 65, 0)), "default", false, inland.sampler);
        assertEquals(HydrologyCandidateRejection.ROUTE_LIMIT, overlong.rejection());
    }

    @Test
    public void theVerifiedMouthRetainsItsAuthoredInletIncisionBudget() throws Exception {
        HydrologyPlannerSettings settings = HydrologyRegionalPlannerTest.settings(false, 1);
        HydrologyTerrainSampler terrain = (x, z) -> x >= 800 ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(x >= 768 ? 80 : 65, 0D, "land");
        List<HydrologyPoint> path = List.of(new HydrologyPoint(0, 65, 0), new HydrologyPoint(767, 65, 0),
                new HydrologyPoint(768, 80, 0), new HydrologyPoint(799, 80, 0), new HydrologyPoint(800, 63, 0));
        HydrologyGeometrySampler geometry = request -> switch (request.field()) {
            case SURFACE_WIDTH -> 6;
            case SURFACE_DEPTH -> 2;
            default -> request.minimum();
        };
        SurfaceCourseResult course = new SurfaceCourseBuilder(settings.surface(), terrain, geometry, 63).build(
                1L, 2L, "default", path, SurfaceTerminal.OCEAN_MOUTH, 63, 512);
        HydrologyRegionalRoute route = new HydrologyRegionalRoute(new HydrologyPlanner(1L, settings, terrain));
        Method validate = HydrologyRegionalRoute.class.getDeclaredMethod("validate", List.class, String.class, boolean.class, HydrologyTerrainSampler.class);
        validate.setAccessible(true);
        HydrologyRegionalRoute.Refinement result = (HydrologyRegionalRoute.Refinement) validate.invoke(route, path, "default", false, terrain);

        assertNull(course.rejection());
        assertNull(result.toString(), result.rejection());
        assertEquals(new HydrologyPoint(800, 63, 0), result.oceanEntry().receiving());
        HydrologyTerrainSampler inlandRidge = (x, z) -> x >= 800 ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(x >= 600 && x <= 631 ? 80 : 65, 0D, "land");
        HydrologyRegionalRoute.Refinement rejected = (HydrologyRegionalRoute.Refinement) validate.invoke(
                new HydrologyRegionalRoute(new HydrologyPlanner(1L, settings, inlandRidge)), path, "default", false, inlandRidge);
        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, rejected.rejection());
    }

    private static HydrologyRegionalRoute.Refinement refine(Scenario scenario, HydrologyTerrainSample ocean,
                                                            String confines, boolean coastal) {
        HydrologyPlannerSettings settings = HydrologyRegionalPlannerTest.settings(false, 1);
        HydrologyPlannerSettings.Geometry geometry = settings.geometry();
        HydrologyPlannerSettings.Routing routing = settings.routing();
        HydrologyPlannerSettings.Regional regional = routing.regional();
        HydrologyPlannerSettings.Routing route = new HydrologyPlannerSettings.Routing(routing.tileSize(), routing.sampleSpacing(),
                routing.maximumRouteNodes(), Math.max(routing.maximumRouteLength(), scenario.destination()),
                routing.minimumSurfaceCourseLength(), routing.minimumUndergroundCourseLength(), routing.valleyPreference(),
                routing.uphillPenalty(), routing.slopePenalty(), routing.confluenceAttraction(), routing.lengthPreference(),
                routing.tributaries(), new HydrologyPlannerSettings.Regional(regional.enabled(), regional.sampleSpacing(),
                scenario.minimumLength(), regional.maximumTrunks(), regional.maximumCachedBasins(), regional.maximumCachedStations(),
                regional.coastalChannels(), regional.coastalChannelChance(), regional.maximumCoastalIncision()));
        HydrologyPlannerSettings precise = new HydrologyPlannerSettings(settings.seaLevel(), route,
                settings.surface(), settings.hydraulics(), settings.underground(), settings.outlets(),
                new HydrologyPlannerSettings.Geometry(new HydrologyPlannerSettings.Meanders(64, 12, 0D, 0D, 0D, 0, 20D),
                        geometry.surface(), geometry.underground(), geometry.grottos(), geometry.drops()),
                settings.deepFluids(), settings.surfacePools(), settings.widestShoreBiomeWidth(), settings.seaCaves(),
                settings.surfacePolicyBounds());
        HydrologyTerrainSample land = terrain(false, 65, "default", confines);
        HydrologyPlanner planner = new HydrologyPlanner(15L, precise,
                (x, z) -> x >= scenario.oceanStart() && x <= scenario.oceanStart() + 128 ? ocean : land);
        return new HydrologyRegionalRoute(planner).select(
                List.of(new HydrologyPoint(0, 65, 0), new HydrologyPoint(scenario.destination(), 65, 0)), "default", coastal, planner.sampler, candidate -> new HydrologyRegionalRoute.Attempt(candidate, HydrologyRegionalNetwork.EMPTY, null)).refinement();
    }

    private record Scenario(int oceanStart, int destination, int minimumLength) {
    }

    private static HydrologyTerrainSample terrain(boolean ocean, int height, String profile, String confines) {
        return new HydrologyTerrainSample(height, 0D, ocean, false, height - 32, height - 30,
                !ocean, true, !ocean, false, false, false,
                0D, 1D, 1D, 1D, 1D, 1D, 1D, 1D,
                "land", "land", "land", "land", "land", "land", List.of(profile), List.of(),
                Double.NaN, confines, Double.NaN, true, SurfaceRiverPolicy.INHERIT);
    }
}
