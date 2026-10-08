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

import art.arcane.iris.spi.PlatformBlockState;
import com.volmit.block.BlockState;
import com.volmit.hunk.Hunk;

import java.util.function.Function;

/**
 * Iris's chunk block hunk seen as IrisApi block states. Iris leaves unset blocks null, which IrisApi
 * forbids, so they read as air. States are converted through their keys and remembered for the
 * invocation, since a chunk repeats few states many times.
 */
final class BlockStateHunk implements Hunk<BlockState> {
    private final art.arcane.iris.util.project.hunk.Hunk<PlatformBlockState> blocks;
    private final PlatformBlockStates states;

    /** {@code platformStates} turns a block state key into the platform's state, such as {@code B::getState}. */
    BlockStateHunk(art.arcane.iris.util.project.hunk.Hunk<PlatformBlockState> blocks, Function<String, PlatformBlockState> platformStates) {
        this.blocks = blocks;
        this.states = new PlatformBlockStates(platformStates);
    }

    @Override
    public int getWidth() {
        return blocks.getWidth();
    }

    @Override
    public int getHeight() {
        return blocks.getHeight();
    }

    @Override
    public int getDepth() {
        return blocks.getDepth();
    }

    @Override
    public BlockState get(int x, int y, int z) {
        requireInside(x, y, z);
        return states.toApi(blocks.get(x, y, z));
    }

    @Override
    public void set(int x, int y, int z, BlockState value) {
        requireInside(x, y, z);
        if (value == null) {
            throw new NullPointerException("Block hunks hold no null states; write minecraft:air for empty space");
        }
        blocks.set(x, y, z, states.toPlatform(value));
    }

    private void requireInside(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= getWidth() || y >= getHeight() || z >= getDepth()) {
            throw new IndexOutOfBoundsException("Block " + x + ", " + y + ", " + z + " is outside the "
                    + getWidth() + "x" + getHeight() + "x" + getDepth() + " chunk hunk");
        }
    }
}
