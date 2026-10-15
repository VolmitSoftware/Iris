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
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IngestReservedNamespaceTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void defaultIngestNeverAdoptsIrisRegisteredBiomes() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        ModIngestFixture.write(root.resolve("packs").resolve("underworld"), "dimensions/underworld.json", "{\"name\": \"Underworld\"}");
        Path target = root.resolve("packs").resolve("modded");

        ModIngestReport report = ModIngester.ingest(new ModIngestRequest(source, target, "overworld", catalog(), Set.of(), Set.of(), false));

        assertNull(ModIngestFixture.item(report, "irisworldgen:packs/6f76/dimensions/6f76/biomes/dunes"));
        assertNull(ModIngestFixture.item(report, "overworld:dunes"));
        assertNull(ModIngestFixture.item(report, "underworld:ash_fields"));
        assertTrue(report.items().stream().noneMatch(item -> Set.of("irisworldgen", "overworld", "underworld").contains(item.namespace())));
        assertEquals(List.of("examplemod", "othermod"),
                ModIngestFixture.strings(ModIngestFixture.readJson(target.resolve("ingest/manifest.json")).get("namespaces")));
    }

    @Test
    public void explicitIrisNamespacesAreRefused() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        Path target = root.resolve("packs").resolve("modded");

        for (String namespace : List.of("irisworldgen", "overworld")) {
            assertThrows(namespace, IllegalArgumentException.class, () -> ModIngester.ingest(
                    new ModIngestRequest(source, target, "overworld", catalog(), Set.of(namespace), Set.of(), false)));
        }
    }

    private static WorldgenCatalog catalog() {
        WorldgenCatalog fixture = ModIngestFixture.catalog();
        List<CatalogBiome> biomes = new ArrayList<>(fixture.biomes());
        biomes.add(biome("irisworldgen:packs/6f76/dimensions/6f76/biomes/dunes"));
        biomes.add(biome("overworld:dunes"));
        biomes.add(biome("underworld:ash_fields"));
        return new WorldgenCatalog(fixture.format(), fixture.minecraftVersion(), fixture.platform(),
                fixture.capturedAtEpochMillis(), fixture.contentFingerprint(), fixture.namespaces(), biomes,
                fixture.placedFeatures(), fixture.structures(), fixture.dimensionTypes(), fixture.levelStems(), fixture.blockTags());
    }

    private static CatalogBiome biome(String key) {
        List<List<String>> steps = new ArrayList<>(11);
        for (int index = 0; index < 11; index++) {
            steps.add(List.of());
        }
        return new CatalogBiome(key, 1.5F, 0.1F, true, List.of("minecraft:is_overworld"), steps, List.of(), null);
    }
}
