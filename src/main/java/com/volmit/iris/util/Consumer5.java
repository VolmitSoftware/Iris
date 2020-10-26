package com.volmit.iris.util;

@SuppressWarnings("hiding")
@FunctionalInterface
public interface Consumer5<A, B, C, D, E>
{
	public void accept(A a, B b, C c, D d, E e);
}
