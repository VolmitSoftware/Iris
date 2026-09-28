package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.cave.CavePosition;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveMode;
import art.arcane.iris.generation.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveRejection;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyTileTest {
    @Test
    public void retainedOwnerFreezesMutableDraftCollections() {
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        HydrologyPoint point = new HydrologyPoint(0, 72, 0);
        HydraulicSegment segment = new HydraulicSegment(3L, 4L, HydrologyFeatureType.SURFACE_POOL,
                72, 72, 2, 1, false, false, List.of(point), HydraulicChannelProfile.uniform(2D, 1D));
        RiverCourse course = new RiverCourse(4L, RiverCourseType.SURFACE_POOL, OptionalLong.empty(),
                OptionalLong.empty(), "water", 1, List.of(), List.of(segment));
        HydrologyDiagnosticCandidate candidate = new HydrologyDiagnosticCandidate(9L,
                HydrologyCandidateKind.SOURCE, HydrologyFeatureType.SURFACE_POOL, point,
                HydrologyCandidateRejection.SOURCE_SPACING, 16);
        List<DrainageNode> nodes = new ArrayList<>();
        List<DrainageEdge> edges = new ArrayList<>();
        List<RiverOutlet> outlets = new ArrayList<>();
        List<RiverCourse> courses = new ArrayList<>(List.of(course));
        List<HydrologyCavePlan> plans = new ArrayList<>();
        List<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>(List.of(candidate));
        HydrologyOwnerDraft draft = new HydrologyOwnerDraft(key,
                new HydrologyCaveCourseFilter.Result(nodes, edges, outlets, courses, plans), diagnostics, null);
        HydrologyTile tile = new HydrologyTile(key, 91L, 17L, 64, List.of(), List.of(), List.of(), List.of(),
                Set.of(), List.of(), List.of(), RiverFootprint.empty())
                .withResolvedOwner(new CrossTileResolvedOwner(draft, List.of()));

        courses.clear();
        diagnostics.clear();
        nodes.add(new DrainageNode(1L, 0, 0, HydrologyTerrainSample.openLand(80, 0D, "plains"), 10D, 2L));
        edges.add(new DrainageEdge(7L, 8L, 1L, 2L, 1D, 1, 0, List.of(point, new HydrologyPoint(1, 72, 0))));
        outlets.add(new RiverOutlet(2L, HydrologyFeatureType.MOUTH, 1L, point,
                new HydrologyPoint(1, 63, 0), 63, true));
        CavePosition position = new CavePosition(0, 20, 0);
        plans.add(new HydrologyCavePlan(new HydrologyCaveSource(4L, position, position, 20,
                HydrologyCaveMode.CLOSED_COMPONENT), HydrologyCaveRejection.NONE,
                Map.of(), Map.of(), OptionalLong.empty()));

        HydrologyOwnerDraft retained = tile.resolvedOwner().draft();
        assertEquals(List.of(course), retained.result().courses());
        assertEquals(List.of(candidate), retained.diagnostics());
        assertTrue(retained.result().nodes().isEmpty());
        assertTrue(retained.result().edges().isEmpty());
        assertTrue(retained.result().outlets().isEmpty());
        assertTrue(retained.result().cavePlans().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> retained.result().courses().clear());
        assertThrows(UnsupportedOperationException.class, () -> retained.diagnostics().clear());
    }
}
