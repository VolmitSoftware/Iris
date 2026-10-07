package art.arcane.iris.generation.hydrology.cave;

import art.arcane.iris.generation.hydrology.HydrologyCaveVoxelViewFactory;
import art.arcane.iris.generation.hydrology.HydrologyObservedPlannedSurface;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyCaveContainmentPlannerTest {
    @Test
    public void cavePositionHashSeparatesAxisExtrusions() {
        assertNotEquals(new CavePosition(0, 31, 0).hashCode(), new CavePosition(1, 0, 0).hashCode());
        assertNotEquals(new CavePosition(0, 1, 0).hashCode(), new CavePosition(0, 0, 31).hashCode());
    }

    private final HydrologyCaveContainmentPlanner planner = new HydrologyCaveContainmentPlanner();

    @Test
    public void planOutputsAreImmutable() {
        TestVoxelView view = new TestVoxelView();
        HydrologyCaveCandidate candidate = candidate(12L, 10, "water",
                Map.of(position(0, 10, 0), HydrologyCaveAction.WET_SOURCE), settings());
        HydrologyCavePlanningResult result = planner.validateAll(view, List.of(candidate));
        HydrologyCavePlan plan = result.plans().get(0);
        assertTrue(plan.accepted());

        assertThrows(
                UnsupportedOperationException.class,
                () -> plan.actions().put(position(9, 9, 9), HydrologyCaveAction.WET_SOURCE)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> plan.baselinePreconditions().put(
                        position(9, 9, 9),
                        new CaveVoxelPrecondition(CaveVoxel.SOLID, false)
                )
        );
        assertThrows(UnsupportedOperationException.class, () -> result.plans().add(plan));
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.actions().put(position(9, 9, 9), HydrologyCaveAction.WET_SOURCE)
        );
    }

    @Test
    public void plannedVolumesRejectHazardsSurfaceExposureAndOverflowWithoutActions() {
        CavePosition center = position(0, 10, 0);
        HydrologyCaveCandidate acceptedCandidate = candidate(
                201L,
                10,
                "water",
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        TestVoxelView lava = new TestVoxelView();
        lava.set(CaveVoxel.LAVA, center);
        TestVoxelView incompatible = new TestVoxelView();
        incompatible.set(CaveVoxel.INCOMPATIBLE_FLUID, center);
        TestVoxelView exposed = new TestVoxelView();
        exposed.set(CaveVoxel.CAVE_AIR, center);
        exposed.openToSurface(center);
        exposed.aboveTerrain(center);
        HydrologyCavePlannerSettings oneVoxel = new HydrologyCavePlannerSettings(
                12,
                20,
                1,
                2,
                2,
                0,
                HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE,
                12,
                20
        );
        HydrologyCaveCandidate overflow = candidate(
                202L,
                10,
                "water",
                Map.of(
                        center,
                        HydrologyCaveAction.WET_SOURCE,
                        position(0, 9, 0),
                        HydrologyCaveAction.WET_SOURCE
                ),
                oneVoxel
        );

        assertRejectedWithoutPublication(
                planner.validate(lava, acceptedCandidate),
                HydrologyCaveRejection.LAVA_CONTACT
        );
        assertRejectedWithoutPublication(
                planner.validate(incompatible, acceptedCandidate),
                HydrologyCaveRejection.INCOMPATIBLE_FLUID
        );
        assertRejectedWithoutPublication(
                planner.validate(exposed, acceptedCandidate),
                HydrologyCaveRejection.OPEN_SURFACE
        );
        assertRejectedWithoutPublication(
                planner.validate(new TestVoxelView(), overflow),
                HydrologyCaveRejection.VOLUME_LIMIT
        );
    }

    @Test
    public void validationCacheReusesStableUniqueObservationsAndInvalidatesChangedVoxels() {
        CavePosition center = position(0, 10, 0);
        HydrologyCaveCandidate candidate = candidate(
                203L,
                10,
                "water",
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        TestVoxelView view = new TestVoxelView();
        HydrologyCaveContainmentPlanner.ValidationCache cache =
                new HydrologyCaveContainmentPlanner.ValidationCache();

        HydrologyCavePlan initial = planner.validateAll(view, List.of(candidate), cache).plans().getFirst();
        HydrologyCavePlan cached = planner.validateAll(view, List.of(candidate), cache).plans().getFirst();

        assertTrue(initial.accepted());
        assertEquals(initial, cached);
        assertEquals(1L, cache.hits());
        assertEquals(1L, cache.misses());

        view.set(CaveVoxel.LAVA, center);
        HydrologyCavePlan changed = planner.validateAll(view, List.of(candidate), cache).plans().getFirst();

        assertEquals(HydrologyCaveRejection.LAVA_CONTACT, changed.rejection());
        assertEquals(1L, cache.hits());
        assertEquals(2L, cache.misses());
    }

    @Test
    public void oversizedPlannedVolumeRejectsBeforeLoadingVoxels() {
        AtomicInteger voxelLoads = new AtomicInteger();
        CaveVoxelView view = new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                voxelLoads.incrementAndGet();
                return true;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                voxelLoads.incrementAndGet();
                return CaveVoxel.SOLID;
            }

            @Override
            public boolean isOpenToSurface(CavePosition position) {
                voxelLoads.incrementAndGet();
                return false;
            }

            @Override
            public boolean isAboveTerrainSurface(CavePosition position) {
                voxelLoads.incrementAndGet();
                return false;
            }
        };
        HydrologyCavePlannerSettings oneVoxel = new HydrologyCavePlannerSettings(
                12,
                20,
                1,
                2,
                2,
                0,
                HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE,
                12,
                20
        );
        HydrologyCaveCandidate candidate = candidate(
                205L,
                10,
                "water",
                Map.of(
                        position(0, 10, 0), HydrologyCaveAction.WET_SOURCE,
                        position(0, 9, 0), HydrologyCaveAction.WET_SOURCE
                ),
                oneVoxel
        );

        HydrologyCavePlan plan = planner.validate(view, candidate);

        assertRejectedWithoutPublication(plan, HydrologyCaveRejection.VOLUME_LIMIT);
        assertEquals(0, voxelLoads.get());
    }

    @Test
    public void plannedSurfaceCacheTracksObservedColumnsWithoutRetainingVoxelObservations() {
        CavePosition center = position(0, 10, 0);
        HydrologyCaveCandidate candidate = candidate(
                204L,
                10,
                "water",
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        HydrologyCaveContainmentPlanner.ValidationCache cache =
                new HydrologyCaveContainmentPlanner.ValidationCache();
        HydrologyObservedPlannedSurface initialSurface = observedSurface(32, true);
        HydrologyObservedPlannedSurface matchingSurface = observedSurface(32, true);
        HydrologyObservedPlannedSurface changedSurface = observedSurface(9, true);

        HydrologyCavePlan initial = planner.validateAll(
                plannedSurfaceView(initialSurface),
                List.of(candidate),
                cache,
                initialSurface
        ).plans().getFirst();
        HydrologyCavePlan cached = planner.validateAll(
                plannedSurfaceView(matchingSurface),
                List.of(candidate),
                cache,
                matchingSurface
        ).plans().getFirst();

        assertTrue(initial.accepted());
        assertEquals(initial, cached);
        assertEquals(1L, cache.hits());
        assertEquals(1L, cache.misses());

        HydrologyCavePlan changed = planner.validateAll(
                plannedSurfaceView(changedSurface),
                List.of(candidate),
                cache,
                changedSurface
        ).plans().getFirst();

        assertEquals(HydrologyCaveRejection.OPEN_SURFACE, changed.rejection());
        assertEquals(1L, cache.hits());
        assertEquals(2L, cache.misses());
    }

    @Test
    public void plannedSurfaceCacheInvalidatesEqualHeightWhenTerrainOwnershipChanges() {
        CavePosition center = position(0, 10, 0);
        HydrologyCaveCandidate candidate = candidate(
                204L, 10, "water", Map.of(center, HydrologyCaveAction.WET_SOURCE), settings());
        HydrologyCaveContainmentPlanner.ValidationCache cache =
                new HydrologyCaveContainmentPlanner.ValidationCache();
        HydrologyObservedPlannedSurface initialSurface = observedSurface(32, true);
        HydrologyObservedPlannedSurface matchingSurface = observedSurface(32, true);
        HydrologyObservedPlannedSurface changedSurface = observedSurface(32, false);

        HydrologyCavePlan initial = planner.validateAll(plannedSurfaceView(initialSurface),
                List.of(candidate), cache, initialSurface).plans().getFirst();
        HydrologyCavePlan cached = planner.validateAll(plannedSurfaceView(matchingSurface),
                List.of(candidate), cache, matchingSurface).plans().getFirst();
        HydrologyCavePlan changed = planner.validateAll(plannedSurfaceView(changedSurface),
                List.of(candidate), cache, changedSurface).plans().getFirst();

        assertTrue(initial.accepted());
        assertEquals(initial, cached);
        assertEquals(HydrologyCaveRejection.OPEN_SURFACE, changed.rejection());
        assertEquals(1L, cache.hits());
        assertEquals(2L, cache.misses());
    }

    @Test
    public void validationCacheEvictsItsPreviousWorkingSetWithoutResettingStatistics() {
        CavePosition center = position(0, 10, 0);
        HydrologyCaveCandidate first = candidate(
                206L,
                10,
                "water",
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        HydrologyCaveCandidate second = candidate(
                207L,
                10,
                "water",
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        HydrologyCaveContainmentPlanner.ValidationCache cache =
                new HydrologyCaveContainmentPlanner.ValidationCache(1, Long.MAX_VALUE);
        TestVoxelView view = new TestVoxelView();

        planner.validateAll(view, List.of(first), cache);
        planner.validateAll(view, List.of(first), cache);
        planner.validateAll(view, List.of(second), cache);
        planner.validateAll(view, List.of(first), cache);

        assertEquals(1L, cache.hits());
        assertEquals(3L, cache.misses());
    }

    @Test
    public void geometricallyExposedPlannedVolumeRejectsBeforeVoxelLoading() {
        CavePosition center = position(0, 10, 0);
        AtomicInteger voxelLoads = new AtomicInteger();
        CaveVoxelView view = new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                return true;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                voxelLoads.incrementAndGet();
                return CaveVoxel.SOLID;
            }

            @Override
            public boolean isOpenToSurface(CavePosition position) {
                return true;
            }

            @Override
            public boolean isAboveTerrainSurface(CavePosition position) {
                return position.equals(center);
            }
        };

        HydrologyCavePlan plan = planner.validate(
                view,
                candidate(
                        2021L,
                        10,
                        "water",
                        Map.of(center, HydrologyCaveAction.WET_SOURCE),
                        settings()
                )
        );

        assertRejectedWithoutPublication(plan, HydrologyCaveRejection.OPEN_SURFACE);
        assertEquals(0, voxelLoads.get());
    }

    @Test
    public void plannedWetVolumeSealsAClosedCaveContact() {
        CavePosition center = position(0, 10, 0);
        CavePosition closedCaveContact = position(1, 10, 0);
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, closedCaveContact);
        HydrologyCavePlan plan = planner.validate(
                view,
                candidate(
                        203L,
                        10,
                        "water",
                        Map.of(center, HydrologyCaveAction.WET_SOURCE),
                        settings()
                )
        );

        assertTrue(plan.rejection().toString(), plan.accepted());
        assertEquals(HydrologyCaveAction.SEAL_GUARD, plan.actions().get(closedCaveContact));
        assertEquals(CaveVoxel.CAVE_AIR, plan.baselinePreconditions().get(closedCaveContact).voxel());
    }

    @Test
    public void plannedWetVolumeSealsGeneratedSurfaceConnectedCaveBelowTerrain() {
        CavePosition center = position(0, 10, 0);
        CavePosition generatedOpening = position(1, 10, 0);
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, generatedOpening);
        view.openToSurface(generatedOpening);

        HydrologyCavePlan plan = planner.validate(
                view,
                candidate(
                        2031L,
                        10,
                        "water",
                        Map.of(center, HydrologyCaveAction.WET_SOURCE),
                        settings()
                )
        );

        assertTrue(plan.rejection().toString(), plan.accepted());
        assertEquals(HydrologyCaveAction.SEAL_GUARD, plan.actions().get(generatedOpening));
        assertTrue(plan.baselinePreconditions().get(generatedOpening).openToSurface());
    }

    @Test
    public void intentionalOpeningIsValidatedAndCapturedTransactionally() {
        CavePosition center = position(0, 10, 0);
        CavePosition opening = position(1, 10, 0);
        HydrologyCaveSource source = new HydrologyCaveSource(
                2032L,
                center,
                center,
                10,
                HydrologyCaveMode.GENERATED_GROTTO
        );
        HydrologyCaveCandidate candidate = new HydrologyCaveCandidate(
                source,
                "water",
                settings(),
                true,
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                Set.of(opening)
        );
        TestVoxelView acceptedView = new TestVoxelView();
        acceptedView.set(CaveVoxel.CAVE_AIR, opening);
        acceptedView.openToSurface(opening);
        acceptedView.aboveTerrain(opening);

        HydrologyCavePlan accepted = planner.validate(acceptedView, candidate);
        assertTrue(accepted.rejection().toString(), accepted.accepted());
        assertEquals(
                new CaveVoxelPrecondition(CaveVoxel.CAVE_AIR, true),
                accepted.baselinePreconditions().get(opening)
        );

        TestVoxelView hazardousView = new TestVoxelView();
        hazardousView.set(CaveVoxel.LAVA, opening);
        assertRejectedWithoutPublication(
                planner.validate(hazardousView, candidate),
                HydrologyCaveRejection.LAVA_CONTACT
        );
    }

    @Test
    public void unconditionalGeneratedOpeningRetainsIntentionalSurfaceExposureWithoutGuards() {
        CavePosition center = position(0, 10, 0);
        CavePosition opening = position(1, 10, 0);
        HydrologyCaveCandidate candidate = new HydrologyCaveCandidate(
                new HydrologyCaveSource(
                        2033L,
                        center,
                        center,
                        10,
                        HydrologyCaveMode.GENERATED_GROTTO
                ),
                "water",
                settings(),
                false,
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                Set.of(opening)
        );
        CaveVoxelView generatedView = new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                return true;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                return CaveVoxel.UNCONDITIONAL;
            }

            @Override
            public boolean isOpenToSurface(CavePosition position) {
                return false;
            }

            @Override
            public boolean isAboveTerrainSurface(CavePosition position) {
                return false;
            }
        };

        HydrologyCavePlan plan = planner.validate(generatedView, candidate);

        assertTrue(plan.rejection().toString(), plan.accepted());
        assertEquals(
                new CaveVoxelPrecondition(CaveVoxel.UNCONDITIONAL, true),
                plan.baselinePreconditions().get(opening)
        );
        assertTrue(plan.actions().values().stream()
                .noneMatch((HydrologyCaveAction action) -> action == HydrologyCaveAction.SEAL_GUARD));
    }

    @Test
    public void plannedDryHeadroomRetainsConfiguredClosedCaveConnection() {
        CavePosition dryHeadroom = position(0, 11, 0);
        CavePosition closedCaveContact = position(1, 11, 0);
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, closedCaveContact);
        HydrologyCavePlan plan = planner.validate(
                view,
                candidate(
                        204L,
                        10,
                        "water",
                        Map.of(dryHeadroom, HydrologyCaveAction.DRY_AIR),
                        detailedSettings(2, HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE)
                )
        );

        assertTrue(plan.rejection().toString(), plan.accepted());
        assertFalse(plan.actions().containsKey(closedCaveContact));
        assertEquals(
                new CaveVoxelPrecondition(CaveVoxel.CAVE_AIR, false),
                plan.baselinePreconditions().get(closedCaveContact)
        );
    }

    @Test
    public void plannedVolumeOverlapArbitrationIsDeterministicAndTransactional() {
        CavePosition center = position(0, 10, 0);
        HydrologyCaveCandidate lower = candidate(
                220L,
                10,
                "water",
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        HydrologyCaveCandidate higher = candidate(
                210L,
                11,
                "lava",
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        TestVoxelView view = new TestVoxelView();

        HydrologyCavePlanningResult forward = planner.validateAll(view, List.of(lower, higher));
        HydrologyCavePlanningResult reverse = planner.validateAll(view, List.of(higher, lower));
        List<HydrologyCavePlan> plansOnlyForward = planner.validateAllPlans(view, List.of(lower, higher));
        List<HydrologyCavePlan> plansOnlyReverse = planner.validateAllPlans(view, List.of(higher, lower));

        assertEquals(forward, reverse);
        assertEquals(forward.plans(), plansOnlyForward);
        assertEquals(forward.plans(), plansOnlyReverse);
        assertTrue(forward.plans().get(0).accepted());
        assertEquals(HydrologyCaveRejection.OVERLAPPING_SOURCE, forward.plans().get(1).rejection());
        assertEquals(210L, forward.plans().get(1).arbitrationWinnerSourceId().getAsLong());
        assertEquals(HydrologyCaveAction.WET_SOURCE, forward.actions().get(center));
    }

    @Test
    public void plannedGuardAndMutationOverlapArbitrationIsDeterministic() {
        CavePosition firstPosition = position(0, 10, 0);
        CavePosition secondPosition = position(1, 10, 0);
        HydrologyCaveCandidate first = candidate(
                230L,
                12,
                "water",
                Map.of(firstPosition, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        HydrologyCaveCandidate second = candidate(
                240L,
                11,
                "water",
                Map.of(secondPosition, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        TestVoxelView view = new TestVoxelView();

        HydrologyCavePlanningResult forward = planner.validateAll(view, List.of(first, second));
        HydrologyCavePlanningResult reverse = planner.validateAll(view, List.of(second, first));
        List<HydrologyCavePlan> plansOnlyForward = planner.validateAllPlans(view, List.of(first, second));
        List<HydrologyCavePlan> plansOnlyReverse = planner.validateAllPlans(view, List.of(second, first));

        assertEquals(forward, reverse);
        assertEquals(forward.plans(), plansOnlyForward);
        assertEquals(forward.plans(), plansOnlyReverse);
        assertTrue(forward.plans().get(0).accepted());
        assertEquals(HydrologyCaveRejection.OVERLAPPING_SOURCE, forward.plans().get(1).rejection());
        assertEquals(230L, forward.plans().get(1).arbitrationWinnerSourceId().orElseThrow());
        assertEquals(HydrologyCaveAction.SEAL_GUARD, forward.actions().get(secondPosition));
    }

    private void assertRejectedWithoutPublication(HydrologyCavePlan plan, HydrologyCaveRejection rejection) {
        assertFalse(plan.accepted());
        assertEquals(rejection, plan.rejection());
        assertTrue(plan.actions().isEmpty());
        assertTrue(plan.baselinePreconditions().isEmpty());
    }

    private HydrologyCaveCandidate candidate(
            long sourceId,
            int waterHeadY,
            String profileKey,
            Map<CavePosition, HydrologyCaveAction> actions,
            HydrologyCavePlannerSettings settings
    ) {
        CavePosition sourcePosition = position(0, waterHeadY, 0);
        return new HydrologyCaveCandidate(
                new HydrologyCaveSource(
                        sourceId,
                        sourcePosition,
                        sourcePosition,
                        waterHeadY,
                        HydrologyCaveMode.GENERATED_GROTTO
                ),
                profileKey,
                settings,
                true,
                actions,
                Set.of()
        );
    }

    private HydrologyCavePlannerSettings settings() {
        return settings(HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE);
    }

    private HydrologyCavePlannerSettings settings(HydrologyCaveFluidPolicy policy) {
        return new HydrologyCavePlannerSettings(
                12,
                20,
                64,
                2,
                2,
                0,
                policy,
                12,
                20
        );
    }

    private HydrologyCavePlannerSettings detailedSettings(
            int dryHeadroom,
            HydrologyCaveFluidPolicy policy
    ) {
        return new HydrologyCavePlannerSettings(
                12,
                20,
                2048,
                4,
                4,
                dryHeadroom,
                policy,
                12,
                20
        );
    }

    private CavePosition position(int x, int y, int z) {
        return new CavePosition(x, y, z);
    }

    private HydrologyObservedPlannedSurface observedSurface(int resolvedHeight, boolean terrainOwned) {
        HydrologyCaveVoxelViewFactory.PlannedSurface surface = new HydrologyCaveVoxelViewFactory.PlannedSurface() {
            @Override
            public int resolve(int x, int z, int naturalHeight) {
                return resolvedHeight;
            }

            @Override
            public boolean ownsTerrain(int x, int z) {
                return terrainOwned;
            }
        };
        return new HydrologyObservedPlannedSurface(surface);
    }

    private CaveVoxelView plannedSurfaceView(HydrologyObservedPlannedSurface surface) {
        return new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                return true;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                return CaveVoxel.SOLID;
            }

            @Override
            public boolean isOpenToSurface(CavePosition position) {
                return isAboveTerrainSurface(position);
            }

            @Override
            public boolean isAboveTerrainSurface(CavePosition position) {
                return !surface.ownsTerrain(position.x(), position.z())
                        || position.y() > surface.resolve(position.x(), position.z(), 32);
            }
        };
    }

    private static final class TestVoxelView implements CaveVoxelView {
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;
        private final Map<CavePosition, CaveVoxel> voxels = new HashMap<>();
        private final Set<CavePosition> surfaceOpenings = new HashSet<>();
        private final Set<CavePosition> aboveTerrain = new HashSet<>();

        private TestVoxelView() {
            this(-32, 32, 0, 32, -32, 32);
        }

        private TestVoxelView(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        @Override
        public boolean isInWorld(CavePosition position) {
            return position.x() >= minX
                    && position.x() <= maxX
                    && position.y() >= minY
                    && position.y() <= maxY
                    && position.z() >= minZ
                    && position.z() <= maxZ;
        }

        @Override
        public CaveVoxel voxelAt(CavePosition position) {
            return voxels.getOrDefault(position, CaveVoxel.SOLID);
        }

        @Override
        public boolean isOpenToSurface(CavePosition position) {
            return surfaceOpenings.contains(position);
        }

        @Override
        public boolean isAboveTerrainSurface(CavePosition position) {
            return aboveTerrain.contains(position);
        }

        private void set(CaveVoxel voxel, CavePosition... positions) {
            for (CavePosition position : positions) {
                voxels.put(position, voxel);
            }
        }

        private void openToSurface(CavePosition position) {
            surfaceOpenings.add(position);
        }

        private void aboveTerrain(CavePosition position) {
            aboveTerrain.add(position);
        }
    }
}
