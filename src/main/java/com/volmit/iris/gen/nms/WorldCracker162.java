package com.volmit.iris.gen.nms;

import org.bukkit.World;
import org.bukkit.craftbukkit.v1_16_R2.CraftWorld;

import net.minecraft.server.v1_16_R2.ChunkGenerator;
import net.minecraft.server.v1_16_R2.DimensionManager;
import net.minecraft.server.v1_16_R2.IChunkAccess;
import net.minecraft.server.v1_16_R2.MinecraftServer;
import net.minecraft.server.v1_16_R2.RegistryMaterials;
import net.minecraft.server.v1_16_R2.StructureManager;
import net.minecraft.server.v1_16_R2.StructureSettings;
import net.minecraft.server.v1_16_R2.WorldChunkManager;
import net.minecraft.server.v1_16_R2.WorldDataServer;
import net.minecraft.server.v1_16_R2.WorldDimension;
import net.minecraft.server.v1_16_R2.WorldServer;

public class WorldCracker162
{
	@SuppressWarnings("unused")
	public static void printSignature(World world, int x, int z) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException
	{
		WorldServer ws = ((CraftWorld) world).getHandle();
		MinecraftServer server = ws.getMinecraftServer();
		WorldDataServer wds = ws.worldDataServer;
		StructureManager sm = ws.getStructureManager();
		RegistryMaterials<WorldDimension> registrymaterials = wds.getGeneratorSettings().d();
		WorldDimension wdm = (WorldDimension) registrymaterials.a(WorldDimension.OVERWORLD);
		DimensionManager dm = wdm.b();
		ChunkGenerator cg = wdm.c();
		StructureSettings ss = cg.getSettings();
		IChunkAccess ica = ws.getChunkAt(x, z);
		WorldChunkManager wcm = cg.getWorldChunkManager();
	}
}
