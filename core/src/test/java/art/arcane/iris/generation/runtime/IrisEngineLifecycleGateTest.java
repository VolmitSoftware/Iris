package art.arcane.iris.generation.runtime;

import org.junit.Test;

import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisEngineLifecycleGateTest {
    @Test
    public void studioSessionSealDoesNotMeanWorldShutdown() {
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.isShuttingDown()).thenCallRealMethod();
        when(engine.isClosing()).thenReturn(true);
        engine.lifecycleState = IrisEngine.LifecycleState.HOTLOADING;

        assertTrue(engine.isClosing());
        assertFalse(engine.isShuttingDown());

        engine.lifecycleState = IrisEngine.LifecycleState.CLOSING;
        assertTrue(engine.isShuttingDown());
        engine.lifecycleState = IrisEngine.LifecycleState.CLOSED;
        assertTrue(engine.isShuttingDown());
        engine.lifecycleState = IrisEngine.LifecycleState.FAILED;
        assertTrue(engine.isShuttingDown());
    }

    @Test
    public void closedWorldRejectsNavigationRegardlessOfLifecyclePublication() {
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.isShuttingDown()).thenCallRealMethod();
        engine.lifecycleState = IrisEngine.LifecycleState.RUNNING;
        engine.closed = true;

        assertTrue(engine.isShuttingDown());
    }

    @Test
    public void incompleteBackgroundDrainBlocksResourceRelease() {
        EngineBackgroundTasks.BackgroundTaskDrain drain = new EngineBackgroundTasks.BackgroundTaskDrain(
                new TimeoutException("still running"), false);

        assertFalse(drain.allowsResourceRelease());
    }

    @Test
    public void completedFailedTaskAllowsSafeResourceRelease() {
        EngineBackgroundTasks.BackgroundTaskDrain drain = new EngineBackgroundTasks.BackgroundTaskDrain(
                new IllegalStateException("completed exceptionally"), true);

        assertTrue(drain.allowsResourceRelease());
    }
}
