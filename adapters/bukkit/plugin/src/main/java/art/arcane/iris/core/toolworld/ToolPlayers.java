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

package art.arcane.iris.core.toolworld;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The tool players: one {@link BukkitToolPlayer} for each online player, and what the host remembers about them while
 * they are in tool worlds, which is where they came from and the game mode they had before a tool made them creative.
 */
final class ToolPlayers {
    private final OpenToolWorlds worlds;
    private final BukkitToolMenus menus = new BukkitToolMenus();
    private final Map<UUID, BukkitToolPlayer> players = new ConcurrentHashMap<>();
    private final Map<UUID, Location> origins = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> modesBeforeCreative = new ConcurrentHashMap<>();

    ToolPlayers(OpenToolWorlds worlds) {
        this.worlds = worlds;
    }

    BukkitToolPlayer of(Player player) {
        return players.computeIfAbsent(player.getUniqueId(), id -> new BukkitToolPlayer(player, worlds, this, menus));
    }

    /** Forgets everything about a player who left the server. */
    void forget(Player player) {
        UUID id = player.getUniqueId();
        players.remove(id);
        origins.remove(id);
        modesBeforeCreative.remove(id);
        menus.forget(id);
    }

    /** Remembers where a player was before their first step into a tool world; later steps keep the first. */
    void rememberOrigin(Player player, Location origin) {
        origins.putIfAbsent(player.getUniqueId(), origin.clone());
    }

    /** Hands over where the player came from, once. */
    Optional<Location> takeOrigin(Player player) {
        return Optional.ofNullable(origins.remove(player.getUniqueId()));
    }

    /** Remembers the game mode of a player a tool makes creative; the first mode is kept while they stay creative. */
    void rememberModeBeforeCreative(Player player) {
        modesBeforeCreative.putIfAbsent(player.getUniqueId(), player.getGameMode());
    }

    /** Gives a player the game mode they had before a tool made them creative; does nothing when none did. */
    void restoreGameMode(Player player) {
        GameMode previous = modesBeforeCreative.remove(player.getUniqueId());
        if (previous != null) {
            player.setGameMode(previous);
        }
    }

    BukkitToolMenus menus() {
        return menus;
    }
}
