package com.volmit.iris.generator.atomics;

import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.util.KList;

public class TerrainNode
{
	private static final KList<BlockData> blockDataPalette = new KList<BlockData>();

	private final byte biome;
	private final short block;

	private TerrainNode(byte biome, short block)
	{
		this.biome = biome;
		this.block = block;
	}

	public TerrainNode(Biome biome, BlockData block)
	{
		this((byte) (biome.ordinal()), (short) (paletteOf(block)));
	}

	public TerrainNode setBlockData(BlockData block)
	{
		return new TerrainNode(biome, (short) (paletteOf(block)));
	}

	public TerrainNode setBiome(Biome biome)
	{
		return new TerrainNode((byte) (biome.ordinal()), block);
	}

	public BlockData getBlockData()
	{
		return blockDataPalette.get(block);
	}

	public Biome getBiome()
	{
		return Biome.values()[biome];
	}

	private static int paletteOf(BlockData b)
	{
		synchronized(blockDataPalette)
		{
			int v = blockDataPalette.indexOf(b);

			if(v >= 0)
			{
				return v;
			}

			blockDataPalette.add(b);
			return blockDataPalette.size() - 1;
		}
	}
}
