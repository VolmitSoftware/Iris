package com.volmit.iris.engine;

import art.arcane.amulet.concurrent.J;
import art.arcane.amulet.io.IO;
import art.arcane.amulet.io.JarLoader;
import art.arcane.cram.PakFile;
import art.arcane.cram.PakKey;
import art.arcane.cram.PakResource;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapterFactory;
import com.volmit.iris.engine.resolver.*;
import lombok.Data;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
public class EngineData {
    private final Engine engine;
    private final Gson gson;
    private List<Resolvable> resolvableTypes;
    private final Map<Class<?>, Resolver<?>> resolvers;

    public EngineData(Engine engine) throws IOException {
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

    public void registerResolver(Class<?> type, Resolver<?> resolver, String namespace)
    {
        if(resolvers.containsKey(type)) {
            Resolver r = resolvers.get(type);
            resolvers.put(type, r.and(namespace, r));
        }

        else {
            resolvers.put(type, resolver);
        }
    }

    public void loadData(File folder) throws IOException {
        i("Loading Data in " + folder.getPath());
        for(File i : folder.listFiles()) {
            if(i.isDirectory()) {
                loadDataNamespaced(i, i.getName());
            }
        }
    }

    public void loadDataNamespaced(File folder, String namespace) throws IOException {
        i("Loading Namespace " + namespace + " in " + folder.getPath());
        for(Resolvable i : resolvableTypes)
        {
            new File(folder, i.entity().getId()).mkdirs();
            IO.writeAll(
                    new File(new File(folder, i.entity().getId()), "example.json"), gson.toJson(i));
        }

        for(File i : folder.listFiles())
        {
            if(i.isDirectory()) {
                loadDataFolder(i, namespace);
            }

            else if(i.getName().endsWith(".dat")) {
                loadPakFile(folder, i.getName().split("\\Q.\\E")[0]);
            }
        }
    }

    public void loadDataFolder(File folder, String namespace) {
        for(Resolvable i : resolvableTypes)
        {
            if(!folder.getName().equals(i.entity().getId())) {
                continue;
            }

            registerResolver(i.getClass(), Resolver.hotDirectoryJson(namespace, i.getClass(), folder, gson), namespace);
        }
    }

    public void loadPakFile(File folder, String name) throws IOException {
        PakFile pakFile = new PakFile(folder, name);
        Map<PakKey, PakResource> resources = pakFile.getAllResources();

        for(Resolvable i : resolvableTypes)
        {
            Class<? extends Resolvable> resolvableClass = i.getClass();
            CompositeResolver<?> composite = Resolver.frozen(resources, (p) -> p.getClass().equals(resolvableClass));

            for(String j : composite.getResolvers().keySet())
            {
                Resolver<? extends Resolvable> resolver = composite.getResolvers().get(i);
                this.registerResolver(i.getClass(), resolver, j);
            }
        }
    }

    public void printResolvers() {
        resolvers.forEach((k, i) -> i.print(k.simpleName(), this));
    }
}
