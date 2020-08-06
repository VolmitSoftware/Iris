package com.volmit.iris.util;

@SuppressWarnings("hiding")
@FunctionalInterface
public interface Function3<A, B, C, R>
{
	public R apply(A a, B b, C c);
}
