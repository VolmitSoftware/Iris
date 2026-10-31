package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.testsupport.CompatTestPlatform;

import java.io.File;
import java.io.IOException;
import java.util.Map;

final class SelectionTestPacks {
    private static final String STYLES = """
            "regionStyle": {"style": "SIMPLEX"},
            "continentalStyle": {"style": "SIMPLEX"},
            "landBiomeStyle": {"style": "SIMPLEX"},
            "seaBiomeStyle": {"style": "SIMPLEX"},
            "shoreBiomeStyle": {"style": "SIMPLEX"},
            "caveBiomeStyle": {"style": "SIMPLEX", "fracture": {"style": "SIMPLEX", "multiplier": 20}}
            """;

    private SelectionTestPacks() {
    }

    static File threeRegions(File pack) throws IOException {
        dimension(pack, """
                "regions": ["cold", "warm", "temperate"],
                "regionZoom": 4,
                "continentZoom": 3,
                "landChance": 0.6,
                "carving": [{"id": "deep", "biome": "deep_dark", "worldYRange": {"min": -60, "max": -40}}]
                """);
        CompatTestPlatform.write(pack, "regions/cold.json", """
                {"landBiomes": ["tundra", "taiga"], "seaBiomes": ["frozen_ocean"], "shoreBiomes": ["snowy_beach"], "caveBiomes": ["ice_cave"]}
                """);
        CompatTestPlatform.write(pack, "regions/warm.json", """
                {"rarity": 2, "landBiomeZoom": 2, "landBiomes": ["desert", "savanna", "mesa"], "seaBiomes": ["warm_ocean"],
                 "shoreBiomes": ["beach"], "shoreHeightMin": 2, "shoreHeightMax": 6}
                """);
        CompatTestPlatform.write(pack, "regions/temperate.json", """
                {"landBiomes": ["plains", "forest"], "seaBiomes": ["ocean", "deep_ocean"], "shoreBiomes": ["beach"],
                 "caveBiomes": ["lush_cave", "dripstone_cave"]}
                """);
        biomes(pack, Map.ofEntries(
                Map.entry("tundra", "\"derivative\": \"minecraft:snowy_plains\""),
                Map.entry("taiga", "\"derivative\": \"minecraft:taiga\", \"rarity\": 2"),
                Map.entry("frozen_ocean", "\"derivative\": \"minecraft:frozen_ocean\""),
                Map.entry("snowy_beach", "\"derivative\": \"minecraft:snowy_beach\""),
                Map.entry("ice_cave", "\"derivative\": \"minecraft:frozen_peaks\", \"caveMinDepthBelowSurface\": 20"),
                Map.entry("desert", "\"derivative\": \"minecraft:desert\""),
                Map.entry("savanna", "\"derivative\": \"minecraft:savanna\""),
                Map.entry("mesa", "\"derivative\": \"minecraft:badlands\", \"rarity\": 3"),
                Map.entry("warm_ocean", "\"derivative\": \"minecraft:warm_ocean\""),
                Map.entry("beach", "\"derivative\": \"minecraft:beach\""),
                Map.entry("plains", "\"derivative\": \"minecraft:plains\""),
                Map.entry("forest", "\"derivative\": \"minecraft:forest\", \"children\": [\"birch_forest\"], \"childShrinkFactor\": 2"),
                Map.entry("birch_forest", "\"derivative\": \"minecraft:birch_forest\""),
                Map.entry("ocean", "\"derivative\": \"minecraft:ocean\""),
                Map.entry("deep_ocean", "\"derivative\": \"minecraft:deep_ocean\", \"rarity\": 2"),
                Map.entry("lush_cave", "\"derivative\": \"minecraft:lush_caves\""),
                Map.entry("dripstone_cave", "\"derivative\": \"minecraft:dripstone_caves\", \"rarity\": 2"),
                Map.entry("deep_dark", "\"derivative\": \"minecraft:deep_dark\"")));
        return pack;
    }

    static File childChain(File pack) throws IOException {
        dimension(pack, """
                "regions": ["chain"],
                "landChance": 1.5
                """);
        CompatTestPlatform.write(pack, "regions/chain.json", """
                {"landBiomes": ["a"], "seaBiomes": ["sea"], "shoreBiomes": ["sea"]}
                """);
        biomes(pack, Map.of(
                "a", "\"children\": [\"b\"], \"childStyle\": {\"style\": \"SIMPLEX\"}",
                "b", "\"children\": [\"c\"], \"childStyle\": {\"style\": \"SIMPLEX\"}",
                "c", "\"children\": [\"d\"], \"childStyle\": {\"style\": \"SIMPLEX\"}",
                "d", "\"children\": [\"e\"], \"childStyle\": {\"style\": \"SIMPLEX\"}",
                "e", "\"children\": [\"f\"], \"childStyle\": {\"style\": \"SIMPLEX\"}",
                "f", "\"derivative\": \"minecraft:plains\"",
                "sea", "\"derivative\": \"minecraft:ocean\""));
        return pack;
    }

    static File childChainBesideStillBiome(File pack) throws IOException {
        dimension(pack, """
                "regions": ["chain"],
                "landChance": 1.5
                """);
        CompatTestPlatform.write(pack, "regions/chain.json", """
                {"landBiomes": ["a", "still"], "seaBiomes": ["sea"], "shoreBiomes": ["sea"]}
                """);
        biomes(pack, Map.of(
                "a", "\"children\": [\"b\"], \"childStyle\": {\"style\": \"SIMPLEX\"}",
                "b", "\"children\": [\"c\"], \"childStyle\": {\"style\": \"SIMPLEX\"}",
                "c", "\"derivative\": \"minecraft:plains\"",
                "still", "\"derivative\": \"minecraft:forest\"",
                "sea", "\"derivative\": \"minecraft:ocean\""));
        return pack;
    }

    private static void dimension(File pack, String body) throws IOException {
        CompatTestPlatform.write(pack, "dimensions/" + ExportTestPacks.DIMENSION + ".json", "{" + STYLES + "," + body + "}");
    }

    private static void biomes(File pack, Map<String, String> bodies) throws IOException {
        for (Map.Entry<String, String> biome : bodies.entrySet()) {
            CompatTestPlatform.write(pack, "biomes/" + biome.getKey() + ".json", "{\"name\": \"" + biome.getKey() + "\", " + biome.getValue() + "}");
        }
    }
}
