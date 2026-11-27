package art.arcane.iris.world.history;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GenerationStudioRecoveryTest extends GenerationHistorySupport {
    private static final GenerationBoundary.ChunkCoordinate REGENERATED = new GenerationBoundary.ChunkCoordinate(3, 4);

    @Test
    public void regenForgetsEarlierRecordsSoTheNextPromotionStaysConsistent() throws Exception {
        Path world = temporaryFolder.newFolder("regen-release-world").toPath();
        GenerationHistory history = createHistory(world, createPack("regen-release-a", "alpha"));
        int[][] chunks = {{3, 4}, {5, 4}};
        writeRegion(Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca"), chunks);
        claimSemantics(history, 3, 4, 1L);
        claimSemantics(history, 5, 4, 1L);
        assertTrue(history.savedBiomes().claimAndPersist(savedBiomes(3, 4, 1L)));
        stage(history, createPack("regen-release-b", "beta"));
        promoteLive(history);

        assertEquals(1, history.releaseForRegeneration(List.of(REGENERATED)));

        assertEquals(2L, history.resolveActivation(3, 4).activationId());
        assertTrue(history.semantics(3, 4).isEmpty());
        assertTrue(history.savedBiomes().get(3, 4).isEmpty());
        assertEquals(1L, history.semantics(5, 4).orElseThrow().activationId());
        claimSemantics(history, 3, 4, 2L);
        assertTrue(history.savedBiomes().claimAndPersist(savedBiomes(3, 4, 2L)));
        stage(history, createPack("regen-release-c", "gamma"));
        promoteLive(history);

        assertEquals(3L, history.activeActivation().activationId());
        GenerationHistory reopened = GenerationHistory.open(world);
        assertEquals(2L, reopened.semantics(3, 4).orElseThrow().activationId());
        assertEquals(2L, reopened.resolveActivation(3, 4).activationId());
    }

    @Test
    public void regenHealsRecordsThatAnEarlierReclaimLeftOnTheOldActivation() throws Exception {
        Path world = temporaryFolder.newFolder("regen-heal-world").toPath();
        GenerationHistory history = createHistory(world, createPack("regen-heal-a", "alpha"));
        int[][] chunks = {{3, 4}};
        writeRegion(Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca"), chunks);
        claimSemantics(history, 3, 4, 1L);
        stage(history, createPack("regen-heal-b", "beta"));
        promoteLive(history);
        history.releaseForRegeneration(List.of(REGENERATED));
        claimSemantics(history, 3, 4, 2L);

        assertEquals(0, history.releaseForRegeneration(List.of(REGENERATED)));

        assertEquals(2L, history.semantics(3, 4).orElseThrow().activationId());
    }

    @Test
    public void newerStudioUpdateReplacesAPendingActivationThatNeverPromoted() throws Exception {
        Path world = temporaryFolder.newFolder("stale-pending-world").toPath();
        GenerationHistory history = createHistory(world, createPack("stale-pending-a", "alpha"));
        Path failedPack = createPack("stale-pending-b", "beta");
        Path nextPack = createPack("stale-pending-c", "gamma");
        String failedEpoch = stage(history, failedPack).epochId();

        assertThrows(IllegalStateException.class, () -> stage(history, nextPack));
        GenerationActivation replaced = stageReplacing(history, nextPack);

        assertEquals(2L, replaced.activationId());
        assertFalse(replaced.epochId().equals(failedEpoch));
        assertTrue(history.manifest().epochs().stream().noneMatch(epoch -> epoch.epochId().equals(failedEpoch)));
        history.promotePending(List.of());
        assertEquals(replaced.epochId(), GenerationHistory.open(world).activeActivation().epochId());
    }

    @Test
    public void replacedPendingActivationPublishesItsOwnBoundary() throws Exception {
        Path world = temporaryFolder.newFolder("stale-boundary-world").toPath();
        GenerationHistory history = createHistory(world, createPack("stale-boundary-a", "alpha"));
        Path region = Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca");
        writeRegion(region, new int[][]{{3, 4}});
        stage(history, createPack("stale-boundary-b", "beta"));
        assertThrows(IOException.class, () -> history.promotePending(boundary -> {
            throw new IOException("signature capture failed");
        }));
        assertTrue(history.pendingActivation().isPresent());
        int[][] grown = {{3, 4}, {5, 4}};
        writeRegion(region, grown);

        stageReplacing(history, createPack("stale-boundary-c", "gamma"));
        history.promotePending(signaturesForChunks(grown));

        assertEquals(2L, history.activeActivation().activationId());
        assertTrue(history.transitionPlan(2L).isHistoricalChunk(5, 4));
    }

    @Test
    public void revertingToTheActivePackDropsTheStalePendingActivation() throws Exception {
        Path world = temporaryFolder.newFolder("stale-revert-world").toPath();
        Path activePack = createPack("stale-revert-a", "alpha");
        GenerationHistory history = createHistory(world, activePack);
        stage(history, createPack("stale-revert-b", "beta"));

        GenerationActivation staged = stageReplacing(history, activePack);

        assertEquals(history.activeActivation(), staged);
        assertTrue(history.pendingActivation().isEmpty());
        assertEquals(1, history.manifest().activations().size());
    }

    private static void promoteLive(GenerationHistory history) throws IOException {
        try (GenerationHistory.LiveCutover cutover = history.beginLiveCutover()) {
            cutover.promote(boundary -> (blockX, blockZ) -> signature(blockX, blockZ));
        }
    }

    private static GenerationActivation stageReplacing(GenerationHistory history, Path pack) throws IOException {
        return history.stageUpdateReplacingStalePending(
                pack,
                fingerprint(pack),
                contract(),
                GenerationRegistryContract.empty(),
                256
        );
    }

    private static void claimSemantics(GenerationHistory history, int chunkX, int chunkZ, long activationId)
            throws IOException {
        try (GenerationHistory.GenerationStage stage = history.openStage(chunkX, chunkZ)) {
            assertTrue(history.claimGeneratedSemantics(stage,
                    ChunkGenerationSemantics.builder(chunkX, chunkZ, activationId).addObject("object").seal().build()));
        }
    }

    private static SavedBiomeChunk savedBiomes(int chunkX, int chunkZ, long activationId) {
        SavedBiomeChunk.Cell cell = new SavedBiomeChunk.Cell(activationId, "biome/main", "region/main");
        SavedBiomeChunk.Column column = new SavedBiomeChunk.Column(cell, cell,
                List.of(new SavedBiomeChunk.Span(-64, 320, cell)));
        SavedBiomeChunk.Builder builder = SavedBiomeChunk.builder(
                new SavedBiomeChunk.Header(chunkX, chunkZ, activationId, -64, 384));
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                builder.column(x, z, column);
            }
        }
        return builder.build();
    }
}
