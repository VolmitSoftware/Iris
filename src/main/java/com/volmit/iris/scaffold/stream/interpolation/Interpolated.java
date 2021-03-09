package com.volmit.iris.scaffold.stream.interpolation;

import com.volmit.iris.scaffold.stream.ProceduralStream;
import com.volmit.iris.util.CaveResult;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;
import org.bukkit.block.data.BlockData;

import java.util.function.Function;

public interface Interpolated<T>
{
	Interpolated<BlockData> BLOCK_DATA = of((t) -> 0D, (t) -> null);
	Interpolated<KList<CaveResult>> CAVE_RESULTS = of((t) -> 0D, (t) -> null);
	Interpolated<RNG> RNG = of((t) -> 0D, (t) -> null);
	Interpolated<Double> DOUBLE = of((t) -> t, (t) -> t);
	Interpolated<Integer> INT = of(Double::valueOf, Double::intValue);
	Interpolated<Long> LONG = of(Double::valueOf, Double::longValue);

	double toDouble(T t);

	T fromDouble(double d);

	default InterpolatorFactory<T> interpolate()
	{
		if(this instanceof ProceduralStream)
		{
			return new InterpolatorFactory<T>((ProceduralStream<T>) this);
		}

		return null;
	}

	static <T> Interpolated<T> of(Function<T, Double> a, Function<Double, T> b)
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
