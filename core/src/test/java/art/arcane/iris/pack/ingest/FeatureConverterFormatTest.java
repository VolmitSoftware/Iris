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

import art.arcane.iris.pack.ingest.catalog.CatalogPlacedFeature;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogIndex;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FeatureConverterFormatTest {
    private static final VanillaHeights.VerticalBounds OVERWORLD = new VanillaHeights.VerticalBounds(-64, 384);
    private static final String PATCH_TAIL = """
            {"type": "minecraft:offset",
             "x": {"type": "minecraft:trapezoid", "max": 7, "min": -7, "plateau": 0},
             "y": {"type": "minecraft:trapezoid", "max": 3, "min": -3, "plateau": 0},
             "z": {"type": "minecraft:trapezoid", "max": 7, "min": -7, "plateau": 0}},
            {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}}
            """;

    @Test
    public void currentVanillaIronOreSplitsIntoStoneAndDeepslateDeposits() throws Exception {
        CatalogPlacedFeature iron = feature("minecraft:ore", """
                {"type": "minecraft:ore", "discard_chance_on_air_exposure": 0.0, "size": 9, "targets": [
                  {"state": "minecraft:iron_ore", "target": {"predicate_type": "minecraft:any_of", "rules": [
                    {"predicate_type": "minecraft:all_of", "rules": [
                      {"predicate_type": "minecraft:tag_match", "tag": "minecraft:height_specific_ore_replaceables"},
                      {"max_inclusive": 2031, "min_inclusive": 0, "predicate_type": "minecraft:height_match"}]},
                    {"predicate_type": "minecraft:all_of", "rules": [
                      {"predicate_type": "minecraft:not", "rule": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:height_specific_ore_replaceables"}},
                      {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}]}]}},
                  {"state": "minecraft:deepslate_iron_ore", "target": {"predicate_type": "minecraft:any_of", "rules": [
                    {"predicate_type": "minecraft:all_of", "rules": [
                      {"predicate_type": "minecraft:tag_match", "tag": "minecraft:height_specific_ore_replaceables"},
                      {"max_inclusive": 8, "min_inclusive": -2032, "predicate_type": "minecraft:height_match"}]},
                    {"predicate_type": "minecraft:all_of", "rules": [
                      {"predicate_type": "minecraft:not", "rule": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:height_specific_ore_replaceables"}},
                      {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}]}]}}]}
                """, """
                [{"type": "minecraft:count", "count": 10}, {"type": "minecraft:in_square"},
                 {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "max_inclusive": {"absolute": 56}, "min_inclusive": {"absolute": -24}}},
                 {"type": "minecraft:biome"}]
                """);

        List<JsonObject> deposits = OreDepositConverter.convert(iron, OVERWORLD, -64, Map.of());

        assertEquals(2, deposits.size());
        assertEquals("minecraft:iron_ore", deposits.get(0).getAsJsonArray("palette").get(0).getAsJsonObject().get("block").getAsString());
        assertEquals(40, deposits.get(0).get("minHeight").getAsInt());
        assertEquals(120, deposits.get(0).get("maxHeight").getAsInt());
        assertEquals("TRIANGLE", deposits.get(0).get("heightDistribution").getAsString());
        assertEquals(10, deposits.get(0).get("maxPerChunk").getAsInt());
        assertEquals(List.of("minecraft:deepslate"), IngestJson.strings(deposits.get(1).get("replaceableBlocks")));
    }

    @Test
    public void convertedOresPlaceInTheirHostsRegardlessOfTheTerrainSurface() throws IngestConversionException {
        CatalogPlacedFeature clay = new CatalogPlacedFeature("examplemod:alpha/clay_patch", "examplemod:alpha/clay_patch",
                "minecraft:ore", JsonParser.parseString("""
                {"discard_chance_on_air_exposure": 0.0, "size": 20,
                 "targets": [{"state": {"Name": "minecraft:clay"}, "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:sand"}}]}
                """), JsonParser.parseString("""
                [{"type": "minecraft:count", "count": 6}, {"type": "minecraft:in_square"},
                 {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 57}, "max_inclusive": {"absolute": 62}}},
                 {"type": "minecraft:biome"}]
                """));

        List<JsonObject> deposits = OreDepositConverter.convert(clay, OVERWORLD, -64, Map.of());

        assertEquals(1, deposits.size());
        assertEquals("FULL_HEIGHT", deposits.getFirst().get("placementScope").getAsString());
        assertEquals(List.of("minecraft:sand"), IngestJson.strings(deposits.getFirst().get("replaceableBlocks")));
    }

    @Test
    public void environmentScanOrePassesThroughWithTheModifierNamed() {
        CatalogPlacedFeature buried = feature("minecraft:ore",
                "{\"size\": 4, \"targets\": [{\"state\": \"minecraft:stone\", \"target\": {\"predicate_type\": \"minecraft:always_true\"}}]}",
                "[{\"type\": \"minecraft:environment_scan\", \"direction_of_search\": \"down\", \"max_steps\": 16, \"target_condition\": {\"type\": \"minecraft:solid\"}}]");

        IngestConversionException failure = assertThrows(IngestConversionException.class, () -> OreDepositConverter.convert(buried, OVERWORLD, -64, Map.of()));

        assertTrue(failure.getMessage(), failure.getMessage().contains("minecraft:environment_scan"));
    }

    @Test
    public void densePatchCountsBeforeAndAfterTheHeightmapSaturateCoverage() throws Exception {
        CatalogPlacedFeature lavender = feature(PatchDecoratorConverter.SIMPLE_BLOCK,
                "{\"type\": \"minecraft:simple_block\", \"to_place\": {\"id\": \"examplemod:lavender\"}}",
                "[{\"type\": \"minecraft:count\", \"count\": 15}, {\"type\": \"minecraft:in_square\"},"
                        + "{\"type\": \"minecraft:heightmap\", \"heightmap\": \"WORLD_SURFACE_WG\"}, {\"type\": \"minecraft:biome\"},"
                        + "{\"type\": \"minecraft:count\", \"count\": 96}," + PATCH_TAIL + "]");

        JsonObject decorator = PatchDecoratorConverter.convert(lavender, index(), 0D);

        assertEquals(1.0D - Math.exp(-(15.0D * 96.0D * 0.25D) / 256.0D), decorator.get("chance").getAsDouble(), 0.000001D);
        assertTrue(decorator.get("chance").getAsDouble() < 1.0D);
    }

    @Test
    public void leafBlockedHeightmapsLoseTheCanopyShareOfTheirPlacements() throws Exception {
        CatalogPlacedFeature lavender = feature(PatchDecoratorConverter.SIMPLE_BLOCK,
                "{\"type\": \"minecraft:simple_block\", \"to_place\": {\"id\": \"examplemod:lavender\"}}",
                "[{\"type\": \"minecraft:count\", \"count\": 4}, {\"type\": \"minecraft:in_square\"},"
                        + "{\"type\": \"minecraft:heightmap\", \"heightmap\": \"MOTION_BLOCKING\"}, {\"type\": \"minecraft:biome\"}]");
        CatalogPlacedFeature underLeaves = feature(PatchDecoratorConverter.SIMPLE_BLOCK,
                "{\"type\": \"minecraft:simple_block\", \"to_place\": {\"id\": \"examplemod:lavender\"}}",
                "[{\"type\": \"minecraft:count\", \"count\": 4}, {\"type\": \"minecraft:in_square\"},"
                        + "{\"type\": \"minecraft:heightmap\", \"heightmap\": \"MOTION_BLOCKING_NO_LEAVES\"}, {\"type\": \"minecraft:biome\"}]");

        assertEquals(1.0D - Math.exp(-(4.0D * 0.5D) / 256.0D),
                PatchDecoratorConverter.convert(lavender, index(), 0.5D).get("chance").getAsDouble(), 0.000001D);
        assertEquals(1.0D - Math.exp(-4.0D / 256.0D),
                PatchDecoratorConverter.convert(underLeaves, index(), 0.5D).get("chance").getAsDouble(), 0.000001D);
    }

    @Test
    public void unnamespacedTypesAndClampedCountsConvert() throws Exception {
        CatalogPlacedFeature flowers = feature("simple_block", "{\"type\": \"simple_block\", \"to_place\": {\"id\": \"minecraft:poppy\"}}", """
                [{"type": "rarity_filter", "chance": 2}, {"type": "minecraft:in_square"},
                 {"type": "count", "count": {"type": "minecraft:clamped", "min_inclusive": 0, "max_inclusive": 64,
                   "source": {"type": "minecraft:uniform", "min_inclusive": -3, "max_inclusive": 1}}},
                 {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"},
                 {"type": "minecraft:offset", "x": {"type": "minecraft:trapezoid", "plateau": 0, "min": -7, "max": 7},
                   "y": {"type": "minecraft:trapezoid", "plateau": 0, "min": -3, "max": 3}, "z": {"type": "minecraft:trapezoid", "plateau": 0, "min": -7, "max": 7}},
                 {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
                   {"type": "minecraft:matching_blocks", "blocks": ["minecraft:grass_block"], "offset": [0, -1, 0]}]}},
                 {"type": "minecraft:biome"}]
                """);

        JsonObject decorator = PatchDecoratorConverter.convert(flowers, index(), 0D);

        assertEquals(1.0D - Math.exp(-(0.5D * 0.2D * 0.25D) / 256.0D), decorator.get("chance").getAsDouble(), 0.000001D);
        assertEquals("minecraft:grass_block", decorator.getAsJsonArray("whitelist").get(0).getAsJsonObject().get("block").getAsString());
    }

    @Test
    public void underwaterAndRegistryProviderPatchesPassThrough() {
        CatalogPlacedFeature seagrass = feature(PatchDecoratorConverter.SIMPLE_BLOCK,
                "{\"to_place\": {\"id\": \"minecraft:seagrass\"}}",
                "[{\"type\": \"minecraft:heightmap\", \"heightmap\": \"OCEAN_FLOOR_WG\"}]");
        CatalogPlacedFeature plains = feature(PatchDecoratorConverter.SIMPLE_BLOCK,
                "{\"type\": \"minecraft:simple_block\", \"to_place\": \"minecraft:flower_plain\"}",
                "[{\"type\": \"minecraft:heightmap\", \"heightmap\": \"MOTION_BLOCKING\"}]");

        assertThrows(IngestConversionException.class, () -> PatchDecoratorConverter.convert(seagrass, index(), 0D));
        IngestConversionException reference = assertThrows(IngestConversionException.class, () -> PatchDecoratorConverter.convert(plains, index(), 0D));
        assertTrue(reference.getMessage(), reference.getMessage().contains("minecraft:flower_plain"));
    }

    private static CatalogPlacedFeature feature(String type, String config, String placement) {
        return new CatalogPlacedFeature("examplemod:sample", "examplemod:sample", type,
                JsonParser.parseString(config), JsonParser.parseString(placement));
    }

    private static WorldgenCatalogIndex index() {
        return WorldgenCatalogIndex.of(new WorldgenCatalog(WorldgenCatalog.FORMAT, "26.3", "fabric", 0L, "",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of()));
    }
}
