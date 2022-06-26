package com.volmit.iris.platform;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class PlatformRegistry<T extends PlatformNamespaced> {
    private final Map<PlatformNamespaceKey, T> registry;

    public PlatformRegistry(Stream<T> stream) {
        registry = Collections.unmodifiableMap(stream.collect(Collectors.toMap(PlatformNamespaced::getKey, (t) -> t)));
    }

    public T get(PlatformNamespaceKey key) {
        return registry.get(key);
    }

    public Set<PlatformNamespaceKey> getKeys() {
        return Collections.unmodifiableSet(registry.keySet());
    }
}