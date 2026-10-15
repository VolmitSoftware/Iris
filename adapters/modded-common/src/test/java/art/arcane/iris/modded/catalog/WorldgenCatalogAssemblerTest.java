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

import art.arcane.iris.pack.ingest.catalog.CatalogBiome;
import art.arcane.iris.pack.ingest.catalog.CatalogLevelStem;
import art.arcane.iris.pack.ingest.catalog.CatalogNamespace;
import art.arcane.iris.pack.ingest.catalog.CatalogPlacedFeature;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogIndex;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogFingerprint;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModMetadata;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.EncodeFailures;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.WorldgenBiome;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.WorldgenDimensionType;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.WorldgenLevelStem;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.WorldgenPlacedFeature;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.WorldgenSpawn;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.WorldgenStructure;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class WorldgenCatalogAssemblerTest {
    private static final CatalogCaptureContext CONTEXT = new CatalogCaptureContext("26.3", "neoforge", 1234L, List.of(
            new NativeModMetadata("minecraft", "Minecraft", "26.3"),
            new NativeModMetadata("examplemod", "Example Mod", "1.0.0"),
            new NativeModMetadata("quietmod", "Quiet Mod", "2.0.0")));

    @Test
    public void namespacesCombineLoaderModsWithRegistryKeyNamespaces() {
        WorldgenCatalog catalog = WorldgenCatalogAssembler.assemble(snapshot(), CONTEXT);

        assertEquals(List.of(
                new CatalogNamespace("datapack", "datapack", ""),
                new CatalogNamespace("examplemod", "Example Mod", "1.0.0"),
                new CatalogNamespace("minecraft", "Minecraft", "26.3"),
                new CatalogNamespace("quietmod", "Quiet Mod", "2.0.0")), catalog.namespaces());
    }

    @Test
    public void minecraftNamespaceFallsBackToTheRunningVersion() {
        CatalogCaptureContext context = new CatalogCaptureContext("26.3", "forge", 1L, List.of());

        WorldgenCatalog catalog = WorldgenCatalogAssembler.assemble(snapshot(), context);

        assertEquals(new CatalogNamespace("minecraft", "Minecraft", "26.3"), catalog.namespaces().get(2));
    }

    @Test
    public void recordsAreCopiedWithTheirJson() {
        NativeWorldgenSnapshot snapshot = snapshot();

        WorldgenCatalog catalog = WorldgenCatalogAssembler.assemble(snapshot, CONTEXT);

        assertEquals(WorldgenCatalog.FORMAT, catalog.format());
        assertEquals("26.3", catalog.minecraftVersion());
        assertEquals("neoforge", catalog.platform());
        assertEquals(1234L, catalog.capturedAtEpochMillis());
        CatalogBiome glade = catalog.biomes().getFirst();
        assertEquals("examplemod:glade", glade.key());
        assertEquals(0.7F, glade.temperature(), 0F);
        assertEquals(0.4F, glade.downfall(), 0F);
        assertEquals(List.of(List.of(), List.of("examplemod:ore_glimmer")), glade.featureSteps());
        assertEquals("creature", glade.spawns().getFirst().category());
        assertSame(snapshot.biomes().getFirst().definition(), glade.definition());
        CatalogPlacedFeature ore = catalog.placedFeatures().getFirst();
        assertEquals("minecraft:ore", ore.featureType());
        assertSame(snapshot.placedFeatures().getFirst().config(), ore.config());
        CatalogLevelStem stem = catalog.levelStems().getFirst();
        assertEquals("minecraft:multi_noise", stem.biomeSourceType());
        assertEquals("examplemod:towers", catalog.structures().getFirst().structureSets().getFirst());
        assertEquals(384, catalog.dimensionTypes().getFirst().height());
    }

    @Test
    public void fingerprintCoversNamespacesAndEveryRegistryKey() {
        WorldgenCatalog catalog = WorldgenCatalogAssembler.assemble(snapshot(), CONTEXT);

        String expected = WorldgenCatalogFingerprint.of(catalog.namespaces(), Map.of(
                "biome", List.of("datapack:dunes", "examplemod:glade"),
                "placed_feature", List.of("examplemod:ore_glimmer"),
                "structure", List.of("examplemod:tower"),
                "dimension_type", List.of("minecraft:overworld"),
                "level_stem", List.of("minecraft:overworld"),
                "block_tag", List.of("examplemod:glimmer_hosts")));
        assertEquals(expected, catalog.contentFingerprint());
    }

    @Test
    public void onlyBlockTagsReferencedByPlacedFeaturesAreKept() {
        WorldgenCatalog catalog = WorldgenCatalogAssembler.assemble(snapshot(), CONTEXT);

        assertEquals(Map.of("examplemod:glimmer_hosts", List.of("minecraft:dirt", "minecraft:grass_block")), catalog.blockTags());
        assertEquals(List.of("minecraft:dirt", "minecraft:grass_block"),
                WorldgenCatalogIndex.of(catalog).blockTag("examplemod:glimmer_hosts").orElseThrow());
    }

    static NativeWorldgenSnapshot snapshot() {
        JsonObject definition = new JsonObject();
        definition.add("downfall", new JsonPrimitive(0.4F));
        JsonObject config = JsonParser.parseString("""
                {"type": "minecraft:ore", "targets": [{"state": "examplemod:glimmer_ore",
                  "target": {"predicate_type": "minecraft:tag_match", "tag": "examplemod:glimmer_hosts"}}]}
                """).getAsJsonObject();
        return new NativeWorldgenSnapshot(
                List.of(new WorldgenBiome("examplemod:glade", 0.7F, 0.4F, true, List.of("minecraft:is_overworld"),
                                List.of(List.of(), List.of("examplemod:ore_glimmer")),
                                List.of(new WorldgenSpawn("creature", "minecraft:sheep", 12, 2, 4)), definition),
                        new WorldgenBiome("datapack:dunes", 2.0F, 0F, false, List.of(), List.of(), List.of(), null)),
                List.of(new WorldgenPlacedFeature("examplemod:ore_glimmer", "examplemod:ore_glimmer", "minecraft:ore", config, null)),
                List.of(new WorldgenStructure("examplemod:tower", "minecraft:jigsaw", "surface_structures", "beard_thin",
                        "examplemod:has_tower", List.of("examplemod:glade"), List.of("examplemod:towers"))),
                List.of(new WorldgenDimensionType("minecraft:overworld", -64, 384, 384, null)),
                List.of(new WorldgenLevelStem("minecraft:overworld", "minecraft:overworld", "minecraft:noise",
                        "minecraft:multi_noise", null)),
                Map.of("examplemod:glimmer_hosts", List.of("minecraft:dirt", "minecraft:grass_block"),
                        "minecraft:unreferenced", List.of("minecraft:stone")),
                new EncodeFailures(0, List.of(), null));
    }
}
