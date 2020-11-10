package com.volmit.iris.scaffold.stream.arithmetic;

import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;

public class RadialStream<T> extends BasicStream<T> implements ProceduralStream<T>
{
	private final double scale;

	public RadialStream(ProceduralStream<T> stream)
	{
		this(stream, 1D);
	}

	public RadialStream(ProceduralStream<T> stream, double scale)
	{
		super(stream);
		this.scale = scale;
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

	private double radii(double v)
	{
		return (v / (360D * scale)) % 360D;
	}

	@Override
	public T get(double x, double z)
	{
		return fromDouble(radii(getTypedSource().getDouble(x, z)));
	}

	@Override
	public T get(double x, double y, double z)
	{
		return fromDouble(radii(getTypedSource().getDouble(x, y, z)));
	}

}
