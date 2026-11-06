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

package art.arcane.iris.engine.mantle.components;

import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import com.volmit.mantle.CarvedVoid;
import com.volmit.mantle.MantleLayer;

import java.util.BitSet;

/** The fluid voids a carver planned in one chunk, to be drained back to open voids where no solid cup holds them. */
final class CaveFluidSupportPlan {
    private static final int MIN_SUPPORTED_NEIGHBOURS = 4;
    private static final int CHUNK_WIDTH = 16;
    private static final int COLUMN_BITS = 8;
    private static final int COLUMN_MASK = (1 << COLUMN_BITS) - 1;

    private final BitSet fluidPositions = new BitSet();

    void add(int localX, int y, int localZ) {
        int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
        fluidPositions.set((y << COLUMN_BITS) | columnIndex);
    }

    /** Turns every planned fluid void of the chunk at block origin {@code originX}, {@code originZ} that is no longer fluid or has no cup under it into an open void. */
    void resolve(MantleLayer<CarvedVoid> voids, int originX, int originZ, int worldHeight) {
        for (int position = fluidPositions.nextSetBit(0); position >= 0; position = fluidPositions.nextSetBit(position + 1)) {
            int y = position >>> COLUMN_BITS;
            int columnIndex = position & COLUMN_MASK;
            int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
            int localZ = columnIndex & (CHUNK_WIDTH - 1);
            int x = originX + localX;
            int z = originZ + localZ;
            if (voids.get(x, y, z) != CarvedVoid.FLUID || hasCupSupport(voids, originX, originZ, localX, y, localZ, worldHeight)) {
                continue;
            }
            voids.set(x, y, z, CarvedVoid.OPEN);
        }
        fluidPositions.clear();
    }

    private static boolean hasCupSupport(MantleLayer<CarvedVoid> voids, int originX, int originZ, int localX, int y, int localZ, int worldHeight) {
        if (localX <= 0 || localX >= CHUNK_WIDTH - 1 || localZ <= 0 || localZ >= CHUNK_WIDTH - 1
                || y <= 1 || !isSolid(voids, originX, originZ, localX, y - 1, localZ, worldHeight)
                || !isSolid(voids, originX, originZ, localX, y - 2, localZ, worldHeight)) {
            return false;
        }

        int support = 0;
        if (isSolid(voids, originX, originZ, localX + 1, y, localZ, worldHeight)) {
            support++;
        }
        if (isSolid(voids, originX, originZ, localX - 1, y, localZ, worldHeight)) {
            support++;
        }
        if (isSolid(voids, originX, originZ, localX, y, localZ + 1, worldHeight)) {
            support++;
        }
        if (isSolid(voids, originX, originZ, localX, y, localZ - 1, worldHeight)) {
            support++;
        }
        if (isSolid(voids, originX, originZ, localX, y + 1, localZ, worldHeight)) {
            support++;
        }
        return support >= MIN_SUPPORTED_NEIGHBOURS;
    }

    private static boolean isSolid(MantleLayer<CarvedVoid> voids, int originX, int originZ, int localX, int y, int localZ, int worldHeight) {
        if (localX < 0 || localX >= CHUNK_WIDTH || localZ < 0 || localZ >= CHUNK_WIDTH) {
            return false;
        }
        return y >= worldHeight || voids.get(originX + localX, y, originZ + localZ) == null;
    }
}
