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

package art.arcane.iris.engine.framework.structure;

import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;

/**
 * The declared size of a pack object, which is all a jigsaw graph reads of it: connectors are placed against the size, and
 * its center, which the object's offsets count from, is half the size.
 */
public record ObjectFrame(int width, int height, int depth) {
    public static ObjectFrame of(BlockBox size) {
        return new ObjectFrame(size.maxX() - size.minX(), size.maxY() - size.minY(), size.maxZ() - size.minZ());
    }

    /** The block the object's offsets count from, relative to its corner. */
    public BlockPosition center() {
        return new BlockPosition(width / 2, height / 2, depth / 2);
    }
}
