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

import com.volmit.iris.Iris;
import com.volmit.iris.core.events.IrisEngineHotloadEvent;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.*;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class EngineAssignedWorldManager extends EngineAssignedComponent implements EngineWorldManager, Listener {
    private final int taskId;
    protected AtomicBoolean ignoreTP = new AtomicBoolean(false);

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
    public void on(IrisEngineHotloadEvent e) {
        for (Player i : e.getEngine().getWorld().getPlayers()) {
            i.playSound(i.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1.8f);
            VolmitSender s = new VolmitSender(i);
            s.sendTitle(C.IRIS + "Engine " + C.AQUA + "<font:minecraft:uniform>Hotloaded", 70, 60, 410);
        }
    }

//    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
//    public void on(PlayerTeleportEvent e) {
//        if(ignoreTP.get()) {
//            System.out.println("IgTP1");
//            return;
//        }
//
//        if(!PaperLib.isPaper() || e.getTo() == null) {
//            System.out.println("IgTP2");
//
////            return;
//        }
//
////        try {
////            System.out.println("IgTP3");
////
////            if(e.getTo().getWorld().equals(getTarget().getWorld().realWorld())) {
////                System.out.println("IgTP4");
////
////                getEngine().getWorldManager().teleportAsync(e);
////            }
////        } catch(Throwable ex) {
////
////        }
//    }

    @EventHandler
    public void on(WorldSaveEvent e) {
        if (e.getWorld().equals(getTarget().getWorld().realWorld())) {
            getEngine().save();
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

    @EventHandler
    public void on(ChunkUnloadEvent e) {
        if (e.getChunk().getWorld().equals(getTarget().getWorld().realWorld())) {
            onChunkUnload(e.getChunk());
        }
    }

    @Override
    public void close() {
        super.close();
        Iris.instance.unregisterListener(this);
        Bukkit.getScheduler().cancelTask(taskId);
    }
}
