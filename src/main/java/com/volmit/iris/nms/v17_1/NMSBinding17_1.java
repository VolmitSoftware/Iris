package com.volmit.iris.nms.v17_1;

import com.volmit.iris.nms.INMSBinding;
import com.volmit.iris.util.KMap;
import net.minecraft.core.IRegistry;
import net.minecraft.core.IRegistryWritable;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.BiomeBase;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_17_R1.CraftServer;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class NMSBinding17_1 implements INMSBinding {
    private final KMap<Biome, Object> baseBiomeCache = new KMap<>();

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
    public Object getCustomBiomeBaseFor(String mckey) {
        return getCustomBiomeRegistry().d(ResourceKey.a(IRegistry.aO, new MinecraftKey(mckey)));
    }

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
            return (T) f.invoke(from, par);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    private <T> T invokeStatic(Class<?> from, String name, Object... par) {
        try {
            Method f = from.getDeclaredMethod(name, classify(par));
            f.setAccessible(true);
            return (T) f.invoke(null, par);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    private <T> T getField(Object from, String name) {
        try {
            Field f = from.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(from);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    private <T> T getStaticField(Class<?> t, String name) {
        try {
            Field f = t.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(null);
        } catch (Throwable e) {
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
        v = org.bukkit.craftbukkit.v1_17_R1.block.CraftBlock.biomeToBiomeBase((IRegistry<BiomeBase>) registry, biome);
        if (v == null) {
            // Ok so there is this new biome name called "CUSTOM" in Paper's new releases.
            // But, this does NOT exist within CraftBukkit which makes it return an error.
            // So, we will just return the ID that the plains biome returns instead.
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
    public boolean isBukkit() {
        return false;
    }
}
