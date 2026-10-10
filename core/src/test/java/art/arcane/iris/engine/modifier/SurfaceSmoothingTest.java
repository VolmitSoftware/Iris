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

import com.volmit.block.BlockState;
import org.junit.Test;

import static art.arcane.iris.engine.modifier.ApiChunk.AIR;
import static art.arcane.iris.engine.modifier.ApiChunk.block;
import static org.junit.Assert.assertEquals;

/**
 * iris:post through IrisApi alone: a nib floating above its neighbours goes, a flat surface stays, a step gets a slab,
 * a steep face gets its wall, a stair under water is waterlogged and a plant that lost its ground goes.
 */
public class SurfaceSmoothingTest {
    private static final int X = 5;
    private static final int Z = 5;
    private static final int GROUND = 4;
    private static final int HEIGHT = 32;
    private static final BlockState STONE = block("minecraft:stone");

    @Test
    public void aNibFloatingAboveItsFourNeighboursIsRemoved() {
        int nib = 10;
        ApiChunk chunk = ground((x, z) -> GROUND).surface(column(nib)).set(X, nib, Z, STONE);

        chunk.generate(new PostModule());

        assertEquals(AIR, chunk.get(X, nib, Z));
    }

    @Test
    public void aFlatSurfaceIsLeftAlone() {
        ApiChunk chunk = ground((x, z) -> GROUND);

        chunk.generate(new PostModule());

        assertEquals(STONE, chunk.get(X, GROUND, Z));
        assertEquals(AIR, chunk.get(X, GROUND + 1, Z));
    }

    @Test
    public void aStepUpGetsASlabWhereTheSlopeAllowsIt() {
        BlockState slab = block("minecraft:stone_slab[type=bottom]");
        ApiChunk chunk = ground((x, z) -> x > X ? GROUND + 1 : GROUND);
        chunk.pending().slabs(slab, 1);

        chunk.generate(new PostModule());

        assertEquals(slab, chunk.get(X, GROUND + 1, Z));
    }

    @Test
    public void slabsWaitForASlopeTheirLayerAllows() {
        ApiChunk chunk = ground((x, z) -> x > X ? GROUND + 1 : GROUND).slope(2);
        chunk.pending().slabs(block("minecraft:stone_slab[type=bottom]"), 1);

        chunk.generate(new PostModule());

        assertEquals(AIR, chunk.get(X, GROUND + 1, Z));
    }

    @Test
    public void aSteepFaceIsPaintedWithItsWall() {
        BlockState wall = block("minecraft:cobblestone");
        int cliff = GROUND + 4;
        ApiChunk chunk = ground((x, z) -> x <= X ? cliff : GROUND);
        chunk.pending().walls(wall);

        chunk.generate(new PostModule());

        assertEquals(wall, chunk.get(X, cliff, Z));
        assertEquals(wall, chunk.get(X, cliff - 1, Z));
    }

    @Test
    public void aStairUnderTheFluidHeightNextToWaterIsWaterlogged() {
        BlockState stair = block("minecraft:oak_stairs[facing=east,waterlogged=false]");
        ApiChunk chunk = ground((x, z) -> GROUND).fluidHeight(GROUND + 1)
                .set(X, GROUND, Z, stair).set(X + 1, GROUND, Z, block("minecraft:water"));

        chunk.generate(new PostModule());

        assertEquals(stair.withProperty("waterlogged", "true"), chunk.get(X, GROUND, Z));
    }

    @Test
    public void foliageThatLostItsGroundGoesButDecorationsStay() {
        BlockState poppy = block("minecraft:poppy");
        ApiChunk chunk = ground((x, z) -> GROUND).set(X, GROUND + 1, Z, block("minecraft:fern")).set(X + 2, GROUND + 1, Z, poppy);

        chunk.generate(new PostModule());

        assertEquals("stone holds no fern", AIR, chunk.get(X, GROUND + 1, Z));
        assertEquals("perfection, not post, removes decorations", poppy, chunk.get(X + 2, GROUND + 1, Z));
    }

    /** A chunk of stone up to {@code heights}, the terrain's surface. */
    private static ApiChunk ground(java.util.function.IntBinaryOperator heights) {
        ApiChunk chunk = new ApiChunk(0, 0, HEIGHT).surface(heights);
        for (int x = 0; x < ApiChunk.WIDTH; x++) {
            for (int z = 0; z < ApiChunk.WIDTH; z++) {
                for (int y = 0; y <= heights.applyAsInt(x, z); y++) {
                    chunk.set(x, y, z, STONE);
                }
            }
        }
        return chunk;
    }

    /** The terrain's height: the ground everywhere but the nib column, which the terrain says stands {@code nib} high. */
    private static java.util.function.IntBinaryOperator column(int nib) {
        return (x, z) -> x == X && z == Z ? nib : GROUND;
    }
}
