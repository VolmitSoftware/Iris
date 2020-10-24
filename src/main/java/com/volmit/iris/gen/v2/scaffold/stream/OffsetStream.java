package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;

public class OffsetStream<T> extends BasicLayer implements ProceduralStream<T>
{
	private final ProceduralStream<T> stream;
	private final double ox;
	private final double oy;
	private final double oz;

	public OffsetStream(ProceduralStream<T> stream, double x, double y, double z)
	{
		super();
		this.stream = stream;
		this.ox = x;
		this.oy = y;
		this.oz = z;
	}

	@Override
	public double toDouble(T t)
	{
		return stream.toDouble(t);
	}

	@Override
	public T fromDouble(double d)
	{
		return stream.fromDouble(d);
	}

	@Override
	public T get(double x, double z)
	{
		return stream.get(x + ox, z + oz);
	}

	@Override
	public T get(double x, double y, double z)
	{
		return stream.get(x + ox, y + oy, z + oz);
	}

}
