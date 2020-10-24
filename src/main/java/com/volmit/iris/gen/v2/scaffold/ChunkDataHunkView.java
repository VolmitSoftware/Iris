package com.volmit.iris.gen.v2.scaffold;

import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

public class ChunkDataHunkView implements Hunk<BlockData>
{
	private final ChunkData chunk;

	public ChunkDataHunkView(ChunkData chunk)
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
		return chunk.getMaxHeight();
	}

	@Override
	public Hunk<BlockData> croppedView(int x1, int y1, int z1, int x2, int y2, int z2)
	{
		return new HunkView<BlockData>(this, x2 - x1, y2 - y1, z2 - z1, x1, y1, z1);
	}

	@Override
	public ArrayHunk<BlockData> crop(int x1, int y1, int z1, int x2, int y2, int z2)
	{
		ArrayHunk<BlockData> h = new ArrayHunk<BlockData>(x2 - x1, y2 - y1, z2 - z1);

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
	public void insert(int offX, int offY, int offZ, Hunk<BlockData> hunk, boolean invertY)
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
	public void set(int x1, int y1, int z1, int x2, int y2, int z2, BlockData t)
	{
		if(x1 >= getWidth() || y1 >= getHeight() || z1 >= getDepth() || x2 >= getWidth() || y2 >= getHeight() || z2 >= getDepth())
		{
			throw new RuntimeException(x1 + "-" + x2 + " " + y1 + "-" + y2 + " " + z1 + "-" + z2 + " is out of the bounds 0,0,0 - " + (getWidth() - 1) + "," + (getHeight() - 1) + "," + (getDepth() - 1));
		}

		chunk.setRegion(x1, y1, z1, x2, y2, z2, t);
	}

	@Override
	public void set(int x, int y, int z, BlockData t)
	{
		if(x >= getWidth() || y >= getHeight() || z >= getDepth())
		{
			throw new RuntimeException(x + " " + y + " " + z + " is out of the bounds 0,0,0 - " + (getWidth() - 1) + "," + (getHeight() - 1) + "," + (getDepth() - 1));
		}

		chunk.setBlock(x, y, z, t);
	}

	@Override
	public BlockData get(int x, int y, int z)
	{
		if(x >= getWidth() || y >= getHeight() || z >= getDepth())
		{
			throw new RuntimeException(x + " " + y + " " + z + " is out of the bounds 0,0,0 - " + (getWidth() - 1) + "," + (getHeight() - 1) + "," + (getDepth() - 1));
		}

		return chunk.getBlockData(x, y, z);
	}

	@Override
	public BlockData getClosest(int x, int y, int z)
	{
		return chunk.getBlockData(x >= getWidth() ? getWidth() + 1 : x, y >= getHeight() ? getHeight() - 1 : y, z >= getDepth() ? getDepth() - 1 : z);
	}

	@Override
	public void fill(BlockData t)
	{
		set(0, 0, 0, getWidth(), getHeight(), getDepth(), t);
	}

	@Override
	public Hunk<BlockData> getFace(HunkFace f)
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
	public Hunk<BlockData> getSource()
	{
		return null;
	}
}
