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

package art.arcane.iris.engine.framework.structure.fit;

import com.volmit.mantle.BlockBox;

/** A box of world blocks, every bound inclusive and Y in world heights, the way the platform's structures measure. */
record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    /** {@code box}, an API box above the dimension's bottom with exclusive maxima, in world heights. */
    static Box of(BlockBox box, int bottomY) {
        return new Box(box.minX(), box.minY() + bottomY, box.minZ(),
                box.maxX() - 1, box.maxY() - 1 + bottomY, box.maxZ() - 1);
    }

    /** This box as an API box above the dimension's bottom. */
    BlockBox toApi(int bottomY) {
        return new BlockBox(minX, minY - bottomY, minZ, maxX + 1, maxY + 1 - bottomY, maxZ + 1);
    }

    int xSpan() {
        return maxX - minX + 1;
    }

    int ySpan() {
        return maxY - minY + 1;
    }

    int zSpan() {
        return maxZ - minZ + 1;
    }

    boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    boolean intersects(Box other) {
        return maxX >= other.minX && minX <= other.maxX && maxY >= other.minY && minY <= other.maxY
                && maxZ >= other.minZ && minZ <= other.maxZ;
    }

    boolean intersectsHorizontally(Box other) {
        return maxX >= other.minX && minX <= other.maxX && maxZ >= other.minZ && minZ <= other.maxZ;
    }

    /** This box grown by {@code blocks} sideways, keeping its heights. */
    Box widened(int blocks) {
        return new Box(minX - blocks, minY, minZ - blocks, maxX + blocks, maxY, maxZ + blocks);
    }

    Box encapsulating(Box other) {
        return new Box(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
    }

    /** The column index of a block of this box's columns, rows along X. */
    int columnIndex(int x, int z) {
        return (z - minZ) * xSpan() + x - minX;
    }
}
