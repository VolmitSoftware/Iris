package com.volmit.iris.gen.scaffold;

import java.util.List;
import java.util.Random;

import org.bukkit.generator.BlockPopulator;

public interface TerrainProvider
{
	public TerrainTarget getTarget();

	public void generate(Random random, int x, int z, TerrainChunk chunk);

	public boolean canSpawn(int x, int z);

	public List<BlockPopulator> getPopulators();

	public boolean isParallelCapable();

	public boolean shouldGenerateMobs();
	
	public boolean shouldGenerateCaves();
	
	public boolean shouldGenerateDecorations();

	public boolean shouldGenerateVanillaStructures();
}
