package ninja.bytecode.iris.util;

import java.util.Random;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;

import ninja.bytecode.iris.generator.atomics.AtomicChunkData;

@FunctionalInterface
public interface ChunkSpliceListener
{
	public AtomicChunkData onSpliceAvailable(World world, Random random, int x, int z, BiomeGrid biome);
}
