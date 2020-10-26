package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;
import com.volmit.iris.util.Function2;
import com.volmit.iris.util.Function3;

public class MinningStream<T> extends BasicLayer implements ProceduralStream<T>
{
	private final ProceduralStream<T> stream;
	private final Function3<Double, Double, Double, Double> add;

	public MinningStream(ProceduralStream<T> stream, Function3<Double, Double, Double, Double> add)
	{
		super();
		this.stream = stream;
		this.add = add;
	}

	public MinningStream(ProceduralStream<T> stream, Function2<Double, Double, Double> add)
	{
		this(stream, (x, y, z) -> add.apply(x, z));
	}

	public MinningStream(ProceduralStream<T> stream, double add)
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
		return fromDouble(Math.min(add.apply(x, 0D, z), stream.getDouble(x, z)));
	}

	@Override
	public T get(double x, double y, double z)
	{
		return fromDouble(Math.min(add.apply(x, y, z), stream.getDouble(x, y, z)));
	}

}
