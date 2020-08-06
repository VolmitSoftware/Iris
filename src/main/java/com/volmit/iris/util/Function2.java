package com.volmit.iris.util;

@SuppressWarnings("hiding")
@FunctionalInterface
public interface Function2<A, B, R>
{
	public R apply(A a, B b);
}
