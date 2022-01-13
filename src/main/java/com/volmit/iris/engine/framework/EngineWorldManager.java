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

package com.volmit.iris.engine.framework;

import org.bukkit.Chunk;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

@SuppressWarnings("EmptyMethod")
public interface EngineWorldManager {
    void close();

    double getEnergy();

    int getEntityCount();

    int getChunkCount();

    double getEntitySaturation();

    void onTick();

    void onSave();

    void onBlockBreak(BlockBreakEvent e);

    void onBlockPlace(BlockPlaceEvent e);

    void onChunkLoad(Chunk e, boolean generated);

    void chargeEnergy();

    void teleportAsync(PlayerTeleportEvent e);
}
