package com.volmit.iris.scaffold.stream.arithmetic;

import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;
import com.volmit.iris.util.Function2;
import com.volmit.iris.util.Function3;

public class AddingStream<T> extends BasicStream<T>
{
	private final Function3<Double, Double, Double, Double> add;

	public AddingStream(ProceduralStream<T> stream, Function3<Double, Double, Double, Double> add)
	{
		super(stream);
		this.add = add;
	}

	public AddingStream(ProceduralStream<T> stream, Function2<Double, Double, Double> add)
	{
		this(stream, (x, y, z) -> add.apply(x, z));
	}

	public AddingStream(ProceduralStream<T> stream, double add)
	{
		this(stream, (x, y, z) -> add);
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
		return fromDouble(add.apply(x, 0D, z) + getTypedSource().getDouble(x, z));
	}

	@Override
	public T get(double x, double y, double z)
	{
		return fromDouble(add.apply(x, y, z) + getTypedSource().getDouble(x, y, z));
	}

}
