package com.volmit.iris.gen.v2.scaffold.stream;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;
import com.volmit.iris.util.ChunkPosition;

public class CachedStream2D<T> extends BasicLayer implements ProceduralStream<T>
{
	private final ProceduralStream<T> stream;
	private final LoadingCache<ChunkPosition, T> cache;

	public CachedStream2D(ProceduralStream<T> stream, int size)
	{
		super();
		this.stream = stream;
		cache = Caffeine.newBuilder().maximumSize(size).build((b) -> stream.get(b.getX(), b.getZ()));
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
		return cache.get(new ChunkPosition((int) x, (int) z));
	}

	@Override
	public T get(double x, double y, double z)
	{
		return stream.get(x, y, z);
	}
}
