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

package com.volmit.iris.util.fakenews;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.*;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Consumer;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;

@SuppressWarnings({"deprecation", "ClassCanBeRecord", "unchecked"})
public class HeightedFakeWorld implements World {
    private final int height;

    public HeightedFakeWorld(int height) {
        this.height = height;
    }

    @Override
    public void sendPluginMessage(@NotNull Plugin source, @NotNull String channel, @NotNull byte[] message) {

    }

    @NotNull
    @Override
    public Set<String> getListeningPluginChannels() {

        return null;
    }

    @Override
    public void setMetadata(@NotNull String metadataKey, @NotNull MetadataValue newMetadataValue) {

    }

    @NotNull
    @Override
    public List<MetadataValue> getMetadata(@NotNull String metadataKey) {

        return null;
    }

    @Override
    public boolean hasMetadata(@NotNull String metadataKey) {

        return false;
    }

    @Override
    public void removeMetadata(@NotNull String metadataKey, @NotNull Plugin owningPlugin) {

    }

    @NotNull
    @Override
    public Block getBlockAt(int x, int y, int z) {

        return null;
    }

    @NotNull
    @Override
    public Block getBlockAt(@NotNull Location location) {

        return null;
    }

    @Override
    public int getHighestBlockYAt(int x, int z) {

        return 0;
    }

    @Override
    public int getHighestBlockYAt(@NotNull Location location) {

        return 0;
    }

    @NotNull
    @Override
    public Block getHighestBlockAt(int x, int z) {

        return null;
    }

    @NotNull
    @Override
    public Block getHighestBlockAt(@NotNull Location location) {

        return null;
    }

    @Override
    public int getHighestBlockYAt(int x, int z, @NotNull HeightMap heightMap) {

        return 0;
    }

    @Override
    public int getHighestBlockYAt(@NotNull Location location, @NotNull HeightMap heightMap) {

        return 0;
    }

    @NotNull
    @Override
    public Block getHighestBlockAt(int x, int z, @NotNull HeightMap heightMap) {

        return null;
    }

    @NotNull
    @Override
    public Block getHighestBlockAt(@NotNull Location location, @NotNull HeightMap heightMap) {

        return null;
    }

    @NotNull
    @Override
    public Chunk getChunkAt(int x, int z) {

        return null;
    }

    @NotNull
    @Override
    public Chunk getChunkAt(@NotNull Location location) {

        return null;
    }

    @NotNull
    @Override
    public Chunk getChunkAt(@NotNull Block block) {

        return null;
    }

    @Override
    public boolean isChunkLoaded(@NotNull Chunk chunk) {

        return false;
    }

    @NotNull
    @Override
    public Chunk[] getLoadedChunks() {

        return null;
    }

    @Override
    public void loadChunk(@NotNull Chunk chunk) {

    }

    @Override
    public boolean isChunkLoaded(int x, int z) {

        return false;
    }

    @Override
    public boolean isChunkGenerated(int x, int z) {

        return false;
    }

    @Override
    public boolean isChunkInUse(int x, int z) {

        return false;
    }

    @Override
    public void loadChunk(int x, int z) {

    }

    @Override
    public boolean loadChunk(int x, int z, boolean generate) {

        return false;
    }

    @Override
    public boolean unloadChunk(@NotNull Chunk chunk) {

        return false;
    }

    @Override
    public boolean unloadChunk(int x, int z) {

        return false;
    }

    @Override
    public boolean unloadChunk(int x, int z, boolean save) {

        return false;
    }

    @Override
    public boolean unloadChunkRequest(int x, int z) {

        return false;
    }

    @Override
    public boolean regenerateChunk(int x, int z) {

        return false;
    }

    @Override
    public boolean refreshChunk(int x, int z) {

        return false;
    }

    @Override
    public boolean isChunkForceLoaded(int x, int z) {

        return false;
    }

    @Override
    public void setChunkForceLoaded(int x, int z, boolean forced) {

    }

    @NotNull
    @Override
    public Collection<Chunk> getForceLoadedChunks() {

        return null;
    }

    @Override
    public boolean addPluginChunkTicket(int x, int z, @NotNull Plugin plugin) {

        return false;
    }

    @Override
    public boolean removePluginChunkTicket(int x, int z, @NotNull Plugin plugin) {

        return false;
    }

    @Override
    public void removePluginChunkTickets(@NotNull Plugin plugin) {

    }

    @NotNull
    @Override
    public Collection<Plugin> getPluginChunkTickets(int x, int z) {

        return null;
    }

    @NotNull
    @Override
    public Map<Plugin, Collection<Chunk>> getPluginChunkTickets() {

        return null;
    }

    @NotNull
    @Override
    public Item dropItem(@NotNull Location location, @NotNull ItemStack item) {

        return null;
    }

    @NotNull
    @Override
    public Item dropItem(@NotNull Location location, @NotNull ItemStack itemStack, @Nullable Consumer<Item> consumer) {
        return null;
    }

    @NotNull
    @Override
    public Item dropItemNaturally(@NotNull Location location, @NotNull ItemStack item) {

        return null;
    }

    @NotNull
    @Override
    public Item dropItemNaturally(@NotNull Location location, @NotNull ItemStack itemStack, @Nullable Consumer<Item> consumer) {
        return null;
    }

    @NotNull
    @Override
    public Arrow spawnArrow(@NotNull Location location, @NotNull Vector direction, float speed, float spread) {

        return null;
    }

    @NotNull
    @Override
    public <T extends AbstractArrow> T spawnArrow(@NotNull Location location, @NotNull Vector direction, float speed, float spread, @NotNull Class<T> clazz) {

        return null;
    }

    @Override
    public boolean generateTree(@NotNull Location location, @NotNull TreeType type) {

        return false;
    }

    @Override
    public boolean generateTree(@NotNull Location loc, @NotNull TreeType type, @NotNull BlockChangeDelegate delegate) {

        return false;
    }

    @NotNull
    @Override
    public Entity spawnEntity(@NotNull Location loc, @NotNull EntityType type) {

        return null;
    }

    @NotNull
    @Override
    public LightningStrike strikeLightning(@NotNull Location loc) {

        return null;
    }

    @NotNull
    @Override
    public LightningStrike strikeLightningEffect(@NotNull Location loc) {

        return null;
    }

    @NotNull
    @Override
    public List<Entity> getEntities() {

        return null;
    }

    @NotNull
    @Override
    public List<LivingEntity> getLivingEntities() {

        return null;
    }

    @NotNull
    @Override
    public <T extends Entity> Collection<T> getEntitiesByClass(@NotNull Class<T>... classes) {

        return null;
    }

    @NotNull
    @Override
    public <T extends Entity> Collection<T> getEntitiesByClass(@NotNull Class<T> cls) {

        return null;
    }

    @NotNull
    @Override
    public Collection<Entity> getEntitiesByClasses(@NotNull Class<?>... classes) {

        return null;
    }

    @NotNull
    @Override
    public List<Player> getPlayers() {

        return null;
    }

    @NotNull
    @Override
    public Collection<Entity> getNearbyEntities(@NotNull Location location, double x, double y, double z) {

        return null;
    }

    @NotNull
    @Override
    public Collection<Entity> getNearbyEntities(@NotNull Location location, double x, double y, double z, Predicate<Entity> filter) {

        return null;
    }

    @NotNull
    @Override
    public Collection<Entity> getNearbyEntities(@NotNull BoundingBox boundingBox) {

        return null;
    }

    @NotNull
    @Override
    public Collection<Entity> getNearbyEntities(@NotNull BoundingBox boundingBox, Predicate<Entity> filter) {

        return null;
    }

    @Override
    public RayTraceResult rayTraceEntities(@NotNull Location start, @NotNull Vector direction, double maxDistance) {

        return null;
    }

    @Override
    public RayTraceResult rayTraceEntities(@NotNull Location start, @NotNull Vector direction, double maxDistance, double raySize) {

        return null;
    }

    @Override
    public RayTraceResult rayTraceEntities(@NotNull Location start, @NotNull Vector direction, double maxDistance, Predicate<Entity> filter) {

        return null;
    }

    @Override
    public RayTraceResult rayTraceEntities(@NotNull Location start, @NotNull Vector direction, double maxDistance, double raySize, Predicate<Entity> filter) {

        return null;
    }

    @Override
    public RayTraceResult rayTraceBlocks(@NotNull Location start, @NotNull Vector direction, double maxDistance) {

        return null;
    }

    @Override
    public RayTraceResult rayTraceBlocks(@NotNull Location start, @NotNull Vector direction, double maxDistance, @NotNull FluidCollisionMode fluidCollisionMode) {

        return null;
    }

    @Override
    public RayTraceResult rayTraceBlocks(@NotNull Location start, @NotNull Vector direction, double maxDistance, @NotNull FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks) {

        return null;
    }

    @Override
    public RayTraceResult rayTrace(@NotNull Location start, @NotNull Vector direction, double maxDistance, @NotNull FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks, double raySize, Predicate<Entity> filter) {

        return null;
    }

    @NotNull
    @Override
    public String getName() {

        return null;
    }

    @NotNull
    @Override
    public UUID getUID() {

        return null;
    }

    @NotNull
    @Override
    public Location getSpawnLocation() {

        return null;
    }

    @Override
    public boolean setSpawnLocation(@NotNull Location location) {

        return false;
    }

    @Override
    public boolean setSpawnLocation(int i, int i1, int i2, float v) {
        return false;
    }

    @Override
    public boolean setSpawnLocation(int x, int y, int z) {

        return false;
    }

    @Override
    public long getTime() {

        return 0;
    }

    @Override
    public void setTime(long time) {

    }

    @Override
    public long getFullTime() {

        return 0;
    }

    @Override
    public void setFullTime(long time) {

    }

    @Override
    public long getGameTime() {
        return 0;
    }

    @Override
    public boolean hasStorm() {

        return false;
    }

    @Override
    public void setStorm(boolean hasStorm) {

    }

    @Override
    public int getWeatherDuration() {

        return 0;
    }

    @Override
    public void setWeatherDuration(int duration) {

    }

    @Override
    public boolean isThundering() {

        return false;
    }

    @Override
    public void setThundering(boolean thundering) {

    }

    @Override
    public int getThunderDuration() {

        return 0;
    }

    @Override
    public void setThunderDuration(int duration) {

    }

    @Override
    public boolean isClearWeather() {
        return false;
    }

    @Override
    public void setClearWeatherDuration(int i) {

    }

    @Override
    public int getClearWeatherDuration() {
        return 0;
    }

    @Override
    public boolean createExplosion(double x, double y, double z, float power) {

        return false;
    }

    @Override
    public boolean createExplosion(double x, double y, double z, float power, boolean setFire) {

        return false;
    }

    @Override
    public boolean createExplosion(double x, double y, double z, float power, boolean setFire, boolean breakBlocks) {

        return false;
    }

    @Override
    public boolean createExplosion(double x, double y, double z, float power, boolean setFire, boolean breakBlocks, Entity source) {

        return false;
    }

    @Override
    public boolean createExplosion(@NotNull Location loc, float power) {

        return false;
    }

    @Override
    public boolean createExplosion(@NotNull Location loc, float power, boolean setFire) {

        return false;
    }

    @Override
    public boolean createExplosion(@NotNull Location loc, float power, boolean setFire, boolean breakBlocks) {

        return false;
    }

    @Override
    public boolean createExplosion(@NotNull Location loc, float power, boolean setFire, boolean breakBlocks, Entity source) {

        return false;
    }

    @NotNull
    @Override
    public Environment getEnvironment() {

        return null;
    }

    @Override
    public long getSeed() {

        return 0;
    }

    @Override
    public boolean getPVP() {

        return false;
    }

    @Override
    public void setPVP(boolean pvp) {

    }

    @Override
    public ChunkGenerator getGenerator() {

        return null;
    }

    @Override
    public void save() {

    }

    @NotNull
    @Override
    public List<BlockPopulator> getPopulators() {

        return null;
    }

    @NotNull
    @Override
    public <T extends Entity> T spawn(@NotNull Location location, @NotNull Class<T> clazz) throws IllegalArgumentException {

        return null;
    }

    @NotNull
    @Override
    public <T extends Entity> T spawn(@NotNull Location location, @NotNull Class<T> clazz, Consumer<T> function) throws IllegalArgumentException {

        return null;
    }

    @NotNull
    @Override
    public FallingBlock spawnFallingBlock(@NotNull Location location, @NotNull MaterialData data) throws IllegalArgumentException {

        return null;
    }

    @NotNull
    @Override
    public FallingBlock spawnFallingBlock(@NotNull Location location, @NotNull BlockData data) throws IllegalArgumentException {

        return null;
    }

    @NotNull
    @Override
    public FallingBlock spawnFallingBlock(@NotNull Location location, @NotNull Material material, byte data) throws IllegalArgumentException {

        return null;
    }

    @Override
    public void playEffect(@NotNull Location location, @NotNull Effect effect, int data) {

    }

    @Override
    public void playEffect(@NotNull Location location, @NotNull Effect effect, int data, int radius) {

    }

    @Override
    public <T> void playEffect(@NotNull Location location, @NotNull Effect effect, T data) {

    }

    @Override
    public <T> void playEffect(@NotNull Location location, @NotNull Effect effect, T data, int radius) {

    }

    @NotNull
    @Override
    public ChunkSnapshot getEmptyChunkSnapshot(int x, int z, boolean includeBiome, boolean includeBiomeTemp) {

        return null;
    }

    @Override
    public void setSpawnFlags(boolean allowMonsters, boolean allowAnimals) {

    }

    @Override
    public boolean getAllowAnimals() {

        return false;
    }

    @Override
    public boolean getAllowMonsters() {

        return false;
    }

    @NotNull
    @Override
    public Biome getBiome(int x, int z) {

        return null;
    }

    @NotNull
    @Override
    public Biome getBiome(int x, int y, int z) {

        return null;
    }

    @Override
    public void setBiome(int x, int z, @NotNull Biome bio) {

    }

    @Override
    public void setBiome(int x, int y, int z, @NotNull Biome bio) {

    }

    @Override
    public double getTemperature(int x, int z) {

        return 0;
    }

    @Override
    public double getTemperature(int x, int y, int z) {

        return 0;
    }

    @Override
    public double getHumidity(int x, int z) {

        return 0;
    }

    @Override
    public double getHumidity(int x, int y, int z) {

        return 0;
    }

    @Override
    public int getMinHeight() {
        return 0;
    }

    @Override
    public int getMaxHeight() {

        return height;
    }

    @Override
    public int getSeaLevel() {

        return 0;
    }

    @Override
    public boolean getKeepSpawnInMemory() {

        return false;
    }

    @Override
    public void setKeepSpawnInMemory(boolean keepLoaded) {

    }

    @Override
    public boolean isAutoSave() {

        return false;
    }

    @Override
    public void setAutoSave(boolean value) {

    }

    @Override
    public void setDifficulty(@NotNull Difficulty difficulty) {

    }

    @NotNull
    @Override
    public Difficulty getDifficulty() {

        return null;
    }

    @NotNull
    @Override
    public File getWorldFolder() {

        return null;
    }

    @Override
    public WorldType getWorldType() {

        return null;
    }

    @Override
    public boolean canGenerateStructures() {

        return false;
    }

    @Override
    public boolean isHardcore() {

        return false;
    }

    @Override
    public void setHardcore(boolean hardcore) {

    }

    @Override
    public long getTicksPerAnimalSpawns() {

        return 0;
    }

    @Override
    public void setTicksPerAnimalSpawns(int ticksPerAnimalSpawns) {

    }

    @Override
    public long getTicksPerMonsterSpawns() {

        return 0;
    }

    @Override
    public void setTicksPerMonsterSpawns(int ticksPerMonsterSpawns) {

    }

    @Override
    public long getTicksPerWaterSpawns() {

        return 0;
    }

    @Override
    public void setTicksPerWaterSpawns(int ticksPerWaterSpawns) {

    }

    @Override
    public long getTicksPerWaterAmbientSpawns() {

        return 0;
    }

    @Override
    public void setTicksPerWaterAmbientSpawns(int ticksPerAmbientSpawns) {

    }

    @Override
    public long getTicksPerAmbientSpawns() {

        return 0;
    }

    @Override
    public void setTicksPerAmbientSpawns(int ticksPerAmbientSpawns) {

    }

    @Override
    public int getMonsterSpawnLimit() {

        return 0;
    }

    @Override
    public void setMonsterSpawnLimit(int limit) {

    }

    @Override
    public int getAnimalSpawnLimit() {

        return 0;
    }

    @Override
    public void setAnimalSpawnLimit(int limit) {

    }

    @Override
    public int getWaterAnimalSpawnLimit() {

        return 0;
    }

    @Override
    public void setWaterAnimalSpawnLimit(int limit) {

    }

    @Override
    public int getWaterAmbientSpawnLimit() {

        return 0;
    }

    @Override
    public void setWaterAmbientSpawnLimit(int limit) {

    }

    @Override
    public int getAmbientSpawnLimit() {

        return 0;
    }

    @Override
    public void setAmbientSpawnLimit(int limit) {

    }

    @Override
    public void playSound(@NotNull Location location, @NotNull Sound sound, float volume, float pitch) {

    }

    @Override
    public void playSound(@NotNull Location location, @NotNull String sound, float volume, float pitch) {

    }

    @Override
    public void playSound(@NotNull Location location, @NotNull Sound sound, @NotNull SoundCategory category, float volume, float pitch) {

    }

    @Override
    public void playSound(@NotNull Location location, @NotNull String sound, @NotNull SoundCategory category, float volume, float pitch) {

    }

    @NotNull
    @Override
    public String[] getGameRules() {

        return null;
    }

    @Override
    public String getGameRuleValue(String rule) {

        return null;
    }

    @Override
    public boolean setGameRuleValue(@NotNull String rule, @NotNull String value) {

        return false;
    }

    @Override
    public boolean isGameRule(@NotNull String rule) {

        return false;
    }

    @Override
    public <T> T getGameRuleValue(@NotNull GameRule<T> rule) {

        return null;
    }

    @Override
    public <T> T getGameRuleDefault(@NotNull GameRule<T> rule) {

        return null;
    }

    @Override
    public <T> boolean setGameRule(@NotNull GameRule<T> rule, @NotNull T newValue) {

        return false;
    }

    @NotNull
    @Override
    public WorldBorder getWorldBorder() {

        return null;
    }

    @Override
    public void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count) {

    }

    @Override
    public void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count) {

    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, T data) {

    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, T data) {

    }

    @Override
    public void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, double offsetX, double offsetY, double offsetZ) {

    }

    @Override
    public void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ) {

    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, double offsetX, double offsetY, double offsetZ, T data) {

    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, T data) {

    }

    @Override
    public void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {

    }

    @Override
    public void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra) {

    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) {

    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) {

    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force) {

    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force) {

    }

    @Override
    public Location locateNearestStructure(@NotNull Location origin, @NotNull StructureType structureType, int radius, boolean findUnexplored) {
        return null;
    }

    @Override
    public int getViewDistance() {
        return 0;
    }

    @NotNull
    @Override
    public Spigot spigot() {
        return null;
    }

    @Override
    public Raid locateNearestRaid(@NotNull Location location, int radius) {
        return null;
    }

    @NotNull
    @Override
    public List<Raid> getRaids() {
        return null;
    }

    @Override
    public DragonBattle getEnderDragonBattle() {
        return null;
    }
}
