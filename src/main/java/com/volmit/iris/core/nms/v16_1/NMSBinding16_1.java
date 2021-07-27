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

package com.volmit.iris.core.nms.v16_1;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMSBinding;
import com.volmit.iris.util.collection.KMap;
import net.minecraft.server.v1_16_R1.BiomeBase;
import net.minecraft.server.v1_16_R1.IRegistryWritable;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_16_R1.CraftWorld;
import org.bukkit.generator.ChunkGenerator;

import java.lang.reflect.Field;

public class NMSBinding16_1 implements INMSBinding {
    private final KMap<Biome, Object> baseBiomeCache = new KMap<>();
    private Field biomeStorageCache = null;

    public boolean supportsDataPacks() {
        return true;
    }

    private Field getFieldForBiomeStorage(Object storage) {
        Field f = biomeStorageCache;

        if (f != null) {
            return f;
        }
        try {

            f = storage.getClass().getDeclaredField("biome");
            f.setAccessible(true);
            return f;
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
            Iris.error(storage.getClass().getCanonicalName());
        }

        biomeStorageCache = f;
        return null;
    }

    private IRegistryWritable<BiomeBase> getCustomBiomeRegistry() {
        return null;
    }

    @Override
    public Object getBiomeBaseFromId(int id) {
        return null;
    }

    @Override
    public int getMinHeight(World world) {
        return 0;
    }

    @Override
    public boolean supportsCustomHeight() {
        return false;
    }

    @Override
    public boolean supportsCustomBiomes() {
        return false;
    }

    @Override
    public int getTrueBiomeBaseId(Object biomeBase) {
        return -1;
    }

    @Override
    public Object getTrueBiomeBase(Location location) {
        return ((CraftWorld) location.getWorld()).getHandle().getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public String getTrueBiomeBaseKey(Location location) {
        return getKeyForBiomeBase(getTrueBiomeBase(location));
    }

    @Override
    public Object getCustomBiomeBaseFor(String mckey) {
        try {
            return null;
        } catch (Throwable e) {
            Iris.reportError(e);
        }

        return null;
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    public String getKeyForBiomeBase(Object biomeBase) {
        return getCustomBiomeRegistry().c((BiomeBase) biomeBase).get().a().toString();
    }

    @Override
    public Object getBiomeBase(World world, Biome biome) {
        return null; // Can't find IRegistryCustom in 16_R1
    }

    @Override
    public Object getBiomeBase(Object registry, Biome biome) {
        Object v = baseBiomeCache.get(biome);

        if (v != null) {
            return v;
        }
        //noinspection unchecked
        v = org.bukkit.craftbukkit.v1_16_R1.block.CraftBlock.biomeToBiomeBase(biome);
        if (v == null) {
            // Ok so there is this new biome name called "CUSTOM" in Paper's new releases.
            // But, this does NOT exist within CraftBukkit which makes it return an error.
            // So, we will just return the ID that the plains biome returns instead.
            return org.bukkit.craftbukkit.v1_16_R1.block.CraftBlock.biomeToBiomeBase(Biome.PLAINS);
        }
        baseBiomeCache.put(biome, v);
        return v;
    }

    @Override
    public int getBiomeId(Biome biome) {
        return -1;
    }

    @Override
    public int countCustomBiomes() {
        return 0;
    }

    @Override
    public void forceBiomeInto(int x, int y, int z, Object somethingVeryDirty, ChunkGenerator.BiomeGrid chunk) {

    }

    @Override
    public boolean isBukkit() {
        return false;
    }
}
