package ninja.bytecode.iris.object.atomics;

import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import ninja.bytecode.iris.util.HeightMap;

public class AtomicSliverMap
{
	private final AtomicSliver[] slivers;

	public AtomicSliverMap()
	{
		slivers = new AtomicSliver[256];

		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				slivers[i * 16 + j] = new AtomicSliver(i, j);
			}
		}
	}

	public AtomicSliver getSliver(int x, int z)
	{
		return slivers[x * 16 + z];
	}

	public void write(ChunkData data, BiomeGrid grid, HeightMap height)
	{
		for(AtomicSliver i : slivers)
		{
			if(i != null)
			{
				i.write(data);
				i.write(grid);
				i.write(height);
			}
		}
	}
}
