/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.Position2;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public abstract class EngineAssignedWorldManager extends EngineAssignedComponent implements EngineWorldManager, Listener {
    private final int taskId;

    public EngineAssignedWorldManager() {
        super(null, null);
        taskId = -1;
    }

    public EngineAssignedWorldManager(Engine engine) {
        super(engine, "World");
        Iris.instance.registerListener(this);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, this::onTick, 0, 0);
    }

    @EventHandler
    public void on(WorldSaveEvent e) {
        if (e.getWorld().equals(getTarget().getWorld().realWorld())) {
            getEngine().save();
        }
    }

    @EventHandler
    public void on(EntitySpawnEvent e) {
        if (e.getEntity().getWorld().equals(getTarget().getWorld().realWorld())) {
            if (e.getEntityType().equals(EntityType.ENDER_SIGNAL)) {
                KList<Position2> p = getEngine().getDimension().getStrongholds(getEngine().getWorld().seed());
                Position2 px = new Position2(e.getEntity().getLocation().getBlockX(), e.getEntity().getLocation().getBlockZ());
                Position2 pr = null;
                double d = Double.MAX_VALUE;

                for (Position2 i : p) {
                    double dx = i.distance(px);
                    if (dx < d) {
                        d = dx;
                        pr = i;
                    }
                }

                if (pr != null) {
                    e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1.6f);
                    ((EnderSignal) e.getEntity()).setTargetLocation(new Location(e.getEntity().getWorld(), pr.getX(), 40, pr.getZ()));
                }
            }
        }
    }

    @EventHandler
    public void on(WorldUnloadEvent e) {
        if (e.getWorld().equals(getTarget().getWorld().realWorld())) {
            getEngine().close();
        }
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        if (e.getPlayer().getWorld().equals(getTarget().getWorld().realWorld())) {
            onBlockBreak(e);
        }
    }

    @EventHandler
    public void on(BlockPlaceEvent e) {
        if (e.getPlayer().getWorld().equals(getTarget().getWorld().realWorld())) {
            onBlockPlace(e);
        }
    }

    @EventHandler
    public void on(ChunkLoadEvent e) {
        if (e.getChunk().getWorld().equals(getTarget().getWorld().realWorld())) {
            onChunkLoad(e.getChunk(), e.isNewChunk());
        }
    }

    @Override
    public void close() {
        super.close();
        Iris.instance.unregisterListener(this);
        Bukkit.getScheduler().cancelTask(taskId);
    }
}
