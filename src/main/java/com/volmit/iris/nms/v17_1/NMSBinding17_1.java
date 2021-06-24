package com.volmit.iris.nms.v17_1;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Lifecycle;
import com.volmit.iris.nms.INMSBinding;
import com.volmit.iris.pregen.Pregenerator;
import com.volmit.iris.util.J;
import com.volmit.iris.util.KMap;
import net.minecraft.core.IRegistry;
import net.minecraft.core.RegistryMaterials;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.RegistryReadOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.datafix.DataConverterRegistry;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.MobSpawnerCat;
import net.minecraft.world.entity.npc.MobSpawnerTrader;
import net.minecraft.world.level.EnumGamemode;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.MobSpawner;
import net.minecraft.world.level.WorldSettings;
import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.DimensionManager;
import net.minecraft.world.level.dimension.WorldDimension;
import net.minecraft.world.level.levelgen.GeneratorSettings;
import net.minecraft.world.level.levelgen.MobSpawnerPatrol;
import net.minecraft.world.level.levelgen.MobSpawnerPhantom;
import net.minecraft.world.level.storage.Convertable;
import net.minecraft.world.level.storage.WorldDataServer;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_17_R1.CraftServer;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

public class NMSBinding17_1 implements INMSBinding
{
	private final KMap<Biome, Object> baseBiomeCache = new KMap<>();

	@Override
	public Object getBiomeBase(World world, Biome biome)
	{
		return getBiomeBase(((CraftWorld)world).getHandle().t().d(IRegistry.aO), biome);
	}

	private <T> T getField(Object from, String name)
	{
		try {
			Field f = from.getClass().getDeclaredField(name);
			f.setAccessible(true);
			return (T) f.get(from);
		} catch (Throwable e) {
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public World createWorld(WorldCreator creator) {
		CraftServer cs = (CraftServer) Bukkit.getServer();
		DedicatedServer console = getField(cs, "console");
		Map<String, World> worlds = getField(cs, "worlds");
		Preconditions.checkState(!console.R.isEmpty(), "Cannot create additional worlds on STARTUP");
		Validate.notNull(creator, "Creator may not be null");
		String name = creator.name();
		ChunkGenerator generator = creator.generator();
		File folder = new File(cs.getWorldContainer(), name);
		World world = cs.getWorld(name);
		if (world != null) {
			return world;
		} else if (folder.exists() && !folder.isDirectory()) {
			throw new IllegalArgumentException("File exists with the name '" + name + "' and isn't a folder");
		} else {
			if (generator == null) {
				generator = cs.getGenerator(name);
			}

			ResourceKey actualDimension;
			switch(creator.environment()) {
				case NORMAL:
					actualDimension = WorldDimension.b;
					break;
				case NETHER:
					actualDimension = WorldDimension.c;
					break;
				case THE_END:
					actualDimension = WorldDimension.d;
					break;
				default:
					throw new IllegalArgumentException("Illegal dimension");
			}

			Convertable.ConversionSession worldSession;
			try {
				worldSession = Convertable.a(cs.getWorldContainer().toPath()).c(name, actualDimension);
			} catch (IOException var22) {
				throw new RuntimeException(var22);
			}

			MinecraftServer.convertWorld(worldSession);
			boolean hardcore = creator.hardcore();
			RegistryReadOps<NBTBase> registryreadops = RegistryReadOps.a(DynamicOpsNBT.a, console.aC.i(), console.l);
			WorldDataServer worlddata = (WorldDataServer)worldSession.a(registryreadops, console.datapackconfiguration);
			if (worlddata == null) {
				Properties properties = new Properties();
				properties.put("generator-settings", Objects.toString(creator.generatorSettings()));
				properties.put("level-seed", Objects.toString(creator.seed()));
				properties.put("generate-structures", Objects.toString(creator.generateStructures()));
				properties.put("level-type", Objects.toString(creator.type().getName()));
				GeneratorSettings generatorsettings = GeneratorSettings.a(console.getCustomRegistry(), properties);
				WorldSettings worldSettings = new WorldSettings(name, EnumGamemode.getById(cs.getDefaultGameMode().getValue()), hardcore, EnumDifficulty.b, false, new GameRules(), console.datapackconfiguration);
				worlddata = new WorldDataServer(worldSettings, generatorsettings, Lifecycle.stable());
			}

			worlddata.checkName(name);
			worlddata.a(console.getServerModName(), console.getModded().isPresent());
			if (console.options.has("forceUpgrade")) {
				net.minecraft.server.Main.convertWorld(worldSession, DataConverterRegistry.a(), console.options.has("eraseCache"), () -> {
					return true;
				}, (ImmutableSet)worlddata.getGeneratorSettings().d().d().stream().map((entry) -> {
					return ResourceKey.a(IRegistry.P, ((ResourceKey)entry.getKey()).a());
				}).collect(ImmutableSet.toImmutableSet()));
			}

			long j = BiomeManager.a(creator.seed());
			List<MobSpawner> list = ImmutableList.of(new MobSpawnerPhantom(), new MobSpawnerPatrol(), new MobSpawnerCat(), new VillageSiege(), new MobSpawnerTrader(worlddata));
			RegistryMaterials<WorldDimension> registrymaterials = worlddata.getGeneratorSettings().d();
			WorldDimension worlddimension = (WorldDimension)registrymaterials.a(actualDimension);
			DimensionManager dimensionmanager;
			Object chunkgenerator;
			if (worlddimension == null) {
				dimensionmanager = (DimensionManager)console.l.d(IRegistry.P).d(DimensionManager.k);
				chunkgenerator = GeneratorSettings.a(console.l.d(IRegistry.aO), console.l.d(IRegistry.aH), (new Random()).nextLong());
			} else {
				dimensionmanager = worlddimension.b();
				chunkgenerator = worlddimension.c();
			}

			String levelName = cs.getServer().getDedicatedServerProperties().p;
			ResourceKey worldKey;
			if (name.equals(levelName + "_nether")) {
				worldKey = net.minecraft.world.level.World.g;
			} else if (name.equals(levelName + "_the_end")) {
				worldKey = net.minecraft.world.level.World.h;
			} else {
				worldKey = ResourceKey.a(IRegistry.Q, new MinecraftKey(name.toLowerCase(Locale.ENGLISH)));
			}

			WorldServer internal = new WorldServer(console, console.aA, worldSession, worlddata, worldKey, dimensionmanager, cs.getServer().L.create(11), (net.minecraft.world.level.chunk.ChunkGenerator)chunkgenerator, worlddata.getGeneratorSettings().isDebugWorld(), j, creator.environment() == World.Environment.NORMAL ? list : ImmutableList.of(), true, creator.environment(), generator);
			if (!worlds.containsKey(name.toLowerCase(Locale.ENGLISH))) {
				return null;
			} else {
				console.initWorld(internal, worlddata, worlddata, worlddata.getGeneratorSettings());
				internal.setSpawnFlags(true, true);
				console.R.put(internal.getDimensionKey(), internal);
				cs.getLogger().info("Preparing start region for dime... Oh right, This is Iris.");
				//NO cs.getServer().loadSpawn(internal.getChunkProvider().a.z, internal);
				internal.G.a();
				cs.getPluginManager().callEvent(new WorldLoadEvent(internal.getWorld()));
				J.a(() -> {
					new Pregenerator(internal.getWorld(), 256);
				});
				return internal.getWorld();
			}
		}
	}

	@Override
	public Object getBiomeBase(Object registry, Biome biome) {
		Object v = baseBiomeCache.get(biome);

		if(v != null)
		{
			return v;
		}
		v = org.bukkit.craftbukkit.v1_17_R1.block.CraftBlock.biomeToBiomeBase((IRegistry<BiomeBase>) registry, biome);
		if (v == null) {
			// Ok so there is this new biome name called "CUSTOM" in Paper's new releases.
			// But, this does NOT exist within CraftBukkit which makes it return an error.
			// So, we will just return the ID that the plains biome returns instead.
			return org.bukkit.craftbukkit.v1_17_R1.block.CraftBlock.biomeToBiomeBase((IRegistry<BiomeBase>) registry, Biome.PLAINS);
		}
		baseBiomeCache.put(biome, v);
		return v;
	}

	@Override
	public int getBiomeId(Biome biome) {
		for(World i : Bukkit.getWorlds())
		{
			if(i.getEnvironment().equals(World.Environment.NORMAL))
			{

				IRegistry<BiomeBase> registry = ((CraftWorld)i).getHandle().t().d(IRegistry.aO);

				return registry.getId((BiomeBase) getBiomeBase(registry, biome));
			}
		}

		return biome.ordinal();
	}

	@Override
	public boolean isBukkit() {
		return false;
	}
}
