package com.volmit.iris.util;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;

@SuppressWarnings("deprecation")
public class PreemptiveChunk implements BiomeGrid, ChunkData
{
	private ChunkData c;
	private BiomeStorage b;

	public PreemptiveChunk(ChunkData c)
	{
		this.c = c;
		this.b = new BiomeStorage();
	}

	public void inject(ChunkData ic, BiomeGrid ib)
	{
		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 256; j++)
			{
				for(int k = 0; k < 16; k++)
				{
					if(!getType(i, j, k).equals(Material.AIR))
					{
						ic.setBlock(i, j, k, getBlockData(i, j, k));
					}
				}
			}
		}

		b.inject(ib);
	}

	@Override
	public Biome getBiome(int arg0, int arg1)
	{
		throw new UnsupportedOperationException("Not Supported. Use x y z");
	}

	@Override
	public Biome getBiome(int x, int y, int z)
	{
		return b.getBiome(x, y, z);
	}

	@Override
	public void setBiome(int x, int z, Biome arg2)
	{
		for(int i = 0; i < 256; i++)
		{
			b.setBiome(x, i, z, arg2);
		}
	}

	@Override
	public void setBiome(int arg0, int arg1, int arg2, Biome arg3)
	{
		b.setBiome(arg0, arg1, arg2, arg3);
	}

	@Override
	public BlockData getBlockData(int arg0, int arg1, int arg2)
	{
		return c.getBlockData(arg0, arg1, arg2);
	}

	@Deprecated
	@Override
	public byte getData(int arg0, int arg1, int arg2)
	{
		return c.getData(arg0, arg1, arg2);
	}

	@Override
	public int getMaxHeight()
	{
		return c.getMaxHeight();
	}

	@Override
	public Material getType(int arg0, int arg1, int arg2)
	{
		return c.getType(arg0, arg1, arg2);
	}

	@Deprecated
	@Override
	public MaterialData getTypeAndData(int arg0, int arg1, int arg2)
	{
		return c.getTypeAndData(arg0, arg1, arg2);
	}

	@Override
	public void setBlock(int arg0, int arg1, int arg2, Material arg3)
	{
		c.setBlock(arg0, arg1, arg2, arg3);
	}

	@Deprecated
	@Override
	public void setBlock(int arg0, int arg1, int arg2, MaterialData arg3)
	{
		c.setBlock(arg0, arg1, arg2, arg3);
	}

	@Override
	public void setBlock(int arg0, int arg1, int arg2, BlockData arg3)
	{
		c.setBlock(arg0, arg1, arg2, arg3);
	}

	@Override
	public void setRegion(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, Material arg6)
	{
		c.setRegion(arg0, arg1, arg2, arg3, arg4, arg5, arg6);
	}

	@Deprecated
	@Override
	public void setRegion(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, MaterialData arg6)
	{
		c.setRegion(arg0, arg1, arg2, arg3, arg4, arg5, arg6);
	}

	@Override
	public void setRegion(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, BlockData arg6)
	{
		c.setRegion(arg0, arg1, arg2, arg3, arg4, arg5, arg6);
	}
}
