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

import art.arcane.iris.core.tooling.ToolWorldsHost;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.util.common.plugin.IrisService;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolWorlds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Tool worlds on Bukkit: the platform side of {@code com.volmit.tooling.world}. It keeps the open tool worlds and the
 * tool players, listens for the events that concern them ({@link ToolWorldEvents}), and hands each pack its
 * {@link ToolWorlds} through {@link ToolWorldsHost}, which core reaches it by.
 */
public final class BukkitToolWorldService implements IrisService, ToolWorldsHost {
    private final OpenToolWorlds worlds = new OpenToolWorlds();
    private final ToolPlayers players = new ToolPlayers(worlds);
    private final ToolWorldEvents events = new ToolWorldEvents(worlds, players);

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(events, BukkitPlatform.plugin());
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(events);
        worlds.all().forEach(BukkitToolWorld::unloaded);
    }

    @Override
    public ToolWorlds worldsOf(String packName) {
        return new BukkitToolWorlds(packName, worlds, players);
    }

    /** The tool player for {@code player}, the same object for as long as the player is online. */
    public ToolPlayer playerOf(Player player) {
        return players.of(player);
    }
}
