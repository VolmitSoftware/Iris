package com.volmit.iris.engine.resolver;

import art.arcane.amulet.format.Form;
import com.volmit.iris.platform.PlatformNamespaceKey;
import lombok.Data;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Data
public class MergedNamespaceResolver<T extends Resolvable> implements Resolver<T> {
    private final String namespace;
    private final List<Resolver<T>> resolvers;

    public MergedNamespaceResolver(String namespace, Resolver<T>... resolvers)
    {
        this(namespace, Arrays.stream(resolvers).toList());
    }

    public MergedNamespaceResolver(String namespace, List<Resolver<T>> resolvers)
    {
        this.namespace = namespace;
        this.resolvers = resolvers;
    }

    @Override
    public boolean hasNamespace(String namespace) {
        return this.namespace.equals(namespace);
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

    @Override
    public void print(String type, Object printer, int indent) {
        printer.i(Form.repeat(" ", indent) + "Merged[" + namespace + "] " + type);

        for(Resolver<T> i : getResolvers()) {
            i.print(type, printer, indent + 2);
        }
    }

    @Override
    public Resolver<T> and(String namespace, Resolver<T> resolver) {
        if(namespace.equals(getNamespace()))
        {
            List<Resolver<T>> r = resolvers.copy();
            r.add(resolver);
            return new MergedNamespaceResolver<>(namespace, r);
        }

        return new CompositeResolver<>(Map.of(getNamespace(), this, namespace, resolver));
    }
}
