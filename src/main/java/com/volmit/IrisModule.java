package com.volmit;

public interface IrisModule {

    String name();

    String displayVersion();
    /** Called during registration, before settings are decoded or generation starts. */
    void register(FeatureRegistry registry);
}
