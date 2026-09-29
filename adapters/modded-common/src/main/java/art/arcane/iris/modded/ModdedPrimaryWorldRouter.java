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

import art.arcane.iris.world.safeguard.GenerationRefusalNotice;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldTeleport;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeDimensionRuntime;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedPrimaryWorldRouter {
    private static final int TICK_INTERVAL = 20;

    private static final Set<UUID> routed = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private static int tickCounter = 0;
    private static volatile String loginRefusal;
    private static volatile String absentPrimary;

    private ModdedPrimaryWorldRouter() {
    }

    public static void clear() {
        routed.clear();
        inFlight.clear();
        loginRefusal = null;
        absentPrimary = null;
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

    /**
     * Disconnects a joining player while the configured primary world is missing.
     *
     * @return true when the player was refused
     */
    public static boolean refuseIfUnavailable(NativeProtocolPlayer player) {
        String refusal = loginRefusal;
        if (refusal == null || player == null) {
            return false;
        }
        player.disconnect(refusal);
        return true;
    }

    public static void tick(NativeModdedServer server) {
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
            loginRefusal = null;
            return;
        }
        String primary = config.primaryWorld();
        if (primary.isBlank()) {
            loginRefusal = null;
            return;
        }

        NativeWorld target = ModdedDimensionManager.level(server, primary);
        if (target == null) {
            if (ModdedStartup.dimensionsRestored()) {
                judgeMissingPrimary(server, primary);
            }
            return;
        }
        loginRefusal = null;
        NativeWorld overworld = server.overworld();
        if (NativeDimensionRuntime.sameWorld(target, overworld)) {
            return;
        }

        server.forEachPlayer(player -> {
            UUID id = player.id();
            if (routed.contains(id) || !inFlight.add(id)) {
                return;
            }
            if (!player.isInWorld(overworld)) {
                inFlight.remove(id);
                routed.add(id);
                return;
            }
            try {
                CompletableFuture<Boolean> teleport = NativeWorldTeleport.teleport(player,
                        new NativeWorldTeleport.Destination(server, target, player.x(), Double.MIN_VALUE, player.z(),
                                System.nanoTime() + TimeUnit.SECONDS.toNanos(10)));
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
        });
    }

    /**
     * primaryWorld is instance-wide but the dimension it names belongs to one save. Only a save whose registry lists
     * it expected it here, so only that save has a primary world that failed to load.
     */
    private static void judgeMissingPrimary(NativeModdedServer server, String primary) {
        if (loginRefusal == null && primary.equals(absentPrimary)) {
            return;
        }
        if (loginRefusal == null && !expectedInThisSave(server, primary)) {
            absentPrimary = primary;
            ModdedIrisLog.warn("Iris primary world '" + primary + "' from config/irisworldgen/modded.json does not"
                    + " exist in this save, so players are not routed. Create it here with /iris world replace-overworld"
                    + " <pack> or clear primaryWorld.");
            return;
        }
        refuseLogins(server, primary);
    }

    private static boolean expectedInThisSave(NativeModdedServer server, String primary) {
        try {
            return ModdedDimensionRegistryStore.get(server, primary) != null;
        } catch (RuntimeException unreadable) {
            ModdedIrisLog.error("Iris could not read this save's dimension registry to judge primary world '"
                    + primary + "'", unreadable);
            return true;
        }
    }

    /**
     * Players left in the overworld would generate the terrain the primary Iris world was configured to replace, so a
     * primary world this save expected and did not load refuses every player instead.
     */
    private static void refuseLogins(NativeModdedServer server, String primary) {
        String refusal = "Iris primary world '" + primary + "' is not loaded, so this server refuses players."
                + " An operator has to check the server console.";
        if (loginRefusal == null) {
            for (String line : GenerationRefusalNotice.compose(
                    "Iris refused player logins: primary world '" + primary + "' is not loaded",
                    List.of("routePlayersToPrimaryWorld sends players to '" + primary
                            + "', which this save's dimension registry expects but did not load at startup."),
                    List.of(
                            "Players are disconnected instead of playing in the overworld, so no chunks are"
                                    + " written there.",
                            "Fix the dimension (see the Iris errors above) or clear primaryWorld in"
                                    + " config/irisworldgen/modded.json, then start the server again."
                    ))) {
                ModdedIrisLog.error(line);
            }
        }
        loginRefusal = refusal;
        List<NativeProtocolPlayer> online = new ArrayList<>();
        // Disconnecting removes the player from the list being walked, so collect first.
        server.forEachPlayer(online::add);
        for (NativeProtocolPlayer player : online) {
            player.disconnect(refusal);
        }
    }
}
