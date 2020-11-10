package com.volmit.iris.scaffold.stream.arithmetic;

import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;
import com.volmit.iris.util.Function2;
import com.volmit.iris.util.Function3;

public class ModuloStream<T> extends BasicStream<T>
{
	private final Function3<Double, Double, Double, Double> add;

	public ModuloStream(ProceduralStream<T> stream, Function3<Double, Double, Double, Double> add)
	{
		super(stream);
		this.add = add;
	}

	public ModuloStream(ProceduralStream<T> stream, Function2<Double, Double, Double> add)
	{
		this(stream, (x, y, z) -> add.apply(x, z));
	}

	public ModuloStream(ProceduralStream<T> stream, double add)
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
		return fromDouble(getTypedSource().getDouble(x, z) % add.apply(x, 0D, z));
	}

	@Override
	public T get(double x, double y, double z)
	{
		return fromDouble(getTypedSource().getDouble(x, y, z) % add.apply(x, y, z));
	}
}
