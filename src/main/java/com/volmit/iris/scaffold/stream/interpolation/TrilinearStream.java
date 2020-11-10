package com.volmit.iris.scaffold.stream.interpolation;

import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;
import com.volmit.iris.util.IrisInterpolation;

public class TrilinearStream<T> extends BasicStream<T> implements Interpolator<T>
{
	private final int rx;
	private final int ry;
	private final int rz;

	public TrilinearStream(ProceduralStream<T> stream, int rx, int ry, int rz)
	{
		super(stream);
		this.rx = rx;
		this.ry = ry;
		this.rz = rz;
	}

	public T interpolate(double x, double y, double z)
	{
		int fx = (int) Math.floor(x / rx);
		int fy = (int) Math.floor(y / ry);
		int fz = (int) Math.floor(z / rz);
		int x1 = (int) Math.round(fx * rx);
		int y1 = (int) Math.round(fy * ry);
		int z1 = (int) Math.round(fz * rz);
		int x2 = (int) Math.round((fx + 1) * rx);
		int y2 = (int) Math.round((fy + 1) * ry);
		int z2 = (int) Math.round((fz + 1) * rz);
		double px = IrisInterpolation.rangeScale(0, 1, x1, x2, x);
		double py = IrisInterpolation.rangeScale(0, 1, y1, y2, y);
		double pz = IrisInterpolation.rangeScale(0, 1, z1, z2, z);

		//@builder
		return getTypedSource().fromDouble(IrisInterpolation.trilerp(
				getTypedSource().getDouble(x1, y1, z1),
				getTypedSource().getDouble(x2, y1, z1),
				getTypedSource().getDouble(x1, y1, z2),
				getTypedSource().getDouble(x2, y1, z2),
				getTypedSource().getDouble(x1, y2, z1),
				getTypedSource().getDouble(x2, y2, z1),
				getTypedSource().getDouble(x1, y2, z2),
				getTypedSource().getDouble(x2, y2, z2),
				px, pz, py));
		//@done
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
		return interpolate(x, 0, z);
	}

	@Override
	public T get(double x, double y, double z)
	{
		return interpolate(x, y, z);
	}
}
