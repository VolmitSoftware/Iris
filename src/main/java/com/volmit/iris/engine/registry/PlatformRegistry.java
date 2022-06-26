package com.volmit.iris.engine.registry;

import art.arcane.amulet.collections.Biset;
import art.arcane.amulet.collections.ObjectBiset;
import com.volmit.iris.engine.object.Namespaced;
import com.volmit.iris.engine.object.NSKey;
import com.volmit.iris.platform.PlatformDataTransformer;
import lombok.Data;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


public class PlatformRegistry<NATIVE, T extends Namespaced> {
    @Getter
    private final PlatformDataTransformer<NATIVE, T> transformer;
    private final Map<NSKey, RegistryValue<NATIVE, T>> registry;

    public PlatformRegistry(PlatformDataTransformer<NATIVE, T> transformer)
    {
        this.transformer = transformer;
        registry = transformer.getRegistry().collect(Collectors.toMap(transformer::getKey, (t) -> new RegistryValue<>(t, transformer.toIris(t))));
        d("Registered " + transformer.countSuffixName(registry.size()));
    }

    public NATIVE getNative(NSKey key) {
        return registry.get(key).getNativeValue();
    }

    public T get(NSKey key) {
        return registry.get(key).getValue();
    }

    public Set<NSKey> getKeys() {
        return Collections.unmodifiableSet(registry.keySet());
    }
}