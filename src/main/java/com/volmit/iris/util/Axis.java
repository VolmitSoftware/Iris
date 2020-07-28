package com.volmit.iris.util;

import org.bukkit.util.Vector;

public enum Axis
{
	X(1, 0, 0),
	Y(0, 1, 0),
	Z(0, 0, 1);
	
	private int x;
	private int y;
	private int z;
	
	private Axis(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public Vector positive()
	{
		return new Vector(x, y, z);
	}
	
	public Vector negative()
	{
		return VectorMath.reverse(positive());
	}
}
