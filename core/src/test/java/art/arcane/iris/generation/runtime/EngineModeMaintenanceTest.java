package art.arcane.iris.generation.runtime;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EngineModeMaintenanceTest {
    @Test
    public void maintenanceDisablesContextCacheWithoutActivePregen() {
        assertTrue(EngineMode.shouldDisableContextCacheForMaintenance(true, false));
    }

    @Test
    public void maintenanceKeepsContextCacheForActivePregenWorld() {
        assertFalse(EngineMode.shouldDisableContextCacheForMaintenance(true, true));
    }

    @Test
    public void noMaintenanceKeepsContextCacheEnabled() {
        assertFalse(EngineMode.shouldDisableContextCacheForMaintenance(false, false));
        assertFalse(EngineMode.shouldDisableContextCacheForMaintenance(false, true));
    }
}
