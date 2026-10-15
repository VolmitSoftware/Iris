/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded.catalog;

import art.arcane.iris.modded.catalog.WorldgenCatalogSummary.NamespaceSummary;
import art.arcane.iris.modded.catalog.WorldgenCatalogSummary.TypeCount;
import art.arcane.iris.pack.ingest.catalog.CatalogBiome;
import art.arcane.iris.pack.ingest.catalog.CatalogDimensionType;
import art.arcane.iris.pack.ingest.catalog.CatalogLevelStem;
import art.arcane.iris.pack.ingest.catalog.CatalogNamespace;
import art.arcane.iris.pack.ingest.catalog.CatalogPlacedFeature;
import art.arcane.iris.pack.ingest.catalog.CatalogStructure;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorldgenCatalogSummaryTest {
    @Test
    public void summariesCountEveryRegistryPerNamespace() {
        List<NamespaceSummary> summaries = WorldgenCatalogSummary.namespaces(catalog());

        assertEquals(List.of("examplemod", "minecraft", "quietmod"),
                summaries.stream().map((NamespaceSummary summary) -> summary.namespace().namespace()).toList());
        NamespaceSummary example = summaries.getFirst();
        assertEquals(List.of("examplemod:glade", "examplemod:marsh"), example.biomes());
        assertEquals(6, example.placedFeatures().size());
        assertEquals(List.of("examplemod:tower"), example.structures());
        assertEquals(List.of("examplemod:sky"), example.dimensionTypes());
        assertEquals(List.of("examplemod:sky"), example.levelStems());
        assertTrue(example.hasContent());
        assertFalse(summaries.get(2).hasContent());
    }

    @Test
    public void featureTypesAreRankedByCountThenName() {
        NamespaceSummary example = WorldgenCatalogSummary.namespace(catalog(), "examplemod").orElseThrow();

        assertEquals(List.of(new TypeCount("minecraft:ore", 3), new TypeCount("minecraft:random_patch", 2),
                new TypeCount("examplemod:big_tree", 1)), example.featureTypes());
        assertEquals("ore 3, random_patch 2 (+1 more)", WorldgenCatalogSummary.featureTypes(example.featureTypes(), 2));
        assertEquals("ore 3, random_patch 2, examplemod:big_tree 1",
                WorldgenCatalogSummary.featureTypes(example.featureTypes(), 5));
    }

    @Test
    public void unknownNamespaceHasNoSummary() {
        Optional<NamespaceSummary> missing = WorldgenCatalogSummary.namespace(catalog(), "missingmod");

        assertTrue(missing.isEmpty());
    }

    @Test
    public void keyNamespacesWithoutModMetadataStillSummarize() {
        WorldgenCatalog catalog = new WorldgenCatalog(WorldgenCatalog.FORMAT, "26.3", "fabric", 1L, "x",
                List.of(), List.of(biome("datapack:dunes")), List.of(), List.of(), List.of(), List.of());

        NamespaceSummary summary = WorldgenCatalogSummary.namespace(catalog, "datapack").orElseThrow();

        assertEquals(new CatalogNamespace("datapack", "datapack", ""), summary.namespace());
        assertEquals(List.of("datapack:dunes"), summary.biomes());
    }

    @Test
    public void limitedListsShortenOwnKeysAndCountTheRest() {
        assertEquals("glade, marsh (+1 more)",
                WorldgenCatalogSummary.limited(List.of("examplemod:glade", "examplemod:marsh", "examplemod:moor"), "examplemod", 2));
        assertEquals("glade, minecraft:plains",
                WorldgenCatalogSummary.limited(List.of("examplemod:glade", "minecraft:plains"), "examplemod", 5));
        assertEquals("", WorldgenCatalogSummary.limited(List.of(), "examplemod", 5));
    }

    private static WorldgenCatalog catalog() {
        return new WorldgenCatalog(WorldgenCatalog.FORMAT, "26.3", "neoforge", 1L, "x",
                List.of(new CatalogNamespace("examplemod", "Example Mod", "1.0.0"),
                        new CatalogNamespace("minecraft", "Minecraft", "26.3"),
                        new CatalogNamespace("quietmod", "Quiet Mod", "2.0.0")),
                List.of(biome("examplemod:marsh"), biome("examplemod:glade"), biome("minecraft:plains")),
                List.of(feature("examplemod:ore_a", "minecraft:ore"), feature("examplemod:ore_b", "minecraft:ore"),
                        feature("examplemod:ore_c", "minecraft:ore"), feature("examplemod:patch_a", "minecraft:random_patch"),
                        feature("examplemod:patch_b", "minecraft:random_patch"), feature("examplemod:tree", "examplemod:big_tree"),
                        feature("minecraft:ore_iron", "minecraft:ore")),
                List.of(new CatalogStructure("examplemod:tower", "minecraft:jigsaw", "surface_structures", "none", "",
                        List.of(), List.of())),
                List.of(new CatalogDimensionType("examplemod:sky", 0, 256, 256, null)),
                List.of(new CatalogLevelStem("examplemod:sky", "examplemod:sky", "minecraft:noise", "minecraft:fixed", null)));
    }

    private static CatalogBiome biome(String key) {
        return new CatalogBiome(key, 0.5F, 0.5F, true, List.of(), List.of(), List.of(), null);
    }

    private static CatalogPlacedFeature feature(String key, String type) {
        return new CatalogPlacedFeature(key, key, type, null, null);
    }
}
