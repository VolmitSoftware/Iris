package com.volmit.iris.gen.bindings;

import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

public abstract class BukkitTerrainProvider extends ChunkGenerator implements TerrainProvider
{
	private final TerrainTarget target;

	public BukkitTerrainProvider(TerrainTarget target)
	{
		this.target = target;
	}

	@Override
	public abstract void generate(Random random, int x, int z, TerrainChunk chunk);

	@Override
	public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome)
	{
		TerrainChunk terrain = TerrainChunk.create(world);
		generate(random, x, z, terrain);
		return terrain;
	}

	@Override
	public abstract boolean canSpawn(World world, int x, int z);

	@Override
	public boolean canSpawnDefault(World world, int x, int z)
	{
		return super.canSpawn(world, x, z);
	}

	@Override
	public abstract List<BlockPopulator> getDefaultPopulators(World world);

	@Override
	public abstract Location getFixedSpawnLocation(World world, Random random);

	@Override
	public abstract boolean isParallelCapable();

	@Override
	public abstract boolean shouldGenerateCaves();

	@Override
	public abstract boolean shouldGenerateDecorations();

	@Override
	public abstract boolean shouldGenerateMobs();

	@Override
	public abstract boolean shouldGenerateStructures();

	@Override
	public TerrainTarget getTarget()
	{
		return target;
	}
}
