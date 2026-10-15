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

import art.arcane.iris.pack.ingest.catalog.CatalogDimensionType;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import com.google.gson.JsonObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class IngestHeightBoundsTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void relativeAnchorsResolveAgainstThePacksOwnHeight() throws Exception {
        JsonObject stone = tinDeposit("\"dimensionHeight\": {\"min\": 0, \"max\": 256}", ModIngestFixture.catalog());

        assertEquals(16, stone.get("minHeight").getAsInt());
        assertEquals(235, stone.get("maxHeight").getAsInt());
    }

    @Test
    public void relativeAnchorsResolveAgainstTheBorrowedTypeWhenSet() throws Exception {
        WorldgenCatalog fixture = ModIngestFixture.catalog();
        List<CatalogDimensionType> types = new ArrayList<>(fixture.dimensionTypes());
        types.add(new CatalogDimensionType("examplemod:skyrealm", 0, 256, 256, null));
        WorldgenCatalog catalog = new WorldgenCatalog(fixture.format(), fixture.minecraftVersion(), fixture.platform(),
                fixture.capturedAtEpochMillis(), fixture.contentFingerprint(), fixture.namespaces(), fixture.biomes(),
                fixture.placedFeatures(), fixture.structures(), types, fixture.levelStems());

        JsonObject stone = tinDeposit("\"dimensionHeight\": {\"min\": 0, \"max\": 192}, \"borrowedDimensionType\": \"examplemod:skyrealm\"",
                catalog);

        assertEquals(16, stone.get("minHeight").getAsInt());
        assertEquals(235, stone.get("maxHeight").getAsInt());
    }

    private JsonObject tinDeposit(String heightFields, WorldgenCatalog catalog) throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        ModIngestFixture.write(source, "dimensions/overworld.json", """
                {
                  "name": "Overworld",
                  "environment": "NORMAL",
                  "regions": ["hot", "temperate"],
                  %s
                }
                """.formatted(heightFields));
        Path target = root.resolve("packs").resolve("modded");
        ModIngester.ingest(new ModIngestRequest(source, target, "overworld", catalog, Set.of(ModIngestFixture.MOD), Set.of(), false));
        JsonObject dimension = ModIngestFixture.readJson(target.resolve("dimensions/modded.json"));
        JsonObject stone = ModIngestFixture.findByPaletteBlock(dimension.getAsJsonArray("deposits"), "examplemod:tin_ore");
        assertNotNull(stone);
        return stone;
    }
}
