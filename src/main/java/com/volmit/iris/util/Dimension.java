package com.volmit.iris.util;

/**
 * Dimensions
 *
 * @author cyberpwn
 */
public class Dimension
{
	private final int width;
	private final int height;
	private final int depth;

	/**
	 * Make a dimension
	 *
	 * @param width
	 *            width of this (X)
	 * @param height
	 *            the height (Y)
	 * @param depth
	 *            the depth (Z)
	 */
	public Dimension(int width, int height, int depth)
	{
		this.width = width;
		this.height = height;
		this.depth = depth;
	}

	/**
	 * Make a dimension
	 *
	 * @param width
	 *            width of this (X)
	 * @param height
	 *            the height (Y)
	 */
	public Dimension(int width, int height)
	{
		this.width = width;
		this.height = height;
		this.depth = 0;
	}

	/**
	 * Get the direction of the flat part of this dimension (null if no thin
	 * face)
	 *
	 * @return the direction of the flat pane or null
	 */
	public DimensionFace getPane()
	{
		if(width == 1)
		{
			return DimensionFace.X;
		}

		if(height == 1)
		{
			return DimensionFace.Y;
		}

		if(depth == 1)
		{
			return DimensionFace.Z;
		}

		return null;
	}

	public int getWidth()
	{
		return width;
	}

	public int getHeight()
	{
		return height;
	}

	public int getDepth()
	{
		return depth;
	}
}