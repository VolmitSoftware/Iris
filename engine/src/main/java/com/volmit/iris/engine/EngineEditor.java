package com.volmit.iris.engine;

import art.arcane.amulet.concurrent.J;
import art.arcane.amulet.io.JarLoader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapterFactory;
import com.volmit.iris.engine.dimension.IrisGenerator;
import com.volmit.iris.engine.editor.Resolvable;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class EngineEditor {
    private final Engine engine;
    private final Gson gson;
    private  List<Resolvable> resolvableTypes;

    public EngineEditor(Engine engine)
    {
        this.engine = engine;
        this.resolvableTypes =  J.attempt(() -> new JarLoader(getClass()).all().parallel()
            .filter(Objects::nonNull)
            .filter(i -> !i.isInterface() && !i.isEnum())
            .filter(i -> i.isAssignableFrom(Resolvable.class) || Resolvable.class.isAssignableFrom(i))
            .map(i -> J.attempt(() -> (Resolvable) i.getDeclaredConstructor().newInstance(), null)).toList(), List.of());
        GsonBuilder gsonBuilder = new GsonBuilder();
        resolvableTypes.forEach(i -> i.apply(gsonBuilder));
        this.gson = gsonBuilder.setPrettyPrinting().create();
        i("Registered " + resolvableTypes.size() + " Mutators with " + resolvableTypes.stream().filter(i -> i instanceof TypeAdapterFactory).count() + " Type Adapter Factories");

        i(gson.toJson(gson.fromJson("Noise.simplex(seed)", IrisGenerator.class)));
    }
}
