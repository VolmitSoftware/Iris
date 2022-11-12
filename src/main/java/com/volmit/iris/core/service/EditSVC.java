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

import com.volmit.iris.Iris;
import com.volmit.iris.core.edit.BlockEditor;
import com.volmit.iris.core.edit.BukkitBlockEditor;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.plugin.IrisService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldUnloadEvent;

public class EditSVC implements IrisService {
    private KMap<World, BlockEditor> editors;

    @Override
    public void onEnable() {
        this.editors = new KMap<>();
        Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, this::update, 1000, 1000);
    }

    @Override
    public void onDisable() {
        flushNow();
    }

    public BlockData get(World world, int x, int y, int z) {
        return open(world).get(x, y, z);
    }

    public void set(World world, int x, int y, int z, BlockData d) {
        open(world).set(x, y, z, d);
    }

    public void setBiome(World world, int x, int y, int z, Biome d) {
        open(world).setBiome(x, y, z, d);
    }

    public void setBiome(World world, int x, int z, Biome d) {
        open(world).setBiome(x, z, d);
    }

    public Biome getBiome(World world, int x, int y, int z) {
        return open(world).getBiome(x, y, z);
    }

    public Biome getBiome(World world, int x, int z) {
        return open(world).getBiome(x, z);
    }

    @EventHandler
    public void on(WorldUnloadEvent e) {
        if (editors.containsKey(e.getWorld())) {
            editors.remove(e.getWorld()).close();
        }
    }

    public void update() {
        for (World i : editors.k()) {
            if (M.ms() - editors.get(i).last() > 1000) {
                editors.remove(i).close();
            }
        }
    }

    public void flushNow() {
        for (World i : editors.k()) {
            editors.remove(i).close();
        }
    }

    public BlockEditor open(World world) {
        if (editors.containsKey(world)) {
            return editors.get(world);
        }

        BlockEditor e = new BukkitBlockEditor(world);
        editors.put(world, e);

        return e;
    }

}
