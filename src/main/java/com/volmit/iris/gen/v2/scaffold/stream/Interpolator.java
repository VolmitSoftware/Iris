package com.volmit.iris.gen.v2.scaffold.stream;

import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;

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
