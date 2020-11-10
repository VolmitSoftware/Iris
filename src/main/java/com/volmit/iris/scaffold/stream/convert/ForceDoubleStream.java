package com.volmit.iris.scaffold.stream.convert;

import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;

public class ForceDoubleStream extends BasicStream<Double>
{
	private ProceduralStream<?> stream;

	public ForceDoubleStream(ProceduralStream<?> stream)
	{
		super(null);
	}

	@Override
	public double toDouble(Double t)
	{
		return t;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Double fromDouble(double d)
	{
		return d;
	}

	@Override
	public Double get(double x, double z)
	{
		return stream.getDouble(x, z);
	}

	@Override
	public Double get(double x, double y, double z)
	{
		return stream.getDouble(x, y, z);
	}

}
