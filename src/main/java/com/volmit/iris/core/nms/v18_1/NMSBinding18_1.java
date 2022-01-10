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

package com.volmit.iris.core.nms.v18_1;


import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMSBinding;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.nbt.io.NBTUtil;
import com.volmit.iris.util.nbt.mca.palette.MCABiomeContainer;
import com.volmit.iris.util.nbt.mca.palette.MCAChunkBiomeContainer;
import com.volmit.iris.util.nbt.mca.palette.MCAIdMap;
import com.volmit.iris.util.nbt.mca.palette.MCAPaletteAccess;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import net.minecraft.core.Registry;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_18_R1.CraftServer;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

public class NMSBinding18_1 implements INMSBinding {

    private final KMap<Biome, Object> baseBiomeCache = new KMap<>();
    private final AtomicCache<MCAIdMap<net.minecraft.world.level.biome.Biome>> biomeMapCache = new AtomicCache<>();

    @Override
    public boolean hasTile(Location l) {
        return ((CraftWorld) l.getWorld()).getHandle().getBlockEntity(new BlockPos(l.getBlockX(), l.getBlockY(), l.getBlockZ()), false) != null;
    }

    @Override
    public CompoundTag serializeTile(Location location) {
        BlockEntity e = ((CraftWorld) location.getWorld()).getHandle().getBlockEntity(new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()), true);

        if (e == null) {
            return null;
        }

        net.minecraft.nbt.CompoundTag tag = e.saveWithFullMetadata();
        return convert(tag);
    }

    private CompoundTag convert(net.minecraft.nbt.CompoundTag tag) {
        try {
            ByteArrayOutputStream boas = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(boas);
            tag.write(dos);
            dos.close();
            return (CompoundTag) NBTUtil.read(new ByteArrayInputStream(boas.toByteArray()), false).getTag();
        } catch (Throwable ex) {
            ex.printStackTrace();
        }

        return null;
    }

    private net.minecraft.nbt.CompoundTag convert(CompoundTag tag) {
        try {
            ByteArrayOutputStream boas = new ByteArrayOutputStream();
            NBTUtil.write(tag, boas, false);
            DataInputStream din = new DataInputStream(new ByteArrayInputStream(boas.toByteArray()));
            net.minecraft.nbt.CompoundTag c = NbtIo.read(din);
            din.close();
            return c;
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void deserializeTile(CompoundTag c, Location newPosition) {
        ((CraftWorld) newPosition.getWorld()).getHandle().getChunkAt(new BlockPos(newPosition.getBlockX(), 0, newPosition.getBlockZ())).setBlockEntityNbt(convert(c));
    }

    @Override
    public CompoundTag serializeEntity(Entity location) {
        return null;// TODO:
    }

    @Override
    public Entity deserializeEntity(CompoundTag s, Location newPosition) {
        return null;// TODO:
    }

    @Override
    public boolean supportsCustomHeight() {
        return true;
    }

    private Registry<net.minecraft.world.level.biome.Biome> getCustomBiomeRegistry() {
        return ((CraftServer) Bukkit.getServer()).getHandle().getServer().registryHolder.registry(Registry.BIOME_REGISTRY).orElse(null);
    }

    @Override
    public Object getBiomeBaseFromId(int id) {
        return getCustomBiomeRegistry().byId(id);
    }

    @Override
    public int getMinHeight(World world) {
        return world.getMinHeight();
    }

    @Override
    public boolean supportsCustomBiomes() {
        return true;
    }

    @Override
    public int getTrueBiomeBaseId(Object biomeBase) {
        return getCustomBiomeRegistry().getId((net.minecraft.world.level.biome.Biome) biomeBase);
    }

    @Override
    public Object getTrueBiomeBase(Location location) {
        return ((CraftWorld) location.getWorld()).getHandle().getBiome(new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
    }

    @Override
    public String getTrueBiomeBaseKey(Location location) {
        return getKeyForBiomeBase(getTrueBiomeBase(location));
    }

    @Override
    public Object getCustomBiomeBaseFor(String mckey) {
        return null;
    }

    @Override
    public String getKeyForBiomeBase(Object biomeBase) {
        return getCustomBiomeRegistry().getKey((net.minecraft.world.level.biome.Biome) biomeBase).getPath(); // something, not something:something
    }

    @Override
    public Object getBiomeBase(World world, Biome biome) {
        return getBiomeBase(((CraftWorld)world).getHandle().registryAccess().registry(Registry.BIOME_REGISTRY).orElse(null), biome);
    }

    @Override
    public Object getBiomeBase(Object registry, Biome biome) {
        Object v = baseBiomeCache.get(biome);

        if (v != null) {
            return v;
        }
        //noinspection unchecked
        v = org.bukkit.craftbukkit.v1_18_R1.block.CraftBlock.biomeToBiomeBase((Registry<net.minecraft.world.level.biome.Biome>) registry, biome);
        if (v == null) {
            // Ok so there is this new biome name called "CUSTOM" in Paper's new releases.
            // But, this does NOT exist within CraftBukkit which makes it return an error.
            // So, we will just return the ID that the plains biome returns instead.
            //noinspection unchecked
            return org.bukkit.craftbukkit.v1_18_R1.block.CraftBlock.biomeToBiomeBase((Registry<net.minecraft.world.level.biome.Biome>) registry, Biome.PLAINS);
        }
        baseBiomeCache.put(biome, v);
        return v;
    }

    @Override
    public boolean isBukkit() {
        return true;
    }

    @Override
    public int getBiomeId(Biome biome) {
        for (World i : Bukkit.getWorlds()) {
            if (i.getEnvironment().equals(World.Environment.NORMAL)) {
                Registry<net.minecraft.world.level.biome.Biome> registry = ((CraftWorld) i).getHandle().registryAccess().registry(Registry.BIOME_REGISTRY).orElse(null);
                return registry.getId((net.minecraft.world.level.biome.Biome) getBiomeBase(registry, biome));
            }
        }

        return biome.ordinal();
    }

    private MCAIdMap<net.minecraft.world.level.biome.Biome> getBiomeMapping() {
        return biomeMapCache.aquire(() -> new MCAIdMap<>() {
            @NotNull
            @Override
            public Iterator<net.minecraft.world.level.biome.Biome> iterator() {
                return getCustomBiomeRegistry().iterator();
            }

            @Override
            public int getId(net.minecraft.world.level.biome.Biome paramT) {
                return getCustomBiomeRegistry().getId(paramT);
            }

            @Override
            public net.minecraft.world.level.biome.Biome byId(int paramInt) {
                return getCustomBiomeRegistry().fromId(paramInt);
            }
        });
    }

    @NotNull
    private MCABiomeContainer getBiomeContainerInterface(MCAIdMap<net.minecraft.world.level.biome.Biome> biomeMapping, MCAChunkBiomeContainer<net.minecraft.world.level.biome.Biome> base) {
        return new MCABiomeContainer() {
            @Override
            public int[] getData() {
                return base.writeBiomes();
            }

            @Override
            public void setBiome(int x, int y, int z, int id) {
                base.setBiome(x, y, z, biomeMapping.byId(id));
            }

            @Override
            public int getBiome(int x, int y, int z) {
                return biomeMapping.getId(base.getBiome(x, y, z));
            }
        };
    }

    @Override
    public MCABiomeContainer newBiomeContainer(int min, int max) {
        MCAChunkBiomeContainer<net.minecraft.world.level.biome.Biome> base = new MCAChunkBiomeContainer<>(getBiomeMapping(), min, max);
        return getBiomeContainerInterface(getBiomeMapping(), base);
    }

    @Override
    public MCABiomeContainer newBiomeContainer(int min, int max, int[] data) {
        MCAChunkBiomeContainer<net.minecraft.world.level.biome.Biome> base = new MCAChunkBiomeContainer<>(getBiomeMapping(), min, max, data);
        return getBiomeContainerInterface(getBiomeMapping(), base);
    }

    @Override
    public int countCustomBiomes() {
        AtomicInteger a = new AtomicInteger(0);

        getCustomBiomeRegistry().keySet().forEach((i) -> {
            if (i.getNamespace().equals("minecraft")) {
                return;
            }

            a.incrementAndGet();
            Iris.debug("Custom Biome: " + i);
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
    public MCAPaletteAccess createPalette() {
        return null;
    }
}
