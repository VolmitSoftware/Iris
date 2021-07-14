package com.volmit.iris.generator.noise;

public interface NoiseGenerator {
    double noise(double x);

    double noise(double x, double z);

    double noise(double x, double y, double z);

    default boolean isStatic() {
        return false;
    }
}
