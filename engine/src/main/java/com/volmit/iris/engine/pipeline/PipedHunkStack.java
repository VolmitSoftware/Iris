package com.volmit.iris.engine.pipeline;

import art.arcane.amulet.collections.hunk.Hunk;
import com.volmit.iris.platform.PlatformNamespaced;

import java.util.HashMap;
import java.util.Map;

public class PipedHunkStack {
    private final Map<Class<? extends PlatformNamespaced>, Hunk<? extends PlatformNamespaced>> hunks;

    public PipedHunkStack()
    {
        this.hunks = new HashMap<>();
    }

    public void register(Class<? extends PlatformNamespaced> clazz, Hunk<? extends PlatformNamespaced> hunk)
    {
        hunks.put(clazz, hunk);
    }

    @SuppressWarnings("unchecked")
    public <T extends PlatformNamespaced> Hunk<T> hunk(Class<?> hunk)
    {
        return (Hunk<T>) hunks.get(hunk);
    }
}
