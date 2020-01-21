package ninja.bytecode.iris.util;

import org.bukkit.block.Biome;

import ninja.bytecode.iris.generator.atomics.AtomicChunkData;

public class PrepackagedChunk
{
	private AtomicChunkData data;
	private Biome[] biome;

	public PrepackagedChunk(AtomicChunkData data, Biome[] biome)
	{
		this.data = data;
		this.biome = biome;
	}

	public AtomicChunkData getData()
	{
		return data;
	}

	public void setData(AtomicChunkData data)
	{
		this.data = data;
	}

	public Biome[] getBiome()
	{
		return biome;
	}

	public void setBiome(Biome[] biome)
	{
		this.biome = biome;
	}
}
