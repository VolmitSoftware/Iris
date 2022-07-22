package com.volmit.iris.engine.feature;

@FunctionalInterface
public interface FeatureTaskTiming {
    void onCompleted(double ms);
}
