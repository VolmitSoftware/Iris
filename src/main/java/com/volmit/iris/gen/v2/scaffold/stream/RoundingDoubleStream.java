package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.BasicLayer;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;

public class RoundingDoubleStream extends BasicLayer implements ProceduralStream<Double>
{
	private final ProceduralStream<?> stream;

	public RoundingDoubleStream(ProceduralStream<?> stream)
	{
		super();
		this.stream = stream;
	}

	@Override
	public double toDouble(Double t)
	{
		return t.doubleValue();
	}

	@Override
	public Double fromDouble(double d)
	{
		return (double) Math.round(d);
	}

	private double round(double v)
	{
		return Math.round(v);
	}

	@Override
	public Double get(double x, double z)
	{
		return round(stream.getDouble(x, z));
	}

	@Override
	public Double get(double x, double y, double z)
	{
		return round(stream.getDouble(x, y, z));
	}

}
