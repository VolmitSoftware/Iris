package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.cave.CavePosition;
import art.arcane.iris.generation.hydrology.cave.CaveVoxel;
import art.arcane.iris.generation.hydrology.cave.CaveVoxelView;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.generation.hydrology.surface.SurfaceBounds;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void persistedMixedSurfaceOwnershipRestoresWithoutTerrainPlanning() throws Exception {
        HydrologyPlanner planner = new HydrologyPlanner(1L, SETTINGS, TERRAIN, solid(null));
        RiverCourse regional = course();
        HydrologyRegionalNetwork network = metadata(regional, List.of());
        List<HydrologyCavePlan> plans = new HydrologyRegionalFalls(planner, regional)
                .validate(network, new ArrayList<>());
        network = metadata(regional, plans);
        SurfaceBounds bounds = new SurfaceBounds(-2, -2, 1, 2);
        HydrologyFootprintCompiler compiler = new HydrologyFootprintCompiler(SETTINGS, TERRAIN, planner.geometrySampler);
        compiler.seedRegionalSurface(network, bounds);
        HydrologyCrossTileResolver.MaterializedHydrology materialized = new HydrologyCrossTileResolver(planner)
                .materializeFinalHydrology(new HydrologyCaveCourseFilter.Result(network.nodes(), network.edges(),
                        network.outlets(), network.courses(), plans), new ArrayList<>(), compiler, true);
        HydrologyCaveCourseFilter.Result clipped = HydrologyCrossTileResolver.clipRegionalPlans(materialized.result(), network, bounds);
        RiverCourse local = new RiverCourse(101L, RiverCourseType.SURFACE, regional.sourceNodeId(), regional.outletId(),
                "water", 1, List.of(), List.of(new HydraulicSegment(102L, 101L, HydrologyFeatureType.SURFACE_POOL,
                72, 72, 4, 2, false, false, List.of(new HydrologyPoint(0, 72, 0), new HydrologyPoint(1, 72, 0)),
                HydraulicChannelProfile.uniform(4, 2))));
        HydrologyTile original = new HydrologyTile(new HydrologyTileKey(0, 0), 1L, SETTINGS.fingerprint(),
                SETTINGS.routing().tileSize(), network.nodes(), network.edges(), network.outlets(),
                List.of(regional, local), Set.of(regional.id()), clipped.cavePlans(), List.of(), materialized.footprint());
        PreparedHydrologyTileStore store = new PreparedHydrologyTileStore(temporaryFolder.newFolder().toPath(),
                new HydrologyTileCache.SharedCacheScope("regional-ownership", 1L, 128, "overworld", SETTINGS.fingerprint()),
                SETTINGS.routing().tileSize());
        store.save(original);
        HydrologyTile restored = store.load(original.key()).orElseThrow();
        assertEquals(original, restored);
        assertFalse(restored.cavePlans().isEmpty());
        HydrologyPlanner fresh = new HydrologyPlanner(1L, SETTINGS, (x, z) -> {
            throw new AssertionError("Restoring final ownership must not sample terrain");
        });

        fresh.reuseResolvedTile(restored);

        HydrologyCaveCourseFilter.Result owned = fresh.resolvedOwners.getIfPresent(restored.key()).draft().result();
        assertEquals(List.of(local), owned.courses());
        assertTrue(owned.cavePlans().isEmpty());
        assertEquals(restored.nodes(), owned.nodes());
        assertEquals(restored.edges(), owned.edges());
        assertEquals(restored.outlets(), owned.outlets());
    }

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
                clipped.nodes(), clipped.edges(), clipped.outlets(), clipped.courses(), Set.of(clipped.courses().getFirst().id()), clipped.cavePlans(), List.of(), materialized.footprint());
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
