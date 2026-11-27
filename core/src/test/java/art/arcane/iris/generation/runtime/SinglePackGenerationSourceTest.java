package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.GenerationBoundary;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SinglePackGenerationSourceTest {
    private final GenerationSource source = GenerationSource.singlePack();

    @Test
    public void everyChunkGeneratesWithTheCurrentPackWithoutARoute() throws Exception {
        assertFalse(source.keepsPackVersions());
        assertNull(source.openRoute(-72, -15));
        assertTrue(source.currentRouteOwnership().isEmpty());
        assertFalse(source.predatesActivePack(1L));
    }

    @Test
    public void scopesBindNothingAndRecordNothing() throws Exception {
        try (GenerationScope scope = source.openGenerationScope(-1152, -240)) {
            assertFalse(scope.claimGeneratedSemantics());
        }
        try (GenerationScope scope = source.openReadScope(-1152, -240)) {
            assertFalse(scope.claimGeneratedSemantics());
        }
    }

    @Test
    public void rolesAreSharedAcrossCalls() {
        assertSame(source.savedBiomes(), source.savedBiomes());
        assertSame(source.recordedChunks(), source.recordedChunks());
        assertSame(source.transitions(), source.transitions());
    }

    @Test
    public void savedBiomesAreAlwaysAbsent() throws Exception {
        SavedBiomes biomes = source.savedBiomes();

        assertTrue(biomes.at(0, 64, 0, true).isEmpty());
        assertTrue(biomes.caveBaseAt(0, 0).isEmpty());
        assertTrue(biomes.subterrainCellAt(0, 0, 0).isEmpty());
        assertEquals(Optional.empty(), biomes.readChunk(0, 0).join());
        assertEquals("absent", biomes.readSurface(0, 0, saved -> saved.isEmpty() ? "absent" : "saved"));
        assertFalse(biomes.mayHaveBlockDropRules("minecraft:stone"));
        biomes.prepareChunk(0, 0);
    }

    @Test
    public void noChunkWasRecordedByAnEarlierPack() {
        RecordedChunks recorded = source.recordedChunks();

        assertFalse(recorded.ownedByEarlierPack(-72, -15));
        assertFalse(recorded.hasUnrecordedEarlierPackChunkInSquare(0, 0, 8));
        assertTrue(recorded.semantics(0, 0).isEmpty());
        assertTrue(recorded.findNearest(null, record -> true).isEmpty());
    }

    @Test
    public void transitionsCarryNothingAndAllowEveryFootprint() throws Exception {
        PackTransitions transitions = source.transitions();

        assertEquals(List.of(), transitions.objectContinuations(null, 0, 0));
        assertTrue(transitions.allowsObjectFootprint(null, -8, -8, 8, 8));
        transitions.recordNaturalTerrain(null, null);
    }

    @Test
    public void regenerationAndSyncHaveNothingToRelease() throws Exception {
        source.releaseForRegeneration(List.of(new GenerationBoundary.ChunkCoordinate(-72, -15)));
        source.sync();
    }
}
