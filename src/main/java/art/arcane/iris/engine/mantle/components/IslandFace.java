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

import art.arcane.iris.builtin.floating.IslandAnchor;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandSample;
import art.arcane.iris.engine.object.terrain.floating.ResolvedFloatingChild;

/**
 * The top or bottom face of one floating child's island, at the height an object is anchored: what Iris's placer asks
 * about the island while it places the object, and which blocks the object may write. An object may overhang its anchor
 * only where the same child's face lies near the anchor height within {@value #OVERHANG_RADIUS} blocks.
 */
final class IslandFace implements IslandAnchor {
    private static final int OVERHANG_RADIUS = 2;
    private static final int OVERHANG_HEIGHT_TOLERANCE = 4;

    private final SampleProvider samples;
    private final ResolvedFloatingChild entry;
    private final int anchorY;
    private final Side side;
    private final int worldHeight;

    private IslandFace(SampleProvider samples, ResolvedFloatingChild entry, int anchorY, Side side, int worldHeight) {
        this.samples = samples;
        this.entry = entry;
        this.anchorY = anchorY;
        this.side = side;
        this.worldHeight = worldHeight;
    }

    static IslandFace top(SampleProvider samples, ResolvedFloatingChild entry, int anchorY, int worldHeight) {
        return new IslandFace(samples, entry, anchorY, Side.TOP, worldHeight);
    }

    static IslandFace bottom(SampleProvider samples, ResolvedFloatingChild entry, int anchorY, int worldHeight) {
        return new IslandFace(samples, entry, anchorY, Side.BOTTOM, worldHeight);
    }

    @Override
    public int faceY(int x, int z) {
        FloatingIslandSample sample = samples.sample(x, z);
        if (sample == null) {
            return anchorY;
        }
        if (side == Side.TOP) {
            return sample.topY();
        }
        int bottomY = sample.bottomY();
        return bottomY < 0 ? anchorY : bottomY;
    }

    @Override
    public boolean hasIsland(int x, int z) {
        return samples.sample(x, z) != null;
    }

    @Override
    public boolean islandSolid(int x, int y, int z) {
        FloatingIslandSample sample = samples.sample(x, z);
        if (sample == null) {
            return false;
        }
        int index = y - sample.islandBaseY;
        return index >= 0 && index < sample.solidMask.length && sample.solidMask[index];
    }

    @Override
    public boolean writes(int x, int y, int z) {
        return !skipsAirColumn(x, y, z);
    }

    /** Whether {@code sample} is this face's child's island, by the face this anchor is on. */
    static boolean matchesAnchor(FloatingIslandSample sample, ResolvedFloatingChild entry, Side side) {
        if (sample == null) {
            return false;
        }
        return side == Side.TOP ? sample.entry == entry : sample.bottomEntry() == entry;
    }

    private boolean skipsAirColumn(int x, int y, int z) {
        if (y < 0 || y >= worldHeight) {
            return true;
        }

        FloatingIslandSample sample = samples.sample(x, z);
        if (matchesAnchor(sample, entry, side) && isNearAnchorHeight(sample)) {
            return side == Side.BOTTOM && y >= anchorY;
        }
        if (side == Side.TOP && y <= anchorY) {
            return true;
        }
        if (side == Side.BOTTOM && y >= anchorY) {
            return true;
        }
        return !hasNearbySupport(x, z);
    }

    private boolean hasNearbySupport(int x, int z) {
        for (int dz = -OVERHANG_RADIUS; dz <= OVERHANG_RADIUS; dz++) {
            for (int dx = -OVERHANG_RADIUS; dx <= OVERHANG_RADIUS; dx++) {
                FloatingIslandSample sample = samples.sample(x + dx, z + dz);
                if (matchesAnchor(sample, entry, side) && isNearAnchorHeight(sample)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNearAnchorHeight(FloatingIslandSample sample) {
        int faceY = side == Side.TOP ? sample.topY() : sample.bottomY();
        return faceY >= 0 && Math.abs(faceY - anchorY) <= OVERHANG_HEIGHT_TOLERANCE;
    }

    enum Side {
        TOP,
        BOTTOM
    }

    @FunctionalInterface
    interface SampleProvider {
        FloatingIslandSample sample(int x, int z);
    }
}
