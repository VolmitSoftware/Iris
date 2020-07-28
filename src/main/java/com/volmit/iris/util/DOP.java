package com.volmit.iris.util;

import org.bukkit.util.Vector;

public abstract class DOP
{
	private String type;
	
	public DOP(String type)
	{
		this.type = type;
	}
	
	public abstract Vector op(Vector v);
	
	public String getType()
	{
		return type;
	}
}
