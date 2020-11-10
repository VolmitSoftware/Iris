package com.volmit.iris.scaffold.stream.interpolation;

import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;

public class BiStarcastStream<T> extends BasicStream<T> implements Interpolator<T>
{
	private int rad;
	private int checks;

	public BiStarcastStream(ProceduralStream<T> stream, int rad, int checks)
	{
		super(stream);
		this.rad = rad;
		this.checks = checks;
	}

	public T interpolate(double x, double y)
	{
		double m = (360D / checks);
		double v = 0;

		for(int i = 0; i < 360; i += m)
		{
			double sin = Math.sin(Math.toRadians(i));
			double cos = Math.cos(Math.toRadians(i));
			double cx = x + ((rad * cos) - (rad * sin));
			double cz = y + ((rad * sin) + (rad * cos));
			v += getTypedSource().getDouble(cx, cz);
		}

		return getTypedSource().fromDouble(v / checks);
	}

	@Override
	public double toDouble(T t)
	{
		return getTypedSource().toDouble(t);
	}

	@Override
	public T fromDouble(double d)
	{
		return getTypedSource().fromDouble(d);
	}

	@Override
	public T get(double x, double z)
	{
		return interpolate(x, z);
	}

	@Override
	public T get(double x, double y, double z)
	{
		return interpolate(x, z);
	}
}
