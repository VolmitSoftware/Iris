package com.volmit.iris.util;

@SuppressWarnings("hiding")
@FunctionalInterface
public interface Function4<A, B, C, D, R>
{
	public R apply(A a, B b, C c, D d);
}
