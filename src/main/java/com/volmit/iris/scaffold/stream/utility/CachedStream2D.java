package com.volmit.iris.scaffold.stream.utility;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.cache.Cache;
import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;

public class CachedStream2D<T> extends BasicStream<T> implements ProceduralStream<T>
{
	private final ProceduralStream<T> stream;
	private final ConcurrentLinkedHashMap<Long, T> cache;

	public CachedStream2D(ProceduralStream<T> stream, int size)
	{
		super();
		this.stream = stream;
		cache = new ConcurrentLinkedHashMap.Builder<Long, T>()
				.initialCapacity(size)
				.maximumWeightedCapacity(size)
				.concurrencyLevel(32)
				.build();
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
		return cache.compute(Cache.key((int) x, (int) z), (k, v) -> v != null ? v : stream.get((int)x, (int)z));
	}

	@Override
	public T get(double x, double y, double z)
	{
		return stream.get(x, y, z);
	}
}
