package com.volmit.iris.scaffold.stream.interpolation;

import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;
import com.volmit.iris.object.InterpolationMethod;
import com.volmit.iris.util.IrisInterpolation;
import com.volmit.iris.util.NoiseProvider;

public class InterpolatingStream<T> extends BasicStream<T> implements Interpolator<T>
{
	private final InterpolationMethod type;
	private final NoiseProvider np;
	private final int rx;

	public InterpolatingStream(ProceduralStream<T> stream, int rx, InterpolationMethod type)
	{
		super(stream);
		this.type = type;
		this.rx = rx;
		this.np = (xf, zf) -> getTypedSource().getDouble(xf, zf);
	}

	public T interpolate(double x, double y)
	{
		return fromDouble(IrisInterpolation.getNoise(type, (int) x, (int) y, (double) rx, np));
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
