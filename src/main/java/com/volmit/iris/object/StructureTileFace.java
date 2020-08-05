package com.volmit.iris.object;

public enum StructureTileFace
{
	UP,
	DOWN,
	NORTH,
	SOUTH,
	EAST,
	WEST;

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
