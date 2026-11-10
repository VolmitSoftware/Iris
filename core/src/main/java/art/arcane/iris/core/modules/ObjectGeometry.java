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

import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.IrisBlockVector;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An Iris object as IrisApi geometry. Iris keys an object's blocks by their offset from its center, so
 * those offsets are the local positions and the anchor is the origin. Tile data travels as block entities.
 */
public final class ObjectGeometry {
    private static final BlockPosition CENTER = new BlockPosition(0, 0, 0);
    private static final BlockState UNREADABLE_BLOCK = BlockState.parse("minecraft:cave_air");
    private static final BlockBox EMPTY = new BlockBox(0, 0, 0, 0, 0, 0);

    private ObjectGeometry() {
    }

    /** Reads the object under its read lock. A block Iris cannot read becomes cave air, as Iris places it. */
    public static GeneratedObject of(IrisObject object) {
        object.readLock.lock();
        try {
            List<ObjectBlock> blocks = blocksOf(object);
            return new GeneratedObject(boundsOf(blocks), CENTER, blocks, List.of());
        } finally {
            object.readLock.unlock();
        }
    }

    /**
     * Like {@link #of}, but the bounds are the size the object file declares around its center, not the box of its blocks,
     * so the file can be written again at the size it had.
     */
    public static GeneratedObject ofDeclared(IrisObject object) {
        object.readLock.lock();
        try {
            int centerX = object.w / 2;
            int centerY = object.h / 2;
            int centerZ = object.d / 2;
            BlockBox declared = new BlockBox(-centerX, -centerY, -centerZ,
                    object.w - centerX, object.h - centerY, object.d - centerZ);
            return new GeneratedObject(declared, CENTER, blocksOf(object), List.of());
        } finally {
            object.readLock.unlock();
        }
    }

    private static List<ObjectBlock> blocksOf(IrisObject object) {
        Map<BlockPosition, ObjectBlock> blocks = new LinkedHashMap<>();
        for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : object.blocks) {
            BlockPosition position = positionOf(entry.getKey());
            TileData tile = object.states.get(entry.getKey());
            blocks.put(position, new ObjectBlock(position, stateOf(entry.getValue()), Optional.ofNullable(tile).map(BlockEntities::of)));
        }
        return List.copyOf(blocks.values());
    }

    private static BlockPosition positionOf(IrisBlockVector offset) {
        return new BlockPosition((int) Math.round(offset.getX()), (int) Math.round(offset.getY()), (int) Math.round(offset.getZ()));
    }

    private static BlockState stateOf(PlatformBlockState state) {
        return state == null ? UNREADABLE_BLOCK : BlockState.parse(PlatformBlockStates.namespaced(state.key()));
    }

    private static BlockBox boundsOf(List<ObjectBlock> blocks) {
        if (blocks.isEmpty()) {
            return EMPTY;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (ObjectBlock block : blocks) {
            BlockPosition position = block.position();
            minX = Math.min(minX, position.x());
            minY = Math.min(minY, position.y());
            minZ = Math.min(minZ, position.z());
            maxX = Math.max(maxX, position.x());
            maxY = Math.max(maxY, position.y());
            maxZ = Math.max(maxZ, position.z());
        }
        return new BlockBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }
}
