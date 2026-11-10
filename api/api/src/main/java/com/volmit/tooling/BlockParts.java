package com.volmit.tooling;

import com.volmit.geometry.BlockPosition;

import java.util.Map;
import java.util.Objects;

/**
 * Which part of an entry placed each block of one scene, as {@link SceneView#parts} reports it. A part is numbered the
 * way {@link GraphNode#part()} numbers the node that draws it, so the studio can light up the blocks of a node and find
 * the node of a block.
 *
 * @param partsByBlock the part of each block that has one, by the block's position in the scene
 */
public record BlockParts(Map<BlockPosition, Integer> partsByBlock) {
    /** What {@link #partAt} answers for a block no part placed, such as a decoration. */
    public static final int NO_PART = -1;

    public BlockParts {
        Objects.requireNonNull(partsByBlock, "partsByBlock");
        partsByBlock = Map.copyOf(partsByBlock);
        partsByBlock.forEach((position, part) -> {
            if (part < 0) {
                throw new IllegalArgumentException("Block " + position + " is in part " + part + "; parts are numbered from 0");
            }
        });
    }

    public int partAt(BlockPosition position) {
        return partsByBlock.getOrDefault(position, NO_PART);
    }
}
