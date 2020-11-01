package com.volmit.iris.v2.scaffold.stream.utility;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.volmit.iris.v2.scaffold.cache.Cache;
import com.volmit.iris.v2.scaffold.stream.BasicStream;
import com.volmit.iris.v2.scaffold.stream.ProceduralStream;

public class NullSafeStream<T> extends BasicStream<T> implements ProceduralStream<T>
{
	private final ProceduralStream<T> stream;
	private final T ifNull;

	public NullSafeStream(ProceduralStream<T> stream, T ifNull)
	{
		super();
		this.stream = stream;
		this.ifNull = ifNull;
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
		T t = stream.get(x, z);

		if(t == null)
		{
			return ifNull;
		}

		return t;
	}

	@Override
	public T get(double x, double y, double z)
	{
		T t = stream.get(x, y, z);

		if(t == null)
		{
			return ifNull;
		}

		return t;
	}
}
