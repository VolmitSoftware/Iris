package art.arcane.iris.world.history;

import art.arcane.iris.testsupport.DurabilityMode;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TransitionGenerationPlanTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void interpolatesPresentAndAbsentUpperDepthUsingTheSameBoundedNeighbours() throws Exception {
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(
                temporaryFolder.newFolder("upper-depth-neighbours").toPath());
        TerrainBoundarySignature.Samples samples = new TerrainBoundarySignature.Samples(
                new TerrainBoundarySignature.VerticalLayout(-64, 4, 0),
                new TerrainBoundarySignature.BiomeEncoding(List.of(), new short[0]));
        TerrainBoundarySignature present = new TerrainBoundarySignature(
                new TerrainBoundarySignature.Column(15, 0, 64, 64, OptionalInt.empty(), OptionalInt.of(100)), samples, BoundaryColumnGeometry.empty());
        TerrainBoundarySignature absent = new TerrainBoundarySignature(
                new TerrainBoundarySignature.Column(15, 2, 64, 64, OptionalInt.empty(), OptionalInt.empty()), samples, BoundaryColumnGeometry.empty());
        TransitionBoundarySampler sampler = new TransitionBoundarySampler(32, store.publish(2L, List.of(present, absent)));

        assertEquals(100D, sampler.sample(16, 0).historicalUpperCeilingDepth(), 0D);
        assertEquals(0D, sampler.sample(16, 2).historicalUpperCeilingDepth(), 0D);
        assertEquals(50D, sampler.sample(17, 1).historicalUpperCeilingDepth(), 0D);
        assertEquals(1L, sampler.candidateBuildCount());
    }

    @Test
    public void bindsImmutableActivationEpochWidthAndBoundaryInputs() throws Exception {
        GenerationBoundary boundary = GenerationBoundary.freeze("frontier-sha256", List.of(
                new GenerationBoundary.ChunkCoordinate(0, 0)
        ));
        TerrainBoundarySignatureStore.Snapshot snapshot = snapshot(9L, boundary);
        TransitionGenerationPlan.Specification specification = new TransitionGenerationPlan.Specification(
                9L,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                GenerationTransition.CURRENT_ALGORITHM_VERSION,
                32,
                "frontier-sha256",
                snapshot.identity()
        );

        TransitionGenerationPlan plan = new TransitionGenerationPlan(specification, boundary, snapshot);

        assertEquals(9L, plan.activationId());
        assertEquals(specification.oldEpochId(), plan.oldEpochId());
        assertEquals(specification.newEpochId(), plan.newEpochId());
        assertEquals(32, plan.widthBlocks());
        assertEquals(GenerationTransition.CURRENT_ALGORITHM_VERSION, plan.algorithmVersion());
        assertEquals("frontier-sha256", plan.boundaryIdentity());
        assertEquals(snapshot.identity(), plan.terrainSignatures().identity());
        assertTrue(plan.isHistoricalBlock(15, 15));
        assertEquals(0D, plan.newEpochWeightAt(15, 15), 0D);
        assertEquals(1D, plan.newEpochWeightAt(47, 15), 0D);
    }

    @Test
    public void computesTransitionForEnclosedNewTerritory() throws Exception {
        GenerationBoundary boundary = GenerationBoundary.freeze("ring", List.of(
                new GenerationBoundary.ChunkCoordinate(-1, 0),
                new GenerationBoundary.ChunkCoordinate(1, 0),
                new GenerationBoundary.ChunkCoordinate(0, -1),
                new GenerationBoundary.ChunkCoordinate(0, 1)
        ));
        TerrainBoundarySignatureStore.Snapshot snapshot = snapshot(2L, boundary);
        TransitionGenerationPlan plan = new TransitionGenerationPlan(
                specification(2L, "old", "new", 16, "ring", snapshot.identity()),
                boundary,
                snapshot
        );

        assertEquals(8D, plan.distanceToHistoricalChunks(8, 8), 0D);
        assertEquals(0.5D, plan.newEpochWeightAt(8, 8), 0D);
    }

    @Test
    public void samplesTerrainAndHydrologyWithinTheSameFiniteWidth() throws Exception {
        GenerationBoundary boundary = GenerationBoundary.freeze("terrain", List.of(
                new GenerationBoundary.ChunkCoordinate(0, 0)
        ));
        TerrainBoundarySignatureStore.Snapshot snapshot = snapshot(3L, boundary);
        TransitionGenerationPlan plan = new TransitionGenerationPlan(
                specification(3L, "old", "new", 32, "terrain", snapshot.identity()),
                boundary,
                snapshot
        );

        TransitionGenerationPlan.TerrainSample edge = plan.terrainSampleAt(16, 8);
        assertNotNull(edge.nearestSignature());
        assertEquals(1D, edge.distanceToHistoricalTerrain(), 0D);
        assertEquals(64D, edge.historicalSurfaceHeight(), 0D);
        assertEquals(edge.newEpochWeight(), edge.hydrologyWeight(), 0D);
        assertTrue(edge.newEpochWeight() > 0D);
        assertTrue(edge.newEpochWeight() < 0.001D);

        assertEquals(1D, plan.newEpochWeightAt(47, 8), 0D);
        assertEquals(1D, plan.hydrologyWeightAt(47, 8), 0D);
        assertEquals(1D, plan.hydrologyWeightAt(63, 8), 0D);
        assertEquals(1D, plan.hydrologyWeightAt(79, 8), 0D);
        assertTrue(plan.allowsNewFootprint(31, 8, 48, 8));
        assertTrue(plan.allowsNewFootprint(47, 8, 63, 8));
    }

    @Test
    public void eachNewBoundaryColumnStartsAtItsTouchingHistoricalEdgeHeight() throws Exception {
        GenerationBoundary boundary = GenerationBoundary.freeze("varying-edge", List.of(
                new GenerationBoundary.ChunkCoordinate(0, 0)
        ));
        Path dimensionRoot = temporaryFolder.getRoot().toPath().resolve("varying-edge");
        Files.createDirectory(dimensionRoot);
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(dimensionRoot);
        TerrainBoundarySignatureStore.Snapshot snapshot = store.publish(
                7L,
                boundary.exposedBlockColumns().stream()
                        .map(column -> heightSignature(
                                column.blockX(),
                                column.blockZ(),
                                column.blockX() == 15 ? 40 + column.blockZ() * 7 : 180
                        ))
                        .toList()
        );
        TransitionGenerationPlan plan = new TransitionGenerationPlan(
                specification(7L, "old", "new", 16, "varying-edge", snapshot.identity()),
                boundary,
                snapshot
        );

        for (int blockZ = 1; blockZ < 15; blockZ++) {
            assertEquals(
                    40D + blockZ * 7D,
                    plan.terrainSampleAt(16, blockZ).historicalSurfaceHeight(),
                    0D
            );
        }
    }

    @Test
    public void rejectsFootprintsCrossingHistoryAndAllowsTheTerrainBand() throws Exception {
        GenerationBoundary boundary = GenerationBoundary.freeze("footprints", List.of(
                new GenerationBoundary.ChunkCoordinate(0, 0)
        ));
        TerrainBoundarySignatureStore.Snapshot snapshot = snapshot(5L, boundary);
        TransitionGenerationPlan plan = new TransitionGenerationPlan(
                specification(5L, "old", "new", 16, "footprints", snapshot.identity()),
                boundary,
                snapshot
        );

        assertFalse(plan.allowsNewFootprint(-20, -20, 40, 40));
        assertTrue(plan.allowsNewFootprint(16, -20, 16, 35));
        assertTrue(plan.allowsNewFootprint(31, 8, 31, 8));
    }

    @Test
    public void exposesTheNearestFrozenPhysicalBiomeUntilDiscreteHandoff() throws Exception {
        GenerationBoundary boundary = GenerationBoundary.freeze("biomes", List.of(
                new GenerationBoundary.ChunkCoordinate(0, 0)
        ));
        Path dimensionRoot = temporaryFolder.getRoot().toPath().resolve("activation-biomes");
        Files.createDirectory(dimensionRoot);
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(dimensionRoot);
        TerrainBoundarySignatureStore.Snapshot snapshot = store.publish(6L, boundary.exposedBlockColumns().stream()
                .map(column -> biomeSignature(column.blockX(), column.blockZ()))
                .toList());
        TransitionGenerationPlan plan = new TransitionGenerationPlan(
                specification(6L, "old", "new", 16, "biomes", snapshot.identity()),
                boundary,
                snapshot
        );

        assertEquals("iris:middle", plan.historicalPhysicalBiomeKeyAt(16, 14, 8).orElseThrow());
        assertEquals("iris:high", plan.historicalPhysicalBiomeKeyAt(16, 18, 8).orElseThrow());
        assertTrue(plan.historicalPhysicalBiomeKeyAt(31, 14, 8).isEmpty());
        for (int blockX = -2; blockX <= 34; blockX++) {
            for (int blockZ : new int[]{-2, 8, 17}) {
                TransitionGenerationPlan.TerrainSample sample = plan.terrainSampleAt(blockX, blockZ);
                for (int blockY = -16; blockY <= 48; blockY++) {
                    double weight = GenerationBlend.newEpochWeight(
                            Math.max(0D, sample.distanceToHistoricalTerrain() - 1D), Math.max(1, plan.widthBlocks() - 1));
                    Optional<String> expected = sample.newEpochWeight() != 1D
                            && GenerationBlend.usesHistoricalMaterial(blockX, blockY, blockZ, weight)
                            ? sample.historicalPhysicalBiomeKeyAt(blockY) : Optional.empty();
                    assertEquals(expected, plan.historicalPhysicalBiomeKeyAt(blockX, blockY, blockZ, sample));
                    assertEquals(expected, plan.historicalPhysicalBiomeKeyAt(blockX, blockY, blockZ));
                }
            }
        }
    }

    @Test
    public void emptyHistoryUsesTheNewEpochWithoutAStoredTerrainSample() throws Exception {
        GenerationBoundary boundary = GenerationBoundary.freeze("empty", List.of());
        TerrainBoundarySignatureStore.Snapshot snapshot = snapshot(4L, boundary);
        TransitionGenerationPlan plan = new TransitionGenerationPlan(
                specification(4L, "old", "new", 32, "empty", snapshot.identity()),
                boundary,
                snapshot
        );

        TransitionGenerationPlan.TerrainSample sample = plan.terrainSampleAt(-120, 240);
        assertFalse(sample.hasHistoricalSignature());
        assertEquals(1D, sample.newEpochWeight(), 0D);
        assertEquals(1D, sample.hydrologyWeight(), 0D);
        assertTrue(plan.allowsNewDiscreteContentAt(-120, 240));
    }

    @Test
    public void boundedChunkCandidatesMatchExhaustiveNearestWeightingAndTies() throws Exception {
        List<TerrainBoundarySignature> signatures = new ArrayList<>();
        for (int x = -160; x <= 160; x += 11) {
            for (int z = -160; z <= 160; z += 13) {
                signatures.add(heightSignature(x, z, Math.floorMod(x * 7 + z * 17, 220)));
            }
        }
        signatures.add(heightSignature(-1, 0, 77));
        signatures.add(heightSignature(1, 0, 101));
        signatures.add(heightSignature(0, -1, 125));
        signatures.add(heightSignature(0, 1, 149));
        TerrainBoundarySignatureStore.Snapshot snapshot = snapshot("nearest-oracle", signatures);
        for (int width : new int[]{32, GenerationTransition.MAXIMUM_WIDTH_BLOCKS}) {
            TransitionBoundarySampler sampler = new TransitionBoundarySampler(width, snapshot);
            for (GenerationBoundary.ChunkCoordinate chunk : List.of(
                    new GenerationBoundary.ChunkCoordinate(-1, -1),
                    new GenerationBoundary.ChunkCoordinate(0, 0),
                    new GenerationBoundary.ChunkCoordinate(4, 5)
            )) {
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        assertMatchesExhaustive(signatures, sampler, chunk.chunkX() * 16 + localX,
                                chunk.chunkZ() * 16 + localZ, width);
                    }
                }
            }
        }
    }

    @Test
    public void includesSearchRadiusTiesAndExcludesMoreDistantSignatures() throws Exception {
        List<TerrainBoundarySignature> signatures = List.of(
                heightSignature(-64, 0, 40), heightSignature(64, 0, 60),
                heightSignature(0, -64, 80), heightSignature(0, 64, 100),
                heightSignature(65, 0, 500)
        );
        TransitionBoundarySampler sampler = new TransitionBoundarySampler(64, snapshot("radius-ties", signatures));

        assertMatchesExhaustive(signatures, sampler, 0, 0, 64);
        assertEquals(70D, sampler.sample(0, 0).historicalSurfaceHeight(), 0D);
        assertEquals(-64, sampler.sample(0, 0).nearestSignature().blockX());
        assertMatchesExhaustive(signatures, sampler, 0, 1, 64);
    }

    @Test
    public void wideFragmentedBoundaryOnlyLoadsCompetitiveCellsAndKeepsBoundedCandidates() throws Exception {
        List<TerrainBoundarySignature> signatures = fragmentedSignatures();
        TerrainBoundarySignatureStore.Snapshot snapshot = snapshot("fragmented-nearest", signatures);

        List<TerrainBoundarySignature> candidates = snapshot.nearestCandidatesForChunk(0, 0, 32_768);

        assertTrue(signatures.size() > 1_024);
        assertTrue(candidates.size() <= 1_024);
        assertEquals(4, candidates.size());
        assertEquals(1L, snapshot.shardLoadCount());
        assertTrue(snapshot.catalogProbeCount() <= 4_356L);
        TransitionBoundarySampler sampler = new TransitionBoundarySampler(16_384, snapshot);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertMatchesExhaustive(signatures, sampler, x, z, 16_384);
            }
        }
        assertEquals(1L, sampler.candidateBuildCount());
        assertEquals(1L, snapshot.shardLoadCount());
        assertTrue(snapshot.cachedShardCount() <= 64);
    }

    @Test
    public void farOutsideChunkDoesBoundedCatalogWorkOnceAndDoesNotReadShards() throws Exception {
        TerrainBoundarySignatureStore.Snapshot snapshot = snapshot("distant-nearest", fragmentedSignatures());
        TransitionBoundarySampler sampler = new TransitionBoundarySampler(16_384, snapshot);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertFalse(sampler.sample(1_000_000_000 + x, -1_000_000_000 + z).hasHistoricalSignature());
            }
        }

        assertEquals(1L, sampler.candidateBuildCount());
        assertEquals(0L, sampler.shardLoadCount());
        assertTrue(sampler.catalogProbeCount() <= 4_356L);
    }

    @Test
    public void footprintIntersectionStopsAtTheFirstWitnessWithoutCollectingSignatures() throws Exception {
        TerrainBoundarySignatureStore.Snapshot snapshot = snapshot("footprint-stream", fragmentedSignatures());
        TransitionBoundarySampler sampler = new TransitionBoundarySampler(16_384, snapshot);

        assertTrue(sampler.intersectsTerrainBand(Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(1L, sampler.shardLoadCount());
        assertEquals(1L, sampler.catalogProbeCount());

        TerrainBoundarySignatureStore.Snapshot exact = snapshot("footprint-edge", List.of(heightSignature(0, 0, 70)));
        TransitionBoundarySampler edge = new TransitionBoundarySampler(32, exact);
        assertFalse(edge.intersectsTerrainBand(32, 0, 32, 0));
        assertTrue(edge.intersectsTerrainBand(31, 0, 31, 0));
        assertFalse(edge.intersectsTerrainBand(23, 23, 23, 23));
    }

    @Test
    public void rejectsInvalidOrMismatchedImmutableInputs() throws Exception {
        GenerationBoundary boundary = GenerationBoundary.freeze("actual", List.of());
        TerrainBoundarySignatureStore.Snapshot snapshot = snapshot(1L, boundary);

        assertThrows(IllegalArgumentException.class,
                () -> specification(0L, "old", "new", 16, "actual", snapshot.identity()));
        assertThrows(IllegalArgumentException.class,
                () -> specification(1L, "same", "same", 16, "actual", snapshot.identity()));
        assertThrows(IllegalArgumentException.class,
                () -> specification(1L, "old", "new", 0, "actual", snapshot.identity()));
        assertThrows(IllegalArgumentException.class,
                () -> new TransitionGenerationPlan(
                        specification(1L, "old", "new", 16, "different", snapshot.identity()),
                        boundary,
                        snapshot
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new TransitionGenerationPlan(
                        specification(2L, "old", "new", 16, "actual", snapshot.identity()),
                        boundary,
                        snapshot
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new TransitionGenerationPlan(
                        specification(1L, "old", "new", 16, "actual", "f".repeat(64)),
                        boundary,
                        snapshot
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new TransitionGenerationPlan(
                        new TransitionGenerationPlan.Specification(
                                1L,
                                "old",
                                "new",
                                GenerationTransition.CURRENT_ALGORITHM_VERSION + 1,
                                16,
                                "actual",
                                snapshot.identity()
                        ),
                        boundary,
                        snapshot
                ));
    }

    private TerrainBoundarySignatureStore.Snapshot snapshot(
            long activationId,
            GenerationBoundary boundary
    ) throws IOException {
        Path dimensionRoot = temporaryFolder.getRoot().toPath().resolve("activation-" + activationId);
        Files.createDirectory(dimensionRoot);
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(dimensionRoot);
        return store.publish(activationId, boundary.exposedBlockColumns().stream()
                .map(column -> signature(column.blockX(), column.blockZ()))
                .toList());
    }

    private TerrainBoundarySignatureStore.Snapshot snapshot(
            String name,
            List<TerrainBoundarySignature> signatures
    ) throws IOException {
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(temporaryFolder.newFolder(name).toPath());
        store.publish(1L, signatures);
        return store.load(1L);
    }

    private static List<TerrainBoundarySignature> fragmentedSignatures() {
        List<TerrainBoundarySignature> signatures = new ArrayList<>();
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                for (int local = 0; local < 4; local++) {
                    signatures.add(heightSignature(x * 1_024 + local * 4, z * 1_024 + local * 4, 60 + local));
                }
            }
        }
        return signatures;
    }

    private static void assertMatchesExhaustive(
            List<TerrainBoundarySignature> signatures,
            TransitionBoundarySampler sampler,
            int blockX,
            int blockZ,
            int searchWidth
    ) {
        List<TerrainBoundarySignature> nearest = signatures.stream()
                .filter(signature -> distanceSquared(signature, blockX, blockZ) <= (double) searchWidth * searchWidth)
                .sorted(Comparator.comparingDouble((TerrainBoundarySignature signature) -> distanceSquared(signature, blockX, blockZ))
                        .thenComparingInt(TerrainBoundarySignature::blockX)
                        .thenComparingInt(TerrainBoundarySignature::blockZ))
                .limit(4)
                .toList();
        TransitionGenerationPlan.TerrainSample sampled = sampler.sample(blockX, blockZ);
        assertEquals(!nearest.isEmpty(), sampled.hasHistoricalSignature());
        if (nearest.isEmpty()) {
            return;
        }
        assertEquals(nearest.getFirst().blockX(), sampled.nearestSignature().blockX());
        assertEquals(nearest.getFirst().blockZ(), sampled.nearestSignature().blockZ());
        assertEquals(Math.sqrt(distanceSquared(nearest.getFirst(), blockX, blockZ)), sampled.distanceToHistoricalTerrain(), 0D);
        double surface = nearest.getFirst().surfaceHeight();
        double floor = nearest.getFirst().oceanFloorHeight();
        if (distanceSquared(nearest.getFirst(), blockX, blockZ) > 1D) {
            surface = 0D;
            floor = 0D;
            double totalWeight = 0D;
            for (TerrainBoundarySignature signature : nearest) {
                double weight = 1D / distanceSquared(signature, blockX, blockZ);
                surface += signature.surfaceHeight() * weight;
                floor += signature.oceanFloorHeight() * weight;
                totalWeight += weight;
            }
            surface /= totalWeight;
            floor /= totalWeight;
        }
        assertEquals(surface, sampled.historicalSurfaceHeight(), 0D);
        assertEquals(floor, sampled.historicalOceanFloorHeight(), 0D);
    }

    private static double distanceSquared(TerrainBoundarySignature signature, int blockX, int blockZ) {
        long x = (long) signature.blockX() - blockX;
        long z = (long) signature.blockZ() - blockZ;
        return (double) x * x + (double) z * z;
    }

    private static TransitionGenerationPlan.Specification specification(
            long activationId,
            String oldEpochId,
            String newEpochId,
            int widthBlocks,
            String boundaryIdentity,
            String terrainSignatureIdentity
    ) {
        return new TransitionGenerationPlan.Specification(
                activationId,
                oldEpochId,
                newEpochId,
                GenerationTransition.CURRENT_ALGORITHM_VERSION,
                widthBlocks,
                boundaryIdentity,
                terrainSignatureIdentity
        );
    }

    private static TerrainBoundarySignature signature(int blockX, int blockZ) {
        return new TerrainBoundarySignature(
                new TerrainBoundarySignature.Column(blockX, blockZ, 64, 63, OptionalInt.empty(), OptionalInt.empty()),
                new TerrainBoundarySignature.Samples(
                        new TerrainBoundarySignature.VerticalLayout(0, 1, 1),
                        new TerrainBoundarySignature.BiomeEncoding(List.of("iris:test"), new short[]{0})
                )
        , BoundaryColumnGeometry.empty());
    }

    private static TerrainBoundarySignature biomeSignature(int blockX, int blockZ) {
        return new TerrainBoundarySignature(
                new TerrainBoundarySignature.Column(blockX, blockZ, 64, 63, OptionalInt.empty(), OptionalInt.empty()),
                new TerrainBoundarySignature.Samples(
                        new TerrainBoundarySignature.VerticalLayout(0, 10, 3),
                        new TerrainBoundarySignature.BiomeEncoding(
                                List.of("iris:low", "iris:middle", "iris:high"),
                                new short[]{0, 1, 2})
                )
        , BoundaryColumnGeometry.empty());
    }

    private static TerrainBoundarySignature heightSignature(int blockX, int blockZ, int height) {
        return new TerrainBoundarySignature(
                new TerrainBoundarySignature.Column(blockX, blockZ, height, height - 1, OptionalInt.empty(), OptionalInt.empty()),
                new TerrainBoundarySignature.Samples(
                        new TerrainBoundarySignature.VerticalLayout(0, 1, 1),
                        new TerrainBoundarySignature.BiomeEncoding(List.of("iris:test"), new short[]{0})
                )
        , BoundaryColumnGeometry.empty());
    }
}
