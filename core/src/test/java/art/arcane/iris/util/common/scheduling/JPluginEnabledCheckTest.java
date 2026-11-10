package art.arcane.iris.util.common.scheduling;

import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JPluginEnabledCheckTest {
    private final Plugin plugin = mock(Plugin.class);
    private final PluginManager manager = mock(PluginManager.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);

    @Before
    public void bindBukkitHost() {
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.platformName()).thenReturn("bukkit");
        IrisPlatforms.bind(platform);
        BukkitPlatform.hostPlugin(plugin);
    }

    @After
    public void unbindBukkitHost() {
        BukkitPlatform.hostPlugin(null);
        IrisPlatforms.unbind();
    }

    @Test
    public void enabledCheckReadsThePluginWithoutTheManagerMonitor() {
        when(plugin.isEnabled()).thenReturn(true);

        scheduleAsync();

        verify(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        verify(manager, never()).isPluginEnabled(any(Plugin.class));
    }

    @Test
    public void disabledPluginRejectsSchedulingWithoutTheManagerMonitor() {
        when(plugin.isEnabled()).thenReturn(false);

        scheduleAsync();

        verify(scheduler, never()).runTaskAsynchronously(any(Plugin.class), any(Runnable.class));
        verify(manager, never()).isPluginEnabled(any(Plugin.class));
    }

    private void scheduleAsync() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<FoliaScheduler> folia = mockStatic(FoliaScheduler.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            J.aBukkit(() -> {
            });
        }
    }
}
