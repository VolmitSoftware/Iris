package com.volmit.iris.scaffold.stream.arithmetic;

import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;

public class ClampedStream<T> extends BasicStream<T> implements ProceduralStream<T>
{
	private final double min;
	private final double max;

	public ClampedStream(ProceduralStream<T> stream, double min, double max)
	{
		super(stream);
		this.min = min;
		this.max = max;
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

	private double clamp(double v)
	{
		return Math.max(Math.min(v, max), min);
	}

	@Override
	public T get(double x, double z)
	{
		return fromDouble(clamp(getTypedSource().getDouble(x, z)));
	}

	@Override
	public T get(double x, double y, double z)
	{
		return fromDouble(clamp(getTypedSource().getDouble(x, y, z)));
	}

}
