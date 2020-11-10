package com.volmit.iris.generator.legacy.scaffold;

import java.util.List;
import java.util.Random;
import java.util.function.Function;

import org.bukkit.generator.BlockPopulator;
import org.bukkit.util.BlockVector;

public interface TerrainProvider
{
	public TerrainTarget getTarget();
	
	public Provisioned getProvisioner();
	
	public void setProvisioner(Provisioned p);

	public BlockVector computeSpawn(Function<BlockVector, Boolean> allowed);

	public GeneratedChunk generate(Random random, int x, int z, TerrainChunk chunk);

	public boolean canSpawn(int x, int z);

	public List<BlockPopulator> getPopulators();

	public boolean isParallelCapable();

	public boolean shouldGenerateMobs();

	public boolean shouldGenerateCaves();

	public boolean shouldGenerateDecorations();

	public boolean shouldGenerateVanillaStructures();
}
