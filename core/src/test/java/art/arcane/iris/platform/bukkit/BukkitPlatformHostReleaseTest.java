package art.arcane.iris.platform.bukkit;

import org.bukkit.plugin.Plugin;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;

/**
 * The host statics are how core reaches the plugin. Nothing cleared them on disable, so the disabled
 * plugin instance, its console sender and its log bridge stayed reachable from a static for the life of
 * the JVM.
 */
public class BukkitPlatformHostReleaseTest {
    @After
    public void clearHost() {
        BukkitPlatform.releaseHost();
    }

    @Test
    public void releasingTheHostDropsThePluginAndTheHud() {
        BukkitPlatform.hostPlugin(mock(Plugin.class));

        BukkitPlatform.releaseHost();

        assertFalse(BukkitPlatform.hasPlugin());
        assertFalse(BukkitPlatform.hasHud());
    }
}
