package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;

public class ForceDoubleStream<T> extends BasicLayer implements ProceduralStream<T>
{
	private final ProceduralStream<T> stream;

	public ForceDoubleStream(ProceduralStream<T> stream)
	{
		super();
		this.stream = stream;
	}

	@Override
	public double toDouble(T t)
	{
		return (double) t;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T fromDouble(double d)
	{
		return (T) Double.valueOf(d);
	}

	@Override
	public T get(double x, double z)
	{
		return stream.get(x, z);
	}

	@Override
	public T get(double x, double y, double z)
	{
		return stream.get(x, y, z);
	}

}
