package art.arcane.iris.core.runtime;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;

public class StudioOpenCoordinatorSpawnStuckRegressionTest {
    @Test
    public void legacySafeEntryRetryLoopIsRemoved() {
        boolean found = Arrays.stream(StudioOpenCoordinator.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("waitForSafeEntry"));
        assertFalse("waitForSafeEntry retry loop must remain removed", found);
    }

    @Test
    public void requestEntryChunkRedundantLoopIsRemoved() {
        boolean found = Arrays.stream(StudioOpenCoordinator.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("requestEntryChunk"));
        assertFalse("requestEntryChunk must be removed — createLevel already loads (0,0)", found);
    }

    @Test
    public void waitForEntryChunkRedundantLoopIsRemoved() {
        boolean found = Arrays.stream(StudioOpenCoordinator.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("waitForEntryChunk"));
        assertFalse("waitForEntryChunk retry loop must be removed", found);
    }
}
