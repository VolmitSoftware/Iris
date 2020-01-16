package ninja.bytecode.iris.util;

import org.bukkit.generator.ChunkGenerator;

import ninja.bytecode.shuriken.collections.GList;

public class MulticoreChunkGenerator extends ChunkGenerator
{
	private GList<ParallelChunkGenerator> generators;
	
	public MulticoreChunkGenerator(int tc)
	{
		
	}
}
