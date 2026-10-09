package art.arcane.iris.update;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.volmlib.util.update.BukkitUpdateService;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

public final class IrisUpdateNotifier implements AutoCloseable {
    private final Consumer<IrisSettings> settingsListener = this::reconfigure;
    private final BukkitUpdateService updates;
    private volatile boolean enabled;
    private boolean closed;

    public IrisUpdateNotifier(Plugin plugin) {
        enabled = IrisSettings.get().getGeneral().isUpdateNotifications();
        updates = BukkitUpdateService.register(plugin, new BukkitUpdateService.Options(
                "VolmitSoftware", "Iris", "iris.update", () -> enabled));
        IrisSettings.addSettingsListener(settingsListener);
        reconfigure(IrisSettings.get());
    }

    private synchronized void reconfigure(IrisSettings settings) {
        if (closed) {
            return;
        }
        enabled = settings.getGeneral().isUpdateNotifications();
        updates.reconfigure();
    }

    @Override
    public synchronized void close() {
        closed = true;
        IrisSettings.removeSettingsListener(settingsListener);
        updates.close();
    }
}
