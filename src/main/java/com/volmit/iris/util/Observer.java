package com.volmit.iris.util;

@FunctionalInterface
public interface Observer<T>
{
	public void onChanged(T from, T to);
}
