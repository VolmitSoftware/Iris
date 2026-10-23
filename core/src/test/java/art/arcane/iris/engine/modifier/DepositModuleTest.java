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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.deposit.DepositSettings;
import art.arcane.iris.builtin.deposit.DepositGenerator;
import art.arcane.iris.engine.object.TestSettings;
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.config.SettingsStack;
import com.volmit.resource.ResourceId;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DepositModuleTest {
    private static final int COLUMN_X = 39;
    private static final int COLUMN_Z = -9;

    @Test
    public void everyLevelAtTheColumnStacksInLevelOrder() {
        DepositGenerator dimension = new DepositGenerator();
        DepositGenerator region = new DepositGenerator();
        DepositGenerator biome = new DepositGenerator();
        DepositGenerator elsewhere = new DepositGenerator();
        FeatureSettings settings = new FeatureSettings() {
            @Override
            @SuppressWarnings("unchecked")
            public <F extends IrisFeature> SettingsStack<F> at(Class<F> type, int worldX, int worldZ) {
                if (worldX != COLUMN_X || worldZ != COLUMN_Z) {
                    return SettingsStack.of(List.of((Entry<F>) entry(Level.BIOME, "biome/other", elsewhere)));
                }
                return SettingsStack.of(List.of((Entry<F>) entry(Level.DIMENSION, "dimension/overworld", dimension),
                        (Entry<F>) entry(Level.REGION, "region/hot", region),
                        (Entry<F>) entry(Level.BIOME, "biome/desert", biome)));
            }
        };

        assertEquals(List.of(dimension, region, biome), DepositModule.generatorsAt(settings, COLUMN_X, COLUMN_Z));
    }

    private static FeatureSettings.Entry<DepositSettings> entry(Level level, String source, DepositGenerator generator) {
        return new FeatureSettings.Entry<>(level, ResourceId.parse("pack:" + source), new DepositSettings(List.of(generator)));
    }

    private static final int HEIGHT = 128;
    private static final int GROUND = 80;
    private static final BlockState STONE = new BlockState("minecraft:stone");

    @Test
    public void aDimensionsDepositsReplaceStoneInsideTheirBandAndNothingElse() {
        ApiChunk chunk = groundedChunk()
                .dimensionSettings(depositsOf("{\"minHeight\": 10, \"maxHeight\": 40, \"minSize\": 6, \"maxSize\": 6, \"minPerChunk\": 4, \"maxPerChunk\": 4,"
                        + " \"varience\": 1, \"palette\": [{\"block\": \"iron_ore\"}], \"replaceableBlocks\": [\"stone\"]}"));
        chunk.set(8, 20, 8, new BlockState("minecraft:dirt"));

        chunk.generate(ApiChunk.terrainStandIn(), new DepositModule());

        int ores = countOf(chunk, new BlockState("minecraft:iron_ore"));
        assertTrue("some ore was placed: " + ores, ores > 0);
        assertEquals("the dirt is not stone", new BlockState("minecraft:dirt"), chunk.get(8, 20, 8));
        for (int x = 0; x < ApiChunk.WIDTH; x++) {
            for (int z = 0; z < ApiChunk.WIDTH; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (chunk.get(x, y, z).equals(new BlockState("minecraft:iron_ore"))) {
                        assertTrue("ore at " + y + " is below the ground and inside the band's reach", y <= GROUND - 7);
                    }
                }
            }
        }
    }

    @Test
    public void depositsAreTheSameWheneverTheSameChunkIsGenerated() {
        DepositSettings deposits = depositsOf("{\"minHeight\": 10, \"maxHeight\": 60, \"minSize\": 3, \"maxSize\": 9, \"minPerChunk\": 3, \"maxPerChunk\": 6,"
                + " \"palette\": [{\"block\": \"coal_ore\"}, {\"block\": \"iron_ore\"}]}");
        ApiChunk first = groundedChunk().dimensionSettings(deposits);
        ApiChunk second = groundedChunk().dimensionSettings(deposits);

        first.generate(ApiChunk.terrainStandIn(), new DepositModule());
        second.generate(ApiChunk.terrainStandIn(), new DepositModule());

        for (int x = 0; x < ApiChunk.WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < ApiChunk.WIDTH; z++) {
                    assertEquals(x + " " + y + " " + z, first.get(x, y, z), second.get(x, y, z));
                }
            }
        }
        assertTrue(countOf(first, new BlockState("minecraft:coal_ore")) + countOf(first, new BlockState("minecraft:iron_ore")) > 0);
    }

    @Test
    public void depositsLeaveCarvedVoidsAlone() {
        DepositSettings deposits = depositsOf("{\"minHeight\": 10, \"maxHeight\": 60, \"minSize\": 40, \"maxSize\": 40, \"minPerChunk\": 6, \"maxPerChunk\": 6,"
                + " \"palette\": [{\"block\": \"iron_ore\"}]}");
        ApiChunk free = groundedChunk().dimensionSettings(deposits);
        ApiChunk carved = groundedChunk().dimensionSettings(deposits);
        for (int x = 0; x < ApiChunk.WIDTH; x++) {
            for (int z = 0; z < ApiChunk.WIDTH; z++) {
                for (int y = 20; y < 40; y++) {
                    carved.carve(32 + x, y, -16 + z);
                }
            }
        }

        free.generate(ApiChunk.terrainStandIn(), new DepositModule());
        carved.generate(ApiChunk.terrainStandIn(), new DepositModule());

        assertTrue(countBetween(free, 20, 40) > 0);
        assertEquals(0, countBetween(carved, 20, 40));
        assertTrue("above and below the voids the same deposits grow", countBetween(carved, 0, 20) + countBetween(carved, 40, HEIGHT) > 0);
    }

    @Test
    public void aChunkWithoutDepositsAtItsCentreIsLeftAlone() {
        ApiChunk chunk = groundedChunk();

        chunk.generate(ApiChunk.terrainStandIn(), new DepositModule());

        assertEquals(0, countOf(chunk, new BlockState("minecraft:iron_ore")));
    }

    private static ApiChunk groundedChunk() {
        ApiChunk chunk = new ApiChunk(32, -16, HEIGHT).surface((x, z) -> GROUND);
        for (int x = 0; x < ApiChunk.WIDTH; x++) {
            for (int z = 0; z < ApiChunk.WIDTH; z++) {
                for (int y = 0; y <= GROUND; y++) {
                    chunk.set(x, y, z, STONE);
                }
            }
        }
        return chunk;
    }

    private static DepositSettings depositsOf(String generatorJson) {
        return TestSettings.of(DepositSettings.class, "{\"generators\": [" + generatorJson + "]}");
    }

    private static int countOf(ApiChunk chunk, BlockState block) {
        int count = 0;
        for (int x = 0; x < ApiChunk.WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < ApiChunk.WIDTH; z++) {
                    count += chunk.get(x, y, z).equals(block) ? 1 : 0;
                }
            }
        }
        return count;
    }

    private static int countBetween(ApiChunk chunk, int from, int to) {
        int count = 0;
        for (int x = 0; x < ApiChunk.WIDTH; x++) {
            for (int y = from; y < to; y++) {
                for (int z = 0; z < ApiChunk.WIDTH; z++) {
                    count += chunk.get(x, y, z).equals(new BlockState("minecraft:iron_ore")) ? 1 : 0;
                }
            }
        }
        return count;
    }
}
