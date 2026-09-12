package art.arcane.iris.world;

import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Objects;
import java.util.UUID;

public final class PaperWorldReplacementEntryListener implements Listener {
    private final PendingWorldReplacementManager manager;

    public PaperWorldReplacementEntryListener(PendingWorldReplacementManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        UUID playerId = event.getConnection().getProfile().getId();
        if (playerId == null) {
            return;
        }
        try {
            PendingWorldReplacementManager.ReplacementEntryRedirect redirect = manager.prepareReplacementEntry(
                    playerId,
                    event.getSpawnLocation(),
                    event.isNewPlayer()
            );
            if (redirect == null) {
                return;
            }
            Location location = redirect.location();
            event.setSpawnLocation(location);
            if (redirect.acknowledgementRequired()) {
                manager.expectReplacementEntryAcknowledgement(playerId, redirect.transactionId());
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            refuseUnsafeEntry(event, playerId, failure);
        } catch (Throwable failure) {
            refuseUnsafeEntry(event, playerId, failure);
        }
    }

    private void refuseUnsafeEntry(AsyncPlayerSpawnLocationEvent event, UUID playerId, Throwable failure) {
        manager.reportUnsafeEntry(playerId, failure);
        event.getConnection().disconnect(Component.text(
                "Iris could not verify a safe login location after the Overworld replacement. Retry after startup completes."
        ));
    }
}
