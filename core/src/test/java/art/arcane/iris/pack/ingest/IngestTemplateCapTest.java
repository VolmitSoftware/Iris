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
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IngestTemplateCapTest {
    private static final String MOD = "modcaps";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void caveTemplatesHostAtMostThreeModBiomesAndOverflowMovesToTheNextBestTemplate() throws Exception {
        ModIngestReport report = ingest(7, 0);

        Map<String, Integer> templates = templateUses(report);
        assertEquals(Integer.valueOf(3), templates.get("caves/lush"));
        for (Map.Entry<String, Integer> template : templates.entrySet()) {
            assertTrue(template.toString(), template.getValue() <= 3);
        }
        for (int index = 0; index < 7; index++) {
            assertEquals(ModIngestTier.ADOPTED, ModIngestFixture.item(report, MOD + ":grotto_" + index).tier());
        }
    }

    @Test
    public void landTemplatesHostAtMostFiveModBiomesAndOverflowMovesToTheNextBestTemplate() throws Exception {
        ModIngestReport report = ingest(0, 7);

        Map<String, Integer> templates = templateUses(report);
        assertEquals(Integer.valueOf(5), templates.get("wet/marsh"));
        assertEquals(Integer.valueOf(2), templates.get("dry/plains"));
    }

    @Test
    public void modBiomeIsSkippedWithAReasonWhenEveryCompatibleTemplateIsFull() throws Exception {
        ModIngestReport report = ingest(10, 0);

        int adopted = 0;
        List<ModIngestItem> skipped = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            ModIngestItem item = ModIngestFixture.item(report, MOD + ":grotto_" + index);
            if (item.tier() == ModIngestTier.ADOPTED) {
                adopted++;
            } else {
                skipped.add(item);
            }
        }
        assertEquals(9, adopted);
        assertEquals(1, skipped.size());
        assertEquals(ModIngestTier.SKIPPED, skipped.getFirst().tier());
        assertTrue(skipped.getFirst().reason(), skipped.getFirst().reason().contains("already hosts 3 adopted biomes"));
    }

    private ModIngestReport ingest(int caves, int marshes) throws IOException {
        Path root = folder.newFolder().toPath();
        Path source = root.resolve("source");
        ModIngestFixture.write(source, "dimensions/overworld.json", """
                {"name": "Overworld", "environment": "NORMAL", "regions": ["wet", "dry"], "dimensionHeight": {"min": -64, "max": 320}}
                """);
        ModIngestFixture.write(source, "regions/wet.json", """
                {"name": "Wet", "landBiomes": ["wet/marsh"], "caveBiomes": ["caves/lush"]}
                """);
        ModIngestFixture.write(source, "regions/dry.json", """
                {"name": "Dry", "landBiomes": ["dry/plains"], "caveBiomes": ["caves/drip", "caves/sand"]}
                """);
        biome(source, "wet/marsh", "minecraft:swamp");
        biome(source, "dry/plains", "minecraft:plains");
        biome(source, "caves/lush", "minecraft:lush_caves");
        biome(source, "caves/drip", "minecraft:dripstone_caves");
        biome(source, "caves/sand", "minecraft:dripstone_caves");
        Path target = root.resolve("packs").resolve("capped");
        return ModIngester.ingest(new ModIngestRequest(source, target, "overworld", catalog(caves, marshes), Set.of(MOD), Set.of(), false));
    }

    private static Map<String, Integer> templateUses(ModIngestReport report) {
        Map<String, Integer> uses = new TreeMap<>();
        for (ModIngestItem item : report.items()) {
            if (item.kind().equals("biome") && item.tier() == ModIngestTier.ADOPTED) {
                String reason = item.reason();
                uses.merge(reason.substring(reason.indexOf("from template ") + "from template ".length()), 1, Integer::sum);
            }
        }
        return uses;
    }

    private static void biome(Path pack, String key, String derivative) throws IOException {
        ModIngestFixture.write(pack, "biomes/" + key + ".json", "{\"name\": \"" + key + "\", \"derivative\": \"" + derivative + "\"}");
    }

    private static WorldgenCatalog catalog(int caves, int marshes) {
        List<CatalogBiome> biomes = new ArrayList<>();
        biomes.add(biome("minecraft:swamp", 0.8F, 0.9F, List.of("c:is_swamp")));
        biomes.add(biome("minecraft:plains", 0.8F, 0.4F, List.of("c:is_plains")));
        biomes.add(biome("minecraft:lush_caves", 0.5F, 0.5F, List.of("c:is_cave", "c:is_lush")));
        biomes.add(biome("minecraft:dripstone_caves", 2.0F, 0.0F, List.of("c:is_cave")));
        for (int index = 0; index < caves; index++) {
            biomes.add(biome(MOD + ":grotto_" + index, 0.5F, 0.5F, List.of("c:is_cave", "c:is_lush")));
        }
        for (int index = 0; index < marshes; index++) {
            biomes.add(biome(MOD + ":marsh_" + index, 0.8F, 0.9F, List.of("c:is_swamp")));
        }
        return new WorldgenCatalog(WorldgenCatalog.FORMAT, "26.3", "fabric", 1000L, "fingerprint-caps",
                List.of(new CatalogNamespace(MOD, "Mod Caps", "1.0.0")), biomes, List.of(), List.of(),
                List.of(new CatalogDimensionType("minecraft:overworld", -64, 384, 384, null)), List.of(), Map.of());
    }

    private static CatalogBiome biome(String key, float temperature, float downfall, List<String> categoryTags) {
        List<List<String>> steps = new ArrayList<>(11);
        for (int index = 0; index < 11; index++) {
            steps.add(List.of());
        }
        List<String> tags = new ArrayList<>(categoryTags);
        tags.add("minecraft:is_overworld");
        return new CatalogBiome(key, temperature, downfall, true, tags, steps, List.of(), null);
    }
}
