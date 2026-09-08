package art.arcane.iris.engine.history;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GenerationHistoryRecoveryTest extends GenerationHistorySupport {
    @Test
    public void openFailsClosedOnDanglingOwnershipAndPartialHistory() throws Exception {
        Path danglingWorld = temporaryFolder.newFolder("dangling-world").toPath();
        Path pack = createPack("dangling-pack", "alpha");
        GenerationHistory history = createHistory(danglingWorld, pack);
        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(history.paths().ownershipRoot());
        ownership.assign(1, 2, 99L);
        ownership.persist();

        assertThrows(IOException.class, () -> GenerationHistory.open(danglingWorld));

        Path absentWorld = temporaryFolder.newFolder("absent-world").toPath();
        Optional<GenerationHistory> absent = GenerationHistory.openIfPresent(absentWorld);
        assertTrue(absent.isEmpty());

        Path partialWorld = temporaryFolder.newFolder("partial-world").toPath();
        Files.createDirectories(GenerationHistoryPaths.forDimension(partialWorld).generationRoot());
        assertThrows(NoSuchFileException.class, () -> GenerationHistory.openIfPresent(partialWorld));
    }

    @Test
    public void savedHistoryOpensWithoutRetainingItsExecutableKernel() throws Exception {
        Path world = temporaryFolder.newFolder("retained-abi-world").toPath();
        Path packA = createPack("retained-abi-pack-a", "alpha");
        Path packB = createPack("retained-abi-pack-b", "beta");
        GenerationKernelRegistry.Version versionOne = new GenerationKernelRegistry.Version(1, 1, 1);
        GenerationKernelRegistry.Version versionTwo = new GenerationKernelRegistry.Version(2, 1, 1);
        GenerationKernelRegistry kernels = new GenerationKernelRegistry(
                versionTwo,
                List.of(
                        new GenerationKernelRegistry.Kernel(
                                1,
                                "1".repeat(64),
                                Map.of(
                                        new GenerationKernelRegistry.AlgorithmVersion(1, 1),
                                        (engine, transitionPlan) -> {
                                            throw new AssertionError("History-only kernel factory was invoked.");
                                        }
                                )
                        ),
                        new GenerationKernelRegistry.Kernel(
                                2,
                                "2".repeat(64),
                                Map.of(
                                        new GenerationKernelRegistry.AlgorithmVersion(1, 1),
                                        (engine, transitionPlan) -> {
                                            throw new AssertionError("History-only kernel factory was invoked.");
                                        }
                                )
                        )
                )
        );
        GenerationHistory history = GenerationHistory.create(
                world,
                packA,
                fingerprint(packA),
                42L,
                contract(),
                GenerationRegistryContract.empty(),
                versionOne,
                kernels
        );

        history.stageUpdate(
                packB,
                fingerprint(packB),
                contract(),
                GenerationRegistryContract.empty(),
                256,
                versionTwo
        );
        history.promotePending(List.of());

        assertEquals(2, GenerationHistory.open(world).activeEpoch().generatorAbi());
        GenerationHistory reopened = GenerationHistory.open(world, kernels);
        assertEquals(2, reopened.activeEpoch().generatorAbi());
        assertEquals(1, reopened.manifest().activation(1L).orElseThrow().activationId());
    }

    @Test
    public void changedBuildExpandsFromSavedTerrainWithoutTheOldFactory() throws Exception {
        Path world = temporaryFolder.newFolder("build-upgrade-world").toPath();
        Path pack = createPack("build-upgrade-pack", "alpha");
        GenerationKernelRegistry.Version oldVersion = new GenerationKernelRegistry.Version(101, 1, 1);
        GenerationKernelRegistry.Version newVersion = new GenerationKernelRegistry.Version(102, 1, 1);
        GenerationKernelRegistry firstBuild = singleKernel(oldVersion, "a");
        GenerationKernelRegistry secondBuild = singleKernel(newVersion, "b");
        GenerationHistory.create(world, pack, fingerprint(pack), 42L,
                contract(), GenerationRegistryContract.empty(), oldVersion, firstBuild);
        Path region = Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca");
        SavedTerrainTestRegion.write(region, new int[][]{{0, 0}});
        byte[] savedBlocks = Files.readAllBytes(region);

        GenerationHistory upgraded = GenerationHistory.open(world, secondBuild);
        assertFalse(upgraded.usesCurrentGenerator());
        upgraded.prepareCurrentGenerator(32);
        assertTrue(upgraded.usesCurrentGenerator());
        assertEquals(2L, upgraded.activeActivation().activationId());
        assertEquals(oldVersion, upgraded.resolveEpoch(0, 0).kernelVersion());
        try (GenerationHistory.GenerationStage generation = upgraded.openStage(1, 0)) {
            assertEquals(newVersion, generation.epoch().kernelVersion());
        }
        assertTrue(upgraded.terrainSignatures(2L).size() > 0);
        assertArrayEquals(savedBlocks, Files.readAllBytes(region));
        GenerationHistory reopened = GenerationHistory.open(world, secondBuild);
        assertTrue(reopened.usesCurrentGenerator());
        assertEquals(2L, reopened.activeActivation().activationId());
    }

    @Test
    public void openFailsClosedWhenAnOwnershipShardDisappears() throws Exception {
        Path world = temporaryFolder.newFolder("missing-ownership-world").toPath();
        Path packA = createPack("missing-ownership-pack-a", "alpha");
        Path packB = createPack("missing-ownership-pack-b", "beta");
        GenerationHistory history = createHistory(world, packA);
        Path region = Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca");
        writeRegion(region, new int[][]{{1, 2}});
        stage(history, packB);
        history.promotePending(signaturesForChunks(new int[][]{{1, 2}}));
        Files.delete(history.paths().ownershipRoot().resolve(RegionGenerationOwnership.fileName(0, 0)));

        assertThrows(IOException.class, () -> GenerationHistory.open(world));
    }

    @Test
    public void pendingCutoverResumesAfterPartialOutgoingOwnershipPublication() throws Exception {
        Path world = temporaryFolder.newFolder("partial-cutover-world").toPath();
        Path packA = createPack("partial-cutover-pack-a", "alpha");
        Path packB = createPack("partial-cutover-pack-b", "beta");
        GenerationHistory history = createHistory(world, packA);
        Path region = Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca");
        int[][] chunks = {{1, 2}, {3, 4}};
        writeRegion(region, chunks);
        stage(history, packB);

        ChunkGenerationOwnership partial = ChunkGenerationOwnership.load(history.paths().ownershipRoot());
        assertTrue(partial.assign(1, 2, 1L));
        partial.persist();

        GenerationHistory recovered = GenerationHistory.open(world);
        assertEquals(1, recovered.explicitChunkCount());
        assertEquals(2L, recovered.promotePending(signaturesForChunks(chunks)).activationId());
        assertEquals(2, GenerationHistory.open(world).explicitChunkCount());
    }

    @Test
    public void pendingCutoverStillRejectsMissingOlderOwnership() throws Exception {
        Path world = temporaryFolder.newFolder("pending-missing-old-world").toPath();
        Path packA = createPack("pending-missing-old-pack-a", "alpha");
        Path packB = createPack("pending-missing-old-pack-b", "beta");
        Path packC = createPack("pending-missing-old-pack-c", "gamma");
        GenerationHistory history = createHistory(world, packA);
        Path region = Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca");
        writeRegion(region, new int[][]{{1, 2}});
        stage(history, packB);
        history.promotePending(signaturesForChunks(new int[][]{{1, 2}}));
        stage(history, packC);
        Files.delete(history.paths().ownershipRoot().resolve(RegionGenerationOwnership.fileName(0, 0)));

        assertThrows(IOException.class, () -> GenerationHistory.open(world));
    }

    @Test
    public void pendingCutoverDiscardsOutgoingOwnershipForUnstoredChunks() throws Exception {
        Path world = temporaryFolder.newFolder("pending-forged-world").toPath();
        Path packA = createPack("pending-forged-pack-a", "alpha");
        Path packB = createPack("pending-forged-pack-b", "beta");
        GenerationHistory history = createHistory(world, packA);
        stage(history, packB);
        ChunkGenerationOwnership forged = ChunkGenerationOwnership.load(history.paths().ownershipRoot());
        assertTrue(forged.assign(9, 9, 1L));
        forged.persist();

        GenerationHistory recovered = GenerationHistory.open(world);
        assertEquals(1, recovered.explicitChunkCount());
        recovered.promotePending(List.of());
        assertEquals(0, recovered.explicitChunkCount());
        assertEquals(2L, recovered.resolveActivation(9, 9).activationId());
        assertEquals(0, GenerationHistory.open(world).explicitChunkCount());
    }

    @Test
    public void sealedGenerationClaimWithoutStoredTerrainIsDiscardedAfterCrash() throws Exception {
        Path world = temporaryFolder.newFolder("semantic-claim-cutover-world").toPath();
        Path packA = createPack("semantic-claim-cutover-pack-a", "alpha");
        Path packB = createPack("semantic-claim-cutover-pack-b", "beta");
        GenerationHistory history = createHistory(world, packA);
        GenerationHistory.GenerationStage generation = history.openStage(9, 9);
        ChunkGenerationSemantics claim = ChunkGenerationSemantics.builder(9, 9, 1L)
                .addSurfaceBiome("iris:forest")
                .seal()
                .build();

        assertTrue(history.claimGeneratedSemantics(generation, claim));
        assertFalse(history.claimGeneratedSemantics(generation, claim));
        generation.close();
        assertThrows(IllegalStateException.class, () -> history.claimGeneratedSemantics(generation, claim));
        assertFalse(Files.exists(world.resolve("region")));
        stage(history, packB);

        Path recoveredWorld = temporaryFolder.newFolder("restored-semantic-claim-world").toPath();
        try (Stream<Path> entries = Files.walk(world)) {
            for (Path source : entries.toList()) {
                Path target = recoveredWorld.resolve(world.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target);
                }
            }
        }
        GenerationHistory recovered = GenerationHistory.open(recoveredWorld);
        recovered.promotePending(List.of());

        assertEquals(2L, recovered.resolveActivation(9, 9).activationId());
        assertEquals(2L, recovered.resolveActivation(10, 9).activationId());
        assertEquals(0, recovered.explicitChunkCount());
        GenerationHistory reopened = GenerationHistory.open(recoveredWorld);
        assertTrue(reopened.semantics(9, 9).isEmpty());
        assertEquals(2L, reopened.resolveActivation(9, 9).activationId());
        try (GenerationHistory.GenerationStage replacement = reopened.openStage(9, 9)) {
            assertTrue(reopened.claimGeneratedSemantics(replacement,
                    ChunkGenerationSemantics.builder(9, 9, 2L).addObject("replacement").seal().build()));
        }
    }

    @Test
    public void coldRecoveryUsesNativeNoiseStatusAsTheMinimumStoredTerrain() throws Exception {
        Path world = temporaryFolder.newFolder("native-status-recovery-world").toPath();
        createHistory(world, createPack("native-status-recovery-pack", "alpha"));
        Path regions = Files.createDirectories(world.resolve("region"));
        SavedTerrainTestRegion.write(regions.resolve("r.0.0.mca"), new int[][]{{0, 0}}, "minecraft:noise");
        SavedTerrainTestRegion.write(regions.resolve("r.1.0.mca"), new int[][]{{32, 0}}, "minecraft:biomes");
        GenerationSemanticIndex index = GenerationSemanticIndex.loadRequired(world);
        index.claimAndPersist(ChunkGenerationSemantics.builder(0, 0, 1L).addObject("saved-noise").seal().build());
        index.claimAndPersist(ChunkGenerationSemantics.builder(32, 0, 1L).addObject("unsaved-terrain").seal().build());

        GenerationHistory recovered = GenerationHistory.open(world);
        recovered.prepareCurrentGenerator(64);

        assertTrue(recovered.semantics(0, 0).isPresent());
        assertTrue(recovered.semantics(32, 0).isEmpty());
        assertEquals("minecraft:biomes", SavedTerrainChunk.readStatus(world, 32, 0));
        assertEquals(0, recovered.explicitChunkCount());
        GenerationHistory reopened = GenerationHistory.open(world);
        assertTrue(reopened.semantics(0, 0).isPresent());
        assertTrue(reopened.semantics(32, 0).isEmpty());
    }

    @Test
    public void coldRecoveryKeepsStoredClaimsHistoricalMetadataAndFrozenPacks() throws Exception {
        Path world = temporaryFolder.newFolder("cold-recovery-world").toPath();
        Path packA = createPack("cold-recovery-a", "alpha");
        Path packB = createPack("cold-recovery-b", "beta");
        GenerationHistory initial = createHistory(world, packA);
        String oldEpoch = initial.activeEpoch().epochId();
        Path region = Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca");
        writeRegion(region, new int[][]{{0, 0}});
        GenerationSemanticIndex initialIndex = GenerationSemanticIndex.loadRequired(world);
        initialIndex.claimAndPersist(ChunkGenerationSemantics.builder(0, 0, 1L).addObject("historical").seal().build());
        GenerationHistory first = GenerationHistory.open(world);
        stage(first, packB);
        first.promotePending(signaturesForChunks(new int[][]{{0, 0}}));
        GenerationSemanticIndex index = GenerationSemanticIndex.loadRequired(world);
        index.claimAndPersist(ChunkGenerationSemantics.builder(1, 0, 2L).addObject("saved").seal().build());
        index.claimAndPersist(ChunkGenerationSemantics.builder(2, 0, 2L).addObject("orphan").seal().build());
        writeRegion(region, new int[][]{{1, 0}});

        GenerationHistory recovered = GenerationHistory.open(world);
        recovered.prepareCurrentGenerator(64);

        assertEquals(2L, recovered.activeActivation().activationId());
        assertEquals(1, recovered.explicitChunkCount());
        assertTrue(recovered.semantics(0, 0).isPresent());
        assertTrue(recovered.semantics(1, 0).isPresent());
        assertTrue(recovered.semantics(2, 0).isEmpty());
        assertTrue(Files.isDirectory(recovered.paths().packRoot(oldEpoch)));
        assertEquals(GenerationPackFingerprint.compute(packA, GenerationPackFingerprint.CURRENT_VERSION),
                GenerationPackFingerprint.compute(recovered.paths().packRoot(oldEpoch), GenerationPackFingerprint.CURRENT_VERSION));
        assertTrue(Files.isDirectory(recovered.activePackRoot()));
        assertTrue(Files.isDirectory(packA));
        GenerationHistory reopened = GenerationHistory.open(world);
        assertEquals(1L, reopened.resolveActivation(0, 0).activationId());
        assertTrue(reopened.semantics(2, 0).isEmpty());
    }

    @Test
    public void openFailsClosedOnSemanticActivationMismatch() throws Exception {
        Path world = temporaryFolder.newFolder("semantic-mismatch-world").toPath();
        Path packA = createPack("semantic-mismatch-pack-a", "alpha");
        Path packB = createPack("semantic-mismatch-pack-b", "beta");
        GenerationHistory history = createHistory(world, packA);
        stage(history, packB);
        history.promotePending(List.of());
        GenerationSemanticIndex index = GenerationSemanticIndex.loadRequired(world);
        index.recordAndPersist(ChunkGenerationSemantics.builder(7, 8, 1L)
                .addSurfaceBiome("iris:old")
                .seal()
                .build());

        assertThrows(IOException.class, () -> GenerationHistory.open(world));
    }

    @Test
    public void openFailsClosedOnDeletedOrCorruptTransitionSnapshots() throws Exception {
        Path deletedWorld = temporaryFolder.newFolder("deleted-snapshot-world").toPath();
        Path deletedPackA = createPack("deleted-snapshot-pack-a", "alpha");
        Path deletedPackB = createPack("deleted-snapshot-pack-b", "beta");
        GenerationHistory deletedHistory = createHistory(deletedWorld, deletedPackA);
        stage(deletedHistory, deletedPackB);
        deletedHistory.promotePending(List.of());
        Files.delete(new TerrainBoundarySignatureStore(deletedWorld).snapshotPath(2L));

        assertThrows(IOException.class, () -> GenerationHistory.open(deletedWorld));

        Path corruptWorld = temporaryFolder.newFolder("corrupt-snapshot-world").toPath();
        Path corruptPackA = createPack("corrupt-snapshot-pack-a", "alpha");
        Path corruptPackB = createPack("corrupt-snapshot-pack-b", "beta");
        GenerationHistory corruptHistory = createHistory(corruptWorld, corruptPackA);
        stage(corruptHistory, corruptPackB);
        corruptHistory.promotePending(List.of());
        Files.write(new GenerationBoundaryStore(corruptWorld).snapshotPath(2L), new byte[]{1, 2, 3});

        assertThrows(IOException.class, () -> GenerationHistory.open(corruptWorld));
    }
}
