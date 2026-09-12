package art.arcane.iris.world;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class IrisStartupAdmissionListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        IrisStartupValidation.denialReason().ifPresent(reason -> event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                reason + " Check the server console, correct the reported Iris state, and restart."));
    }
}
