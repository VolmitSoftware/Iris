package com.volmit.iris.util;

@FunctionalInterface
public interface Function2<A, B, R>
{
	public R apply(A a, B b);
}
