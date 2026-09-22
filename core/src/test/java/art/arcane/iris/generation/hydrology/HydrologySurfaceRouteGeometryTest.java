package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
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
    public void undergroundEdgesRejectAContainedTerrainPit() {
        HydrologyTerrainSampler sampler = (x, z) -> HydrologyTerrainSample.openLand(
                128 - Math.floorDiv(x, 2) - (x >= 30 && x <= 34 && Math.abs(z) <= 1 ? 14 : 0),
                0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(91L, HydrologyPlannerSettings.defaults(), sampler);
        HydrologyPoint start = new HydrologyPoint(0, 128, 0);
        HydrologyPoint end = new HydrologyPoint(64, 96, 0);
        HydrologyPoint continuation = new HydrologyPoint(96, 80, 0);

        List<HydrologyPoint> cave = planner.routeGeometry.refineUndergroundEdge(1L, 2L, start, end, continuation, 4);
        assertTrue(cave.isEmpty());
    }
}
