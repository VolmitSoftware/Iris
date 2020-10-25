package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;
import com.volmit.iris.object.InterpolationMethod;
import com.volmit.iris.util.IrisInterpolation;
import com.volmit.iris.util.NoiseProvider;

public class InterpolatingStream<T> extends BasicLayer implements ProceduralStream<T>, Interpolator<T>
{
	private final ProceduralStream<T> stream;
	private final InterpolationMethod type;
	private final NoiseProvider np;
	private final int rx;

	public InterpolatingStream(ProceduralStream<T> stream, int rx, InterpolationMethod type)
	{
		this.type = type;
		this.stream = stream;
		this.rx = rx;
		this.np = (xf, zf) -> stream.getDouble(xf, zf);
	}

	public T interpolate(double x, double y)
	{
		return fromDouble(IrisInterpolation.getNoise(type, (int) x, (int) y, (double) rx, np));
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
