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

package art.arcane.iris.core.nms;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.nms.container.BiomeColor;
import art.arcane.iris.core.nms.container.BlockProperty;
import art.arcane.iris.core.nms.container.StructurePlacement;
import art.arcane.iris.core.nms.datapack.DataVersion;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.util.mantle.Mantle;
import art.arcane.volmlib.util.math.Vector3d;
import art.arcane.volmlib.util.nbt.mca.palette.MCABiomeContainer;
import art.arcane.volmlib.util.nbt.mca.palette.MCAPaletteAccess;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;

import java.awt.Color;
import java.util.List;

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
        if (c.generator() instanceof PlatformChunkGenerator gen
                && missingDimensionTypes(gen.getTarget().getDimension().getDimensionTypeKey()))
            throw new IllegalStateException("Missing dimension types to create world");
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
        return DataVersion.V1_19_2;
    }

    default int getSpawnChunkCount(World world) {
        return 441;
    }

    KList<String> getStructureKeys();

    boolean missingDimensionTypes(String... keys);

    default boolean injectBukkit() {
        return true;
    }

    KMap<Material, List<BlockProperty>> getBlockProperties();

    void placeStructures(Chunk chunk);

    KMap<Identifier, StructurePlacement> collectStructures();
}
