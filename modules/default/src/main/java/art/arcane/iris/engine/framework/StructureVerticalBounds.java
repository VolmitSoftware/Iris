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

package art.arcane.iris.engine.framework;

public final class StructureVerticalBounds {
    private StructureVerticalBounds() {
    }

    public static int clampOffset(
            int structureMinY,
            int structureMaxY,
            int requestedOffset,
            int worldMinY,
            int worldMaxYExclusive
    ) {
        if (structureMaxY < structureMinY) {
            throw new IllegalArgumentException("Structure maximum Y cannot be below its minimum Y");
        }
        if (worldMaxYExclusive <= worldMinY) {
            throw new IllegalArgumentException("World maximum Y must be above its minimum Y");
        }

        long structureHeight = (long) structureMaxY - structureMinY + 1L;
        long worldHeight = (long) worldMaxYExclusive - worldMinY;
        if (structureHeight > worldHeight) {
            throw new IllegalArgumentException("Structure height " + structureHeight
                    + " exceeds writable world height " + worldHeight);
        }

        long minimumOffset = (long) worldMinY - structureMinY;
        long maximumOffset = (long) worldMaxYExclusive - 1L - structureMaxY;
        long clamped = Math.max(minimumOffset, Math.min(maximumOffset, (long) requestedOffset));
        return Math.toIntExact(clamped);
    }
}
