package com.volmit.iris.engine.pipeline;

import com.volmit.iris.engine.feature.IrisFeatureTarget;
import com.volmit.iris.platform.PlatformNamespaced;

import java.util.HashMap;
import java.util.Map;

public class PipedHunkStack {
    private final Map<Class<? extends PlatformNamespaced>, IrisFeatureTarget<? extends PlatformNamespaced>> hunks;

    public PipedHunkStack()
    {
        this.hunks = new HashMap<>();
    }

    public void register(Class<? extends PlatformNamespaced> clazz, IrisFeatureTarget<? extends PlatformNamespaced> hunk)
    {
        hunks.put(clazz, hunk);
    }

    @SuppressWarnings("unchecked")
    public <T extends PlatformNamespaced> IrisFeatureTarget<T> hunk(Class<?> hunk)
    {
        return (IrisFeatureTarget<T>) hunks.get(hunk);
    }
}
