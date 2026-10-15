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
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IngestBiomePlacementTest {
    private static final String MOD = "modpack";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void modBiomesAvoidThemedRegionsAndFollowTheirCategory() throws Exception {
        ModIngestReport report = ingest(Set.of());

        assertEquals("temperate", region(report, "lavender_fields"));
        assertEquals("temperate", region(report, "redwood_forest"));
        assertTrue(reason(report, "redwood_forest").contains("temperate/forest"));
        assertEquals("hot", region(report, "brushland"));
        assertTrue(reason(report, "brushland").contains("hot/savanna"));
        for (int index = 0; index < 6; index++) {
            assertEquals("temperate", region(report, "prairie_" + index));
        }
    }

    @Test
    public void repeatedClimatesSpreadOverEquallyGoodTemplates() throws Exception {
        ModIngestReport report = ingest(Set.of());

        Set<String> templates = new TreeSet<>();
        for (int index = 0; index < 6; index++) {
            String reason = reason(report, "prairie_" + index);
            templates.add(reason.substring(reason.indexOf("from template ") + "from template ".length()));
        }
        assertEquals(Set.of("temperate/meadow", "temperate/plains"), templates);
    }

    @Test
    public void excludedRegionsNeverReceiveModBiomes() throws Exception {
        ModIngestReport report = ingest(Set.of("temperate"));

        for (ModIngestItem item : report.items()) {
            if (item.kind().equals("biome") && item.tier() == ModIngestTier.ADOPTED) {
                assertFalse(item.reason(), item.reason().contains("in region temperate "));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> ingest(Set.of("missing")));
    }

    private ModIngestReport ingest(Set<String> excludedRegions) throws IOException {
        Path root = folder.newFolder().toPath();
        Path source = root.resolve("source");
        ModIngestFixture.write(source, "dimensions/overworld.json", """
                {"name": "Overworld", "environment": "NORMAL", "regions": ["temperate", "hot", "prismatic"], "dimensionHeight": {"min": -64, "max": 320}}
                """);
        ModIngestFixture.write(source, "regions/temperate.json", """
                {"name": "Temperate", "landBiomes": ["temperate/plains", "temperate/meadow", "temperate/forest"], "seaBiomes": ["temperate/ocean"]}
                """);
        ModIngestFixture.write(source, "regions/hot.json", """
                {"name": "Hot", "landBiomes": ["hot/desert", "hot/savanna"]}
                """);
        ModIngestFixture.write(source, "regions/prismatic.json", """
                {"name": "Prismatic", "rarity": 20, "landBiomes": ["prismatic/white", "prismatic/red", "prismatic/blue"]}
                """);
        biome(source, "temperate/plains", "minecraft:plains");
        biome(source, "temperate/meadow", "minecraft:plains");
        biome(source, "temperate/forest", "minecraft:forest");
        biome(source, "temperate/ocean", "minecraft:ocean");
        biome(source, "hot/desert", "minecraft:desert");
        biome(source, "hot/savanna", "minecraft:savanna");
        biome(source, "prismatic/white", "minecraft:plains");
        biome(source, "prismatic/red", "minecraft:plains");
        biome(source, "prismatic/blue", "minecraft:plains");
        Path target = root.resolve("packs").resolve("placed");
        return ModIngester.ingest(new ModIngestRequest(source, target, "overworld", catalog(), Set.of(MOD), excludedRegions, false));
    }

    private static void biome(Path pack, String key, String derivative) throws IOException {
        ModIngestFixture.write(pack, "biomes/" + key + ".json", "{\"name\": \"" + key + "\", \"derivative\": \"" + derivative + "\"}");
    }

    private static WorldgenCatalog catalog() {
        List<CatalogBiome> biomes = new ArrayList<>();
        biomes.add(biome("minecraft:plains", 0.8F, 0.4F, "c:is_plains"));
        biomes.add(biome("minecraft:forest", 0.7F, 0.8F, "minecraft:is_forest"));
        biomes.add(biome("minecraft:desert", 2.0F, 0.0F, "c:is_desert"));
        biomes.add(biome("minecraft:savanna", 1.2F, 0.4F, "minecraft:is_savanna"));
        biomes.add(biome("minecraft:ocean", 0.5F, 0.5F, "minecraft:is_ocean"));
        biomes.add(biome(MOD + ":lavender_fields", 0.8F, 0.5F, "c:is_plains"));
        biomes.add(biome(MOD + ":redwood_forest", 0.8F, 0.6F, "minecraft:is_forest"));
        biomes.add(biome(MOD + ":brushland", 1.3F, 0.3F, "minecraft:is_savanna"));
        for (int index = 0; index < 6; index++) {
            biomes.add(biome(MOD + ":prairie_" + index, 0.8F, 0.4F, "c:is_plains"));
        }
        return new WorldgenCatalog(WorldgenCatalog.FORMAT, "26.3", "fabric", 1000L, "fingerprint-placement",
                List.of(new CatalogNamespace(MOD, "Mod Pack", "1.0.0")), biomes, List.of(), List.of(),
                List.of(new CatalogDimensionType("minecraft:overworld", -64, 384, 384, null)), List.of(), Map.of());
    }

    private static CatalogBiome biome(String key, float temperature, float downfall, String categoryTag) {
        List<List<String>> steps = new ArrayList<>(11);
        for (int index = 0; index < 11; index++) {
            steps.add(List.of());
        }
        return new CatalogBiome(key, temperature, downfall, true, List.of("minecraft:is_overworld", categoryTag), steps, List.of(), null);
    }

    private static String region(ModIngestReport report, String biome) {
        String reason = reason(report, biome);
        int start = reason.indexOf("in region ") + "in region ".length();
        return reason.substring(start, reason.indexOf(' ', start));
    }

    private static String reason(ModIngestReport report, String biome) {
        ModIngestItem item = ModIngestFixture.item(report, MOD + ":" + biome);
        assertEquals(ModIngestTier.ADOPTED, item.tier());
        return item.reason();
    }
}
