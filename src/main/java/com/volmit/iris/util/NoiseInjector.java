package com.volmit.iris.util;

@FunctionalInterface
public interface NoiseInjector {
    double[] combine(double src, double value);
}
