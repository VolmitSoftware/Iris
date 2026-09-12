package art.arcane.iris.world.history;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GenerationOrphanRecoveryTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void removesOnlySelectedOwnershipWithoutStoredNativeTerrain() throws Exception {
        Path root = temporaryFolder.newFolder("ownership").toPath();
        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(root);
        ownership.assign(-33, -1, 2L);
        ownership.assign(-32, -1, 2L);
        ownership.assign(0, 0, 1L);
        ownership.persist();
        WorldChunkInventory stored = WorldChunkInventory.ofPackedChunks(ChunkGenerationOwnership.packChunk(-32, -1));

        assertEquals(1, ownership.discardUnstoredClaims(stored, Set.of(2L)));
        ChunkGenerationOwnership reopened = ChunkGenerationOwnership.load(root);
        assertFalse(reopened.isExplicitlyAssigned(-33, -1));
        assertTrue(reopened.isExplicitlyAssigned(-32, -1));
        assertEquals(1L, reopened.explicitActivation(0, 0));
        assertEquals(2, reopened.explicitChunkCount());
        assertEquals(0, reopened.discardUnstoredClaims(stored, Set.of(2L)));
        reopened.assign(-33, -1, 3L);
        reopened.persist();
        assertEquals(3L, ChunkGenerationOwnership.load(root).explicitActivation(-33, -1));
    }

    @Test
    public void journalClaimsCannotResurrectAfterRecoveryAndNewGeneration() throws Exception {
        Path root = temporaryFolder.newFolder("semantics").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.load(root);
        index.claimAndPersist(ChunkGenerationSemantics.builder(-33, -1, 2L).addObject("orphan").seal().build());
        index.claimAndPersist(ChunkGenerationSemantics.builder(-32, -1, 2L).addObject("saved").seal().build());
        index.claimAndPersist(ChunkGenerationSemantics.builder(0, 0, 1L).addObject("older").seal().build());
        WorldChunkInventory stored = WorldChunkInventory.ofPackedChunks(ChunkGenerationOwnership.packChunk(-32, -1));

        assertEquals(1, index.discardUnstoredClaims(stored, Set.of(2L)));
        GenerationSemanticIndex reopened = GenerationSemanticIndex.load(root);
        assertTrue(reopened.get(-33, -1).isEmpty());
        assertTrue(reopened.hasSealedClaim(-32, -1, 2L));
        assertTrue(reopened.hasSealedClaim(0, 0, 1L));
        assertEquals(2, reopened.recordCount());
        assertEquals(0, reopened.discardUnstoredClaims(stored, Set.of(2L)));
        reopened.claimAndPersist(ChunkGenerationSemantics.builder(-33, -1, 3L).addObject("replacement").seal().build());
        GenerationSemanticIndex replaced = GenerationSemanticIndex.load(root);
        assertTrue(replaced.hasSealedClaim(-33, -1, 3L));
        assertEquals(Set.of("replacement"), replaced.get(-33, -1).orElseThrow().objectKeys());
    }
}
