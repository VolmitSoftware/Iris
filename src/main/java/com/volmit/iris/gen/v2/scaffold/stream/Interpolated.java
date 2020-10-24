package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;

public interface Interpolated<T>
{
	public double toDouble(T t);

	public T fromDouble(double d);

	default InterpolatorFactory<T> interpolate()
	{
		if(this instanceof ProceduralStream)
		{
			return new InterpolatorFactory<T>((ProceduralStream<T>) this);
		}

		return null;
	}
}
