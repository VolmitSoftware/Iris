package com.volmit.iris.scaffold.stream.sources;

import com.volmit.iris.scaffold.stream.BasicLayer;
import com.volmit.iris.scaffold.stream.ProceduralStream;
import com.volmit.iris.generator.noise.CNG;

public class CNGStream extends BasicLayer implements ProceduralStream<Double>
{
	private final CNG cng;

	public CNGStream(CNG cng)
	{
		this.cng = cng;
	}

	public CNGStream(CNG cng, double zoom, double offsetX, double offsetY, double offsetZ)
	{
		super(1337, zoom, offsetX, offsetY, offsetZ);
		this.cng = cng;
	}

	public CNGStream(CNG cng, double zoom)
	{
		super(1337, zoom);
		this.cng = cng;
	}

	@Override
	public double toDouble(Double t)
	{
		return t;
	}

	@Override
	public Double fromDouble(double d)
	{
		return d;
	}

	@Override
	public ProceduralStream<Double> getTypedSource() {
		return null;
	}

	@Override
	public ProceduralStream<?> getSource() {
		return null;
	}

	@Override
	public Double get(double x, double z)
	{
		return cng.noise((x + getOffsetX()) / getZoom(), (z + getOffsetZ()) / getZoom());
	}

	@Override
	public Double get(double x, double y, double z)
	{
		return cng.noise((x + getOffsetX()) / getZoom(), (y + getOffsetY()) / getZoom(), (z + getOffsetZ()) * getZoom());
	}

}
