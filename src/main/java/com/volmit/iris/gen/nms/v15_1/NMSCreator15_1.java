package com.volmit.iris.gen.nms;

import java.io.File;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.v1_15_R1.CraftServer;
import org.bukkit.craftbukkit.v1_15_R1.CraftWorld;
import org.bukkit.event.Event;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.ChunkGenerator;

import com.google.common.base.Preconditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.volmit.iris.util.V;

import net.minecraft.server.v1_15_R1.DedicatedServer;
import net.minecraft.server.v1_15_R1.DimensionManager;
import net.minecraft.server.v1_15_R1.EnumDifficulty;
import net.minecraft.server.v1_15_R1.EnumGamemode;
import net.minecraft.server.v1_15_R1.GameProfilerFiller;
import net.minecraft.server.v1_15_R1.MinecraftServer;
import net.minecraft.server.v1_15_R1.WorldData;
import net.minecraft.server.v1_15_R1.WorldNBTStorage;
import net.minecraft.server.v1_15_R1.WorldProvider;
import net.minecraft.server.v1_15_R1.WorldServer;
import net.minecraft.server.v1_15_R1.WorldSettings;
import net.minecraft.server.v1_15_R1.ChunkCoordIntPair;
import net.minecraft.server.v1_15_R1.TicketType;
import net.minecraft.server.v1_15_R1.Unit;

public class NMSCreator151
{
	public static void addStartTicket(Location center, int size)
	{
		((CraftWorld) center.getWorld()).getHandle().getChunkProvider().addTicket(TicketType.START, new ChunkCoordIntPair(center.getBlockX() >> 4, center.getBlockZ() >> 4), size, Unit.INSTANCE);
	}
	
	@SuppressWarnings({"resource", "deprecation"})
	public static World createWorld(WorldCreator creator, boolean loadSpawn)
	{
		CraftServer server = ((CraftServer) Bukkit.getServer());
		Map<String, World> worlds = new V(server).get("worlds");
		DedicatedServer console = new V(server).get("console");
		WorldSettings worldSettings;
		Preconditions.checkState((boolean) (!console.worldServer.isEmpty()), (Object) "Cannot create additional worlds on STARTUP");
		Validate.notNull((Object) creator, (String) "Creator may not be null");
		String name = creator.name();
		ChunkGenerator generator = creator.generator();
		File folder = new File(server.getWorldContainer(), name);
		World world = server.getWorld(name);
		net.minecraft.server.v1_15_R1.WorldType type = net.minecraft.server.v1_15_R1.WorldType.getType((String) creator.type().getName());
		boolean generateStructures = creator.generateStructures();
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
		console.convertWorld(name);
		int dimension = 10 + console.worldServer.size();
		boolean used = false;
		block0: do
		{
			for(WorldServer ss : console.getWorlds())
			{
				@SuppressWarnings("unused")
				boolean bl = used = ss.getWorldProvider().getDimensionManager().getDimensionID() == dimension;
				if(!used)
					continue;
				++dimension;
				continue block0;
			}
		}
		while(used);
		boolean hardcore = false;
		WorldNBTStorage sdm = new WorldNBTStorage(server.getWorldContainer(), name, (MinecraftServer) server.getServer(), server.getHandle().getServer().dataConverterManager);
		WorldData worlddata = sdm.getWorldData();
		if(worlddata == null)
		{
			worldSettings = new WorldSettings(creator.seed(), EnumGamemode.getById((int) server.getDefaultGameMode().getValue()), generateStructures, hardcore, type);
			JsonElement parsedSettings = new JsonParser().parse(creator.generatorSettings());
			if(parsedSettings.isJsonObject())
			{
				worldSettings.setGeneratorSettings((JsonElement) parsedSettings.getAsJsonObject());
			}
			worlddata = new WorldData(worldSettings, name);
		}
		else
		{
			worlddata.setName(name);
			worldSettings = new WorldSettings(worlddata);
		}
		DimensionManager actualDimension = DimensionManager.a((int) creator.environment().getId());
		DimensionManager internalDimension = DimensionManager.register((String) name.toLowerCase(Locale.ENGLISH), (DimensionManager) new DimensionManager(dimension, actualDimension.getSuffix(), actualDimension.folder, (w, manager) -> (WorldProvider) manager.providerFactory.apply(w, manager), actualDimension.hasSkyLight(), actualDimension.getGenLayerZoomer(), actualDimension));
		//@builder
        WorldServer internal = new WorldServer(
        		(MinecraftServer)console, 
        		console.executorService, 
        		sdm, 
        		worlddata, 
        		internalDimension, 
        		(GameProfilerFiller)console.getMethodProfiler(), 
        		server.getServer().worldLoadListenerFactory.create(11), 
        		creator.environment(), 
        		generator);
        //@done
		if(!worlds.containsKey(name.toLowerCase(Locale.ENGLISH)))
		{
			return null;
		}
		console.initWorld(internal, worlddata, worldSettings);
		internal.worldData.setDifficulty(EnumDifficulty.EASY);
		internal.setSpawnFlags(true, true);
		console.worldServer.put(internal.getWorldProvider().getDimensionManager(), internal);
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
		return internal.getWorld();
	}
}
