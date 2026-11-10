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

package art.arcane.iris.core.structure.export;

import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.object.ObjectBlock;
import com.volmit.object.PlaceableObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What an exporter reads of a pack object: its declared size and its blocks and block entities at positions counted from
 * the object's corner, with the blocks only the platform can place or that need a block entity noted.
 *
 * @param customBlocks        where the stored block is a custom-content block
 * @param blocksNeedingEntity where the stored block is of a kind that carries a block entity
 */
public record ObjectContents(int width, int height, int depth, Map<BlockPosition, BlockState> blocks,
                             Map<BlockPosition, BlockEntityData> blockEntities, Set<BlockPosition> customBlocks,
                             Set<BlockPosition> blocksNeedingEntity) {
    public ObjectContents {
        blocks = Map.copyOf(blocks);
        blockEntities = Map.copyOf(blockEntities);
        customBlocks = Set.copyOf(customBlocks);
        blocksNeedingEntity = Set.copyOf(blocksNeedingEntity);
    }

    /** An object with these blocks and nothing special about any of them. */
    public static ObjectContents plain(int width, int height, int depth, Map<BlockPosition, BlockState> blocks,
                                       Map<BlockPosition, BlockEntityData> blockEntities) {
        return new ObjectContents(width, height, depth, blocks, blockEntities, Set.of(), Set.of());
    }

    /** {@code object} as its blocks lie in its declared box; {@code registry} says which are custom or hold block entities. */
    public static ObjectContents of(PlaceableObject object, BlockRegistry registry) {
        BlockPosition center = object.center();
        Map<BlockPosition, BlockState> blocks = new LinkedHashMap<>();
        Map<BlockPosition, BlockEntityData> entities = new LinkedHashMap<>();
        Set<BlockPosition> custom = new LinkedHashSet<>();
        Set<BlockPosition> needingEntity = new LinkedHashSet<>();
        for (ObjectBlock block : object.geometry().blocks()) {
            BlockPosition position = block.position().add(center);
            blocks.put(position, block.state());
            block.blockEntity().ifPresent(entity -> entities.put(position, entity));
            if (registry.hasTrait(block.state(), BlockTrait.CUSTOM)) {
                custom.add(position);
            }
            if (registry.hasTrait(block.state(), BlockTrait.STORAGE)) {
                needingEntity.add(position);
            }
        }
        return new ObjectContents(object.width(), object.height(), object.depth(), blocks, entities, custom, needingEntity);
    }

    /** The block the object's offsets count from, relative to its corner. */
    public BlockPosition center() {
        return new BlockPosition(width / 2, height / 2, depth / 2);
    }
}
