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

package com.volmit.iris.core.edit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.biome.BiomeTypes;
import com.volmit.iris.util.math.M;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

public class WEBlockEditor implements BlockEditor {
    private final World world;
    private final EditSession es;
    private final long last;

    public WEBlockEditor(World world) {
        last = M.ms();
        this.world = world;
        es = WorldEdit.getInstance().newEditSessionBuilder().world(BukkitAdapter.adapt(world)).build();
    }

    @SuppressWarnings("deprecation")
    public void setBiome(int x, int z, Biome b) {
        es.setBiome(BlockVector2.at(x, z), BiomeTypes.get("minecraft:" + b.name().toLowerCase()));
    }

    public void setBiome(int x, int y, int z, Biome b) {
        es.setBiome(BlockVector3.at(x, y, z), BiomeTypes.get("minecraft:" + b.name().toLowerCase()));
    }

    @Override
    public void set(int x, int y, int z, BlockData d) {
        es.rawSetBlock(BlockVector3.at(x, y, z), BukkitAdapter.adapt(d));
    }

    @Override
    public BlockData get(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getBlockData();
    }

    @Override
    public void close() {
        es.close();
    }

    @Override
    public long last() {
        return last;
    }

    @Override
    public Biome getBiome(int x, int y, int z) {
        return world.getBiome(x, y, z);
    }

    @SuppressWarnings("deprecation")
    @Override
    public Biome getBiome(int x, int z) {
        return world.getBiome(x, z);
    }
}
