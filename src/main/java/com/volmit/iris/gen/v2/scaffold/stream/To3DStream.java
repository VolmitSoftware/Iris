package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;

public class To3DStream<T> extends BasicLayer implements ProceduralStream<T>
{
	private final ProceduralStream<T> stream;

	public To3DStream(ProceduralStream<T> stream)
	{
		super();
		this.stream = stream;
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
		return stream.get(x, z);
	}

	@Override
	public T get(double x, double y, double z)
	{
		return stream.fromDouble(stream.getDouble(x, z) >= y ? 1D : 0D);
	}

}
