package com.volmit.iris.engine.resolver;

import com.volmit.iris.platform.PlatformNamespaceKey;
import lombok.Data;

import java.util.Map;

@Data
public class CompositeResolver<T extends Resolvable> implements Resolver<T> {
    private final Map<String, Resolver<T>> resolvers;
    private final String[] namespaces;

    public CompositeResolver(Map<String, Resolver<T>> resolvers)
    {
        this.resolvers = resolvers;
        this.namespaces = resolvers.keySet().toArray(new String[0]);
    }

    @Override
    public boolean hasNamespace(String namespace) {
        return resolvers.containsKey(namespace);
    }

    @Override
    public T resolve(PlatformNamespaceKey key) {
        return resolvers.get(key.getNamespace()).resolve(key);
    }

    @Override
    public T resolve(String key) {
        for(String i : getNamespaces()) {
            if(resolvers.get(i).contains(i, key)) {
                return resolvers.get(i).resolve(key);
            }
        }

        return null;
    }
}
