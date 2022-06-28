package com.volmit.iris.engine.feature;

@FunctionalInterface
public interface IrisFeatureTaskTiming {
    void onCompleted(double ms);
}
