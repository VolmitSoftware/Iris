package com.volmit.iris.gen.v2.scaffold.stream;

import java.util.function.Function;

import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;
import com.volmit.iris.util.KMap;

public class CachedConversionStream<T, V> extends BasicLayer implements ProceduralStream<V>
{
	private final ProceduralStream<T> stream;
	private final Function<T, V> converter;
	private final KMap<T, V> cache;

	public CachedConversionStream(ProceduralStream<T> stream, Function<T, V> converter)
	{
		super();
		this.stream = stream;
		this.converter = converter;
		cache = new KMap<>();
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
		return cache.compute(stream.get(x, z), (k, v) -> v != null ? v : converter.apply(k));
	}

	@Override
	public V get(double x, double y, double z)
	{
		return cache.compute(stream.get(x, y, z), (k, v) -> v != null ? v : converter.apply(k));
	}
}
