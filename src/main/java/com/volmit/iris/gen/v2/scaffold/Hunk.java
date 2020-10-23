package com.volmit.iris.gen.v2.scaffold;

public interface Hunk<T>
{
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
	 * @return the new hunk (x2-x1, y2-y1, z2-z1)
	 */
	public Hunk<T> croppedView(int x1, int y1, int z1, int x2, int y2, int z2);

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
	public Hunk<T> crop(int x1, int y1, int z1, int x2, int y2, int z2);

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
		insert(0, 0, 0, hunk, false);
	}

	/**
	 * Insert a hunk into this one
	 * 
	 * @param hunk
	 *            the hunk to insert
	 */
	default void insert(Hunk<T> hunk)
	{
		insert(hunk, false);
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
	public void insert(int offX, int offY, int offZ, Hunk<T> hunk, boolean invertY);

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
	public void set(int x1, int y1, int z1, int x2, int y2, int z2, T t);

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
	public void set(int x, int y, int z, T t);

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
	public T get(int x, int y, int z);

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
	public T getClosest(int x, int y, int z);

	public void fill(T t);
}
