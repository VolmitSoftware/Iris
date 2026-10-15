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
import art.arcane.iris.pack.ingest.catalog.CatalogNamespace;
import art.arcane.iris.pack.ingest.catalog.CatalogPlacedFeature;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogIndex;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class VegetationOcclusionTest {
    private static final double CANOPY = 1D - Math.exp(-10D * VegetationOcclusion.CROWN_COLUMNS / 256D);
    private static final double GRASS_COVERAGE = 1D - Math.exp(-(24D * 32D * 0.25D) / 256D);

    @Test
    public void flowersPlacedAfterTreesSeeTheTreeCanopy() {
        VegetationOcclusion estimate = new VegetationOcclusion(WorldgenCatalogIndex.of(catalog()));

        assertEquals(CANOPY, estimate.occlusion("mod:flowers_after", Set.of("mod:woods")).canopy(), 0.000001D);
        assertEquals(0D, estimate.occlusion("mod:flowers_before", Set.of("mod:woods")).canopy(), 0.000001D);
        assertEquals(CANOPY / 2D, estimate.occlusion("mod:flowers_after", Set.of("mod:woods", "mod:meadow")).canopy(), 0.000001D);
    }

    @Test
    public void surfacePatchesPlacedEarlierOccupyTheirShareOfColumns() {
        VegetationOcclusion estimate = new VegetationOcclusion(WorldgenCatalogIndex.of(catalog()));

        assertEquals(GRASS_COVERAGE, estimate.occlusion("mod:flowers_after", Set.of("mod:woods")).occupancy(), 0.000001D);
        assertEquals(0D, estimate.occlusion("mod:flowers_before", Set.of("mod:woods")).occupancy(), 0.000001D);
        assertEquals(GRASS_COVERAGE / 2D, estimate.occlusion("mod:flowers_after", Set.of("mod:woods", "mod:meadow")).occupancy(), 0.000001D);
    }

    private static WorldgenCatalog catalog() {
        List<CatalogBiome> biomes = List.of(
                biome("mod:woods", List.of("mod:flowers_before", "mod:oak_trees", "mod:grass_patch", "mod:flowers_after")),
                biome("mod:meadow", List.of("mod:flowers_after")));
        List<CatalogPlacedFeature> features = new ArrayList<>();
        features.add(new CatalogPlacedFeature("mod:oak_trees", "mod:oak_trees", "minecraft:tree", JsonParser.parseString("{}"),
                JsonParser.parseString("[{\"type\": \"minecraft:count\", \"count\": 10}, {\"type\": \"minecraft:in_square\"},"
                        + "{\"type\": \"minecraft:surface_water_depth_filter\", \"max_water_depth\": 0}, {\"type\": \"minecraft:biome\"}]")));
        features.add(new CatalogPlacedFeature("mod:grass_patch", "mod:grass_patch", "minecraft:simple_block",
                JsonParser.parseString("{\"to_place\": {\"id\": \"minecraft:short_grass\"}}"),
                JsonParser.parseString("[{\"type\": \"minecraft:count\", \"count\": 24}, {\"type\": \"minecraft:in_square\"},"
                        + "{\"type\": \"minecraft:heightmap\", \"heightmap\": \"WORLD_SURFACE_WG\"}, {\"type\": \"minecraft:biome\"},"
                        + "{\"type\": \"minecraft:count\", \"count\": 32},"
                        + "{\"type\": \"minecraft:offset\", \"y\": {\"type\": \"minecraft:trapezoid\", \"min\": -3, \"max\": 3, \"plateau\": 0}}]")));
        return new WorldgenCatalog(WorldgenCatalog.FORMAT, "26.3", "fabric", 1L, "canopy", List.of(new CatalogNamespace("mod", "Mod", "1")),
                biomes, features, List.of(), List.of(), List.of(), Map.of());
    }

    private static CatalogBiome biome(String key, List<String> vegetal) {
        List<List<String>> steps = new ArrayList<>(11);
        for (int index = 0; index < 11; index++) {
            steps.add(index == ModIngestFixture.VEGETAL_STEP ? vegetal : List.of());
        }
        return new CatalogBiome(key, 0.7F, 0.8F, true, List.of("minecraft:is_overworld"), steps, List.of(), null);
    }
}
