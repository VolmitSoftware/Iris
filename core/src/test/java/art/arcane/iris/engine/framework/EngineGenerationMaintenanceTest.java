package art.arcane.iris.engine.framework;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EngineGenerationMaintenanceTest {
    @Test
    public void maintenanceDisablesContextCacheWithoutActivePregen() {
        assertTrue(EngineGeneration.shouldDisableContextCacheForMaintenance(true, false));
    }

    @Test
    public void maintenanceKeepsContextCacheForActivePregenWorld() {
        assertFalse(EngineGeneration.shouldDisableContextCacheForMaintenance(true, true));
    }

    @Test
    public void noMaintenanceKeepsContextCacheEnabled() {
        assertFalse(EngineGeneration.shouldDisableContextCacheForMaintenance(false, false));
        assertFalse(EngineGeneration.shouldDisableContextCacheForMaintenance(false, true));
    }
}
