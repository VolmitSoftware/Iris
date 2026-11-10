/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedPrimaryWorldRouter {
    private static final int TICK_INTERVAL = 20;

    private static final Set<UUID> routed = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private static int tickCounter = 0;

    private ModdedPrimaryWorldRouter() {
    }

    public static void clear() {
        routed.clear();
        inFlight.clear();
    }

    /**
     * Drops a disconnected player's routing mark. Without this the set grows with every unique player the
     * server has ever seen, and a returning player is never routed again.
     */
    public static void forget(UUID player) {
        if (player != null) {
            routed.remove(player);
            inFlight.remove(player);
        }
    }

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        tickCounter++;
        if (tickCounter < TICK_INTERVAL) {
            return;
        }
        tickCounter = 0;

        ModdedModConfig config = ModdedModConfig.get();
        if (!config.routePlayersToPrimaryWorld()) {
            return;
        }
        String primary = config.primaryWorld();
        if (primary.isBlank()) {
            return;
        }

        ServerLevel target = ModdedDimensionManager.level(server, primary);
        if (target == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        if (target == overworld) {
            return;
        }

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            if (routed.contains(id) || !inFlight.add(id)) {
                continue;
            }
            if (player.level() != overworld) {
                inFlight.remove(id);
                routed.add(id);
                continue;
            }
            try {
                CompletableFuture<Boolean> teleport = ModdedDimensionManager.teleportAsync(
                        player,
                        server,
                        primary,
                        player.getX(),
                        Double.MIN_VALUE,
                        player.getZ(),
                        ModdedTeleportDeadline.fromNow());
                teleport.whenComplete((success, failure) -> {
                    inFlight.remove(id);
                    if (Boolean.TRUE.equals(success) && failure == null) {
                        routed.add(id);
                        return;
                    }
                    if (failure != null) {
                        ModdedIrisLog.error("Iris failed to route player {} to primary world '{}'",
                                id, primary, failure);
                    }
                });
            } catch (Throwable e) {
                inFlight.remove(id);
                ModdedIrisLog.error("Iris failed to route player {} to primary world '{}'", id, primary, e);
            }
        }
    }
}
