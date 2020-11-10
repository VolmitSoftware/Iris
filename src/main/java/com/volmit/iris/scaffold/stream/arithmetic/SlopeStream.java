package com.volmit.iris.scaffold.stream.arithmetic;

import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;

public class SlopeStream<T> extends BasicStream<T>
{
	private final int range;

	public SlopeStream(ProceduralStream<T> stream, int range)
	{
		super(stream);
		this.range = range;
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
		double height = getTypedSource().getDouble(x, z);
		double dx = getTypedSource().getDouble(x + range, z) - height;
		double dy = getTypedSource().getDouble(x, z + range) - height;

		return fromDouble(Math.sqrt(dx * dx + dy * dy));
	}

	@Override
	public T get(double x, double y, double z)
	{
		double height = getTypedSource().getDouble(x, y, z);
		double dx = getTypedSource().getDouble(x + range,y, z) - height;
		double dy = getTypedSource().getDouble(x,y+range, z) - height;
		double dz = getTypedSource().getDouble(x,y, z + range) - height;

		return fromDouble(Math.cbrt((dx * dx) + (dy * dy) + (dz * dz)));
	}

}
