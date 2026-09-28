package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.IrisWorld;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisEngineGenerationHistorySyncTest {
    @Test
    public void syncForcesTheAttachedHistory() throws Exception {
        GenerationHistory history = mock(GenerationHistory.class);
        IrisEngine engine = engineWith(history);
        engine.syncGenerationHistory();
        verify(history).sync();
    }

    @Test
    public void syncWithoutAnAttachedHistoryDoesNothing() {
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.empty());
        doCallRealMethod().when(engine).syncGenerationHistory();
        engine.syncGenerationHistory();
    }

    @Test
    public void failedSyncSurfacesTheStorageFailure() throws Exception {
        GenerationHistory history = mock(GenerationHistory.class);
        IOException failure = new IOException("history force failed");
        doThrow(failure).when(history).sync();
        IrisEngine engine = engineWith(history);
        when(engine.getWorld()).thenReturn(IrisWorld.builder().name("world").build());
        assertSame(failure, assertThrows(IllegalStateException.class, engine::syncGenerationHistory).getCause());
    }

    private static IrisEngine engineWith(GenerationHistory history) {
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        when(router.history()).thenReturn(history);
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        doCallRealMethod().when(engine).syncGenerationHistory();
        return engine;
    }
}
