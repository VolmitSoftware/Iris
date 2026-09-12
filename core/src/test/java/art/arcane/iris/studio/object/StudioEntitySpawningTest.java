package art.arcane.iris.studio.object;

import art.arcane.iris.configuration.IrisSettings;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudioEntitySpawningTest {
    @Test
    public void studioEntitySpawningDefaultsToEnabled() {
        assertTrue(new IrisSettings.IrisSettingsStudio().isEntitySpawning());
    }

    @Test
    public void objectStudioDoesNotCancelEntitySpawns() {
        boolean hasSpawnCanceller = Arrays.stream(ObjectStudioSaveService.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("onCreatureSpawn")
                        || method.getName().equals("onEntitySpawn"));

        assertFalse(hasSpawnCanceller);
    }
}
