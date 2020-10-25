package com.volmit.iris.gen.v2.scaffold;

import com.volmit.iris.util.KList;

public interface Significance<T>
{
	public KList<T> getFactorTypes();

	public double getSignificance(T t);

	public T getMostSignificantType();
}
