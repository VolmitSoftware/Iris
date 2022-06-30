package com.volmit.iris.platform;

import art.arcane.amulet.format.Form;
import lombok.Data;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
public class PlatformRegistry<T extends PlatformNamespaced> {
    private final Map<PlatformNamespaceKey, T> registry;
    private final String name;
    private final String namePlural;

    public PlatformRegistry(String name, String namePlural, Stream<T> stream) {
        this.name = name;
        this.namePlural = namePlural;
        registry = Collections.unmodifiableMap(stream.collect(Collectors.toMap(PlatformNamespaced::getKey, (t) -> t)));
        i("Registered " + Form.f(registry.size()) + " " + namePlural);
    }

    public PlatformRegistry(String name, Stream<T> stream) {
        this(name, name + "s", stream);
    }


    public T get(PlatformNamespaceKey key) {
        return registry.get(key);
    }

    public Set<PlatformNamespaceKey> getKeys() {
        return Collections.unmodifiableSet(registry.keySet());
    }
}