/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded.catalog;

import art.arcane.iris.pack.ingest.catalog.CatalogBiome;
import art.arcane.iris.pack.ingest.catalog.CatalogDimensionType;
import art.arcane.iris.pack.ingest.catalog.CatalogLevelStem;
import art.arcane.iris.pack.ingest.catalog.CatalogNamespace;
import art.arcane.iris.pack.ingest.catalog.CatalogPlacedFeature;
import art.arcane.iris.pack.ingest.catalog.CatalogSpawn;
import art.arcane.iris.pack.ingest.catalog.CatalogStructure;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogFingerprint;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogIndex;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModMetadata;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.WorldgenBiome;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.WorldgenDimensionType;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.WorldgenLevelStem;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.WorldgenPlacedFeature;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.WorldgenSpawn;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.WorldgenStructure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class WorldgenCatalogAssembler {
    private static final String MINECRAFT = "minecraft";

    private WorldgenCatalogAssembler() {
    }

    public static WorldgenCatalog assemble(NativeWorldgenSnapshot snapshot, CatalogCaptureContext context) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(context, "context");
        List<CatalogBiome> biomes = biomes(snapshot.biomes());
        List<CatalogPlacedFeature> placedFeatures = placedFeatures(snapshot.placedFeatures());
        List<CatalogStructure> structures = structures(snapshot.structures());
        List<CatalogDimensionType> dimensionTypes = dimensionTypes(snapshot.dimensionTypes());
        List<CatalogLevelStem> levelStems = levelStems(snapshot.levelStems());
        Map<String, List<String>> blockTags = referencedBlockTags(placedFeatures, snapshot.blockTags());
        Map<String, List<String>> registryKeys = new LinkedHashMap<>();
        registryKeys.put("biome", biomes.stream().map(CatalogBiome::key).toList());
        registryKeys.put("placed_feature", placedFeatures.stream().map(CatalogPlacedFeature::key).toList());
        registryKeys.put("structure", structures.stream().map(CatalogStructure::key).toList());
        registryKeys.put("dimension_type", dimensionTypes.stream().map(CatalogDimensionType::key).toList());
        registryKeys.put("level_stem", levelStems.stream().map(CatalogLevelStem::key).toList());
        registryKeys.put("block_tag", List.copyOf(blockTags.keySet()));
        List<CatalogNamespace> namespaces = namespaces(registryKeys, context);
        return new WorldgenCatalog(WorldgenCatalog.FORMAT, context.minecraftVersion(), context.platform(),
                context.capturedAtEpochMillis(), WorldgenCatalogFingerprint.of(namespaces, registryKeys), namespaces,
                biomes, placedFeatures, structures, dimensionTypes, levelStems, blockTags);
    }

    private static Map<String, List<String>> referencedBlockTags(List<CatalogPlacedFeature> features,
                                                                 Map<String, List<String>> available) {
        Set<String> referenced = new TreeSet<>();
        for (CatalogPlacedFeature feature : features) {
            collectTagMatches(feature.config(), referenced);
        }
        Map<String, List<String>> tags = new TreeMap<>();
        for (String tag : referenced) {
            List<String> members = available.get(tag);
            if (members != null) {
                tags.put(tag, members);
            }
        }
        return tags;
    }

    private static void collectTagMatches(JsonElement element, Set<String> tags) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement entry : element.getAsJsonArray()) {
                collectTagMatches(entry, tags);
            }
            return;
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement predicate = object.get("predicate_type");
        JsonElement tag = object.get("tag");
        if (predicate != null && predicate.isJsonPrimitive() && predicate.getAsString().endsWith("tag_match")
                && tag != null && tag.isJsonPrimitive()) {
            String key = tag.getAsString();
            tags.add(key.contains(":") ? key : MINECRAFT + ":" + key);
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            collectTagMatches(entry.getValue(), tags);
        }
    }

    private static List<CatalogNamespace> namespaces(Map<String, List<String>> registryKeys, CatalogCaptureContext context) {
        Map<String, CatalogNamespace> namespaces = new TreeMap<>();
        for (NativeModMetadata mod : context.mods()) {
            namespaces.put(mod.id(), new CatalogNamespace(mod.id(), mod.displayName(), mod.version()));
        }
        for (List<String> keys : registryKeys.values()) {
            for (String key : keys) {
                String namespace = WorldgenCatalogIndex.namespaceOf(key);
                if (!namespaces.containsKey(namespace)) {
                    namespaces.put(namespace, fallbackNamespace(namespace, context));
                }
            }
        }
        if (!namespaces.containsKey(MINECRAFT)) {
            namespaces.put(MINECRAFT, fallbackNamespace(MINECRAFT, context));
        }
        return List.copyOf(namespaces.values());
    }

    private static CatalogNamespace fallbackNamespace(String namespace, CatalogCaptureContext context) {
        if (MINECRAFT.equals(namespace)) {
            return new CatalogNamespace(MINECRAFT, "Minecraft", context.minecraftVersion());
        }
        return new CatalogNamespace(namespace, namespace, "");
    }

    private static List<CatalogBiome> biomes(List<WorldgenBiome> biomes) {
        List<CatalogBiome> result = new ArrayList<>(biomes.size());
        for (WorldgenBiome biome : biomes) {
            List<CatalogSpawn> spawns = new ArrayList<>(biome.spawns().size());
            for (WorldgenSpawn spawn : biome.spawns()) {
                spawns.add(new CatalogSpawn(spawn.category(), spawn.entity(), spawn.weight(), spawn.minCount(), spawn.maxCount()));
            }
            result.add(new CatalogBiome(biome.key(), biome.temperature(), biome.downfall(), biome.hasPrecipitation(),
                    biome.tags(), biome.featureSteps(), spawns, biome.definition()));
        }
        return result;
    }

    private static List<CatalogPlacedFeature> placedFeatures(List<WorldgenPlacedFeature> features) {
        List<CatalogPlacedFeature> result = new ArrayList<>(features.size());
        for (WorldgenPlacedFeature feature : features) {
            result.add(new CatalogPlacedFeature(feature.key(), feature.feature(), feature.featureType(),
                    feature.config(), feature.placement()));
        }
        return result;
    }

    private static List<CatalogStructure> structures(List<WorldgenStructure> structures) {
        List<CatalogStructure> result = new ArrayList<>(structures.size());
        for (WorldgenStructure structure : structures) {
            result.add(new CatalogStructure(structure.key(), structure.type(), structure.step(),
                    structure.terrainAdaptation(), structure.biomeTag(), structure.biomes(), structure.structureSets()));
        }
        return result;
    }

    private static List<CatalogDimensionType> dimensionTypes(List<WorldgenDimensionType> types) {
        List<CatalogDimensionType> result = new ArrayList<>(types.size());
        for (WorldgenDimensionType type : types) {
            result.add(new CatalogDimensionType(type.key(), type.minY(), type.height(), type.logicalHeight(), type.definition()));
        }
        return result;
    }

    private static List<CatalogLevelStem> levelStems(List<WorldgenLevelStem> stems) {
        List<CatalogLevelStem> result = new ArrayList<>(stems.size());
        for (WorldgenLevelStem stem : stems) {
            result.add(new CatalogLevelStem(stem.key(), stem.dimensionType(), stem.generatorType(),
                    stem.biomeSourceType(), stem.generator()));
        }
        return result;
    }
}
