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

import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogIndex;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ModIngesterWeightedRefsTest {
    private static final String ORPHAN = "unused/orphan";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path source;

    @Before
    public void writeWeightedPack() throws Exception {
        source = ModIngestFixture.writeSourcePack(folder.getRoot().toPath());
        ModIngestFixture.write(source, "dimensions/overworld.json", """
                {
                  "name": "Overworld",
                  "environment": "NORMAL",
                  "regions": [{"region": "hot", "weight": 3}, "temperate"],
                  "dimensionHeight": {"min": -128, "max": 320}
                }
                """);
        ModIngestFixture.write(source, "regions/hot.json", """
                {
                  "name": "Hot",
                  "landBiomes": [{"biome": "hot/desert", "weight": 4}, "hot/savanna"],
                  "seaBiomes": [{"biome": "hot/warm-ocean", "weight": 2}],
                  "shoreBiomes": [{"biome": "hot/beach", "weight": 2}],
                  "caveBiomes": [{"biome": "caves/dripstone", "weight": 2}]
                }
                """);
        ModIngestFixture.write(source, "biomes/hot/savanna.json", """
                {"name": "Savanna", "derivative": "minecraft:savanna", "children": [{"biome": "%s", "weight": 5}]}
                """.formatted(ORPHAN));
    }

    @Test
    public void weightedRegionsPoolsAndChildrenAreRead() throws Exception {
        IngestPackModel model = IngestPackModel.load(new IngestPack(source), "overworld", WorldgenCatalogIndex.of(ModIngestFixture.catalog()));

        assertEquals(List.of("hot", "temperate"), model.regions().stream().map(IngestPackModel.RegionProfile::key).toList());
        IngestPackModel.RegionProfile hot = model.regions().getFirst();
        assertEquals(List.of("hot/desert", "hot/savanna"), hot.pool(BiomeRole.LAND));
        assertEquals(List.of("hot/warm-ocean"), hot.pool(BiomeRole.SEA));
        assertEquals(List.of("hot/beach"), hot.pool(BiomeRole.SHORE));
        assertEquals(List.of("caves/dripstone"), hot.pool(BiomeRole.CAVE));
        assertTrue("weighted child stays reachable", model.reachableBiomes().containsKey(ORPHAN));
    }

    @Test
    public void adoptingIntoAWeightedPoolKeepsExistingObjectEntries() throws Exception {
        Path target = folder.getRoot().toPath().resolve("packs").resolve("modded");

        ModIngester.ingest(ModIngestFixture.request(source, target, Set.of(ModIngestFixture.MOD), false));

        JsonObject hot = ModIngestFixture.readJson(target.resolve("regions/hot.json"));
        JsonArray land = hot.getAsJsonArray("landBiomes");
        JsonElement firstEntry = JsonParser.parseString("{\"biome\": \"hot/desert\", \"weight\": 4}");
        assertEquals(firstEntry, land.get(0));
        assertEquals("hot/savanna", land.get(1).getAsString());
        assertEquals("ingest/examplemod/scorched_mesa", land.get(land.size() - 1).getAsString());
        assertEquals(JsonParser.parseString("{\"biome\": \"hot/beach\", \"weight\": 2}"), hot.getAsJsonArray("shoreBiomes").get(0));
        assertEquals(JsonParser.parseString("{\"biome\": \"caves/dripstone\", \"weight\": 2}"), hot.getAsJsonArray("caveBiomes").get(0));
    }
}
