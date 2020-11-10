package com.volmit.iris.scaffold.stream.arithmetic;

import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;

public class OffsetStream<T> extends BasicStream<T> implements ProceduralStream<T>
{
	private final double ox;
	private final double oy;
	private final double oz;

	public OffsetStream(ProceduralStream<T> stream, double x, double y, double z)
	{
		super(stream);
		this.ox = x;
		this.oy = y;
		this.oz = z;
	}

	@Override
	public double toDouble(T t)
	{
		return getTypedSource().toDouble(t);
	}

	@Override
	public T fromDouble(double d)
	{
		return getTypedSource().fromDouble(d);
	}

	@Override
	public T get(double x, double z)
	{
		return getTypedSource().get(x + ox, z + oz);
	}

	@Override
	public T get(double x, double y, double z)
	{
		return getTypedSource().get(x + ox, y + oy, z + oz);
	}

}
