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

import art.arcane.iris.engine.object.noise.IrisGeneratorStyle;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.IrisObjectTranslate;
import com.volmit.mantle.BlockBox;
import com.volmit.object.GeneratedObject;

/**
 * The columns one object placement may read and write: every column the object reaches in any rotation
 * around its anchor, moved by its translation and surface warp, widened by one block for neighbor reads and
 * by the placement pipeline's reach. The object reaches as far as its blocks or its declared size, whichever
 * is larger, since boring and terrain bends cover the declared size. Heights are not bounded; the placer
 * keeps writes inside its world.
 */
final class PlacementArea {
    private static final int NEIGHBOR_READ_BLOCKS = 1;

    private PlacementArea() {
    }

    static BlockBox around(IrisObject object, GeneratedObject geometry, IrisObjectPlacement config, int x, int z,
                           int pipelineReachBlocks) {
        int objectRadius = Math.max(rotationRadius(geometry.bounds()), rotationRadius(declaredBox(object)));
        int reach = objectRadius + translationLength(config.getTranslate())
                + warpMargin(config.getWarp()) + NEIGHBOR_READ_BLOCKS + pipelineReachBlocks;
        return new BlockBox(x - reach, Integer.MIN_VALUE, z - reach, x + reach + 1, Integer.MAX_VALUE, z + reach + 1);
    }

    /** The object's declared size around its center, which its block offsets count from. */
    private static BlockBox declaredBox(IrisObject object) {
        int centerX = object.getCenter().getBlockX();
        int centerY = object.getCenter().getBlockY();
        int centerZ = object.getCenter().getBlockZ();
        return new BlockBox(-centerX, -centerY, -centerZ,
                Math.max(1, object.getW()) - centerX, Math.max(1, object.getH()) - centerY, Math.max(1, object.getD()) - centerZ);
    }

    /** The farthest any block of {@code bounds} lies from the origin, which rotations turn around. */
    private static int rotationRadius(BlockBox bounds) {
        long x = farthest(bounds.minX(), bounds.maxX());
        long y = farthest(bounds.minY(), bounds.maxY());
        long z = farthest(bounds.minZ(), bounds.maxZ());
        return (int) Math.ceil(Math.sqrt(x * x + y * y + z * z));
    }

    private static long farthest(int min, int maxExclusive) {
        return Math.max(Math.abs((long) min), Math.abs((long) maxExclusive - 1));
    }

    private static int translationLength(IrisObjectTranslate translate) {
        long x = translate.getX();
        long y = translate.getY();
        long z = translate.getZ();
        return (int) Math.ceil(Math.sqrt(x * x + y * y + z * z));
    }

    private static int warpMargin(IrisGeneratorStyle warp) {
        return warp.isFlat() ? 0 : (int) Math.ceil(Math.abs(warp.getMultiplier()) / 2D);
    }
}
