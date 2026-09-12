/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.generation.runtime;

import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.concurrent.CompletableFuture;

final class WorldTeleportWarmup {
    void teleportAsync(PlayerTeleportEvent e) {
        Location destination = e.getTo();
        if (destination == null) {
            return;
        }

        Player player = e.getPlayer();
        PlayerTeleportEvent.TeleportCause cause = e.getCause();
        e.setCancelled(true);
        CompletableFuture<Boolean> teleport;
        try {
            teleport = BukkitPlatform.teleportAsync(
                    player,
                    destination.clone(),
                    cause);
        } catch (Throwable failure) {
            reportFailure(player, destination, failure);
            return;
        }
        if (teleport == null) {
            reportFailure(player, destination, new IllegalStateException(
                    "Async teleport returned no completion future."));
            return;
        }
        teleport.whenComplete((success, failure) -> {
            if (failure != null) {
                reportFailure(player, destination, failure);
            } else if (!Boolean.TRUE.equals(success)) {
                reportFailure(player, destination, new IllegalStateException(
                        "Async teleport did not complete successfully."));
            }
        });
    }

    private void reportFailure(Player player, Location destination, Throwable failure) {
        IrisLogging.error("Async teleport into Iris world failed for " + player.getName()
                + " at " + destination.getBlockX() + ", " + destination.getBlockY() + ", "
                + destination.getBlockZ() + ".");
        IrisLogging.reportError(failure);
    }
}
