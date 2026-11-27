package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.GenerationBoundary;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SinglePackGenerationSourceTest {
    private final GenerationSource source = GenerationSource.singlePack();

    @Test
    public void everyChunkGeneratesWithTheCurrentPackWithoutARoute() throws Exception {
        assertFalse(source.keepsPackVersions());
        assertNull(source.openRoute(-72, -15));
        assertTrue(source.currentRouteOwnership().isEmpty());
        assertFalse(source.ownedByEarlierPack(-72, -15));
        assertFalse(source.predatesActivePack(1L));
        assertFalse(source.hasUnrecordedEarlierPackChunkInSquare(0, 0, 8));
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
    public void savedStateIsAlwaysAbsent() throws Exception {
        assertTrue(source.recordedSemantics(0, 0).isEmpty());
        assertTrue(source.findRecorded(null, record -> true).isEmpty());
        assertTrue(source.savedBiome(0, 64, 0, true).isEmpty());
        assertTrue(source.savedCaveBase(0, 0).isEmpty());
        assertTrue(source.savedSubterrainCell(0, 0, 0).isEmpty());
        assertEquals(Optional.empty(), source.readSavedBiomes(0, 0).join());
        assertEquals("absent", source.readSavedSurfaceBiome(0, 0, saved -> saved.isEmpty() ? "absent" : "saved"));
        assertFalse(source.savedBiomesMayHaveBlockDropRules("minecraft:stone"));
        assertEquals(List.of(), source.objectContinuations(null, 0, 0));
        assertTrue(source.allowsObjectFootprint(null, -8, -8, 8, 8));
    }

    @Test
    public void regenerationAndSyncHaveNothingToRelease() throws Exception {
        source.releaseForRegeneration(List.of(new GenerationBoundary.ChunkCoordinate(-72, -15)));
        source.recordNaturalTerrain(null, null);
        source.sync();
    }
}
