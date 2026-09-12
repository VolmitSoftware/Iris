package art.arcane.iris.integration;

import art.arcane.iris.api.pregen.IrisPregenerationEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;
import java.util.UUID;

public final class IrisPapiListener implements Listener {
    private final IrisPapiState state;

    public IrisPapiListener(IrisPapiState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    static void track(IrisPapiState state, UUID playerId, Location location) {
        publish(state, playerId, location, false);
    }

    static void trackNow(IrisPapiState state, UUID playerId, Location location) {
        publish(state, playerId, location, true);
    }

    private static void publish(IrisPapiState state, UUID playerId, Location location, boolean immediate) {
        if (state == null || playerId == null || location == null) {
            return;
        }

        World world = location.getWorld();

        if (world == null) {
            return;
        }

        if (immediate) {
            state.trackPositionNow(playerId, world, location.getBlockX(), location.getBlockZ());
            return;
        }

        state.trackPosition(playerId, world, location.getBlockX(), location.getBlockZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        track(state, player == null ? null : player.getUniqueId(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        trackNow(state, player == null ? null : player.getUniqueId(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        trackNow(state, player == null ? null : player.getUniqueId(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        trackNow(state, player == null ? null : player.getUniqueId(), event.getRespawnLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        trackNow(state, player == null ? null : player.getUniqueId(), player == null ? null : player.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        trackNow(state, player == null ? null : player.getUniqueId(), player == null ? null : player.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (player != null) {
            state.release(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPregeneration(IrisPregenerationEvent event) {
        state.publishPregen(event.getPhase(), event.getProgress());
    }
}
