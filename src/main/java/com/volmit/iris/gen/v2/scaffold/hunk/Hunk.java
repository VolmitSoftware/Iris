package com.volmit.iris.gen.v2.scaffold.hunk;

import java.util.function.Consumer;

import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.gen.v2.scaffold.multicore.BurstExecutor;
import com.volmit.iris.gen.v2.scaffold.multicore.MultiBurst;
import com.volmit.iris.util.Consumer2;
import com.volmit.iris.util.Consumer3;
import com.volmit.iris.util.Consumer4;
import com.volmit.iris.util.Consumer5;
import com.volmit.iris.util.Function3;
import com.volmit.iris.util.KList;

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

	public static Hunk<BlockData> view(ChunkData src)
	{
		return new ChunkDataHunkView(src);
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

	default Hunk<T> iterate(Consumer3<Integer, Integer, Integer> c)
	{
		for(int i = 0; i < getWidth(); i++)
		{
			for(int j = 0; j < getWidth(); j++)
			{
				for(int k = 0; k < getWidth(); k++)
				{
					c.accept(i, j, k);
				}
			}
		}

		return this;
	}

	default Hunk<T> iterate(Consumer4<Integer, Integer, Integer, T> c)
	{
		for(int i = 0; i < getWidth(); i++)
		{
			for(int j = 0; j < getWidth(); j++)
			{
				for(int k = 0; k < getWidth(); k++)
				{
					c.accept(i, j, k, get(i, j, k));
				}
			}
		}

		return this;
	}

	default Hunk<T> compute3D(int parallelism, Consumer4<Integer, Integer, Integer, Hunk<T>> v)
	{
		BurstExecutor e = MultiBurst.burst.burst(parallelism);
		KList<Runnable> rq = new KList<Runnable>(parallelism);
		getSections3D(parallelism, (xx, yy, zz, h, r) -> e.queue(() ->
		{
			v.accept(xx, yy, zz, h);
			synchronized(rq)
			{
				rq.add(r);
			}
		}), (xx, yy, zz, c) -> insert(xx, yy, zz, c));
		e.complete();
		rq.forEach(Runnable::run);
		return this;
	}

	default Hunk<T> compute2D(int parallelism, Consumer4<Integer, Integer, Integer, Hunk<T>> v)
	{
		BurstExecutor e = MultiBurst.burst.burst(parallelism);
		KList<Runnable> rq = new KList<Runnable>(parallelism);
		getSections2D(parallelism, (xx, yy, zz, h, r) -> e.queue(() ->
		{
			v.accept(xx, yy, zz, h);
			synchronized(rq)
			{
				rq.add(r);
			}
		}), (xx, yy, zz, c) -> insert(xx, yy, zz, c));
		e.complete();
		rq.forEach(Runnable::run);
		return this;
	}

	default Hunk<T> compute2DAtomically(int parallelism, Consumer4<Integer, Integer, Integer, Hunk<T>> v)
	{
		BurstExecutor e = MultiBurst.burst.burst(parallelism);
		KList<Runnable> rq = new KList<Runnable>(parallelism);
		getAtomicSections2D(parallelism, (xx, yy, zz, h) -> e.queue(() -> v.accept(xx, yy, zz, h)));
		e.complete();
		rq.forEach(Runnable::run);
		return this;
	}

	default Hunk<T> compute3DAtomically(int parallelism, Consumer4<Integer, Integer, Integer, Hunk<T>> v)
	{
		BurstExecutor e = MultiBurst.burst.burst(parallelism);
		KList<Runnable> rq = new KList<Runnable>(parallelism);
		getAtomicSections3D(parallelism, (xx, yy, zz, h) -> e.queue(() -> v.accept(xx, yy, zz, h)));
		e.complete();
		rq.forEach(Runnable::run);
		return this;
	}

	default Hunk<T> getSections2D(int sections, Consumer5<Integer, Integer, Integer, Hunk<T>, Runnable> v)
	{
		return getSections2D(sections, v, (xx, yy, zz, c) -> insert(xx, yy, zz, c));
	}

	default Hunk<T> getSections2D(int sections, Consumer5<Integer, Integer, Integer, Hunk<T>, Runnable> v, Consumer4<Integer, Integer, Integer, Hunk<T>> inserter)
	{
		int dim = (int) Math.ceil(Math.sqrt(sections));
		dim = dim < 2 ? 2 : dim;
		dim = dim % 2 != 0 ? dim + 1 : dim;
		int w = getWidth() / dim;
		int d = getDepth() / dim;
		int i, j;

		for(i = 0; i < getWidth(); i += w)
		{
			int ii = i;

			for(j = 0; j < getDepth(); j += d)
			{
				int jj = j;
				getSection(i, 0, j, i + w, getHeight(), j + d, (h, r) -> v.accept(ii, 0, jj, h, r), inserter);
			}
		}

		return this;
	}

	default Hunk<T> getAtomicSections2D(int sections, Consumer4<Integer, Integer, Integer, Hunk<T>> v)
	{
		int dim = (int) Math.ceil(Math.sqrt(sections));
		dim = dim < 2 ? 2 : dim;
		dim = dim % 2 != 0 ? dim + 1 : dim;
		int w = getWidth() / dim;
		int d = getDepth() / dim;
		int i, j;

		for(i = 0; i < getWidth(); i += w)
		{
			int ii = i;

			for(j = 0; j < getDepth(); j += d)
			{
				int jj = j;
				getAtomicSection(i, 0, j, i + w, getHeight(), j + d, (h) -> v.accept(ii, 0, jj, h));
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
		int dim = (int) Math.ceil(Math.cbrt(sections));
		dim = dim < 2 ? 2 : dim;
		dim = dim % 2 != 0 ? dim + 1 : dim;
		int w = getWidth() / dim;
		int h = getHeight() / dim;
		int d = getDepth() / dim;
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
					getSection(i, j, k, i + w, j + h, k + d, (hh, r) -> v.accept(ii, jj, kk, hh, r), inserter);
				}
			}
		}

		return this;
	}

	default Hunk<T> getAtomicSections3D(int sections, Consumer4<Integer, Integer, Integer, Hunk<T>> v)
	{
		int dim = (int) Math.ceil(Math.cbrt(sections));
		dim = dim < 2 ? 2 : dim;
		dim = dim % 2 != 0 ? dim + 1 : dim;
		int w = getWidth() / dim;
		int h = getHeight() / dim;
		int d = getDepth() / dim;
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
					getAtomicSection(i, j, k, i + w, j + h, k + d, (hh) -> v.accept(ii, jj, kk, hh));
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
		v.accept(croppedView(x, y, z, x1, y1, z1));
		return this;
	}

	default void enforceBounds(int x, int y, int z)
	{
		if(x < 0 || x >= getWidth() || y < 0 || y >= getHeight() || z < 0 || z >= getDepth())
		{
			throw new IndexOutOfBoundsException(x + "," + y + "," + z + " does not fit within size " + getWidth() + "," + getHeight() + "," + getDepth() + " (0,0,0 to " + (getWidth() - 1) + "," + (getHeight() - 1) + "," + (getDepth() - 1) + ")");
		}
	}

	default void enforceBounds(int x, int y, int z, int w, int h, int d)
	{
		if(x < 0 || x >= getWidth() || y < 0 || y >= getHeight() || z < 0 || z >= getDepth() || x + w < 0 || x + w > getWidth() || y + h < 0 || y + h > getHeight() || z + d < 0 || z + d > getDepth())
		{
			throw new IndexOutOfBoundsException("The hunk " + w + "," + h + "," + d + " with an offset of " + x + "," + y + "," + z + " does not fit within the parent hunk " + getWidth() + "," + getHeight() + "," + getDepth() + " (0,0,0 to " + (getWidth() - 1) + "," + (getHeight() - 1) + "," + (getDepth() - 1) + ")");
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
		return getRaw(x >= getWidth() ? getWidth() - 1 : x, y >= getHeight() ? getHeight() - 1 : y, z >= getDepth() ? getDepth() - 1 : z);
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
}
