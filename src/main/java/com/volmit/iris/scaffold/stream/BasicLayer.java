package com.volmit.iris.scaffold.stream;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BasicLayer implements ProceduralLayer
{
	private final long seed;
	private final double zoom;
	private final double offsetX;
	private final double offsetY;
	private final double offsetZ;

	public BasicLayer(long seed, double zoom)
	{
		this(seed, zoom, 0D, 0D, 0D);
	}

	public BasicLayer(long seed)
	{
		this(seed, 1D);
	}

	public BasicLayer()
	{
		this(1337);
	}
}
