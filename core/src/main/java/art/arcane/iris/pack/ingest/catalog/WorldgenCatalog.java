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

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record WorldgenCatalog(
        int format,
        String minecraftVersion,
        String platform,
        long capturedAtEpochMillis,
        String contentFingerprint,
        List<CatalogNamespace> namespaces,
        List<CatalogBiome> biomes,
        List<CatalogPlacedFeature> placedFeatures,
        List<CatalogStructure> structures,
        List<CatalogDimensionType> dimensionTypes,
        List<CatalogLevelStem> levelStems,
        Map<String, List<String>> blockTags) {
    public static final int FORMAT = 2;

    public WorldgenCatalog {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(contentFingerprint, "contentFingerprint");
        namespaces = List.copyOf(Objects.requireNonNull(namespaces, "namespaces"));
        biomes = List.copyOf(Objects.requireNonNull(biomes, "biomes"));
        placedFeatures = List.copyOf(Objects.requireNonNull(placedFeatures, "placedFeatures"));
        structures = List.copyOf(Objects.requireNonNull(structures, "structures"));
        dimensionTypes = List.copyOf(Objects.requireNonNull(dimensionTypes, "dimensionTypes"));
        levelStems = List.copyOf(Objects.requireNonNull(levelStems, "levelStems"));
        blockTags = Map.copyOf(Objects.requireNonNull(blockTags, "blockTags"));
    }
}
