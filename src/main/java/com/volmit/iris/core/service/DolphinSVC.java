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

package com.volmit.iris.core.service;

import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.function.Consumer4;
import com.volmit.iris.util.math.Spiraler;
import com.volmit.iris.util.matter.MatterStructurePOI;
import com.volmit.iris.util.plugin.IrisService;
import net.minecraft.core.BlockPos;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftDolphin;
import org.bukkit.entity.Dolphin;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.generator.structure.StructureType;

import java.util.concurrent.atomic.AtomicReference;

public class DolphinSVC implements IrisService {

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }

    @EventHandler
    public void on(PlayerInteractEntityEvent event) {
        if (!IrisToolbelt.isIrisWorld(event.getPlayer().getWorld())) {
            return;
        }

        Material hand = event.getPlayer().getInventory().getItem(event.getHand()).getType();
        if (event.getRightClicked().getType().equals(EntityType.DOLPHIN) && (hand.equals(Material.TROPICAL_FISH) || hand.equals(Material.PUFFERFISH) || hand.equals(Material.COD) || hand.equals(Material.SALMON))) {
            Engine e = IrisToolbelt.access(event.getPlayer().getWorld()).getEngine();
            searchNearestTreasure(e, event.getPlayer().getLocation().getBlockX() >> 4, event.getPlayer().getLocation().getBlockZ() >> 4, e.getMantle().getRadius() - 1, StructureType.BURIED_TREASURE, (x, y, z, p) -> {
                event.setCancelled(true);
                Dolphin d = (Dolphin) event.getRightClicked();
                CraftDolphin cd = (CraftDolphin) d;
                d.getWorld().playSound(d, Sound.ENTITY_DOLPHIN_EAT, SoundCategory.NEUTRAL, 1, 1);
                cd.getHandle().setTreasurePos(new BlockPos(x, y, z));
                cd.getHandle().setGotFish(true);
            });

        }
    }

    @ChunkCoordinates
    public void findTreasure(Engine engine, int chunkX, int chunkY, StructureType type, Consumer4<Integer, Integer, Integer, MatterStructurePOI> consumer) {
        AtomicReference<MatterStructurePOI> ref = new AtomicReference<>();
        engine.getMantle().getMantle().iterateChunk(chunkX, chunkY, MatterStructurePOI.class, ref.get() == null ? (x, y, z, d) -> {
            if (d.getType().equals(type.getKey().getKey())) {
                ref.set(d);
                consumer.accept(x, y, z, d);
            }
        } : (x, y, z, d) -> {
        });
    }

    @ChunkCoordinates
    public void searchNearestTreasure(Engine engine, int chunkX, int chunkY, int radius, StructureType type, Consumer4<Integer, Integer, Integer, MatterStructurePOI> consumer) {
        AtomicReference<MatterStructurePOI> ref = new AtomicReference<>();
        new Spiraler(radius * 2, radius * 2, (x, z) -> findTreasure(engine, x, z, type, ref.get() == null ? (i, d, g, a) -> {
            ref.set(a);
            consumer.accept(i, d, g, a);
        } : (i, d, g, a) -> {
        })).setOffset(chunkX, chunkY).drain();
    }
}
