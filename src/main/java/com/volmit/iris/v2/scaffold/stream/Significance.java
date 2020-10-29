package com.volmit.iris.v2.scaffold.stream;

import com.volmit.iris.util.KList;

public interface Significance<T>
{
	public KList<T> getFactorTypes();

	public double getSignificance(T t);

	public T getMostSignificantType();
}
