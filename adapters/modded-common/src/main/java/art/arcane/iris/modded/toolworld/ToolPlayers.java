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

package art.arcane.iris.modded.toolworld;

import art.arcane.iris.modded.ModdedDimensionManager;
import com.volmit.geometry.BlockPosition;
import com.volmit.tooling.world.ToolPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * The tool players of a modded server, and what the host remembers about them: where they came from, the game mode they had
 * before the tool made them creative, and which tool world they were last seen in. Presence is found by looking at every
 * player once a tick, because Minecraft tells loaders nothing common about arrivals, departures and steps.
 */
final class ToolPlayers {
    private static final double TELEPORT_DISTANCE_BLOCKS = 10.0D;

    /** Where a player stood before they entered a tool world. */
    private record Origin(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {
        static Origin of(ServerPlayer player) {
            return new Origin(player.level().dimension(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        }
    }

    /** What the last look at a player saw. */
    private static final class Seen {
        private ModdedToolWorld world;
        private BlockPosition block;
        private boolean sneaking;
        private double x;
        private double y;
        private double z;
        private Origin lastOutsideToolWorlds;
    }

    private final ToolMenus menus = new ToolMenus();
    private final ToolSidebars sidebars = new ToolSidebars();
    private final Map<UUID, ModdedToolPlayer> known = new ConcurrentHashMap<>();
    private final Map<UUID, Origin> origins = new ConcurrentHashMap<>();
    private final Map<UUID, GameType> modesBeforeCreative = new ConcurrentHashMap<>();
    private final Map<UUID, Seen> seen = new ConcurrentHashMap<>();

    ToolSidebars sidebars() {
        return sidebars;
    }

    ModdedToolPlayer playerOf(ServerPlayer player) {
        return known.computeIfAbsent(player.getUUID(),
                id -> new ModdedToolPlayer(id, player.getScoreboardName(), this, menus));
    }

    ToolPlayer of(ServerPlayer player) {
        return playerOf(player);
    }

    void rememberModeBeforeCreative(ServerPlayer player) {
        modesBeforeCreative.putIfAbsent(player.getUUID(), player.gameMode());
    }

    void restoreGameMode(ServerPlayer player) {
        GameType before = modesBeforeCreative.remove(player.getUUID());
        if (before != null) {
            player.setGameMode(before);
        }
    }

    /** Sends the player to where they came from, or to the spawn of the main world when that is not known or is a tool world. */
    CompletableFuture<Boolean> destinationAfterToolWorld(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Origin origin = origins.remove(player.getUUID());
        ServerLevel back = origin == null ? null : server.getLevel(origin.dimension());
        if (back == null || ToolWorldRegistry.INSTANCE.forLevel(back).isPresent()) {
            return teleportToMainSpawn(player, server);
        }
        return ModdedDimensionManager.teleportAsync(player, server, back, origin.x(), origin.y(), origin.z())
                .thenApply(arrived -> faceAfterReturn(player, back, origin, arrived));
    }

    private static boolean faceAfterReturn(ServerPlayer player, ServerLevel back, Origin origin, Boolean arrived) {
        if (Boolean.TRUE.equals(arrived)) {
            player.teleportTo(back, player.getX(), player.getY(), player.getZ(), Set.of(), origin.yaw(), origin.pitch(), false);
        }
        return Boolean.TRUE.equals(arrived);
    }

    private static CompletableFuture<Boolean> teleportToMainSpawn(ServerPlayer player, MinecraftServer server) {
        ServerLevel main = server.overworld();
        BlockPos spawn = main.getRespawnData().pos();
        int spawnY = main.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        return ModdedDimensionManager.teleportAsync(player, server, main, spawn.getX() + 0.5D, spawnY, spawn.getZ() + 0.5D);
    }

    /** Looks at every player once, and tells the tool worlds what changed since the last look. Runs on the server thread. */
    void track(MinecraftServer server) {
        if (!ToolWorldRegistry.INSTANCE.hasWorlds() && seen.isEmpty()) {
            return;
        }
        Set<UUID> online = new java.util.HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            trackOne(player);
        }
        seen.keySet().removeIf(id -> !online.contains(id) && playerLeftServer(id));
    }

    private boolean playerLeftServer(UUID id) {
        Seen last = seen.get(id);
        ModdedToolPlayer player = known.remove(id);
        if (last != null && last.world != null && player != null) {
            last.world.tell(listener -> listener.left(player));
        }
        origins.remove(id);
        modesBeforeCreative.remove(id);
        menus.forget(id);
        sidebars.forget(id);
        return true;
    }

    private void trackOne(ServerPlayer player) {
        Seen last = seen.computeIfAbsent(player.getUUID(), id -> new Seen());
        Optional<ModdedToolWorld> now = player.level() instanceof ServerLevel level
                ? ToolWorldRegistry.INSTANCE.forLevel(level) : Optional.empty();
        ModdedToolWorld current = now.orElse(null);
        if (last.world != current) {
            changedWorld(player, last, current);
        } else if (current != null) {
            moved(player, last, current);
        }
        if (current == null) {
            last.lastOutsideToolWorlds = Origin.of(player);
        }
        remember(player, last, current);
    }

    private void changedWorld(ServerPlayer player, Seen last, ModdedToolWorld current) {
        ModdedToolPlayer toolPlayer = playerOf(player);
        ModdedToolWorld before = last.world;
        if (before == null) {
            origins.put(player.getUUID(), last.lastOutsideToolWorlds != null ? last.lastOutsideToolWorlds : Origin.of(player));
        }
        if (before != null) {
            before.tell(listener -> listener.left(toolPlayer));
        }
        if (current == null) {
            restoreGameMode(player);
            origins.remove(player.getUUID());
            sidebars.hide(player);
            return;
        }
        BlockPosition arrival = ToolBlocks.positionOf(player.blockPosition());
        current.tell(listener -> listener.entered(toolPlayer));
        current.tell(listener -> listener.teleported(toolPlayer, arrival));
    }

    private void moved(ServerPlayer player, Seen last, ModdedToolWorld current) {
        ModdedToolPlayer toolPlayer = playerOf(player);
        BlockPosition block = ToolBlocks.positionOf(player.blockPosition());
        if (!block.equals(last.block)) {
            double dx = player.getX() - last.x;
            double dy = player.getY() - last.y;
            double dz = player.getZ() - last.z;
            boolean teleport = dx * dx + dy * dy + dz * dz > TELEPORT_DISTANCE_BLOCKS * TELEPORT_DISTANCE_BLOCKS;
            current.tell(listener -> {
                if (teleport) {
                    listener.teleported(toolPlayer, block);
                } else {
                    listener.moved(toolPlayer, block);
                }
            });
        }
        if (player.isShiftKeyDown() != last.sneaking) {
            boolean sneaking = player.isShiftKeyDown();
            current.tell(listener -> listener.sneakToggled(toolPlayer, sneaking));
        }
    }

    private static void remember(ServerPlayer player, Seen last, ModdedToolWorld current) {
        last.world = current;
        last.block = ToolBlocks.positionOf(player.blockPosition());
        last.sneaking = player.isShiftKeyDown();
        last.x = player.getX();
        last.y = player.getY();
        last.z = player.getZ();
    }

    void clear() {
        known.clear();
        origins.clear();
        modesBeforeCreative.clear();
        seen.clear();
    }
}
