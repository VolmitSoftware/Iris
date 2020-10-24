package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;

public class FittedStream<T> extends BasicLayer implements ProceduralStream<T>
{
	private final ProceduralStream<T> stream;
	private final double min;
	private final double max;
	private final double inMin;
	private final double inMax;

	public FittedStream(ProceduralStream<T> stream, double inMin, double inMax, double min, double max)
	{
		super();
		this.stream = stream;
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
		return stream.toDouble(t);
	}

	@Override
	public T fromDouble(double d)
	{
		return stream.fromDouble(d);
	}

	private double dlerp(double v)
	{
		return min + ((max - min) * ((v - inMin) / (inMax - inMin)));
	}

	@Override
	public T get(double x, double z)
	{
		return fromDouble(dlerp(stream.getDouble(x, z)));
	}

	@Override
	public T get(double x, double y, double z)
	{
		return fromDouble(dlerp(stream.getDouble(x, y, z)));
	}

}
