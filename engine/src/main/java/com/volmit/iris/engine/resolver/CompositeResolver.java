package com.volmit.iris.engine.resolver;

import art.arcane.amulet.format.Form;
import com.volmit.iris.platform.PlatformNamespaceKey;
import lombok.Data;

import java.util.Arrays;
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

    @Override
    public Resolver<T> and(String namespace, Resolver<T> resolver) {
        Map<String, Resolver<T>> resolvers = this.resolvers.copy();

        if(hasNamespace(namespace)) {
            resolvers.put(namespace, resolvers.get(namespace).and(namespace, resolver));
        }

        else {
            resolvers.put(namespace, resolver);
        }

        return new CompositeResolver<>(resolvers);
    }

    @Override
    public void print(String type, Object printer, int indent) {
        printer.i(Form.repeat(" ", indent) + "Composite[" + Arrays.toString(getNamespaces()) + "] " + type);

        for(Resolver<T> i : getResolvers().values()) {
            i.print(type, printer, indent + 2);
        }
    }
}
