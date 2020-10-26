package com.volmit.iris.gen.v2.scaffold;

import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;

public class BiomeGridHunkView implements Hunk<Biome>
{
	private final BiomeGrid chunk;

	public BiomeGridHunkView(BiomeGrid chunk)
	{
		this.chunk = chunk;
	}

	@Override
	public int getWidth()
	{
		return 16;
	}

	@Override
	public int getDepth()
	{
		return 16;
	}

	@Override
	public int getHeight()
	{
		return 256;
	}

	@Override
	public Hunk<Biome> croppedView(int x1, int y1, int z1, int x2, int y2, int z2)
	{
		return new HunkView<Biome>(this, x2 - x1, y2 - y1, z2 - z1, x1, y1, z1);
	}

	@Override
	public ArrayHunk<Biome> crop(int x1, int y1, int z1, int x2, int y2, int z2)
	{
		ArrayHunk<Biome> h = new ArrayHunk<Biome>(x2 - x1, y2 - y1, z2 - z1);

		for(int i = x1; i < x2; i++)
		{
			for(int j = y1; j < y2; j++)
			{
				for(int k = z1; k < z2; k++)
				{
					h.set(i - x1, j - y1, k - z1, get(i, j, k));
				}
			}
		}

		return h;
	}

	@Override
	public void insert(int offX, int offY, int offZ, Hunk<Biome> hunk, boolean invertY)
	{
		if(offX + (hunk.getWidth() - 1) >= getWidth() || offY + (hunk.getHeight() - 1) >= getHeight() || offZ + (hunk.getDepth() - 1) >= getDepth() || offX < 0 || offY < 0 || offZ < 0)
		{
			throw new RuntimeException("Cannot insert hunk " + hunk.getWidth() + "," + hunk.getHeight() + "," + hunk.getDepth() + " into Hunk " + getWidth() + "," + getHeight() + "," + getDepth() + " with offset " + offZ + "," + offY + "," + offZ);
		}

		for(int i = offX; i < offX + hunk.getWidth(); i++)
		{
			for(int j = offY; j < offY + hunk.getHeight(); j++)
			{
				for(int k = offZ; k < offZ + hunk.getDepth(); k++)
				{
					set(i, j, k, hunk.get(i - offX, j - offY, k - offZ));
				}
			}
		}
	}

	@Override
	public void set(int x, int y, int z, Biome t)
	{
		if(x >= getWidth() || y >= getHeight() || z >= getDepth())
		{
			throw new RuntimeException(x + " " + y + " " + z + " is out of the bounds 0,0,0 - " + (getWidth() - 1) + "," + (getHeight() - 1) + "," + (getDepth() - 1));
		}

		chunk.setBiome(x, y, z, t);
	}

	@Override
	public Biome get(int x, int y, int z)
	{
		if(x >= getWidth() || y >= getHeight() || z >= getDepth())
		{
			throw new RuntimeException(x + " " + y + " " + z + " is out of the bounds 0,0,0 - " + (getWidth() - 1) + "," + (getHeight() - 1) + "," + (getDepth() - 1));
		}

		return chunk.getBiome(x, y, z);
	}

	@Override
	public Biome getClosest(int x, int y, int z)
	{
		return chunk.getBiome(x >= getWidth() ? getWidth() + 1 : x, y >= getHeight() ? getHeight() - 1 : y, z >= getDepth() ? getDepth() - 1 : z);
	}

	@Override
	public void fill(Biome t)
	{
		set(0, 0, 0, getWidth(), getHeight(), getDepth(), t);
	}

	@Override
	public Hunk<Biome> getFace(HunkFace f)
	{
		switch(f)
		{
			case BOTTOM:
				return croppedView(0, 0, 0, getWidth() - 1, 0, getDepth() - 1);
			case EAST:
				return croppedView(getWidth() - 1, 0, 0, getWidth() - 1, getHeight() - 1, getDepth() - 1);
			case NORTH:
				return croppedView(0, 0, 0, getWidth() - 1, getHeight() - 1, 0);
			case SOUTH:
				return croppedView(0, 0, 0, 0, getHeight() - 1, getDepth() - 1);
			case TOP:
				return croppedView(0, getHeight() - 1, 0, getWidth() - 1, getHeight() - 1, getDepth() - 1);
			case WEST:
				return croppedView(0, 0, getDepth() - 1, getWidth() - 1, getHeight() - 1, getDepth() - 1);
			default:
				break;
		}

		return null;
	}

	@Override
	public Hunk<Biome> getSource()
	{
		return null;
	}

	@Override
	public void set(int x1, int y1, int z1, int x2, int y2, int z2, Biome t)
	{
		for(int i = x1; i <= x2; i++)
		{
			for(int j = y1; j <= y2; j++)
			{
				for(int k = z1; k <= z2; k++)
				{
					set(i, j, k, t);
				}
			}
		}
	}
}
