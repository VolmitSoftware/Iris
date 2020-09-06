package com.volmit.iris.gen.bindings;

import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;

public interface TerrainProvider
{
	public TerrainTarget getTarget();

	public void generate(Random random, int x, int z, TerrainChunk chunk);

	public boolean canSpawn(World world, int x, int z);

	public boolean canSpawnDefault(World world, int x, int z);

	public List<BlockPopulator> getDefaultPopulators(World world);

	public Location getFixedSpawnLocation(World world, Random random);

	public boolean isParallelCapable();

	public boolean shouldGenerateCaves();

	public boolean shouldGenerateDecorations();

	public boolean shouldGenerateMobs();

	public boolean shouldGenerateStructures();
}
