package art.arcane.iris.world.history;

import art.arcane.iris.generation.mantle.ObjectContinuationBundle;
import art.arcane.iris.generation.runtime.BiomeEnvironment;
import art.arcane.iris.generation.runtime.GenerationScope;
import art.arcane.iris.generation.runtime.PackTransitions;
import art.arcane.iris.generation.runtime.RecordedChunks;
import art.arcane.iris.generation.runtime.SavedBiomes;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class VersionedGenerationSourceTest {
    private static final String DETACHED = "Iris generation-history runtime router is detached.";

    @Test
    public void scopesAndRoutesComeFromTheOwningActivation() throws Exception {
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        GenerationHistoryRuntimeRouter.CoordinateScope scope = mock(GenerationHistoryRuntimeRouter.CoordinateScope.class);
        GenerationHistoryRuntimeRouter.RuntimeRoute route = mock(GenerationHistoryRuntimeRouter.RuntimeRoute.class);
        when(router.openCoordinateScope(-1152, -240)).thenReturn(scope);
        when(router.openRoute(-72, -15)).thenReturn(route);
        VersionedGenerationSource source = new VersionedGenerationSource(router);

        assertTrue(source.keepsPackVersions());
        assertSame(scope, source.openGenerationScope(-1152, -240));
        assertSame(route, source.openRoute(-72, -15));
    }

    @Test
    public void ownershipQuestionsReadTheHistory() {
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationActivation active = mock(GenerationActivation.class);
        ChunkGenerationSemantics sealed = mock(ChunkGenerationSemantics.class);
        ChunkGenerationSemantics unsealed = mock(ChunkGenerationSemantics.class);
        when(router.history()).thenReturn(history);
        when(history.activeActivation()).thenReturn(active);
        when(active.activationId()).thenReturn(3L);
        when(history.isHistoricallyOwned(-72, -15)).thenReturn(true);
        when(sealed.sealed()).thenReturn(true);
        when(history.semantics(0, 0)).thenReturn(Optional.of(sealed));
        when(history.semantics(1, 0)).thenReturn(Optional.of(unsealed));
        VersionedGenerationSource source = new VersionedGenerationSource(router);

        assertTrue(source.predatesActivePack(2L));
        assertFalse(source.predatesActivePack(3L));
        assertTrue(source.recordedChunks().ownedByEarlierPack(-72, -15));
        assertFalse(source.recordedChunks().ownedByEarlierPack(0, 0));
        assertSame(sealed, source.recordedChunks().semantics(0, 0).orElseThrow());
        assertTrue(source.recordedChunks().semantics(1, 0).isEmpty());
    }

    @Test
    public void rolesAreSharedAcrossCalls() {
        VersionedGenerationSource source = new VersionedGenerationSource(mock(GenerationHistoryRuntimeRouter.class));

        assertSame(source.savedBiomes(), source.savedBiomes());
        assertSame(source.recordedChunks(), source.recordedChunks());
        assertSame(source.transitions(), source.transitions());
    }

    @Test
    public void recordedChunksReadTheHistory() {
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationSemanticIndex.Match match = mock(GenerationSemanticIndex.Match.class);
        when(router.history()).thenReturn(history);
        when(history.hasHistoricalFallbackInSquare(0, 0, 8)).thenReturn(true);
        when(history.findRecorded(any(), any())).thenReturn(Optional.of(match));
        RecordedChunks recorded = new VersionedGenerationSource(router).recordedChunks();

        assertTrue(recorded.hasUnrecordedEarlierPackChunkInSquare(0, 0, 8));
        assertFalse(recorded.hasUnrecordedEarlierPackChunkInSquare(1, 1, 8));
        assertSame(match, recorded.findNearest(null, record -> true).orElseThrow());
    }

    @Test
    public void savedBiomesReadTheSavedBiomeRuntime() throws Exception {
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        SavedBiomeRuntime saved = mock(SavedBiomeRuntime.class);
        BiomeEnvironment surface = mock(BiomeEnvironment.class);
        BiomeEnvironment caveBase = mock(BiomeEnvironment.class);
        SubterrainCell cell = mock(SubterrainCell.class);
        SavedBiomeRuntime.ReadSession session = mock(SavedBiomeRuntime.ReadSession.class);
        when(router.biomes()).thenReturn(saved);
        when(saved.resolve(-20, 64, 18, true)).thenReturn(Optional.of(surface));
        when(saved.resolveCaveBase(-20, 18)).thenReturn(Optional.of(caveBase));
        when(saved.subterrainCell(-20, 12, 18)).thenReturn(Optional.of(cell));
        when(saved.readSurfaceBiome(eq(-20), eq(18), any())).thenReturn("saved");
        when(saved.readChunkAsync(-2, 1)).thenReturn(CompletableFuture.completedFuture(session));
        when(saved.mayHaveBlockDropRules("minecraft:stone")).thenReturn(true);
        SavedBiomes biomes = new VersionedGenerationSource(router).savedBiomes();

        assertSame(surface, biomes.at(-20, 64, 18, true).orElseThrow());
        assertSame(caveBase, biomes.caveBaseAt(-20, 18).orElseThrow());
        assertSame(cell, biomes.subterrainCellAt(-20, 12, 18).orElseThrow());
        assertEquals("saved", biomes.readSurface(-20, 18, ignored -> "unsaved"));
        assertSame(session, biomes.readChunk(-2, 1).join().orElseThrow());
        assertTrue(biomes.mayHaveBlockDropRules("minecraft:stone"));
        biomes.prepareChunk(-2, 1);
        verify(saved).prepareChunk(-2, 1);
    }

    @Test
    public void transitionsGoThroughTheRouter() throws Exception {
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        TransitionGenerationPlan plan = mock(TransitionGenerationPlan.class);
        List<GenerationHistoryRuntimeRouter.ObjectContinuation> fragments =
                List.of(new GenerationHistoryRuntimeRouter.ObjectContinuation(1L, null));
        List<Matter> decoded = List.of(mock(Matter.class));
        SavedTerrainChunk terrain = mock(SavedTerrainChunk.class);
        FloatingBiomeOverlay floating = mock(FloatingBiomeOverlay.class);
        when(router.objectContinuations(plan, -2, 1)).thenReturn(fragments);
        when(router.objectContinuations(plan, 0, 0)).thenReturn(List.of());
        when(router.decodeObjectContinuations(fragments)).thenReturn(decoded);
        when(router.allowsObjectFootprint(plan, new ObjectContinuationBundle.Bounds(-8, -8, 8, 8))).thenReturn(false);
        when(terrain.chunkX()).thenReturn(-2);
        when(terrain.chunkZ()).thenReturn(1);
        PackTransitions transitions = new VersionedGenerationSource(router).transitions();

        assertSame(decoded, transitions.objectContinuations(plan, -2, 1));
        assertEquals(List.of(), transitions.objectContinuations(plan, 0, 0));
        assertFalse(transitions.allowsObjectFootprint(plan, -8, -8, 8, 8));
        transitions.recordNaturalTerrain(terrain, floating);
        verify(router).recordNaturalTerrain(terrain);
        verify(router).recordFloatingBiomes(-2, 1, floating);
    }

    @Test
    public void regenerationReleasesChunksThroughTheRouter() throws Exception {
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        List<GenerationBoundary.ChunkCoordinate> chunks = List.of(new GenerationBoundary.ChunkCoordinate(-72, -15));
        new VersionedGenerationSource(router).releaseForRegeneration(chunks);

        verify(router).releaseForRegeneration(chunks);
    }

    @Test
    public void detachedSourceRefusesToRouteInsteadOfFallingBackToTheCurrentPack() throws Exception {
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        VersionedGenerationSource source = new VersionedGenerationSource(router);
        source.detach();

        assertTrue(source.keepsPackVersions());
        IllegalStateException scope = assertThrows(IllegalStateException.class,
                () -> { try (GenerationScope ignored = source.openGenerationScope(0, 0)) { } });
        assertEquals(DETACHED, scope.getMessage());
        assertEquals(DETACHED, assertThrows(IllegalStateException.class, () -> source.openRoute(0, 0)).getMessage());
        assertEquals(DETACHED, assertThrows(IllegalStateException.class, () -> source.openReadScope(0, 0)).getMessage());
        assertTrue(source.recordedChunks().semantics(0, 0).isEmpty());
        assertTrue(source.currentRouteOwnership().isEmpty());
        verify(router, never()).openCoordinateScope(anyInt(), anyInt());
    }

    @Test
    public void detachedRolesAnswerAsIfNothingWasSaved() throws Exception {
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        VersionedGenerationSource source = new VersionedGenerationSource(router);
        SavedBiomes biomes = source.savedBiomes();
        RecordedChunks recorded = source.recordedChunks();
        PackTransitions transitions = source.transitions();
        source.detach();

        assertFalse(source.predatesActivePack(0L));
        source.releaseForRegeneration(List.of(new GenerationBoundary.ChunkCoordinate(-72, -15)));
        source.sync();
        assertTrue(biomes.at(0, 64, 0, true).isEmpty());
        assertTrue(biomes.caveBaseAt(0, 0).isEmpty());
        assertTrue(biomes.subterrainCellAt(0, 0, 0).isEmpty());
        assertEquals("absent", biomes.readSurface(0, 0, saved -> saved.isEmpty() ? "absent" : "saved"));
        assertEquals(Optional.empty(), biomes.readChunk(0, 0).join());
        assertFalse(biomes.mayHaveBlockDropRules("minecraft:stone"));
        biomes.prepareChunk(0, 0);
        assertFalse(recorded.ownedByEarlierPack(-72, -15));
        assertFalse(recorded.hasUnrecordedEarlierPackChunkInSquare(0, 0, 8));
        assertTrue(recorded.semantics(0, 0).isEmpty());
        assertTrue(recorded.findNearest(null, record -> true).isEmpty());
        assertEquals(List.of(), transitions.objectContinuations(null, 0, 0));
        assertTrue(transitions.allowsObjectFootprint(null, -8, -8, 8, 8));
        transitions.recordNaturalTerrain(null, null);
        verifyNoInteractions(router);
    }
}
