package com.volmit.iris.scaffold.stream.utility;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.volmit.iris.scaffold.cache.Cache;
import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;

public class CachedStream2D<T> extends BasicStream<T> implements ProceduralStream<T>
{
	private final ProceduralStream<T> stream;
	private final LoadingCache<Long, T> cache;

	public CachedStream2D(ProceduralStream<T> stream, int size)
	{
		super();
		this.stream = stream;
		cache = Caffeine.newBuilder()
				.maximumSize(size)
				.build((b) -> stream.get(Cache.keyX(b), Cache.keyZ(b)));
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
		return cache.get(Cache.key((int) x, (int) z));
	}

	@Override
	public T get(double x, double y, double z)
	{
		return stream.get(x, y, z);
	}
}
