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

import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import com.volmit.mantle.BlockBox;

/**
 * The voids Iris's mantle holds for chunks to take over, IrisApi's {@link com.volmit.mantle.Mantle#isCarved}: where the
 * cave planner marked a cavern in the mantle's cavern slice, whatever the cavern holds (air, fluid, lava).
 */
final class MantleCaverns {
    private static final int CHUNK_SHIFT = 4;
    private static final int SECTION_SHIFT = 4;
    private static final int LOCAL_MASK = 15;

    private final BlockBox bounds;
    private final IrisModuleMantle.Chunks chunks;

    MantleCaverns(BlockBox bounds, IrisModuleMantle.Chunks chunks) {
        this.bounds = bounds;
        this.chunks = chunks;
    }

    boolean isCarved(int x, int y, int z) {
        requireInside(x, y, z);
        return cavernAt(requireChunk(x >> CHUNK_SHIFT, z >> CHUNK_SHIFT), x, y, z);
    }

    private static boolean cavernAt(MantleChunk<Matter> chunk, int x, int y, int z) {
        int section = y >> SECTION_SHIFT;
        if (!chunk.exists(section)) {
            return false;
        }
        Matter matter = chunk.get(section);
        if (matter == null || !matter.hasSlice(MatterCavern.class)) {
            return false;
        }
        return matter.<MatterCavern>getSlice(MatterCavern.class).get(x & LOCAL_MASK, y & LOCAL_MASK, z & LOCAL_MASK) != null;
    }

    private MantleChunk<Matter> requireChunk(int chunkX, int chunkZ) {
        MantleChunk<Matter> chunk = chunks.chunk(chunkX, chunkZ);
        if (chunk == null) {
            throw new IllegalStateException("Mantle chunk " + chunkX + ", " + chunkZ + " is inside " + bounds + " but was not prepared");
        }
        return chunk;
    }

    private void requireInside(int x, int y, int z) {
        if (!bounds.contains(x, y, z)) {
            throw new IndexOutOfBoundsException("Block " + x + ", " + y + ", " + z + " is outside the mantle this invocation prepared, " + bounds);
        }
    }
}
