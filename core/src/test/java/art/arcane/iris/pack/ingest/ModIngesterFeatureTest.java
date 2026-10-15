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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ModIngesterFeatureTest {
    private static final double EPSILON = 0.0000005D;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path target;
    private ModIngestReport report;
    private JsonObject dimension;

    @Before
    public void ingest() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        target = root.resolve("packs").resolve("modded");
        report = ModIngester.ingest(ModIngestFixture.request(source, target, Set.of(ModIngestFixture.MOD), false));
        dimension = ModIngestFixture.readJson(target.resolve("dimensions/modded.json"));
    }

    @Test
    public void oreInjectedIntoVanillaBiomesBecomesDimensionDepositsPerTarget() {
        JsonArray deposits = dimension.getAsJsonArray("deposits");
        JsonObject stone = ModIngestFixture.findByPaletteBlock(deposits, "examplemod:tin_ore");
        JsonObject deepslate = ModIngestFixture.findByPaletteBlock(deposits, "examplemod:deepslate_tin_ore");

        assertEquals(4, deposits.size());
        assertNotNull(stone);
        assertNotNull(deepslate);
        assertEquals(16, stone.get("minHeight").getAsInt());
        assertEquals(427, stone.get("maxHeight").getAsInt());
        assertEquals("UNIFORM", stone.get("heightDistribution").getAsString());
        assertEquals(12, stone.get("minPerChunk").getAsInt());
        assertEquals(12, stone.get("maxPerChunk").getAsInt());
        assertEquals(0.5D, stone.get("spawnChance").getAsDouble(), EPSILON);
        assertEquals(7, stone.get("minSize").getAsInt());
        assertEquals(7, stone.get("maxSize").getAsInt());
        assertEquals("VANILLA_ELLIPSOID", stone.get("shape").getAsString());
        assertEquals(0, stone.get("surfaceClearance").getAsInt());
        assertEquals(List.of("minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:stone", "minecraft:tuff"),
                ModIngestFixture.strings(stone.get("replaceableBlocks")));
        assertEquals(List.of("minecraft:deepslate"), ModIngestFixture.strings(deepslate.get("replaceableBlocks")));
        assertFalse(stone.has("includedBiomes"));
        assertEquals(ModIngestTier.NATIVE, ModIngestFixture.item(report, "examplemod:ore_tin").tier());
    }

    @Test
    public void oreDeclaredByOneVanillaBiomeIsFilteredToThatBiome() {
        JsonObject sapphire = ModIngestFixture.findByPaletteBlock(dimension.getAsJsonArray("deposits"), "examplemod:sapphire_ore");

        assertNotNull(sapphire);
        assertEquals(List.of("minecraft:desert"), ModIngestFixture.strings(sapphire.get("includedBiomes")));
        assertEquals("SURFACE", sapphire.get("biomeScope").getAsString());
        assertEquals(128, sapphire.get("minHeight").getAsInt());
        assertEquals(160, sapphire.get("maxHeight").getAsInt());
        assertEquals(2, sapphire.get("maxPerChunk").getAsInt());
        assertEquals(6, ModIngestFixture.strings(sapphire.get("replaceableBlocks")).size());
    }

    @Test
    public void modOnlyScatteredOreBecomesBiomeDeposit() throws Exception {
        JsonObject biome = ModIngestFixture.readJson(target.resolve("biomes/ingest/examplemod/scorched_mesa.json"));
        JsonObject ruby = ModIngestFixture.findByPaletteBlock(biome.getAsJsonArray("deposits"), "examplemod:ruby_ore");

        assertNotNull(ruby);
        assertEquals("false", ruby.getAsJsonArray("palette").get(0).getAsJsonObject().getAsJsonObject("data").get("lit").getAsString());
        assertEquals(112, ruby.get("minHeight").getAsInt());
        assertEquals(176, ruby.get("maxHeight").getAsInt());
        assertEquals("TRIANGLE", ruby.get("heightDistribution").getAsString());
        assertEquals(1, ruby.get("minPerChunk").getAsInt());
        assertEquals(3, ruby.get("maxPerChunk").getAsInt());
        assertEquals("VANILLA_SCATTERED", ruby.get("shape").getAsString());
        assertEquals(0.5D, ruby.get("discardChanceOnAirExposure").getAsDouble(), EPSILON);
        assertEquals(List.of("minecraft:stone"), ModIngestFixture.strings(ruby.get("replaceableBlocks")));
        assertNull(ModIngestFixture.findByPaletteBlock(dimension.getAsJsonArray("deposits"), "examplemod:ruby_ore"));
        assertEquals("ingest/examplemod/scorched_mesa", ModIngestFixture.item(report, "examplemod:ore_ruby").target());
    }

    @Test
    public void flowerPatchLandsInModBiomeAndExistingVanillaDerivativeBiome() throws Exception {
        JsonObject maple = ModIngestFixture.readJson(target.resolve("biomes/ingest/examplemod/maple_woods.json"));
        JsonObject forest = ModIngestFixture.readJson(target.resolve("biomes/temperate/forest.json"));
        JsonObject orphan = ModIngestFixture.readJson(target.resolve("biomes/unused/orphan.json"));
        JsonObject mapleFlowers = ModIngestFixture.findByPaletteBlock(maple.getAsJsonArray("decorators"), "examplemod:glowcap");
        JsonObject forestFlowers = ModIngestFixture.findByPaletteBlock(forest.getAsJsonArray("decorators"), "examplemod:glowcap");

        assertNotNull(mapleFlowers);
        assertNotNull(forestFlowers);
        assertEquals(2, forest.getAsJsonArray("decorators").size());
        double mapleCanopy = 1.0D - Math.exp(-CanopyEstimate.CROWN_COLUMNS / 256.0D);
        double openShare = 1.0D - mapleCanopy / 2.0D;
        assertEquals(1.0D - Math.exp(-(1.0D / 4.0D * 32.0D * 0.25D * openShare) / 256.0D), forestFlowers.get("chance").getAsDouble(), 0.000001D);
        JsonArray palette = forestFlowers.getAsJsonArray("palette");
        assertEquals(3, palette.get(0).getAsJsonObject().get("weight").getAsInt());
        assertEquals("examplemod:glowbell", palette.get(1).getAsJsonObject().get("block").getAsString());
        assertEquals("true", palette.get(1).getAsJsonObject().getAsJsonObject("data").get("lit").getAsString());
        assertEquals("minecraft:grass_block", forestFlowers.getAsJsonArray("whitelist").get(0).getAsJsonObject().get("block").getAsString());
        assertFalse(orphan.has("decorators"));
        ModIngestItem item = ModIngestFixture.item(report, "examplemod:glow_flowers");
        assertEquals(ModIngestTier.NATIVE, item.tier());
        assertTrue(item.target(), item.target().contains("temperate/forest"));
        assertTrue(item.target(), item.target().contains("ingest/examplemod/maple_woods"));
    }

    @Test
    public void legacyRandomPatchConvertsIntoPlainsDecorator() throws Exception {
        JsonObject plains = ModIngestFixture.readJson(target.resolve("biomes/temperate/plains.json"));
        JsonObject bluebell = ModIngestFixture.findByPaletteBlock(plains.getAsJsonArray("decorators"), "examplemod:bluebell");

        assertNotNull(bluebell);
        assertEquals(1.0D - Math.exp(-(0.5D * 64.0D * 0.25D) / 256.0D), bluebell.get("chance").getAsDouble(), 0.000001D);
    }

    @Test
    public void unconvertibleFeaturesPassThroughAndConvertedOnesAreDisabled() {
        ModIngestItem trees = ModIngestFixture.item(report, "examplemod:maple_trees");
        ModIngestItem weird = ModIngestFixture.item(report, "examplemod:weird_ore");
        JsonObject imported = dimension.getAsJsonObject("importedFeatures");

        assertEquals(ModIngestTier.PASSTHROUGH, trees.tier());
        assertEquals(ModIngestTier.PASSTHROUGH, weird.tier());
        assertTrue(weird.reason(), weird.reason().contains("examplemod:custom_stone"));
        assertTrue(imported.get("enabled").getAsBoolean());
        assertEquals(List.of("minecraft:ore_diamond", "minecraft:", "othermod:"),
                ModIngestFixture.strings(imported.get("disabled")));
        assertEquals(List.of("examplemod:bluebell_patch", "examplemod:glow_flowers",
                        "examplemod:ore_ruby", "examplemod:ore_sapphire", "examplemod:ore_tin"),
                ModIngestFixture.strings(imported.get("disabledExact")));
        assertEquals(List.of("LAKES"), ModIngestFixture.strings(imported.get("disabledSteps")));
    }

    @Test
    public void featuresWithoutHostsAreSkippedAndConvertedKeysNeverShadowTheirFamily() {
        ModIngestItem nether = ModIngestFixture.item(report, "examplemod:nether_ore");
        ModIngestItem buried = ModIngestFixture.item(report, "examplemod:ore_tin_buried");

        assertEquals(ModIngestTier.SKIPPED, nether.tier());
        assertEquals(ModIngestTier.PASSTHROUGH, buried.tier());
    }

    @Test
    public void structuresAreAdoptedUnlessDisabledOrHostless() {
        assertEquals(ModIngestTier.ADOPTED, ModIngestFixture.item(report, "examplemod:maple_tower").tier());
        ModIngestItem ruin = ModIngestFixture.item(report, "examplemod:old_ruin");
        assertEquals(ModIngestTier.SKIPPED, ruin.tier());
        assertTrue(ruin.reason(), ruin.reason().contains("importedStructures"));
        assertEquals(ModIngestTier.SKIPPED, ModIngestFixture.item(report, "examplemod:nether_fort").tier());
        assertNull(ModIngestFixture.item(report, "minecraft:village_plains"));
    }
}
