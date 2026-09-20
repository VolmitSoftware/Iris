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

package art.arcane.iris.platform.bukkit.nms;

import art.arcane.volmlib.nativelib.terrain.ServerShutdownBoundary;

import art.arcane.volmlib.nativelib.terrain.NativeBlockVolume;

import art.arcane.volmlib.nativelib.terrain.BukkitTerrainBuffer;

import org.bukkit.block.data.BlockData;

import art.arcane.volmlib.util.nbt.mca.NBTWorldSupport;

import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainAccess;
import java.util.concurrent.CompletableFuture;
import art.arcane.iris.pack.datapack.DatapackStructureScopeIndex;
import art.arcane.iris.structure.nativegen.IrisImportedStructureControl;
import art.arcane.iris.world.lifecycle.WorldLifecycleCaller;
import art.arcane.iris.world.lifecycle.WorldLifecycleRequest;
import art.arcane.iris.world.lifecycle.WorldLifecycleService;
import art.arcane.volmlib.nativelib.terrain.BiomeColor;
import art.arcane.volmlib.nativelib.terrain.BlockProperty;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.generation.chunk.TerrainChunk;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.nativelib.terrain.structure.NativeStructureVolume;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.volmlib.nativelib.terrain.JigsawSourceMetadata;
import art.arcane.volmlib.util.hunk.Hunk;
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
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;

import java.awt.Color;
import java.util.List;
import java.util.Set;

public interface INMSBinding extends NativeTerrainAccess {
    default CompletableFuture<Void> flushSavedTerrainCapture(World world) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "Native saved terrain checkpoints are unavailable."));
    }

    default CompletableFuture<SavedTerrainChunk> captureSavedTerrainChunk(
            World world, int chunkX, int chunkZ, int minimumY, int height) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "Native saved terrain capture is unavailable."));
    }

    default PlatformGenerationRegistry generationRegistry() {
        throw new UnsupportedOperationException(
                "The active NMS binding does not expose generation registry definitions."
        );
    }

    boolean hasTile(Material material);

    boolean hasTile(Location l);

    KMap<String, Object> serializeTile(Location location);

    default void deserializeTile(KMap<String, Object> data, Location location) {
        deserializeTile(data, location, BukkitTileWriteScheduler.INSTANCE);
    }

    CompoundTag serializeEntity(Entity location);

    Entity deserializeEntity(CompoundTag s, Location newPosition);

    boolean supportsCustomHeight();

    int getMinHeight(World world);

    boolean supportsCustomBiomes();

    boolean supportsIrisWorldGeneration();

    String getTrueBiomeBaseKey(Location location);

    KList<Biome> getBiomes();

    KList<String> getStructureKeys();

    default KList<String> getJigsawStructureKeys() {
        return new KList<>();
    }

    default KList<String> getTemplatePoolKeys() {
        return new KList<>();
    }

    default JigsawSourceMetadata getJigsawSourceMetadata(String structureKey) {
        throw new UnsupportedOperationException("The active NMS binding does not expose registered jigsaw metadata");
    }

    default int getTemplatePoolHorizontalSpan(String templatePoolKey) {
        throw new UnsupportedOperationException("The active NMS binding does not expose registered template pool spans");
    }

    default int getJigsawStartPoolHorizontalSpan(String structureKey, String templatePoolKey) {
        return getTemplatePoolHorizontalSpan(templatePoolKey);
    }

    KList<String> getStructureSetKeys();

    KList<String> getReachableStructureKeys(World world);

    KList<String> getStructureBiomeKeys(String structureKey);

    KList<String> getPossibleBiomeKeys(World world);

    default KList<String> getObjectFeatureKeys() {
        return new KList<>();
    }

    default boolean placeFeature(World world, int x, int y, int z, String featureKey, long seed) {
        throw new UnsupportedOperationException("The active NMS binding does not support feature placement.");
    }

    default int[] placeStructure(World world, int chunkX, int chunkZ, String structureKey, long seed, int maxSpan) {
        throw new UnsupportedOperationException("The active NMS binding does not support structure placement.");
    }

    default boolean supportsStructureCapture() {
        return false;
    }

    /**
     * World-space piece bounds of every native structure that will generate inside the given XZ rect. Bindings
     * without native structure support answer with no volumes, which leaves the object veto inert.
     */
    default KList<NativeStructureVolume> nativeStructureVolumes(Engine engine, int minX, int minZ, int maxX, int maxZ) {
        return NativeStructureVolume.NONE;
    }

    default void invalidateNativeStructureVolumeIndex(Engine engine) {
    }

    int getBiomeId(Biome biome);

    MCABiomeContainer newBiomeContainer(int min, int max, int[] data);

    MCABiomeContainer newBiomeContainer(int min, int max);

    default World createWorld(WorldCreator c) {
        WorldLifecycleRequest request = WorldLifecycleRequest.fromCreator(c, false, false, WorldLifecycleCaller.CREATE);
        return createWorld(c, request);
    }

    default CompletableFuture<World> createWorldAsync(WorldCreator c) {
        WorldLifecycleRequest request = WorldLifecycleRequest.fromCreator(c, false, false, WorldLifecycleCaller.CREATE);
        return createWorldAsync(c, request);
    }

    default World createWorld(WorldCreator c, WorldLifecycleRequest request) {
        validateDimensionTypes(c);
        return WorldLifecycleService.get().createBlocking(request);
    }

    default CompletableFuture<World> createWorldAsync(WorldCreator c, WorldLifecycleRequest request) {
        try {
            validateDimensionTypes(c);
            return WorldLifecycleService.get().create(request);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    default void ensureServerLevelInjection() {
    }

    int countCustomBiomes();

    default boolean supportsDataPacks() {
        return false;
    }

    MCAPaletteAccess createPalette(NBTWorldSupport.BlockStateCodec<BlockData> codec);

    default boolean applyChunkBlocks(Chunk chunk, BukkitTerrainBuffer data) {
        return false;
    }

    default boolean applyChunkDataBlocks(ChunkGenerator.ChunkData chunkData, NativeBlockVolume data) {
        return false;
    }

    default boolean clearChunkBlocks(Chunk chunk) {
        return false;
    }

    default boolean forceEvictChunk(World world, int chunkX, int chunkZ) {
        return false;
    }

    default boolean saveAndUnloadChunk(World world, int x, int z) {
        return false;
    }

    default boolean pollChunkTask(World world) {
        return false;
    }

    default void flushChunkIO(World world) {
    }

    default void reconcileNativeStructurePois(Chunk chunk) {
    }

    void injectBiomesFromMantle(Chunk e, Mantle<Matter> mantle);

    ItemStack applyCustomNbt(ItemStack itemStack, KMap<String, Object> customNbt) throws IllegalArgumentException;

    void inject(long seed, Engine engine, World world) throws NoSuchFieldException, IllegalAccessException;

    DatapackStructureScopeResult scopeDatapackStructures(
            World world,
            DatapackStructureScopeIndex scopeIndex,
            Set<String> declaredSources,
            IrisImportedStructureControl importedStructures
    ) throws NoSuchFieldException, IllegalAccessException;

    CompletableFuture<Void> completeStudioStructureBootstrap(World world) throws NoSuchFieldException, IllegalAccessException;

    void abandonStudioStructureBootstrap(World world);

    Vector3d getBoundingbox(org.bukkit.entity.EntityType entity);

    String getEntitySpawnCategory(String key);

    Entity spawnEntity(Location location, EntityType type, CreatureSpawnEvent.SpawnReason reason);

    Color getBiomeColor(Location location, BiomeColor type);

    default DataVersion getDataVersion() {
        return DataVersion.V26_2;
    }

    default int getSpawnChunkCount(World world) {
        return 441;
    }

    boolean missingDimensionTypes(String... keys);

    default boolean injectBukkit() {
        return true;
    }

    /**
     * Removes any instrumentation installed by {@link #injectBukkit()}. Must be idempotent;
     * called on plugin disable and pre-unload so the transformer cannot outlive the plugin.
     */
    default void uninjectBukkit() {
    }

    default void deferPluginClassLoaderClose() {
    }

    default void releasePluginClassLoaderClose() {
    }

    default boolean isServerStopping() {
        return false;
    }

    default ServerShutdownBoundary createServerShutdownBoundary() {
        return new ServerShutdownBoundary(() -> true, Thread.currentThread());
    }

    KMap<Material, List<BlockProperty>> getBlockProperties();

    private void validateDimensionTypes(WorldCreator c) {
        if (!(c.generator() instanceof PlatformChunkGenerator generator)) {
            return;
        }
        if (!supportsIrisWorldGeneration()) {
            throw new IllegalStateException("Iris world '" + c.name() + "' cannot be created with limited NMS binding "
                    + getClass().getSimpleName()
                    + "; set general.disableNMS=false and use a supported Minecraft server runtime (26.1.2, 26.2 or 26.3)");
        }
        if (missingDimensionTypes(generator.getTarget().getDimension().getDimensionTypeKey())) {
            throw new IllegalStateException("Missing dimension types to create world");
        }
    }
}
