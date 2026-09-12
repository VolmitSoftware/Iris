package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;

import art.arcane.iris.generation.hydrology.cave.CavePosition;
import art.arcane.iris.generation.hydrology.cave.CaveVoxel;
import art.arcane.iris.generation.hydrology.cave.CaveVoxelPrecondition;
import art.arcane.iris.generation.hydrology.cave.CaveVoxelView;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCavePlan;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HydrologyPlannerGlobalInvariantTest {
    private static final int TILE_SIZE = 128;
    private static final CaveVoxelView SOLID_CAVE_VIEW = new SolidCaveVoxelView();

    @Test
    public void deepOnlyPlanningSkipsTheRoutedDrainageLattice() {
        HydrologyPlannerSettings.DeepFluid deepFluid = new HydrologyPlannerSettings.DeepFluid(
                "deep_water",
                true,
                1D,
                TILE_SIZE,
                -32,
                -32,
                1,
                1,
                1,
                1,
                0,
                0,
                1,
                1,
                1,
                64,
                1,
                true,
                false
        );
        AtomicInteger sampleCount = new AtomicInteger();
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            sampleCount.incrementAndGet();
            return terrain(120, false, false, false, false);
        };
        HydrologyPlanner planner = new HydrologyPlanner(
                7341L,
                settings(disabledSource(), disabledSource(), false, false, List.of(deepFluid)),
                sampler,
                SOLID_CAVE_VIEW
        );

        HydrologyTile tile = planner.plan(new HydrologyTileKey(0, 0));

        assertTrue(tile.nodes().isEmpty());
        assertTrue(tile.edges().isEmpty());
        assertTrue(tile.outlets().isEmpty());
        assertEquals(1, courses(tile, RiverCourseType.DEEP_FLUID).size());
        assertTrue(sampleCount.get() < 50);
    }

    @Test
    public void deepSitesUseOneWorldLatticeAcrossAxesCornersNegativesAndRequestOrder() {
        int spacing = 192;
        HydrologyPlannerSettings.DeepFluid deepFluid = new HydrologyPlannerSettings.DeepFluid(
                "deep_lava",
                true,
                64D,
                spacing,
                -96,
                -80,
                1,
                1,
                1,
                1,
                0,
                0,
                1,
                1,
                1,
                64,
                64,
                true,
                false
        );
        HydrologyPlannerSettings settings = settings(
                disabledSource(),
                disabledSource(),
                false,
                false,
                List.of(deepFluid)
        );
        HydrologyPlanner planner = new HydrologyPlanner(
                99177L,
                settings,
                (int x, int z) -> terrain(120, false, false, false, false),
                SOLID_CAVE_VIEW
        );
        List<HydrologyTileKey> keys = tileKeys(3);

        Map<HydrologyTileKey, HydrologyTile> forward = plans(planner, keys);
        ArrayList<HydrologyTileKey> reversedKeys = new ArrayList<>(keys);
        Collections.reverse(reversedKeys);
        Map<HydrologyTileKey, HydrologyTile> reverse = plans(planner, reversedKeys);
        List<HydrologyPoint> sites = deepSites(forward);

        assertEquals(forward, reverse);
        assertTrue(sites.size() > 4);
        assertQuadrants(sites);
        assertMinimumSpacing(sites, spacing);
        assertEquals(sites.size(), new HashSet<>(sites).size());
        for (Map.Entry<HydrologyTileKey, HydrologyTile> entry : forward.entrySet()) {
            for (RiverCourse course : courses(entry.getValue(), RiverCourseType.DEEP_FLUID)) {
                HydrologyPoint site = course.segments().getFirst().start();
                assertTrue(entry.getKey().contains(site.x(), site.z(), TILE_SIZE));
            }
        }
    }

    @Test
    public void surfaceSourceSpacingIsGlobalAcrossAxesCornersNegativesAndRequestOrder() {
        assertGlobalSourceSpacing(true);
    }

    @Test
    public void undergroundSourceSpacingIsGlobalAcrossAxesCornersNegativesAndRequestOrder() {
        assertGlobalSourceSpacing(false);
    }

    @Test
    public void acceptedPlansRemainDeterministicAcrossSeeds() {
        HydrologyPlannerSettings.Source surface = new HydrologyPlannerSettings.Source(
                true,
                1D,
                80,
                0,
                1,
                32
        );
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            int height = 180 - Math.floorDiv(Math.abs(x - 64), 8) - Math.floorDiv(Math.abs(z - 64), 8);
            return surfaceTerrain(height, true, false, 1D);
        };
        long[] seeds = {11L, 29L, 47L, 83L, 101L};
        for (long seed : seeds) {
            HydrologyTile first = new HydrologyPlanner(
                    seed,
                    settings(surface, disabledSource(), true, false, List.of()),
                    sampler,
                    SOLID_CAVE_VIEW
            ).plan(new HydrologyTileKey(0, 0));
            HydrologyTile second = new HydrologyPlanner(
                    seed,
                    settings(surface, disabledSource(), true, false, List.of()),
                    sampler,
                    SOLID_CAVE_VIEW
            ).plan(new HydrologyTileKey(0, 0));

            assertEquals("seed " + seed, acceptedPlanFingerprint(first), acceptedPlanFingerprint(second));
        }
    }

    @Test
    public void lazyAdmissionMatchesEagerSelectionAcrossSeededCandidateSets() {
        long[] seeds = {5L, 17L, 31L, 73L, 127L};
        for (long seed : seeds) {
            Random random = new Random(seed);
            for (int scenario = 0; scenario < 32; scenario++) {
                int candidateCount = 8 + random.nextInt(57);
                int target = random.nextInt(candidateCount + 1);
                int guaranteed = random.nextInt(target + 1);
                boolean[] globallyAdmitted = new boolean[candidateCount];
                for (int candidateIndex = 0; candidateIndex < candidateCount; candidateIndex++) {
                    globallyAdmitted[candidateIndex] = random.nextBoolean();
                }

                SourceAdmissionSelection lazy = HydrologySourcePlanner.selectSourceAdmissions(
                        candidateCount,
                        target,
                        guaranteed,
                        (int candidateIndex) -> globallyAdmitted[candidateIndex]
                );

                assertEquals(
                        "seed " + seed + ", scenario " + scenario,
                        eagerSourceSelection(globallyAdmitted, target, guaranteed),
                        lazy.selectedCandidateIndices()
                );
            }
        }
    }

    @Test
    public void lazyAdmissionStopsGlobalEvaluationWhenQuotaIsFilled() {
        AtomicInteger evaluations = new AtomicInteger();

        SourceAdmissionSelection selection = HydrologySourcePlanner.selectSourceAdmissions(
                256,
                1,
                0,
                (int candidateIndex) -> {
                    evaluations.incrementAndGet();
                    return true;
                }
        );

        assertEquals(List.of(0), selection.selectedCandidateIndices());
        assertEquals(1, evaluations.get());
        assertTrue(selection.selected(0));
        assertFalse(selection.selected(1));
        assertFalse(selection.rejectedBySpacing(1));
    }

    @Test
    public void filledQuotaDoesNotBuildNeighborSourceRoutingContexts() {
        HydrologyPlannerSettings.Source surface = new HydrologyPlannerSettings.Source(
                true,
                1D,
                80,
                0,
                1,
                32
        );
        AtomicInteger samplesOutsideOwnerGrid = new AtomicInteger();
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            if (x < -64 || x > 192 || z < -64 || z > 192) {
                samplesOutsideOwnerGrid.incrementAndGet();
            }
            if (x >= 112) {
                return oceanTerrain();
            }
            int height = 180 - Math.floorDiv(Math.abs(x - 64), 8) - Math.floorDiv(Math.abs(z - 64), 8);
            double weight = x == 64 && z == 64 ? 64D : 1D;
            return surfaceTerrain(height, true, false, weight);
        };
        HydrologyTile tile = new HydrologyPlanner(
                4051L,
                settings(surface, disabledSource(), true, false, List.of()),
                sampler,
                SOLID_CAVE_VIEW
        ).plan(new HydrologyTileKey(0, 0));

        List<HydrologyPoint> sources = sourcePoints(tile, RiverCourseType.SURFACE);
        assertEquals(1, sources.size());
        assertPointNear(new HydrologyPoint(64, 180, 64), sources.getFirst(), 8);
        assertEquals(0, samplesOutsideOwnerGrid.get());
    }

    @Test
    public void blockedHigherPriorityNeighborCannotSuppressAViableNaturalSource() {
        HydrologyPlannerSettings.Source surface = new HydrologyPlannerSettings.Source(
                true,
                1D,
                80,
                0,
                1,
                32
        );
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            boolean source = z == 0 && (x == -16 || x == 0);
            int height = x == -16 ? 160 : 120 - Math.floorDiv(Math.max(0, x), 8);
            return surfaceTerrain(height, source, false, x == -16 ? 2D : 1D);
        };
        HydrologyPlanner planner = new HydrologyPlanner(
                5812L,
                settings(surface, disabledSource(), true, false, List.of()),
                sampler,
                SOLID_CAVE_VIEW
        );

        HydrologyTile tile = planner.plan(new HydrologyTileKey(0, 0));
        List<HydrologyPoint> sources = sourcePoints(tile, RiverCourseType.SURFACE);

        assertEquals(1, sources.size());
        assertPointNear(new HydrologyPoint(0, 120, 0), sources.getFirst(), 8);
    }

    @Test
    public void requiredQuotaOutranksAHigherWeightViableNaturalNeighbor() {
        HydrologyPlannerSettings.Source surface = new HydrologyPlannerSettings.Source(
                true,
                1D,
                80,
                1,
                1,
                32
        );
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            if (z == 0 && x == 0) {
                return surfaceTerrain(120, true, true, 1D);
            }
            if (z == 0 && x == 16) {
                return surfaceTerrain(120, true, false, 64D);
            }
            return surfaceTerrain(120 - Math.floorDiv(Math.max(0, x), 8), false, false, 0D);
        };
        HydrologyTile tile = new HydrologyPlanner(
                5813L,
                settings(surface, disabledSource(), true, false, List.of()),
                sampler,
                SOLID_CAVE_VIEW
        ).plan(new HydrologyTileKey(0, 0));

        List<HydrologyPoint> sources = sourcePoints(tile, RiverCourseType.SURFACE);
        assertEquals(1, sources.size());
        assertPointNear(new HydrologyPoint(0, 120, 0), sources.getFirst(), 8);
    }

    @Test
    public void requiredMinimumPerTileDoesNotCreateAClusteredShortBranch() {
        HydrologyPlannerSettings.Source surface = new HydrologyPlannerSettings.Source(
                true,
                0D,
                80,
                2,
                2,
                32
        );
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            boolean source = z == 0 && (x == 0 || x == 16);
            return surfaceTerrain(120 - Math.floorDiv(Math.max(0, x), 8), source, x == 0 && z == 0, 1D);
        };
        HydrologyTile tile = new HydrologyPlanner(
                5814L,
                settings(surface, disabledSource(), true, false, List.of()),
                sampler,
                SOLID_CAVE_VIEW
        ).plan(new HydrologyTileKey(0, 0));

        List<HydrologyPoint> sources = sourcePoints(tile, RiverCourseType.SURFACE);
        assertEquals(1, sources.size());
        assertPointNear(new HydrologyPoint(0, 120, 0), sources.getFirst(), 8);
    }

    @Test
    public void minimumPerTileDoesNotCreateNaturalSourcesWithoutRequiredPolicy() {
        HydrologyPlannerSettings.Source surface = new HydrologyPlannerSettings.Source(
                true,
                0D,
                80,
                2,
                2,
                32
        );
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            boolean source = z == 0 && (x == 0 || x == 16);
            return surfaceTerrain(120 - Math.floorDiv(Math.max(0, x), 8), source, false, 1D);
        };
        HydrologyTile tile = new HydrologyPlanner(
                5814L,
                settings(surface, disabledSource(), true, false, List.of()),
                sampler,
                SOLID_CAVE_VIEW
        ).plan(new HydrologyTileKey(0, 0));

        assertTrue(sourcePoints(tile, RiverCourseType.SURFACE).isEmpty());
    }

    @Test
    public void crossTileAdmissionKeepsOneNetworkAcrossClusteredRequiredSources() {
        int spacing = 192;
        HydrologyPlannerSettings.Source surface = new HydrologyPlannerSettings.Source(
                true,
                0D,
                80,
                0,
                1,
                spacing
        );
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            int localX = Math.floorMod(x, TILE_SIZE);
            int localZ = Math.floorMod(z, TILE_SIZE);
            if (localX >= 112) {
                return oceanTerrain();
            }
            boolean source = localX == 0 && localZ == 0;
            return surfaceTerrain(120 - Math.floorDiv(localX, 8), source, true, 1D);
        };
        HydrologyPlanner planner = new HydrologyPlanner(
                5815L,
                settings(surface, disabledSource(), true, false, List.of()),
                sampler,
                SOLID_CAVE_VIEW
        );
        HydrologyTile negative = planner.plan(new HydrologyTileKey(-1, 0));
        HydrologyTile positive = planner.plan(new HydrologyTileKey(0, 0));
        ArrayList<HydrologyPoint> sources = new ArrayList<>();
        sources.addAll(sourcePoints(negative, RiverCourseType.SURFACE));
        sources.addAll(sourcePoints(positive, RiverCourseType.SURFACE));

        assertEquals(1, sources.size());
    }

    @Test
    public void refinedRoutesRejectAOneBlockOceanBarrierBetweenSamples() {
        HydrologyPlannerSettings.Source surface = new HydrologyPlannerSettings.Source(
                true,
                1D,
                80,
                1,
                1,
                16
        );
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            if (x == 2 || x >= 112) {
                return oceanTerrain();
            }
            return terrain(120 - Math.floorDiv(x, 8), false, x == 0 && z == 0, true, false);
        };
        HydrologyTile tile = new HydrologyPlanner(
                617L,
                settings(surface, disabledSource(), true, false, List.of()),
                sampler,
                SOLID_CAVE_VIEW
        ).plan(new HydrologyTileKey(0, 0));

        assertTrue(courses(tile, RiverCourseType.SURFACE).isEmpty());
        assertTrue(tile.diagnosticCandidates().stream().anyMatch(
                (HydrologyDiagnosticCandidate candidate) -> candidate.rejection()
                        == HydrologyCandidateRejection.NO_DRAINAGE_PATH
        ));
    }

    @Test
    public void gradualCascadesCompileGradedWettedRunsAndReceivingCells() {
        HydrologyPlannerSettings.Source surface = new HydrologyPlannerSettings.Source(
                true,
                1D,
                80,
                1,
                1,
                16
        );
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            int terrace = Math.max(0, Math.floorDiv(x, 32));
            int height = 120 - terrace * 2;
            return surfaceTerrainWithTransit(height, x == 0 && z == 0, true, 1D, z == 0);
        };
        HydrologyTile tile = new HydrologyPlanner(
                1441L,
                gradualSettings(surface, disabledSource(), true, false),
                sampler,
                SOLID_CAVE_VIEW
        ).plan(new HydrologyTileKey(0, 0));

        int cascadeCount = 0;
        for (RiverCourse course : courses(tile, RiverCourseType.SURFACE)) {
            for (HydraulicSegment segment : course.segments()) {
                if (segment.type() != HydrologyFeatureType.CASCADE) {
                    continue;
                }
                cascadeCount++;
                assertTrue(segment.drop() > 0);
                assertFalse(segment.fallingFluid());
                assertTrue(segment.width() >= 1);
                assertTrue(hasCourseChannelLayer(tile, segment.start(), course.id()));
                assertTrue(
                        segment + " column=" + tile.columnAt(segment.end().x(), segment.end().z()),
                        hasChannelHead(tile, segment.end(), course.id(), segment.downstreamHeadY())
                );
                assertGradedCenterline(segment, 2);
            }
        }
        assertTrue(
                courses(tile, RiverCourseType.SURFACE).stream()
                        .map((RiverCourse course) -> course.segments().stream()
                                .map((HydraulicSegment segment) -> segment.type() + ":" + segment.drop())
                                .toList())
                        .toList()
                        .toString(),
                cascadeCount >= 1
        );
    }

    @Test
    public void undergroundGradualDropsCompileGradedChannelsWithBedsAndReceivingCells() {
        HydrologyPlannerSettings.Source underground = new HydrologyPlannerSettings.Source(
                true,
                1D,
                Integer.MIN_VALUE,
                1,
                1,
                16
        );
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            int terrace = Math.max(0, Math.floorDiv(x, 32));
            int fluidY = 84 - terrace * 2;
            return undergroundTerrainWithTransit(120, fluidY, x == 0 && z == 0, true, 1D, z == 0);
        };
        HydrologyTile tile = new HydrologyPlanner(
                1442L,
                gradualSettings(disabledSource(), underground, false, true),
                sampler,
                SOLID_CAVE_VIEW
        ).plan(new HydrologyTileKey(0, 0));

        assertDistributedTransition(tile, RiverCourseType.UNDERGROUND, HydrologyFeatureType.UNDERGROUND_DROP);
    }

    private void assertDistributedTransition(
            HydrologyTile tile,
            RiverCourseType courseType,
            HydrologyFeatureType featureType
    ) {

        boolean gradedDrop = false;
        for (RiverCourse course : courses(tile, courseType)) {
            for (HydraulicSegment segment : course.segments()) {
                if (segment.type() != featureType || segment.drop() <= 0) {
                    continue;
                }
                gradedDrop = true;
                assertFalse(segment.fallingFluid());
                assertTrue(hasChannelLayer(tile, segment.start(), segment.id()));
                assertTrue(hasTransitionLayer(tile, segment.end(), segment.id(), false));
                assertGradedCenterline(segment, 2);
            }
        }
        assertTrue(gradedDrop);
    }

    private void assertGradedCenterline(HydraulicSegment segment, int maximumStepDrop) {
        int previousHead = segment.upstreamHeadY();
        for (HydrologyPoint point : segment.centerline()) {
            assertTrue(point.y() <= previousHead);
            assertTrue(previousHead - point.y() <= maximumStepDrop);
            previousHead = point.y();
        }
        assertEquals(segment.downstreamHeadY(), previousHead);
    }

    private void assertGlobalSourceSpacing(boolean surfaceMode) {
        int spacing = 192;
        HydrologyPlannerSettings.Source active = new HydrologyPlannerSettings.Source(
                true,
                64D,
                80,
                0,
                64,
                spacing
        );
        HydrologyPlannerSettings plannerSettings = settings(
                surfaceMode ? active : disabledSource(),
                surfaceMode ? disabledSource() : active,
                surfaceMode,
                !surfaceMode,
                List.of()
        );
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            int localX = Math.floorMod(x, TILE_SIZE);
            int localZ = Math.floorMod(z, TILE_SIZE);
            if (localX >= 112) {
                return oceanTerrain();
            }
            boolean source = localX == 0 && localZ == 0;
            int height = 120 - Math.floorDiv(localX, 8);
            return terrain(height, false, surfaceMode && source, false, !surfaceMode && source);
        };
        HydrologyPlanner planner = new HydrologyPlanner(47119L, plannerSettings, sampler, SOLID_CAVE_VIEW);
        List<HydrologyTileKey> keys = tileKeys(3);

        Map<HydrologyTileKey, HydrologyTile> forward = plans(planner, keys);
        ArrayList<HydrologyTileKey> reversedKeys = new ArrayList<>(keys);
        Collections.reverse(reversedKeys);
        Map<HydrologyTileKey, HydrologyTile> reverse = plans(planner, reversedKeys);
        RiverCourseType type = surfaceMode ? RiverCourseType.SURFACE : RiverCourseType.UNDERGROUND;
        ArrayList<HydrologyPoint> sources = new ArrayList<>();
        for (HydrologyTile tile : forward.values()) {
            sources.addAll(sourcePoints(tile, type));
        }
        assertEquals(forward, reverse);
        if (surfaceMode) {
            assertTrue(sources.toString(), sources.size() >= 2);
            assertTrue(sources.stream().anyMatch((HydrologyPoint point) -> point.x() < 0));
            assertTrue(sources.stream().anyMatch((HydrologyPoint point) -> point.x() >= 0));
            assertTrue(sources.stream().anyMatch((HydrologyPoint point) -> point.z() < 0));
            ArrayList<HydrologyTileKey> positiveKeys = new ArrayList<>();
            for (HydrologyTileKey key : tileKeys(1)) {
                positiveKeys.add(new HydrologyTileKey(key.tileX(), key.tileZ() + 8));
            }
            HydrologyPlanner positivePlanner = new HydrologyPlanner(
                    47119L,
                    plannerSettings,
                    sampler,
                    SOLID_CAVE_VIEW
            );
            ArrayList<HydrologyPoint> positiveSources = new ArrayList<>();
            for (HydrologyTile tile : plans(positivePlanner, positiveKeys).values()) {
                positiveSources.addAll(sourcePoints(tile, type));
            }
            assertTrue(positiveSources.toString(), positiveSources.stream().anyMatch(
                    (HydrologyPoint point) -> point.z() >= 0
            ));
            assertMinimumSpacing(positiveSources, spacing);
        } else {
            assertTrue(sources.size() > 4);
            assertQuadrants(sources);
        }
        assertMinimumSpacing(sources, spacing);
        assertEquals(sources.size(), new HashSet<>(sources).size());
    }

    private HydrologyPlannerSettings settings(
            HydrologyPlannerSettings.Source surfaceSources,
            HydrologyPlannerSettings.Source undergroundSources,
            boolean surfaceEnabled,
            boolean undergroundEnabled,
            List<HydrologyPlannerSettings.DeepFluid> deepFluids
    ) {
        return plannerSettings(
                surfaceSources,
                undergroundSources,
                surfaceEnabled,
                undergroundEnabled,
                deepFluids
        );
    }

    private HydrologyPlannerSettings plannerSettings(
            HydrologyPlannerSettings.Source surfaceSources,
            HydrologyPlannerSettings.Source undergroundSources,
            boolean surfaceEnabled,
            boolean undergroundEnabled,
            List<HydrologyPlannerSettings.DeepFluid> deepFluids
    ) {
        return new HydrologyPlannerSettings(
                63,
                new HydrologyPlannerSettings.Routing(
                        TILE_SIZE,
                        16,
                        512,
                        256,
                        16,
                        8,
                        0.5D,
                        12D,
                        0.5D,
                        0.1D,
                        1D,
                        0, HydrologyPlannerSettings.Regional.disabled()
                ),
                new HydrologyPlannerSettings.Surface(
                        surfaceEnabled,
                        surfaceSources,
                        2,
                        4,
                        1,
                        2,
                        128,
                        1D,
                        HydrologyPlannerSettings.Banks.defaults()),
                new HydrologyPlannerSettings.Hydraulics(4),
                HydrologyPlannerSettings.Underground.of(
                        undergroundEnabled,
                        undergroundSources,
                        64,
                        84,
                        2,
                        4,
                        1,
                        2,
                        2,
                        4,
                        false,
                        0
                ),
                HydrologyPlannerSettings.Outlets.of(
                        true,
                        new HydrologyPlannerSettings.Grotto(false, 2, 2, 2, 512),
                        new HydrologyPlannerSettings.Grotto(false, 2, 2, 2, 512),
                        false,
                        32,
                        16,
                        1,
                        8,
                        8
                ),
                HydrologyPlannerSettings.Geometry.defaults(),
                deepFluids, List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private HydrologyPlannerSettings gradualSettings(
            HydrologyPlannerSettings.Source surfaceSources,
            HydrologyPlannerSettings.Source undergroundSources,
            boolean surfaceEnabled,
            boolean undergroundEnabled
    ) {
        HydrologyPlannerSettings base = plannerSettings(
                surfaceSources,
                undergroundSources,
                surfaceEnabled,
                undergroundEnabled,
                List.of()
        );
        HydrologyPlannerSettings.Surface surface = base.surface();
        HydrologyPlannerSettings.Underground underground = base.underground();
        return new HydrologyPlannerSettings(
                base.seaLevel(),
                base.routing(),
                new HydrologyPlannerSettings.Surface(
                        surface.enabled(),
                        surface.sources(),
                        surface.minimumWidth(),
                        surface.minimumWidth(),
                        surface.minimumDepth(),
                        surface.maximumDepth(),
                        surface.maximumIncision(),
                        surface.shoreWidth(),
                        HydrologyPlannerSettings.Banks.defaults()),
                base.hydraulics(),
                HydrologyPlannerSettings.Underground.of(
                        underground.enabled(),
                        underground.sources(),
                        underground.minimumFluidY(),
                        underground.maximumFluidY(),
                        underground.minimumWidth(),
                        underground.minimumWidth(),
                        underground.minimumDepth(),
                        underground.maximumDepth(),
                        underground.minimumHeadroom(),
                        underground.maximumHeadroom(),
                        underground.connectToExistingCaves(),
                        0
                ),
                base.outlets(),
                HydrologyPlannerSettings.Geometry.defaults(),
                base.deepFluids(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private HydrologyPlannerSettings.Source disabledSource() {
        return new HydrologyPlannerSettings.Source(false, 0D, Integer.MIN_VALUE, 0, 0, 0);
    }

    private Map<HydrologyTileKey, HydrologyTile> plans(
            HydrologyPlanner planner,
            List<HydrologyTileKey> keys
    ) {
        HashMap<HydrologyTileKey, HydrologyTile> plans = new HashMap<>();
        for (HydrologyTileKey key : keys) {
            plans.put(key, planner.plan(key));
        }
        return Map.copyOf(plans);
    }

    private List<HydrologyTileKey> tileKeys(int radius) {
        ArrayList<HydrologyTileKey> keys = new ArrayList<>();
        for (int tileZ = -radius; tileZ <= radius; tileZ++) {
            for (int tileX = -radius; tileX <= radius; tileX++) {
                keys.add(new HydrologyTileKey(tileX, tileZ));
            }
        }
        return List.copyOf(keys);
    }

    private void assertPointNear(HydrologyPoint expected, HydrologyPoint actual, int radius) {
        assertTrue(StrictMath.abs(expected.y() - actual.y()) <= 2);
        assertTrue(actual.distanceSquared2D(expected) <= (long) radius * radius);
    }

    private List<HydrologyPoint> deepSites(Map<HydrologyTileKey, HydrologyTile> plans) {
        ArrayList<HydrologyPoint> sites = new ArrayList<>();
        for (HydrologyTile tile : plans.values()) {
            for (RiverCourse course : courses(tile, RiverCourseType.DEEP_FLUID)) {
                sites.add(course.segments().getFirst().start());
            }
        }
        return List.copyOf(sites);
    }

    private List<HydrologyPoint> sourcePoints(HydrologyTile tile, RiverCourseType type) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (RiverCourse course : courses(tile, type)) {
            long sourceNodeId = course.sourceNodeId().orElseThrow();
            DrainageNode source = tile.node(sourceNodeId).orElseThrow();
            points.add(new HydrologyPoint(source.x(), source.terrain().naturalHeight(), source.z()));
        }
        return List.copyOf(points);
    }

    private List<RiverCourse> courses(HydrologyTile tile, RiverCourseType type) {
        ArrayList<RiverCourse> selected = new ArrayList<>();
        for (RiverCourse course : tile.courses()) {
            if (course.type() == type) {
                selected.add(course);
            }
        }
        return List.copyOf(selected);
    }

    private long acceptedPlanFingerprint(HydrologyTile tile) {
        AcceptedPlanFingerprint fingerprint = new AcceptedPlanFingerprint();
        fingerprint.add(tile);
        return fingerprint.value();
    }

    private List<Integer> eagerSourceSelection(boolean[] globallyAdmitted, int target, int guaranteed) {
        ArrayList<Integer> selected = new ArrayList<>(Math.min(globallyAdmitted.length, target));
        boolean[] selectedCandidates = new boolean[globallyAdmitted.length];
        for (int candidateIndex = 0; candidateIndex < globallyAdmitted.length; candidateIndex++) {
            if (!globallyAdmitted[candidateIndex] || selected.size() >= target) {
                continue;
            }
            selected.add(candidateIndex);
            selectedCandidates[candidateIndex] = true;
        }
        for (int candidateIndex = 0;
             candidateIndex < globallyAdmitted.length && selected.size() < guaranteed;
             candidateIndex++) {
            if (selectedCandidates[candidateIndex]) {
                continue;
            }
            selected.add(candidateIndex);
            selectedCandidates[candidateIndex] = true;
        }
        return List.copyOf(selected);
    }

    private void assertMinimumSpacing(List<HydrologyPoint> points, int spacing) {
        long minimumSquared = (long) spacing * spacing;
        for (int firstIndex = 0; firstIndex < points.size(); firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex < points.size(); secondIndex++) {
                HydrologyPoint first = points.get(firstIndex);
                HydrologyPoint second = points.get(secondIndex);
                assertTrue(
                        "first=" + first + " second=" + second + " spacing=" + spacing,
                        first.distanceSquared2D(second) >= minimumSquared
                );
            }
        }
    }

    private void assertQuadrants(List<HydrologyPoint> points) {
        assertTrue(points.stream().anyMatch((HydrologyPoint point) -> point.x() < 0));
        assertTrue(points.stream().anyMatch((HydrologyPoint point) -> point.x() >= 0));
        assertTrue(points.stream().anyMatch((HydrologyPoint point) -> point.z() < 0));
        assertTrue(points.stream().anyMatch((HydrologyPoint point) -> point.z() >= 0));
    }

    private boolean hasTransitionLayer(
            HydrologyTile tile,
            HydrologyPoint point,
            long segmentId,
            boolean falling
    ) {
        HydrologyColumnSample column = tile.columnAt(point.x(), point.z()).orElseThrow();
        for (HydrologyColumnLayer layer : column.layers()) {
            if (layer.feature().segmentId() == segmentId
                    && (falling ? layer.fallingFluid() : layer.receivingPool())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCourseChannelLayer(HydrologyTile tile, HydrologyPoint point, long courseId) {
        HydrologyColumnSample column = tile.columnAt(point.x(), point.z()).orElseThrow();
        for (HydrologyColumnLayer layer : column.layers()) {
            if (layer.feature().courseId() == courseId && layer.channel()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasChannelHead(HydrologyTile tile, HydrologyPoint point, long courseId, int head) {
        HydrologyColumnSample column = tile.columnAt(point.x(), point.z()).orElseThrow();
        for (HydrologyColumnLayer layer : column.layers()) {
            if (layer.feature().courseId() == courseId && layer.channel() && layer.fluidHeadY() == head) {
                return true;
            }
        }
        return false;
    }

    private boolean hasChannelLayer(HydrologyTile tile, HydrologyPoint point, long segmentId) {
        HydrologyColumnSample column = tile.columnAt(point.x(), point.z()).orElseThrow();
        for (HydrologyColumnLayer layer : column.layers()) {
            if (layer.feature().segmentId() == segmentId && layer.channel()) {
                return true;
            }
        }
        return false;
    }

    private HydrologyTerrainSample terrain(
            int height,
            boolean ocean,
            boolean surfaceSource,
            boolean required,
            boolean undergroundSource
    ) {
        return new HydrologyTerrainSample(
                height,
                ocean ? 0D : 1D,
                ocean,
                !ocean,
                68,
                74,
                !ocean,
                !ocean,
                surfaceSource,
                surfaceSource && required,
                undergroundSource,
                undergroundSource && required,
                0D,
                surfaceSource ? 1D : 0D,
                undergroundSource ? 1D : 0D,
                1D,
                1D,
                1D,
                1D,
                1D,
                ocean ? "ocean" : "land",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded",
                List.of("default"), List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true,
                SurfaceRiverPolicy.INHERIT
        );
    }

    private HydrologyTerrainSample oceanTerrain() {
        return terrain(54, true, false, false, false);
    }

    private HydrologyTerrainSample surfaceTerrain(
            int height,
            boolean source,
            boolean required,
            double weight
    ) {
        return surfaceTerrainWithTransit(height, source, required, weight, true);
    }

    private HydrologyTerrainSample surfaceTerrainWithTransit(
            int height,
            boolean source,
            boolean required,
            double weight,
            boolean transit
    ) {
        return new HydrologyTerrainSample(
                height,
                1D,
                false,
                true,
                68,
                74,
                transit,
                transit,
                source,
                source && required,
                false,
                false,
                0D,
                source ? weight : 0D,
                0D,
                1D,
                1D,
                1D,
                1D,
                1D,
                "land",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded",
                List.of("default"), List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true,
                SurfaceRiverPolicy.INHERIT
        );
    }

    private HydrologyTerrainSample undergroundTerrain(
            int height,
            int fluidY,
            boolean source,
            boolean required,
            double weight
    ) {
        return undergroundTerrainWithTransit(height, fluidY, source, required, weight, true);
    }

    private HydrologyTerrainSample undergroundTerrainWithTransit(
            int height,
            int fluidY,
            boolean source,
            boolean required,
            double weight,
            boolean transit
    ) {
        return new HydrologyTerrainSample(
                height,
                1D,
                false,
                true,
                fluidY - 4,
                fluidY,
                transit,
                transit,
                false,
                false,
                source,
                source && required,
                0D,
                0D,
                source ? weight : 0D,
                1D,
                1D,
                1D,
                1D,
                1D,
                "land",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded",
                List.of("default"), List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true,
                SurfaceRiverPolicy.INHERIT
        );
    }

    private static final class SolidCaveVoxelView implements CaveVoxelView {
        @Override
        public boolean isInWorld(CavePosition position) {
            return position.y() > -2048 && position.y() < 2048;
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
            return false;
        }
    }

    private static final class AcceptedPlanFingerprint {
        private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
        private static final long FNV_PRIME = 0x100000001b3L;
        private static final Comparator<HydrologyColumnLayer> LAYER_ORDER = Comparator
                .comparingLong((HydrologyColumnLayer layer) -> layer.feature().courseId())
                .thenComparingLong((HydrologyColumnLayer layer) -> layer.feature().segmentId())
                .thenComparingLong((HydrologyColumnLayer layer) -> layer.feature().id())
                .thenComparingInt(HydrologyColumnLayer::bedY)
                .thenComparingInt(HydrologyColumnLayer::fluidHeadY)
                .thenComparingInt(HydrologyColumnLayer::ceilingY);
        private static final Comparator<Map.Entry<CavePosition, ?>> CAVE_POSITION_ORDER = Comparator
                .comparingInt((Map.Entry<CavePosition, ?> entry) -> entry.getKey().x())
                .thenComparingInt((Map.Entry<CavePosition, ?> entry) -> entry.getKey().y())
                .thenComparingInt((Map.Entry<CavePosition, ?> entry) -> entry.getKey().z());

        private long hash = FNV_OFFSET_BASIS;

        private long value() {
            return hash;
        }

        private void add(HydrologyTile tile) {
            addString("nodes");
            addInt(tile.nodes().size());
            for (DrainageNode node : tile.nodes()) {
                add(node);
            }
            addString("edges");
            addInt(tile.edges().size());
            for (DrainageEdge edge : tile.edges()) {
                add(edge);
            }
            addString("outlets");
            addInt(tile.outlets().size());
            for (RiverOutlet outlet : tile.outlets()) {
                add(outlet);
            }
            addString("courses");
            addInt(tile.courses().size());
            for (RiverCourse course : tile.courses()) {
                add(course);
            }
            addString("cavePlans");
            addInt(tile.cavePlans().size());
            for (HydrologyCavePlan plan : tile.cavePlans()) {
                add(plan);
            }
            addString("footprint");
            ArrayList<Map.Entry<Long, HydrologyColumnSample>> columns = new ArrayList<>(
                    tile.footprint().columns().entrySet()
            );
            columns.sort(Comparator.comparingLong((Map.Entry<Long, HydrologyColumnSample> entry) -> entry.getKey()));
            addInt(columns.size());
            for (Map.Entry<Long, HydrologyColumnSample> entry : columns) {
                addLong(entry.getKey());
                add(entry.getValue());
            }
        }

        private void add(DrainageNode node) {
            addLong(node.id());
            addInt(node.x());
            addInt(node.z());
            add(node.terrain());
            addDouble(node.potential());
            addLong(node.outletId());
        }

        private void add(HydrologyTerrainSample terrain) {
            addInt(terrain.naturalHeight());
            addDouble(terrain.slope());
            addBoolean(terrain.ocean());
            addBoolean(terrain.caveAvailable());
            addInt(terrain.caveFloorY());
            addInt(terrain.caveFluidY());
            addBoolean(terrain.transitAllowed());
            addBoolean(terrain.outletAllowed());
            addBoolean(terrain.surfaceSourceAllowed());
            addBoolean(terrain.surfaceSourceRequired());
            addBoolean(terrain.undergroundSourceAllowed());
            addBoolean(terrain.undergroundSourceRequired());
            addDouble(terrain.routingCost());
            addDouble(terrain.surfaceSourceWeight());
            addDouble(terrain.undergroundSourceWeight());
            addDouble(terrain.widthMultiplier());
            addDouble(terrain.depthMultiplier());
            addDouble(terrain.incisionMultiplier());
            addDouble(terrain.routingMultiplier());
            addString(terrain.parentBiomeKey());
            addString(terrain.surfaceBiomeKey());
            addString(terrain.mouthBiomeKey());
            addString(terrain.shoreBiomeKey());
            addString(terrain.bankBiomeKey());
            addString(terrain.floodedCaveBiomeKey());
            addInt(terrain.preferredProfileKeys().size());
            for (String profile : terrain.preferredProfileKeys()) {
                addString(profile);
            }
        }

        private void add(DrainageEdge edge) {
            addLong(edge.id());
            addLong(edge.upstreamNodeId());
            addLong(edge.downstreamNodeId());
            addLong(edge.outletId());
            addDouble(edge.cost());
            addInt(edge.contributingSurfaceSources());
            addInt(edge.contributingUndergroundSources());
            addPoints(edge.centerline());
        }

        private void add(RiverOutlet outlet) {
            addLong(outlet.id());
            addEnum(outlet.type());
            addLong(outlet.drainageNodeId());
            add(outlet.landwardPoint());
            add(outlet.connectionPoint());
            addInt(outlet.seaLevel());
            addBoolean(outlet.directOcean());
        }

        private void add(RiverCourse course) {
            addLong(course.id());
            addEnum(course.type());
            addBoolean(course.sourceNodeId().isPresent());
            if (course.sourceNodeId().isPresent()) {
                addLong(course.sourceNodeId().getAsLong());
            }
            addBoolean(course.outletId().isPresent());
            if (course.outletId().isPresent()) {
                addLong(course.outletId().getAsLong());
            }
            addString(course.profileKey());
            addInt(course.discharge());
            addInt(course.drainageEdges().size());
            for (DrainageEdge edge : course.drainageEdges()) {
                add(edge);
            }
            addInt(course.segments().size());
            for (HydraulicSegment segment : course.segments()) {
                add(segment);
            }
        }

        private void add(HydraulicSegment segment) {
            addLong(segment.id());
            addLong(segment.courseId());
            addEnum(segment.type());
            addInt(segment.upstreamHeadY());
            addInt(segment.downstreamHeadY());
            addInt(segment.width());
            addInt(segment.depth());
            addBoolean(segment.fallingFluid());
            addBoolean(segment.receivingPool());
            addPoints(segment.centerline());
            addInt(segment.channelProfile().size());
            for (int station = 0; station < segment.channelProfile().size(); station++) {
                addLong(Double.doubleToLongBits(segment.channelProfile().widthAt(station)));
                addLong(Double.doubleToLongBits(segment.channelProfile().depthAt(station)));
            }
        }

        private void add(HydrologyCavePlan plan) {
            addLong(plan.source().sourceId());
            add(plan.source().entry());
            add(plan.source().target());
            addInt(plan.source().waterHeadY());
            addEnum(plan.source().mode());
            addEnum(plan.rejection());
            ArrayList<Map.Entry<CavePosition, HydrologyCaveAction>> actions = new ArrayList<>(
                    plan.actions().entrySet()
            );
            actions.sort(AcceptedPlanFingerprint::compareCavePositions);
            addInt(actions.size());
            for (Map.Entry<CavePosition, HydrologyCaveAction> entry : actions) {
                add(entry.getKey());
                addEnum(entry.getValue());
            }
            ArrayList<Map.Entry<CavePosition, CaveVoxelPrecondition>> preconditions = new ArrayList<>(
                    plan.baselinePreconditions().entrySet()
            );
            preconditions.sort(AcceptedPlanFingerprint::compareCavePositions);
            addInt(preconditions.size());
            for (Map.Entry<CavePosition, CaveVoxelPrecondition> entry : preconditions) {
                add(entry.getKey());
                addEnum(entry.getValue().voxel());
                addBoolean(entry.getValue().openToSurface());
            }
            addBoolean(plan.arbitrationWinnerSourceId().isPresent());
            if (plan.arbitrationWinnerSourceId().isPresent()) {
                addLong(plan.arbitrationWinnerSourceId().getAsLong());
            }
        }

        private void add(HydrologyColumnSample column) {
            addInt(column.x());
            addInt(column.z());
            addInt(column.naturalHeight());
            addInt(column.seaLevel());
            addBoolean(column.ocean());
            addString(column.parentBiomeKey());
            ArrayList<HydrologyColumnLayer> layers = new ArrayList<>(column.layers());
            layers.sort(LAYER_ORDER);
            addInt(layers.size());
            for (HydrologyColumnLayer layer : layers) {
                add(layer);
            }
        }

        private void add(HydrologyColumnLayer layer) {
            add(layer.feature());
            addInt(layer.bedY());
            addInt(layer.fluidHeadY());
            addInt(layer.ceilingY());
            addBoolean(layer.channel());
            addBoolean(layer.shore());
            addBoolean(layer.grading());
            addBoolean(layer.connectedFluid());
            addBoolean(layer.fallingFluid());
            addBoolean(layer.receivingPool());
            addBoolean(layer.terrainOwned());
            addBoolean(layer.fluidOwned());
            addBoolean(layer.oceanApron());
            addString(layer.profileKey());
            addString(layer.surfaceBiomeKey());
            addString(layer.mouthBiomeKey());
            addString(layer.shoreBiomeKey());
            addString(layer.bankBiomeKey());
            addString(layer.floodedCaveBiomeKey());
        }

        private void add(HydrologyFeatureRef feature) {
            addLong(feature.id());
            addEnum(feature.type());
            addLong(feature.courseId());
            addLong(feature.segmentId());
            addInt(feature.x());
            addInt(feature.y());
            addInt(feature.z());
            addInt(feature.flowDeltaX());
            addInt(feature.flowDeltaZ());
            addBoolean(feature.source());
        }

        private void addPoints(List<HydrologyPoint> points) {
            addInt(points.size());
            for (HydrologyPoint point : points) {
                add(point);
            }
        }

        private void add(HydrologyPoint point) {
            addInt(point.x());
            addInt(point.y());
            addInt(point.z());
        }

        private void add(CavePosition position) {
            addInt(position.x());
            addInt(position.y());
            addInt(position.z());
        }

        private void addEnum(Enum<?> value) {
            addString(value.name());
        }

        private void addString(String value) {
            addInt(value.length());
            for (int index = 0; index < value.length(); index++) {
                addInt(value.charAt(index));
            }
        }

        private void addDouble(double value) {
            addLong(Double.doubleToLongBits(value));
        }

        private void addLong(long value) {
            for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
                addByte((int) (value >>> shift));
            }
        }

        private void addInt(int value) {
            for (int shift = 0; shift < Integer.SIZE; shift += Byte.SIZE) {
                addByte(value >>> shift);
            }
        }

        private void addBoolean(boolean value) {
            addByte(value ? 1 : 0);
        }

        private void addByte(int value) {
            hash ^= value & 0xffL;
            hash *= FNV_PRIME;
        }

        private static int compareCavePositions(
                Map.Entry<CavePosition, ?> first,
                Map.Entry<CavePosition, ?> second
        ) {
            return CAVE_POSITION_ORDER.compare(first, second);
        }
    }
}
