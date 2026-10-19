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

package com.volmit.iris.core.gui;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisBiomePaletteLayer;
import com.volmit.iris.engine.object.IrisRegion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

record PaletteCatalog(List<RegionEntry> regions, List<String> biomesOutsideRegions, Map<String, BiomeEntry> biomes) {
    private static final int SUMMARIZED_BLOCK_COUNT = 3;

    static PaletteCatalog load(Engine engine) {
        IrisData data = engine.getData();
        Map<String, BiomeEntry> biomes = loadBiomes(data);
        List<RegionEntry> regions = new ArrayList<>();
        Set<String> biomesInRegions = new HashSet<>();
        for (String regionKey : regionKeysInUse(engine)) {
            IrisRegion region = data.getRegionLoader().load(regionKey);
            if (region == null) {
                continue;
            }
            RegionEntry entry = describeRegion(regionKey, region, biomes);
            regions.add(entry);
            entry.groups().forEach(group -> group.biomeKeys().forEach(key -> collectWithChildren(key, biomes, biomesInRegions)));
        }
        List<String> outside = biomes.keySet().stream().filter(key -> !biomesInRegions.contains(key)).toList();
        return new PaletteCatalog(List.copyOf(regions), outside, Map.copyOf(biomes));
    }

    // A studio focus region is generated even when the dimension's region list doesn't include it.
    private static Set<String> regionKeysInUse(Engine engine) {
        Set<String> keys = new LinkedHashSet<>();
        if (engine.getDimension().hasFocusRegion()) {
            keys.add(engine.getDimension().getFocusRegion());
        }
        keys.addAll(engine.getDimension().getRegions());
        return keys;
    }

    BiomeEntry biome(String key) {
        return key == null ? null : biomes.get(key);
    }

    RegionEntry region(String key) {
        return regions.stream().filter(region -> region.key().equals(key)).findFirst().orElse(null);
    }

    static String summarizeBlocks(IrisBiomePaletteLayer layer) {
        List<String> names = layer.getPalette().stream()
                .map(block -> block.getBlock().replace("minecraft:", "").toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (names.size() <= SUMMARIZED_BLOCK_COUNT) {
            return String.join(", ", names);
        }
        return String.join(", ", names.subList(0, SUMMARIZED_BLOCK_COUNT))
                + " +" + (names.size() - SUMMARIZED_BLOCK_COUNT) + " more";
    }

    private static Map<String, BiomeEntry> loadBiomes(IrisData data) {
        String[] keys = data.getBiomeLoader().getPossibleKeys();
        Map<String, IrisBiome> loaded = new LinkedHashMap<>();
        if (keys == null) {
            return Map.of();
        }
        Arrays.sort(keys);
        for (String key : keys) {
            IrisBiome biome = data.getBiomeLoader().load(key);
            if (biome != null) {
                loaded.put(key, biome);
            }
        }
        Map<String, List<String>> parentsByChild = parentsByChild(loaded);
        Map<String, BiomeEntry> biomes = new LinkedHashMap<>();
        loaded.forEach((key, biome) -> biomes.put(key, new BiomeEntry(
                key,
                biome.getName(),
                summarizePalettes(key, biome),
                biome.getChildren().stream().filter(loaded::containsKey).distinct().toList(),
                parentsByChild.getOrDefault(key, List.of())
        )));
        return biomes;
    }

    private static Map<String, List<String>> parentsByChild(Map<String, IrisBiome> biomes) {
        Map<String, List<String>> parents = new LinkedHashMap<>();
        biomes.forEach((parentKey, parent) -> parent.getChildren().stream().distinct().forEach(childKey ->
                parents.computeIfAbsent(childKey, key -> new ArrayList<>()).add(parentKey)));
        return parents;
    }

    private static List<PaletteSummary> summarizePalettes(String key, IrisBiome biome) {
        return BiomePalette.allOf(key, biome).stream()
                .map(palette -> new PaletteSummary(palette, summarizeBlocks(palette.resolve(biome))))
                .toList();
    }

    private static RegionEntry describeRegion(String key, IrisRegion region, Map<String, BiomeEntry> biomes) {
        List<BiomeGroup> groups = new ArrayList<>();
        addGroup(groups, "Land", region.getLandBiomes(), biomes);
        addGroup(groups, "Shore", region.getShoreBiomes(), biomes);
        addGroup(groups, "Sea", region.getSeaBiomes(), biomes);
        addGroup(groups, "Cave", region.getCaveBiomes(), biomes);
        return new RegionEntry(key, region.getName(), List.copyOf(groups));
    }

    private static void addGroup(List<BiomeGroup> groups, String label, List<String> keys, Map<String, BiomeEntry> biomes) {
        List<String> known = keys.stream().filter(biomes::containsKey).distinct().toList();
        if (!known.isEmpty()) {
            groups.add(new BiomeGroup(label, known));
        }
    }

    private static void collectWithChildren(String key, Map<String, BiomeEntry> biomes, Set<String> collected) {
        BiomeEntry biome = biomes.get(key);
        if (biome == null || !collected.add(key)) {
            return;
        }
        biome.childKeys().forEach(child -> collectWithChildren(child, biomes, collected));
    }

    record RegionEntry(String key, String name, List<BiomeGroup> groups) {
        List<String> allBiomeKeys() {
            return groups.stream().flatMap(group -> group.biomeKeys().stream()).distinct().toList();
        }
    }

    record BiomeGroup(String label, List<String> biomeKeys) {
    }

    /**
     * @param parentKeys the biomes that list this one as a child, which it can appear inside of
     */
    record BiomeEntry(String key, String name, List<PaletteSummary> palettes, List<String> childKeys,
                      List<String> parentKeys) {
        boolean matches(String filter) {
            if (key.toLowerCase().contains(filter) || name.toLowerCase().contains(filter)) {
                return true;
            }
            return palettes.stream().anyMatch(summary -> summary.text().toLowerCase().contains(filter));
        }

        BiomePalette firstPalette() {
            return palettes.isEmpty() ? null : palettes.get(0).palette();
        }
    }

    record PaletteSummary(BiomePalette palette, String blocks) {
        String text() {
            return palette.label() + " · " + blocks;
        }
    }
}
