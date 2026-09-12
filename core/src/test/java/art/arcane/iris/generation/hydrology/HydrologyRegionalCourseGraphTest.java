package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalCourseGraphTest {
    @Test
    public void truncatedCoursePublishesOnlyItsRetainedPathAndDischarge() {
        List<HydrologyPoint> guide = List.of(point(0, 0), point(1000, 0), point(2000, 0));
        HydrologyRegionalFlow flow = new HydrologyRegionalFlow(guide, new int[]{2, 50, 100});
        List<HydrologyPoint> path = List.of(point(0, 0), point(0, 200), point(300, 200), point(500, 100));

        HydrologyRegionalCourseGraph graph = HydrologyRegionalCourseGraph.create(terrain(),
                new HydrologyRegionalCourseGraph.Options(7L, 9L, 128, path, flow));

        assertEquals(0, graph.nodes().getFirst().x());
        assertEquals(500, graph.nodes().getLast().x());
        assertEquals(100, graph.nodes().getLast().z());
        assertEquals(0D, graph.nodes().getLast().potential(), 0D);
        assertEquals(26, graph.discharge());
        double length = 500D + StrictMath.hypot(200D, 100D);
        assertEquals(length, graph.nodes().getFirst().potential(), 1e-9D);
        for (int index = 0; index < graph.edges().size(); index++) {
            DrainageEdge edge = graph.edges().get(index);
            assertEquals(graph.nodes().get(index).id(), edge.upstreamNodeId());
            assertEquals(graph.nodes().get(index + 1).id(), edge.downstreamNodeId());
            assertEquals(9L, edge.outletId());
            assertTrue(edge.cost() > 0D);
            assertTrue(edge.contributingSurfaceSources() <= graph.discharge());
            assertTrue(edge.centerline().getLast().x() <= 500);
        }
    }

    @Test
    public void denseHydraulicPathsRetainOnlyBoundedCoarseGraphNodes() {
        ArrayList<HydrologyPoint> path = new ArrayList<>();
        for (int x = 0; x <= 8192; x++) {
            path.add(point(x, 0));
        }
        HydrologyRegionalFlow flow = new HydrologyRegionalFlow(List.of(path.getFirst(), path.getLast()),
                new int[]{1, 64});

        HydrologyRegionalCourseGraph graph = HydrologyRegionalCourseGraph.create(terrain(),
                new HydrologyRegionalCourseGraph.Options(7L, 9L, 256, path, flow));

        assertEquals(33, graph.nodes().size());
        assertEquals(32, graph.edges().size());
        assertEquals(8192, graph.nodes().getLast().x());
        assertEquals(64, graph.discharge());
        for (DrainageEdge edge : graph.edges()) {
            assertEquals(2, edge.centerline().size());
        }
    }

    private static HydrologyPoint point(int x, int z) {
        return new HydrologyPoint(x, 70, z);
    }

    private static HydrologyTerrainSampler terrain() {
        return (x, z) -> HydrologyTerrainSample.openLand(70, 0D, "land");
    }
}
