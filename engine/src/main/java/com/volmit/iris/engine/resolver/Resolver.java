package com.volmit.iris.engine.resolver;

import art.arcane.cram.PakKey;
import art.arcane.cram.PakResource;
import com.google.gson.Gson;
import com.volmit.iris.platform.PlatformNamespaceKey;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Resolver<T extends Resolvable> {
    @SuppressWarnings("unchecked")
    static <F extends Resolvable> CompositeResolver<F> frozen(Map<PakKey, PakResource> resources, Predicate<PakResource> isTypePredicate) {
        Map<String, Map<String, F>> resolvables = new HashMap<>();
        Map<String, Resolver<F>> resolvers = new HashMap<>();

        for(PakKey i : resources.keySet()) {
            PakResource r = resources.get(i);

            if(isTypePredicate.test(r)) {
                Map<String, F> rs = resolvables.computeIfAbsent(i.getNamespace(), (k) -> new HashMap<>());
                rs.put(i.getKey(), (F) i);
            }
        }

        for(String i : resolvables.keySet()) {
            resolvers.put(i, frozen(i, resolvables.get(i)));
        }

        return new CompositeResolver<>(resolvers);
    }

    static <F extends Resolvable> Resolver<F> frozen(String namespace, Map<String, F> map) {
        return new FrozenResolver<>(namespace, map);
    }

    static <F extends Resolvable> Resolver<F> hot(String namespace, Function<String, F> loader) {
        return new HotResolver<>(namespace, loader);
    }

    static <F extends Resolvable> Resolver<F> hotDirectoryJson(String namespace, Class<?> resolvableClass, File folder, Gson gson) {
        return hotDirectory(namespace, (in) -> (F) gson.fromJson(new InputStreamReader(in), resolvableClass), folder, "json");
    }

    static <F extends Resolvable> Resolver<F> hotDirectory(String namespace, Function<InputStream, F> loader, File folder, String... extensions) {
        return new HotResolver<>(namespace, (key) -> {
            for(String i : extensions)
            {
                File f = new File(folder, key + "." + i);
                if(f.exists())
                {
                    try {
                        FileInputStream in = new FileInputStream(f);
                        F ff = loader.apply(in);
                        in.close();
                        return ff;
                    } catch(IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            return null;
        });
    }

    boolean hasNamespace(String namespace);

    T resolve(PlatformNamespaceKey key);

    T resolve(String key);

    default boolean contains(String namespace, String key) {
        return hasNamespace(namespace) && resolve(key) != null;
    }

    default boolean contains(PlatformNamespaceKey key){
        return hasNamespace(key.getNamespace()) && resolve(key) != null;
    }

    Resolver<T> and(String namespace, Resolver<T> resolver);

    default void print(String type, Object printer)
    {
        print(type, printer, 0);
    }

    void print(String type, Object printer, int index);
}
