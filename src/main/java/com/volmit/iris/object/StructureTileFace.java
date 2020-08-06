package com.volmit.iris.object;

public enum StructureTileFace
{
	UP,
	DOWN,
	NORTH,
	SOUTH,
	EAST,
	WEST;

	public int x()
	{
		return this.equals(EAST) ? 1 : this.equals(WEST) ? -1 : 0;
	}

	public int y()
	{
		return this.equals(UP) ? 1 : this.equals(DOWN) ? -1 : 0;
	}

	public int z()
	{
		return this.equals(SOUTH) ? 1 : this.equals(NORTH) ? -1 : 0;
	}

	public StructureTileFace rotate90CW()
	{
		switch(this)
		{
			case EAST:
				return SOUTH;
			case NORTH:
				return EAST;
			case SOUTH:
				return WEST;
			case WEST:
				return NORTH;
			default:
				return this;
		}
	}
}
