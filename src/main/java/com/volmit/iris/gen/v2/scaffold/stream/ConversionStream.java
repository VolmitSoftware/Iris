package com.volmit.iris.gen.v2.scaffold.stream;

import java.util.function.Function;

import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;

public class ConversionStream<T, V> extends BasicLayer implements ProceduralStream<V>
{
	private final ProceduralStream<T> stream;
	private final Function<T, V> converter;

	public ConversionStream(ProceduralStream<T> stream, Function<T, V> converter)
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
		return converter.apply(stream.get(x, z));
	}

	@Override
	public V get(double x, double y, double z)
	{
		return converter.apply(stream.get(x, y, z));
	}
}
