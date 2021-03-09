package com.volmit.iris.scaffold.stream;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.scaffold.stream.arithmetic.*;
import com.volmit.iris.scaffold.stream.convert.*;
import com.volmit.iris.scaffold.stream.interpolation.Interpolated;
import com.volmit.iris.scaffold.stream.sources.FunctionStream;
import com.volmit.iris.scaffold.stream.utility.*;
import com.volmit.iris.util.*;

import java.util.List;
import java.util.function.Function;

public interface ProceduralStream<T> extends ProceduralLayer, Interpolated<T>
{
	public static ProceduralStream<Double> ofDouble(Function2<Double, Double, Double> f)
	{
		try {
			return of(f, Interpolated.DOUBLE);
		} catch (IncompatibleClassChangeError e){
			Iris.warn(f.toString());
			e.printStackTrace();
			return null;
		}
	}

	public static ProceduralStream<Double> ofDouble(Function3<Double, Double, Double, Double> f)
	{
		return of(f, Interpolated.DOUBLE);
	}

	public static <T> ProceduralStream<T> of(Function2<Double, Double, T> f, Interpolated<T> helper)
	{
		return of(f, (x, y, z) -> f.apply(x, z), helper);
	}

	public static <T> ProceduralStream<T> of(Function3<Double, Double, Double, T> f, Interpolated<T> helper)
	{
		return of((x, z) -> f.apply(x, 0D, z), f, helper);
	}

	public static <T> ProceduralStream<T> of(Function2<Double, Double, T> f, Function3<Double, Double, Double, T> f2, Interpolated<T> helper)
	{
		return new FunctionStream<>(f, f2, helper);
	}

	default ProceduralStream<T> profile()
	{
		return profile(10);
	}

	default ProceduralStream<T> profile(int memory)
	{
		return new ProfiledStream<>(this, memory);
	}

	default ProceduralStream<T> onNull(T v)
	{
		return new NullSafeStream<>(this, v);
	}

	default ProceduralStream<T> add(Function3<Double, Double, Double, Double> a)
	{
		return new AddingStream<>(this, a);
	}

	default ProceduralStream<T> add(Function2<Double, Double, Double> a)
	{
		return new AddingStream<>(this, a);
	}

	default ProceduralStream<T> add2D(Function2<Double, Double, Double> a)
	{
		return new AddingStream<>(this, a);
	}

	default ProceduralStream<T> add(double a)
	{
		return new AddingStream<>(this, a);
	}

	default ProceduralStream<T> blockToChunkCoords()
	{
		return bitShiftCoordsRight(4);
	}

	default ProceduralStream<T> chunkToRegionCoords()
	{
		return bitShiftCoordsRight(5);
	}

	default ProceduralStream<T> blockToRegionCoords()
	{
		return blockToChunkCoords().chunkToRegionCoords();
	}

	default ProceduralStream<T> regionToBlockCoords()
	{
		return regionToChunkCoords().chunkToBlockCoords();
	}

	default ProceduralStream<T> regionToChunkCoords()
	{
		return bitShiftCoordsLeft(5);
	}

	default ProceduralStream<T> chunkToBlockCoords()
	{
		return bitShiftCoordsLeft(4);
	}

	default ProceduralStream<T> bitShiftCoordsRight(int a)
	{
		return new CoordinateBitShiftRightStream<>(this, a);
	}

	default ProceduralStream<T> synchronize()
	{
		return new SynchronizedStream<>(this);
	}

	default ProceduralStream<T> semaphore(int permits)
	{
		return new SemaphoreStream<>(this, permits);
	}

	default ProceduralStream<T> bitShiftCoordsLeft(int a)
	{
		return new CoordinateBitShiftLeftStream<>(this, a);
	}

	default ProceduralStream<T> max(Function3<Double, Double, Double, Double> a)
	{
		return new MaxingStream<>(this, a);
	}

	default ProceduralStream<T> max(Function2<Double, Double, Double> a)
	{
		return new MaxingStream<>(this, a);
	}

	default ProceduralStream<T> slope()
	{
		return slope(1);
	}

	default ProceduralStream<T> slope(int range)
	{
		return new SlopeStream<>(this, range);
	}

	default ProceduralStream<T> max(double a)
	{
		return new MaxingStream<>(this, a);
	}

	default ProceduralStream<T> min(Function3<Double, Double, Double, Double> a)
	{
		return new MinningStream<>(this, a);
	}

	default ProceduralStream<T> min(Function2<Double, Double, Double> a)
	{
		return new MinningStream<>(this, a);
	}

	default ProceduralStream<T> min(double a)
	{
		return new MinningStream<>(this, a);
	}

	default ProceduralStream<T> subtract(Function3<Double, Double, Double, Double> a)
	{
		return new SubtractingStream<>(this, a);
	}

	default ProceduralStream<T> subtract(Function2<Double, Double, Double> a)
	{
		return new SubtractingStream<>(this, a);
	}

	default ProceduralStream<T> subtract(double a)
	{
		return new SubtractingStream<>(this, a);
	}

	default ProceduralStream<T> multiply(Function3<Double, Double, Double, Double> a)
	{
		return new MultiplyingStream<>(this, a);
	}

	default ProceduralStream<T> multiply(Function2<Double, Double, Double> a)
	{
		return new MultiplyingStream<>(this, a);
	}

	default ProceduralStream<T> multiply(double a)
	{
		return new MultiplyingStream<>(this, a);
	}

	default ProceduralStream<T> divide(Function3<Double, Double, Double, Double> a)
	{
		return new DividingStream<>(this, a);
	}

	default ProceduralStream<T> divide(Function2<Double, Double, Double> a)
	{
		return new DividingStream<>(this, a);
	}

	default ProceduralStream<T> divide(double a)
	{
		return new DividingStream<>(this, a);
	}

	default ProceduralStream<T> modulo(Function3<Double, Double, Double, Double> a)
	{
		return new ModuloStream<>(this, a);
	}

	default ProceduralStream<T> modulo(Function2<Double, Double, Double> a)
	{
		return new ModuloStream<>(this, a);
	}

	default ProceduralStream<T> modulo(double a)
	{
		return new ModuloStream<>(this, a);
	}

	default ProceduralStream<Integer> round()
	{
		return new RoundingStream(this);
	}

	default ProceduralStream<Double> roundDouble()
	{
		return new RoundingDoubleStream(this);
	}

	default ProceduralStream<Double> forceDouble()
	{
		return new ForceDoubleStream(this);
	}

	default ProceduralStream<Significance<T>> significance(double radius, int checks)
	{
		return new SignificanceStream<Significance<T>, T>(this, radius, checks);
	}

	default ProceduralStream<T> to3D()
	{
		return new To3DStream<T>(this);
	}

	default ProceduralStream<T> cache2D(int maxSize)
	{
		return new CachedStream2D<T>(this, maxSize);
	}

	default <V> ProceduralStream<V> convert(Function<T, V> converter)
	{
		return new ConversionStream<T,V>(this, converter);
	}

	default <V> ProceduralStream<V> convertAware2D(Function3<T, Double, Double, V> converter)
	{
		return new AwareConversionStream2D<T, V>(this, converter);
	}

	default <V> ProceduralStream<V> convertAware3D(Function4<T, Double, Double, Double, V> converter)
	{
		return new AwareConversionStream3D<T, V>(this, converter);
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

	default ProceduralStream<T> radial(double scale)
	{
		return new RadialStream<>(this, scale);
	}

	default ProceduralStream<T> radial()
	{
		return radial(1D);
	}

	default <V> ProceduralStream<V> select(V... types)
	{
		return new SelectionStream<V>(this, types);
	}

	default <V> ProceduralStream<V> select(List<V> types)
	{
		return new SelectionStream<V>(this, types);
	}

	@SuppressWarnings("unchecked")
	default <V> ProceduralStream<V> selectRarity(V... types)
	{
		KList<V> rarityTypes = new KList<>();

		for(V i : types)
		{
			rarityTypes.addMultiple(i, IRare.get(i));
		}

		return new SelectionStream<V>(this, rarityTypes);
	}

	default <V> ProceduralStream<V> selectRarity(List<V> types)
	{
		KList<V> rarityTypes = new KList<>();
		int totalRarity = 0;
		for(V i : types)
		{
			totalRarity += IRare.get(i);
		}

		for(V i : types)
		{
			rarityTypes.addMultiple(i, Math.max(1, (IRare.get(i) / totalRarity)));
		}

		return new SelectionStream<V>(this, rarityTypes);
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

	default void fill(Hunk<T> h, double x, double y, double z, int parallelism)
	{
		h.compute3D(parallelism, (xx, yy, zz, hh) -> hh.iterate((xv, yv, zv) -> hh.set(xv, yv, zv, get(xx + xv + x, yy + yv + y, zz + zv + z))));
	}

	default <V> void fill2D(Hunk<V> h, double x, double z, V v, int parallelism)
	{
		h.compute2D(parallelism, (xx, __, zz, hh) ->
		{
			for(int i = 0; i < hh.getWidth(); i++)
			{
				for(int k = 0; k < hh.getDepth(); k++)
				{
					double n = getDouble(i + x + xx, k + z + zz);

					for(int j = 0; j < Math.min(h.getHeight(), n); j++)
					{
						hh.set(i, j, k, v);
					}
				}
			}
		});
	}

	default <V> void fill2D(Hunk<V> h, double x, double z, ProceduralStream<V> v, int parallelism)
	{
		h.compute2D(parallelism, (xx, yy, zz, hh) ->
		{
			for(int i = 0; i < hh.getWidth(); i++)
			{
				for(int k = 0; k < hh.getDepth(); k++)
				{
					double n = getDouble(i + x + xx, k + z + zz);

					for(int j = 0; j < Math.min(h.getHeight(), n); j++)
					{
						hh.set(i, j, k, v.get(i + x + xx, j + yy, k + z + zz));
					}
				}
			}
		});
	}

	default <V> void fill2DYLocked(Hunk<V> h, double x, double z, V v, int parallelism)
	{
		h.compute2D(parallelism, (xx, yy, zz, hh) ->
		{
			for(int i = 0; i < hh.getWidth(); i++)
			{
				for(int k = 0; k < hh.getDepth(); k++)
				{
					double n = getDouble(i + x + xx, k + z + zz);

					for(int j = 0; j < Math.min(h.getHeight(), n); j++)
					{
						hh.set(i, j, k, v);
					}
				}
			}
		});
	}

	default <V> void fill2DYLocked(Hunk<V> h, double x, double z, ProceduralStream<V> v, int parallelism)
	{
		h.compute2D(parallelism, (xx, yy, zz, hh) ->
		{
			for(int i = 0; i < hh.getWidth(); i++)
			{
				for(int k = 0; k < hh.getDepth(); k++)
				{
					double n = getDouble(i + x + xx, k + z + zz);

					for(int j = 0; j < Math.min(h.getHeight(), n); j++)
					{
						hh.set(i, j, k, v.get(i + x + xx, k + z + zz));
					}
				}
			}
		});
	}

	default <V> void fill3D(Hunk<V> h, double x, int y, double z, V v, int parallelism)
	{
		h.compute3D(parallelism, (xx, yy, zz, hh) -> hh.iterate((xv, yv, zv) ->
		{
			if(getDouble(xx + xv + x, yy + yv + y, zz + zv + z) > 0.5)
			{
				hh.set(xv, yv, zv, v);
			}
		}));
	}

	default <V> void fill3D(Hunk<V> h, double x, int y, double z, ProceduralStream<V> v, int parallelism)
	{
		h.compute3D(parallelism, (xx, yy, zz, hh) -> hh.iterate((xv, yv, zv) ->
		{
			if(getDouble(xx + xv + x, yy + yv + y, zz + zv + z) > 0.5)
			{
				hh.set(xv, yv, zv, v.get(xx + xv + x, yy + yv + y, zz + zv + z));
			}
		}));
	}

	default void fill(Hunk<T> h, double x, double y, double z)
	{
		fill(h, x, z, 4);
	}

	default <V> void fill2D(Hunk<V> h, double x, double z, V v)
	{
		fill2D(h, x, z, v, 4);
	}

	default <V> void fill2D(Hunk<V> h, double x, double z, ProceduralStream<V> v)
	{
		fill2D(h, x, z, v, 4);
	}

	default <V> void fill2DYLocked(Hunk<V> h, double x, double z, V v)
	{
		fill2DYLocked(h, x, z, v, 4);
	}

	default <V> void fill2DYLocked(Hunk<V> h, double x, double z, ProceduralStream<V> v)
	{
		fill2DYLocked(h, x, z, v, 4);
	}

	default <V> void fill3D(Hunk<V> h, double x, int y, double z, V v)
	{
		fill3D(h, x, y, z, v, 4);
	}

	default <V> void fill3D(Hunk<V> h, double x, int y, double z, ProceduralStream<V> v)
	{
		fill3D(h, x, y, z, v, 4);
	}

	default double getDouble(double x, double z)
	{
		return toDouble(get(x, z));
	}

	default double getDouble(double x, double y, double z)
	{
		return toDouble(get(x, y, z));
	}

	public ProceduralStream<T> getTypedSource();

	public ProceduralStream<?> getSource();

	public T get(double x, double z);

	public T get(double x, double y, double z);
}
