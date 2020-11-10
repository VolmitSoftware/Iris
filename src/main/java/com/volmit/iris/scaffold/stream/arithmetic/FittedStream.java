package com.volmit.iris.scaffold.stream.arithmetic;

import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;

public class FittedStream<T> extends BasicStream<T> implements ProceduralStream<T>
{
	private final double min;
	private final double max;
	private final double inMin;
	private final double inMax;

	public FittedStream(ProceduralStream<T> stream, double inMin, double inMax, double min, double max)
	{
		super(stream);
		this.inMin = inMin;
		this.inMax = inMax;
		this.min = min;
		this.max = max;
	}

	public FittedStream(ProceduralStream<T> stream, double min, double max)
	{
		this(stream, 0, 1, min, max);
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

	private double dlerp(double v)
	{
		return min + ((max - min) * ((v - inMin) / (inMax - inMin)));
	}

	@Override
	public T get(double x, double z)
	{
		return fromDouble(dlerp(getTypedSource().getDouble(x, z)));
	}

	@Override
	public T get(double x, double y, double z)
	{
		return fromDouble(dlerp(getTypedSource().getDouble(x, y, z)));
	}

}
