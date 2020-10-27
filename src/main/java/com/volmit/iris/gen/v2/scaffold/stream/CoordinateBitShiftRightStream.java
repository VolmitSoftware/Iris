package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;

public class CoordinateBitShiftRightStream<T> extends BasicLayer implements ProceduralStream<T>
{
	private final ProceduralStream<T> stream;
	private final int amount;

	public CoordinateBitShiftRightStream(ProceduralStream<T> stream, int amount)
	{
		super();
		this.stream = stream;
		this.amount = amount;
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
		return stream.get((int) x >> amount, (int) z >> amount);
	}

	@Override
	public T get(double x, double y, double z)
	{
		return stream.get((int) x >> amount, (int) y >> amount, (int) z >> amount);
	}

}
