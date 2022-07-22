package com.volmit.iris.engine.pipeline;

import com.volmit.iris.engine.feature.FeatureTarget;
import com.volmit.iris.platform.PlatformNamespaced;

import java.util.HashMap;
import java.util.Map;

public class PipedHunkStack {
    private final Map<Class<? extends PlatformNamespaced>, FeatureTarget<? extends PlatformNamespaced>> hunks;

    public PipedHunkStack()
    {
        this.hunks = new HashMap<>();
    }

    public void register(Class<? extends PlatformNamespaced> clazz, FeatureTarget<? extends PlatformNamespaced> hunk)
    {
        hunks.put(clazz, hunk);
    }

    @SuppressWarnings("unchecked")
    public <T extends PlatformNamespaced> FeatureTarget<T> hunk(Class<?> hunk)
    {
        return (FeatureTarget<T>) hunks.get(hunk);
    }
}
