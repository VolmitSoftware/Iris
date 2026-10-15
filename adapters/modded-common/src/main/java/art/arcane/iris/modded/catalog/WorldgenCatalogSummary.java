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
import art.arcane.iris.pack.ingest.catalog.CatalogStructure;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogIndex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

public final class WorldgenCatalogSummary {
    private static final String VANILLA_PREFIX = "minecraft:";

    private WorldgenCatalogSummary() {
    }

    public static List<NamespaceSummary> namespaces(WorldgenCatalog catalog) {
        Map<String, Builder> builders = new TreeMap<>();
        for (CatalogNamespace namespace : catalog.namespaces()) {
            builders.put(namespace.namespace(), new Builder(namespace));
        }
        collect(builders, catalog.biomes(), CatalogBiome::key, Builder::biomes);
        collect(builders, catalog.placedFeatures(), CatalogPlacedFeature::key, Builder::placedFeatures);
        collect(builders, catalog.structures(), CatalogStructure::key, Builder::structures);
        collect(builders, catalog.dimensionTypes(), CatalogDimensionType::key, Builder::dimensionTypes);
        collect(builders, catalog.levelStems(), CatalogLevelStem::key, Builder::levelStems);
        for (CatalogPlacedFeature feature : catalog.placedFeatures()) {
            builder(builders, WorldgenCatalogIndex.namespaceOf(feature.key())).featureTypes()
                    .merge(feature.featureType(), 1, Integer::sum);
        }
        List<NamespaceSummary> summaries = new ArrayList<>(builders.size());
        for (Builder builder : builders.values()) {
            summaries.add(builder.build());
        }
        return summaries;
    }

    public static Optional<NamespaceSummary> namespace(WorldgenCatalog catalog, String namespace) {
        for (NamespaceSummary summary : namespaces(catalog)) {
            if (summary.namespace().namespace().equals(namespace)) {
                return Optional.of(summary);
            }
        }
        return Optional.empty();
    }

    public static String featureTypes(List<TypeCount> types, int limit) {
        List<String> labels = new ArrayList<>(Math.min(types.size(), limit));
        for (int index = 0; index < types.size() && index < limit; index++) {
            TypeCount type = types.get(index);
            labels.add(shortType(type.type()) + " " + type.count());
        }
        return withRemainder(String.join(", ", labels), types.size() - labels.size());
    }

    public static String limited(List<String> keys, String namespace, int limit) {
        List<String> labels = new ArrayList<>(Math.min(keys.size(), limit));
        String ownPrefix = namespace + ":";
        for (int index = 0; index < keys.size() && index < limit; index++) {
            String key = keys.get(index);
            labels.add(key.startsWith(ownPrefix) ? key.substring(ownPrefix.length()) : key);
        }
        return withRemainder(String.join(", ", labels), keys.size() - labels.size());
    }

    private static String shortType(String type) {
        return type.startsWith(VANILLA_PREFIX) ? type.substring(VANILLA_PREFIX.length()) : type;
    }

    private static String withRemainder(String joined, int remainder) {
        return remainder > 0 ? joined + " (+" + remainder + " more)" : joined;
    }

    private static <T> void collect(Map<String, Builder> builders, List<T> entries, Function<T, String> key,
                                    Function<Builder, List<String>> target) {
        for (T entry : entries) {
            String entryKey = key.apply(entry);
            target.apply(builder(builders, WorldgenCatalogIndex.namespaceOf(entryKey))).add(entryKey);
        }
    }

    private static Builder builder(Map<String, Builder> builders, String namespace) {
        return builders.computeIfAbsent(namespace, (String missing) -> new Builder(new CatalogNamespace(missing, missing, "")));
    }

    public record TypeCount(String type, int count) {
        public TypeCount {
            Objects.requireNonNull(type, "type");
        }
    }

    public record NamespaceSummary(
            CatalogNamespace namespace,
            List<String> biomes,
            List<String> placedFeatures,
            List<TypeCount> featureTypes,
            List<String> structures,
            List<String> dimensionTypes,
            List<String> levelStems) {
        public NamespaceSummary {
            Objects.requireNonNull(namespace, "namespace");
            biomes = List.copyOf(biomes);
            placedFeatures = List.copyOf(placedFeatures);
            featureTypes = List.copyOf(featureTypes);
            structures = List.copyOf(structures);
            dimensionTypes = List.copyOf(dimensionTypes);
            levelStems = List.copyOf(levelStems);
        }

        public boolean hasContent() {
            return !biomes.isEmpty() || !placedFeatures.isEmpty() || !structures.isEmpty()
                    || !dimensionTypes.isEmpty() || !levelStems.isEmpty();
        }
    }

    private record Builder(
            CatalogNamespace namespace,
            List<String> biomes,
            List<String> placedFeatures,
            Map<String, Integer> featureTypes,
            List<String> structures,
            List<String> dimensionTypes,
            List<String> levelStems) {
        private Builder(CatalogNamespace namespace) {
            this(namespace, new ArrayList<>(), new ArrayList<>(), new TreeMap<>(), new ArrayList<>(), new ArrayList<>(),
                    new ArrayList<>());
        }

        private NamespaceSummary build() {
            List<TypeCount> types = new ArrayList<>(featureTypes.size());
            for (Map.Entry<String, Integer> entry : featureTypes.entrySet()) {
                types.add(new TypeCount(entry.getKey(), entry.getValue()));
            }
            types.sort(Comparator.comparingInt(TypeCount::count).reversed().thenComparing(TypeCount::type));
            return new NamespaceSummary(namespace, sorted(biomes), sorted(placedFeatures), types, sorted(structures),
                    sorted(dimensionTypes), sorted(levelStems));
        }

        private static List<String> sorted(List<String> keys) {
            List<String> copy = new ArrayList<>(keys);
            copy.sort(null);
            return copy;
        }
    }
}
