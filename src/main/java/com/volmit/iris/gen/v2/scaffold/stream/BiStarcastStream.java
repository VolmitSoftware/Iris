package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;

public class StarcastStream<T> extends BasicLayer implements ProceduralStream<T>, Interpolator<T>
{
	private ProceduralStream<T> stream;
	private int rad;
	private int checks;

	public StarcastStream(ProceduralStream<T> stream, int rad, int checks)
	{
		this.stream = stream;
		this.rad = rad;
		this.checks = checks;
	}

	public T interpolate(double x, double y)
	{
		double m = (360 / checks);
		double v = 0;

		for(int i = 0; i < 360; i += m)
		{
			double sin = Math.sin(Math.toRadians(i));
			double cos = Math.cos(Math.toRadians(i));
			double cx = x + ((rad * cos) - (rad * sin));
			double cz = y + ((rad * sin) + (rad * cos));
			v += stream.getDouble(cx, cz);
		}

		return stream.fromDouble(v / checks);
	}

	@Override
	public double toDouble(T t)
	{
		return stream.toDouble(t);
	}

	@Override
	public T fromDouble(double d)
	{
		return stream.fromDouble(d);
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
