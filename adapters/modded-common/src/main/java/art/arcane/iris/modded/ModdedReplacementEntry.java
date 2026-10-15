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

import art.arcane.iris.world.lifecycle.WorldReplacementEntryGuard;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeDimensionRuntime;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeServerSpawn;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldTeleport;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedReplacementEntry {
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();
    private static volatile Path guardedWorld;
    private static volatile UUID transaction;

    private ModdedReplacementEntry() {
    }

    public static boolean prepare(NativeModdedServer server) {
        clear();
        Path overworld = ModdedDimensionReplacement.storageFolder(server.root(), ModdedReplacementReconciler.OVERWORLD);
        Optional<WorldReplacementEntryGuard.Entry> entry;
        try {
            entry = WorldReplacementEntryGuard.load(overworld);
        } catch (IOException failure) {
            ModdedIrisLog.error("Iris could not read the Overworld replacement entry marker under {}", overworld, failure);
            return false;
        }
        if (entry.isEmpty()) {
            return false;
        }
        PENDING.addAll(entry.get().pendingPlayers());
        transaction = entry.get().transactionId();
        guardedWorld = overworld;
        ModdedIrisLog.info("Iris replaced the Overworld; {} returning player(s) are checked for a safe position on their next join",
                PENDING.size());
        return true;
    }

    public static void retireIfComplete() {
        Path world = guardedWorld;
        UUID current = transaction;
        if (world == null || current == null || !PENDING.isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(() -> retire(world, current));
    }

    public static void onPlayerJoin(NativeModdedServer server, NativeProtocolPlayer player) {
        if (guardedWorld == null || server == null || player == null || !PENDING.contains(player.id())) {
            return;
        }
        NativeWorld overworld = server.overworld();
        if (overworld == null || !player.isInWorld(overworld) || standingSafely(overworld, player)) {
            complete(player.id());
            return;
        }
        NativeServerSpawn.Respawn spawn = new NativeServerSpawn(server).current();
        boolean overworldSpawn = spawn != null && NativeDimensionRuntime.sameWorld(spawn.world(), overworld);
        double x = overworldSpawn ? spawn.position().x() + 0.5D : 0.5D;
        double z = overworldSpawn ? spawn.position().z() + 0.5D : 0.5D;
        UUID playerId = player.id();
        NativeWorldTeleport.teleport(player, new NativeWorldTeleport.Destination(server, overworld, x, Double.MIN_VALUE, z,
                        ModdedTeleportDeadline.fromNow()))
                .whenComplete((Boolean moved, Throwable failure) -> {
                    if (failure != null || !Boolean.TRUE.equals(moved)) {
                        ModdedIrisLog.error("Iris could not move {} out of the replaced Overworld terrain to the spawn surface",
                                playerId, failure);
                        return;
                    }
                    ModdedIrisLog.info("Iris moved {} from an unsafe position in the replaced Overworld to the spawn surface",
                            playerId);
                    complete(playerId);
                });
    }

    public static void clear() {
        PENDING.clear();
        guardedWorld = null;
        transaction = null;
    }

    static boolean standingSafely(NativeWorld world, NativeProtocolPlayer player) {
        int y = player.blockY();
        if (y < world.minHeight() || y + 1 >= world.maxHeight()) {
            return false;
        }
        return !world.getBlock(player.blockX(), y, player.blockZ()).isSolid()
                && !world.getBlock(player.blockX(), y + 1, player.blockZ()).isSolid();
    }

    private static void complete(UUID playerId) {
        Path world = guardedWorld;
        UUID current = transaction;
        if (world == null || current == null || !PENDING.remove(playerId)) {
            return;
        }
        CompletableFuture.runAsync(() -> persistCompletion(world, current, playerId));
    }

    private static synchronized void persistCompletion(Path world, UUID current, UUID playerId) {
        try {
            Optional<WorldReplacementEntryGuard.Entry> updated = WorldReplacementEntryGuard.completePlayer(world, current, playerId);
            if (updated.isPresent() && updated.get().pendingPlayers().isEmpty()) {
                retire(world, current);
            }
        } catch (IOException | RuntimeException failure) {
            ModdedIrisLog.error("Iris could not record the safe Overworld replacement entry of {}", playerId, failure);
        }
    }

    private static synchronized void retire(Path world, UUID current) {
        try {
            if (WorldReplacementEntryGuard.retireIfEmpty(world, current)) {
                ModdedIrisLog.info("Iris retired the Overworld replacement entry marker: every known player entered safely");
                if (world.equals(guardedWorld)) {
                    clear();
                }
            }
        } catch (IOException | RuntimeException failure) {
            ModdedIrisLog.error("Iris could not retire the Overworld replacement entry marker under {}", world, failure);
        }
    }
}
