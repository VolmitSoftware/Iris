package com.volmit.iris.engine.editor;

import com.volmit.iris.platform.PlatformNamespaced;

import java.util.Map;

public class MutatedFrozenResolver<T extends Mutated> implements MutatedResolver<T> {
    private final Map<PlatformNamespaced, T> registry;

    public MutatedFrozenResolver(Map<PlatformNamespaced, T> registry)
    {
        this.registry = registry;
    }

    public T resolve(PlatformNamespaced key) {
        return registry.get(key);
    }
}
