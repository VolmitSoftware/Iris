package com.volmit.iris.util;

public class ChunkPosition
{
	private int x;
	private int z;

	public ChunkPosition(int x, int z)
	{
		this.x = x;
		this.z = z;
	}

	public int getX()
	{
		return x;
	}

	public void setX(int x)
	{
		this.x = x;
	}

	public int getZ()
	{
		return z;
	}

	public void setZ(int z)
	{
		this.z = z;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + x;
		result = prime * result + z;
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
		{
			return true;
		}
		if(!(obj instanceof ChunkPosition))
		{
			return false;
		}
		ChunkPosition other = (ChunkPosition) obj;
		return x == other.x && z == other.z;
	}
}
