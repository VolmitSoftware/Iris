package com.volmit.iris.scaffold.stream.arithmetic;

import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;

public class CoordinateBitShiftRightStream<T> extends BasicStream<T> implements ProceduralStream<T>
{
	private final int amount;

	public CoordinateBitShiftRightStream(ProceduralStream<T> stream, int amount)
	{
		super(stream);
		this.amount = amount;
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
		return getTypedSource().get((int) x >> amount, (int) z >> amount);
	}

	@Override
	public T get(double x, double y, double z)
	{
		return getTypedSource().get((int) x >> amount, (int) y >> amount, (int) z >> amount);
	}

}
