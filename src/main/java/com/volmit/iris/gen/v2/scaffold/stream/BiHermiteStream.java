package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;
import com.volmit.iris.util.IrisInterpolation;

public class BiHermiteStream<T> extends BasicLayer implements ProceduralStream<T>, Interpolator<T>
{
	private final ProceduralStream<T> stream;
	private final int rx;
	private final int ry;
	private final double tension;
	private final double bias;

	public BiHermiteStream(ProceduralStream<T> stream, int rx, int ry, double tension, double bias)
	{
		this.stream = stream;
		this.rx = rx;
		this.ry = ry;
		this.tension = tension;
		this.bias = bias;
	}

	public BiHermiteStream(ProceduralStream<T> stream, int rx, int ry)
	{
		this(stream, rx, ry, 0.5, 0);
	}

	public T interpolate(double x, double y)
	{
		int fx = (int) Math.floor(x / rx);
		int fz = (int) Math.floor(y / ry);
		int x0 = (int) Math.round((fx - 1) * rx);
		int z0 = (int) Math.round((fz - 1) * ry);
		int x1 = (int) Math.round(fx * rx);
		int z1 = (int) Math.round(fz * ry);
		int x2 = (int) Math.round((fx + 1) * rx);
		int z2 = (int) Math.round((fz + 1) * ry);
		int x3 = (int) Math.round((fx + 2) * rx);
		int z3 = (int) Math.round((fz + 2) * ry);
		double px = IrisInterpolation.rangeScale(0, 1, x1, x2, x);
		double pz = IrisInterpolation.rangeScale(0, 1, z1, z2, y);

		//@builder
		return stream.fromDouble(IrisInterpolation.bihermite(
				stream.getDouble(x0, z0), 
				stream.getDouble(x0, z1), 
				stream.getDouble(x0, z2), 
				stream.getDouble(x0, z3), 
				stream.getDouble(x1, z0), 
				stream.getDouble(x1, z1), 
				stream.getDouble(x1, z2), 
				stream.getDouble(x1, z3), 
				stream.getDouble(x2, z0), 
				stream.getDouble(x2, z1), 
				stream.getDouble(x2, z2), 
				stream.getDouble(x2, z3), 
				stream.getDouble(x3, z0), 
				stream.getDouble(x3, z1), 
				stream.getDouble(x3, z2), 
				stream.getDouble(x3, z3), 
				px, pz, tension, bias));
		//@done
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
