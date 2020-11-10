package com.volmit.iris.scaffold.stream.convert;

import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;

public class To3DStream<T> extends BasicStream<T>
{
	public To3DStream(ProceduralStream<T> stream)
	{
		super(stream);
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
		return getTypedSource().get(x, z);
	}

	@Override
	public T get(double x, double y, double z)
	{
		return getTypedSource().fromDouble(getTypedSource().getDouble(x, z) >= y ? 1D : 0D);
	}
}
