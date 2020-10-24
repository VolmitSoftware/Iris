package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;

public class InterpolatorFactory<T>
{
	private final ProceduralStream<T> stream;

	public InterpolatorFactory(ProceduralStream<T> stream)
	{
		this.stream = stream;
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

	public StarcastStream<T> starcast(int radius, int checks)
	{
		return new StarcastStream<>(stream, radius, checks);
	}
	
	public StarcastStream<T> starcast3(int radius)
	{
		return starcast(radius, 3);
	}
	
	public StarcastStream<T> starcast6(int radius)
	{
		return starcast(radius, 6);
	}
	
	public StarcastStream<T> starcast9(int radius)
	{
		return starcast(radius, 9);
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
