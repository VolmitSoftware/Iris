package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;

public class ClampedStream<T> extends BasicLayer implements ProceduralStream<T>
{
	private final ProceduralStream<T> stream;
	private final double min;
	private final double max;

	public ClampedStream(ProceduralStream<T> stream, double min, double max)
	{
		super();
		this.stream = stream;
		this.min = min;
		this.max = max;
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

	private double clamp(double v)
	{
		return Math.max(Math.min(v, max), min);
	}

	@Override
	public T get(double x, double z)
	{
		return fromDouble(clamp(stream.getDouble(x, z)));
	}

	@Override
	public T get(double x, double y, double z)
	{
		return fromDouble(clamp(stream.getDouble(x, y, z)));
	}

}
