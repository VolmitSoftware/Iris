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
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public class IngestPlantableSurfaceTest {
    private static final String MOD = "modtrees";
    private static final int VEGETAL_STEP = ModIngestFixture.VEGETAL_STEP;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void modBiomesWithTheirOwnTreesAvoidSnowCoveredTemplates() throws Exception {
        ModIngestReport report = ingest();

        assertTrue(reason(report, "snowy_maple_woods"), reason(report, "snowy_maple_woods").contains("in region tundra from template tundra/"));
        assertTrue(reason(report, "scarlet_peaks"), reason(report, "scarlet_peaks").contains("in region tundra from template tundra/"));
    }

    @Test
    public void modBiomesWithoutVegetationKeepTheBestClimateTemplate() throws Exception {
        ModIngestReport report = ingest();

        assertTrue(reason(report, "frost_flats"), reason(report, "frost_flats").contains("from template frozen/plains"));
    }

    private ModIngestReport ingest() throws IOException {
        Path root = folder.newFolder().toPath();
        Path source = root.resolve("source");
        ModIngestFixture.write(source, "dimensions/overworld.json", """
                {"name": "Overworld", "environment": "NORMAL", "regions": ["frozen", "tundra"], "dimensionHeight": {"min": -64, "max": 320}}
                """);
        ModIngestFixture.write(source, "regions/frozen.json", """
                {"name": "Frozen", "landBiomes": ["frozen/plains", "frozen/slopes"]}
                """);
        ModIngestFixture.write(source, "regions/tundra.json", """
                {"name": "Tundra", "landBiomes": ["tundra/taiga", "tundra/mountain-forest"]}
                """);
        ModIngestFixture.write(source, "snippet/biome-palette/snow/top.json", """
                {"palette": [{"block": "minecraft:snow_block"}], "minHeight": 1, "maxHeight": 1}
                """);
        biome(source, "frozen/plains", "minecraft:snowy_plains", "\"snippet/biome-palette/snow/top\"");
        biome(source, "frozen/slopes", "minecraft:snowy_slopes", "\"snippet/biome-palette/snow/top\"");
        biome(source, "tundra/taiga", "minecraft:taiga", "{\"palette\": [{\"block\": \"grass_block\"}]}");
        biome(source, "tundra/mountain-forest", "minecraft:windswept_forest", "{\"palette\": [{\"block\": \"minecraft:grass_block\", \"weight\": 3}, {\"block\": \"minecraft:snow_block\"}]}");
        Path target = root.resolve("packs").resolve("trees");
        return ModIngester.ingest(new ModIngestRequest(source, target, "overworld", catalog(), Set.of(MOD), Set.of(), false));
    }

    private static void biome(Path pack, String key, String derivative, String topLayer) throws IOException {
        ModIngestFixture.write(pack, "biomes/" + key + ".json", "{\"name\": \"" + key + "\", \"derivative\": \"" + derivative
                + "\", \"layers\": [" + topLayer + ", {\"palette\": [{\"block\": \"minecraft:dirt\"}]}]}");
    }

    private static WorldgenCatalog catalog() {
        List<CatalogBiome> biomes = new ArrayList<>();
        biomes.add(biome("minecraft:snowy_plains", 0.0F, 0.5F, List.of("c:is_snowy", "c:is_plains"), List.of()));
        biomes.add(biome("minecraft:snowy_slopes", -0.3F, 0.9F, List.of("c:is_snowy", "minecraft:is_mountain"), List.of()));
        biomes.add(biome("minecraft:taiga", 0.25F, 0.8F, List.of("minecraft:is_taiga"), List.of()));
        biomes.add(biome("minecraft:windswept_forest", 0.2F, 0.3F, List.of("minecraft:is_mountain", "minecraft:is_forest"), List.of()));
        biomes.add(biome(MOD + ":snowy_maple_woods", 0.0F, 0.5F, List.of("c:is_snowy"), List.of(MOD + ":maple_trees")));
        biomes.add(biome(MOD + ":scarlet_peaks", -0.3F, 0.9F, List.of("c:is_snowy", "minecraft:is_mountain"), List.of(MOD + ":scarlet_trees")));
        biomes.add(biome(MOD + ":frost_flats", 0.0F, 0.5F, List.of("c:is_snowy", "c:is_plains"), List.of()));
        List<CatalogPlacedFeature> features = List.of(tree("maple_trees"), tree("scarlet_trees"));
        return new WorldgenCatalog(WorldgenCatalog.FORMAT, "26.3", "fabric", 1L, "plantable",
                List.of(new CatalogNamespace(MOD, "Mod Trees", "1")), biomes, features, List.of(),
                List.of(new CatalogDimensionType("minecraft:overworld", -64, 384, 384, null)), List.of(), Map.of());
    }

    private static CatalogPlacedFeature tree(String path) {
        String key = MOD + ":" + path;
        return new CatalogPlacedFeature(key, key, "minecraft:tree", JsonParser.parseString("{}"),
                JsonParser.parseString("[{\"type\": \"minecraft:count\", \"count\": 4}]"));
    }

    private static CatalogBiome biome(String key, float temperature, float downfall, List<String> tags, List<String> vegetal) {
        List<List<String>> steps = new ArrayList<>(11);
        for (int index = 0; index < 11; index++) {
            steps.add(index == VEGETAL_STEP ? vegetal : List.of());
        }
        List<String> allTags = new ArrayList<>(tags);
        allTags.add("minecraft:is_overworld");
        return new CatalogBiome(key, temperature, downfall, true, allTags, steps, List.of(), null);
    }

    private static String reason(ModIngestReport report, String biome) {
        return ModIngestFixture.item(report, MOD + ":" + biome).reason();
    }
}
