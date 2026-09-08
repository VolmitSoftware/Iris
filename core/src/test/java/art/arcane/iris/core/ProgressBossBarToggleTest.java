package art.arcane.iris.core;

import art.arcane.iris.platform.bukkit.BukkitPlatform;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ProgressBossBarToggleTest {
    @Test
    public void progressBossBarsAreEnabledByDefault() {
        assertTrue(new IrisSettings.IrisSettingsGeneral().isProgressBossBar());
    }

    @Test
    public void showProgressLaneNeverTouchesTheLaneServiceWhenDisabled() {
        withProgressBossBar(false, () ->
                BukkitPlatform.showProgressLane(null, "iris:job", "Working", 0.5D, 4000L));
    }

    @Test
    public void showProgressLaneStillReachesTheLaneServiceWhenEnabled() {
        withProgressBossBar(true, () -> assertThrows(IllegalStateException.class,
                () -> BukkitPlatform.showProgressLane(null, "iris:job", "Working", 0.5D, 4000L)));
    }

    private void withProgressBossBar(boolean enabled, Runnable body) {
        IrisSettings previous = IrisSettings.settings;
        try {
            IrisSettings live = new IrisSettings();
            live.getGeneral().setProgressBossBar(enabled);
            IrisSettings.settings = live;
            assertFalse("another test hosted a HUD; this test needs an unhosted platform",
                    BukkitPlatform.hasHud());
            body.run();
        } finally {
            IrisSettings.settings = previous;
        }
    }
}
