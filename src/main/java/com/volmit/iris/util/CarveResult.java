package com.volmit.iris.util;

import lombok.Value;

@Value
public class CarveResult
{
	private final int surface;
	private final int ceiling;
	
	public int getHeight()
	{
		return ceiling - surface;
	}
}
