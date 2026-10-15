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

package art.arcane.iris.pack.ingest;

import art.arcane.iris.pack.ingest.catalog.CatalogBiome;
import art.arcane.iris.pack.ingest.catalog.CatalogDimensionType;
import art.arcane.iris.pack.ingest.catalog.CatalogNamespace;
import art.arcane.iris.pack.ingest.catalog.CatalogPlacedFeature;
import art.arcane.iris.pack.ingest.catalog.CatalogStructure;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class ModIngestFixture {
    static final String MOD = "examplemod";
    static final String OTHER_MOD = "othermod";
    static final int ORE_STEP = 6;
    static final int VEGETAL_STEP = 9;
    private static final List<String> OVERWORLD = List.of("minecraft:is_overworld");
    private static final String STONE_THEN_DEEPSLATE_TIN = """
            {
              "type": "minecraft:ore",
              "discard_chance_on_air_exposure": 0.0,
              "size": 7,
              "targets": [
                {
                  "state": "examplemod:tin_ore",
                  "target": {
                    "predicate_type": "minecraft:any_of",
                    "rules": [
                      {
                        "predicate_type": "minecraft:all_of",
                        "rules": [
                          {"predicate_type": "minecraft:tag_match", "tag": "minecraft:height_specific_ore_replaceables"},
                          {"predicate_type": "minecraft:height_match", "min_inclusive": 0, "max_inclusive": 2031}
                        ]
                      },
                      {
                        "predicate_type": "minecraft:all_of",
                        "rules": [
                          {"predicate_type": "minecraft:not", "rule": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:height_specific_ore_replaceables"}},
                          {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}
                        ]
                      }
                    ]
                  }
                },
                {
                  "state": {"id": "examplemod:deepslate_tin_ore"},
                  "target": {
                    "predicate_type": "minecraft:any_of",
                    "rules": [
                      {
                        "predicate_type": "minecraft:all_of",
                        "rules": [
                          {"predicate_type": "minecraft:tag_match", "tag": "minecraft:height_specific_ore_replaceables"},
                          {"predicate_type": "minecraft:height_match", "min_inclusive": -2032, "max_inclusive": 8}
                        ]
                      },
                      {
                        "predicate_type": "minecraft:all_of",
                        "rules": [
                          {"predicate_type": "minecraft:not", "rule": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:height_specific_ore_replaceables"}},
                          {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}
                        ]
                      }
                    ]
                  }
                }
              ]
            }
            """;
    private static final String TIN_PLACEMENT = """
            [
              {"type": "minecraft:count", "count": 12},
              {"type": "minecraft:rarity_filter", "chance": 2},
              {"type": "minecraft:in_square"},
              {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 16}, "max_inclusive": {"below_top": 20}}},
              {"type": "minecraft:biome"}
            ]
            """;
    private static final String RUBY_CONFIG = """
            {
              "discard_chance_on_air_exposure": 0.5,
              "size": 3,
              "targets": [
                {"state": {"Name": "examplemod:ruby_ore", "Properties": {"lit": "false"}}, "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:stone"}}
              ]
            }
            """;
    private static final String RUBY_PLACEMENT = """
            [
              {"type": "minecraft:count", "count": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 3}},
              {"type": "minecraft:in_square"},
              {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": -16}, "max_inclusive": {"absolute": 48}, "plateau": 0}},
              {"type": "minecraft:biome"}
            ]
            """;
    private static final String SAPPHIRE_CONFIG = """
            {"size": 4, "targets": [{"state": "examplemod:sapphire_ore", "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:base_stone_overworld"}}]}
            """;
    private static final String SAPPHIRE_PLACEMENT = """
            [
              {"type": "count", "count": 2},
              {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 0}, "max_inclusive": {"absolute": 32}}}
            ]
            """;
    private static final String GLOW_FLOWERS_CONFIG = """
            {
              "type": "minecraft:simple_block",
              "to_place": {
                "type": "minecraft:weighted",
                "entries": [
                  {"data": "examplemod:glowcap", "weight": 3},
                  {"data": {"id": "examplemod:glowbell", "properties": {"lit": "true"}}, "weight": 1}
                ]
              }
            }
            """;
    private static final String GLOW_FLOWERS_PLACEMENT = """
            [
              {"type": "minecraft:rarity_filter", "chance": 4},
              {"type": "minecraft:in_square"},
              {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"},
              {"type": "minecraft:biome"},
              {"type": "minecraft:count", "count": 32},
              {"type": "minecraft:offset", "x": {"type": "minecraft:trapezoid", "min": -7, "max": 7, "plateau": 0}, "y": {"type": "minecraft:trapezoid", "min": -3, "max": 3, "plateau": 0}, "z": {"type": "minecraft:trapezoid", "min": -7, "max": 7, "plateau": 0}},
              {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [{"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}, {"type": "minecraft:matching_blocks", "blocks": "minecraft:grass_block", "offset": [0, -1, 0]}]}}
            ]
            """;
    private static final String BLUEBELL_PATCH_CONFIG = """
            {
              "tries": 64,
              "xz_spread": 7,
              "y_spread": 3,
              "feature": {
                "feature": {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:simple_state_provider", "state": {"Name": "examplemod:bluebell"}}}},
                "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_blocks", "blocks": ["minecraft:air"]}}]
              }
            }
            """;
    private static final String BLUEBELL_PATCH_PLACEMENT = """
            [
              {"type": "minecraft:rarity_filter", "chance": 2},
              {"type": "minecraft:in_square"},
              {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"},
              {"type": "minecraft:biome"}
            ]
            """;
    private static final String BURIED_TIN_PLACEMENT = """
            [
              {"type": "minecraft:count", "count": 2},
              {"type": "minecraft:environment_scan", "direction_of_search": "down", "max_steps": 8, "target_condition": {"type": "minecraft:solid"}}
            ]
            """;
    private static final String WEIRD_ORE_CONFIG = """
            {"size": 5, "targets": [{"state": "examplemod:weird_ore", "target": {"predicate_type": "minecraft:tag_match", "tag": "examplemod:custom_stone"}}]}
            """;

    private ModIngestFixture() {
    }

    static Path writeSourcePack(Path root) throws IOException {
        Path pack = root.resolve("source");
        write(pack, "dimensions/overworld.json", """
                {
                  "name": "Overworld",
                  "environment": "NORMAL",
                  "regions": ["hot", "temperate"],
                  "dimensionHeight": {"min": -128, "max": 320},
                  "deposits": [{"minHeight": 10, "maxHeight": 40, "palette": [{"block": "minecraft:coal_ore"}]}],
                  "importedStructures": {"disabled": ["examplemod:old_ruin"]},
                  "importedFeatures": {"enabled": false, "disabled": ["minecraft:ore_diamond"], "disabledSteps": ["LAKES"]}
                }
                """);
        write(pack, "regions/hot.json", """
                {
                  "name": "Hot",
                  "landBiomes": ["hot/desert", "hot/savanna"],
                  "seaBiomes": ["hot/warm-ocean"],
                  "shoreBiomes": ["hot/beach"],
                  "caveBiomes": ["caves/dripstone"]
                }
                """);
        write(pack, "regions/temperate.json", """
                {
                  "name": "Temperate",
                  "landBiomes": ["temperate/forest", "temperate/plains"],
                  "seaBiomes": ["temperate/ocean"],
                  "shoreBiomes": ["temperate/beach"]
                }
                """);
        write(pack, "biomes/hot/desert.json", """
                {
                  "name": "Desert",
                  "derivative": "minecraft:desert",
                  "layers": [{"palette": [{"block": "minecraft:sand"}]}],
                  "objects": [{"chance": 0.1, "place": ["desert/cactus"]}]
                }
                """);
        write(pack, "biomes/hot/savanna.json", """
                {"name": "Savanna", "derivative": "minecraft:savanna", "children": ["hot/desert"]}
                """);
        write(pack, "biomes/hot/warm-ocean.json", """
                {"name": "Warm Ocean", "derivative": "minecraft:warm_ocean"}
                """);
        write(pack, "biomes/hot/beach.json", """
                {"name": "Hot Beach", "derivative": "minecraft:beach"}
                """);
        write(pack, "biomes/caves/dripstone.json", """
                {"name": "Dripstone", "derivative": "minecraft:dripstone_caves"}
                """);
        write(pack, "biomes/temperate/forest.json", """
                {
                  "name": "Forest",
                  "derivative": "minecraft:forest",
                  "vanillaDerivative": "minecraft:forest",
                  "customDerivitives": [{"id": "forest", "grassColor": "#91BD59"}],
                  "biomeScatter": ["minecraft:birch_forest"],
                  "children": ["temperate/plains"],
                  "objects": [{"chance": 0.4, "place": ["trees/oak"]}],
                  "proceduralObjects": {"trees": [{"chance": 0.2}], "ruins": [{"chance": 0.01}]},
                  "decorators": [{"chance": 0.3, "palette": [{"block": "minecraft:short_grass"}]}]
                }
                """);
        write(pack, "biomes/temperate/plains.json", """
                {"name": "Plains", "derivative": "plains"}
                """);
        write(pack, "biomes/temperate/ocean.json", """
                {"name": "Ocean", "derivative": "minecraft:ocean"}
                """);
        write(pack, "biomes/temperate/beach.json", """
                {"name": "Beach", "derivative": "minecraft:beach"}
                """);
        write(pack, "biomes/unused/orphan.json", """
                {"name": "Orphan", "derivative": "minecraft:forest"}
                """);
        write(pack, ".git/HEAD", "ref: refs/heads/main\n");
        write(pack, "source.code-workspace", "{}");
        return pack;
    }

    static WorldgenCatalog catalog() {
        List<CatalogBiome> biomes = new ArrayList<>();
        List<String> sharedOres = List.of("minecraft:ore_iron", MOD + ":ore_tin", MOD + ":ore_tin_buried");
        biomes.add(biome("minecraft:desert", 2.0F, 0.0F, OVERWORLD, steps(ORE_STEP, concat(sharedOres, MOD + ":ore_sapphire"))));
        biomes.add(biome("minecraft:savanna", 1.2F, 0.4F, OVERWORLD, steps(ORE_STEP, sharedOres)));
        biomes.add(biome("minecraft:warm_ocean", 0.5F, 0.5F, List.of("minecraft:is_overworld", "minecraft:is_ocean"), steps(ORE_STEP, sharedOres)));
        biomes.add(biome("minecraft:ocean", 0.5F, 0.5F, List.of("minecraft:is_overworld", "minecraft:is_ocean"), steps(ORE_STEP, sharedOres)));
        biomes.add(biome("minecraft:beach", 0.8F, 0.4F, List.of("minecraft:is_overworld", "minecraft:is_beach"), steps(ORE_STEP, sharedOres)));
        biomes.add(biome("minecraft:dripstone_caves", 0.8F, 0.4F, OVERWORLD, steps(ORE_STEP, sharedOres)));
        biomes.add(biome("minecraft:forest", 0.7F, 0.8F, OVERWORLD, merge(steps(ORE_STEP, sharedOres),
                steps(VEGETAL_STEP, List.of("minecraft:trees_birch_and_oak", MOD + ":glow_flowers")))));
        biomes.add(biome("minecraft:plains", 0.8F, 0.4F, OVERWORLD, merge(steps(ORE_STEP, sharedOres),
                steps(VEGETAL_STEP, List.of(MOD + ":bluebell_patch", OTHER_MOD + ":daisies")))));
        biomes.add(biome("minecraft:river", 0.5F, 0.5F, List.of("minecraft:is_overworld", "minecraft:is_river"), steps(ORE_STEP, sharedOres)));
        biomes.add(biome("minecraft:nether_wastes", 2.0F, 0.0F, List.of("minecraft:is_nether"), steps(ORE_STEP, List.of())));
        biomes.add(biome(MOD + ":scorched_mesa", 1.9F, 0.05F, OVERWORLD, steps(ORE_STEP, concat(sharedOres, MOD + ":ore_ruby"))));
        biomes.add(biome(MOD + ":maple_woods", 0.6F, 0.8F, List.of("minecraft:is_overworld", "c:is_forest"), merge(steps(ORE_STEP, sharedOres),
                steps(VEGETAL_STEP, List.of(MOD + ":maple_trees", MOD + ":glow_flowers", "minecraft:patch_grass")))));
        biomes.add(biome(MOD + ":coral_depths", 0.5F, 0.5F, List.of("minecraft:is_overworld", "minecraft:is_ocean"), steps(ORE_STEP, sharedOres)));
        biomes.add(biome(MOD + ":dune_beach", 1.8F, 0.1F, List.of("minecraft:is_overworld", "minecraft:is_beach"), steps(ORE_STEP, sharedOres)));
        biomes.add(biome(MOD + ":crystal_caves", 0.8F, 0.4F, List.of("minecraft:is_overworld", "c:is_cave"),
                steps(ORE_STEP, concat(sharedOres, MOD + ":weird_ore"))));
        biomes.add(biome(MOD + ":misty_river", 0.5F, 0.5F, List.of("minecraft:is_overworld", "minecraft:is_river"), steps(ORE_STEP, sharedOres)));
        biomes.add(biome(MOD + ":ashen_wastes", 2.0F, 0.0F, List.of("minecraft:is_nether"), steps(ORE_STEP, List.of(MOD + ":nether_ore"))));
        biomes.add(biome(MOD + ":void_garden", 0.5F, 0.5F, List.of(), steps(ORE_STEP, List.of())));
        biomes.add(biome(OTHER_MOD + ":bamboo_marsh", 0.9F, 0.9F, OVERWORLD, steps(VEGETAL_STEP, List.of(OTHER_MOD + ":daisies"))));

        List<CatalogPlacedFeature> features = new ArrayList<>();
        features.add(feature("minecraft:ore_iron", "minecraft:ore", "{\"size\": 9, \"targets\": []}", "[]"));
        features.add(feature(MOD + ":ore_tin", "minecraft:ore", STONE_THEN_DEEPSLATE_TIN, TIN_PLACEMENT));
        features.add(feature(MOD + ":ore_tin_buried", "minecraft:ore", STONE_THEN_DEEPSLATE_TIN, BURIED_TIN_PLACEMENT));
        features.add(feature(MOD + ":ore_ruby", "minecraft:scattered_ore", RUBY_CONFIG, RUBY_PLACEMENT));
        features.add(feature(MOD + ":ore_sapphire", "minecraft:ore", SAPPHIRE_CONFIG, SAPPHIRE_PLACEMENT));
        features.add(feature(MOD + ":weird_ore", "minecraft:ore", WEIRD_ORE_CONFIG, SAPPHIRE_PLACEMENT));
        features.add(feature(MOD + ":nether_ore", "minecraft:ore", WEIRD_ORE_CONFIG, "[]"));
        features.add(feature(MOD + ":glow_flowers", "minecraft:simple_block", GLOW_FLOWERS_CONFIG, GLOW_FLOWERS_PLACEMENT));
        features.add(feature(MOD + ":bluebell_patch", "minecraft:random_patch", BLUEBELL_PATCH_CONFIG, BLUEBELL_PATCH_PLACEMENT));
        features.add(feature(MOD + ":maple_trees", "minecraft:tree", "{\"trunk_provider\": {}}", "[]"));
        features.add(feature(OTHER_MOD + ":daisies", "minecraft:simple_block",
                "{\"to_place\": {\"id\": \"othermod:daisy\"}}", GLOW_FLOWERS_PLACEMENT));

        List<CatalogStructure> structures = List.of(
                structure(MOD + ":maple_tower", List.of(MOD + ":maple_woods")),
                structure(MOD + ":old_ruin", List.of("minecraft:plains")),
                structure(MOD + ":nether_fort", List.of(MOD + ":ashen_wastes")),
                structure("minecraft:village_plains", List.of("minecraft:plains")));
        return new WorldgenCatalog(WorldgenCatalog.FORMAT, "26.3", "fabric", 1000L, "fingerprint-1",
                List.of(new CatalogNamespace(MOD, "Example Mod", "1.0.0"), new CatalogNamespace(OTHER_MOD, "Other Mod", "2.0.0")),
                biomes, features, structures,
                List.of(new CatalogDimensionType("minecraft:overworld", -64, 384, 384, null)),
                List.of());
    }

    static ModIngestRequest request(Path source, Path target, Set<String> namespaces, boolean replace) {
        return new ModIngestRequest(source, target, "overworld", catalog(), namespaces, replace);
    }

    static JsonObject readJson(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static List<String> strings(JsonElement element) {
        List<String> values = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return values;
        }
        for (JsonElement value : element.getAsJsonArray()) {
            values.add(value.getAsString());
        }
        return values;
    }

    static JsonObject findByPaletteBlock(JsonArray entries, String block) {
        for (JsonElement entry : entries) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonArray palette = entry.getAsJsonObject().getAsJsonArray("palette");
            if (palette == null) {
                continue;
            }
            for (JsonElement paletteEntry : palette) {
                if (block.equals(paletteEntry.getAsJsonObject().get("block").getAsString())) {
                    return entry.getAsJsonObject();
                }
            }
        }
        return null;
    }

    static ModIngestItem item(ModIngestReport report, String sourceKey) {
        for (ModIngestItem item : report.items()) {
            if (item.sourceKey().equals(sourceKey)) {
                return item;
            }
        }
        return null;
    }

    static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static CatalogBiome biome(String key, float temperature, float downfall, List<String> tags, List<List<String>> steps) {
        return new CatalogBiome(key, temperature, downfall, true, tags, steps, List.of(), null);
    }

    private static CatalogPlacedFeature feature(String key, String type, String config, String placement) {
        return new CatalogPlacedFeature(key, key, type, JsonParser.parseString(config), JsonParser.parseString(placement));
    }

    private static CatalogStructure structure(String key, List<String> biomes) {
        return new CatalogStructure(key, "minecraft:jigsaw", "surface_structures", "beard_thin", "", biomes, List.of());
    }

    private static List<List<String>> steps(int step, List<String> keys) {
        List<List<String>> steps = new ArrayList<>(11);
        for (int index = 0; index < 11; index++) {
            steps.add(index == step ? keys : List.of());
        }
        return steps;
    }

    private static List<List<String>> merge(List<List<String>> first, List<List<String>> second) {
        List<List<String>> merged = new ArrayList<>(11);
        for (int index = 0; index < 11; index++) {
            merged.add(concat(first.get(index), second.get(index)));
        }
        return merged;
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> values = new ArrayList<>(first);
        values.addAll(second);
        return values;
    }

    private static List<String> concat(List<String> first, String extra) {
        return concat(first, List.of(extra));
    }
}
