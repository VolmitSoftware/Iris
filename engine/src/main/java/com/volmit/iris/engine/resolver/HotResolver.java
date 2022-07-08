package com.volmit.iris.engine.resolver;

import art.arcane.amulet.format.Form;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.volmit.iris.platform.PlatformNamespaceKey;
import com.volmit.iris.util.NSK;
import lombok.Data;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Data
public class HotResolver<T extends Resolvable> implements Resolver<T>, CacheLoader<String, T> {
    private final LoadingCache<String, T> cache;
    private final String namespace;
    private final Function<String, T> loader;
    private final Supplier<List<String>> keyGetter;

    public HotResolver(String namespace, Function<String, T> loader, Supplier<List<String>> keyGetter)
    {
        this.namespace = namespace;
        this.keyGetter = keyGetter;
        this.loader = loader;
        cache = Caffeine.newBuilder().build(this);
    }

    public T resolve(PlatformNamespaceKey key) {
        return cache.get(key.getKey());
    }

    @Override
    public T resolve(String key) {
        return cache.get(key);
    }

    public boolean hasNamespace(String namespace) {
        return this.namespace.equals(namespace);
    }

    public FrozenResolver<T> freeze()
    {
        cache.cleanUp();
        Map<String, T> map = new HashMap<>((int) cache.estimatedSize());
        Map<String, T> view = cache.asMap();

        for(String i : view.keySet()) {
            map.put(i, view.get(i));
        }

        return new FrozenResolver<>(getNamespace(), Collections.unmodifiableMap(map));
    }

    @Override
    public @Nullable T load(String key) {
        return loader.apply(key);
    }

    @Override
    public Resolver<T> and(String namespace, Resolver<T> resolver) {
        if(!namespace.equals(getNamespace())) {
            return new CompositeResolver<>(Map.of(namespace, resolver, getNamespace(), this));
        }

        return new MergedNamespaceResolver<>(namespace, this, resolver);
    }

    @Override
    public void print(String type, Object printer, int indent) {
        printer.i(Form.repeat(" ", indent) + "Hot[" + namespace + "] " + type);
    }

    @Override
    public void addAllKeys(List<PlatformNamespaceKey> keys) {
        keys.addAll(keyGetter.get().stream().map(i -> new NSK(getNamespace(), i)).toList());
    }
}
