package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;
import com.volmit.iris.util.Function3;

public class AwareConversionStream2D<T, V> extends BasicLayer implements ProceduralStream<V>
{
	private final ProceduralStream<T> stream;
	private final Function3<T, Double, Double, V> converter;

	public AwareConversionStream2D(ProceduralStream<T> stream, Function3<T, Double, Double, V> converter)
	{
		super();
		this.stream = stream;
		this.converter = converter;
	}

	@Override
	public double toDouble(V t)
	{
		return 0;
	}

	@Override
	public V fromDouble(double d)
	{
		return null;
	}

	@Override
	public V get(double x, double z)
	{
		return converter.apply(stream.get(x, z), x, z);
	}

	@Override
	public V get(double x, double y, double z)
	{
		return converter.apply(stream.get(x, y, z), x, z);
	}
}
