package com.volmit.iris.util;

@FunctionalInterface
public interface NoiseProvider {
    double noise(double x, double z);
}