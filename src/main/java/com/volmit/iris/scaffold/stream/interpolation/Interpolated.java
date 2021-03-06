package com.volmit.iris.scaffold.stream.interpolation;

import com.volmit.iris.scaffold.stream.ProceduralStream;
import com.volmit.iris.util.CaveResult;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;
import org.bukkit.block.data.BlockData;

import java.util.function.Function;

public interface Interpolated<T>
{
	public static final Interpolated<BlockData> BLOCK_DATA = of((t) -> 0D, (t) -> null);
	public static final Interpolated<KList<CaveResult>> CAVE_RESULTS = of((t) -> 0D, (t) -> null);
	public static final Interpolated<RNG> RNG = of((t) -> 0D, (t) -> null);
	public static final Interpolated<Double> DOUBLE = of((t) -> t, (t) -> t);
	public static final Interpolated<Integer> INT = of(Double::valueOf, Double::intValue);
	public static final Interpolated<Long> LONG = of(Double::valueOf, Double::longValue);

	public double toDouble(T t);

	public T fromDouble(double d);

	default InterpolatorFactory<T> interpolate()
	{
		if(this instanceof ProceduralStream)
		{
			return new InterpolatorFactory<T>((ProceduralStream<T>) this);
		}

		return null;
	}

	public static <T> Interpolated<T> of(Function<T, Double> a, Function<Double, T> b)
	{
		return new Interpolated<T>()
		{
			@Override
			public double toDouble(T t)
			{
				return a.apply(t);
			}

			@Override
			public T fromDouble(double d)
			{
				return b.apply(d);
			}
		};
	}
}
