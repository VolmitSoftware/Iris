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

package art.arcane.iris.platform.bukkit.nms.v1X;

import art.arcane.iris.pack.datapack.DatapackStructureScopeIndex;
import art.arcane.iris.platform.bukkit.nms.DatapackStructureScopeResult;
import art.arcane.iris.structure.nativegen.IrisImportedStructureControl;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.platform.bukkit.nms.INMSBinding;
import art.arcane.iris.platform.bukkit.nms.container.BiomeColor;
import art.arcane.iris.platform.bukkit.nms.container.BlockProperty;
import art.arcane.iris.platform.bukkit.nms.datapack.DataVersion;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.math.Vector3d;
import art.arcane.volmlib.util.nbt.mca.palette.MCABiomeContainer;
import art.arcane.volmlib.util.nbt.mca.palette.MCAPaletteAccess;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.StreamSupport;

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
    public boolean hasTile(Material material) {
        return false;
    }

    @Override
    public boolean hasTile(Location l) {
        return false;
    }

    @Override
    public KMap<String, Object> serializeTile(Location location) {
        return null;
    }

    @Override
    public void deserializeTile(KMap<String, Object> s, Location newPosition) {

    }


    @Override
    public void injectBiomesFromMantle(Chunk e, Mantle<Matter> mantle) {

    }

    @Override
    public ItemStack applyCustomNbt(ItemStack itemStack, KMap<String, Object> customNbt) throws IllegalArgumentException {
        return itemStack;
    }

    @Override
    public void inject(long seed, Engine engine, World world) throws NoSuchFieldException, IllegalAccessException {
        throw new IllegalStateException("Iris world generation requires the supported NMS binding; "
                + "general.disableNMS=true cannot create or initialize an Iris world");
    }

    @Override
    public DatapackStructureScopeResult scopeDatapackStructures(
            World world,
            DatapackStructureScopeIndex scopeIndex,
            Set<String> declaredSources,
            IrisImportedStructureControl importedStructures
    ) {
        throw new IllegalStateException("Iris-managed datapack structure isolation requires the supported NMS binding");
    }

    @Override
    public CompletableFuture<Void> completeStudioStructureBootstrap(World world) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void abandonStudioStructureBootstrap(World world) {
    }

    public Vector3d getBoundingbox() {
        return null;
    }

    @Override
    public Entity spawnEntity(Location location, EntityType type, CreatureSpawnEvent.SpawnReason reason) {
        return location.getWorld().spawnEntity(location, type);
    }

    @Override
    public Color getBiomeColor(Location location, BiomeColor type) {
        return Color.GREEN;
    }

    @Override
    public boolean missingDimensionTypes(String... keys) {
        return false;
    }

    @Override
    public KMap<Material, List<BlockProperty>> getBlockProperties() {
        KMap<Material, List<BlockProperty>> map = new KMap<>();
        for (Material m : Material.values()) {
            if (m.isBlock()) map.put(m, List.of());
        }
        return map;
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
    public boolean supportsIrisWorldGeneration() {
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
    public KList<Biome> getBiomes() {
        KList<Biome> biomes = new KList<>();
        for (Biome biome : Registry.BIOME) {
            biomes.add(biome);
        }
        return biomes;
    }

    @Override
    public KList<String> getStructureKeys() {
        throw unsupportedStructureHook("read registered structure keys");
    }

    @Override
    public KList<String> getStructureSetKeys() {
        throw unsupportedStructureHook("read registered structure-set keys");
    }

    @Override
    public KList<String> getReachableStructureKeys(World world) {
        throw unsupportedStructureHook("resolve reachable structures");
    }

    @Override
    public KList<String> getStructureBiomeKeys(String structureKey) {
        throw unsupportedStructureHook("resolve structure biome keys");
    }

    @Override
    public KList<String> getPossibleBiomeKeys(World world) {
        throw unsupportedStructureHook("resolve possible biome keys");
    }

    @Override
    public DataVersion getDataVersion() {
        return DataVersion.UNSUPPORTED;
    }

    @Override
    public int getBiomeId(Biome biome) {
        List<Biome> biomes = StreamSupport.stream(Registry.BIOME.spliterator(), false).toList();
        int index = biomes.indexOf(biome);
        return Math.max(index, 0);
    }

    @Override
    public MCABiomeContainer newBiomeContainer(int min, int max) {
        IrisLogging.error("Cannot use the custom biome data! Iris is incapable of using MCA generation on this version of minecraft!");

        return null;
    }

    @Override
    public MCABiomeContainer newBiomeContainer(int min, int max, int[] v) {
        IrisLogging.error("Cannot use the custom biome data! Iris is incapable of using MCA generation on this version of minecraft!");

        return null;
    }

    @Override
    public int countCustomBiomes() {
        return 0;
    }

    @Override
    public Vector3d getBoundingbox(org.bukkit.entity.EntityType entity) {
      return null;
    }

    @Override
    public String getEntitySpawnCategory(String key) {
        return null;
    }

    @Override
    public MCAPaletteAccess createPalette() {
        IrisLogging.error("Cannot use the global data palette! Iris is incapable of using MCA generation on this version of minecraft!");
        return null;
    }

    private IllegalStateException unsupportedStructureHook(String operation) {
        return new IllegalStateException("Iris cannot " + operation + " with limited NMS binding "
                + getClass().getSimpleName()
                + "; set general.disableNMS=false and use a supported Minecraft server runtime (26.1.2 or 26.2)");
    }
}
