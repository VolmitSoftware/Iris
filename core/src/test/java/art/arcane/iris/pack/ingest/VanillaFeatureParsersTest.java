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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VanillaFeatureParsersTest {
    private static final double EPSILON = 0.000001D;
    private static final VanillaHeights.VerticalBounds OVERWORLD = new VanillaHeights.VerticalBounds(-64, 384);

    @Test
    public void intProvidersReportMeanAndRange() throws Exception {
        assertStats(7, 7, 7, VanillaIntProviders.stats(json("7")));
        assertStats(4, 4, 4, VanillaIntProviders.stats(json("{\"type\": \"minecraft:constant\", \"value\": 4}")));
        assertStats(2, 1, 3, VanillaIntProviders.stats(json("{\"type\": \"minecraft:uniform\", \"min_inclusive\": 1, \"max_inclusive\": 3}")));
        assertStats(0.2D, 0, 1, VanillaIntProviders.stats(json("""
                {"type": "minecraft:clamped", "min_inclusive": 0, "max_inclusive": 64,
                 "source": {"type": "minecraft:uniform", "min_inclusive": -3, "max_inclusive": 1}}
                """)));
        assertStats(0, -3, 3, VanillaIntProviders.stats(json("{\"type\": \"minecraft:trapezoid\", \"min\": -3, \"max\": 3, \"plateau\": 0}")));
        assertStats(2.5D, 1, 4, VanillaIntProviders.stats(json("""
                {"type": "minecraft:weighted_list", "distribution": [{"data": 1, "weight": 1}, {"data": 4, "weight": 1}]}
                """)));
        assertThrows(IngestConversionException.class, () -> VanillaIntProviders.stats(json("{\"type\": \"minecraft:mystery\"}")));
    }

    @Test
    public void offsetProvidersReportTheChanceOfLandingOnTheSurface() throws Exception {
        assertEquals(0.25D, VanillaIntProviders.probabilityOf(json("{\"type\": \"minecraft:trapezoid\", \"min\": -3, \"max\": 3, \"plateau\": 0}"), 0), EPSILON);
        assertEquals(1.0D / 16.0D, VanillaIntProviders.probabilityOf(json("{\"type\": \"minecraft:trapezoid\", \"min\": -3, \"max\": 3, \"plateau\": 0}"), 3), EPSILON);
        assertEquals(1.0D / 7.0D, VanillaIntProviders.probabilityOf(json("{\"type\": \"minecraft:trapezoid\", \"min\": -3, \"max\": 3, \"plateau\": 6}"), 0), EPSILON);
        assertEquals(0.2D, VanillaIntProviders.probabilityOf(json("{\"type\": \"minecraft:uniform\", \"min_inclusive\": -2, \"max_inclusive\": 2}"), 0), EPSILON);
        assertEquals(1.0D, VanillaIntProviders.probabilityOf(json("0"), 0), EPSILON);
        assertEquals(0.0D, VanillaIntProviders.probabilityOf(json("2"), 0), EPSILON);
    }

    @Test
    public void heightProvidersResolveAnchorsAgainstTheDimensionType() throws Exception {
        VanillaHeights.HeightBand uniform = VanillaHeights.band(json("""
                {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 16}, "max_inclusive": {"below_top": 20}}
                """), OVERWORLD);
        VanillaHeights.HeightBand trapezoid = VanillaHeights.band(json("""
                {"type": "minecraft:trapezoid", "min_inclusive": {"above_bottom": -80}, "max_inclusive": {"above_bottom": 80}}
                """), OVERWORLD);
        VanillaHeights.HeightBand flat = VanillaHeights.band(json("""
                {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": 0}, "max_inclusive": {"absolute": 40}, "plateau": 40}
                """), OVERWORLD);
        VanillaHeights.HeightBand constant = VanillaHeights.band(json("{\"absolute\": 12}"), OVERWORLD);

        assertEquals(new VanillaHeights.HeightBand(-48, 299, "UNIFORM"), uniform);
        assertEquals(new VanillaHeights.HeightBand(-144, 16, "TRIANGLE"), trapezoid);
        assertEquals("UNIFORM", flat.distribution());
        assertEquals(new VanillaHeights.HeightBand(12, 12, "UNIFORM"), constant);
        assertThrows(IngestConversionException.class, () -> VanillaHeights.band(json("""
                {"type": "minecraft:very_biased_to_bottom", "min_inclusive": {"absolute": 0}, "max_inclusive": {"absolute": 9}}
                """), OVERWORLD));
    }

    @Test
    public void ruleTestsFlattenVanillaHeightSpecificTargets() throws Exception {
        VanillaRuleTests.HostSet stone = VanillaRuleTests.hosts(json("""
                {"predicate_type": "minecraft:any_of", "rules": [
                  {"predicate_type": "minecraft:all_of", "rules": [
                    {"predicate_type": "minecraft:tag_match", "tag": "minecraft:height_specific_ore_replaceables"},
                    {"predicate_type": "minecraft:height_match", "min_inclusive": 0, "max_inclusive": 2031}]},
                  {"predicate_type": "minecraft:all_of", "rules": [
                    {"predicate_type": "minecraft:not", "rule": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:height_specific_ore_replaceables"}},
                    {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}]}]}
                """));

        assertFalse(stone.any());
        assertEquals(Set.of("minecraft:tuff", "minecraft:stone", "minecraft:granite", "minecraft:diorite", "minecraft:andesite"), stone.blocks());
        assertEquals(Set.of("minecraft:netherrack"),
                VanillaRuleTests.hosts(json("{\"predicate_type\": \"minecraft:block_match\", \"block\": \"minecraft:netherrack\"}")).blocks());
        assertEquals(Set.of("minecraft:end_stone"), VanillaRuleTests.hosts(json(
                "{\"predicate_type\": \"minecraft:blockstate_match\", \"block_state\": {\"Name\": \"minecraft:end_stone\"}}")).blocks());
        assertTrue(VanillaRuleTests.hosts(json("{\"predicate_type\": \"minecraft:always_true\"}")).any());
        assertThrows(IngestConversionException.class, () -> VanillaRuleTests.hosts(json(
                "{\"predicate_type\": \"minecraft:random_block_match\", \"block\": \"minecraft:stone\", \"probability\": 0.5}")));
        IngestConversionException unknownTag = assertThrows(IngestConversionException.class, () -> VanillaRuleTests.hosts(json(
                "{\"predicate_type\": \"minecraft:tag_match\", \"tag\": \"examplemod:moon_rock\"}")));
        assertTrue(unknownTag.getMessage().contains("examplemod:moon_rock"));
    }

    @Test
    public void blockStatesAcceptEveryCodecShape() throws Exception {
        JsonObject compact = VanillaBlockStates.irisBlock(json("\"minecraft:iron_ore\""));
        JsonObject bracketed = VanillaBlockStates.irisBlock(json("\"minecraft:oak_log[axis=x]\""));
        JsonObject modern = VanillaBlockStates.irisBlock(json("{\"id\": \"minecraft:sunflower\", \"properties\": {\"half\": \"lower\"}}"));
        JsonObject legacy = VanillaBlockStates.irisBlock(json("{\"Name\": \"minecraft:sweet_berry_bush\", \"Properties\": {\"age\": \"3\"}}"));

        assertEquals("minecraft:iron_ore", compact.get("block").getAsString());
        assertFalse(compact.has("data"));
        assertEquals("x", bracketed.getAsJsonObject("data").get("axis").getAsString());
        assertEquals("lower", modern.getAsJsonObject("data").get("half").getAsString());
        assertEquals("3", legacy.getAsJsonObject("data").get("age").getAsString());
        assertThrows(IngestConversionException.class, () -> VanillaBlockStates.irisBlock(json("{\"oops\": true}")));
    }

    @Test
    public void stateProvidersBecomeWeightedPalettes() throws Exception {
        List<JsonObject> threshold = VanillaBlockStates.palette(json("""
                {"type": "minecraft:noise_threshold", "default_state": "minecraft:dandelion",
                 "low_states": ["minecraft:red_tulip"], "high_states": ["minecraft:poppy", "minecraft:dandelion"],
                 "noise": {"base_amplitude": 1.0, "base_octave": 0}, "scale": 0.005, "seed": 2345, "threshold": -0.8, "high_chance": 0.33}
                """));
        List<JsonObject> legacy = VanillaBlockStates.palette(json("""
                {"type": "minecraft:weighted_state_provider", "entries": [{"data": {"Name": "minecraft:poppy"}, "weight": 2}]}
                """));

        assertEquals(List.of("minecraft:dandelion", "minecraft:red_tulip", "minecraft:poppy"),
                threshold.stream().map(entry -> entry.get("block").getAsString()).toList());
        assertEquals(2, legacy.get(0).get("weight").getAsInt());
        assertEquals("minecraft:short_grass", VanillaBlockStates.palette(json("{\"id\": \"minecraft:short_grass\"}")).get(0).get("block").getAsString());
        IngestConversionException reference = assertThrows(IngestConversionException.class,
                () -> VanillaBlockStates.palette(json("\"minecraft:flower_plain\"")));
        assertTrue(reference.getMessage().contains("minecraft:flower_plain"));
    }

    private static void assertStats(double mean, int min, int max, VanillaIntProviders.IntStats stats) {
        assertEquals(mean, stats.mean(), EPSILON);
        assertEquals(min, stats.min());
        assertEquals(max, stats.max());
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }
}
