package com.volmit.iris.engine.resolver;

import com.volmit.iris.platform.PlatformNamespaceKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MergedResolver<T extends Resolvable> implements Resolver<T> {
    private final List<Resolver<T>> resolvers;

    public MergedResolver(Resolver<T>... resolvers)
    {
        this(Arrays.stream(resolvers).toList());
    }

    public MergedResolver(List<Resolver<T>> resolvers)
    {
        this.resolvers = resolvers;
    }

    @Override
    public boolean hasNamespace(String namespace) {
        for(Resolver<T> i : resolvers) {
            if(i.hasNamespace(namespace)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public T resolve(PlatformNamespaceKey key) {
        for(Resolver<T> i : resolvers) {
            T t = i.resolve(key);

            if(t != null)
            {
                return t;
            }
        }

        return null;
    }

    @Override
    public T resolve(String key) {
        for(Resolver<T> i : resolvers) {
            T t = i.resolve(key);

            if(t != null)
            {
                return t;
            }
        }

        return null;
    }
}
