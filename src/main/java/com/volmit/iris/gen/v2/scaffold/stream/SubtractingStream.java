package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;
import com.volmit.iris.util.Function2;
import com.volmit.iris.util.Function3;

public class SubtractingStream<T> extends BasicLayer implements ProceduralStream<T>
{
	private final ProceduralStream<T> stream;
	private final Function3<Double, Double, Double, Double> add;

	public SubtractingStream(ProceduralStream<T> stream, Function3<Double, Double, Double, Double> add)
	{
		super();
		this.stream = stream;
		this.add = add;
	}

	public SubtractingStream(ProceduralStream<T> stream, Function2<Double, Double, Double> add)
	{
		this(stream, (x, y, z) -> add.apply(x, z));
	}

	public SubtractingStream(ProceduralStream<T> stream, double add)
	{
		this(stream, (x, y, z) -> add);
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
		return fromDouble(stream.getDouble(x, z) - add.apply(x, 0D, z));
	}

	@Override
	public T get(double x, double y, double z)
	{
		return fromDouble(stream.getDouble(x, y, z) - add.apply(x, y, z));
	}
}
