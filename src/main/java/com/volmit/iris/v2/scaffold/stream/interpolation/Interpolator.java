package com.volmit.iris.v2.scaffold.stream.interpolation;

import com.volmit.iris.v2.scaffold.stream.ProceduralStream;

public interface Interpolator<T>
{
	@SuppressWarnings("unchecked")
	default InterpolatorFactory<T> into()
	{
		if(this instanceof ProceduralStream)
		{
			return new InterpolatorFactory<T>((ProceduralStream<T>) this);
		}

		return null;
	}
}
