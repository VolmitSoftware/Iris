package art.arcane.iris.world.history;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public final class GenerationHistoryStartupValidationTest extends GenerationHistorySupport {
    @Test
    public void startupRetainsSavedClaimsWithoutRepeatingReferenceTraversal() throws Exception {
        Path world = temporaryFolder.newFolder("startup-reference-reuse").toPath();
        createHistory(world, createPack("startup-reference-pack", "alpha"));
        Path regions = Files.createDirectories(world.resolve("region"));
        SavedTerrainTestRegion.write(regions.resolve("r.0.0.mca"), new int[][]{{0, 0}}, "minecraft:noise");
        SavedTerrainTestRegion.write(regions.resolve("r.1.0.mca"), new int[][]{{32, 0}}, "minecraft:biomes");
        GenerationSemanticIndex stored = GenerationSemanticIndex.loadRequired(world);
        for (int chunkX : new int[]{0, 32, 64}) {
            stored.recordAndPersist(ChunkGenerationSemantics.builder(chunkX, 0, 1L).seal().build());
        }
        GenerationHistory history = GenerationHistory.open(world);
        GenerationSemanticIndex observed = observeSemantics(history);

        try (GenerationHistory.StartupPreparation ignored = history.prepareStartup(64)) {
            assertEquals(1L, history.activeActivation().activationId());
        }

        verify(observed, never()).forEachRecord(any());
        assertTrue(history.semantics(0, 0).isPresent());
        assertTrue(history.semantics(32, 0).isEmpty());
        assertTrue(history.semantics(64, 0).isEmpty());
        GenerationHistory reopened = GenerationHistory.open(world);
        assertTrue(reopened.semantics(0, 0).isPresent());
        assertTrue(reopened.semantics(32, 0).isEmpty());
        assertTrue(reopened.semantics(64, 0).isEmpty());
    }

    @Test
    public void startupPromotionRecoversOrphansWithoutRepeatingReferenceTraversal() throws Exception {
        Path world = temporaryFolder.newFolder("startup-promotion-reference-reuse").toPath();
        createHistory(world, createPack("startup-promotion-pack-a", "alpha"));
        GenerationSemanticIndex stored = GenerationSemanticIndex.loadRequired(world);
        stored.recordAndPersist(ChunkGenerationSemantics.builder(0, 0, 1L).seal().build());
        GenerationHistory history = GenerationHistory.open(world);
        stage(history, createPack("startup-promotion-pack-b", "beta"));
        GenerationSemanticIndex observed = observeSemantics(history);

        try (GenerationHistory.StartupPreparation ignored = history.prepareStartup(64)) {
            assertEquals(2L, history.activeActivation().activationId());
        }

        verify(observed, never()).forEachRecord(any());
        assertTrue(history.semantics(0, 0).isEmpty());
        assertEquals(2L, GenerationHistory.open(world).activeActivation().activationId());
    }

    @Test
    public void standalonePromotionStillValidatesSemanticReferences() throws Exception {
        Path world = temporaryFolder.newFolder("standalone-reference-validation").toPath();
        GenerationHistory history = createHistory(world, createPack("standalone-reference-pack", "alpha"));
        semantics(history).recordAndPersist(ChunkGenerationSemantics.builder(0, 0, 99L).seal().build());

        IOException failure = assertThrows(IOException.class, () -> history.promotePending(List.of()));

        assertTrue(failure.getMessage().contains("missing generation activation 99"));
        assertEquals(1L, history.activeActivation().activationId());
    }

    @Test
    public void livePromotionStillValidatesSemanticReferences() throws Exception {
        Path world = temporaryFolder.newFolder("live-reference-validation").toPath();
        GenerationHistory history = createHistory(world, createPack("live-reference-pack", "alpha"));
        try (GenerationHistory.GenerationStage ignored = history.openStage(0, 0)) {
        }
        semantics(history).recordAndPersist(ChunkGenerationSemantics.builder(0, 0, 99L).seal().build());

        try (GenerationHistory.LiveCutover cutover = history.beginLiveCutover()) {
            IOException failure = assertThrows(IOException.class,
                    () -> cutover.promote(boundary -> (blockX, blockZ) -> null));
            assertTrue(failure.getMessage().contains("missing generation activation 99"));
        }
        assertEquals(1L, history.activeActivation().activationId());
    }

    @Test
    public void startupValidationCannotBeReusedAfterGenerationAdmission() throws Exception {
        Path world = temporaryFolder.newFolder("startup-admission-guard").toPath();
        GenerationHistory history = createHistory(world, createPack("startup-admission-pack", "alpha"));
        try (GenerationHistory.GenerationStage generation = history.openStage(0, 0)) {
            history.claimGeneratedSemantics(generation,
                    ChunkGenerationSemantics.builder(0, 0, 1L).seal().build());
        }

        assertThrows(IllegalStateException.class, () -> history.prepareStartup(64));
        assertFalse(history.semantics(0, 0).isEmpty());
    }

    @Test
    public void openingHistoryStillRejectsInvalidSemanticReferences() throws Exception {
        Path world = temporaryFolder.newFolder("open-reference-validation").toPath();
        createHistory(world, createPack("open-reference-pack", "alpha"));
        GenerationSemanticIndex stored = GenerationSemanticIndex.loadRequired(world);
        stored.recordAndPersist(ChunkGenerationSemantics.builder(0, 0, 99L).seal().build());

        IOException failure = assertThrows(IOException.class, () -> GenerationHistory.open(world));

        assertTrue(failure.getMessage().contains("missing generation activation 99"));
    }

    @Test
    public void kernelStagingRejectsTamperedActivePackWithOrWithoutPendingActivation() throws Exception {
        GenerationKernelRegistry.Version oldVersion = new GenerationKernelRegistry.Version(101, 1, 1);
        GenerationKernelRegistry.Version currentVersion = new GenerationKernelRegistry.Version(102, 1, 1);
        for (boolean pending : new boolean[]{false, true}) {
            Path world = temporaryFolder.newFolder("kernel-tamper-" + pending).toPath();
            Path pack = createPack("kernel-tamper-pack-" + pending, "alpha");
            GenerationHistory.create(world, pack, fingerprint(pack), 42L,
                    contract(), GenerationRegistryContract.empty(), oldVersion, singleKernel(oldVersion, "a"));
            GenerationHistory history = GenerationHistory.open(world, singleKernel(currentVersion, "b"));
            Path activePack = history.activePackRoot();
            if (pending) {
                history.stageCurrentKernel(32);
            }
            Files.writeString(activePack.resolve("dimensions/main.json"), "changed");

            assertThrows(IOException.class, () -> history.stageCurrentKernel(32));

            assertEquals(pending, history.pendingActivation().isPresent());
            assertEquals(1L, history.activeActivation().activationId());
        }
    }

    @Test
    public void kernelStagingRejectsSymlinkedActivePackRoots() throws Exception {
        Path world = temporaryFolder.newFolder("kernel-symlink").toPath();
        GenerationHistory history = createHistory(world, createPack("kernel-symlink-pack", "alpha"));
        Path activePack = history.activePackRoot();
        Path moved = temporaryFolder.getRoot().toPath().resolve("moved-kernel-pack");
        Files.move(activePack, moved);
        Files.createSymbolicLink(activePack, moved);

        assertThrows(IOException.class, () -> history.stageCurrentKernel(32));
        assertTrue(history.pendingActivation().isEmpty());
    }

    private static GenerationSemanticIndex observeSemantics(GenerationHistory history) throws Exception {
        GenerationSemanticIndex observed = spy(semantics(history));
        Field field = GenerationHistory.class.getDeclaredField("semantics");
        field.setAccessible(true);
        field.set(history, observed);
        return observed;
    }

    private static GenerationSemanticIndex semantics(GenerationHistory history) throws Exception {
        Field field = GenerationHistory.class.getDeclaredField("semantics");
        field.setAccessible(true);
        return (GenerationSemanticIndex) field.get(history);
    }
}
