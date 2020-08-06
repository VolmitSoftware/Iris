package com.volmit.iris.util;

@FunctionalInterface
public interface Consumer2<A, B>
{
	public void accept(A a, B b);
}
