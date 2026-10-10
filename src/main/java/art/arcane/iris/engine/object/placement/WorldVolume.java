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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.anchor.AnchorSampling;
import art.arcane.iris.core.modules.IrisPlacement;

/**
 * The world blocks a placed object occupies, inclusive: its transformed bounds around the anchor. A painted
 * object follows the surface column by column, so its volume spans every surface height under it.
 */
record WorldVolume(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    static WorldVolume of(IrisPlacement placement, PreparedPlacement prepared, int anchorY) {
        TransformedBounds bounds = prepared.bounds();
        int minX = placement.x() + bounds.minX();
        int maxX = placement.x() + bounds.maxX();
        int minZ = placement.z() + bounds.minZ();
        int maxZ = placement.z() + bounds.maxZ();
        if (!isPainted(placement, prepared)) {
            return new WorldVolume(minX, maxX, anchorY + bounds.minY(), anchorY + bounds.maxY(), minZ, maxZ);
        }
        int lowestSurface = Integer.MAX_VALUE;
        int highestSurface = Integer.MIN_VALUE;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int surface = prepared.terrain().getHighest(x, z, placement.object().getLoader(), prepared.config().isUnderwater());
                lowestSurface = Math.min(lowestSurface, surface);
                highestSurface = Math.max(highestSurface, surface);
            }
        }
        int paintOffset = Math.floorDiv(placement.object().h, 2);
        return new WorldVolume(minX, maxX, lowestSurface + bounds.minY() + paintOffset,
                highestSurface + bounds.maxY() + paintOffset, minZ, maxZ);
    }

    /** Whether each block of the placement follows the surface of its own column. */
    static boolean isPainted(IrisPlacement placement, PreparedPlacement prepared) {
        return placement.requestedY() < 0 && prepared.mode().anchor() == AnchorSampling.SURFACE;
    }
}
