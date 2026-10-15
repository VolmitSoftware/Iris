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

import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ModIngesterBiomeTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path target;
    private ModIngestReport report;

    @Before
    public void ingest() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        target = root.resolve("packs").resolve("modded");
        report = ModIngester.ingest(ModIngestFixture.request(source, target, Set.of(ModIngestFixture.MOD), false));
    }

    @Test
    public void hotModBiomeJoinsHotRegionFromNearestTemplate() throws Exception {
        JsonObject biome = ModIngestFixture.readJson(target.resolve("biomes/ingest/examplemod/scorched_mesa.json"));

        assertEquals("examplemod:scorched_mesa", biome.get("derivative").getAsString());
        assertEquals("Scorched Mesa", biome.get("name").getAsString());
        assertTrue(biome.has("layers"));
        assertTrue("template objects stay when the mod brings no vegetation", biome.has("objects"));
        List<String> hotLand = ModIngestFixture.strings(ModIngestFixture.readJson(target.resolve("regions/hot.json")).get("landBiomes"));
        assertEquals("ingest/examplemod/scorched_mesa", hotLand.get(hotLand.size() - 1));
        assertFalse(ModIngestFixture.strings(ModIngestFixture.readJson(target.resolve("regions/temperate.json")).get("landBiomes"))
                .contains("ingest/examplemod/scorched_mesa"));

        ModIngestItem item = ModIngestFixture.item(report, "examplemod:scorched_mesa");
        assertNotNull(item);
        assertEquals(ModIngestTier.ADOPTED, item.tier());
        assertEquals("biome", item.kind());
        assertEquals("ingest/examplemod/scorched_mesa", item.target());
        assertTrue(item.reason(), item.reason().contains("hot/desert"));
    }

    @Test
    public void forestModBiomeDropsOverridesChildrenAndTreesWhenItBringsItsOwn() throws Exception {
        JsonObject biome = ModIngestFixture.readJson(target.resolve("biomes/ingest/examplemod/maple_woods.json"));

        assertEquals("examplemod:maple_woods", biome.get("derivative").getAsString());
        assertFalse(biome.has("vanillaDerivative"));
        assertFalse(biome.has("customDerivitives"));
        assertFalse(biome.has("biomeScatter"));
        assertFalse(biome.has("children"));
        assertFalse(biome.has("objects"));
        assertFalse(biome.getAsJsonObject("proceduralObjects").has("trees"));
        assertTrue(biome.getAsJsonObject("proceduralObjects").has("ruins"));
        assertTrue(ModIngestFixture.strings(ModIngestFixture.readJson(target.resolve("regions/temperate.json")).get("landBiomes"))
                .contains("ingest/examplemod/maple_woods"));
    }

    @Test
    public void oceanShoreAndCaveBiomesJoinTheMatchingPools() throws Exception {
        JsonObject temperate = ModIngestFixture.readJson(target.resolve("regions/temperate.json"));
        JsonObject hot = ModIngestFixture.readJson(target.resolve("regions/hot.json"));

        assertTrue(ModIngestFixture.strings(temperate.get("seaBiomes")).contains("ingest/examplemod/coral_depths"));
        assertTrue(ModIngestFixture.strings(hot.get("shoreBiomes")).contains("ingest/examplemod/dune_beach"));
        assertTrue(ModIngestFixture.strings(hot.get("caveBiomes")).contains("ingest/examplemod/crystal_caves"));
        assertFalse(temperate.has("caveBiomes"));
        assertEquals("Ocean", ModIngestFixture.readJson(target.resolve("biomes/temperate/ocean.json")).get("name").getAsString());
        assertTrue(ModIngestFixture.item(report, "examplemod:coral_depths").reason().contains("temperate/ocean"));
        assertTrue(ModIngestFixture.item(report, "examplemod:dune_beach").reason().contains("hot/beach"));
        assertTrue(ModIngestFixture.item(report, "examplemod:crystal_caves").reason().contains("caves/dripstone"));
    }

    @Test
    public void riverNetherAndUntaggedBiomesAreSkippedWithReasons() {
        ModIngestItem river = ModIngestFixture.item(report, "examplemod:misty_river");
        ModIngestItem nether = ModIngestFixture.item(report, "examplemod:ashen_wastes");
        ModIngestItem untagged = ModIngestFixture.item(report, "examplemod:void_garden");

        assertEquals(ModIngestTier.SKIPPED, river.tier());
        assertTrue(river.reason(), river.reason().contains("river"));
        assertEquals(ModIngestTier.SKIPPED, nether.tier());
        assertTrue(nether.reason(), nether.reason().contains("nether"));
        assertEquals(ModIngestTier.SKIPPED, untagged.tier());
        assertTrue(untagged.reason(), untagged.reason().contains("minecraft:is_overworld"));
        assertFalse(Files.exists(target.resolve("biomes/ingest/examplemod/misty_river.json")));
        assertFalse(Files.exists(target.resolve("biomes/ingest/examplemod/ashen_wastes.json")));
    }

    @Test
    public void unselectedNamespacesAreLeftAlone() {
        assertNull(ModIngestFixture.item(report, "othermod:bamboo_marsh"));
        assertNull(ModIngestFixture.item(report, "othermod:daisies"));
        assertFalse(Files.exists(target.resolve("biomes/ingest/othermod")));
    }
}
