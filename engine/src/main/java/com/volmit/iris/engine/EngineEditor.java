package com.volmit.iris.engine;

import art.arcane.amulet.concurrent.J;
import art.arcane.amulet.io.JarLoader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapterFactory;
import com.volmit.iris.engine.dimension.IrisBiome;
import com.volmit.iris.engine.dimension.IrisDimension;
import com.volmit.iris.engine.dimension.IrisGenerator;
import com.volmit.iris.engine.dimension.IrisSeedSet;
import com.volmit.iris.engine.editor.Mutated;
import lombok.Data;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
public class EngineEditor {
    private final Engine engine;
    private final Gson gson;
    private  List<Mutated> mutatedTypes;

    public EngineEditor(Engine engine)
    {
        this.engine = engine;
        this.mutatedTypes =  J.attempt(() -> new JarLoader(getClass()).all().parallel()
            .filter(Objects::nonNull)
            .filter(i -> !i.isInterface() && !i.isEnum())
            .filter(i -> i.isAssignableFrom(Mutated.class) || Mutated.class.isAssignableFrom(i))
            .map(i -> J.attempt(() -> (Mutated) i.getDeclaredConstructor().newInstance(), null)).toList(), List.of());
        GsonBuilder gsonBuilder = new GsonBuilder();
        mutatedTypes.forEach(i -> i.apply(gsonBuilder));
        this.gson = gsonBuilder.setPrettyPrinting().create();
        i("Registered " + mutatedTypes.size() + " Mutators with " + mutatedTypes.stream().filter(i -> i instanceof TypeAdapterFactory).count() + " Type Adapter Factories");

        i(gson.toJson(gson.fromJson("Noise.simplex(seed)", IrisGenerator.class)));
    }
}
