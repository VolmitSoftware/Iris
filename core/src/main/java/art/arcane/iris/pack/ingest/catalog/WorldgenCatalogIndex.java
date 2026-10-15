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


package art.arcane.iris.pack.ingest.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class WorldgenCatalogIndex {
    private final WorldgenCatalog catalog;
    private final Map<String, CatalogBiome> biomes;
    private final Map<String, CatalogPlacedFeature> placedFeatures;
    private final Map<String, CatalogStructure> structures;
    private final Map<String, CatalogDimensionType> dimensionTypes;
    private final Map<String, CatalogLevelStem> levelStems;

    private WorldgenCatalogIndex(WorldgenCatalog catalog) {
        this.catalog = catalog;
        this.biomes = index(catalog.biomes(), CatalogBiome::key);
        this.placedFeatures = index(catalog.placedFeatures(), CatalogPlacedFeature::key);
        this.structures = index(catalog.structures(), CatalogStructure::key);
        this.dimensionTypes = index(catalog.dimensionTypes(), CatalogDimensionType::key);
        this.levelStems = index(catalog.levelStems(), CatalogLevelStem::key);
    }

    public static WorldgenCatalogIndex of(WorldgenCatalog catalog) {
        return new WorldgenCatalogIndex(Objects.requireNonNull(catalog, "catalog"));
    }

    public static String namespaceOf(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? "minecraft" : key.substring(0, colon);
    }

    public WorldgenCatalog catalog() {
        return catalog;
    }

    public Optional<CatalogBiome> biome(String key) {
        return Optional.ofNullable(biomes.get(key));
    }

    public Optional<CatalogPlacedFeature> placedFeature(String key) {
        return Optional.ofNullable(placedFeatures.get(key));
    }

    public Optional<CatalogStructure> structure(String key) {
        return Optional.ofNullable(structures.get(key));
    }

    public Optional<CatalogDimensionType> dimensionType(String key) {
        return Optional.ofNullable(dimensionTypes.get(key));
    }

    public Optional<List<String>> blockTag(String key) {
        return Optional.ofNullable(catalog.blockTags().get(key));
    }

    public Optional<CatalogLevelStem> levelStem(String key) {
        return Optional.ofNullable(levelStems.get(key));
    }

    public List<CatalogBiome> biomesIn(String namespace) {
        List<CatalogBiome> result = new ArrayList<>();
        for (CatalogBiome biome : catalog.biomes()) {
            if (namespaceOf(biome.key()).equals(namespace)) {
                result.add(biome);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<CatalogBiome> biomesDeclaring(String placedFeatureKey) {
        List<CatalogBiome> result = new ArrayList<>();
        for (CatalogBiome biome : catalog.biomes()) {
            for (List<String> step : biome.featureSteps()) {
                if (step.contains(placedFeatureKey)) {
                    result.add(biome);
                    break;
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> key) {
        Map<String, T> result = new LinkedHashMap<>(Math.max(16, values.size() * 2));
        for (T value : values) {
            result.put(key.apply(value), value);
        }
        return Collections.unmodifiableMap(result);
    }
}
