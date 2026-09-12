package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceCourseBuilder;
import art.arcane.iris.generation.hydrology.surface.SurfaceCourseResult;
import art.arcane.iris.generation.hydrology.surface.SurfaceTerminal;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalWidthAdaptationTest {
    private static final HydrologyPlannerSettings SETTINGS = HydrologyRegionalPlannerTest.settings(false, 1);

    @Test
    public void openValleysPreserveAccumulationAndAuthoredWidthVariation() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(80, 0D, "land");
        HydrologyGeometrySampler geometry = request -> request.field() == HydrologyGeometrySampler.Field.SURFACE_WIDTH
                ? 4 + Math.floorMod(request.x() / 256, 5) : request.minimum();
        HydrologyPlanner planner = planner(terrain, geometry);
        List<HydrologyPoint> guide = guide(terrain);
        int[] contributions = {1, 4, 16, 64, 256};
        HydrologyRegionalFlow flow = new HydrologyRegionalFlow(guide, contributions);
        Object context = context(planner, guide, contributions, false);
        int previous = 0;
        for (HydrologyPoint point : guide) {
            double fraction = 0.25D + 0.75D * flow.fraction(point.x(), point.z());
            double accumulated = 4D + 4D * fraction;
            int authored = 4 + Math.floorMod(point.x() / 256, 5);
            int expected = (int) StrictMath.round(accumulated * 0.75D + authored * 0.25D);
            int actual = width(planner, context, point.x());
            assertEquals(expected, actual);
            assertTrue(actual >= previous);
            previous = actual;
        }
        HydrologyPlanner narrow = planner(terrain, geometry(4));
        HydrologyPlanner wide = planner(terrain, geometry(8));
        assertTrue(width(wide, context(wide, guide, contributions, false), 512)
                > width(narrow, context(narrow, guide, contributions, false), 512));
    }

    @Test
    public void steeperAndConfinedValleysNarrowWithinTheAuthoredBounds() throws Exception {
        HydrologyTerrainSampler open = (x, z) -> HydrologyTerrainSample.openLand(80, 0D, "land");
        HydrologyTerrainSampler steep = (x, z) -> HydrologyTerrainSample.openLand(80 + Math.floorDiv(x, 4), 0D, "land");
        HydrologyTerrainSampler confined = (x, z) -> HydrologyTerrainSample.openLand(Math.abs(z) >= 4 ? 120 : 80, 0D, "land");
        for (HydrologyTerrainSampler terrain : List.of(open, steep, confined)) {
            HydrologyPlanner planner = planner(terrain, geometry(8));
            List<HydrologyPoint> guide = guide(terrain);
            Object context = context(planner, guide, uniform(guide), false);
            int width = width(planner, context, 512);
            assertEquals(terrain == open ? 8 : 4, width);
        }
    }

    @Test
    public void theCompleteGuideProfileIsReusedWithoutSamplingAtWidthQueries() throws Exception {
        AtomicBoolean sampled = new AtomicBoolean();
        HydrologyTerrainSampler terrain = (x, z) -> {
            assertTrue("Width lookup must reuse the source profile", !sampled.get());
            return HydrologyTerrainSample.openLand(x >= 512 && Math.abs(z) >= 4 ? 120 : 80, 0D, "land");
        };
        HydrologyPlanner planner = planner(terrain, geometry(8));
        List<HydrologyPoint> guide = guide(terrain);
        Object context = context(planner, guide, uniform(guide), false);
        sampled.set(true);
        int previous = width(planner, context, 0);
        for (int x = 8; x <= 1024; x += 8) {
            int width = width(planner, context, x);
            assertTrue(width >= 4 && width <= 8);
            assertTrue(Math.abs(width - previous) <= 1);
            assertEquals(width, width(planner, context, x));
            previous = width;
        }
        assertEquals(4, previous);
    }

    @Test
    public void narrowingDoesNotChangeDepthSampling() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(Math.abs(z) >= 4 ? 120 : 80, 0D, "land");
        HydrologyPlanner planner = planner(terrain, request -> request.maximum());
        List<HydrologyPoint> guide = guide(terrain);
        Object context = context(planner, guide, uniform(guide), false);

        assertEquals(4, width(planner, context, 512));
        assertEquals(3, regionalGeometry(planner, context, new HydrologyGeometrySampler.Request(
                HydrologyGeometrySampler.Field.SURFACE_DEPTH, "default", 512, 0, 1L, 2, 3)));
    }

    @Test
    public void inlandProfilesStillApplyTheBiomeWidthMultiplier() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> withWidthMultiplier(HydrologyTerrainSample.openLand(80, 0D, "land"), 1.25D);
        HydrologyPlanner planner = planner(terrain, geometry(8));
        List<HydrologyPoint> guide = guide(terrain);
        Object context = context(planner, guide, uniform(guide), false);
        HydrologyGeometrySampler geometry = request -> regionalGeometry(planner, context, request);
        SurfaceCourseResult built = new SurfaceCourseBuilder(SETTINGS.surface(), terrain, geometry, SETTINGS.seaLevel())
                .build(71L, 91L, "default", guide, SurfaceTerminal.SINKHOLE, 40, 512);

        assertTrue(built.accepted());
        assertEquals(10D, storedWidth(built.segments(), 512), 0D);
    }

    @Test
    public void coastalPublicationKeepsFractionalBiomeWidthsAndCeilsItsEnvelope() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> x < 0 || x > 1024 ? HydrologyTerrainSample.ocean(60, "ocean")
                : withWidthMultiplier(HydrologyTerrainSample.openLand(63, 0D, "land"), x < 256 ? 0.1D : x < 768 ? 1.1D : 3D);
        HydrologyPlanner planner = planner(terrain, geometry(8));
        List<HydrologyPoint> guide = guide(terrain);
        Object context = context(planner, guide, uniform(guide), true);
        Method attempt = HydrologyRegionalPlanner.class.getDeclaredMethod("attempt", context.getClass(), HydrologyRegionalRoute.Refinement.class);
        attempt.setAccessible(true);
        HydrologyRegionalRoute.Attempt accepted = (HydrologyRegionalRoute.Attempt) attempt.invoke(planner.regional, context,
                new HydrologyRegionalRoute.Refinement(guide, null, null, 0, null));

        assertTrue(accepted.toString(), accepted.accepted());
        RiverCourse course = accepted.network().courses().getFirst();
        assertTrue(course.hydraulicallyNonRising());
        assertEquals(4D, storedWidth(course.segments(), 0), 0D);
        assertEquals(8.8D, storedWidth(course.segments(), 512), 0.000000001D);
        assertEquals(16D, storedWidth(course.segments(), 1024), 0D);
        for (HydraulicSegment segment : course.segments()) {
            assertEquals((int) StrictMath.ceil(segment.channelProfile().maximumWidth()), segment.width());
            assertEquals(2, segment.depth());
        }
    }

    private static double storedWidth(List<HydraulicSegment> segments, int x) {
        for (HydraulicSegment segment : segments) {
            for (int index = 0; index < segment.centerline().size(); index++) {
                if (segment.centerline().get(index).x() == x) {
                    return segment.channelProfile().widthAt(index);
                }
            }
        }
        throw new AssertionError("Missing station " + x);
    }

    private static int width(HydrologyPlanner planner, Object context, int x) {
        return regionalGeometry(planner, context, new HydrologyGeometrySampler.Request(
                HydrologyGeometrySampler.Field.SURFACE_WIDTH, "default", x, 0, 1L, 4, 8));
    }

    private static int regionalGeometry(HydrologyPlanner planner, Object context, HydrologyGeometrySampler.Request request) {
        try {
            Method geometry = HydrologyRegionalPlanner.class.getDeclaredMethod("regionalGeometry", HydrologyGeometrySampler.Request.class, context.getClass());
            geometry.setAccessible(true);
            return (int) geometry.invoke(planner.regional, request, context);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Object context(HydrologyPlanner planner, List<HydrologyPoint> guide, int[] contributions, boolean coastal) throws Exception {
        Class<?> type = Class.forName(HydrologyRegionalPlanner.class.getName() + "$BuildContext");
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        int seaLevel = planner.settings.seaLevel();
        HydrologyPoint source = guide.getFirst();
        HydrologyPoint destination = guide.getLast();
        RiverOutlet outlet = new RiverOutlet(2L, HydrologyFeatureType.MOUTH, 8L, destination,
                new HydrologyPoint(destination.x() + 1, seaLevel, destination.z()), seaLevel, true);
        OutletCandidate origin = coastal ? new OutletCandidate(0, 0, new RiverOutlet(3L, HydrologyFeatureType.MOUTH, 0L, source,
                new HydrologyPoint(source.x() - 1, seaLevel, source.z()), seaLevel, true)) : null;
        HydrologyTerrainSampler terrain = planner.sampler;
        return constructor.newInstance(new HydrologyGridNode(0, 0, 0, source.x(), source.z(), 100L, terrain.sample(source.x(), source.z())),
                origin, 123L, outlet, "default", new HydrologyRegionalFlow(guide, contributions),
                HydrologyRegionalMorphology.sample(guide, terrain, planner.settings));
    }

    private static List<HydrologyPoint> guide(HydrologyTerrainSampler terrain) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (int x = 0; x <= 1024; x += 256) {
            points.add(new HydrologyPoint(x, terrain.sample(x, 0).naturalHeight(), 0));
        }
        return List.copyOf(points);
    }

    private static int[] uniform(List<HydrologyPoint> guide) {
        int[] contributions = new int[guide.size()];
        Arrays.fill(contributions, 1);
        return contributions;
    }

    private static HydrologyGeometrySampler geometry(int width) {
        return request -> request.field() == HydrologyGeometrySampler.Field.SURFACE_WIDTH ? width : request.minimum();
    }

    private static HydrologyPlanner planner(HydrologyTerrainSampler terrain, HydrologyGeometrySampler geometry) {
        return new HydrologyPlanner(71L, SETTINGS, terrain, geometry, -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(terrain, 63, -4096, 4096));
    }

    private static HydrologyTerrainSample withWidthMultiplier(HydrologyTerrainSample source, double multiplier) {
        return new HydrologyTerrainSample(source.naturalHeight(), source.slope(), source.ocean(), source.caveAvailable(),
                source.caveFloorY(), source.caveFluidY(), source.transitAllowed(), source.outletAllowed(), source.surfaceSourceAllowed(),
                source.surfaceSourceRequired(), source.undergroundSourceAllowed(), source.undergroundSourceRequired(), source.routingCost(),
                source.surfaceSourceWeight(), source.undergroundSourceWeight(), multiplier, source.depthMultiplier(), source.incisionMultiplier(),
                source.routingMultiplier(), source.bankMultiplier(), source.parentBiomeKey(), source.surfaceBiomeKey(), source.mouthBiomeKey(),
                source.shoreBiomeKey(), source.bankBiomeKey(), source.floodedCaveBiomeKey(), source.preferredProfileKeys(), source.surfacePoolKeys(),
                source.shoreBiomeWidth(), source.confinesKey(), source.shoreWidth(), source.erosion(), source.surfacePolicy());
    }
}
