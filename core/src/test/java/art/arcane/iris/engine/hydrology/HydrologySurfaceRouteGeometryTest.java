package art.arcane.iris.engine.hydrology;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HydrologySurfaceRouteGeometryTest {
    @Test
    public void surfaceAnchorKeepsTheCoarseValleyWhenSmoothGroundIsMuchHigher() {
        HydrologyTerrainSampler sampler = (x, z) -> HydrologyTerrainSample.openLand(
                x == 0 && z == 0 ? 84 : 137, x == 0 && z == 0 ? 100D : 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(91L, HydrologyPlannerSettings.defaults(), sampler);
        HydrologyGridNode node = new HydrologyGridNode(0, 0, 0, 0, 0, 719L, sampler.sample(0, 0));
        planner.planningSamples.set(new HydrologyPlanner.PlanningSamples());

        HydrologyPoint caveAnchor = planner.routeGeometry.routeAnchor(node, false);
        HydrologyPoint surfaceAnchor = planner.routeGeometry.routeAnchor(node, true);

        assertEquals(137, caveAnchor.y());
        assertEquals(node.naturalPoint(), surfaceAnchor);
        assertEquals(caveAnchor, planner.routeGeometry.routeAnchor(node, false));
        assertEquals(surfaceAnchor, planner.routeGeometry.routeAnchor(node, true));
    }

    @Test
    public void surfaceAnchorReliefFitsTheExistingIncisionBudget() {
        HydrologyTerrainSample coarse = HydrologyTerrainSample.openLand(84, 0D, "land");
        HydrologyGridNode node = new HydrologyGridNode(0, 0, 0, 0, 0, 719L, coarse);
        for (int candidateHeight : List.of(85, 84, 80, 75)) {
            HydrologyTerrainSampler sampler = (x, z) -> HydrologyTerrainSample.openLand(candidateHeight, 0D, "land");
            HydrologyPlanner planner = new HydrologyPlanner(91L, HydrologyPlannerSettings.defaults(), sampler);
            double score = planner.routeGeometry.anchorScore(node, 2, 0, 2, 0, 0.3D, true);

            assertEquals("candidate height=" + candidateHeight,
                    candidateHeight <= 84 && candidateHeight >= 76, Double.isFinite(score));
        }
    }

    @Test
    public void steepSurfaceEdgesCanDetourAroundAContainedTerrainPit() {
        HydrologyTerrainSampler sampler = (x, z) -> HydrologyTerrainSample.openLand(
                128 - Math.floorDiv(x, 2) - (x >= 30 && x <= 34 && Math.abs(z) <= 1 ? 14 : 0),
                0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(91L, HydrologyPlannerSettings.defaults(), sampler);
        HydrologyPoint start = new HydrologyPoint(0, 128, 0);
        HydrologyPoint end = new HydrologyPoint(64, 96, 0);
        HydrologyPoint continuation = new HydrologyPoint(96, 80, 0);

        List<HydrologyPoint> cave = planner.routeGeometry.refineEdge(1L, 2L, start, end, continuation, 4, false);
        List<HydrologyPoint> surface = planner.routeGeometry.refineEdge(1L, 2L, start, end, continuation, 4, true);

        assertTrue(cave.isEmpty());
        assertFalse(surface.isEmpty());
        assertTrue(surface.stream().anyMatch(point -> Math.abs(point.z()) > 1));
        assertTrue(surface.stream().allMatch(point -> Math.abs(point.z()) <= 16));
        assertTrue(planner.routePaths.traversableRoute(surface));
        assertFalse(planner.routePaths.containsTerrainPit(surface));
    }

    @Test
    public void curvatureUsesTheContinuousCurveBeforeBlockRounding() {
        HydrologyPlanner planner = new HydrologyPlanner(91L, HydrologyPlannerSettings.defaults(),
                (x, z) -> HydrologyTerrainSample.openLand(84, 0D, "land"));
        RouteCandidate first = candidate(0D, 0D);
        RouteCandidate middle = candidate(3.6D, 0.49D);
        RouteCandidate last = candidate(7D, 2.1D);

        assertTrue(HydrologyRoutePath.routeTurnDegrees(first.point(), middle.point(), last.point()) > 20D);
        assertTrue(planner.routeGeometry.continuousTurnDegrees(first, middle, last) < 20D);
        RouteCandidate[] route = planner.routeGeometry.selectCurvatureAwareTerrainRoute(
                List.of(List.of(first), List.of(middle), List.of(last)), 4D, 20D, 2.5D);

        assertEquals(3, route.length);
    }

    @Test
    public void continuousCurvatureStillRejectsASharpTurn() {
        HydrologyPlanner planner = new HydrologyPlanner(91L, HydrologyPlannerSettings.defaults(),
                (x, z) -> HydrologyTerrainSample.openLand(84, 0D, "land"));
        RouteCandidate[] route = planner.routeGeometry.selectCurvatureAwareTerrainRoute(
                List.of(List.of(candidate(0D, 0D)), List.of(candidate(4D, 0D)), List.of(candidate(4D, 4D))),
                4D, 20D, 2.5D);

        assertEquals(0, route.length);
    }

    private RouteCandidate candidate(double x, double z) {
        return new RouteCandidate(
                new HydrologyPoint((int) StrictMath.round(x), 84, (int) StrictMath.round(z)),
                x, z, 0D, 0D, 0D, new RouteDirection(1D, 0D), true);
    }
}
