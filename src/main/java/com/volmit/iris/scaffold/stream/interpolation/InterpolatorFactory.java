package com.volmit.iris.scaffold.stream.interpolation;

import com.volmit.iris.scaffold.stream.ProceduralStream;
import com.volmit.iris.object.InterpolationMethod;

public class InterpolatorFactory<T>
{
	private final ProceduralStream<T> stream;

	public InterpolatorFactory(ProceduralStream<T> stream)
	{
		this.stream = stream;
	}

	public InterpolatingStream<T> with(InterpolationMethod t, int rx)
	{
		return new InterpolatingStream<T>(stream, rx, t);
	}

	public TrilinearStream<T> trilinear(int rx, int ry, int rz)
	{
		return new TrilinearStream<>(stream, rx, ry, rz);
	}

	public TricubicStream<T> tricubic(int rx, int ry, int rz)
	{
		return new TricubicStream<>(stream, rx, ry, rz);
	}

	public BicubicStream<T> bicubic(int rx, int ry)
	{
		return new BicubicStream<>(stream, rx, ry);
	}

	public BicubicStream<T> bicubic(int r)
	{
		return bicubic(r, r);
	}

	public BilinearStream<T> bilinear(int rx, int ry)
	{
		return new BilinearStream<>(stream, rx, ry);
	}

	public BilinearStream<T> bilinear(int r)
	{
		return bilinear(r, r);
	}

	public TriStarcastStream<T> tristarcast(int radius, int checks)
	{
		return new TriStarcastStream<>(stream, radius, checks);
	}

	public TriStarcastStream<T> tristarcast3(int radius)
	{
		return tristarcast(radius, 3);
	}

	public TriStarcastStream<T> tristarcast6(int radius)
	{
		return tristarcast(radius, 6);
	}

	public TriStarcastStream<T> tristarcast9(int radius)
	{
		return tristarcast(radius, 9);
	}

	public BiStarcastStream<T> bistarcast(int radius, int checks)
	{
		return new BiStarcastStream<>(stream, radius, checks);
	}

	public BiStarcastStream<T> bistarcast3(int radius)
	{
		return bistarcast(radius, 3);
	}

	public BiStarcastStream<T> bistarcast6(int radius)
	{
		return bistarcast(radius, 6);
	}

	public BiStarcastStream<T> bistarcast9(int radius)
	{
		return bistarcast(radius, 9);
	}

	public BiHermiteStream<T> bihermite(int rx, int ry, double tension, double bias)
	{
		return new BiHermiteStream<>(stream, rx, ry, tension, bias);
	}

	public BiHermiteStream<T> bihermite(int rx, int ry)
	{
		return new BiHermiteStream<>(stream, rx, ry);
	}

	public BiHermiteStream<T> bihermite(int r)
	{
		return bihermite(r, r);
	}

	public BiHermiteStream<T> bihermite(int r, double tension, double bias)
	{
		return bihermite(r, r, tension, bias);
	}
}
