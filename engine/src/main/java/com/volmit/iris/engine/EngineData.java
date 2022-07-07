package com.volmit.iris.engine;

import art.arcane.amulet.concurrent.J;
import art.arcane.amulet.io.JarLoader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapterFactory;
import com.volmit.iris.engine.resolver.*;
import lombok.Data;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
public class EngineData {
    private final Engine engine;
    private final Gson gson;
    private List<Resolvable> resolvableTypes;
    private final Map<Class<? extends Resolvable>, Resolver<? extends Resolvable>> resolvers;

    public EngineData(Engine engine)
    {
        this.engine = engine;
        this.resolvers = new HashMap<>();
        this.resolvableTypes =  J.attempt(() -> new JarLoader(getClass()).all().parallel()
            .filter(Objects::nonNull)
            .filter(i -> !i.isInterface() && !i.isEnum())
            .filter(i -> i.isAssignableFrom(Resolvable.class) || Resolvable.class.isAssignableFrom(i))
            .filter(i -> !i.equals(EngineResolvable.class))
            .map(i -> J.attempt(() -> (Resolvable) i.getDeclaredConstructor().newInstance(), null)).toList(), List.of());
        GsonBuilder gsonBuilder = new GsonBuilder();
        resolvableTypes.forEach(i -> i.apply(gsonBuilder));
        this.gson = gsonBuilder.setPrettyPrinting().create();
        i("Registered " + resolvableTypes.size() + " Mutators with " + resolvableTypes.stream().filter(i -> i instanceof TypeAdapterFactory).count() + " Type Adapter Factories");
    }

    public <T extends Resolvable> void registerResolver(Class<T> type, Resolver<T> resolver, String... namespaces)
    {
        if(resolvers.containsKey(type)) {
            Resolver<T> existing = (Resolver<T>) resolvers.get(type);

            if(existing instanceof CompositeResolver<T> c) {
                Map<String, Resolver<T>> oresolvers = c.getResolvers();

                if(namespaces.length > 1) {
                    CompositeResolver<T> n = (CompositeResolver<T>) resolver;

                    for(String i : n.getResolvers().keySet()) {
                        if(oresolvers.containsKey(i)) {
                            oresolvers.put(i, new MergedResolver<>(oresolvers.get(i), n.getResolvers().get(i)));
                        }

                        else
                        {
                            oresolvers.put(i, n.getResolvers().get(i));
                        }
                    }
                }

                else
                {

                }
            }
        }

        else
        {
            resolvers.put(type, resolver);
        }
    }

    public void loadData(File folder)
    {

    }
}
