package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceFootprint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalTributariesTest {
    @Test
    public void crossingLocalRiverBecomesAConnectedRegionalTributary() {
        HydrologyTerrainSampler terrain = terrain(false);
        HydrologyPlanner planner = new HydrologyPlanner(19L, HydrologyRegionalPlannerTest.settings(false, 8), terrain);
        Fixture fixture = fixture(terrain);
        HydrologyFootprintCompiler footprints = new HydrologyFootprintCompiler(planner.settings, terrain);
        footprints.regionalNetwork = fixture.regional();
        ArrayList<RiverCourse> courses = new ArrayList<>(List.of(fixture.local()));
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        CompiledGraph connected = new HydrologyRegionalTributaries(planner).connect(fixture.graph(), courses, footprints, diagnostics);

        assertEquals("diagnostics=" + diagnostics, 1, courses.size());
        RiverCourse tributary = courses.getFirst();
        assertEquals(fixture.local().id(), tributary.id());
        assertNotEquals(fixture.local().sourceNodeId(), tributary.sourceNodeId());
        assertEquals(fixture.regional().courses().getFirst().outletId(), tributary.outletId());
        assertEquals(256, tributary.segments().getLast().end().x());
        assertEquals(63, tributary.segments().getLast().downstreamHeadY());
        assertFalse(tributary.segments().getLast().type() == HydrologyFeatureType.MOUTH);
        assertTrue(tributary.hydraulicallyNonRising());
        assertEquals(12L, tributary.drainageEdges().getLast().downstreamNodeId());
        SurfaceFootprint water = footprints.surfaceFootprint(tributary);
        assertTrue(water.accepted());
        HydrologyPoint join = tributary.segments().getLast().end();
        assertTrue(water.columns().stream().anyMatch(column -> column.x() == join.x() && column.z() == join.z()
                && column.layer().fluidOwned() && column.layer().fluidHeadY() == 63 && column.layer().bedY() < 63));
        HydrologyCaveCourseFilter.Result combined = planner.regional.include(new HydrologyCaveCourseFilter.Result(
                connected.nodes(), connected.edges(), connected.outlets(), courses, List.of()), fixture.regional());
        HydrologyTile tile = new HydrologyTile(new HydrologyTileKey(0, 0), 19L, 0L, 512,
                combined.nodes(), combined.edges(), combined.outlets(), combined.courses(), List.of(), diagnostics,
                RiverFootprint.empty());
        assertTrue(tile.acyclic());
        DrainageNode source = tile.node(tributary.sourceNodeId().orElseThrow()).orElseThrow();
        assertEquals(0, source.x());
        assertEquals(512, source.z());
    }

    @Test
    public void localJunctionCannotLeaveItsConfinedRegion() {
        HydrologyTerrainSampler terrain = terrain(true);
        HydrologyPlanner planner = new HydrologyPlanner(19L, HydrologyRegionalPlannerTest.settings(false, 8), terrain);
        Fixture fixture = fixture(terrain);
        HydrologyFootprintCompiler footprints = new HydrologyFootprintCompiler(planner.settings, terrain);
        footprints.regionalNetwork = fixture.regional();
        ArrayList<RiverCourse> courses = new ArrayList<>(List.of(fixture.local()));
        new HydrologyRegionalTributaries(planner).connect(fixture.graph(), courses, footprints, new ArrayList<>());
        assertTrue(courses.isEmpty());
    }

    private static Fixture fixture(HydrologyTerrainSampler terrain) {
        List<HydrologyPoint> localPath = line(0, 512, 768, 512);
        List<HydrologyPoint> regionalPath = line(256, 0, 256, 1024);
        DrainageNode localSource = new DrainageNode(1L, 0, 512, terrain.sample(0, 512), 768D, 3L);
        DrainageNode localOutlet = new DrainageNode(2L, 768, 512, terrain.sample(768, 512), 0D, 3L);
        DrainageEdge localEdge = new DrainageEdge(4L, 1L, 2L, 3L, 768D, 1, 0, localPath);
        RiverOutlet outlet = new RiverOutlet(3L, HydrologyFeatureType.MOUTH, 2L,
                new HydrologyPoint(768, 65, 512), new HydrologyPoint(769, 63, 512), 63, true);
        RiverCourse local = new RiverCourse(5L, RiverCourseType.SURFACE, OptionalLong.of(1L), OptionalLong.of(3L),
                "default", 1, List.of(localEdge), List.of(segment(6L, 5L, localPath)));
        DrainageNode regionalSource = new DrainageNode(11L, 256, 0, terrain.sample(256, 0), 1024D, 13L);
        DrainageNode regionalOutlet = new DrainageNode(12L, 256, 1024, terrain.sample(256, 1024), 0D, 13L);
        DrainageEdge regionalEdge = new DrainageEdge(14L, 11L, 12L, 13L, 1024D, 16, 0, regionalPath);
        RiverOutlet sea = new RiverOutlet(13L, HydrologyFeatureType.MOUTH, 12L,
                new HydrologyPoint(256, 65, 1024), new HydrologyPoint(256, 63, 1025), 63, true);
        RiverCourse regional = new RiverCourse(15L, RiverCourseType.SURFACE, OptionalLong.of(11L), OptionalLong.of(13L),
                "default", 16, List.of(regionalEdge), List.of(segment(16L, 15L, regionalPath),
                new HydraulicSegment(17L, 15L, HydrologyFeatureType.MOUTH, 63, 63, 8, 2, false, false,
                        List.of(new HydrologyPoint(256, 63, 1024), new HydrologyPoint(256, 63, 1025)),
                        HydraulicChannelProfile.uniform(8, 2))));
        return new Fixture(new CompiledGraph(List.of(localSource, localOutlet), List.of(localEdge), List.of(outlet), Map.of()),
                local, new HydrologyRegionalNetwork(List.of(regionalSource, regionalOutlet), List.of(regionalEdge), List.of(sea), List.of(regional), List.of(), List.of()));
    }

    private static HydraulicSegment segment(long id, long courseId, List<HydrologyPoint> points) {
        return new HydraulicSegment(id, courseId, HydrologyFeatureType.SURFACE_POOL, 63, 63, 8, 2,
                false, false, points, HydraulicChannelProfile.uniform(8, 2));
    }

    private static List<HydrologyPoint> line(int fromX, int fromZ, int toX, int toZ) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        int steps = Math.max(Math.abs(toX - fromX), Math.abs(toZ - fromZ)) / 4;
        for (int step = 0; step <= steps; step++) {
            points.add(new HydrologyPoint(fromX + (toX - fromX) * step / steps, 63,
                    fromZ + (toZ - fromZ) * step / steps));
        }
        return points;
    }

    private static HydrologyTerrainSampler terrain(boolean confined) {
        return (x, z) -> {
            if (x > 768 || z > 1024) {
                return HydrologyTerrainSample.ocean(60, "ocean");
            }
            HydrologyTerrainSample land = HydrologyTerrainSample.openLand(65, 0D, "land");
            if (!confined || x >= 224) {
                return land;
            }
            return new HydrologyTerrainSample(65, 0D, false, false, 33, 35,
                    true, true, true, false, false, false, 0D, 1D, 1D, 1D, 1D, 1D, 1D, 1D,
                    "land", "land", "land", "land", "land", "land", List.of("default"), List.of(),
                    Double.NaN, "region:west", Double.NaN, true, land.surfacePolicy());
        };
    }

    private record Fixture(CompiledGraph graph, RiverCourse local, HydrologyRegionalNetwork regional) {
    }
}
