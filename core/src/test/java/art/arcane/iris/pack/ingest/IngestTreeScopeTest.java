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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IngestTreeScopeTest {
    private static final int VEGETAL_STEP = ModIngestFixture.VEGETAL_STEP;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private ModIngestReport report;
    private JsonObject imported;

    @Before
    public void ingest() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        Path target = root.resolve("packs").resolve("trees");
        report = ModIngester.ingest(new ModIngestRequest(source, target, "overworld", catalog(), Set.of(ModIngestFixture.MOD), Set.of(), false));
        imported = ModIngestFixture.readJson(target.resolve("dimensions/trees.json")).getAsJsonObject("importedFeatures");
    }

    @Test
    public void modTreesInjectedIntoThePacksVanillaBiomesAreDeniedThereOnly() {
        JsonArray rules = imported.getAsJsonArray("biomeRules");

        assertEquals(1, rules.size());
        JsonObject rule = rules.get(0).getAsJsonObject();
        assertEquals(List.of("minecraft:"), ModIngestFixture.strings(rule.get("biomes")));
        assertEquals(List.of("examplemod:inline_trees", "examplemod:oak_bushes", "examplemod:simple_trees"),
                ModIngestFixture.strings(rule.get("disabledExact")));
        ModIngestItem bushes = ModIngestFixture.item(report, "examplemod:oak_bushes");
        assertEquals(ModIngestTier.PASSTHROUGH, bushes.tier());
        assertTrue(bushes.reason(), bushes.reason().contains("minecraft biomes"));
    }

    @Test
    public void nonTreeDecorationAndModOnlyTreesKeepRunningEverywhere() {
        List<String> denied = ModIngestFixture.strings(imported.getAsJsonArray("biomeRules").get(0).getAsJsonObject().get("disabledExact"));

        assertEquals(ModIngestTier.PASSTHROUGH, ModIngestFixture.item(report, "examplemod:mossy_rocks").tier());
        assertTrue(denied.stream().noneMatch(key -> key.equals("examplemod:mossy_rocks")));
        assertTrue(denied.stream().noneMatch(key -> key.equals("examplemod:maple_trees")));
    }

    private static WorldgenCatalog catalog() {
        WorldgenCatalog fixture = ModIngestFixture.catalog();
        List<String> injected = List.of(ModIngestFixture.MOD + ":oak_bushes", ModIngestFixture.MOD + ":simple_trees",
                ModIngestFixture.MOD + ":inline_trees", ModIngestFixture.MOD + ":mossy_rocks");
        List<CatalogBiome> biomes = new ArrayList<>();
        for (CatalogBiome biome : fixture.biomes()) {
            if (!biome.key().equals("minecraft:forest") && !biome.key().equals(ModIngestFixture.MOD + ":maple_woods")) {
                biomes.add(biome);
                continue;
            }
            List<List<String>> steps = new ArrayList<>(biome.featureSteps());
            List<String> vegetal = new ArrayList<>(steps.get(VEGETAL_STEP));
            vegetal.addAll(injected);
            steps.set(VEGETAL_STEP, vegetal);
            biomes.add(new CatalogBiome(biome.key(), biome.temperature(), biome.downfall(), true, biome.tags(), steps, biome.spawns(), null));
        }
        List<CatalogPlacedFeature> features = new ArrayList<>(fixture.placedFeatures());
        features.add(feature("oak_bushes", "minecraft:random_selector", """
                {"features": [{"chance": 0.3, "feature": "examplemod:maple_trees"}], "default": "examplemod:mossy_rocks"}
                """));
        features.add(feature("simple_trees", "minecraft:simple_random_selector", """
                {"features": ["examplemod:mossy_rocks", "examplemod:maple_trees"]}
                """));
        features.add(feature("inline_trees", "minecraft:random_boolean_selector", """
                {"feature_true": {"feature": {"type": "minecraft:tree", "config": {}}, "placement": []}, "feature_false": "examplemod:mossy_rocks"}
                """));
        features.add(feature("mossy_rocks", "minecraft:forest_rock", "{\"state\": \"minecraft:mossy_cobblestone\"}"));
        return new WorldgenCatalog(fixture.format(), fixture.minecraftVersion(), fixture.platform(), fixture.capturedAtEpochMillis(),
                fixture.contentFingerprint(), fixture.namespaces(), biomes, features, fixture.structures(),
                fixture.dimensionTypes(), fixture.levelStems(), fixture.blockTags());
    }

    private static CatalogPlacedFeature feature(String path, String type, String config) {
        String key = ModIngestFixture.MOD + ":" + path;
        return new CatalogPlacedFeature(key, key, type, JsonParser.parseString(config), JsonParser.parseString("[]"));
    }
}
