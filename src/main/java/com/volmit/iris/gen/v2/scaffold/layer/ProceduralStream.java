package com.volmit.iris.gen.v2.scaffold.layer;

import java.util.List;
import java.util.function.Function;

import com.volmit.iris.gen.v2.scaffold.Hunk;
import com.volmit.iris.gen.v2.scaffold.stream.CachedConversionStream;
import com.volmit.iris.gen.v2.scaffold.stream.ClampedStream;
import com.volmit.iris.gen.v2.scaffold.stream.ConversionStream;
import com.volmit.iris.gen.v2.scaffold.stream.FittedStream;
import com.volmit.iris.gen.v2.scaffold.stream.Interpolated;
import com.volmit.iris.gen.v2.scaffold.stream.OffsetStream;
import com.volmit.iris.gen.v2.scaffold.stream.RoundingStream;
import com.volmit.iris.gen.v2.scaffold.stream.SelectionStream;
import com.volmit.iris.gen.v2.scaffold.stream.ZoomStream;

public interface ProceduralStream<T> extends ProceduralLayer, Interpolated<T>
{
	default ProceduralStream<Integer> round()
	{
		return new RoundingStream(this);
	}

	default <V> ProceduralStream<V> convert(Function<T, V> converter)
	{
		return new ConversionStream<T, V>(this, converter);
	}

	default <V> ProceduralStream<V> convertCached(Function<T, V> converter)
	{
		return new CachedConversionStream<T, V>(this, converter);
	}

	default ProceduralStream<T> offset(double x, double y, double z)
	{
		return new OffsetStream<T>(this, x, y, z);
	}

	default ProceduralStream<T> offset(double x, double z)
	{
		return new OffsetStream<T>(this, x, 0, z);
	}

	default ProceduralStream<T> zoom(double x, double y, double z)
	{
		return new ZoomStream<T>(this, x, y, z);
	}

	default ProceduralStream<T> zoom(double x, double z)
	{
		return new ZoomStream<T>(this, x, 1, z);
	}

	default ProceduralStream<T> zoom(double all)
	{
		return new ZoomStream<T>(this, all, all, all);
	}

	default <V> ProceduralStream<V> select(V[] types)
	{
		return new SelectionStream<V>(this, types);
	}

	default <V> ProceduralStream<V> select(List<V> types)
	{
		return new SelectionStream<V>(this, types);
	}

	default ProceduralStream<T> clamp(double min, double max)
	{
		return new ClampedStream<T>(this, min, max);
	}

	default ProceduralStream<T> fit(double min, double max)
	{
		return new FittedStream<T>(this, min, max);
	}

	default ProceduralStream<T> fit(double inMin, double inMax, double min, double max)
	{
		return new FittedStream<T>(this, inMin, inMax, min, max);
	}

	default void fill(Hunk<T> h, double x, double y, double z)
	{
		for(int i = 0; i < h.getWidth(); i++)
		{
			for(int j = 0; j < h.getHeight(); j++)
			{
				for(int k = 0; k < h.getDepth(); k++)
				{
					h.set(i, j, k, get(i + x, j + y, k + z));
				}
			}
		}
	}

	default <V> void fillUp2D(Hunk<V> h, double x, double z, V v)
	{
		for(int i = 0; i < h.getWidth(); i++)
		{
			for(int k = 0; k < h.getDepth(); k++)
			{
				double n = getDouble(i + x, k + z);

				for(int j = 0; j < Math.min(h.getHeight(), n); j++)
				{
					h.set(i, j, k, v);
				}
			}
		}
	}

	default <V> void fillUp2D(Hunk<V> h, double x, double z, ProceduralStream<V> v)
	{
		for(int i = 0; i < h.getWidth(); i++)
		{
			for(int k = 0; k < h.getDepth(); k++)
			{
				double n = getDouble(i + x, k + z);

				for(int j = 0; j < Math.min(h.getHeight(), n); j++)
				{
					h.set(i, j, k, v.get(i + x, j, k + z));
				}
			}
		}
	}

	public T get(double x, double z);

	public T get(double x, double y, double z);

	default double getDouble(double x, double z)
	{
		return toDouble(get(x, z));
	}

	default double getDouble(double x, double y, double z)
	{
		return toDouble(get(x, y, z));
	}
}
