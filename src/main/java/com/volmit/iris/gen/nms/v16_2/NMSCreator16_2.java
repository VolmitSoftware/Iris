package com.volmit.iris.gen.nms.v16_2;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.v1_16_R2.CraftServer;
import org.bukkit.event.Event;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.volmit.iris.gen.nms.INMSCreator;
import com.volmit.iris.gen.scaffold.IrisWorlds;
import com.volmit.iris.gen.scaffold.Provisioned;
import com.volmit.iris.util.O;
import com.volmit.iris.util.V;

import io.papermc.lib.PaperLib;
import net.minecraft.server.v1_16_R2.BiomeBase;
import net.minecraft.server.v1_16_R2.BiomeManager;
import net.minecraft.server.v1_16_R2.Convertable;
import net.minecraft.server.v1_16_R2.DataConverterRegistry;
import net.minecraft.server.v1_16_R2.DedicatedServer;
import net.minecraft.server.v1_16_R2.DimensionManager;
import net.minecraft.server.v1_16_R2.DynamicOpsNBT;
import net.minecraft.server.v1_16_R2.EnumDifficulty;
import net.minecraft.server.v1_16_R2.EnumGamemode;
import net.minecraft.server.v1_16_R2.GameRules;
import net.minecraft.server.v1_16_R2.GeneratorSettingBase;
import net.minecraft.server.v1_16_R2.GeneratorSettings;
import net.minecraft.server.v1_16_R2.IRegistry;
import net.minecraft.server.v1_16_R2.IRegistryCustom.Dimension;
import net.minecraft.server.v1_16_R2.IWorldDataServer;
import net.minecraft.server.v1_16_R2.MinecraftKey;
import net.minecraft.server.v1_16_R2.MinecraftServer;
import net.minecraft.server.v1_16_R2.MobSpawner;
import net.minecraft.server.v1_16_R2.MobSpawnerCat;
import net.minecraft.server.v1_16_R2.MobSpawnerPatrol;
import net.minecraft.server.v1_16_R2.MobSpawnerPhantom;
import net.minecraft.server.v1_16_R2.MobSpawnerTrader;
import net.minecraft.server.v1_16_R2.NBTBase;
import net.minecraft.server.v1_16_R2.RegistryReadOps;
import net.minecraft.server.v1_16_R2.ResourceKey;
import net.minecraft.server.v1_16_R2.SaveData;
import net.minecraft.server.v1_16_R2.VillageSiege;
import net.minecraft.server.v1_16_R2.WorldChunkManager;
import net.minecraft.server.v1_16_R2.WorldChunkManagerOverworld;
import net.minecraft.server.v1_16_R2.WorldDataServer;
import net.minecraft.server.v1_16_R2.WorldDimension;
import net.minecraft.server.v1_16_R2.WorldServer;
import net.minecraft.server.v1_16_R2.WorldSettings;

class NMSCreator16_2 implements INMSCreator
{
	@SuppressWarnings({"unchecked", "rawtypes", "resource"})
	public World createWorld(WorldCreator creator, boolean loadSpawn)
	{
		Provisioned pro = (Provisioned) creator.generator();
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
		final RegistryReadOps<NBTBase> registryreadops = (RegistryReadOps<NBTBase>) RegistryReadOps.a((DynamicOps) DynamicOpsNBT.a, console.dataPackResources.h(), getConsoleDimension(console));
		WorldDataServer worlddata = (WorldDataServer) worldSession.a((DynamicOps) registryreadops, console.datapackconfiguration);
		if(worlddata == null)
		{
			final Properties properties = new Properties();
			properties.put("generator-settings", Objects.toString(creator.generatorSettings()));
			properties.put("level-seed", Objects.toString(creator.seed()));
			properties.put("generate-structures", Objects.toString(creator.generateStructures()));
			properties.put("level-type", Objects.toString(creator.type().getName()));
			final GeneratorSettings generatorsettings = GeneratorSettings.a(getConsoleDimension(console), properties);
			@SuppressWarnings("deprecation")
			final WorldSettings worldSettings = new WorldSettings(name, EnumGamemode.getById(server.getDefaultGameMode().getValue()), hardcore, EnumDifficulty.EASY, false, new GameRules(), console.datapackconfiguration);
			worlddata = new WorldDataServer(worldSettings, generatorsettings, Lifecycle.stable());
		}
		worlddata.checkName(name);
		worlddata.a(console.getServerModName(), console.getModded().isPresent());
		if(console.options.has("forceUpgrade"))
		{
			net.minecraft.server.v1_16_R2.Main.convertWorld(worldSession, DataConverterRegistry.a(), console.options.has("eraseCache"), () -> true, (ImmutableSet) worlddata.getGeneratorSettings().d().d().stream().map(entry -> ResourceKey.a(IRegistry.K, entry.getKey().a())).collect(ImmutableSet.toImmutableSet()));
		}
		final long j = BiomeManager.a(creator.seed());
		final List<MobSpawner> list = (List<MobSpawner>) ImmutableList.of((MobSpawner) new MobSpawnerPhantom(), (MobSpawner) new MobSpawnerPatrol(), (MobSpawner) new MobSpawnerCat(), (MobSpawner) new VillageSiege(), (MobSpawner) new MobSpawnerTrader((IWorldDataServer) worlddata));
		DimensionManager dimensionmanager;
		net.minecraft.server.v1_16_R2.ChunkGenerator chunkgenerator;
		long ll = creator.seed();
		dimensionmanager = (DimensionManager) getConsoleDimension(console).a().d(DimensionManager.OVERWORLD);
		O<WorldServer> ws = new O<WorldServer>();
		chunkgenerator = PaperLib.isPaper() ? new NMSChunkGenerator16_2_PAPER(pro, ws, (WorldChunkManager) new WorldChunkManagerOverworld(ll, false, false, (IRegistry<BiomeBase>) getConsoleDimension(console).b(IRegistry.ay)), ll, () -> (GeneratorSettingBase) getConsoleDimension(console).b(IRegistry.ar).d(GeneratorSettingBase.c)) : new NMSChunkGenerator16_2_SPIGOT(pro, ws, (WorldChunkManager) new WorldChunkManagerOverworld(ll, false, false, (IRegistry<BiomeBase>) getConsoleDimension(console).b(IRegistry.ay)), ll, () -> (GeneratorSettingBase) getConsoleDimension(console).b(IRegistry.ar).d(GeneratorSettingBase.c));
		final ResourceKey<net.minecraft.server.v1_16_R2.World> worldKey = (ResourceKey<net.minecraft.server.v1_16_R2.World>) ResourceKey.a(IRegistry.L, new MinecraftKey(name.toLowerCase(Locale.ENGLISH)));
		//@builder
		final WorldServer internal = new WorldServer((MinecraftServer) console, 
				console.executorService, worldSession, 
				(IWorldDataServer) worlddata, 
				(ResourceKey) worldKey, 
				dimensionmanager, 
				server.getServer().worldLoadListenerFactory.create(11), 
				chunkgenerator, 
				worlddata.getGeneratorSettings().isDebugWorld(), 
				j, 
				(List) ((creator.environment() == World.Environment.NORMAL) ? list : ImmutableList.of()),
				true, 
				creator.environment(), 
				server.getGenerator(name));
		//@done
		IrisWorlds.register(internal.getWorld(), pro);
		ws.set(internal);
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

	private Dimension getConsoleDimension(DedicatedServer console)
	{
		if(PaperLib.isPaper())
		{
			return new V((MinecraftServer) console, true).get("customRegistry");
		}

		return console.f;
	}
}
