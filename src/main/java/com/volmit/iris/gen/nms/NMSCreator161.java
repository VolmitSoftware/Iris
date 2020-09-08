package com.volmit.iris.gen.nms;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.v1_16_R1.CraftServer;
import org.bukkit.craftbukkit.v1_16_R1.CraftWorld;
import org.bukkit.event.Event;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.volmit.iris.util.V;

import net.minecraft.server.v1_16_R1.BiomeManager;
import net.minecraft.server.v1_16_R1.Convertable;
import net.minecraft.server.v1_16_R1.DedicatedServer;
import net.minecraft.server.v1_16_R1.DimensionManager;
import net.minecraft.server.v1_16_R1.DynamicOpsNBT;
import net.minecraft.server.v1_16_R1.EnumDifficulty;
import net.minecraft.server.v1_16_R1.EnumGamemode;
import net.minecraft.server.v1_16_R1.GameRules;
import net.minecraft.server.v1_16_R1.GeneratorSettings;
import net.minecraft.server.v1_16_R1.IRegistry;
import net.minecraft.server.v1_16_R1.IWorldDataServer;
import net.minecraft.server.v1_16_R1.MinecraftKey;
import net.minecraft.server.v1_16_R1.MinecraftServer;
import net.minecraft.server.v1_16_R1.MobSpawner;
import net.minecraft.server.v1_16_R1.MobSpawnerCat;
import net.minecraft.server.v1_16_R1.MobSpawnerPatrol;
import net.minecraft.server.v1_16_R1.MobSpawnerPhantom;
import net.minecraft.server.v1_16_R1.MobSpawnerTrader;
import net.minecraft.server.v1_16_R1.NBTBase;
import net.minecraft.server.v1_16_R1.RegistryMaterials;
import net.minecraft.server.v1_16_R1.RegistryReadOps;
import net.minecraft.server.v1_16_R1.ResourceKey;
import net.minecraft.server.v1_16_R1.SaveData;
import net.minecraft.server.v1_16_R1.VillageSiege;
import net.minecraft.server.v1_16_R1.WorldDataServer;
import net.minecraft.server.v1_16_R1.WorldDimension;
import net.minecraft.server.v1_16_R1.WorldServer;
import net.minecraft.server.v1_16_R1.WorldSettings;
import net.minecraft.server.v1_16_R1.ChunkCoordIntPair;
import net.minecraft.server.v1_16_R1.TicketType;
import net.minecraft.server.v1_16_R1.Unit;

public class NMSCreator161
{
	public static void addStartTicket(Location center, int size)
	{
		((CraftWorld) center.getWorld()).getHandle().getChunkProvider().addTicket(TicketType.START, new ChunkCoordIntPair(center.getBlockX() >> 4, center.getBlockZ() >> 4), size, Unit.INSTANCE);
	}

	@SuppressWarnings({"unchecked", "rawtypes", "resource"})
	public static World createWorld(WorldCreator creator, boolean loadSpawn)
	{
		CraftServer server = ((CraftServer) Bukkit.getServer());
		Map<String, World> worlds = new V(server).get("worlds");
		DedicatedServer console = new V(server).get("console");
		Preconditions.checkState(!console.worldServer.isEmpty(), (Object) "Cannot create additional worlds on STARTUP");
		Validate.notNull((Object) creator, "Creator may not be null");
		final String name = creator.name();
		org.bukkit.generator.ChunkGenerator generator = creator.generator();
		final File folder = new File(server.getWorldContainer(), name);
		final World world = server.getWorld(name);

		if(world != null)
		{
			return world;
		}

		if(folder.exists() && !folder.isDirectory())
		{
			throw new IllegalArgumentException("File exists with the name '" + name + "' and isn't a folder");
		}

		if(generator == null)
		{
			generator = server.getGenerator(name);
		}

		ResourceKey<WorldDimension> actualDimension = null;
		switch(creator.environment())
		{
			case NORMAL:
			{
				actualDimension = (ResourceKey<WorldDimension>) WorldDimension.OVERWORLD;
				break;
			}
			case NETHER:
			{
				actualDimension = (ResourceKey<WorldDimension>) WorldDimension.THE_NETHER;
				break;
			}
			case THE_END:
			{
				actualDimension = (ResourceKey<WorldDimension>) WorldDimension.THE_END;
				break;
			}
			default:
			{
				throw new IllegalArgumentException("Illegal dimension");
			}
		}
		Convertable.ConversionSession worldSession;
		try
		{
			worldSession = Convertable.a(server.getWorldContainer().toPath()).c(name, (ResourceKey) actualDimension);
		}
		catch(IOException ex)
		{
			throw new RuntimeException(ex);
		}
		MinecraftServer.convertWorld(worldSession);
		final boolean hardcore = creator.hardcore();
		final RegistryReadOps<NBTBase> registryreadops = (RegistryReadOps<NBTBase>) RegistryReadOps.a((DynamicOps) DynamicOpsNBT.a, console.dataPackResources.h(), console.f);
		WorldDataServer worlddata = (WorldDataServer) worldSession.a((DynamicOps) registryreadops, console.datapackconfiguration);
		if(worlddata == null)
		{
			final Properties properties = new Properties();
			properties.put("generator-settings", Objects.toString(creator.generatorSettings()));
			properties.put("level-seed", Objects.toString(creator.seed()));
			properties.put("generate-structures", Objects.toString(creator.generateStructures()));
			properties.put("level-type", Objects.toString(creator.type().getName()));
			GeneratorSettings generatorsettings = GeneratorSettings.a((Properties) properties);
			@SuppressWarnings("deprecation")
			final WorldSettings worldSettings = new WorldSettings(name, EnumGamemode.getById(server.getDefaultGameMode().getValue()), hardcore, EnumDifficulty.EASY, false, new GameRules(), console.datapackconfiguration);
			worlddata = new WorldDataServer(worldSettings, generatorsettings, Lifecycle.stable());
		}
		worlddata.checkName(name);
		worlddata.a(console.getServerModName(), console.getModded().isPresent());
		final long j = BiomeManager.a(creator.seed());
		final List<MobSpawner> list = (List<MobSpawner>) ImmutableList.of((MobSpawner) new MobSpawnerPhantom(), (MobSpawner) new MobSpawnerPatrol(), (MobSpawner) new MobSpawnerCat(), (MobSpawner) new VillageSiege(), (MobSpawner) new MobSpawnerTrader((IWorldDataServer) worlddata));
		RegistryMaterials registrymaterials = worlddata.getGeneratorSettings().e();
		final WorldDimension worlddimension = (WorldDimension) registrymaterials.a((ResourceKey) actualDimension);
		DimensionManager dimensionmanager;
		net.minecraft.server.v1_16_R1.ChunkGenerator chunkgenerator;

		if(worlddimension == null)
		{
			dimensionmanager = DimensionManager.a();
			chunkgenerator = GeneratorSettings.a((long) new Random().nextLong());
		}

		else
		{
			dimensionmanager = worlddimension.b();
			chunkgenerator = worlddimension.c();
		}

		ResourceKey typeKey = (ResourceKey) console.f.a().c(dimensionmanager).orElseThrow(() -> new IllegalStateException("Unregistered dimension type: " + (Object) dimensionmanager));
		ResourceKey worldKey = ResourceKey.a((ResourceKey) IRegistry.ae, (MinecraftKey) new MinecraftKey(name.toLowerCase(Locale.ENGLISH)));

		//@builder
        WorldServer internal = new WorldServer(
        		(MinecraftServer)console, 
        		console.executorService, 
        		worldSession, 
        		(IWorldDataServer)worlddata, 
        		worldKey, 
        		typeKey, 
        		dimensionmanager, 
        		server.getServer().worldLoadListenerFactory.create(11), 
        		chunkgenerator, 
        		worlddata.getGeneratorSettings().isDebugWorld(), 
        		j, 
        		(List)(creator.environment() == World.Environment.NORMAL ? list : ImmutableList.of()),
        		true,
        		creator.environment(), 
        		generator);
        
		//@done
		if(!worlds.containsKey(name.toLowerCase(Locale.ENGLISH)))
		{
			try
			{
				internal.close();
			}

			catch(IOException e)
			{
				e.printStackTrace();
			}

			return null;
		}
		console.initWorld(internal, (IWorldDataServer) worlddata, (SaveData) worlddata, worlddata.getGeneratorSettings());
		internal.setSpawnFlags(true, true);
		console.worldServer.put(internal.getDimensionKey(), internal);
		server.getPluginManager().callEvent((Event) new WorldInitEvent((World) internal.getWorld()));
		if(loadSpawn)
		{
			server.getServer().loadSpawn(internal.getChunkProvider().playerChunkMap.worldLoadListener, internal);
		}

		else
		{
			MinecraftServer.LOGGER.info("Preparing start region for dimens... Oh wait, We don't do that here anymore.");
		}

		server.getPluginManager().callEvent((Event) new WorldLoadEvent((World) internal.getWorld()));
		return (World) internal.getWorld();
	}
}
