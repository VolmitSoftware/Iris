package com.volmit.mantle;

@FunctionalInterface
public interface MantleVisitor<T> {
    void accept(int x, int y, int z, T value);
}
