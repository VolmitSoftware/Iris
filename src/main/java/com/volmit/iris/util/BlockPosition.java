package com.volmit.iris.util;

import lombok.Data;

@Data
public class BlockPosition
{
	private int x;
	private int y;
	private int z;

	public BlockPosition(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public int getChunkX()
	{
		return x >> 4;
	}

	public int getChunkZ()
	{
		return z >> 4;
	}

	public boolean is(int x, int z)
	{
		return this.x == x && this.z == z;
	}

	public boolean is(int x, int y, int z)
	{
		return this.x == x && this.y == y && this.z == z;
	}
}
