package com.volmit.iris.util;

import lombok.Data;

@Data
public class CaveResult
{
	private int floor;
	private int ceiling;

	public CaveResult(int floor, int ceiling)
	{
		this.floor = floor;
		this.ceiling = ceiling;
	}

	public boolean isWithin(int v)
	{
		return v > floor || v < ceiling;
	}
}
