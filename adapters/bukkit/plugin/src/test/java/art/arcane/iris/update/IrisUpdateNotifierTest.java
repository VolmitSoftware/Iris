package art.arcane.iris.update;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.volmlib.util.update.BukkitUpdateService;
import org.bukkit.plugin.Plugin;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class IrisUpdateNotifierTest {
    @Test
    public void acceptedSettingsReconfigureChecksAndClosingRemovesTheListener() {
        IrisSettings previous = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        Plugin plugin = mock(Plugin.class);
        BukkitUpdateService service = mock(BukkitUpdateService.class);
        try (MockedStatic<BukkitUpdateService> registration = mockStatic(BukkitUpdateService.class)) {
            registration.when(() -> BukkitUpdateService.register(eq(plugin), any())).thenReturn(service);
            IrisUpdateNotifier notifier = new IrisUpdateNotifier(plugin);
            try {
                ArgumentCaptor<BukkitUpdateService.Options> options = ArgumentCaptor.forClass(BukkitUpdateService.Options.class);
                registration.verify(() -> BukkitUpdateService.register(eq(plugin), options.capture()));
                assertEquals("VolmitSoftware", options.getValue().owner());
                assertEquals("Iris", options.getValue().repository());
                assertEquals("iris.update", options.getValue().permission());
                assertTrue(options.getValue().enabled().getAsBoolean());

                clearInvocations(service);
                assertFalse(IrisSettings.applyHotloadSnapshot(
                        "{\"general\":{\"updateNotifications\":false}}", candidate -> false));
                assertTrue(options.getValue().enabled().getAsBoolean());
                verifyNoInteractions(service);

                IrisSettings.installHotloadSnapshot("{\"general\":{\"updateNotifications\":false}}");
                assertFalse(options.getValue().enabled().getAsBoolean());
                verify(service).reconfigure();
                IrisSettings.settings.getGeneral().setUpdateNotifications(true);
                assertFalse(options.getValue().enabled().getAsBoolean());
            } finally {
                notifier.close();
            }
            verify(service).close();
            clearInvocations(service);
            IrisSettings.installHotloadSnapshot("{}");
            verifyNoInteractions(service);
        } finally {
            IrisSettings.settings = previous;
        }
    }
}
