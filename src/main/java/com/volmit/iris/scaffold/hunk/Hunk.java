package com.volmit.iris.scaffold.hunk;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.hunk.io.HunkIOAdapter;
import com.volmit.iris.scaffold.hunk.storage.*;
import com.volmit.iris.scaffold.hunk.view.*;
import com.volmit.iris.scaffold.parallel.BurstExecutor;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.*;
import org.bukkit.Chunk;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface Hunk<T>
{
	/**
	 * Create a hunk view from a source hunk. This view reads and writes through to
	 * the source hunk. Its is not a copy.
	 * 
	 * @param <T>
	 *            the type
	 * @param src
	 *            the source hunk
	 * @return the hunk view
	 */
	public static <T> Hunk<T> view(Hunk<T> src)
	{
		return new HunkView<T>(src);
	}

	public static Hunk<Biome> view(BiomeGrid biome)
	{
		return new BiomeGridHunkView(biome);
	}

	public static <T> Hunk<T> fringe(Hunk<T> i, Hunk<T> o)
	{
		return new FringedHunkView<>(i, o);
	}

	public static Hunk<BlockData> view(ChunkData src)
	{
		return new ChunkDataHunkView(src);
	}

	public static Hunk<BlockData> viewBlocks(Chunk src)
	{
		return new ChunkHunkView(src);
	}

	public static Hunk<Biome> viewBiomes(Chunk src)
	{
		return new ChunkBiomeHunkView(src);
	}

	public static <T> Hunk<T> newHunk(int w, int h, int d)
	{
		return newArrayHunk(w, h, d);
	}

	@SafeVarargs
	public static <T> Hunk<T> newCombinedHunk(Hunk<T>... hunks)
	{
		return newCombinedArrayHunk(hunks);
	}

	default Hunk<T> listen(Consumer4<Integer, Integer, Integer, T> l)
	{
		return new ListeningHunk<>(this, l);
	}

	default Hunk<T> synchronize()
	{
		return new SynchronizedHunkView<>(this);
	}

	default Hunk<T> trackWrite(AtomicBoolean b)
	{
		return new WriteTrackHunk<T>(this, b);
	}

	public static <T> Hunk<T> newArrayHunk(int w, int h, int d)
	{
		return new ArrayHunk<>(w, h, d);
	}

	@SafeVarargs
	public static <T> Hunk<T> newCombinedArrayHunk(Hunk<T>... hunks)
	{
		return combined(Hunk::newArrayHunk, hunks);
	}

	public static <T> Hunk<T> newSynchronizedArrayHunk(int w, int h, int d)
	{
		return new SynchronizedArrayHunk<>(w, h, d);
	}

	@SafeVarargs
	public static <T> Hunk<T> newCombinedSynchronizedArrayHunk(Hunk<T>... hunks)
	{
		return combined(Hunk::newSynchronizedArrayHunk, hunks);
	}

	public static <T> Hunk<T> newMappedHunk(int w, int h, int d)
	{
		return new MappedHunk<>(w, h, d);
	}

	public static <T> Hunk<T> newMappedHunkSynced(int w, int h, int d)
	{
		return new MappedHunk<T>(w, h, d).synchronize();
	}

	@SafeVarargs
	public static <T> Hunk<T> newCombinedMappedHunk(Hunk<T>... hunks)
	{
		return combined(Hunk::newMappedHunk, hunks);
	}

	public static <T> Hunk<T> newAtomicHunk(int w, int h, int d)
	{
		return new AtomicHunk<>(w, h, d);
	}

	@SafeVarargs
	public static <T> Hunk<T> newCombinedAtomicHunk(Hunk<T>... hunks)
	{
		return combined(Hunk::newAtomicHunk, hunks);
	}

	public static Hunk<Double> newAtomicDoubleHunk(int w, int h, int d)
	{
		return new AtomicDoubleHunk(w, h, d);
	}

	@SafeVarargs
	public static Hunk<Double> newCombinedAtomicDoubleHunk(Hunk<Double>... hunks)
	{
		return combined(Hunk::newAtomicDoubleHunk, hunks);
	}

	public static Hunk<Long> newAtomicLongHunk(int w, int h, int d)
	{
		return new AtomicLongHunk(w, h, d);
	}

	@SafeVarargs
	public static Hunk<Long> newCombinedAtomicLongHunk(Hunk<Long>... hunks)
	{
		return combined(Hunk::newAtomicLongHunk, hunks);
	}

	public static Hunk<Integer> newAtomicIntegerHunk(int w, int h, int d)
	{
		return new AtomicIntegerHunk(w, h, d);
	}

	@SafeVarargs
	public static Hunk<Integer> newCombinedAtomicIntegerHunk(Hunk<Integer>... hunks)
	{
		return combined(Hunk::newAtomicIntegerHunk, hunks);
	}

	/**
	 * Creates a new bounding hunk from the given hunks
	 * 
	 * @param <T>
	 *            the type
	 * @param factory
	 *            the factory that creates a hunk
	 * @param hunks
	 *            the hunks
	 * @return the new bounding hunk
	 */
	@SafeVarargs
	public static <T> Hunk<T> combined(Function3<Integer, Integer, Integer, Hunk<T>> factory, Hunk<T>... hunks)
	{
		int w = 0;
		int h = 0;
		int d = 0;

		for(Hunk<T> i : hunks)
		{
			w = Math.max(w, i.getWidth());
			h = Math.max(h, i.getHeight());
			d = Math.max(d, i.getDepth());
		}

		Hunk<T> b = factory.apply(w, h, d);

		for(Hunk<T> i : hunks)
		{
			b.insert(i);
		}

		return b;
	}

	default Hunk<T> readOnly()
	{
		return new ReadOnlyHunk<>(this);
	}

	default int getNonNullEntries()
	{
		AtomicInteger count = new AtomicInteger();
		iterate((x, y, z, v) -> count.getAndAdd(1));

		return count.get();
	}

	default void write(OutputStream s, HunkIOAdapter<T> h) throws IOException
	{
		h.write(this, s);
	}

	default ByteArrayTag writeByteArrayTag(HunkIOAdapter<T> h, String name) throws IOException
	{
		return h.writeByteArrayTag(this, name);
	}

	default void write(File s, HunkIOAdapter<T> h) throws IOException
	{
		h.write(this, s);
	}

	default boolean isAtomic()
	{
		return false;
	}

	default Hunk<T> invertY()
	{
		return new InvertedHunkView<T>(this);
	}

	default Hunk<T> rotateX(double degrees)
	{
		return new RotatedXHunkView<T>(this, degrees);
	}

	default Hunk<T> rotateY(double degrees)
	{
		return new RotatedYHunkView<T>(this, degrees);
	}

	default Hunk<T> rotateZ(double degrees)
	{
		return new RotatedZHunkView<T>(this, degrees);
	}

	default int getMaximumDimension()
	{
		return Math.max(getWidth(), Math.max(getHeight(), getDepth()));
	}

	default int getIdeal2DParallelism()
	{
		return getMax2DParallelism() / 4;
	}

	default int getIdeal3DParallelism()
	{
		return getMax3DParallelism() / 8;
	}

	default int getMinimumDimension()
	{
		return Math.min(getWidth(), Math.min(getHeight(), getDepth()));
	}

	default int getMax2DParallelism()
	{
		return (int) Math.pow(getMinimumDimension() / 2, 2);
	}

	default int getMax3DParallelism()
	{
		return (int) Math.pow(getMinimumDimension() / 2, 3);
	}

	default int filterDimension(int dim)
	{
		if(dim <= 1)
		{
			return 1;
		}

		dim = dim % 2 != 0 ? dim + 1 : dim;

		if(dim > getMinimumDimension() / 2)
		{
			if(dim <= 2)
			{
				return 1;
			}

			dim -= 2;
		}

		return dim;
	}

	default int get2DDimension(int sections)
	{
		if(sections <= 1)
		{
			return 1;
		}

		return filterDimension((int) Math.ceil(Math.sqrt(sections)));
	}

	default int get3DDimension(int sections)
	{
		if(sections <= 1)
		{
			return 1;
		}

		return filterDimension((int) Math.ceil(Math.cbrt(sections)));
	}

	/**
	 * Iterate surfaces on 2d. Raytraces with a front and a back which stretches
	 * through surfaces. Essentially what is returned is the following (in
	 * order)<br>
	 * <br>
	 * 
	 * The predicate is used to determine if the given block type is solid or not.
	 * 
	 * <br>
	 * <br>
	 * ================================================ <br>
	 * AX, AZ: Hunk Relative X and Z
	 * 
	 * <br>
	 * <br>
	 * HX, HZ: Hunk Positional X and Z (in its parent hunk)
	 * 
	 * <br>
	 * <br>
	 * TOP: The top of this surface (top+1 is air above a surface)
	 * 
	 * <br>
	 * <br>
	 * BOTTOM: The bottom of this surface (bottom is the lowest SOLID surface before
	 * either air or bedrock going down further)
	 * 
	 * <br>
	 * <br>
	 * LAST_BOTTOM: The previous bottom. If your surface is the top surface, this
	 * will be -1 as there is no bottom-of-surface above you. However if you are not
	 * the top surface, this value is equal to the next solid layer above TOP, such
	 * that ((LAST_BOTTOM - 1) - (TOP + 1)) is how many air blocks are between your
	 * surface and the surface above you
	 * 
	 * <br>
	 * <br>
	 * HUNK: The hunk to set data to. <br>
	 * ================================================ <br>
	 * <br>
	 * If we assume your chunk coordinates are x and z, then <br>
	 * <br>
	 * bX = (x * 16)<br>
	 * bZ = (z * 16)<br>
	 * <br>
	 * (ax, az, hx, hz, top, bottom, lastBottom, hunk) {<br>
	 * actualBlockX = ax+hx;<br>
	 * actualBlockZ = az+hz;<br>
	 * <br>
	 * hunkX = ax;<br>
	 * hunkZ = az;<br>
	 * <br>
	 * hunk.set(hunkX, ?, hunkZ, noise(actualBlockX, ?, actualBlockZ));<br>
	 * }<br>
	 * 
	 * @param p
	 *            the predicate
	 * @param c
	 *            the consumer
	 * @return this
	 */
	default Hunk<T> iterateSurfaces2D(Predicate<T> p, Consumer8<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Hunk<T>> c)
	{
		return iterateSurfaces2D(getIdeal2DParallelism(), p, c);
	}

	/**
	 * Iterate surfaces on 2d. Raytraces with a front and a back which stretches
	 * through surfaces. Essentially what is returned is the following (in
	 * order)<br>
	 * <br>
	 * 
	 * The predicate is used to determine if the given block type is solid or not.
	 * 
	 * <br>
	 * <br>
	 * ================================================ <br>
	 * AX, AZ: Hunk Relative X and Z
	 * 
	 * <br>
	 * <br>
	 * HX, HZ: Hunk Positional X and Z (in its parent hunk)
	 * 
	 * <br>
	 * <br>
	 * TOP: The top of this surface (top+1 is air above a surface)
	 * 
	 * <br>
	 * <br>
	 * BOTTOM: The bottom of this surface (bottom is the lowest SOLID surface before
	 * either air or bedrock going down further)
	 * 
	 * <br>
	 * <br>
	 * LAST_BOTTOM: The previous bottom. If your surface is the top surface, this
	 * will be -1 as there is no bottom-of-surface above you. However if you are not
	 * the top surface, this value is equal to the next solid layer above TOP, such
	 * that ((LAST_BOTTOM - 1) - (TOP + 1)) is how many air blocks are between your
	 * surface and the surface above you
	 * 
	 * <br>
	 * <br>
	 * HUNK: The hunk to set data to. <br>
	 * ================================================ <br>
	 * <br>
	 * If we assume your chunk coordinates are x and z, then <br>
	 * <br>
	 * bX = (x * 16)<br>
	 * bZ = (z * 16)<br>
	 * <br>
	 * (ax, az, hx, hz, top, bottom, lastBottom, hunk) {<br>
	 * actualBlockX = ax+hx;<br>
	 * actualBlockZ = az+hz;<br>
	 * <br>
	 * hunkX = ax;<br>
	 * hunkZ = az;<br>
	 * <br>
	 * hunk.set(hunkX, ?, hunkZ, noise(actualBlockX, ?, actualBlockZ));<br>
	 * }<br>
	 * 
	 * @param parallelism
	 *            the ideal threads to use on this
	 * @param p
	 *            the predicate
	 * @param c
	 *            the consumer
	 * @return this
	 */
	default Hunk<T> iterateSurfaces2D(int parallelism, Predicate<T> p, Consumer8<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Hunk<T>> c)
	{
		iterate2DTop(parallelism, (ax, az, hox, hoz, h) ->
		{
			int last = -1;
			int in = getHeight() - 1;
			boolean hitting = false;
			for(int i = getHeight() - 1; i >= 0; i--)
			{
				boolean solid = p.test(h.get(ax, i, az));

				if(!hitting && solid)
				{
					in = i;
					hitting = true;
				}

				else if(hitting && !solid)
				{
					hitting = false;
					c.accept(ax, az, hox, hoz, in, i - 1, last, h);
					last = i - 1;
				}
			}

			if(hitting)
			{
				c.accept(ax, az, hox, hoz, in, 0, last, h);
			}
		});

		return this;
	}

	/**
	 * Iterate on the xz top of this hunk. When using this consumer, given
	 * 
	 * consumer: (ax, az, hx, hz, hunk)
	 * 
	 * hunk.set(ax, ?, az, NOISE.get(ax+hx, az+hz));
	 * 
	 * @param c
	 *            the consumer hunkX, hunkZ, hunkOffsetX, hunkOffsetZ.
	 * @return this
	 */
	default Hunk<T> iterate2DTop(Consumer5<Integer, Integer, Integer, Integer, Hunk<T>> c)
	{
		return iterate2DTop(getIdeal2DParallelism(), c);
	}

	default Hunk<T> drift(int x, int y, int z)
	{
		return new DriftHunkView<>(this, x, y, z);
	}

	/**
	 * Iterate on the xz top of this hunk. When using this consumer, given
	 * 
	 * consumer: (ax, az, hx, hz, hunk)
	 * 
	 * hunk.set(ax, ?, az, NOISE.get(ax+hx, az+hz));
	 * 
	 * @param parallelism
	 *            the target parallelism value or 0 to disable
	 * @param c
	 *            the consumer hunkX, hunkZ, hunkOffsetX, hunkOffsetZ.
	 * @return this
	 */
	default Hunk<T> iterate2DTop(int parallelism, Consumer5<Integer, Integer, Integer, Integer, Hunk<T>> c)
	{
		compute2D(parallelism, (x, y, z, h) ->
		{
			for(int i = 0; i < h.getWidth(); i++)
			{
				for(int k = 0; k < h.getDepth(); k++)
				{
					c.accept(i, k, x, z, h);
				}
			}
		});

		return this;
	}

	default Hunk<T> iterate(Predicate<T> p, Consumer3<Integer, Integer, Integer> c)
	{
		return iterate(getIdeal3DParallelism(), p, c);
	}

	default Hunk<T> iterate(int parallelism, Predicate<T> p, Consumer3<Integer, Integer, Integer> c)
	{
		iterate(parallelism, (x, y, z, t) ->
		{
			if(p.test(t))
			{
				c.accept(x, y, z);
			}
		});

		return this;
	}

	default Hunk<T> iterate(Predicate<T> p, Consumer4<Integer, Integer, Integer, T> c)
	{
		return iterate(getIdeal3DParallelism(), p, c);
	}

	default Hunk<T> iterate(int parallelism, Predicate<T> p, Consumer4<Integer, Integer, Integer, T> c)
	{
		iterate(parallelism, (x, y, z, t) ->
		{
			if(p.test(t))
			{
				c.accept(x, y, z, t);
			}
		});

		return this;
	}

	default Hunk<T> iterate(Consumer3<Integer, Integer, Integer> c)
	{
		return iterate(getIdeal3DParallelism(), c);
	}

	default Hunk<T> iterateSync(Consumer3<Integer, Integer, Integer> c)
	{
		for(int i = 0; i < getWidth(); i++)
		{
			for(int j = 0; j < getHeight(); j++)
			{
				for(int k = 0; k < getDepth(); k++)
				{
					c.accept(i, j, k);
				}
			}
		}

		return this;
	}

	default Hunk<T> iterateSync(Consumer4<Integer, Integer, Integer, T> c)
	{
		for(int i = 0; i < getWidth(); i++)
		{
			for(int j = 0; j < getHeight(); j++)
			{
				for(int k = 0; k < getDepth(); k++)
				{
					c.accept(i, j, k, get(i,j,k));
				}
			}
		}

		return this;
	}

	default Hunk<T> iterate(int parallelism, Consumer3<Integer, Integer, Integer> c)
	{
		compute3D(parallelism, (x, y, z, h) ->
		{
			for(int i = 0; i < h.getWidth(); i++)
			{
				for(int j = 0; j < h.getHeight(); j++)
				{
					for(int k = 0; k < h.getDepth(); k++)
					{
						c.accept(i + x, j + y, k + z);
					}
				}
			}
		});

		return this;
	}

	default Hunk<T> iterate(Consumer4<Integer, Integer, Integer, T> c)
	{
		return iterate(getIdeal3DParallelism(), c);
	}

	default Hunk<T> iterate(int parallelism, Consumer4<Integer, Integer, Integer, T> c)
	{
		compute3D(parallelism, (x, y, z, h) ->
		{
			for(int i = 0; i < h.getWidth(); i++)
			{
				for(int j = 0; j < h.getHeight(); j++)
				{
					for(int k = 0; k < h.getDepth(); k++)
					{
						c.accept(i + x, j + y, k + z, h.get(i, j, k));
					}
				}
			}
		});

		return this;
	}

	default Hunk<T> compute2D(Consumer4<Integer, Integer, Integer, Hunk<T>> v)
	{
		return compute2D(getIdeal2DParallelism(), v);
	}

	public static <A, B> void computeDual2D(int parallelism, Hunk<A> a, Hunk<B> b, Consumer5<Integer, Integer, Integer, Hunk<A>, Hunk<B>> v)
	{
		if(a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight() || a.getDepth() != b.getDepth())
		{
			throw new RuntimeException("Hunk sizes must match!");
		}

		if(a.get2DDimension(parallelism) == 1)
		{
			v.accept(0, 0, 0, a, b);
			return;
		}

		BurstExecutor e = MultiBurst.burst.burst(parallelism);
		KList<Runnable> rq = new KList<Runnable>(parallelism);
		getDualSections2D(parallelism, a, b, (xx, yy, zz, ha, hr, r) -> e.queue(() ->
		{
			v.accept(xx, yy, zz, ha, hr);

			synchronized(rq)
			{
				rq.add(r);
			}
		}), (x, y, z, hax, hbx) ->
		{
			a.insert(x, y, z, hax);
			b.insert(x, y, z, hbx);
		});
		e.complete();
		rq.forEach(Runnable::run);
		return;
	}

	public static <A, B> void getDualSections2D(int sections, Hunk<A> a, Hunk<B> b, Consumer6<Integer, Integer, Integer, Hunk<A>, Hunk<B>, Runnable> v, Consumer5<Integer, Integer, Integer, Hunk<A>, Hunk<B>> inserterAB)
	{
		if(a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight() || a.getDepth() != b.getDepth())
		{
			throw new RuntimeException("Hunk sizes must match!");
		}

		int dim = a.get2DDimension(sections);

		if(sections <= 1)
		{
			getDualSection(0, 0, 0, a.getWidth(), a.getHeight(), a.getDepth(), a, b, (ha, hr, r) -> v.accept(0, 0, 0, ha, hr, r), inserterAB);
			return;
		}

		int w = a.getWidth() / dim;
		int wr = a.getWidth() - (w * dim);
		int d = a.getDepth() / dim;
		int dr = a.getDepth() - (d * dim);
		int i, j;

		for(i = 0; i < a.getWidth(); i += w)
		{
			int ii = i;

			for(j = 0; j < a.getDepth(); j += d)
			{
				int jj = j;
				getDualSection(i, 0, j, i + w + (i == 0 ? wr : 0), a.getHeight(), j + d + (j == 0 ? dr : 0), a, b, (ha, hr, r) -> v.accept(ii, 0, jj, ha, hr, r), inserterAB);
				i = i == 0 ? i + wr : i;
				j = j == 0 ? j + dr : j;
			}
		}
	}

	static <A, B> void getDualSection(int x, int y, int z, int x1, int y1, int z1, Hunk<A> a, Hunk<B> b, Consumer3<Hunk<A>, Hunk<B>, Runnable> v, Consumer5<Integer, Integer, Integer, Hunk<A>, Hunk<B>> inserter)
	{
		Hunk<A> copya = a.crop(x, y, z, x1, y1, z1);
		Hunk<B> copyb = b.crop(x, y, z, x1, y1, z1);
		v.accept(copya, copyb, () -> inserter.accept(x, y, z, copya, copyb));
	}

	default Hunk<T> compute2D(int parallelism, Consumer4<Integer, Integer, Integer, Hunk<T>> v)
	{
		if(get2DDimension(parallelism) == 1)
		{
			v.accept(0, 0, 0, this);
			return this;
		}

		BurstExecutor e = MultiBurst.burst.burst(parallelism);

		if(isAtomic())
		{
			getSectionsAtomic2D(parallelism, (xx, yy, zz, h) -> e.queue(() ->
			{
				v.accept(xx, yy, zz, h);
			}));

			e.complete();
		}

		else
		{
			KList<Runnable> rq = new KList<Runnable>(parallelism);

			getSections2D(parallelism, (xx, yy, zz, h, r) -> e.queue(() ->
			{
				v.accept(xx, yy, zz, h);

				synchronized(rq)
				{
					rq.add(r);
				}
			}), this::insert);

			e.complete();
			rq.forEach(Runnable::run);
		}

		return this;
	}

	default Hunk<T> compute2DYRange(int parallelism, int ymin, int ymax, Consumer4<Integer, Integer, Integer, Hunk<T>> v)
	{
		if(get2DDimension(parallelism) == 1)
		{
			v.accept(0, 0, 0, this);
			return this;
		}

		BurstExecutor e = MultiBurst.burst.burst(parallelism);
		KList<Runnable> rq = new KList<Runnable>(parallelism);
		getSections2DYLimit(parallelism, ymin, ymax, (xx, yy, zz, h, r) -> e.queue(() ->
		{
			v.accept(xx, yy, zz, h);

			synchronized(rq)
			{
				rq.add(r);
			}
		}), this::insert);
		e.complete();
		rq.forEach(Runnable::run);
		return this;
	}

	default Hunk<T> compute3D(Consumer4<Integer, Integer, Integer, Hunk<T>> v)
	{
		return compute3D(getIdeal3DParallelism(), v);
	}

	default Hunk<T> compute3D(int parallelism, Consumer4<Integer, Integer, Integer, Hunk<T>> v)
	{
		if(get3DDimension(parallelism) == 1)
		{
			v.accept(0, 0, 0, this);
			return this;
		}

		BurstExecutor e = MultiBurst.burst.burst(parallelism);
		KList<Runnable> rq = new KList<Runnable>(parallelism);
		getSections3D(parallelism, (xx, yy, zz, h, r) -> e.queue(() ->
		{
			v.accept(xx, yy, zz, h);
			synchronized(rq)
			{
				rq.add(r);
			}
		}), this::insert);
		e.complete();
		rq.forEach(Runnable::run);
		return this;
	}

	default Hunk<T> getSections2D(int sections, Consumer5<Integer, Integer, Integer, Hunk<T>, Runnable> v)
	{
		return getSections2D(sections, v, this::insert);
	}

	default Hunk<T> getSectionsAtomic2D(int sections, Consumer4<Integer, Integer, Integer, Hunk<T>> v)
	{
		int dim = (int) get2DDimension(sections);

		if(sections <= 1)
		{
			getAtomicSection(0, 0, 0, getWidth(), getHeight(), getDepth(), (hh) -> v.accept(0, 0, 0, hh));
			return this;
		}

		int w = getWidth() / dim;
		int wr = getWidth() - (w * dim);
		int d = getDepth() / dim;
		int dr = getDepth() - (d * dim);
		int i, j;

		for(i = 0; i < getWidth(); i += w)
		{
			int ii = i;

			for(j = 0; j < getDepth(); j += d)
			{
				int jj = j;
				getAtomicSection(i, 0, j, i + w + (i == 0 ? wr : 0), getHeight(), j + d + (j == 0 ? dr : 0), (h) -> v.accept(ii, 0, jj, h));
				i = i == 0 ? i + wr : i;
				j = j == 0 ? j + dr : j;
			}
		}
		;

		return this;
	}

	default Hunk<T> getSections2D(int sections, Consumer5<Integer, Integer, Integer, Hunk<T>, Runnable> v, Consumer4<Integer, Integer, Integer, Hunk<T>> inserter)
	{
		int dim = (int) get2DDimension(sections);

		if(sections <= 1)
		{
			getSection(0, 0, 0, getWidth(), getHeight(), getDepth(), (hh, r) -> v.accept(0, 0, 0, hh, r), inserter);
			return this;
		}

		int w = getWidth() / dim;
		int wr = getWidth() - (w * dim);
		int d = getDepth() / dim;
		int dr = getDepth() - (d * dim);
		int i, j;

		for(i = 0; i < getWidth(); i += w)
		{
			int ii = i;

			for(j = 0; j < getDepth(); j += d)
			{
				int jj = j;
				getSection(i, 0, j, i + w + (i == 0 ? wr : 0), getHeight(), j + d + (j == 0 ? dr : 0), (h, r) -> v.accept(ii, 0, jj, h, r), inserter);
				i = i == 0 ? i + wr : i;
				j = j == 0 ? j + dr : j;
			}
		}
		;

		return this;
	}

	default Hunk<T> getSections2DYLimit(int sections, int ymin, int ymax, Consumer5<Integer, Integer, Integer, Hunk<T>, Runnable> v, Consumer4<Integer, Integer, Integer, Hunk<T>> inserter)
	{
		int dim = (int) get2DDimension(sections);

		if(sections <= 1)
		{
			getSection(0, 0, 0, getWidth(), getHeight(), getDepth(), (hh, r) -> v.accept(0, 0, 0, hh, r), inserter);
			return this;
		}

		int w = getWidth() / dim;
		int wr = getWidth() - (w * dim);
		int d = getDepth() / dim;
		int dr = getDepth() - (d * dim);
		int i, j;

		for(i = 0; i < getWidth(); i += w)
		{
			int ii = i;

			for(j = 0; j < getDepth(); j += d)
			{
				int jj = j;
				getSection(i, ymin, j, i + w + (i == 0 ? wr : 0), ymax, j + d + (j == 0 ? dr : 0), (h, r) -> v.accept(ii, ymin, jj, h, r), inserter);
				i = i == 0 ? i + wr : i;
				j = j == 0 ? j + dr : j;
			}
		}

		return this;
	}

	default Hunk<T> getSections3D(int sections, Consumer5<Integer, Integer, Integer, Hunk<T>, Runnable> v)
	{
		return getSections3D(sections, v, (xx, yy, zz, c) -> insert(xx, yy, zz, c));
	}

	default Hunk<T> getSections3D(int sections, Consumer5<Integer, Integer, Integer, Hunk<T>, Runnable> v, Consumer4<Integer, Integer, Integer, Hunk<T>> inserter)
	{
		int dim = (int) get3DDimension(sections);

		if(sections <= 1)
		{
			getSection(0, 0, 0, getWidth(), getHeight(), getDepth(), (hh, r) -> v.accept(0, 0, 0, hh, r), inserter);
			return this;
		}

		int w = getWidth() / dim;
		int h = getHeight() / dim;
		int d = getDepth() / dim;
		int wr = getWidth() - (w * dim);
		int hr = getHeight() - (h * dim);
		int dr = getDepth() - (d * dim);
		int i, j, k;

		for(i = 0; i < getWidth(); i += w)
		{
			int ii = i;

			for(j = 0; j < getHeight(); j += d)
			{
				int jj = j;

				for(k = 0; k < getDepth(); k += d)
				{
					int kk = k;
					getSection(ii, jj, kk, i + w + (i == 0 ? wr : 0), j + h + (j == 0 ? hr : 0), k + d + (k == 0 ? dr : 0), (hh, r) -> v.accept(ii, jj, kk, hh, r), inserter);
					i = i == 0 ? i + wr : i;
					j = j == 0 ? j + hr : j;
					k = k == 0 ? k + dr : k;
				}
			}
		}

		return this;
	}

	default Hunk<T> getSection(int x, int y, int z, int x1, int y1, int z1, Consumer2<Hunk<T>, Runnable> v)
	{
		return getSection(x, y, z, x1, y1, z1, v, (xx, yy, zz, c) -> insert(xx, yy, zz, c));
	}

	default Hunk<T> getSection(int x, int y, int z, int x1, int y1, int z1, Consumer2<Hunk<T>, Runnable> v, Consumer4<Integer, Integer, Integer, Hunk<T>> inserter)
	{
		Hunk<T> copy = crop(x, y, z, x1, y1, z1);
		v.accept(copy, () -> inserter.accept(x, y, z, copy));
		return this;
	}

	default Hunk<T> getAtomicSection(int x, int y, int z, int x1, int y1, int z1, Consumer<Hunk<T>> v)
	{
		Hunk<T> copy = croppedView(x, y, z, x1, y1, z1);
		v.accept(copy);
		return this;
	}

	default void enforceBounds(int x, int y, int z)
	{
		if(x < 0 || x >= getWidth() || y < 0 || y >= getHeight() || z < 0 || z >= getDepth())
		{
			Iris.warn(x + "," + y + "," + z + " does not fit within size " + getWidth() + "," + getHeight() + "," + getDepth() + " (0,0,0 to " + (getWidth() - 1) + "," + (getHeight() - 1) + "," + (getDepth() - 1) + ")");
		}
	}

	default void enforceBounds(int x, int y, int z, int w, int h, int d)
	{
		if(x < 0 || x >= getWidth() || y < 0 || y >= getHeight() || z < 0 || z >= getDepth() || x + w < 0 || x + w > getWidth() || y + h < 0 || y + h > getHeight() || z + d < 0 || z + d > getDepth())
		{
			Iris.warn("The hunk " + w + "," + h + "," + d + " with an offset of " + x + "," + y + "," + z + " does not fit within the parent hunk " + getWidth() + "," + getHeight() + "," + getDepth() + " (0,0,0 to " + (getWidth() - 1) + "," + (getHeight() - 1) + "," + (getDepth() - 1) + ")");
		}
	}

	/**
	 * Create a new hunk from a section of this hunk.
	 * 
	 * 
	 * @param x1
	 *            The min x (inclusive)
	 * @param y1
	 *            The min y (inclusive)
	 * @param z1
	 *            The min z (inclusive)
	 * @param x2
	 *            The max x (exclusive)
	 * @param y2
	 *            The max y (exclusive)
	 * @param z2
	 *            The max z (exclusive)
	 * @return the new hunk (x2-x1, y2-y1, z2-z1)
	 */
	default ArrayHunk<T> crop(int x1, int y1, int z1, int x2, int y2, int z2)
	{
		ArrayHunk<T> h = new ArrayHunk<T>(x2 - x1, y2 - y1, z2 - z1);
		enforceBounds(x1, y1, z1, x2 - x1, y2 - y1, z2 - z1);

		for(int i = x1; i < x2; i++)
		{
			for(int j = y1; j < y2; j++)
			{
				for(int k = z1; k < z2; k++)
				{
					h.setRaw(i - x1, j - y1, k - z1, getRaw(i, j, k));
				}
			}
		}

		return h;
	}

	/**
	 * Create a new view of this same hunk from a section of this hunk.
	 * Modifications are routed to this hunk!
	 * 
	 * @param x1
	 *            The min x (inclusive)
	 * @param y1
	 *            The min y (inclusive)
	 * @param z1
	 *            The min z (inclusive)
	 * @param x2
	 *            The max x (exclusive)
	 * @param y2
	 *            The max y (exclusive)
	 * @param z2
	 *            The max z (exclusive)
	 * @return the cropped view of this hunk (x2-x1, y2-y1, z2-z1)
	 */
	default Hunk<T> croppedView(int x1, int y1, int z1, int x2, int y2, int z2)
	{
		enforceBounds(x1, y1, z1, x2 - x1, y2 - y1, z2 - z1);
		return new HunkView<T>(this, x2 - x1, y2 - y1, z2 - z1, x1, y1, z1);
	}

	/**
	 * @return The X length
	 */
	public int getWidth();

	/**
	 * @return The Z length
	 */
	public int getDepth();

	/**
	 * @return The Y length
	 */
	public int getHeight();

	/**
	 * Set a region
	 * 
	 * @param x1
	 *            inclusive 1st x
	 * @param y1
	 *            inclusive 1st y
	 * @param z1
	 *            inclusive 1st z
	 * @param x2
	 *            inclusive 2nd x
	 * @param y2
	 *            inclusive 2nd y
	 * @param z2
	 *            inclusive 2nd z
	 * @param t
	 *            the value to set
	 */
	default void set(int x1, int y1, int z1, int x2, int y2, int z2, T t)
	{
		enforceBounds(x1, y1, z1, x2 - x1, y2 - y1, z2 - z1);
		for(int i = x1; i <= x2; i++)
		{
			for(int j = y1; j <= y2; j++)
			{
				for(int k = z1; k <= z2; k++)
				{
					setRaw(i, j, k, t);
				}
			}
		}
	}

	/**
	 * Get the value to the closest valid position
	 * 
	 * @param x
	 *            the x
	 * @param y
	 *            the y
	 * @param z
	 *            the z
	 * @return the value closest to the border of the hunk
	 */
	default T getClosest(int x, int y, int z)
	{
		return getRaw(x >= getWidth() ? getWidth() - 1 : x < 0 ? 0 : x, y >= getHeight() ? getHeight() - 1 : y < 0 ? 0 : y, z >= getDepth() ? getDepth() - 1 : z < 0 ? 0 : z);
	}

	default void fill(T t)
	{
		set(0, 0, 0, getWidth() - 1, getHeight() - 1, getDepth() - 1, t);
	}

	/**
	 * Get a 1 node thick hunk representing the face of this hunk
	 * 
	 * @param f
	 *            the face
	 * @return the hunk view of this hunk
	 */
	default Hunk<T> viewFace(HunkFace f)
	{
		switch(f)
		{
			case BOTTOM:
				return croppedView(0, 0, 0, getWidth() - 1, 0, getDepth() - 1);
			case EAST:
				return croppedView(getWidth() - 1, 0, 0, getWidth() - 1, getHeight() - 1, getDepth() - 1);
			case NORTH:
				return croppedView(0, 0, 0, getWidth() - 1, getHeight() - 1, 0);
			case SOUTH:
				return croppedView(0, 0, 0, 0, getHeight() - 1, getDepth() - 1);
			case TOP:
				return croppedView(0, getHeight() - 1, 0, getWidth() - 1, getHeight() - 1, getDepth() - 1);
			case WEST:
				return croppedView(0, 0, getDepth() - 1, getWidth() - 1, getHeight() - 1, getDepth() - 1);
			default:
				break;
		}

		return null;
	}

	/**
	 * Crop (copy) a 1 node thick hunk representing the face of this hunk
	 * 
	 * @param f
	 *            the face
	 * @return the hunk copy (face) of this hunk
	 */
	default Hunk<T> cropFace(HunkFace f)
	{
		switch(f)
		{
			case BOTTOM:
				return crop(0, 0, 0, getWidth() - 1, 0, getDepth() - 1);
			case EAST:
				return crop(getWidth() - 1, 0, 0, getWidth() - 1, getHeight() - 1, getDepth() - 1);
			case NORTH:
				return crop(0, 0, 0, getWidth() - 1, getHeight() - 1, 0);
			case SOUTH:
				return crop(0, 0, 0, 0, getHeight() - 1, getDepth() - 1);
			case TOP:
				return crop(0, getHeight() - 1, 0, getWidth() - 1, getHeight() - 1, getDepth() - 1);
			case WEST:
				return crop(0, 0, getDepth() - 1, getWidth() - 1, getHeight() - 1, getDepth() - 1);
			default:
				break;
		}

		return null;
	}

	/**
	 * Set a value at the given position
	 * 
	 * @param x
	 *            the x
	 * @param y
	 *            the y
	 * @param z
	 *            the z
	 * @param t
	 *            the value
	 */
	default void set(int x, int y, int z, T t)
	{
		enforceBounds(x, y, z);
		setRaw(x, y, z, t);
	}

	default void setIfExists(int x, int y, int z, T t)
	{
		if(x < 0 || x >= getWidth() || y < 0 || y >= getHeight() || z < 0 || z >= getDepth())
		{
			return;
		}

		setRaw(x, y, z, t);
	}

	default T getIfExists(int x, int y, int z, T t)
	{
		if(x < 0 || x >= getWidth() || y < 0 || y >= getHeight() || z < 0 || z >= getDepth())
		{
			return t;
		}

		return getOr(x, y, z, t);
	}

	default T getIfExists(int x, int y, int z)
	{
		return getIfExists(x, y, z, null);
	}

	/**
	 * Set a value at the given position without checking coordinate bounds
	 * 
	 * @param x
	 *            the x
	 * @param y
	 *            the y
	 * @param z
	 *            the z
	 * @param t
	 *            the value
	 */
	public void setRaw(int x, int y, int z, T t);

	/**
	 * Get a value at the given position without checking coordinate bounds
	 * 
	 * @param x
	 *            the x
	 * @param y
	 *            the y
	 * @param z
	 *            the z
	 * @return the value or null
	 */
	public T getRaw(int x, int y, int z);

	/**
	 * Get a value at the given position
	 * 
	 * @param x
	 *            the x
	 * @param y
	 *            the y
	 * @param z
	 *            the z
	 * @return the value or null
	 */
	default T get(int x, int y, int z)
	{
		enforceBounds(x, y, z);
		return getRaw(x, y, z);
	}

	default T getOr(int x, int y, int z, T t)
	{
		enforceBounds(x, y, z);
		T v = getRaw(x, y, z);

		if(v == null)
		{
			return t;
		}

		return v;
	}

	/**
	 * Insert a hunk into this one with an offset the inserted hunk
	 * 
	 * @param offX
	 *            the offset from zero for x
	 * @param offY
	 *            the offset from zero for y
	 * @param offZ
	 *            the offset from zero for z
	 * @param hunk
	 *            the hunk to insert
	 */
	default void insert(int offX, int offY, int offZ, Hunk<T> hunk)
	{
		insert(offX, offY, offZ, hunk, false);
	}

	default void insertSoftly(int offX, int offY, int offZ, Hunk<T> hunk, Predicate<T> shouldOverwrite)
	{
		insertSoftly(offX, offY, offZ, hunk, false, shouldOverwrite);
	}

	/**
	 * Insert a hunk into this one
	 * 
	 * @param hunk
	 *            the hunk to insert
	 */
	default void insert(Hunk<T> hunk)
	{
		insert(0, 0, 0, hunk, false);
	}

	/**
	 * Returns the source of this hunk view. This could return another hunk view,
	 * not an actual source, however it does return it's underlying data source. If
	 * this hunk is a data source and not a view, it will return null.
	 * 
	 * @return the source or null if this is already the source
	 */
	default Hunk<T> getSource()
	{
		return null;
	}

	/**
	 * Insert a hunk into this one
	 * 
	 * @param hunk
	 *            the hunk to insert
	 * @param inverted
	 *            invert the inserted hunk or not
	 */
	default void insert(Hunk<T> hunk, boolean inverted)
	{
		insert(0, 0, 0, hunk, inverted);
	}

	/**
	 * Insert a hunk into this one with an offset and possibly inverting the y of
	 * the inserted hunk
	 *
	 * @param offX
	 *            the offset from zero for x
	 * @param offY
	 *            the offset from zero for y
	 * @param offZ
	 *            the offset from zero for z
	 * @param hunk
	 *            the hunk to insert
	 * @param invertY
	 *            should the inserted hunk be inverted
	 */
	default void insert(int offX, int offY, int offZ, Hunk<T> hunk, boolean invertY)
	{
		enforceBounds(offX, offY, offZ, hunk.getWidth(), hunk.getHeight(), hunk.getDepth());

		for(int i = offX; i < offX + hunk.getWidth(); i++)
		{
			for(int j = offY; j < offY + hunk.getHeight(); j++)
			{
				for(int k = offZ; k < offZ + hunk.getDepth(); k++)
				{
					setRaw(i, j, k, hunk.getRaw(i - offX, j - offY, k - offZ));
				}
			}
		}
	}

	/**
	 * Insert a hunk into this one with an offset and possibly inverting the y of. Will never insert a node if its already used
	 * the inserted hunk
	 *
	 * @param offX
	 *            the offset from zero for x
	 * @param offY
	 *            the offset from zero for y
	 * @param offZ
	 *            the offset from zero for z
	 * @param hunk
	 *            the hunk to insert
	 * @param invertY
	 *            should the inserted hunk be inverted
	 */
	default void insertSoftly(int offX, int offY, int offZ, Hunk<T> hunk, boolean invertY, Predicate<T> shouldOverwrite)
	{
		enforceBounds(offX, offY, offZ, hunk.getWidth(), hunk.getHeight(), hunk.getDepth());

		for(int i = offX; i < offX + hunk.getWidth(); i++)
		{
			for(int j = offY; j < offY + hunk.getHeight(); j++)
			{
				for(int k = offZ; k < offZ + hunk.getDepth(); k++)
				{
					if(shouldOverwrite.test(getRaw(i, j, k)))
					{
						setRaw(i, j, k, hunk.getRaw(i - offX, j - offY, k - offZ));
					}
				}
			}
		}
	}

	/**
	 * Acts like fill, however if used by a mapped hunk, will simply clear it
	 * @param b the data to use for fill
	 */
	default void empty(T b)
	{
		fill(b);
	}
}
