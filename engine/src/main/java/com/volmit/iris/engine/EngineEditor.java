package com.volmit.iris.engine;

import art.arcane.amulet.concurrent.J;
import art.arcane.amulet.io.JarLoader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapterFactory;
import com.volmit.iris.engine.dimension.IrisAuthor;
import com.volmit.iris.engine.dimension.IrisBiome;
import com.volmit.iris.engine.dimension.IrisChance;
import com.volmit.iris.engine.dimension.IrisDecorator;
import com.volmit.iris.engine.dimension.IrisDimension;
import com.volmit.iris.engine.dimension.IrisDimensionMeta;
import com.volmit.iris.engine.dimension.IrisGenerator;
import com.volmit.iris.engine.dimension.IrisPalette;
import com.volmit.iris.engine.dimension.IrisRange;
import com.volmit.iris.engine.dimension.IrisResolvable;
import com.volmit.iris.engine.dimension.IrisSeed;
import com.volmit.iris.engine.dimension.IrisSeedSetMode;
import com.volmit.iris.engine.dimension.IrisSurface;
import com.volmit.iris.engine.dimension.IrisSurfaceLayer;
import com.volmit.iris.engine.editor.Resolvable;
import lombok.Data;

import java.util.List;
import java.util.Map;
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
            .filter(i -> !i.equals(IrisResolvable.class))
            .map(i -> J.attempt(() -> (Resolvable) i.getDeclaredConstructor().newInstance(), null)).toList(), List.of());
        GsonBuilder gsonBuilder = new GsonBuilder();
        resolvableTypes.forEach(i -> i.apply(gsonBuilder));
        this.gson = gsonBuilder.setPrettyPrinting().create();
        i("Registered " + resolvableTypes.size() + " Mutators with " + resolvableTypes.stream().filter(i -> i instanceof TypeAdapterFactory).count() + " Type Adapter Factories");

        i(gson.toJson(gson.fromJson("Noise.simplex(seed)", IrisGenerator.class)));

        System.out.println(gson.toJson(IrisDimension.builder()
            .biome(IrisBiome.builder()
                .name("Plains")
                .surface(IrisSurface.builder()
                    .decorator(IrisDecorator.builder()
                        .palette(IrisPalette.flat("minecraft:grass"))
                        .chance(IrisChance.half(IrisGenerator.builder()
                            .java("Noise.static(seed)")
                            .seed(IrisSeed.builder()
                                .offset(67)
                                .build())
                            .build()))
                        .build())
                    .layer(IrisSurfaceLayer.builder()
                        .palette(IrisPalette.flat("minecraft:grass_block"))
                        .thickness(IrisRange.flat(1))
                        .build())
                    .layer(IrisSurfaceLayer.builder()
                        .palette(IrisPalette.builder()
                            .block("minecraft:dirt")
                            .block("minecraft:coarse_dirt")
                            .build())
                        .thickness(IrisRange.builder()
                            .min(3)
                            .max(5)
                            .generator(IrisGenerator.builder()
                                .java("Noise.simplex(seed).warp(Noise.perlin(seed+1), 0.5, 9)")
                                .seed(IrisSeed.builder()
                                    .offset(446)
                                    .mode(IrisSeedSetMode.WORLD_OFFSET)
                                    .build())
                                .build())
                            .build())
                        .build())
                    .layer(IrisSurfaceLayer.builder()
                        .palette(IrisPalette.builder()
                            .block("minecraft:stone")
                            .block("minecraft:granite")
                            .build())
                        .thickness(IrisRange.builder()
                            .min(3)
                            .max(7)
                            .generator(IrisGenerator.builder()
                                .java("Noise.simplex(seed).warp(Noise.perlin(seed+1), 0.5, 9)")
                                .seed(IrisSeed.builder()
                                    .offset(123)
                                    .mode(IrisSeedSetMode.WORLD_OFFSET)
                                    .build())
                                .build())
                            .build())
                        .build())
                    .build())
                .build())
            .meta(IrisDimensionMeta.builder()
                .name("Overworld")
                .description("The overworld generates stuff")
                .version("3.0.0")
                .author(IrisAuthor.builder()
                    .name("cyberpwn")
                    .social(Map.of("discord", "cyberpwn#1337"))
                    .build())
                .build())
            .build()));
    }
}
