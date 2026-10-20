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
import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The procedural objects of one pack: those its regions and biomes declare, and those saved on their own as snippets,
 * such as {@code snippet/procedural-tree/oak.json}. A definition several places share is baked once.
 */
public final class ProceduralObjectCatalog {
    private static final Gson DEFINITION_JSON = new Gson();
    private static final List<Class<?>> DEFINITION_TYPES = List.of(Tree.class, Ruin.class, Formation.class, Coral.class, Fungus.class, Crystal.class);

    private final IrisData data;
    private final KMap<String, ResolvedProceduralPlacement> byDefinition = new KMap<>();

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
        return byDefinition.computeIfAbsent(identity, ignored -> resolvedFor(definition));
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
     * Every procedural object the pack defines, in snippets or in regions and biomes, whether or not its dimensions
     * reach them. Where two definitions share a type and name, the first one found stands for both.
     */
    public List<ResolvedProceduralPlacement> definitions() {
        Map<String, ResolvedProceduralPlacement> byTypeAndName = new LinkedHashMap<>();
        for (Class<?> type : DEFINITION_TYPES) {
            snippetDefinitions(type).forEach(definition -> addDefinition(byTypeAndName, placementOf(definition)));
        }
        data.getRegionLoader().streamAll().forEach(region -> addDefinitions(byTypeAndName, region.getProceduralObjects()));
        data.getBiomeLoader().streamAll().forEach(biome -> addDefinitions(byTypeAndName, biome.getProceduralObjects()));
        return List.copyOf(byTypeAndName.values());
    }

    private void addDefinitions(Map<String, ResolvedProceduralPlacement> byTypeAndName, ProceduralObjectSettings settings) {
        if (settings == null) {
            return;
        }
        settings.resolved(data).forEach(placement -> addDefinition(byTypeAndName, placement));
    }

    private static void addDefinition(Map<String, ResolvedProceduralPlacement> byTypeAndName, ResolvedProceduralPlacement placement) {
        byTypeAndName.putIfAbsent(placement.kind() + "/" + placement.name(), placement);
    }

    private List<Object> snippetDefinitions(Class<?> type) {
        Path folder = data.getDataFolder().toPath().resolve("snippet").resolve(type.getAnnotation(Snippet.class).value());
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(folder)) {
            List<Object> definitions = new ArrayList<>();
            files.filter(file -> file.toString().endsWith(".json")).sorted().forEach(file -> {
                Object definition = readDefinition(file, type);
                if (definition != null) {
                    definitions.add(definition);
                }
            });
            return definitions;
        } catch (IOException error) {
            Iris.warn("Could not list the procedural objects in " + folder + ": " + error.getMessage());
            return List.of();
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

}
