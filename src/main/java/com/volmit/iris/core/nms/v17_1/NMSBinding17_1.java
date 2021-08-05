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

package com.volmit.iris.core.nms.v17_1;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMSBinding;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.IRegistry;
import net.minecraft.core.IRegistryWritable;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.block.BlockChest;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.chunk.BiomeStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.TileState;
import org.bukkit.craftbukkit.v1_16_R1.block.impl.CraftBamboo;
import org.bukkit.craftbukkit.v1_17_R1.CraftServer;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.block.impl.CraftChest;
import org.bukkit.generator.ChunkGenerator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

public class NMSBinding17_1 implements INMSBinding {
    private final KMap<Biome, Object> baseBiomeCache = new KMap<>();
    private Field biomeStorageCache = null;

    public boolean supportsDataPacks() {
        return true;
    }

    private Object getBiomeStorage(ChunkGenerator.BiomeGrid g) {
        try {
            return getFieldForBiomeStorage(g).get(g);
        } catch (IllegalAccessException e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public CompoundTag serializeTile(Location location) {
        TileEntity e = ((CraftWorld)location.getWorld()).getHandle().getTileEntity(new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ()), true);
    }

    @Override
    public void deserializeTile(CompoundTag s, Location newPosition) {

    }

    @Override
    public boolean supportsCustomHeight() {
        return false;
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
        return ((CraftServer) Bukkit.getServer()).getHandle().getServer().getCustomRegistry().b(IRegistry.aO);
    }

    @Override
    public Object getBiomeBaseFromId(int id) {
        return getCustomBiomeRegistry().fromId(id);
    }

    @Override
    public int getTrueBiomeBaseId(Object biomeBase) {
        return getCustomBiomeRegistry().getId((BiomeBase) biomeBase);
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
    public boolean supportsCustomBiomes() {
        return true;
    }

    @Override
    public int getMinHeight(World world) {
        return world.getMinHeight();
    }

    @Override
    public Object getCustomBiomeBaseFor(String mckey) {
        try {
            return getCustomBiomeRegistry().d(ResourceKey.a(IRegistry.aO, new MinecraftKey(mckey)));
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
        return getBiomeBase(((CraftWorld) world).getHandle().t().d(IRegistry.aO), biome);
    }

    private Class<?>[] classify(Object... par) {
        Class<?>[] g = new Class<?>[par.length];
        for (int i = 0; i < g.length; i++) {
            g[i] = par[i].getClass();
        }

        return g;
    }

    private <T> T invoke(Object from, String name, Object... par) {
        try {
            Method f = from.getClass().getDeclaredMethod(name, classify(par));
            f.setAccessible(true);
            //noinspection unchecked
            return (T) f.invoke(from, par);
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return null;
    }

    private <T> T invokeStatic(Class<?> from, String name, Object... par) {
        try {
            Method f = from.getDeclaredMethod(name, classify(par));
            f.setAccessible(true);
            //noinspection unchecked
            return (T) f.invoke(null, par);
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return null;
    }

    private <T> T getField(Object from, String name) {
        try {
            Field f = from.getClass().getDeclaredField(name);
            f.setAccessible(true);
            //noinspection unchecked
            return (T) f.get(from);
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return null;
    }

    private <T> T getStaticField(Class<?> t, String name) {
        try {
            Field f = t.getDeclaredField(name);
            f.setAccessible(true);
            //noinspection unchecked
            return (T) f.get(null);
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Object getBiomeBase(Object registry, Biome biome) {
        Object v = baseBiomeCache.get(biome);

        if (v != null) {
            return v;
        }
        //noinspection unchecked
        v = org.bukkit.craftbukkit.v1_17_R1.block.CraftBlock.biomeToBiomeBase((IRegistry<BiomeBase>) registry, biome);
        if (v == null) {
            // Ok so there is this new biome name called "CUSTOM" in Paper's new releases.
            // But, this does NOT exist within CraftBukkit which makes it return an error.
            // So, we will just return the ID that the plains biome returns instead.
            //noinspection unchecked
            return org.bukkit.craftbukkit.v1_17_R1.block.CraftBlock.biomeToBiomeBase((IRegistry<BiomeBase>) registry, Biome.PLAINS);
        }
        baseBiomeCache.put(biome, v);
        return v;
    }

    @Override
    public int getBiomeId(Biome biome) {
        for (World i : Bukkit.getWorlds()) {
            if (i.getEnvironment().equals(World.Environment.NORMAL)) {

                IRegistry<BiomeBase> registry = ((CraftWorld) i).getHandle().t().d(IRegistry.aO);

                return registry.getId((BiomeBase) getBiomeBase(registry, biome));
            }
        }

        return biome.ordinal();
    }

    @Override
    public int countCustomBiomes() {
        AtomicInteger a = new AtomicInteger(0);
        getCustomBiomeRegistry().d().forEach((i) -> {
            MinecraftKey k = i.getKey().a();

            if (k.getNamespace().equals("minecraft")) {
                return;
            }

            a.incrementAndGet();
            Iris.debug("Custom Biome: " + k);
        });

        return a.get();
    }

    @Override
    public void forceBiomeInto(int x, int y, int z, Object somethingVeryDirty, ChunkGenerator.BiomeGrid chunk) {
        try {
            BiomeStorage s = (BiomeStorage) getFieldForBiomeStorage(chunk).get(chunk);
            s.setBiome(x, y, z, (BiomeBase) somethingVeryDirty);
        } catch (IllegalAccessException e) {
            Iris.reportError(e);
            e.printStackTrace();
        }
    }

    @Override
    public boolean isBukkit() {
        return false;
    }
}
