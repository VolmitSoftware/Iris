package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxel;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelView;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.engine.hydrology.surface.SurfaceBounds;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class HydrologyRegionalFallsTest {
    private static final HydrologyPlannerSettings SETTINGS = HydrologyPlannerSettings.defaults();
    private static final HydrologyTerrainSampler TERRAIN = (x, z) -> HydrologyTerrainSample.openLand(89, 0D, "parent");

    @Test
    public void fullContainmentProofIsCachedAndOnlyFinalTileActionsAreClipped() {
        AtomicInteger validations = new AtomicInteger();
        HydrologyPlanner planner = new HydrologyPlanner(1L, SETTINGS, TERRAIN, 0, surface -> {
            validations.incrementAndGet();
            return solid(null);
        });
        RiverCourse course = course();
        HydrologyRegionalNetwork metadata = metadata(course, List.of());
        List<HydrologyCavePlan> plans = new HydrologyRegionalFalls(planner, course).validate(metadata, new ArrayList<>());
        assertEquals(1, plans.size());
        HydrologyCavePlan full = plans.getFirst();
        assertTrue(full.actions().containsKey(new CavePosition(12, 80, 0)));
        HydrologyRegionalNetwork regional = metadata(course, plans);
        assertTrue(regional.stations() > full.baselinePreconditions().size());
        SurfaceBounds bounds = new SurfaceBounds(-2, -2, 1, 2);
        HydrologyFootprintCompiler compiler = new HydrologyFootprintCompiler(SETTINGS, TERRAIN, planner.geometrySampler);
        compiler.seedRegionalSurface(regional, bounds);
        HydrologyCaveCourseFilter.Result result = new HydrologyCaveCourseFilter.Result(metadata.nodes(), metadata.edges(),
                metadata.outlets(), metadata.courses(), plans);
        HydrologyCrossTileResolver.MaterializedHydrology materialized = new HydrologyCrossTileResolver(planner)
                .materializeFinalHydrology(result, new ArrayList<>(), compiler, true);
        assertEquals("A clipped raster cannot replace the full regional containment proof", 1, validations.get());
        HydrologyCaveCourseFilter.Result clipped = HydrologyCrossTileResolver.clipRegionalPlans(materialized.result(), regional, bounds);
        HydrologyCavePlan partial = clipped.cavePlans().getFirst();
        assertFalse(partial.actions().isEmpty());
        assertTrue(partial.actions().size() < full.actions().size());
        assertTrue(partial.baselinePreconditions().keySet().stream().allMatch(position -> bounds.contains(position.x(), position.z())));
        for (Map.Entry<CavePosition, HydrologyCaveAction> entry : partial.actions().entrySet()) {
            assertEquals(full.actions().get(entry.getKey()), entry.getValue());
        }
        HydrologyTile tile = new HydrologyTile(new HydrologyTileKey(0, 0), 1L, SETTINGS.fingerprint(), 512,
                clipped.nodes(), clipped.edges(), clipped.outlets(), clipped.courses(), clipped.cavePlans(), List.of(), materialized.footprint());
        assertEquals(1, tile.courses().size());
        assertEquals(full, regional.cavePlans().getFirst());
    }

    @Test
    public void exposedRegionalReceiverRejectsTheEntireCourse() {
        HydrologyPlanner planner = new HydrologyPlanner(1L, SETTINGS, TERRAIN, solid(new CavePosition(12, 80, 0)));
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        assertNull(new HydrologyRegionalFalls(planner, course()).validate(metadata(course(), List.of()), diagnostics));
        assertFalse(diagnostics.isEmpty());
        assertEquals(HydrologyCandidateKind.REGIONAL_SOURCE, diagnostics.getFirst().kind());
    }

    @Test
    public void localTerrainCutsCannotRemoveARegionalSealGuard() {
        HydrologyPlanner planner = new HydrologyPlanner(1L, SETTINGS, TERRAIN, solid(null));
        HydrologyCavePlan plan = new HydrologyRegionalFalls(planner, course())
                .validate(metadata(course(), List.of()), new ArrayList<>()).getFirst();
        CavePosition guard = plan.actions().entrySet().stream()
                .filter(entry -> entry.getValue() == HydrologyCaveAction.SEAL_GUARD).findFirst().orElseThrow().getKey();
        HydrologyFeatureRef feature = new HydrologyFeatureRef(11L, HydrologyFeatureType.SURFACE_POOL, 12L, 13L,
                guard.x(), guard.y(), guard.z(), 1, 0, false);
        HydrologyColumnLayer layer = new HydrologyColumnLayer(feature, guard.y() - 1, guard.y() - 1, guard.y() - 1,
                false, false, true, false, false, false, true, false, false,
                "water", "surface", "mouth", "shore", "bank", "cave");
        HydrologyColumnSample column = new HydrologyColumnSample(guard.x(), guard.z(), 89, 63, false, "parent", List.of(layer));
        RiverFootprint cut = new RiverFootprint(Map.of(RiverFootprint.pack(guard.x(), guard.z()), column));
        assertTrue(HydrologyRegionalProtection.changesWitness("water", cut, "water", plan));
        assertFalse(HydrologyRegionalProtection.changesWitness("water", RiverFootprint.empty(), "water", plan));
    }

    private static RiverCourse course() {
        HydraulicSegment segment = new HydraulicSegment(2L, 1L, HydrologyFeatureType.WATERFALL,
                90, 80, 4, 3, true, true, List.of(new HydrologyPoint(0, 90, 0), new HydrologyPoint(12, 80, 0)),
                HydraulicChannelProfile.uniform(4, 3));
        return new RiverCourse(1L, RiverCourseType.SURFACE, OptionalLong.of(3L), OptionalLong.of(4L),
                "water", 1, List.of(), List.of(segment));
    }

    private static HydrologyRegionalNetwork metadata(RiverCourse course, List<HydrologyCavePlan> plans) {
        DrainageNode node = new DrainageNode(3L, 0, 0, TERRAIN.sample(0, 0), 1D, 4L);
        HydrologyPoint receiver = new HydrologyPoint(12, 80, 0);
        RiverOutlet outlet = new RiverOutlet(4L, HydrologyFeatureType.INLAND_GROTTO, 3L, receiver, receiver, 63, false);
        return new HydrologyRegionalNetwork(List.of(node), List.of(), List.of(outlet), List.of(course), List.of(), plans);
    }

    private static CaveVoxelView solid(CavePosition exposed) {
        return new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                return position.y() >= 0 && position.y() < 128;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                return CaveVoxel.SOLID;
            }

            @Override
            public boolean isOpenToSurface(CavePosition position) {
                return false;
            }

            @Override
            public boolean isAboveTerrainSurface(CavePosition position) {
                return position.equals(exposed);
            }
        };
    }
}
