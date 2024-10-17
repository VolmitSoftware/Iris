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

package com.volmit.iris.core.nms;

import com.volmit.iris.core.nms.container.BiomeColor;
import com.volmit.iris.core.nms.datapack.DataVersion;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.Vector3d;
import com.volmit.iris.util.nbt.mca.palette.MCABiomeContainer;
import com.volmit.iris.util.nbt.mca.palette.MCAPaletteAccess;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.entity.Dolphin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemStack;

import java.awt.*;
import java.awt.Color;

public interface INMSBinding {
    boolean hasTile(Material material);

    boolean hasTile(Location l);

    KMap<String, Object> serializeTile(Location location);

    void deserializeTile(KMap<String, Object> s, Location newPosition);

    CompoundTag serializeEntity(Entity location);

    Entity deserializeEntity(CompoundTag s, Location newPosition);

    boolean supportsCustomHeight();

    Object getBiomeBaseFromId(int id);

    int getMinHeight(World world);

    boolean supportsCustomBiomes();

    int getTrueBiomeBaseId(Object biomeBase);

    Object getTrueBiomeBase(Location location);

    String getTrueBiomeBaseKey(Location location);

    Object getCustomBiomeBaseFor(String mckey);

    Object getCustomBiomeBaseHolderFor(String mckey);

    int getBiomeBaseIdForKey(String key);

    String getKeyForBiomeBase(Object biomeBase);

    Object getBiomeBase(World world, Biome biome);

    Object getBiomeBase(Object registry, Biome biome);

    KList<Biome> getBiomes();

    boolean isBukkit();

    int getBiomeId(Biome biome);

    MCABiomeContainer newBiomeContainer(int min, int max, int[] data);

    MCABiomeContainer newBiomeContainer(int min, int max);

    default World createWorld(WorldCreator c) {
        return c.createWorld();
    }

    int countCustomBiomes();

    void forceBiomeInto(int x, int y, int z, Object somethingVeryDirty, ChunkGenerator.BiomeGrid chunk);

    default boolean supportsDataPacks() {
        return false;
    }

    MCAPaletteAccess createPalette();

    void injectBiomesFromMantle(Chunk e, Mantle mantle);

    ItemStack applyCustomNbt(ItemStack itemStack, KMap<String, Object> customNbt) throws IllegalArgumentException;

    void inject(long seed, Engine engine, World world) throws NoSuchFieldException, IllegalAccessException;

    Vector3d getBoundingbox(org.bukkit.entity.EntityType entity);
    
    Entity spawnEntity(Location location, EntityType type, CreatureSpawnEvent.SpawnReason reason);

    Color getBiomeColor(Location location, BiomeColor type);

    default DataVersion getDataVersion() {
        return DataVersion.V1192;
    }

    default int getSpawnChunkCount(World world) {
        return 441;
    }

    KList<String> getStructureKeys();
}
