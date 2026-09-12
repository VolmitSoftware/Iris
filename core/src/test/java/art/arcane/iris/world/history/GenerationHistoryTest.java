package art.arcane.iris.world.history;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GenerationHistoryTest extends GenerationHistorySupport {
    @Test
    public void upperContentUpdatesPreserveLayoutAndDistinctActivationsAcrossRestarts() throws Exception {
        Path world = temporaryFolder.newFolder("upper-update-world").toPath();
        Path packA = createPack("upper-update-a", "alpha");
        Path packB = createPack("upper-update-b", "beta");
        GenerationEpoch.DimensionContract contractA = upperContract(fingerprint(packA));
        GenerationEpoch.DimensionContract contractB = upperContract(fingerprint(packB));
        GenerationHistory history = GenerationHistory.create(
                world, packA, fingerprint(packA), 42L, contractA, GenerationRegistryContract.empty());
        String epochA = history.activeEpoch().epochId();
        history.stageUpdate(packB, fingerprint(packB), contractB, GenerationRegistryContract.empty(), 32);
        history.promotePending(List.of());

        GenerationHistory restarted = GenerationHistory.open(world);
        assertEquals(contractB, restarted.activeEpoch().dimensionContract());
        restarted.stageUpdate(packA, fingerprint(packA), contractA, GenerationRegistryContract.empty(), 32);
        restarted.promotePending(List.of());

        GenerationHistory returned = GenerationHistory.open(world);
        assertEquals(3L, returned.activeActivation().activationId());
        assertEquals(epochA, returned.activeEpoch().epochId());
        assertFalse(returned.paths().activationMantleRoot(1L).equals(returned.paths().activationMantleRoot(3L)));
    }

    @Test
    public void createPublishesThePackBeforeTheCanonicalManifest() throws Exception {
        Path world = temporaryFolder.newFolder("create-world").toPath();
        Path pack = createPack("create-pack", "alpha");
        String fingerprint = fingerprint(pack);

        GenerationHistory history = createHistory(world, pack);

        assertTrue(Files.isRegularFile(history.paths().manifest()));
        assertTrue(Files.isRegularFile(history.activePackRoot().resolve("dimensions/main.json")));
        assertEquals(1L, history.activeActivation().activationId());
        assertEquals(1L, history.resolveActivation(40, -70).activationId());
        assertEquals(fingerprint, history.resolveEpoch(40, -70).packFingerprint());
    }

    @Test
    public void failedPackPublicationNeverCreatesAManifest() throws Exception {
        Path world = temporaryFolder.newFolder("failed-create-world").toPath();
        Path pack = createPack("failed-create-pack", "alpha");
        GenerationHistoryPaths paths = GenerationHistoryPaths.forDimension(world);

        assertThrows(
                IOException.class,
                () -> GenerationHistory.create(
                        world,
                        pack,
                        "f".repeat(64),
                        42L,
                        contract(),
                        GenerationRegistryContract.empty()
                )
        );
        assertFalse(Files.exists(paths.manifest()));
    }

    @Test
    public void openFailsClosedWhenAnyReferencedPackIsMissingOrChanged() throws Exception {
        Path missingWorld = temporaryFolder.newFolder("missing-world").toPath();
        Path missingPack = createPack("missing-pack", "alpha");
        GenerationHistory missingHistory = createHistory(missingWorld, missingPack);
        Files.delete(missingHistory.activePackRoot().resolve("dimensions/main.json"));

        assertThrows(IOException.class, () -> GenerationHistory.open(missingWorld));

        Path changedWorld = temporaryFolder.newFolder("changed-world").toPath();
        Path changedPack = createPack("changed-pack", "alpha");
        GenerationHistory changedHistory = createHistory(changedWorld, changedPack);
        Files.writeString(changedHistory.activePackRoot().resolve("dimensions/main.json"), "changed");

        assertThrows(IOException.class, () -> GenerationHistory.open(changedWorld));
    }

    @Test
    public void historicalExecutionVersionsDoNotPreventOpeningSavedTerrain() throws Exception {
        Path abiWorld = temporaryFolder.newFolder("abi-world").toPath();
        Path abiPack = createPack("abi-pack", "alpha");
        initializeRawHistory(abiWorld, abiPack, 2, GenerationKernelRegistry.standard().current().rngVersion());
        assertEquals(2, GenerationHistory.open(abiWorld).activeEpoch().generatorAbi());

        Path rngWorld = temporaryFolder.newFolder("rng-world").toPath();
        Path rngPack = createPack("rng-pack", "alpha");
        initializeRawHistory(rngWorld, rngPack, GenerationKernelRegistry.standard().current().generatorAbi(), 2);
        assertEquals(2, GenerationHistory.open(rngWorld).activeEpoch().rngVersion());
    }

    @Test
    public void promotionFreezesAllocatedChunksBeforePublishingTheNewActivation() throws Exception {
        Path world = temporaryFolder.newFolder("promotion-world").toPath();
        Path packA = createPack("promotion-pack-a", "alpha");
        Path packB = createPack("promotion-pack-b", "beta");
        GenerationHistory history = createHistory(world, packA);
        Path region = Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca");
        writeRegion(region, new int[][]{{3, 4}, {31, 31}});

        GenerationActivation pending = stage(history, packB);
        GenerationActivation active = history.promotePending(signaturesForChunks(new int[][]{{3, 4}, {31, 31}}));

        assertEquals(2L, pending.activationId());
        assertEquals(pending.activationId(), active.activationId());
        assertEquals(pending.epochId(), active.epochId());
        assertTrue(active.transition().isComplete());
        assertEquals(1L, history.resolveActivation(3, 4).activationId());
        assertEquals(1L, history.resolveActivation(31, 31).activationId());
        assertEquals(2L, history.resolveActivation(32, 31).activationId());
        GenerationBoundary boundary = history.boundary(2L);
        assertTrue(boundary.isHistoricalChunk(3, 4));
        assertTrue(boundary.isHistoricalChunk(31, 31));
        assertFalse(boundary.isHistoricalChunk(32, 31));
        assertEquals(history.paths().packRoot(history.activeEpoch().epochId()), history.activePackRoot());
        assertTrue(Files.isRegularFile(history.packRoot(1L).resolve("dimensions/main.json")));
        assertEquals(256, history.transitionPlan(2L).widthBlocks());
        assertEquals(boundary.exposedBlockColumns().size(), history.terrainSignatures(2L).size());

        GenerationHistory reopened = GenerationHistory.open(world);
        assertEquals(1L, reopened.resolveActivation(3, 4).activationId());
        assertEquals(2L, reopened.resolveActivation(32, 31).activationId());
        assertEquals(2, reopened.explicitChunkCount());
        assertEquals(256, reopened.transitionPlan(2L).widthBlocks());
    }

    @Test
    public void promotionCapturesSignaturesFromTheDurableBoundary() throws Exception {
        Path world = temporaryFolder.newFolder("capture-boundary-world").toPath();
        Path packA = createPack("capture-boundary-pack-a", "alpha");
        Path packB = createPack("capture-boundary-pack-b", "beta");
        GenerationHistory history = createHistory(world, packA);
        Path region = Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca");
        writeRegion(region, new int[][]{{3, 4}});
        stage(history, packB);

        GenerationActivation active = history.promotePending(boundary -> {
            assertTrue(boundary.isHistoricalChunk(3, 4));
            assertFalse(boundary.isHistoricalChunk(4, 4));
            return GenerationHistoryTest::signature;
        });

        assertEquals(2L, active.activationId());
        assertEquals(history.boundary(2L).identity(), active.transition().boundaryIdentity());
    }

    @Test
    public void promotionIsIdempotentForEmptyAndRecoveredWorlds() throws Exception {
        Path emptyWorld = temporaryFolder.newFolder("empty-world").toPath();
        Path emptyPackA = createPack("empty-pack-a", "alpha");
        Path emptyPackB = createPack("empty-pack-b", "beta");
        GenerationHistory emptyHistory = createHistory(emptyWorld, emptyPackA);
        stage(emptyHistory, emptyPackB);

        assertEquals(2L, emptyHistory.promotePending(List.of()).activationId());
        assertEquals(2L, emptyHistory.promotePending(List.of()).activationId());
        assertEquals(2L, emptyHistory.resolveActivation(0, 0).activationId());
        assertEquals(0, emptyHistory.explicitChunkCount());

        Path recoveredWorld = temporaryFolder.newFolder("recovered-world").toPath();
        Path recoveredPackA = createPack("recovered-pack-a", "alpha");
        Path recoveredPackB = createPack("recovered-pack-b", "beta");
        GenerationHistory recovered = createHistory(recoveredWorld, recoveredPackA);
        stage(recovered, recoveredPackB);

        GenerationHistory reopened = GenerationHistory.open(recoveredWorld);
        assertEquals(2L, reopened.promotePending(List.of()).activationId());
        assertEquals(2L, reopened.resolveActivation(8, 9).activationId());
    }

    @Test
    public void stageUpdateIsIdempotentAndRejectsIncompatibleCandidates() throws Exception {
        Path world = temporaryFolder.newFolder("stage-world").toPath();
        Path packA = createPack("stage-pack-a", "alpha");
        Path packB = createPack("stage-pack-b", "beta");
        GenerationHistory history = createHistory(world, packA);

        GenerationActivation first = stage(history, packB);
        GenerationActivation repeated = stage(history, packB);

        assertEquals(first, repeated);
        assertEquals(2, history.manifest().activations().size());
        GenerationEpoch.DimensionContract incompatible = new GenerationEpoch.DimensionContract(
                "overworld",
                "iris:overworld_type",
                "NORMAL",
                "OVERWORLD",
                127,
                -64,
                512,
                512,
                1D,
                false,
                "none",
                0,
                "0".repeat(64),
                GenerationEpochContractFactory.CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA,
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> history.stageUpdate(
                        packB,
                        fingerprint(packB),
                        incompatible,
                        GenerationRegistryContract.empty(),
                        256
                )
        );
    }

    @Test
    public void stagingTheActiveEpochIsANoOp() throws Exception {
        Path world = temporaryFolder.newFolder("same-epoch-world").toPath();
        Path pack = createPack("same-epoch-pack", "alpha");
        GenerationHistory history = createHistory(world, pack);

        GenerationActivation activation = stage(history, pack);

        assertEquals(history.activeActivation(), activation);
        assertTrue(history.pendingActivation().isEmpty());
        assertEquals(1, history.manifest().activations().size());
    }

    @Test
    public void returningToAnEarlierEpochCreatesANewActivation() throws Exception {
        Path world = temporaryFolder.newFolder("return-world").toPath();
        Path packA = createPack("return-pack-a", "alpha");
        Path packB = createPack("return-pack-b", "beta");
        GenerationHistory history = createHistory(world, packA);
        String epochA = history.activeEpoch().epochId();

        stage(history, packB);
        history.promotePending(List.of());
        GenerationActivation activationA2 = stage(history, packA);
        history.promotePending(List.of());

        assertEquals(3L, activationA2.activationId());
        assertEquals(epochA, activationA2.epochId());
        assertEquals(2, history.manifest().epochs().size());
        assertEquals(3, history.manifest().activations().size());
    }

    @Test
    public void openRejectsWorldSeedMismatch() throws Exception {
        Path world = temporaryFolder.newFolder("seed-world").toPath();
        Path pack = createPack("seed-pack", "alpha");
        createHistory(world, pack);

        assertThrows(IOException.class, () -> GenerationHistory.open(world, 43L));
        assertEquals(42L, GenerationHistory.open(world, 42L).activeEpoch().worldSeed());
    }

    @Test
    public void promotionIsRejectedAfterGenerationAdmissionOpens() throws Exception {
        Path world = temporaryFolder.newFolder("admission-world").toPath();
        Path packA = createPack("admission-pack-a", "alpha");
        Path packB = createPack("admission-pack-b", "beta");
        GenerationHistory history = createHistory(world, packA);
        stage(history, packB);
        GenerationHistory.GenerationStage generationStage = history.openStage(2, 3);
        assertEquals(1L, generationStage.activation().activationId());
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> history.promotePending(List.of())
        );
        assertTrue(failure.getMessage().contains("before generation admission opens"));
        generationStage.close();
        assertThrows(IllegalStateException.class, () -> history.promotePending(List.of()));
    }
}
