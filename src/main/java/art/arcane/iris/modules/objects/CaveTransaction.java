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

package art.arcane.iris.modules.objects;

import art.arcane.volmlib.util.cache.CacheKey;
import com.volmit.placement.PlacementTerrain;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds back what a cave object writes until all of it is known to lie inside the cave: in the world, no shallower than
 * the burial depth under the surface and under the cave's ceiling at its column.
 */
public final class CaveTransaction extends TerrainTransaction {
    private final int worldHeight;
    private final int anchorY;
    private final int minDepthBelowSurface;
    private final Map<Long, Integer> surfaceHeights = new HashMap<>();
    private final Map<Long, Integer> caveCeilings = new HashMap<>();

    /** @param worldHeight the dimension's height in blocks */
    public CaveTransaction(PlacementTerrain delegate, int worldHeight, int anchorY, int minDepthBelowSurface) {
        super(delegate);
        this.worldHeight = worldHeight;
        this.anchorY = anchorY;
        this.minDepthBelowSurface = Math.max(0, minDepthBelowSurface);
    }

    /** The first height above the anchor that the cave does not reach at the column. */
    public int caveCeiling(int x, int z) {
        return caveCeilings.computeIfAbsent(CacheKey.key(x, z), ignored -> findCaveCeiling(x, z));
    }

    /** The highest height an object buried {@code minDepthBelowSurface} under {@code surfaceY} may reach. */
    public static int maxBuriedY(int worldHeight, int surfaceY, int minDepthBelowSurface) {
        return Math.min(worldHeight - 1, surfaceY - Math.max(0, minDepthBelowSurface));
    }

    @Override
    protected boolean accepts(int x, int y, int z) {
        if (y < 0 || y >= worldHeight) {
            return false;
        }
        if (y > maxBuriedY(worldHeight, surfaceHeight(x, z), minDepthBelowSurface)) {
            return false;
        }
        return y < caveCeiling(x, z);
    }

    private int surfaceHeight(int x, int z) {
        return surfaceHeights.computeIfAbsent(CacheKey.key(x, z), ignored -> delegate().highest(x, z, true));
    }

    private int findCaveCeiling(int x, int z) {
        if (worldHeight <= 0 || anchorY < 0 || anchorY >= worldHeight) {
            return 0;
        }
        if (!delegate().isCarved(x, anchorY, z)) {
            return Math.min(worldHeight, anchorY + 1);
        }
        int scanLimit = Math.min(worldHeight - 1, Math.max(anchorY, surfaceHeight(x, z)));
        for (int y = anchorY + 1; y <= scanLimit; y++) {
            if (!delegate().isCarved(x, y, z)) {
                return y;
            }
        }
        return Math.min(worldHeight, scanLimit + 1);
    }
}
