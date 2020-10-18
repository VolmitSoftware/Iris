package com.volmit.iris.gen.nms;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Jigsaw;
import org.bukkit.craftbukkit.v1_16_R2.CraftWorld;

import net.minecraft.server.v1_16_R2.BlockJigsaw;
import net.minecraft.server.v1_16_R2.ChunkGenerator;
import net.minecraft.server.v1_16_R2.DimensionManager;
import net.minecraft.server.v1_16_R2.IChunkAccess;
import net.minecraft.server.v1_16_R2.IRegistry;
import net.minecraft.server.v1_16_R2.IRegistryCustom;
import net.minecraft.server.v1_16_R2.IRegistryWritable;
import net.minecraft.server.v1_16_R2.MinecraftServer;
import net.minecraft.server.v1_16_R2.RegistryMaterials;
import net.minecraft.server.v1_16_R2.ResourceKey;
import net.minecraft.server.v1_16_R2.StructureGenerator;
import net.minecraft.server.v1_16_R2.StructureManager;
import net.minecraft.server.v1_16_R2.StructureSettings;
import net.minecraft.server.v1_16_R2.WorldDataServer;
import net.minecraft.server.v1_16_R2.WorldDimension;
import net.minecraft.server.v1_16_R2.WorldServer;

public class WorldCracker162
{
	public static void makeStuffAt(World world, int x, int z)
	{
		WorldServer ws = ((CraftWorld) world).getHandle();
		MinecraftServer server = ws.getMinecraftServer();
		WorldDataServer wds = ws.worldDataServer;
		StructureManager sm = ws.getStructureManager();
		RegistryMaterials<WorldDimension> registrymaterials = wds.getGeneratorSettings().d();
		WorldDimension wdm = (WorldDimension) registrymaterials.a(WorldDimension.OVERWORLD);
		DimensionManager dm = wdm.b();
		ChunkGenerator cg = wdm.c();
		IChunkAccess ica = ws.getChunkAt(x, z);		
	}

	public static void attemptGenVillage(World world, int x, int z)
	{
		WorldServer ws = ((CraftWorld) world).getHandle();
		WorldDataServer wds = ws.worldDataServer;
		StructureManager sm = ws.getStructureManager();
		RegistryMaterials<WorldDimension> registrymaterials = wds.getGeneratorSettings().d();
		WorldDimension wdm = (WorldDimension) registrymaterials.a(WorldDimension.OVERWORLD);
		DimensionManager dm = wdm.b();
		ChunkGenerator cg = wdm.c();
		StructureSettings structureSettings = cg.getSettings();
	}
}
