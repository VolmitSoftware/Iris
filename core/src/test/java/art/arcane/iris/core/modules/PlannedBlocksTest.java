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

import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.mantle.MantleLayer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The mantle's planned blocks as IrisApi's {@code Mantle.blocks()}: what placements wrote into the mantle's block slice,
 * nothing where the dimension keeps no mantle or the chunk generates without it, read-only, and copied into the chunk
 * as the very platform states the mantle held.
 */
public class PlannedBlocksTest {
    private static final int SECTIONS = 4;
    private static final BlockBox CHUNK = new BlockBox(16, 0, 32, 32, SECTIONS * 16, 48);

    private final InMemoryMantle mantle = new InMemoryMantle(SECTIONS);
    private PlatformBlockState planks;

    @Before
    public void bindPlatform() {
        UsableBlockPlatform.bind();
        planks = UsableBlockPlatform.block("minecraft:oak_planks");
    }

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void placementsBlocksReadInWorldCoordinatesInVisitOrder() {
        plan(20, 10, 33, planks);
        plan(17, 40, 35, planks);
        PlannedBlocks blocks = planned(true, false);
        List<String> visited = new ArrayList<>();

        blocks.visit(CHUNK, (x, y, z, block) -> visited.add(x + "," + y + "," + z + " " + block.key()));

        assertEquals(List.of("17,40,35 minecraft:oak_planks", "20,10,33 minecraft:oak_planks"), visited);
        assertEquals(BlockState.parse("minecraft:oak_planks"), blocks.get(20, 10, 33));
        assertNull(blocks.get(20, 11, 33));
    }

    @Test
    public void aDimensionWithoutAMantleOrAChunkWithoutItPlansNothing() {
        plan(20, 10, 33, planks);

        for (PlannedBlocks blocks : List.of(planned(false, false), planned(true, true))) {
            List<BlockState> visited = new ArrayList<>();
            blocks.visit(CHUNK, (x, y, z, block) -> visited.add(block));
            assertEquals(List.of(), visited);
            assertNull(blocks.get(20, 10, 33));
        }
    }

    @Test
    public void stagesCannotPlanBlocks() {
        MantleLayer<BlockState> blocks = planned(true, false);
        BlockState stone = BlockState.parse("minecraft:stone");

        assertThrows(UnsupportedOperationException.class, () -> blocks.set(20, 10, 33, stone));
        assertThrows(UnsupportedOperationException.class, () -> blocks.remove(20, 10, 33));
        assertThrows(UnsupportedOperationException.class, () -> blocks.clear(CHUNK));
    }

    @Test
    public void aCopiedBlockIsTheMantlesPlatformStateEvenWhereAnotherHasItsKey() {
        PlatformBlockState custom = UsableBlockPlatform.block("minecraft:note_block");
        PlatformBlockState vanilla = UsableBlockPlatform.block("minecraft:note_block");
        plan(20, 10, 33, custom);
        PlatformBlockStates states = new PlatformBlockStates(key -> vanilla);
        PlannedBlocks blocks = PlannedBlocks.of(engine(true, false), CHUNK, mantle::chunk, states);
        Hunk<PlatformBlockState> chunk = Hunk.newArrayHunk(16, SECTIONS * 16, 16);
        BlockStateHunk hunk = new BlockStateHunk(chunk, states);

        blocks.visit(CHUNK, (x, y, z, block) -> hunk.set(x - 16, y, z - 32, block));
        hunk.set(0, 0, 0, BlockState.parse("minecraft:note_block"));

        assertSame("a content plugin's block keeps its identity", custom, chunk.get(4, 10, 1));
        assertSame("a block of the same key a stage writes itself is the platform's", vanilla, chunk.get(0, 0, 0));
    }

    private void plan(int x, int y, int z, PlatformBlockState block) {
        mantle.chunk(x >> 4, z >> 4).getOrCreate(y >> 4).slice(PlatformBlockState.class).set(x & 15, y & 15, z & 15, block);
    }

    private PlannedBlocks planned(boolean useMantle, boolean bypass) {
        return PlannedBlocks.of(engine(useMantle, bypass), CHUNK, mantle::chunk, new PlatformBlockStates(UsableBlockPlatform::block));
    }

    private static Engine engine(boolean useMantle, boolean bypass) {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.isUseMantle()).thenReturn(useMantle);
        EngineGeneration generation = mock(EngineGeneration.class);
        when(generation.bypassesMantleStages()).thenReturn(bypass);
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getGeneration()).thenReturn(generation);
        return engine;
    }
}
