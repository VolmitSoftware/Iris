package art.arcane.iris.core.service;

import art.arcane.iris.core.link.MultiverseCoreLink;
import art.arcane.iris.core.link.MultiverseGuardListener;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.util.common.plugin.VolmitPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MultiverseSVCTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @After
    public void unbindLink() {
        IrisServices.remove(MultiverseCoreLink.class);
    }

    @Test
    public void anAbsentMultiverseLeavesTheGuardUnregistered() {
        MultiverseCoreLink link = bindLink(false);
        VolmitPlugin plugin = mock(VolmitPlugin.class);

        run(plugin, service -> service.onEnable());

        verify(plugin, never()).registerListener(any());
        verify(link, never()).reconcileOwnedWorlds();
    }

    @Test
    public void anAlreadyEnabledMultiverseIsPickedUpAtServiceEnable() {
        MultiverseCoreLink link = bindLink(true);
        VolmitPlugin plugin = mock(VolmitPlugin.class);

        run(plugin, service -> service.onEnable());

        verify(plugin).registerListener(isA(MultiverseGuardListener.class));
        verify(link).reconcileOwnedWorlds();
    }

    @Test
    public void multiverseEnablingAfterIrisRegistersTheGuardExactlyOnce() {
        MultiverseCoreLink link = bindLink(true);
        VolmitPlugin plugin = mock(VolmitPlugin.class);

        run(plugin, service -> {
            service.onEnable();
            service.onPluginEnable(new PluginEnableEvent(namedPlugin("Multiverse-Core")));
        });

        verify(plugin, times(1)).registerListener(any(Listener.class));
        verify(link, times(1)).reconcileOwnedWorlds();
    }

    @Test
    public void anUnrelatedPluginNeverTouchesTheMultiverseLink() {
        MultiverseCoreLink link = bindLink(true);
        VolmitPlugin plugin = mock(VolmitPlugin.class);

        run(plugin, service -> {
            service.onPluginEnable(new PluginEnableEvent(namedPlugin("WorldEdit")));
            service.onPluginDisable(new PluginDisableEvent(namedPlugin("WorldEdit")));
        });

        verify(plugin, never()).registerListener(any());
        verify(link, never()).reconcileOwnedWorlds();
    }

    @Test
    public void multiverseDisablingTakesTheGuardBackDown() {
        bindLink(true);
        VolmitPlugin plugin = mock(VolmitPlugin.class);

        run(plugin, service -> {
            service.onEnable();
            service.onPluginDisable(new PluginDisableEvent(namedPlugin("Multiverse-Core")));
        });

        verify(plugin).unregisterListener(isA(MultiverseGuardListener.class));
    }

    private static MultiverseCoreLink bindLink(boolean active) {
        MultiverseCoreLink link = mock(MultiverseCoreLink.class);
        when(link.isActive()).thenReturn(active);
        IrisServices.register(MultiverseCoreLink.class, link);
        return link;
    }

    private static Plugin namedPlugin(String name) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn(name);
        return plugin;
    }

    private static void run(VolmitPlugin plugin, java.util.function.Consumer<MultiverseSVC> body) {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<BukkitPlatform> platform = mockStatic(BukkitPlatform.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            platform.when(BukkitPlatform::volmitPlugin).thenReturn(plugin);
            body.accept(new MultiverseSVC());
        }
    }
}
