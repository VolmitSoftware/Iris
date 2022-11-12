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

package com.volmit.iris.core.nms.v1X;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMSBinding;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.nbt.mca.palette.MCABiomeContainer;
import com.volmit.iris.util.nbt.mca.palette.MCAPaletteAccess;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import org.bukkit.generator.ChunkGenerator;

public class NMSBinding1X implements INMSBinding {
    private static final boolean supportsCustomHeight = testCustomHeight();

    @SuppressWarnings("ConstantConditions")
    private static boolean testCustomHeight() {
        try {
            if (World.class.getDeclaredMethod("getMaxHeight") != null && World.class.getDeclaredMethod("getMinHeight") != null)
                ;
            {
                return true;
            }
        } catch (Throwable ignored) {

        }

        return false;
    }

    @Override
    public boolean hasTile(Location l) {
        return false;
    }

    @Override
    public CompoundTag serializeTile(Location location) {
        return null;
    }

    @Override
    public void injectBiomesFromMantle(Chunk e, Mantle mantle) {

    }

    @Override
    public void deserializeTile(CompoundTag s, Location newPosition) {

    }

    @Override
    public CompoundTag serializeEntity(Entity location) {
        return null;
    }

    @Override
    public Entity deserializeEntity(CompoundTag s, Location newPosition) {
        return null;
    }

    @Override
    public boolean supportsCustomHeight() {
        return supportsCustomHeight;
    }

    @Override
    public Object getBiomeBaseFromId(int id) {
        return null;
    }

    @Override
    public int getMinHeight(World world) {
        return supportsCustomHeight ? world.getMinHeight() : 0;
    }

    @Override
    public boolean supportsCustomBiomes() {
        return false;
    }

    @Override
    public int getTrueBiomeBaseId(Object biomeBase) {
        return 0;
    }

    @Override
    public Object getTrueBiomeBase(Location location) {
        return null;
    }

    @Override
    public String getTrueBiomeBaseKey(Location location) {
        return null;
    }

    @Override
    public Object getCustomBiomeBaseFor(String mckey) {
        return null;
    }

    @Override
    public Object getCustomBiomeBaseHolderFor(String mckey) {
        return null;
    }

    @Override
    public int getBiomeBaseIdForKey(String key) {
        return 0;
    }

    @Override
    public String getKeyForBiomeBase(Object biomeBase) {
        return null;
    }

    public Object getBiomeBase(World world, Biome biome) {
        return null;
    }

    @Override
    public Object getBiomeBase(Object registry, Biome biome) {
        return null;
    }

    @Override
    public boolean isBukkit() {
        return true;
    }

    @Override
    public int getBiomeId(Biome biome) {
        return biome.ordinal();
    }

    @Override
    public MCABiomeContainer newBiomeContainer(int min, int max) {
        Iris.error("Cannot use the custom biome data! Iris is incapable of using MCA generation on this version of minecraft!");

        return null;
    }

    @Override
    public MCABiomeContainer newBiomeContainer(int min, int max, int[] v) {
        Iris.error("Cannot use the custom biome data! Iris is incapable of using MCA generation on this version of minecraft!");

        return null;
    }

    @Override
    public int countCustomBiomes() {
        return 0;
    }

    @Override
    public void forceBiomeInto(int x, int y, int z, Object somethingVeryDirty, ChunkGenerator.BiomeGrid chunk) {

    }

    @Override
    public MCAPaletteAccess createPalette() {
        Iris.error("Cannot use the global data palette! Iris is incapable of using MCA generation on this version of minecraft!");
        return null;
    }
}
