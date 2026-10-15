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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class IngestRegionAffinityTest {
    private static final String MOD = "modisles";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void modBiomeJoinsTheRegionWhoseTemplatesShareItsCategory() throws Exception {
        ModIngestReport report = ingest(1, false);

        assertEquals("mushroom", region(report, "mirage_isles"));
    }

    @Test
    public void rareThemedRegionNeverHostsAModBiomeWhileACommonRegionAcceptsIt() throws Exception {
        ModIngestReport report = ingest(15, false);

        String region = region(report, "mirage_isles");
        assertNotEquals("mushroom", region);
        assertNotEquals("magnetic", region);
    }

    @Test
    public void themedRegionHostsAModBiomeWhenNoCommonRegionAcceptsItsRole() throws Exception {
        ModIngestReport report = ingest(15, true);

        assertEquals("mushroom", region(report, "spore_hollow"));
    }

    private ModIngestReport ingest(int themedRarity, boolean themedCaves) throws IOException {
        Path root = folder.newFolder().toPath();
        Path source = root.resolve("source");
        ModIngestFixture.write(source, "dimensions/overworld.json", """
                {"name": "Overworld", "environment": "NORMAL", "regions": ["temperate", "hot", "cold", "wet", "magnetic", "mushroom"],
                 "dimensionHeight": {"min": -64, "max": 320}}
                """);
        region(source, "temperate", 1, List.of("temperate/plains"), List.of());
        region(source, "hot", 1, List.of("hot/desert"), List.of());
        region(source, "cold", 1, List.of("cold/snow"), List.of());
        region(source, "wet", 1, List.of("wet/swamp"), List.of());
        region(source, "magnetic", themedRarity, List.of("magnetic/mycelium", "magnetic/plains", "magnetic/frozen", "magnetic/marsh"), List.of());
        region(source, "mushroom", themedRarity, List.of("mushroom/fields", "mushroom/forest", "mushroom/crimson", "mushroom/warped"),
                themedCaves ? List.of("mushroom/caves") : List.of());
        biome(source, "temperate/plains", "minecraft:plains");
        biome(source, "hot/desert", "minecraft:desert");
        biome(source, "cold/snow", "minecraft:snowy_plains");
        biome(source, "wet/swamp", "minecraft:swamp");
        biome(source, "magnetic/mycelium", "minecraft:mushroom_fields");
        biome(source, "magnetic/plains", "minecraft:plains");
        biome(source, "magnetic/frozen", "minecraft:snowy_plains");
        biome(source, "magnetic/marsh", "minecraft:swamp");
        biome(source, "mushroom/fields", "minecraft:mushroom_fields");
        biome(source, "mushroom/forest", "minecraft:mushroom_fields");
        biome(source, "mushroom/crimson", "minecraft:crimson_forest");
        biome(source, "mushroom/warped", "minecraft:warped_forest");
        biome(source, "mushroom/caves", "minecraft:lush_caves");
        Path target = root.resolve("packs").resolve("isles");
        return ModIngester.ingest(new ModIngestRequest(source, target, "overworld", catalog(), Set.of(MOD), Set.of(), false));
    }

    private static String region(ModIngestReport report, String biome) {
        ModIngestItem item = ModIngestFixture.item(report, MOD + ":" + biome);
        assertEquals(item.reason(), ModIngestTier.ADOPTED, item.tier());
        String reason = item.reason();
        int start = reason.indexOf("in region ") + "in region ".length();
        return reason.substring(start, reason.indexOf(' ', start));
    }

    private static void region(Path pack, String key, int rarity, List<String> land, List<String> caves) throws IOException {
        ModIngestFixture.write(pack, "regions/" + key + ".json", "{\"name\": \"" + key + "\", \"rarity\": " + rarity
                + ", \"landBiomes\": " + quoted(land) + ", \"caveBiomes\": " + quoted(caves) + "}");
    }

    private static String quoted(List<String> keys) {
        StringBuilder array = new StringBuilder("[");
        for (String key : keys) {
            array.append(array.length() == 1 ? "" : ", ").append('"').append(key).append('"');
        }
        return array.append(']').toString();
    }

    private static void biome(Path pack, String key, String derivative) throws IOException {
        ModIngestFixture.write(pack, "biomes/" + key + ".json", "{\"name\": \"" + key + "\", \"derivative\": \"" + derivative + "\"}");
    }

    private static WorldgenCatalog catalog() {
        List<CatalogBiome> biomes = new ArrayList<>();
        biomes.add(biome("minecraft:plains", 0.8F, 0.4F, "c:is_plains"));
        biomes.add(biome("minecraft:desert", 2.0F, 0.0F, "c:is_desert"));
        biomes.add(biome("minecraft:snowy_plains", 0.0F, 0.5F, "c:is_snowy"));
        biomes.add(biome("minecraft:swamp", 0.8F, 0.9F, "c:is_swamp"));
        biomes.add(biome("minecraft:mushroom_fields", 0.9F, 1.0F, "c:is_mushroom"));
        biomes.add(biome("minecraft:crimson_forest", 2.0F, 0.0F, "c:is_nether"));
        biomes.add(biome("minecraft:warped_forest", 2.0F, 0.0F, "c:is_nether"));
        biomes.add(biome("minecraft:lush_caves", 0.5F, 0.5F, "c:is_cave"));
        biomes.add(biome(MOD + ":mirage_isles", 0.7F, 0.8F, "c:is_mushroom"));
        biomes.add(biome(MOD + ":spore_hollow", 0.9F, 1.0F, "c:is_cave"));
        return new WorldgenCatalog(WorldgenCatalog.FORMAT, "26.3", "fabric", 1L, "affinity",
                List.of(new CatalogNamespace(MOD, "Mod Isles", "1")), biomes, List.of(), List.of(),
                List.of(new CatalogDimensionType("minecraft:overworld", -64, 384, 384, null)), List.of(), Map.of());
    }

    private static CatalogBiome biome(String key, float temperature, float downfall, String categoryTag) {
        List<List<String>> steps = new ArrayList<>(11);
        for (int index = 0; index < 11; index++) {
            steps.add(List.of());
        }
        return new CatalogBiome(key, temperature, downfall, true, List.of("minecraft:is_overworld", categoryTag), steps, List.of(), null);
    }
}
