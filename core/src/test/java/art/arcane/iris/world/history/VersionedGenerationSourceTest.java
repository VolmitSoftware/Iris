package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.GenerationScope;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        assertTrue(source.ownedByEarlierPack(-72, -15));
        assertFalse(source.ownedByEarlierPack(0, 0));
        assertSame(sealed, source.recordedSemantics(0, 0).orElseThrow());
        assertTrue(source.recordedSemantics(1, 0).isEmpty());
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
        assertTrue(source.recordedSemantics(0, 0).isEmpty());
        assertTrue(source.currentRouteOwnership().isEmpty());
        verify(router, never()).openCoordinateScope(anyInt(), anyInt());
    }
}
