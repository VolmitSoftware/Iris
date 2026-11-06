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

import art.arcane.volmlib.util.data.VectorMap;
import art.arcane.volmlib.util.math.IrisBlockVector;
import art.arcane.volmlib.util.math.Vector3i;
import com.volmit.block.BlockState;

/**
 * The blocks of one deposit clump, around its centre. Blocks are kept in a {@link VectorMap} of positions relative to the centre,
 * which is how Iris always held a clump, so a clump's blocks come in the same order and a chunk's deposits draw the same random
 * numbers.
 */
final class DepositClump {
    private final int width;
    private final int height;
    private final int depth;
    private final Vector3i center;
    private final VectorMap<BlockState> blocks = new VectorMap<>();

    DepositClump(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.center = new Vector3i(width / 2, height / 2, depth / 2);
    }

    /** How many blocks wide the clump's box is, along X. */
    int width() {
        return width;
    }

    Vector3i center() {
        return center;
    }

    /** The clump's blocks by their position relative to its centre. */
    VectorMap<BlockState> blocks() {
        return blocks;
    }

    /** Puts {@code block} at {@code x}, {@code y}, {@code z} counted from the corner of the clump's box. */
    void setFromCorner(int x, int y, int z, BlockState block) {
        if (x >= width || y >= height || z >= depth) {
            throw new IllegalArgumentException(x + " " + y + " " + z + " exceeds limit of " + width + " " + height + " " + depth);
        }
        blocks.put(new IrisBlockVector(x - center.getX(), y - center.getY(), z - center.getZ()), block);
    }

    /** Puts {@code block} on the centre, which is where a one block clump has its block. */
    void setCentre(BlockState block) {
        blocks.put(new IrisBlockVector(center.getX(), center.getY(), center.getZ()), block);
    }
}
