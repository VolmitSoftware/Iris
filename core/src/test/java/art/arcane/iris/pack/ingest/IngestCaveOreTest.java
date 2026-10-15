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
import art.arcane.iris.pack.ingest.catalog.CatalogPlacedFeature;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IngestCaveOreTest {
    private static final String MOD = ModIngestFixture.MOD;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void oresOfAdoptedCaveBiomesRunFromTheRegionScopedToThoseCaves() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        Path target = root.resolve("packs").resolve("caves");

        ModIngestReport report = ModIngester.ingest(new ModIngestRequest(source, target, "overworld", catalog(), Set.of(MOD), Set.of(), false));

        JsonArray regionDeposits = ModIngestFixture.readJson(target.resolve("regions/hot.json")).getAsJsonArray("deposits");
        assertNotNull(regionDeposits);
        JsonObject geode = ModIngestFixture.findByPaletteBlock(regionDeposits, MOD + ":geode_ore");
        assertNotNull(geode);
        assertEquals(List.of("ingest/" + MOD + "/crystal_caves"), ModIngestFixture.strings(geode.get("includedBiomes")));
        assertEquals("CAVE", geode.get("biomeScope").getAsString());
        JsonElement caveDeposits = ModIngestFixture.readJson(target.resolve("biomes/ingest/" + MOD + "/crystal_caves.json")).get("deposits");
        assertTrue(caveDeposits == null || ModIngestFixture.findByPaletteBlock(caveDeposits.getAsJsonArray(), MOD + ":geode_ore") == null);
        assertNull(ModIngestFixture.findByPaletteBlock(ModIngestFixture.readJson(target.resolve("dimensions/caves.json")).getAsJsonArray("deposits"), MOD + ":geode_ore"));
        ModIngestItem item = ModIngestFixture.item(report, MOD + ":geode_ore");
        assertEquals(ModIngestTier.NATIVE, item.tier());
        assertTrue(item.target(), item.target().contains("region hot"));
        assertFalse(ModIngestFixture.readJson(target.resolve("regions/temperate.json")).has("deposits"));
    }

    private static WorldgenCatalog catalog() {
        WorldgenCatalog fixture = ModIngestFixture.catalog();
        List<CatalogBiome> biomes = new ArrayList<>();
        for (CatalogBiome biome : fixture.biomes()) {
            if (!biome.key().equals(MOD + ":crystal_caves")) {
                biomes.add(biome);
                continue;
            }
            List<List<String>> steps = new ArrayList<>(biome.featureSteps());
            List<String> ores = new ArrayList<>(steps.get(ModIngestFixture.ORE_STEP));
            ores.add(MOD + ":geode_ore");
            steps.set(ModIngestFixture.ORE_STEP, ores);
            biomes.add(new CatalogBiome(biome.key(), biome.temperature(), biome.downfall(), true, biome.tags(), steps, biome.spawns(), null));
        }
        List<CatalogPlacedFeature> features = new ArrayList<>(fixture.placedFeatures());
        String key = MOD + ":geode_ore";
        features.add(new CatalogPlacedFeature(key, key, "minecraft:ore", JsonParser.parseString("""
                {"size": 6, "targets": [{"state": "examplemod:geode_ore", "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:stone"}}]}
                """), JsonParser.parseString("""
                [{"type": "minecraft:count", "count": 3}, {"type": "minecraft:in_square"},
                 {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -40}, "max_inclusive": {"absolute": 20}}},
                 {"type": "minecraft:biome"}]
                """)));
        return new WorldgenCatalog(fixture.format(), fixture.minecraftVersion(), fixture.platform(), fixture.capturedAtEpochMillis(),
                fixture.contentFingerprint(), fixture.namespaces(), biomes, features, fixture.structures(), fixture.dimensionTypes(),
                fixture.levelStems(), fixture.blockTags());
    }
}
