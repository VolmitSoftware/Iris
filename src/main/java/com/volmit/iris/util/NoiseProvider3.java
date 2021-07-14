package com.volmit.iris.util;

@FunctionalInterface
public interface NoiseProvider3 {
    double noise(double x, double y, double z);
}