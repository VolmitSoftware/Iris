package art.arcane.iris.platform.bukkit;

import org.bukkit.plugin.Plugin;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

public class BukkitPlatformHostReleaseTest {
    @After
    public void clearHost() {
        BukkitPlatform.releaseHost();
    }

    @Test
    public void releasingTheHostDropsThePluginTheHudAndTheBridges() {
        BukkitPlatform.hostPlugin(mock(Plugin.class));
        BukkitPlatform.hostConsoleSender(() -> null);

        BukkitPlatform.releaseHost();

        assertFalse(BukkitPlatform.hasPlugin());
        assertFalse(BukkitPlatform.hasHud());
        assertThrows(IllegalStateException.class, BukkitPlatform::console);
    }
}
