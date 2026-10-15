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


package art.arcane.iris.pack.ingest.catalog;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WorldgenCatalogStoreTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void writeThenReadPreservesEveryRecord() throws IOException {
        JsonObject config = new JsonObject();
        config.add("size", new JsonPrimitive(9));
        WorldgenCatalog catalog = new WorldgenCatalog(
                WorldgenCatalog.FORMAT,
                "26.3",
                "neoforge",
                1234L,
                "abc",
                List.of(new CatalogNamespace("examplemod", "Example Mod", "1.0.0")),
                List.of(new CatalogBiome("examplemod:glade", 0.7F, 0.8F, true, List.of("minecraft:is_overworld"),
                        List.of(List.of(), List.of("examplemod:ore_glimmer")),
                        List.of(new CatalogSpawn("creature", "minecraft:sheep", 12, 2, 4)), new JsonObject())),
                List.of(new CatalogPlacedFeature("examplemod:ore_glimmer", "examplemod:ore_glimmer", "minecraft:ore", config, null)),
                List.of(new CatalogStructure("examplemod:tower", "minecraft:jigsaw", "surface_structures", "none",
                        "examplemod:has_tower", List.of("examplemod:glade"), List.of("examplemod:towers"))),
                List.of(new CatalogDimensionType("examplemod:sky", 0, 256, 256, new JsonObject())),
                List.of(new CatalogLevelStem("examplemod:sky", "examplemod:sky", "minecraft:noise", "minecraft:multi_noise", null)), Map.of());
        Path file = folder.getRoot().toPath().resolve("nested").resolve(WorldgenCatalogStore.FILE_NAME);

        WorldgenCatalogStore.write(file, catalog);
        WorldgenCatalog read = WorldgenCatalogStore.read(file);

        assertEquals(catalog, read);
        assertTrue(Files.list(file.getParent()).allMatch(path -> path.equals(file)));
    }

    @Test
    public void indexResolvesNamespacesAndFeatureOwners() {
        WorldgenCatalog catalog = new WorldgenCatalog(WorldgenCatalog.FORMAT, "26.3", "fabric", 0L, "",
                List.of(),
                List.of(new CatalogBiome("examplemod:glade", 0.7F, 0.8F, true, List.of(), List.of(List.of("examplemod:flowers")), List.of(), null),
                        new CatalogBiome("minecraft:forest", 0.7F, 0.8F, true, List.of(), List.of(List.of("examplemod:flowers")), List.of(), null),
                        new CatalogBiome("minecraft:desert", 2.0F, 0.0F, false, List.of(), List.of(), List.of(), null)),
                List.of(), List.of(), List.of(), List.of(), Map.of());
        WorldgenCatalogIndex index = WorldgenCatalogIndex.of(catalog);

        assertEquals(1, index.biomesIn("examplemod").size());
        assertEquals(2, index.biomesDeclaring("examplemod:flowers").size());
        assertEquals("minecraft", WorldgenCatalogIndex.namespaceOf("plains"));
        assertTrue(index.biome("minecraft:desert").isPresent());
    }
}
