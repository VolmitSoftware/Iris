package art.arcane.iris.integration;

import art.arcane.iris.Iris;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisPapiLifecycleTest {
    @Test
    public void lateEnableRegistersOnceAndReattachesAfterDisable() throws Exception {
        Iris iris = plugin(true);
        Plugin placeholders = namedPlugin("PlaceholderAPI");
        PluginManager plugins = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<HandlerList> handlers = mockStatic(HandlerList.class);
             MockedStatic<PlaceholderRegistration> availability = mockStatic(PlaceholderRegistration.class);
             MockedStatic<IrisPapiInstaller> installer = mockStatic(IrisPapiInstaller.class);
             MockedConstruction<PlaceholderRegistration> registrations = mockConstruction(PlaceholderRegistration.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            availability.when(PlaceholderRegistration::isPlaceholderApiEnabled).thenReturn(true);
            installer.when(() -> IrisPapiInstaller.install(any(), any(), any())).thenReturn(true);

            iris.onPlaceholderApiEnable(new PluginEnableEvent(placeholders));
            iris.onPlaceholderApiEnable(new PluginEnableEvent(placeholders));
            assertEquals(1, registrations.constructed().size());
            verify(plugins).registerEvents(any(IrisPapiListener.class), eq(iris));

            iris.onPlaceholderApiDisable(new PluginDisableEvent(namedPlugin("Unrelated")));
            verify(registrations.constructed().getFirst(), never()).unregister();

            iris.onPlaceholderApiDisable(new PluginDisableEvent(placeholders));
            verify(registrations.constructed().getFirst()).unregister();
            handlers.verify(() -> HandlerList.unregisterAll(any(IrisPapiListener.class)));

            iris.onPlaceholderApiEnable(new PluginEnableEvent(placeholders));
            assertEquals(2, registrations.constructed().size());
            verify(plugins, times(2)).registerEvents(any(IrisPapiListener.class), eq(iris));
        }
    }

    @Test
    public void enableEventsCannotRegisterBeforeIrisIsReadyOrDuringShutdown() throws Exception {
        Iris iris = plugin(false);
        Plugin placeholders = namedPlugin("PlaceholderAPI");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<IrisPapiInstaller> installer = mockStatic(IrisPapiInstaller.class)) {
            iris.onPlaceholderApiEnable(new PluginEnableEvent(placeholders));
            installer.verifyNoInteractions();
        }
    }

    @Test
    public void unrelatedEnableEventsDoNotRegisterPlaceholders() throws Exception {
        Iris iris = plugin(true);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<IrisPapiInstaller> installer = mockStatic(IrisPapiInstaller.class)) {
            iris.onPlaceholderApiEnable(new PluginEnableEvent(namedPlugin("Unrelated")));
            installer.verifyNoInteractions();
        }
    }

    private static Iris plugin(boolean active) throws ReflectiveOperationException {
        Iris iris = mock(Iris.class);
        when(iris.getLogger()).thenReturn(Logger.getAnonymousLogger());
        doCallRealMethod().when(iris).onPlaceholderApiEnable(any());
        doCallRealMethod().when(iris).onPlaceholderApiDisable(any());
        Field lifecycle = Iris.class.getDeclaredField("papiLifecycleActive");
        lifecycle.setAccessible(true);
        lifecycle.setBoolean(iris, active);
        return iris;
    }

    private static Plugin namedPlugin(String name) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn(name);
        return plugin;
    }
}
