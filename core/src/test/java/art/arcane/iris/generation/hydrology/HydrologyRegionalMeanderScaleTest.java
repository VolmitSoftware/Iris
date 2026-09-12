package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceCenterline;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalMeanderScaleTest {
    @Test
    public void longOpenValleysRetainRepeatedBendsAtTheAuthoredScale() {
        HydrologyPlannerSettings settings = settings(0.34D, 0.42D);
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(76, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(1337L, settings, terrain);
        List<HydrologyPoint> guide = guide(76);
        HydrologyRegionalRoute route = new HydrologyRegionalRoute(planner);
        HydrologyRegionalRoute.Refinement result = route.select(guide, "default", false, terrain, candidate -> new HydrologyRegionalRoute.Attempt(candidate, HydrologyRegionalNetwork.EMPTY, null)).refinement();

        assertValid(result, guide, planner);
        int minimumZ = Integer.MAX_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        int crossings = 0;
        int previousSide = 0;
        for (HydrologyPoint point : result.points()) {
            if (point.x() < 1024 || point.x() > 5120) {
                continue;
            }
            minimumZ = Math.min(minimumZ, point.z());
            maximumZ = Math.max(maximumZ, point.z());
            int side = point.z() > 780 ? 1 : point.z() < 756 ? -1 : 0;
            if (side != 0) {
                if (previousSide != 0 && previousSide != side) {
                    crossings++;
                }
                previousSide = side;
            }
        }
        assertTrue("minimum Z=" + minimumZ, minimumZ <= 736);
        assertTrue("maximum Z=" + maximumZ, maximumZ >= 800);
        assertTrue("bend crossings=" + crossings, crossings >= 3);
        assertEquals(result, route.select(guide, "default", false, terrain, candidate -> new HydrologyRegionalRoute.Attempt(candidate, HydrologyRegionalNetwork.EMPTY, null)).refinement());
        assertEquals(result, new HydrologyRegionalRoute(planner).select(guide, "default", false, terrain, candidate -> new HydrologyRegionalRoute.Attempt(candidate, HydrologyRegionalNetwork.EMPTY, null)).refinement());
    }

    @Test
    public void elevationSelectsADifferentThreeDimensionalBendSlice() {
        HydrologyPlannerSettings settings = settings(0.34D, 0.42D);
        List<HydrologyPoint> lower = refine(settings, 76);
        List<HydrologyPoint> upper = refine(settings, 108);
        int changed = 0;
        int compared = Math.min(lower.size(), upper.size());
        for (int index = 0; index < compared; index++) {
            if (lower.get(index).distanceSquared2D(upper.get(index)) > 64L) {
                changed++;
            }
        }
        assertTrue("different bend stations=" + changed, changed > compared / 2);
    }

    @Test
    public void zeroBendStrengthKeepsAnOpenStraightGuide() {
        List<HydrologyPoint> points = refine(settings(0D, 0D), 76);
        assertTrue(points.stream().allMatch(point -> point.z() == 768));
    }

    private static List<HydrologyPoint> refine(HydrologyPlannerSettings settings, int height) {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(height, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(1337L, settings, terrain);
        List<HydrologyPoint> guide = guide(height);
        HydrologyRegionalRoute.Refinement result = new HydrologyRegionalRoute(planner).select(guide, "default", false, terrain, candidate -> new HydrologyRegionalRoute.Attempt(candidate, HydrologyRegionalNetwork.EMPTY, null)).refinement();
        assertValid(result, guide, planner);
        return result.points();
    }

    private static List<HydrologyPoint> guide(int height) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (int x = 0; x <= 6144; x += 256) {
            points.add(new HydrologyPoint(x, height, 768));
        }
        return List.copyOf(points);
    }

    private static void assertValid(HydrologyRegionalRoute.Refinement result, List<HydrologyPoint> guide,
                                    HydrologyPlanner planner) {
        assertNull(result.toString(), result.rejection());
        assertFalse(result.points().isEmpty());
        assertEquals(guide.getFirst(), result.points().getFirst());
        assertEquals(guide.getLast(), result.points().getLast());
        SurfaceCenterline centerline = SurfaceCenterline.densify(result.points());
        HydrologyRegionalHydraulics hydraulics = new HydrologyRegionalHydraulics(planner.settings);
        int availableHead = Integer.MAX_VALUE;
        for (int station = 0; station < centerline.size(); station++) {
            HydrologyTerrainSample terrain = planner.sampler.sample(centerline.x()[station], centerline.z()[station]);
            availableHead = Math.min(availableHead, hydraulics.maximumHead(terrain));
            assertTrue(hydraulics.minimumHead(terrain) <= availableHead);
        }
        double length = 0D;
        List<HydrologyPoint> points = result.points();
        for (int index = 1; index < points.size(); index++) {
            length += StrictMath.sqrt(points.get(index - 1).distanceSquared2D(points.get(index)));
            if (index + 1 == points.size()) {
                continue;
            }
            HydrologyPoint previous = points.get(Math.max(0, index - 4));
            HydrologyPoint point = points.get(index);
            HydrologyPoint next = points.get(Math.min(points.size() - 1, index + 4));
            double firstX = point.x() - previous.x();
            double firstZ = point.z() - previous.z();
            double nextX = next.x() - point.x();
            double nextZ = next.z() - point.z();
            double turn = StrictMath.toDegrees(StrictMath.abs(StrictMath.atan2(firstX * nextZ - firstZ * nextX,
                    firstX * nextX + firstZ * nextZ)));
            assertTrue("turn=" + turn, turn <= planner.settings.geometry().meanders().maximumTurnDegrees());
        }
        assertTrue(length >= planner.settings.routing().regional().minimumLength());
        assertTrue(length <= planner.settings.routing().maximumRouteLength());
    }

    private static HydrologyPlannerSettings settings(double primaryStrength, double detailStrength) {
        HydrologyPlannerSettings base = HydrologyRegionalPlannerTest.settings(false, 1);
        HydrologyPlannerSettings.Routing routing = base.routing();
        HydrologyPlannerSettings.Geometry geometry = base.geometry();
        HydrologyPlannerSettings.Regional regional = new HydrologyPlannerSettings.Regional(true, 256, 2048,
                1, 1, 32768, false, 1D, 8);
        return new HydrologyPlannerSettings(base.seaLevel(), new HydrologyPlannerSettings.Routing(routing.tileSize(),
                routing.sampleSpacing(), routing.maximumRouteNodes(), 8192, routing.minimumSurfaceCourseLength(),
                routing.minimumUndergroundCourseLength(), routing.valleyPreference(), routing.uphillPenalty(),
                routing.slopePenalty(), routing.confluenceAttraction(), routing.lengthPreference(), routing.tributaries(), regional),
                base.surface(), base.hydraulics(), base.underground(), base.outlets(),
                new HydrologyPlannerSettings.Geometry(new HydrologyPlannerSettings.Meanders(64, 12,
                        primaryStrength, detailStrength, 0.48D, 1, 20D), geometry.surface(), geometry.underground(),
                        geometry.grottos(), geometry.drops()), base.deepFluids(), base.surfacePools(),
                base.widestShoreBiomeWidth(), base.seaCaves(), base.surfacePolicyBounds());
    }
}
