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

/** iris:matter through IrisApi alone: the mantle's planned blocks go into the chunk, below the dimension above. */
public class MatterModuleTest {
    private static final int ORIGIN_X = 16;
    private static final int ORIGIN_Z = 32;
    private static final int HEIGHT = 128;
    private static final int UPPER_DIMENSION = 100;
    private static final BlockState PLANNED = block("minecraft:oak_planks");
    private static final BlockState GROUND = block("minecraft:dirt");

    @Test
    public void plannedBlocksAreCopiedIntoTheChunk() {
        ApiChunk chunk = new ApiChunk(ORIGIN_X, ORIGIN_Z, HEIGHT).set(3, 50, 4, GROUND)
                .plan(ORIGIN_X + 3, 50, ORIGIN_Z + 4, PLANNED).plan(ORIGIN_X + 3, 120, ORIGIN_Z + 4, PLANNED)
                .plan(ORIGIN_X + 3, 51, ORIGIN_Z + 4, AIR);

        chunk.generate(new MatterModule());

        assertEquals(PLANNED, chunk.get(3, 50, 4));
        assertEquals(PLANNED, chunk.get(3, 120, 4));
        assertEquals(AIR, chunk.get(3, 51, 4));
    }

    @Test
    public void blocksAtOrAboveWhereTheUpperDimensionBeginsAreLeftOut() {
        ApiChunk chunk = new ApiChunk(ORIGIN_X, ORIGIN_Z, HEIGHT).upperDimension((x, z) -> UPPER_DIMENSION)
                .plan(ORIGIN_X + 3, UPPER_DIMENSION - 1, ORIGIN_Z + 4, PLANNED)
                .plan(ORIGIN_X + 3, UPPER_DIMENSION, ORIGIN_Z + 4, PLANNED);

        chunk.generate(new MatterModule());

        assertEquals(PLANNED, chunk.get(3, UPPER_DIMENSION - 1, 4));
        assertEquals(AIR, chunk.get(3, UPPER_DIMENSION, 4));
    }
}
