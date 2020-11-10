package com.volmit.iris.scaffold.stream.convert;

import com.volmit.iris.scaffold.stream.ArraySignificance;
import com.volmit.iris.scaffold.stream.Significance;
import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;
import com.volmit.iris.util.KList;

public class SignificanceStream<K extends Significance<T>, T> extends BasicStream<K>
{
	private final ProceduralStream<T> stream;
	private final double radius;
	private final int checks;

	public SignificanceStream(ProceduralStream<T> stream, double radius, int checks)
	{
		super();
		this.stream = stream;
		this.radius = radius;
		this.checks = checks;
	}

	@Override
	public double toDouble(K t)
	{
		return 0;
	}

	@Override
	public K fromDouble(double d)
	{
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public K get(double x, double z)
	{
		KList<T> ke = new KList<>(8);
		KList<Double> va = new KList<Double>(8);

		double m = (360 / checks);
		double v = 0;

		for(int i = 0; i < 360; i += m)
		{
			double sin = Math.sin(Math.toRadians(i));
			double cos = Math.cos(Math.toRadians(i));
			double cx = x + ((radius * cos) - (radius * sin));
			double cz = z + ((radius * sin) + (radius * cos));
			T t = stream.get(cx, cz);

			if(ke.addIfMissing(t))
			{
				va.add(1D);
				v++;
			}

			else
			{
				int ind = ke.indexOf(t);
				va.set(ind, va.get(ind) + 1D);
			}
		}

		for(int i = 0; i < va.size(); i++)
		{
			va.set(i, va.get(i) / (double) v);
		}

		return (K) new ArraySignificance<T>(ke, va);
	}

	@Override
	public K get(double x, double y, double z)
	{
		return get(x, z);
	}
}
