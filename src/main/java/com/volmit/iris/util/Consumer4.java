package com.volmit.iris.util;

@SuppressWarnings("hiding")
@FunctionalInterface
public interface Consumer4<A, B, C, D> {
    void accept(A a, B b, C c, D d);
}
