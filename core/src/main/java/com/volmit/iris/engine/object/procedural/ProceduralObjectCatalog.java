/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.volmit.iris.engine.object.procedural;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.engine.object.procedural.coral.Coral;
import com.volmit.iris.engine.object.procedural.coral.ResolvedCoral;
import com.volmit.iris.engine.object.procedural.crystal.Crystal;
import com.volmit.iris.engine.object.procedural.crystal.ResolvedCrystal;
import com.volmit.iris.engine.object.procedural.formation.Formation;
import com.volmit.iris.engine.object.procedural.formation.ResolvedFormation;
import com.volmit.iris.engine.object.procedural.fungi.Fungus;
import com.volmit.iris.engine.object.procedural.fungi.ResolvedFungus;
import com.volmit.iris.engine.object.procedural.ruin.ResolvedRuin;
import com.volmit.iris.engine.object.procedural.ruin.Ruin;
import com.volmit.iris.engine.object.procedural.tree.ResolvedTree;
import com.volmit.iris.engine.object.procedural.tree.Tree;
import com.volmit.iris.util.collection.KMap;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The procedural objects of one pack: those its regions and biomes declare, and those saved on their own as snippets
 * in a folder per type under {@code snippet/procedural}, such as {@code snippet/procedural/trees/oak.json}. A definition several places share is baked once.
 */
public final class ProceduralObjectCatalog {
    private static final Gson DEFINITION_JSON = new Gson();
    /**
     * The snippet folder all procedural settings live under. Each type keeps its own subfolder because the editor
     * validates a snippet folder against a single schema.
     */
    public static final String SNIPPET_FOLDER = "procedural";
    public static final String TREE_FOLDER = SNIPPET_FOLDER + "/trees";
    public static final String RUIN_FOLDER = SNIPPET_FOLDER + "/ruins";
    public static final String FORMATION_FOLDER = SNIPPET_FOLDER + "/formations";
    public static final String CORAL_FOLDER = SNIPPET_FOLDER + "/coral";
    public static final String FUNGUS_FOLDER = SNIPPET_FOLDER + "/fungi";
    public static final String CRYSTAL_FOLDER = SNIPPET_FOLDER + "/crystals";
    public static final String SET_FOLDER = SNIPPET_FOLDER + "/sets";
    public static final String PART_FOLDER = SNIPPET_FOLDER + "/parts";
    private static final List<Class<?>> DEFINITION_TYPES = List.of(Tree.class, Ruin.class, Formation.class, Coral.class, Fungus.class, Crystal.class);

    private final IrisData data;
    private final KMap<String, ResolvedProceduralPlacement> byDefinition = new KMap<>();
    private final KMap<ResolvedProceduralPlacement, Object> definitionByPlacement = new KMap<>();

    public ProceduralObjectCatalog(IrisData data) {
        this.data = data;
    }

    /** The placements {@code settings} declare, shared with every other declaration of the same definitions. */
    public List<ResolvedProceduralPlacement> resolve(ProceduralObjectSettings settings) {
        List<ResolvedProceduralPlacement> all = new ArrayList<>();
        Stream.of(settings.trees(), settings.ruins(), settings.formations(), settings.coral(), settings.fungi(), settings.crystals())
                .flatMap(List::stream)
                .forEach(definition -> all.add(placementOf(definition)));
        return List.copyOf(all);
    }

    private ResolvedProceduralPlacement placementOf(Object definition) {
        String identity = definition.getClass().getName() + DEFINITION_JSON.toJson(definition);
        return byDefinition.computeIfAbsent(identity, ignored -> {
            ResolvedProceduralPlacement placement = resolvedFor(definition);
            definitionByPlacement.put(placement, definition);
            return placement;
        });
    }

    /**
     * The same definition as {@code placement}, baked from {@code seed} instead of its own, to compare seeds before
     * choosing one. It is not kept, since a viewer may step through many seeds.
     */
    public ResolvedProceduralPlacement reseeded(ResolvedProceduralPlacement placement, long seed) {
        Object definition = definitionByPlacement.get(placement);
        if (definition == null) {
            throw new IllegalArgumentException("The pack was reloaded since " + placement.name() + " was read");
        }
        JsonObject reseededJson = DEFINITION_JSON.toJsonTree(definition).getAsJsonObject();
        reseededJson.addProperty("seed", seed);
        return resolvedFor(data.getGson().fromJson(reseededJson, definition.getClass()));
    }

    private ResolvedProceduralPlacement resolvedFor(Object definition) {
        return switch (definition) {
            case Tree tree -> new ResolvedTree(tree, data);
            case Ruin ruin -> new ResolvedRuin(ruin, data);
            case Formation formation -> new ResolvedFormation(formation, data);
            case Coral coral -> new ResolvedCoral(coral, data);
            case Fungus fungus -> new ResolvedFungus(fungus, data);
            case Crystal crystal -> new ResolvedCrystal(crystal, data);
            default -> throw new IllegalArgumentException("Not a procedural object: " + definition.getClass().getName());
        };
    }

    /**
     * Every procedural object the pack defines, whether or not its dimensions reach them. A snippet is found at its file's
     * path under {@code snippet/procedural}, like {@code trees/estranged/oak}; a definition only written in a region or
     * biome at its type and name, like {@code trees/oak}.
     */
    public List<Definition> definitions() {
        Map<ResolvedProceduralPlacement, String> pathByPlacement = new LinkedHashMap<>();
        Set<String> takenPaths = new HashSet<>();
        DEFINITION_TYPES.forEach(type -> snippetDefinitions(type)
                .forEach((path, definition) -> addDefinition(pathByPlacement, takenPaths, placementOf(definition), path)));
        Stream.concat(data.getRegionLoader().streamAll().map(IrisRegion::getProceduralObjects),
                        data.getBiomeLoader().streamAll().map(IrisBiome::getProceduralObjects))
                .filter(Objects::nonNull)
                .flatMap(settings -> settings.resolved(data).stream())
                .forEach(placement -> addDefinition(pathByPlacement, takenPaths, placement, placement.kind() + "/" + placement.name()));
        List<Definition> definitions = new ArrayList<>();
        pathByPlacement.forEach((placement, path) -> definitions.add(new Definition(path, placement)));
        return definitions;
    }

    // The same definition found twice keeps its first path; two different ones never share a path.
    private static void addDefinition(Map<ResolvedProceduralPlacement, String> pathByPlacement, Set<String> takenPaths,
                                      ResolvedProceduralPlacement placement, String path) {
        if (pathByPlacement.containsKey(placement)) {
            return;
        }
        String uniquePath = path;
        for (int copy = 2; !takenPaths.add(uniquePath); copy++) {
            uniquePath = path + " (" + copy + ")";
        }
        pathByPlacement.put(placement, uniquePath);
    }

    /** The definitions in the snippet folder of one type, by their path under {@code snippet/procedural} without the extension. */
    private Map<String, Object> snippetDefinitions(Class<?> type) {
        Path snippetFolder = data.getDataFolder().toPath().resolve("snippet");
        Path proceduralFolder = snippetFolder.resolve(SNIPPET_FOLDER);
        Path folder = snippetFolder.resolve(type.getAnnotation(Snippet.class).value());
        if (!Files.isDirectory(folder)) {
            return Map.of();
        }
        try (Stream<Path> files = Files.walk(folder)) {
            Map<String, Object> definitions = new LinkedHashMap<>();
            files.filter(file -> file.toString().endsWith(".json")).sorted().forEach(file -> {
                Object definition = readDefinition(file, type);
                if (definition != null) {
                    String path = proceduralFolder.relativize(file).toString().replace('\\', '/');
                    definitions.put(path.substring(0, path.length() - ".json".length()), definition);
                }
            });
            return definitions;
        } catch (IOException error) {
            Iris.warn("Could not list the procedural objects in " + folder + ": " + error.getMessage());
            return Map.of();
        }
    }

    private Object readDefinition(Path file, Class<?> type) {
        try (Reader reader = Files.newBufferedReader(file)) {
            return data.getGson().fromJson(reader, type);
        } catch (IOException | RuntimeException error) {
            Iris.warn("Could not read the procedural object " + file + ": " + error.getMessage());
            return null;
        }
    }

    /** A procedural object and where the pack keeps it, as a path under {@code snippet/procedural}. */
    public record Definition(String path, ResolvedProceduralPlacement placement) {
    }
}
