package com.volmit.iris.gen.standalone;

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

@SuppressWarnings("deprecation")
public class StandaloneWorld implements World
{
	@Override
	public Set<String> getListeningPluginChannels()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void sendPluginMessage(Plugin arg0, String arg1, byte[] arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public List<MetadataValue> getMetadata(String arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean hasMetadata(String arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void removeMetadata(String arg0, Plugin arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setMetadata(String arg0, MetadataValue arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean addPluginChunkTicket(int arg0, int arg1, Plugin arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean canGenerateStructures()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean createExplosion(Location arg0, float arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean createExplosion(Location arg0, float arg1, boolean arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean createExplosion(double arg0, double arg1, double arg2, float arg3)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean createExplosion(Location arg0, float arg1, boolean arg2, boolean arg3)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean createExplosion(double arg0, double arg1, double arg2, float arg3, boolean arg4)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean createExplosion(Location arg0, float arg1, boolean arg2, boolean arg3, Entity arg4)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean createExplosion(double arg0, double arg1, double arg2, float arg3, boolean arg4, boolean arg5)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean createExplosion(double arg0, double arg1, double arg2, float arg3, boolean arg4, boolean arg5, Entity arg6)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Item dropItem(Location arg0, ItemStack arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Item dropItemNaturally(Location arg0, ItemStack arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean generateTree(Location arg0, TreeType arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean generateTree(Location arg0, TreeType arg1, BlockChangeDelegate arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean getAllowAnimals()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean getAllowMonsters()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public int getAmbientSpawnLimit()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public int getAnimalSpawnLimit()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Biome getBiome(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Biome getBiome(int arg0, int arg1, int arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Block getBlockAt(Location arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Block getBlockAt(int arg0, int arg1, int arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Chunk getChunkAt(Location arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Chunk getChunkAt(Block arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Chunk getChunkAt(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Difficulty getDifficulty()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public ChunkSnapshot getEmptyChunkSnapshot(int arg0, int arg1, boolean arg2, boolean arg3)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public DragonBattle getEnderDragonBattle()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public List<Entity> getEntities()
	{
		throw new UnsupportedOperationException();

	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends Entity> Collection<T> getEntitiesByClass(Class<T>... arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T extends Entity> Collection<T> getEntitiesByClass(Class<T> arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Collection<Entity> getEntitiesByClasses(Class<?>... arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Environment getEnvironment()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Collection<Chunk> getForceLoadedChunks()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public long getFullTime()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T> T getGameRuleDefault(GameRule<T> arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public String getGameRuleValue(String arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T> T getGameRuleValue(GameRule<T> arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public String[] getGameRules()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public ChunkGenerator getGenerator()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Block getHighestBlockAt(Location arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Block getHighestBlockAt(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Block getHighestBlockAt(Location arg0, HeightMap arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Block getHighestBlockAt(int arg0, int arg1, HeightMap arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public int getHighestBlockYAt(Location arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public int getHighestBlockYAt(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public int getHighestBlockYAt(Location arg0, HeightMap arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public int getHighestBlockYAt(int arg0, int arg1, HeightMap arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public double getHumidity(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public double getHumidity(int arg0, int arg1, int arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean getKeepSpawnInMemory()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public List<LivingEntity> getLivingEntities()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Chunk[] getLoadedChunks()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public int getMaxHeight()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public int getMonsterSpawnLimit()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public String getName()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Collection<Entity> getNearbyEntities(BoundingBox arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Collection<Entity> getNearbyEntities(BoundingBox arg0, Predicate<Entity> arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Collection<Entity> getNearbyEntities(Location arg0, double arg1, double arg2, double arg3)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Collection<Entity> getNearbyEntities(Location arg0, double arg1, double arg2, double arg3, Predicate<Entity> arg4)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean getPVP()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public List<Player> getPlayers()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Map<Plugin, Collection<Chunk>> getPluginChunkTickets()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Collection<Plugin> getPluginChunkTickets(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public List<BlockPopulator> getPopulators()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public List<Raid> getRaids()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public int getSeaLevel()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public long getSeed()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Location getSpawnLocation()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public double getTemperature(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public double getTemperature(int arg0, int arg1, int arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public int getThunderDuration()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public long getTicksPerAmbientSpawns()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public long getTicksPerAnimalSpawns()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public long getTicksPerMonsterSpawns()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public long getTicksPerWaterAmbientSpawns()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public long getTicksPerWaterSpawns()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public long getTime()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public UUID getUID()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public int getViewDistance()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public int getWaterAmbientSpawnLimit()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public int getWaterAnimalSpawnLimit()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public int getWeatherDuration()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public WorldBorder getWorldBorder()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public File getWorldFolder()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public WorldType getWorldType()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean hasStorm()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean isAutoSave()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean isChunkForceLoaded(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean isChunkGenerated(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean isChunkInUse(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean isChunkLoaded(Chunk arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean isChunkLoaded(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean isGameRule(String arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean isHardcore()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean isThundering()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void loadChunk(Chunk arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void loadChunk(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean loadChunk(int arg0, int arg1, boolean arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Raid locateNearestRaid(Location arg0, int arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Location locateNearestStructure(Location arg0, StructureType arg1, int arg2, boolean arg3)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void playEffect(Location arg0, Effect arg1, int arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T> void playEffect(Location arg0, Effect arg1, T arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void playEffect(Location arg0, Effect arg1, int arg2, int arg3)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T> void playEffect(Location arg0, Effect arg1, T arg2, int arg3)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void playSound(Location arg0, Sound arg1, float arg2, float arg3)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void playSound(Location arg0, String arg1, float arg2, float arg3)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void playSound(Location arg0, Sound arg1, SoundCategory arg2, float arg3, float arg4)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void playSound(Location arg0, String arg1, SoundCategory arg2, float arg3, float arg4)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public RayTraceResult rayTrace(Location arg0, Vector arg1, double arg2, FluidCollisionMode arg3, boolean arg4, double arg5, Predicate<Entity> arg6)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public RayTraceResult rayTraceBlocks(Location arg0, Vector arg1, double arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public RayTraceResult rayTraceBlocks(Location arg0, Vector arg1, double arg2, FluidCollisionMode arg3)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public RayTraceResult rayTraceBlocks(Location arg0, Vector arg1, double arg2, FluidCollisionMode arg3, boolean arg4)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public RayTraceResult rayTraceEntities(Location arg0, Vector arg1, double arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public RayTraceResult rayTraceEntities(Location arg0, Vector arg1, double arg2, double arg3)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public RayTraceResult rayTraceEntities(Location arg0, Vector arg1, double arg2, Predicate<Entity> arg3)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public RayTraceResult rayTraceEntities(Location arg0, Vector arg1, double arg2, double arg3, Predicate<Entity> arg4)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean refreshChunk(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean regenerateChunk(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean removePluginChunkTicket(int arg0, int arg1, Plugin arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void removePluginChunkTickets(Plugin arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void save()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setAmbientSpawnLimit(int arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setAnimalSpawnLimit(int arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setAutoSave(boolean arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setBiome(int arg0, int arg1, Biome arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setBiome(int arg0, int arg1, int arg2, Biome arg3)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setChunkForceLoaded(int arg0, int arg1, boolean arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setDifficulty(Difficulty arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setFullTime(long arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T> boolean setGameRule(GameRule<T> arg0, T arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean setGameRuleValue(String arg0, String arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setHardcore(boolean arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setKeepSpawnInMemory(boolean arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setMonsterSpawnLimit(int arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setPVP(boolean arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setSpawnFlags(boolean arg0, boolean arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean setSpawnLocation(Location arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean setSpawnLocation(int arg0, int arg1, int arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setStorm(boolean arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setThunderDuration(int arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setThundering(boolean arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setTicksPerAmbientSpawns(int arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setTicksPerAnimalSpawns(int arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setTicksPerMonsterSpawns(int arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setTicksPerWaterAmbientSpawns(int arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setTicksPerWaterSpawns(int arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setTime(long arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setWaterAmbientSpawnLimit(int arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setWaterAnimalSpawnLimit(int arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void setWeatherDuration(int arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T extends Entity> T spawn(Location arg0, Class<T> arg1) throws IllegalArgumentException
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T extends Entity> T spawn(Location arg0, Class<T> arg1, Consumer<T> arg2) throws IllegalArgumentException
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Arrow spawnArrow(Location arg0, Vector arg1, float arg2, float arg3)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T extends AbstractArrow> T spawnArrow(Location arg0, Vector arg1, float arg2, float arg3, Class<T> arg4)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Entity spawnEntity(Location arg0, EntityType arg1)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public FallingBlock spawnFallingBlock(Location arg0, MaterialData arg1) throws IllegalArgumentException
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public FallingBlock spawnFallingBlock(Location arg0, BlockData arg1) throws IllegalArgumentException
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public FallingBlock spawnFallingBlock(Location arg0, Material arg1, byte arg2) throws IllegalArgumentException
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void spawnParticle(Particle arg0, Location arg1, int arg2)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T> void spawnParticle(Particle arg0, Location arg1, int arg2, T arg3)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void spawnParticle(Particle arg0, double arg1, double arg2, double arg3, int arg4)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T> void spawnParticle(Particle arg0, double arg1, double arg2, double arg3, int arg4, T arg5)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void spawnParticle(Particle arg0, Location arg1, int arg2, double arg3, double arg4, double arg5)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T> void spawnParticle(Particle arg0, Location arg1, int arg2, double arg3, double arg4, double arg5, T arg6)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void spawnParticle(Particle arg0, Location arg1, int arg2, double arg3, double arg4, double arg5, double arg6)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void spawnParticle(Particle arg0, double arg1, double arg2, double arg3, int arg4, double arg5, double arg6, double arg7)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T> void spawnParticle(Particle arg0, Location arg1, int arg2, double arg3, double arg4, double arg5, double arg6, T arg7)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T> void spawnParticle(Particle arg0, double arg1, double arg2, double arg3, int arg4, double arg5, double arg6, double arg7, T arg8)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public void spawnParticle(Particle arg0, double arg1, double arg2, double arg3, int arg4, double arg5, double arg6, double arg7, double arg8)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T> void spawnParticle(Particle arg0, Location arg1, int arg2, double arg3, double arg4, double arg5, double arg6, T arg7, boolean arg8)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T> void spawnParticle(Particle arg0, double arg1, double arg2, double arg3, int arg4, double arg5, double arg6, double arg7, double arg8, T arg9)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public <T> void spawnParticle(Particle arg0, double arg1, double arg2, double arg3, int arg4, double arg5, double arg6, double arg7, double arg8, T arg9, boolean arg10)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public Spigot spigot()
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public LightningStrike strikeLightning(Location arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public LightningStrike strikeLightningEffect(Location arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean unloadChunk(Chunk arg0)
	{
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean unloadChunk(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean unloadChunk(int arg0, int arg1, boolean arg2)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean unloadChunkRequest(int arg0, int arg1)
	{
		throw new UnsupportedOperationException();
	}

}
