package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxel;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelPrecondition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelView;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCandidate;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlannerSettings;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HydrologyCaveCourseFilterTest {
    private static final int DEFAULT_GROTTO_MAXIMUM_VOLUME = 8192;
    private static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    @Test
    public void rejectedCaveCourseIsAbsentFromCoursesFootprintRendererAndLocator() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment ridge = new HydraulicSegment(
                22L,
                11L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(
                        new HydrologyPoint(0, 20, 8),
                        new HydrologyPoint(8, 20, 8),
                        new HydrologyPoint(16, 20, 8)
                ), HydraulicChannelProfile.uniform(2, 2)
        );
        RiverCourse course = course(ridge);
        HydrologyColumnLayer layer = layer(ridge, 8, 8);
        RiverFootprint footprint = footprint(terrain, layer);
        CaveVoxelView lavaView = view(CaveVoxel.LAVA, false);
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologyCaveCourseFilter.Result filtered = defaultCourseFilter(lavaView, true).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                footprint.columns().values(),
                diagnostics
        );
        HydrologyTile tile = new HydrologyTile(
                new HydrologyTileKey(0, 0),
                1L,
                1L,
                32,
                filtered.nodes(),
                filtered.edges(),
                filtered.outlets(),
                filtered.courses(),
                filtered.cavePlans(),
                diagnostics,
                RiverFootprint.empty()
        );

        assertTrue(tile.courses().isEmpty());
        assertTrue(tile.footprint().isEmpty());
        assertTrue(tile.features().isEmpty());
        assertTrue(tile.renderAt(8, 8).features().isEmpty());
        assertTrue(tile.nearestFeature(HydrologyFeatureType.UNDERGROUND_POOL, 8, 8, 16).isEmpty());
        assertEquals(1, tile.diagnosticCandidates().size());
        assertEquals(HydrologyCandidateRejection.CAVE_CONTAINMENT,
                tile.diagnosticCandidates().getFirst().rejection());
    }

    @Test
    public void generatedSurfaceConnectedCaveAroundAnUndergroundReachIsSealed() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment ridge = new HydraulicSegment(
                42L,
                41L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(
                        new HydrologyPoint(0, 20, 8),
                        new HydrologyPoint(8, 20, 8),
                        new HydrologyPoint(16, 20, 8)
                ), HydraulicChannelProfile.uniform(2, 2)
        );
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologyCaveCourseFilter.Result filtered = defaultCourseFilter(
                view(CaveVoxel.CAVE_AIR, true),
                true
        ).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course(ridge)),
                footprint(terrain, layer(ridge, 8, 8)).columns().values(),
                diagnostics
        );

        assertEquals(List.of(course(ridge)), filtered.courses());
        assertEquals(1, filtered.cavePlans().size());
        assertTrue(filtered.cavePlans().getFirst().actions().containsValue(
                HydrologyCaveAction.SEAL_GUARD
        ));
        assertTrue(filtered.cavePlans().getFirst().baselinePreconditions().values().stream()
                .anyMatch(CaveVoxelPrecondition::openToSurface));
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    public void exactCandidateGeometryIsReusedAndBoundedAcrossSettlementPasses() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment ridge = new HydraulicSegment(
                420L,
                410L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(
                        new HydrologyPoint(0, 20, 8),
                        new HydrologyPoint(8, 20, 8),
                        new HydrologyPoint(16, 20, 8)
                ), HydraulicChannelProfile.uniform(2, 2)
        );
        RiverCourse course = course(ridge);
        List<HydrologyColumnSample> columns = List.copyOf(
                footprint(terrain, layer(ridge, 8, 8)).columns().values()
        );
        AtomicInteger iterations = new AtomicInteger();
        Iterable<HydrologyColumnSample> countedColumns = () -> {
            iterations.incrementAndGet();
            return columns.iterator();
        };
        Map<HydrologyCaveCourseFilter.CandidateKey, HydrologyCaveCandidate> cache = new LinkedHashMap<>();
        HydrologyCaveCourseFilter filter = new HydrologyCaveCourseFilter(
                selectiveSurfaceView(Set.of(), new AtomicInteger()),
                new HydrologyCaveCourseFilter.Options(
                        true,
                        DEFAULT_GROTTO_MAXIMUM_VOLUME,
                        DEFAULT_GROTTO_MAXIMUM_VOLUME
                ),
                cache
        );

        HydrologyCaveCourseFilter.Result first = filter.filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                countedColumns,
                new ArrayList<>()
        );
        HydrologyCaveCourseFilter.Result repeated = filter.filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                countedColumns,
                new ArrayList<>()
        );

        assertEquals(first, repeated);
        assertEquals(2, iterations.get());
        assertEquals(1, cache.size());

        Map.Entry<HydrologyCaveCourseFilter.CandidateKey, HydrologyCaveCandidate> cached =
                cache.entrySet().iterator().next();
        long weight = (long) cached.getValue().actions().size()
                + cached.getValue().intentionalOpenings().size();
        assertTrue(weight > 1L);
        HydrologyCaveCourseFilter.CandidateKey secondKey = new HydrologyCaveCourseFilter.CandidateKey(
                cached.getKey().courseId() + 1L,
                cached.getKey().courseType(),
                cached.getKey().profileKey(),
                cached.getKey().segments(),
                cached.getKey().options()
        );
        HydrologyCaveCourseFilter.CandidateCache entryBounded =
                new HydrologyCaveCourseFilter.CandidateCache(1, weight * 2L);
        entryBounded.put(cached.getKey(), cached.getValue());
        entryBounded.put(secondKey, cached.getValue());
        assertEquals(1, entryBounded.size());
        assertEquals(weight, entryBounded.retainedPositions());
        assertTrue(entryBounded.containsKey(secondKey));

        HydrologyCaveCourseFilter.CandidateCache positionBounded =
                new HydrologyCaveCourseFilter.CandidateCache(2, weight - 1L);
        positionBounded.put(cached.getKey(), cached.getValue());
        assertTrue(positionBounded.isEmpty());
        assertEquals(0L, positionBounded.retainedPositions());
    }

    @Test
    public void undergroundReachAboveFinalTerrainRejectsTheWholeCourse() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment ridge = new HydraulicSegment(
                52L,
                51L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(
                        new HydrologyPoint(0, 20, 8),
                        new HydrologyPoint(8, 20, 8),
                        new HydrologyPoint(16, 20, 8)
                ), HydraulicChannelProfile.uniform(2, 2)
        );
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologyCaveCourseFilter.Result filtered = defaultCourseFilter(
                view(CaveVoxel.CAVE_AIR, true, true),
                true
        ).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course(ridge)),
                footprint(terrain, layer(ridge, 8, 8)).columns().values(),
                diagnostics
        );

        assertTrue(filtered.courses().isEmpty());
        assertEquals(1, diagnostics.size());
    }

    @Test
    public void surfaceExposurePreflightChecksTheFullActionBoundaryBeforeVoxelExpansion() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment ridge = new HydraulicSegment(
                152L,
                151L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(
                        new HydrologyPoint(0, 20, 8),
                        new HydrologyPoint(16, 20, 8)
                ), HydraulicChannelProfile.uniform(2, 2)
        );
        CavePosition exposedBoundary = new CavePosition(8, 25, 8);
        AtomicInteger voxelLoads = new AtomicInteger();
        CaveVoxelView exposedView = selectiveSurfaceView(Set.of(exposedBoundary), voxelLoads);
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologyCaveCourseFilter.Result filtered = defaultCourseFilter(exposedView, true).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course(ridge)),
                footprint(terrain, layer(ridge, 8, 8)).columns().values(),
                diagnostics
        );

        assertTrue(filtered.courses().isEmpty());
        assertTrue(filtered.cavePlans().isEmpty());
        assertEquals(0, voxelLoads.get());
        assertEquals(1, diagnostics.size());
        assertEquals(HydrologyCandidateRejection.CAVE_CONTAINMENT, diagnostics.getFirst().rejection());
    }

    @Test
    public void caveSurfaceOpeningExemptsItsCompleteSixNeighborShell() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment ridge = new HydraulicSegment(
                162L,
                161L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(
                        new HydrologyPoint(0, 20, 8),
                        new HydrologyPoint(8, 20, 8)
                ), HydraulicChannelProfile.uniform(2, 2)
        );
        RiverCourse course = course(ridge);
        RiverFootprint footprint = footprint(terrain, layer(ridge, 0, 8));
        CaveVoxelView exposedView = view(CaveVoxel.SOLID, false, true);
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        Set<Long> preflightRejections = defaultCourseFilter(exposedView, true)
                .preflightRejectedCourseIds(List.of(course), footprint.columns().values(), diagnostics);
        HydrologyCaveCourseFilter.Result filtered = defaultCourseFilter(exposedView, true).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                footprint.columns().values(),
                diagnostics
        );

        assertTrue(preflightRejections.isEmpty());
        assertEquals(List.of(course), filtered.courses());
        assertEquals(1, filtered.cavePlans().size());
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    public void undergroundDropInteriorIsNotExemptedByItsSurfaceOpenings() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment surfaceLip = new HydraulicSegment(
                172L,
                171L,
                HydrologyFeatureType.SURFACE_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 20, 0)), HydraulicChannelProfile.uniform(2, 2)
        );
        HydraulicSegment drop = new HydraulicSegment(
                173L,
                171L,
                HydrologyFeatureType.UNDERGROUND_DROP,
                20,
                18,
                2,
                2,
                true,
                true,
                List.of(
                        new HydrologyPoint(0, 20, 0),
                        new HydrologyPoint(6, 19, 0),
                        new HydrologyPoint(12, 18, 0)
                ), HydraulicChannelProfile.uniform(2, 2)
        );
        HydraulicSegment surfacePool = new HydraulicSegment(
                174L,
                171L,
                HydrologyFeatureType.SURFACE_POOL,
                18,
                18,
                2,
                2,
                false,
                false,
                List.of(new HydrologyPoint(12, 18, 0)), HydraulicChannelProfile.uniform(2, 2)
        );
        RiverCourse course = new RiverCourse(
                171L,
                RiverCourseType.SURFACE,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "water",
                1,
                List.of(),
                List.of(surfaceLip, drop, surfacePool)
        );
        HydrologyColumnLayer dropLayer = caveLayer(drop, 1731L, 6, 0, 16, 18, 22, true);
        RiverFootprint footprint = footprint(terrain, dropLayer);
        CavePosition exposedBoundary = new CavePosition(6, 23, 0);
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        Set<Long> preflightRejections = defaultCourseFilter(
                selectiveSurfaceView(Set.of(exposedBoundary), new AtomicInteger()),
                true
        ).preflightRejectedCourseIds(List.of(course), footprint.columns().values(), diagnostics);

        assertEquals(Set.of(course.id()), preflightRejections);
        assertEquals(1, diagnostics.size());
        assertEquals(HydrologyCandidateRejection.CAVE_CONTAINMENT, diagnostics.getFirst().rejection());
    }

    @Test
    public void undergroundExposurePreflightSkipsVoxelExpansion() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment tunnel = new HydraulicSegment(
                182L,
                181L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(
                        new HydrologyPoint(0, 20, 8),
                        new HydrologyPoint(8, 20, 8)
                ), HydraulicChannelProfile.uniform(2, 2)
        );
        RiverCourse course = undergroundCourse(tunnel);
        RiverFootprint footprint = footprint(terrain, layer(tunnel, 8, 8));
        AtomicInteger voxelLoads = new AtomicInteger();
        CaveVoxelView exposedView = selectiveSurfaceView(Set.of(new CavePosition(8, 20, 8)), voxelLoads);
        ArrayList<HydrologyDiagnosticCandidate> preflightDiagnostics = new ArrayList<>();

        Set<Long> preflightRejections = defaultCourseFilter(exposedView, true)
                .preflightRejectedCourseIds(
                        List.of(course),
                        footprint.columns().values(),
                        preflightDiagnostics
                );
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        HydrologyCaveCourseFilter.Result filtered = defaultCourseFilter(exposedView, true).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                footprint.columns().values(),
                diagnostics
        );

        assertEquals(Set.of(course.id()), preflightRejections);
        assertEquals(1, preflightDiagnostics.size());
        assertTrue(filtered.courses().isEmpty());
        assertTrue(filtered.cavePlans().isEmpty());
        assertEquals(0, voxelLoads.get());
        assertEquals(1, diagnostics.size());
        assertEquals(HydrologyCandidateRejection.CAVE_CONTAINMENT, diagnostics.getFirst().rejection());
    }

    @Test
    public void deepFluidExposurePreflightSkipsVoxelExpansion() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment pool = new HydraulicSegment(
                188L,
                187L,
                HydrologyFeatureType.DEEP_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(
                        new HydrologyPoint(0, 20, 8),
                        new HydrologyPoint(8, 20, 8)
                ), HydraulicChannelProfile.uniform(2, 2)
        );
        RiverCourse course = new RiverCourse(
                pool.courseId(),
                RiverCourseType.DEEP_FLUID,
                OptionalLong.empty(),
                OptionalLong.empty(),
                "water",
                1,
                List.of(),
                List.of(pool)
        );
        RiverFootprint footprint = footprint(terrain, layer(pool, 8, 8));
        AtomicInteger voxelLoads = new AtomicInteger();
        CaveVoxelView exposedView = selectiveSurfaceView(Set.of(new CavePosition(8, 20, 8)), voxelLoads);
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologyCaveCourseFilter.Result filtered = defaultCourseFilter(exposedView, true).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                footprint.columns().values(),
                diagnostics
        );

        assertTrue(filtered.courses().isEmpty());
        assertTrue(filtered.cavePlans().isEmpty());
        assertEquals(0, voxelLoads.get());
        assertEquals(1, diagnostics.size());
        assertEquals(HydrologyCandidateRejection.CAVE_CONTAINMENT, diagnostics.getFirst().rejection());
    }

    @Test
    public void compactSurfaceExposureMatchesLiteralVoxelShells() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment ridge = new HydraulicSegment(
                192L,
                191L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(
                        new HydrologyPoint(0, 20, 0),
                        new HydrologyPoint(12, 20, 0)
                ), HydraulicChannelProfile.uniform(2, 2)
        );
        RiverCourse course = course(ridge);
        Random random = new Random(0x485944524f4c4f47L);
        int exposedCases = 0;
        int containedCases = 0;

        for (int iteration = 0; iteration < 128; iteration++) {
            LinkedHashMap<Long, HydrologyColumnSample> columns = new LinkedHashMap<>();
            HashSet<CavePosition> actions = new HashSet<>();
            HashSet<CavePosition> openings = new HashSet<>();
            addRandomColumn(terrain, ridge, iteration, 0, 0, random, columns, actions, openings);
            addRandomColumn(terrain, ridge, iteration, 6, 0, random, columns, actions, openings);
            int randomColumns = 1 + random.nextInt(10);
            for (int index = 0; index < randomColumns; index++) {
                int x = random.nextInt(15) - 2;
                int z = random.nextInt(7) - 3;
                addRandomColumn(terrain, ridge, iteration, x, z, random, columns, actions, openings);
            }
            HashSet<CavePosition> exposedPositions = new HashSet<>();
            if (!openings.isEmpty()) {
                exposedPositions.add(openings.iterator().next());
            }
            if ((iteration & 1) != 0) {
                List<CavePosition> shell = literalShell(actions, openings);
                exposedPositions.add(shell.get(random.nextInt(shell.size())));
            }
            CaveVoxelView view = selectiveSurfaceView(exposedPositions, new AtomicInteger());
            boolean expectedExposed = literalExposure(actions, openings, view);
            Set<Long> actualRejections = defaultCourseFilter(view, true).preflightRejectedCourseIds(
                    List.of(course),
                    new RiverFootprint(columns).columns().values(),
                    new ArrayList<>()
            );

            assertEquals(expectedExposed, actualRejections.contains(course.id()));
            if (expectedExposed) {
                exposedCases++;
            } else {
                containedCases++;
            }
        }

        assertEquals(64, exposedCases);
        assertEquals(64, containedCases);
    }

    @Test
    public void pureSurfaceCourseBypassesCaveContainmentWithoutChangingItsPlan() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment surface = new HydraulicSegment(
                62L,
                61L,
                HydrologyFeatureType.SURFACE_POOL,
                76,
                76,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(8, 76, 8)), HydraulicChannelProfile.uniform(4, 2)
        );
        RiverCourse course = course(surface);
        RiverFootprint footprint = footprint(terrain, surfaceLayer(surface, 8, 8));
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologyCaveCourseFilter.Result filtered = defaultCourseFilter(
                view(CaveVoxel.LAVA, true),
                true
        ).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                footprint.columns().values(),
                diagnostics
        );

        assertEquals(List.of(course), filtered.courses());
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    public void acceptedCaveCourseRetainsItsExactPlanOnTheImmutableTile() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment ridge = new HydraulicSegment(
                82L,
                81L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(
                        new HydrologyPoint(0, 20, 8),
                        new HydrologyPoint(8, 20, 8),
                        new HydrologyPoint(16, 20, 8)
                ), HydraulicChannelProfile.uniform(2, 2)
        );
        RiverFootprint footprint = footprint(terrain, layer(ridge, 8, 8));
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        DrainageNode source = new DrainageNode(1L, 0, 8, terrain, 1D, 2L);
        RiverOutlet outlet = new RiverOutlet(
                2L,
                HydrologyFeatureType.MOUTH,
                source.id(),
                new HydrologyPoint(0, 63, 8),
                new HydrologyPoint(1, 63, 8),
                63,
                true
        );

        HydrologyCaveCourseFilter.Result filtered = defaultCourseFilter(
                view(CaveVoxel.SOLID, false),
                true
        ).filter(
                List.of(source),
                List.of(),
                List.of(outlet),
                List.of(course(ridge)),
                footprint.columns().values(),
                diagnostics
        );
        HydrologyTile tile = new HydrologyTile(
                new HydrologyTileKey(0, 0),
                1L,
                1L,
                32,
                filtered.nodes(),
                filtered.edges(),
                filtered.outlets(),
                filtered.courses(),
                filtered.cavePlans(),
                diagnostics,
                footprint
        );

        assertEquals(1, tile.courses().size());
        assertEquals(1, tile.cavePlans().size());
        assertTrue(tile.cavePlan(ridge.courseId()).orElseThrow().accepted());
        assertEquals(1, tile.renderAt(8, 8).features().size());
    }

    @Test
    public void coastalGrottoMaximumVolumeBoundsOnlyTheCoastalPlan() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment grotto = grotto(121L, 122L, HydrologyFeatureType.COASTAL_GROTTO);
        RiverCourse course = course(grotto);
        RiverFootprint footprint = footprint(terrain, layer(grotto, 8, 8));
        CaveVoxelView solidView = view(CaveVoxel.SOLID, false);
        ArrayList<HydrologyDiagnosticCandidate> acceptedDiagnostics = new ArrayList<>();

        HydrologyCaveCourseFilter.Result accepted = limitedCourseFilter(solidView, 6, 1).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                footprint.columns().values(),
                acceptedDiagnostics
        );
        ArrayList<HydrologyDiagnosticCandidate> rejectedDiagnostics = new ArrayList<>();
        HydrologyCaveCourseFilter.Result rejected = limitedCourseFilter(solidView, 5, 6).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                footprint.columns().values(),
                rejectedDiagnostics
        );

        assertEquals(List.of(course), accepted.courses());
        assertEquals(1, accepted.cavePlans().size());
        assertTrue(accepted.cavePlans().getFirst().accepted());
        assertTrue(acceptedDiagnostics.isEmpty());
        assertTrue(rejected.courses().isEmpty());
        assertTrue(rejected.cavePlans().isEmpty());
        assertEquals(1, rejectedDiagnostics.size());
        assertEquals(HydrologyCandidateRejection.VOLUME_LIMIT, rejectedDiagnostics.getFirst().rejection());
    }

    @Test
    public void oversizedGrottoPrecedesSurfaceExposure() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment grotto = grotto(123L, 124L, HydrologyFeatureType.COASTAL_GROTTO);
        RiverCourse course = course(grotto);
        RiverFootprint footprint = footprint(
                terrain,
                layer(grotto, 8, 8),
                layer(grotto, 12, 8)
        );
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologyCaveCourseFilter.Result filtered = limitedCourseFilter(
                view(CaveVoxel.SOLID, false, true),
                11,
                DEFAULT_GROTTO_MAXIMUM_VOLUME
        ).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                footprint.columns().values(),
                diagnostics
        );

        assertTrue(filtered.courses().isEmpty());
        assertEquals(1, diagnostics.size());
        assertEquals(HydrologyCandidateRejection.VOLUME_LIMIT, diagnostics.getFirst().rejection());
    }

    @Test
    public void inlandGrottoMaximumVolumeBoundsOnlyTheInlandPlan() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment grotto = grotto(131L, 132L, HydrologyFeatureType.INLAND_GROTTO);
        RiverCourse course = undergroundCourse(grotto);
        RiverFootprint footprint = footprint(terrain, layer(grotto, 8, 8));
        CaveVoxelView solidView = view(CaveVoxel.SOLID, false);
        ArrayList<HydrologyDiagnosticCandidate> acceptedDiagnostics = new ArrayList<>();

        HydrologyCaveCourseFilter.Result accepted = limitedCourseFilter(solidView, 1, 6).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                footprint.columns().values(),
                acceptedDiagnostics
        );
        ArrayList<HydrologyDiagnosticCandidate> rejectedDiagnostics = new ArrayList<>();
        HydrologyCaveCourseFilter.Result rejected = limitedCourseFilter(solidView, 6, 5).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                footprint.columns().values(),
                rejectedDiagnostics
        );

        assertEquals(List.of(course), accepted.courses());
        assertEquals(1, accepted.cavePlans().size());
        assertTrue(accepted.cavePlans().getFirst().accepted());
        assertTrue(acceptedDiagnostics.isEmpty());
        assertTrue(rejected.courses().isEmpty());
        assertTrue(rejected.cavePlans().isEmpty());
        assertEquals(1, rejectedDiagnostics.size());
        assertEquals(HydrologyCandidateRejection.VOLUME_LIMIT, rejectedDiagnostics.getFirst().rejection());
    }

    @Test
    public void grottoCapDoesNotLimitTheRestOfItsTransactionalCourse() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment tunnel = new HydraulicSegment(
                141L,
                140L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 20, 8), new HydrologyPoint(1, 20, 8)), HydraulicChannelProfile.uniform(2, 2)
        );
        HydraulicSegment grotto = grotto(142L, 140L, HydrologyFeatureType.INLAND_GROTTO);
        RiverCourse course = new RiverCourse(
                140L,
                RiverCourseType.UNDERGROUND,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "water",
                1,
                List.of(),
                List.of(tunnel, grotto)
        );
        RiverFootprint footprint = footprint(
                terrain,
                layer(tunnel, 0, 8),
                layer(tunnel, 1, 8),
                layer(grotto, 8, 8)
        );
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologyCaveCourseFilter.Result filtered = limitedCourseFilter(
                view(CaveVoxel.SOLID, false),
                1,
                6
        ).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                footprint.columns().values(),
                diagnostics
        );

        assertEquals(List.of(course), filtered.courses());
        assertEquals(1, filtered.cavePlans().size());
        assertTrue(filtered.cavePlans().getFirst().actions().size() > 6);
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    public void oversizedTransactionalCourseRejectsBeforeVoxelMaterialization() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment tunnel = new HydraulicSegment(
                151L,
                150L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(new HydrologyPoint(8, 20, 8), new HydrologyPoint(9, 20, 8)), HydraulicChannelProfile.uniform(2, 2)
        );
        RiverCourse course = undergroundCourse(tunnel);
        HydrologyColumnLayer oversized = caveLayer(
                tunnel,
                152L,
                8,
                8,
                0,
                20,
                HydrologyCavePlannerSettings.MAXIMUM_PLANNED_MUTATIONS + 1,
                false
        );
        RiverFootprint footprint = footprint(terrain, oversized);
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
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologyCaveCourseFilter.Result filtered = defaultCourseFilter(view, false).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                footprint.columns().values(),
                diagnostics
        );

        assertTrue(filtered.courses().isEmpty());
        assertTrue(filtered.cavePlans().isEmpty());
        assertEquals(1, diagnostics.size());
        assertEquals(HydrologyCandidateRejection.VOLUME_LIMIT, diagnostics.getFirst().rejection());
        assertEquals(0, voxelLoads.get());
    }

    @Test
    public void selfCarvedUndergroundCourseChecksTerrainWithoutLoadingExistingCaves() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment tunnel = new HydraulicSegment(
                161L,
                160L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(new HydrologyPoint(8, 20, 8), new HydrologyPoint(9, 20, 8)), HydraulicChannelProfile.uniform(2, 2)
        );
        RiverCourse course = undergroundCourse(tunnel);
        RiverFootprint footprint = footprint(terrain, layer(tunnel, 8, 8), layer(tunnel, 9, 8));
        AtomicInteger observedLoads = new AtomicInteger();
        AtomicInteger terrainChecks = new AtomicInteger();
        CaveVoxelView observedView = new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                terrainChecks.incrementAndGet();
                return true;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                observedLoads.incrementAndGet();
                return CaveVoxel.SOLID;
            }

            @Override
            public boolean isOpenToSurface(CavePosition position) {
                observedLoads.incrementAndGet();
                return false;
            }

            @Override
            public boolean isAboveTerrainSurface(CavePosition position) {
                terrainChecks.incrementAndGet();
                return false;
            }
        };
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologyCaveCourseFilter.Result filtered = defaultCourseFilter(observedView, false).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                footprint.columns().values(),
                diagnostics
        );

        assertEquals(List.of(course), filtered.courses());
        assertEquals(1, filtered.cavePlans().size());
        assertTrue(filtered.cavePlans().getFirst().accepted());
        assertTrue(filtered.cavePlans().getFirst().actions().values().stream()
                .noneMatch((HydrologyCaveAction action) -> action == HydrologyCaveAction.SEAL_GUARD));
        assertTrue(filtered.cavePlans().getFirst().baselinePreconditions().values().stream()
                .allMatch((CaveVoxelPrecondition precondition) -> precondition.voxel() == CaveVoxel.UNCONDITIONAL));
        assertTrue(diagnostics.isEmpty());
        assertEquals(0, observedLoads.get());
        assertTrue(terrainChecks.get() > 0);
    }

    @Test
    public void ridgeBoreCanUseTheAdjacentUndergroundSegmentsSurfaceOpening() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment ridge = new HydraulicSegment(
                102L,
                101L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(
                        new HydrologyPoint(0, 20, 8),
                        new HydrologyPoint(8, 20, 8)
                ), HydraulicChannelProfile.uniform(2, 2)
        );
        HydraulicSegment drop = new HydraulicSegment(
                103L,
                101L,
                HydrologyFeatureType.UNDERGROUND_DROP,
                20,
                18,
                2,
                2,
                true,
                true,
                List.of(
                        new HydrologyPoint(8, 20, 8),
                        new HydrologyPoint(9, 18, 8)
                ), HydraulicChannelProfile.uniform(2, 2)
        );
        HydraulicSegment surface = new HydraulicSegment(
                104L,
                101L,
                HydrologyFeatureType.SURFACE_POOL,
                18,
                18,
                2,
                2,
                false,
                false,
                List.of(new HydrologyPoint(9, 18, 8)), HydraulicChannelProfile.uniform(2, 2)
        );
        RiverCourse course = new RiverCourse(
                101L,
                RiverCourseType.SURFACE,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "water",
                1,
                List.of(),
                List.of(ridge, drop, surface)
        );
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologyCaveCourseFilter.Result filtered = defaultCourseFilter(
                view(CaveVoxel.CAVE_AIR, true),
                true
        ).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                footprint(terrain, layer(ridge, 8, 8)).columns().values(),
                diagnostics
        );

        assertEquals(List.of(course), filtered.courses());
        assertEquals(1, filtered.cavePlans().size());
        assertTrue(filtered.cavePlans().getFirst().accepted());
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    public void remoteCrossChunkHazardRejectsEveryCellOfTheCourse() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0D, "parent");
        HydraulicSegment ridge = new HydraulicSegment(
                92L,
                91L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(
                        new HydrologyPoint(8, 20, 8),
                        new HydrologyPoint(24, 20, 8)
                ), HydraulicChannelProfile.uniform(2, 2)
        );
        HydrologyColumnLayer local = layer(ridge, 15, 8);
        HydrologyColumnLayer remote = layer(ridge, 16, 8);
        RiverFootprint footprint = footprint(terrain, local, remote);
        CavePosition hazard = new CavePosition(16, 19, 8);
        CaveVoxelView view = selectiveView(Map.of(hazard, CaveVoxel.INCOMPATIBLE_FLUID));
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologyCaveCourseFilter.Result filtered = defaultCourseFilter(view, true).filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(course(ridge)),
                footprint.columns().values(),
                diagnostics
        );

        assertTrue(filtered.courses().isEmpty());
        assertTrue(filtered.cavePlans().isEmpty());
        assertEquals(1, diagnostics.size());
    }

    @Test
    public void seaCaveIsContainedWithItsSeaFaceAsTheOnlyOpening() {
        HydrologyTerrainSampler coast = (int x, int z) -> {
            if (x >= 112) {
                return HydrologyTerrainSample.ocean(48, "ocean");
            }
            if (z >= 9) {
                return HydrologyTerrainSample.openLand(64, 0D, "beach");
            }
            if (x >= 110) {
                return HydrologyTerrainSample.openLand(74, 0D, "face");
            }
            if (x >= 104) {
                return HydrologyTerrainSample.openLand(66, 0D, "low");
            }
            return HydrologyTerrainSample.openLand(92, 0D, "cliff");
        };
        HydraulicSegment chamber = new HydraulicSegment(
                921L,
                920L,
                HydrologyFeatureType.COASTAL_GROTTO,
                63,
                63,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(100, 63, 0), new HydrologyPoint(112, 63, 0)), HydraulicChannelProfile.uniform(4, 2)
        );
        RiverCourse seaCave = new RiverCourse(
                920L,
                RiverCourseType.SEA_CAVE,
                OptionalLong.empty(),
                OptionalLong.empty(),
                "water",
                1,
                List.of(),
                List.of(chamber)
        );
        RiverFootprint footprint = new HydrologyFootprintCompiler(
                seaCaveSettings(),
                coast,
                request -> request.minimum()
        ).compile(List.of(seaCave));
        HydrologyTerrainCaveVoxelView view = new HydrologyTerrainCaveVoxelView(coast, 63, -64, 320);
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologyCaveCourseFilter terrainFilter = new HydrologyCaveCourseFilter(
                view,
                new HydrologyCaveCourseFilter.Options(true, 32768, 32768)
        );
        HydrologyCaveCourseFilter.Result result = terrainFilter.filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(seaCave),
                footprint.columns().values(),
                diagnostics
        );

        assertEquals("diagnostics=" + diagnostics, List.of(seaCave), result.courses());
        assertTrue(diagnostics.isEmpty());
        assertEquals(1, result.cavePlans().size());
        HydrologyCavePlan plan = result.cavePlans().getFirst();
        assertTrue(plan.accepted());
        assertEquals(920L, plan.source().sourceId());
        AtomicInteger seaOpenings = new AtomicInteger();
        plan.forEachPrecondition((CavePosition position, CaveVoxelPrecondition precondition) -> {
            if (position.x() >= 112
                    && position.y() > 48
                    && position.y() <= 63
                    && precondition.voxel() == CaveVoxel.COMPATIBLE_FLUID) {
                seaOpenings.incrementAndGet();
            }
        });
        assertTrue(seaOpenings.get() > 0);
        plan.forEachAction((CavePosition position, HydrologyCaveAction action) -> {
            int naturalHeight = coast.sample(position.x(), position.z()).naturalHeight();
            assertTrue(
                    "carve above the surface at " + position + " " + action,
                    action == HydrologyCaveAction.SEAL_GUARD ? position.y() <= naturalHeight : position.y() < naturalHeight
            );
        });

        ArrayList<HydrologyColumnSample> sealed = new ArrayList<>();
        for (HydrologyColumnSample sample : footprint.columns().values()) {
            List<HydrologyColumnLayer> kept = sample.layers().stream()
                    .filter((HydrologyColumnLayer layer) -> !layer.oceanApron())
                    .toList();
            if (!kept.isEmpty()) {
                sealed.add(new HydrologyColumnSample(
                        sample.x(),
                        sample.z(),
                        sample.naturalHeight(),
                        sample.seaLevel(),
                        sample.ocean(),
                        sample.parentBiomeKey(),
                        kept
                ));
            }
        }
        ArrayList<HydrologyDiagnosticCandidate> sealedDiagnostics = new ArrayList<>();
        HydrologyCaveCourseFilter.Result control = terrainFilter.filter(
                List.of(),
                List.of(),
                List.of(),
                List.of(seaCave),
                sealed,
                sealedDiagnostics
        );

        assertTrue(control.courses().isEmpty());
        assertTrue(control.cavePlans().isEmpty());
        assertEquals(1, sealedDiagnostics.size());
        assertEquals(HydrologyCandidateRejection.CAVE_CONTAINMENT, sealedDiagnostics.getFirst().rejection());
    }

    private static HydrologyPlannerSettings seaCaveSettings() {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Outlets outlets = HydrologyPlannerSettings.Outlets.of(
                true,
                new HydrologyPlannerSettings.Grotto(true, 12, 7, 10, 32768),
                base.outlets().inlandGrotto(),
                base.outlets().surfaceSinkholesEnabled(),
                base.outlets().coastalCliffMinimumHeight(),
                base.outlets().mouthLevelingDistance(),
                8,
                base.outlets().maximumPerTile(),
                base.outlets().maximumPerTile()
        );
        HydrologyPlannerSettings.Geometry geometry = new HydrologyPlannerSettings.Geometry(
                base.geometry().meanders(),
                base.geometry().surface(),
                base.geometry().underground(),
                HydrologyPlannerSettings.ChannelShape.of(2.4D, 0D, 0D, 11),
                base.geometry().drops()
        );
        return new HydrologyPlannerSettings(
                base.seaLevel(),
                base.routing(),
                base.surface(),
                base.hydraulics(),
                base.underground(),
                outlets,
                geometry,
                base.deepFluids(),
                base.surfacePools(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private RiverCourse course(HydraulicSegment segment) {
        return new RiverCourse(
                segment.courseId(),
                RiverCourseType.SURFACE,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "water",
                1,
                List.of(),
                List.of(segment)
        );
    }

    private RiverCourse undergroundCourse(HydraulicSegment segment) {
        return new RiverCourse(
                segment.courseId(),
                RiverCourseType.UNDERGROUND,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "water",
                1,
                List.of(),
                List.of(segment)
        );
    }

    private HydraulicSegment grotto(long segmentId, long courseId, HydrologyFeatureType type) {
        return new HydraulicSegment(
                segmentId,
                courseId,
                type,
                20,
                20,
                2,
                2,
                false,
                false,
                List.of(new HydrologyPoint(8, 20, 8)), HydraulicChannelProfile.uniform(2, 2)
        );
    }

    private HydrologyCaveCourseFilter defaultCourseFilter(CaveVoxelView view, boolean connectToExistingCaves) {
        return new HydrologyCaveCourseFilter(
                view,
                new HydrologyCaveCourseFilter.Options(
                        connectToExistingCaves,
                        DEFAULT_GROTTO_MAXIMUM_VOLUME,
                        DEFAULT_GROTTO_MAXIMUM_VOLUME
                )
        );
    }

    private HydrologyCaveCourseFilter limitedCourseFilter(
            CaveVoxelView view,
            int coastalGrottoMaximumVolume,
            int inlandGrottoMaximumVolume
    ) {
        return new HydrologyCaveCourseFilter(
                view,
                new HydrologyCaveCourseFilter.Options(
                        false,
                        coastalGrottoMaximumVolume,
                        inlandGrottoMaximumVolume
                )
        );
    }

    private HydrologyColumnLayer layer(HydraulicSegment segment, int x, int z) {
        return new HydrologyColumnLayer(
                feature(segment, x, z),
                18,
                20,
                24,
                true,
                false,
                false,
                true,
                false,
                false,
                true,
                true,
                false,
                "water",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded"
        );
    }

    private HydrologyColumnLayer caveLayer(
            HydraulicSegment segment,
            long featureId,
            int x,
            int z,
            int bedY,
            int fluidHeadY,
            int ceilingY,
            boolean fallingFluid
    ) {
        return new HydrologyColumnLayer(
                feature(segment, featureId, x, z),
                bedY,
                fluidHeadY,
                ceilingY,
                true,
                false,
                false,
                true,
                fallingFluid,
                fallingFluid,
                true,
                true,
                false,
                "water",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded"
        );
    }

    private HydrologyColumnLayer surfaceLayer(HydraulicSegment segment, int x, int z) {
        return new HydrologyColumnLayer(
                feature(segment, x, z),
                74,
                76,
                76,
                true,
                false,
                false,
                true,
                false,
                false,
                true,
                true,
                false,
                "water",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded"
        );
    }

    private HydrologyFeatureRef feature(HydraulicSegment segment, int x, int z) {
        return feature(segment, 101L, x, z);
    }

    private HydrologyFeatureRef feature(HydraulicSegment segment, long featureId, int x, int z) {
        return new HydrologyFeatureRef(
                featureId,
                segment.type(),
                segment.courseId(),
                segment.id(),
                x,
                segment.upstreamHeadY(),
                z,
                1,
                0,
                false
        );
    }

    private RiverFootprint footprint(HydrologyTerrainSample terrain, HydrologyColumnLayer... layers) {
        Map<Long, HydrologyColumnSample> columns = new LinkedHashMap<>();
        for (HydrologyColumnLayer layer : layers) {
            HydrologyColumnSample sample = new HydrologyColumnSample(
                    layer.feature().x(),
                    layer.feature().z(),
                    terrain.naturalHeight(),
                    63,
                    false,
                    terrain.parentBiomeKey(),
                    List.of(layer)
            );
            columns.put(RiverFootprint.pack(sample.x(), sample.z()), sample);
        }
        return new RiverFootprint(columns);
    }

    private void addRandomColumn(
            HydrologyTerrainSample terrain,
            HydraulicSegment ridge,
            int iteration,
            int x,
            int z,
            Random random,
            Map<Long, HydrologyColumnSample> columns,
            Set<CavePosition> actions,
            Set<CavePosition> openings
    ) {
        long columnKey = RiverFootprint.pack(x, z);
        if (columns.containsKey(columnKey)) {
            return;
        }
        int bedY = 14 + random.nextInt(6);
        int ceilingY = 20 + random.nextInt(6);
        long featureId = 10_000L + (long) iteration * 1_000L + (long) (x + 16) * 32L + z + 16L;
        HydrologyColumnLayer layer = caveLayer(
                ridge,
                featureId,
                x,
                z,
                bedY,
                20,
                ceilingY,
                false
        );
        columns.put(columnKey, new HydrologyColumnSample(
                x,
                z,
                terrain.naturalHeight(),
                63,
                false,
                terrain.parentBiomeKey(),
                List.of(layer)
        ));
        for (int y = bedY + 1; y <= ceilingY; y++) {
            CavePosition position = new CavePosition(x, y, z);
            actions.add(position);
            if (matchesRidgeOpening(x, z)) {
                addOpeningNeighborhood(position, openings);
            }
        }
    }

    private boolean matchesRidgeOpening(int x, int z) {
        double progress = Math.max(0D, Math.min(1D, x / 2D));
        double deltaX = x - 2D * progress;
        return deltaX * deltaX + (double) z * z <= 4D;
    }

    private void addOpeningNeighborhood(CavePosition position, Set<CavePosition> openings) {
        openings.add(position);
        for (int[] offset : NEIGHBORS) {
            openings.add(position.offset(offset[0], offset[1], offset[2]));
        }
    }

    private List<CavePosition> literalShell(Set<CavePosition> actions, Set<CavePosition> openings) {
        HashSet<CavePosition> shell = new HashSet<>();
        for (CavePosition position : actions) {
            if (!openings.contains(position)) {
                shell.add(position);
            }
            for (int[] offset : NEIGHBORS) {
                CavePosition neighbor = position.offset(offset[0], offset[1], offset[2]);
                if (!actions.contains(neighbor) && !openings.contains(neighbor)) {
                    shell.add(neighbor);
                }
            }
        }
        return List.copyOf(shell);
    }

    private boolean literalExposure(
            Set<CavePosition> actions,
            Set<CavePosition> openings,
            CaveVoxelView view
    ) {
        for (CavePosition position : actions) {
            if (!openings.contains(position)
                    && view.isInWorld(position)
                    && view.isAboveTerrainSurface(position)) {
                return true;
            }
            for (int[] offset : NEIGHBORS) {
                CavePosition neighbor = position.offset(offset[0], offset[1], offset[2]);
                if (actions.contains(neighbor)
                        || openings.contains(neighbor)
                        || !view.isInWorld(neighbor)) {
                    continue;
                }
                if (view.isAboveTerrainSurface(neighbor)) {
                    return true;
                }
            }
        }
        return false;
    }

    private CaveVoxelView view(CaveVoxel voxel, boolean openToSurface) {
        return view(voxel, openToSurface, false);
    }

    private CaveVoxelView view(CaveVoxel voxel, boolean openToSurface, boolean aboveTerrainSurface) {
        return new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                return position.y() > 0 && position.y() < 128;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                return voxel;
            }

            @Override
            public boolean isOpenToSurface(CavePosition position) {
                return openToSurface;
            }

            @Override
            public boolean isAboveTerrainSurface(CavePosition position) {
                return aboveTerrainSurface;
            }
        };
    }

    private CaveVoxelView selectiveView(Map<CavePosition, CaveVoxel> voxels) {
        return new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                return position.y() > 0 && position.y() < 128;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                return voxels.getOrDefault(position, CaveVoxel.SOLID);
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
    }

    private CaveVoxelView selectiveSurfaceView(
            Set<CavePosition> exposedPositions,
            AtomicInteger voxelLoads
    ) {
        return new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                return position.y() > 0 && position.y() < 128;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                voxelLoads.incrementAndGet();
                return CaveVoxel.SOLID;
            }

            @Override
            public boolean isOpenToSurface(CavePosition position) {
                return false;
            }

            @Override
            public boolean isAboveTerrainSurface(CavePosition position) {
                return exposedPositions.contains(position);
            }
        };
    }
}
