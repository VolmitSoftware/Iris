package art.arcane.iris.engine.framework;

import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class IrisEngineModeLifecycleTest {
    @Test
    public void failedContentStageRemainsRetryable() {
        IrisEngineMode mode = new IrisEngineMode(mock(Engine.class)) {
        };
        EngineStage content = mock(EngineStage.class);
        EngineStage terrain = mock(EngineStage.class);
        mode.registerStage(content);
        mode.registerTerrainStage(terrain);
        IllegalStateException failure = new IllegalStateException("Stage close failed");
        doThrow(failure).doNothing().when(content).close();

        assertSame(failure, assertThrows(IllegalStateException.class, mode::close));
        verifyNoInteractions(terrain);
        mode.close();
        mode.close();

        assertTrue(mode.getStages().isEmpty());
        assertTrue(mode.getTerrainStages().isEmpty());
        verify(content, times(2)).close();
        verify(terrain).close();
    }

    @Test
    public void failedTerrainStageDoesNotRepeatReleasedContent() {
        IrisEngineMode mode = new IrisEngineMode(mock(Engine.class)) {
        };
        EngineStage content = mock(EngineStage.class);
        EngineStage terrain = mock(EngineStage.class);
        mode.registerStage(content);
        mode.registerTerrainStage(terrain);
        IllegalStateException failure = new IllegalStateException("Terrain stage close failed");
        doThrow(failure).doNothing().when(terrain).close();

        assertSame(failure, assertThrows(IllegalStateException.class, mode::close));
        assertTrue(mode.getStages().isEmpty());
        mode.close();
        mode.close();

        assertTrue(mode.getTerrainStages().isEmpty());
        verify(content).close();
        verify(terrain, times(2)).close();
    }
}
