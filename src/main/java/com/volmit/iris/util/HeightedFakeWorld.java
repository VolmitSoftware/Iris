package com.volmit.iris.util;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.BlockChangeDelegate;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Difficulty;
import org.bukkit.Effect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Raid;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.StructureType;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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

@SuppressWarnings("deprecation")
public class HeightedFakeWorld implements World
{
	private final int height;

	public HeightedFakeWorld(int height)
	{
		this.height = height;
	}

	@Override
	public void sendPluginMessage(Plugin source, String channel, byte[] message)
	{

	}

	@Override
	public Set<String> getListeningPluginChannels()
	{

		return null;
	}

	@Override
	public void setMetadata(String metadataKey, MetadataValue newMetadataValue)
	{

	}

	@Override
	public List<MetadataValue> getMetadata(String metadataKey)
	{

		return null;
	}

	@Override
	public boolean hasMetadata(String metadataKey)
	{

		return false;
	}

	@Override
	public void removeMetadata(String metadataKey, Plugin owningPlugin)
	{

	}

	@Override
	public Block getBlockAt(int x, int y, int z)
	{

		return null;
	}

	@Override
	public Block getBlockAt(Location location)
	{

		return null;
	}

	@Override
	public int getHighestBlockYAt(int x, int z)
	{

		return 0;
	}

	@Override
	public int getHighestBlockYAt(Location location)
	{

		return 0;
	}

	@Override
	public Block getHighestBlockAt(int x, int z)
	{

		return null;
	}

	@Override
	public Block getHighestBlockAt(Location location)
	{

		return null;
	}

	@Override
	public int getHighestBlockYAt(int x, int z, HeightMap heightMap)
	{

		return 0;
	}

	@Override
	public int getHighestBlockYAt(Location location, HeightMap heightMap)
	{

		return 0;
	}

	@Override
	public Block getHighestBlockAt(int x, int z, HeightMap heightMap)
	{

		return null;
	}

	@Override
	public Block getHighestBlockAt(Location location, HeightMap heightMap)
	{

		return null;
	}

	@Override
	public Chunk getChunkAt(int x, int z)
	{

		return null;
	}

	@Override
	public Chunk getChunkAt(Location location)
	{

		return null;
	}

	@Override
	public Chunk getChunkAt(Block block)
	{

		return null;
	}

	@Override
	public boolean isChunkLoaded(Chunk chunk)
	{

		return false;
	}

	@Override
	public Chunk[] getLoadedChunks()
	{

		return null;
	}

	@Override
	public void loadChunk(Chunk chunk)
	{

	}

	@Override
	public boolean isChunkLoaded(int x, int z)
	{

		return false;
	}

	@Override
	public boolean isChunkGenerated(int x, int z)
	{

		return false;
	}

	@Override
	public boolean isChunkInUse(int x, int z)
	{

		return false;
	}

	@Override
	public void loadChunk(int x, int z)
	{

	}

	@Override
	public boolean loadChunk(int x, int z, boolean generate)
	{

		return false;
	}

	@Override
	public boolean unloadChunk(Chunk chunk)
	{

		return false;
	}

	@Override
	public boolean unloadChunk(int x, int z)
	{

		return false;
	}

	@Override
	public boolean unloadChunk(int x, int z, boolean save)
	{

		return false;
	}

	@Override
	public boolean unloadChunkRequest(int x, int z)
	{

		return false;
	}

	@Override
	public boolean regenerateChunk(int x, int z)
	{

		return false;
	}

	@Override
	public boolean refreshChunk(int x, int z)
	{

		return false;
	}

	@Override
	public boolean isChunkForceLoaded(int x, int z)
	{

		return false;
	}

	@Override
	public void setChunkForceLoaded(int x, int z, boolean forced)
	{

	}

	@Override
	public Collection<Chunk> getForceLoadedChunks()
	{

		return null;
	}

	@Override
	public boolean addPluginChunkTicket(int x, int z, Plugin plugin)
	{

		return false;
	}

	@Override
	public boolean removePluginChunkTicket(int x, int z, Plugin plugin)
	{

		return false;
	}

	@Override
	public void removePluginChunkTickets(Plugin plugin)
	{

	}

	@Override
	public Collection<Plugin> getPluginChunkTickets(int x, int z)
	{

		return null;
	}

	@Override
	public Map<Plugin, Collection<Chunk>> getPluginChunkTickets()
	{

		return null;
	}

	@Override
	public Item dropItem(Location location, ItemStack item)
	{

		return null;
	}

	@NotNull @Override public Item dropItem(@NotNull Location location, @NotNull ItemStack itemStack, @Nullable Consumer<Item> consumer) {
		return null;
	}

	@Override
	public Item dropItemNaturally(Location location, ItemStack item)
	{

		return null;
	}

	@NotNull @Override public Item dropItemNaturally(@NotNull Location location, @NotNull ItemStack itemStack, @Nullable Consumer<Item> consumer) {
		return null;
	}

	@Override
	public Arrow spawnArrow(Location location, Vector direction, float speed, float spread)
	{

		return null;
	}

	@Override
	public <T extends AbstractArrow> T spawnArrow(Location location, Vector direction, float speed, float spread, Class<T> clazz)
	{

		return null;
	}

	@Override
	public boolean generateTree(Location location, TreeType type)
	{

		return false;
	}

	@Override
	public boolean generateTree(Location loc, TreeType type, BlockChangeDelegate delegate)
	{

		return false;
	}

	@Override
	public Entity spawnEntity(Location loc, EntityType type)
	{

		return null;
	}

	@Override
	public LightningStrike strikeLightning(Location loc)
	{

		return null;
	}

	@Override
	public LightningStrike strikeLightningEffect(Location loc)
	{

		return null;
	}

	@Override
	public List<Entity> getEntities()
	{

		return null;
	}

	@Override
	public List<LivingEntity> getLivingEntities()
	{

		return null;
	}

	@Override
	public <T extends Entity> Collection<T> getEntitiesByClass(@SuppressWarnings("unchecked") Class<T>... classes)
	{

		return null;
	}

	@Override
	public <T extends Entity> Collection<T> getEntitiesByClass(Class<T> cls)
	{

		return null;
	}

	@Override
	public Collection<Entity> getEntitiesByClasses(Class<?>... classes)
	{

		return null;
	}

	@Override
	public List<Player> getPlayers()
	{

		return null;
	}

	@Override
	public Collection<Entity> getNearbyEntities(Location location, double x, double y, double z)
	{

		return null;
	}

	@Override
	public Collection<Entity> getNearbyEntities(Location location, double x, double y, double z, Predicate<Entity> filter)
	{

		return null;
	}

	@Override
	public Collection<Entity> getNearbyEntities(BoundingBox boundingBox)
	{

		return null;
	}

	@Override
	public Collection<Entity> getNearbyEntities(BoundingBox boundingBox, Predicate<Entity> filter)
	{

		return null;
	}

	@Override
	public RayTraceResult rayTraceEntities(Location start, Vector direction, double maxDistance)
	{

		return null;
	}

	@Override
	public RayTraceResult rayTraceEntities(Location start, Vector direction, double maxDistance, double raySize)
	{

		return null;
	}

	@Override
	public RayTraceResult rayTraceEntities(Location start, Vector direction, double maxDistance, Predicate<Entity> filter)
	{

		return null;
	}

	@Override
	public RayTraceResult rayTraceEntities(Location start, Vector direction, double maxDistance, double raySize, Predicate<Entity> filter)
	{

		return null;
	}

	@Override
	public RayTraceResult rayTraceBlocks(Location start, Vector direction, double maxDistance)
	{

		return null;
	}

	@Override
	public RayTraceResult rayTraceBlocks(Location start, Vector direction, double maxDistance, FluidCollisionMode fluidCollisionMode)
	{

		return null;
	}

	@Override
	public RayTraceResult rayTraceBlocks(Location start, Vector direction, double maxDistance, FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks)
	{

		return null;
	}

	@Override
	public RayTraceResult rayTrace(Location start, Vector direction, double maxDistance, FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks, double raySize, Predicate<Entity> filter)
	{

		return null;
	}

	@Override
	public String getName()
	{

		return null;
	}

	@Override
	public UUID getUID()
	{

		return null;
	}

	@Override
	public Location getSpawnLocation()
	{

		return null;
	}

	@Override
	public boolean setSpawnLocation(Location location)
	{

		return false;
	}

	@Override public boolean setSpawnLocation(int i, int i1, int i2, float v) {
		return false;
	}

	@Override
	public boolean setSpawnLocation(int x, int y, int z)
	{

		return false;
	}

	@Override
	public long getTime()
	{

		return 0;
	}

	@Override
	public void setTime(long time)
	{

	}

	@Override
	public long getFullTime()
	{

		return 0;
	}

	@Override
	public void setFullTime(long time)
	{

	}

	@Override public long getGameTime() {
		return 0;
	}

	@Override
	public boolean hasStorm()
	{

		return false;
	}

	@Override
	public void setStorm(boolean hasStorm)
	{

	}

	@Override
	public int getWeatherDuration()
	{

		return 0;
	}

	@Override
	public void setWeatherDuration(int duration)
	{

	}

	@Override
	public boolean isThundering()
	{

		return false;
	}

	@Override
	public void setThundering(boolean thundering)
	{

	}

	@Override
	public int getThunderDuration()
	{

		return 0;
	}

	@Override
	public void setThunderDuration(int duration)
	{

	}

	@Override public boolean isClearWeather() {
		return false;
	}

	@Override public void setClearWeatherDuration(int i) {

	}

	@Override public int getClearWeatherDuration() {
		return 0;
	}

	@Override
	public boolean createExplosion(double x, double y, double z, float power)
	{

		return false;
	}

	@Override
	public boolean createExplosion(double x, double y, double z, float power, boolean setFire)
	{

		return false;
	}

	@Override
	public boolean createExplosion(double x, double y, double z, float power, boolean setFire, boolean breakBlocks)
	{

		return false;
	}

	@Override
	public boolean createExplosion(double x, double y, double z, float power, boolean setFire, boolean breakBlocks, Entity source)
	{

		return false;
	}

	@Override
	public boolean createExplosion(Location loc, float power)
	{

		return false;
	}

	@Override
	public boolean createExplosion(Location loc, float power, boolean setFire)
	{

		return false;
	}

	@Override
	public boolean createExplosion(Location loc, float power, boolean setFire, boolean breakBlocks)
	{

		return false;
	}

	@Override
	public boolean createExplosion(Location loc, float power, boolean setFire, boolean breakBlocks, Entity source)
	{

		return false;
	}

	@Override
	public Environment getEnvironment()
	{

		return null;
	}

	@Override
	public long getSeed()
	{

		return 0;
	}

	@Override
	public boolean getPVP()
	{

		return false;
	}

	@Override
	public void setPVP(boolean pvp)
	{

	}

	@Override
	public ChunkGenerator getGenerator()
	{

		return null;
	}

	@Override
	public void save()
	{

	}

	@Override
	public List<BlockPopulator> getPopulators()
	{

		return null;
	}

	@Override
	public <T extends Entity> T spawn(Location location, Class<T> clazz) throws IllegalArgumentException
	{

		return null;
	}

	@Override
	public <T extends Entity> T spawn(Location location, Class<T> clazz, Consumer<T> function) throws IllegalArgumentException
	{

		return null;
	}

	@Override
	public FallingBlock spawnFallingBlock(Location location, MaterialData data) throws IllegalArgumentException
	{

		return null;
	}

	@Override
	public FallingBlock spawnFallingBlock(Location location, BlockData data) throws IllegalArgumentException
	{

		return null;
	}

	@Override
	public FallingBlock spawnFallingBlock(Location location, Material material, byte data) throws IllegalArgumentException
	{

		return null;
	}

	@Override
	public void playEffect(Location location, Effect effect, int data)
	{

	}

	@Override
	public void playEffect(Location location, Effect effect, int data, int radius)
	{

	}

	@Override
	public <T> void playEffect(Location location, Effect effect, T data)
	{

	}

	@Override
	public <T> void playEffect(Location location, Effect effect, T data, int radius)
	{

	}

	@Override
	public ChunkSnapshot getEmptyChunkSnapshot(int x, int z, boolean includeBiome, boolean includeBiomeTemp)
	{

		return null;
	}

	@Override
	public void setSpawnFlags(boolean allowMonsters, boolean allowAnimals)
	{

	}

	@Override
	public boolean getAllowAnimals()
	{

		return false;
	}

	@Override
	public boolean getAllowMonsters()
	{

		return false;
	}

	@Override
	public Biome getBiome(int x, int z)
	{

		return null;
	}

	@Override
	public Biome getBiome(int x, int y, int z)
	{

		return null;
	}

	@Override
	public void setBiome(int x, int z, Biome bio)
	{

	}

	@Override
	public void setBiome(int x, int y, int z, Biome bio)
	{

	}

	@Override
	public double getTemperature(int x, int z)
	{

		return 0;
	}

	@Override
	public double getTemperature(int x, int y, int z)
	{

		return 0;
	}

	@Override
	public double getHumidity(int x, int z)
	{

		return 0;
	}

	@Override
	public double getHumidity(int x, int y, int z)
	{

		return 0;
	}

	@Override public int getMinHeight() {
		return 0;
	}

	@Override
	public int getMaxHeight()
	{

		return height;
	}

	@Override
	public int getSeaLevel()
	{

		return 0;
	}

	@Override
	public boolean getKeepSpawnInMemory()
	{

		return false;
	}

	@Override
	public void setKeepSpawnInMemory(boolean keepLoaded)
	{

	}

	@Override
	public boolean isAutoSave()
	{

		return false;
	}

	@Override
	public void setAutoSave(boolean value)
	{

	}

	@Override
	public void setDifficulty(Difficulty difficulty)
	{

	}

	@Override
	public Difficulty getDifficulty()
	{

		return null;
	}

	@Override
	public File getWorldFolder()
	{

		return null;
	}

	@Override
	public WorldType getWorldType()
	{

		return null;
	}

	@Override
	public boolean canGenerateStructures()
	{

		return false;
	}

	@Override
	public boolean isHardcore()
	{

		return false;
	}

	@Override
	public void setHardcore(boolean hardcore)
	{

	}

	@Override
	public long getTicksPerAnimalSpawns()
	{

		return 0;
	}

	@Override
	public void setTicksPerAnimalSpawns(int ticksPerAnimalSpawns)
	{

	}

	@Override
	public long getTicksPerMonsterSpawns()
	{

		return 0;
	}

	@Override
	public void setTicksPerMonsterSpawns(int ticksPerMonsterSpawns)
	{

	}

	@Override
	public long getTicksPerWaterSpawns()
	{

		return 0;
	}

	@Override
	public void setTicksPerWaterSpawns(int ticksPerWaterSpawns)
	{

	}

	@Override
	public long getTicksPerWaterAmbientSpawns()
	{

		return 0;
	}

	@Override
	public void setTicksPerWaterAmbientSpawns(int ticksPerAmbientSpawns)
	{

	}

	@Override
	public long getTicksPerAmbientSpawns()
	{

		return 0;
	}

	@Override
	public void setTicksPerAmbientSpawns(int ticksPerAmbientSpawns)
	{

	}

	@Override
	public int getMonsterSpawnLimit()
	{

		return 0;
	}

	@Override
	public void setMonsterSpawnLimit(int limit)
	{

	}

	@Override
	public int getAnimalSpawnLimit()
	{

		return 0;
	}

	@Override
	public void setAnimalSpawnLimit(int limit)
	{

	}

	@Override
	public int getWaterAnimalSpawnLimit()
	{

		return 0;
	}

	@Override
	public void setWaterAnimalSpawnLimit(int limit)
	{

	}

	@Override
	public int getWaterAmbientSpawnLimit()
	{

		return 0;
	}

	@Override
	public void setWaterAmbientSpawnLimit(int limit)
	{

	}

	@Override
	public int getAmbientSpawnLimit()
	{

		return 0;
	}

	@Override
	public void setAmbientSpawnLimit(int limit)
	{

	}

	@Override
	public void playSound(Location location, Sound sound, float volume, float pitch)
	{

	}

	@Override
	public void playSound(Location location, String sound, float volume, float pitch)
	{

	}

	@Override
	public void playSound(Location location, Sound sound, SoundCategory category, float volume, float pitch)
	{

	}

	@Override
	public void playSound(Location location, String sound, SoundCategory category, float volume, float pitch)
	{

	}

	@Override
	public String[] getGameRules()
	{

		return null;
	}

	@Override
	public String getGameRuleValue(String rule)
	{

		return null;
	}

	@Override
	public boolean setGameRuleValue(String rule, String value)
	{

		return false;
	}

	@Override
	public boolean isGameRule(String rule)
	{

		return false;
	}

	@Override
	public <T> T getGameRuleValue(GameRule<T> rule)
	{

		return null;
	}

	@Override
	public <T> T getGameRuleDefault(GameRule<T> rule)
	{

		return null;
	}

	@Override
	public <T> boolean setGameRule(GameRule<T> rule, T newValue)
	{

		return false;
	}

	@Override
	public WorldBorder getWorldBorder()
	{

		return null;
	}

	@Override
	public void spawnParticle(Particle particle, Location location, int count)
	{

	}

	@Override
	public void spawnParticle(Particle particle, double x, double y, double z, int count)
	{

	}

	@Override
	public <T> void spawnParticle(Particle particle, Location location, int count, T data)
	{

	}

	@Override
	public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, T data)
	{

	}

	@Override
	public void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ)
	{

	}

	@Override
	public void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ)
	{

	}

	@Override
	public <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, T data)
	{

	}

	@Override
	public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, T data)
	{

	}

	@Override
	public void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra)
	{

	}

	@Override
	public void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra)
	{

	}

	@Override
	public <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data)
	{

	}

	@Override
	public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data)
	{

	}

	@Override
	public <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force)
	{

	}

	@Override
	public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force)
	{

	}

	@Override
	public Location locateNearestStructure(Location origin, StructureType structureType, int radius, boolean findUnexplored)
	{
		return null;
	}

	@Override
	public int getViewDistance()
	{
		return 0;
	}

	@Override
	public Spigot spigot()
	{
		return null;
	}

	@Override
	public Raid locateNearestRaid(Location location, int radius)
	{
		return null;
	}

	@Override
	public List<Raid> getRaids()
	{
		return null;
	}

	@Override
	public DragonBattle getEnderDragonBattle()
	{
		return null;
	}
}
