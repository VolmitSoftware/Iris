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

import art.arcane.iris.core.modules.CarveTestTerrain;
import art.arcane.iris.core.modules.CarveTestWorld;
import art.arcane.iris.core.modules.InMemoryMantle;
import art.arcane.iris.engine.mantle.components.CarveWorld;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import com.volmit.block.BlockState;
import com.volmit.host.ArrayHunk;
import com.volmit.hunk.Hunk;
import com.volmit.resource.ResourceId;
import com.volmit.world.BiomeSample;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The caves the mantle planned become the chunk's blocks: air, lava low down, the biome's wall, floor and ceiling, and the markers cave spawning reads. */
public class CaveApplyingTest {
    private static final int HEIGHT = 64;
    private static final int FLUID_HEIGHT = 20;
    private static final int LAVA_HEIGHT = 4;
    private static final int SURFACE = 40;
    private static final BlockState STONE = new BlockState("minecraft:stone");
    private static final BlockState CAVE_AIR = new BlockState("minecraft:cave_air");
    private static final BlockState LAVA = new BlockState("minecraft:lava");
    private static final BlockState WALL = new BlockState("minecraft:mossy_cobblestone");
    private static final BlockState FLOOR = new BlockState("minecraft:moss_block");
    private static final BlockState CEILING = new BlockState("minecraft:dripstone_block");
    private static final ResourceId LUSH = CarveTestWorld.id("cave/lush");
    private static final MatterCavern AIR_CAVERN = new MatterCavern(true, "", (byte) 0);
    private static final MatterCavern FLUID_CAVERN = new MatterCavern(true, "", (byte) 1);
    private static final int CAVE_X = 5;
    private static final int CAVE_Z = 5;
    private static final int CAVE_FLOOR_Y = 10;
    private static final int CAVE_CEILING_Y = 13;

    private final InMemoryMantle mantle = new InMemoryMantle(HEIGHT / 16);
    private final RecordingCaveDressing dressing = new RecordingCaveDressing();
    private CarveTestWorld world;
    private Hunk<BlockState> blocks;
    private CarveTestTerrain terrain;

    @Before
    public void setUp() {
        world = new CarveTestWorld().biome("cave/lush", 1);
        dressing.walls.put(LUSH, WALL);
        dressing.floors.put(LUSH, List.of(FLOOR));
        dressing.ceilings.put(LUSH, List.of(CEILING));
        blocks = new ArrayHunk<>(BlockState.class, 16, HEIGHT, 16, STONE);
        terrain = new CarveTestTerrain(0, 0, HEIGHT, FLUID_HEIGHT, (x, z) -> SURFACE)
                .biomes((x, y, z) -> new BiomeSample(CarveTestWorld.id("region"), CarveTestWorld.id("plains"), Optional.of(LUSH)));
    }

    @Test
    public void aPlannedCaveBecomesCaveAirWithTheBiomesWallFloorAndCeiling() {
        carve(CAVE_X, CAVE_Z, CAVE_FLOOR_Y, CAVE_CEILING_Y, AIR_CAVERN);

        apply(false);

        for (int y = CAVE_FLOOR_Y; y <= CAVE_CEILING_Y; y++) {
            assertEquals("y=" + y, CAVE_AIR, blocks.get(CAVE_X, y, CAVE_Z));
        }
        assertEquals("the wall beside the cave", WALL, blocks.get(CAVE_X + 1, CAVE_FLOOR_Y + 1, CAVE_Z));
        assertEquals("the floor layer under it", FLOOR, blocks.get(CAVE_X, CAVE_FLOOR_Y - 1, CAVE_Z));
        assertEquals("the ceiling layer over it", CEILING, blocks.get(CAVE_X, CAVE_CEILING_Y + 1, CAVE_Z));
        assertEquals("rock the cave did not touch", STONE, blocks.get(CAVE_X + 3, CAVE_FLOOR_Y + 1, CAVE_Z));
    }

    @Test
    public void lowCavesFillWithLavaAndFluidCavernsWithTheDimensionsFluid() {
        dressing.fluid = new BlockState("minecraft:water");
        carve(CAVE_X, CAVE_Z, LAVA_HEIGHT - 2, LAVA_HEIGHT, AIR_CAVERN);
        carve(CAVE_X + 4, CAVE_Z, CAVE_FLOOR_Y, CAVE_CEILING_Y, FLUID_CAVERN);

        apply(false);

        assertEquals(LAVA, blocks.get(CAVE_X, LAVA_HEIGHT, CAVE_Z));
        assertEquals(LAVA, blocks.get(CAVE_X, LAVA_HEIGHT - 1, CAVE_Z));
        assertEquals(new BlockState("minecraft:water"), blocks.get(CAVE_X + 4, CAVE_FLOOR_Y + 1, CAVE_Z));
    }

    @Test
    public void aDimensionAboveIsLeftAloneUnlessCarvingIsAllowedThroughIt() {
        carve(CAVE_X, CAVE_Z, CAVE_FLOOR_Y, CAVE_CEILING_Y, AIR_CAVERN);
        terrain.upperDimension((x, z) -> CAVE_FLOOR_Y + 1);

        apply(false);
        assertEquals("the first block of the upper dimension stays", STONE, blocks.get(CAVE_X, CAVE_FLOOR_Y + 1, CAVE_Z));
        assertEquals("what lies below it is carved", CAVE_AIR, blocks.get(CAVE_X, CAVE_FLOOR_Y, CAVE_Z));

        blocks = new ArrayHunk<>(BlockState.class, 16, HEIGHT, 16, STONE);
        apply(true);
        assertEquals("carving through it", CAVE_AIR, blocks.get(CAVE_X, CAVE_FLOOR_Y + 1, CAVE_Z));
    }

    @Test
    public void aCaveInTheNeighbouringChunkWallsTheBorderBlocksNextToIt() {
        carve(1, 0, 0, CAVE_Z, CAVE_FLOOR_Y, CAVE_CEILING_Y, AIR_CAVERN);

        apply(false);

        assertEquals("the wall against the neighbour's cave", WALL, blocks.get(15, CAVE_FLOOR_Y + 1, CAVE_Z));
        assertEquals("rock one block further in", STONE, blocks.get(14, CAVE_FLOOR_Y + 1, CAVE_Z));
        assertEquals("rock beside the cave's rows", STONE, blocks.get(15, CAVE_FLOOR_Y + 1, CAVE_Z + 3));
    }

    private void carve(int x, int z, int floorY, int ceilingY, MatterCavern cavern) {
        carve(0, 0, x, z, floorY, ceilingY, cavern);
    }

    private void carve(int chunkX, int chunkZ, int x, int z, int floorY, int ceilingY, MatterCavern cavern) {
        MantleChunk<Matter> chunk = mantle.chunk(chunkX, chunkZ);
        for (int y = floorY; y <= ceilingY; y++) {
            chunk.getOrCreate(y >> 4).<MatterCavern>slice(MatterCavern.class).set(x, y & 15, z, cavern);
        }
    }

    private void apply(boolean upperDimensionCarving) {
        CarveWorld carveWorld = new CarveWorld(world.resources().noise(), 1337L, HEIGHT, FLUID_HEIGHT, LAVA_HEIGHT, CarveTestWorld.MIN_HEIGHT);
        new CaveApplying(world, carveWorld, upperDimensionCarving).apply(mantle.carved(), terrain, 0, 0, blocks, dressing);
    }
}
