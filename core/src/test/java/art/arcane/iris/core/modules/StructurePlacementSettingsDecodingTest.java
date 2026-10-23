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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.structure.NativeStructureSource;
import art.arcane.iris.builtin.structure.NativeStructureSuppression;
import art.arcane.iris.builtin.structure.StructureAnchorMode;
import art.arcane.iris.builtin.structure.StructureDistribution;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.StructurePlacementSettings;
import art.arcane.iris.builtin.structure.StructureTerrainMode;
import art.arcane.iris.engine.framework.StructurePlacementGrid;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import com.volmit.config.Level;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The {@code "structures"} of dimensions, regions and biomes as packs have always written them, read into
 * {@link StructurePlacementSettings}. The pins are what the old host placement class gave for the same JSON.
 */
public class StructurePlacementSettingsDecodingTest {
    /** The overworld's ancient city placement, as the pack wrote it before the move. */
    private static final String OVERWORLD = """
            {
              "structures": [
                {
                  "nativeStructures": [
                    {
                      "structure": "minecraft:ancient_city"
                    }
                  ],
                  "placementId": "ancient-city-native",
                  "nativeSuppression": "REPLACE_SOURCE",
                  "underground": true,
                  "terrain": {
                    "mode": "FORCE_CARVE",
                    "horizontalPadding": 14,
                    "ceilingPadding": 12,
                    "floorPadding": 0,
                    "shape": "ERODED",
                    "erosionStrength": 1.0,
                    "erosionFrequency": 0.05
                  },
                  "stilt": {
                    "maxDepth": 768,
                    "spacing": 1,
                    "palette": {
                      "style": {
                        "style": "STATIC"
                      },
                      "palette": [
                        {
                          "block": "minecraft:deepslate_bricks",
                          "weight": 6
                        },
                        {
                          "block": "minecraft:cracked_deepslate_bricks",
                          "weight": 1
                        },
                        {
                          "block": "minecraft:deepslate_tiles",
                          "weight": 2
                        },
                        {
                          "block": "minecraft:cracked_deepslate_tiles",
                          "weight": 1
                        }
                      ]
                    }
                  },
                  "minHeight": -220,
                  "maxHeight": -220,
                  "distribution": "RANDOM_SPREAD",
                  "spacing": 64,
                  "separation": 5,
                  "salt": 42069
                }
              ]
            }
            """;
    /** The same placement without its id, a second source, a cave anchor and density starts. */
    private static final String ANONYMOUS = """
            {"structures": [{"nativeStructures": [{"structure": "minecraft:ancient_city"}, {"structure": "minecraft:trial_chambers", "weight": 3}], "nativeSuppression": "REPLACE_SOURCE", "underground": true, "terrain": {"mode": "FORCE_CARVE", "horizontalPadding": 14, "ceilingPadding": 12, "floorPadding": 0, "shape": "ERODED", "erosionStrength": 1.0, "erosionFrequency": 0.05}, "stilt": {"maxDepth": 768, "spacing": 1, "palette": {"style": {"style": "STATIC"}, "palette": [{"block": "minecraft:deepslate_bricks", "weight": 6}, {"block": "minecraft:cracked_deepslate_bricks", "weight": 1}, {"block": "minecraft:deepslate_tiles", "weight": 2}, {"block": "minecraft:cracked_deepslate_tiles", "weight": 1}]}}, "minHeight": -220, "maxHeight": -220, "distribution": "DENSITY", "spacing": 64, "separation": 5, "salt": 42069, "anchor": "CAVE_FLOOR", "caveBiomes": ["Deep", " crystal"], "density": 0.4}]}
            """;
    private static final long WORLD_SEED = 1337L;
    private static final int SCANNED_CHUNKS_FROM = -32;
    private static final int SCANNED_CHUNKS_TO = 32;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private LoadedPack pack;
    private ModuleSettingsDecoder decoder;

    @Before
    public void loadBuiltIns() throws IOException {
        Path packFolder = folder.newFolder("pack").toPath();
        pack = LoadedPack.load(packFolder, ModuleHost.of(BuiltInPack.allBuiltIns())
                .describingHostTypesIn(IrisModuleHost.HOST_TYPE_PACKAGE).copyingJarsTo(folder.newFolder("copies").toPath()));
        decoder = new ModuleSettingsDecoder(pack.catalog(), packFolder, Gson::new);
    }

    @After
    public void closePack() {
        pack.close();
    }

    @Test
    public void theOverworldPlacementReadsAsThePackWroteIt() {
        StructurePlacement placement = placements(Level.DIMENSION, OVERWORLD).getFirst();

        assertEquals(List.of("minecraft:ancient_city"), placement.nativeStructures().stream().map(NativeStructureSource::structure).toList());
        assertEquals(1, placement.nativeStructures().getFirst().weight());
        assertTrue(placement.nativeStructures().getFirst().jigsaw().isEmpty());
        assertEquals(List.of(), placement.structures());
        assertEquals("ancient-city-native", placement.placementId());
        assertEquals(NativeStructureSuppression.REPLACE_SOURCE, placement.nativeSuppression());
        assertTrue(placement.underground());
        assertEquals(StructureTerrainMode.FORCE_CARVE, placement.resolvedTerrain().resolvedMode());
        assertEquals(14, placement.resolvedTerrain().horizontalPadding());
        assertEquals(768, placement.stilt().orElseThrow().maxDepth());
        assertEquals(-220, placement.minHeight());
        assertEquals(-220, placement.maxHeight());
        assertEquals(StructureDistribution.RANDOM_SPREAD, placement.distribution());
        assertEquals(64, placement.spacing());
        assertEquals(5, placement.separation());
        assertEquals(42069, placement.salt());
        assertEquals(StructureAnchorMode.HEIGHT_BAND, placement.resolvedAnchor());
    }

    @Test
    public void placementsKeepTheirIdentityAndStarts() {
        StructurePlacement overworld = placements(Level.DIMENSION, OVERWORLD).getFirst();
        StructurePlacement anonymous = placements(Level.BIOME, ANONYMOUS).getFirst();

        assertEquals(-5401497891314909808L, StructurePlacementGrid.placementIdentity(overworld));
        assertEquals(-2165116524116229836L, StructurePlacementGrid.placementRng(overworld, 7, -11, 123L).getSeed());
        assertEquals(new Starts(2, -939), starts(overworld));
        assertEquals(-5867838400029675033L, StructurePlacementGrid.placementIdentity(anonymous));
        assertEquals(-4577893773733009372L, StructurePlacementGrid.placementRng(anonymous, 7, -11, 123L).getSeed());
        assertEquals(new Starts(1585, 865), starts(anonymous));
    }

    @Test
    public void regionsAndBiomesDeclarePlacementsUnderTheSameKey() {
        assertTrue(decoder.keys(Level.REGION).contains("structures"));
        assertTrue(decoder.keys(Level.BIOME).contains("structures"));
        assertEquals(1, placements(Level.REGION, OVERWORLD).size());
    }

    private List<StructurePlacement> placements(Level target, String json) {
        return decoder.decode(target, new JSONObject(json), "test resource")
                .find(StructurePlacementSettings.class).orElseThrow().placements();
    }

    private static Starts starts(StructurePlacement placement) {
        int count = 0;
        long checksum = 0;
        for (int chunkX = SCANNED_CHUNKS_FROM; chunkX < SCANNED_CHUNKS_TO; chunkX++) {
            for (int chunkZ = SCANNED_CHUNKS_FROM; chunkZ < SCANNED_CHUNKS_TO; chunkZ++) {
                if (StructurePlacementGrid.startsInChunk(placement, chunkX, chunkZ, WORLD_SEED)) {
                    count++;
                    checksum += chunkX * 31L + chunkZ;
                }
            }
        }
        return new Starts(count, checksum);
    }

    private record Starts(int count, long checksum) {
    }
}
