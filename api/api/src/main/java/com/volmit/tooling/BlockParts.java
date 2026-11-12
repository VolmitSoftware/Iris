package com.volmit.tooling;

import com.volmit.geometry.BlockPosition;

import java.util.Collections;
import java.util.HashMap;
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
        // Not Map.copyOf: positions of a dense scene share hash codes, which its open addressing handles in quadratic
        // time (a million blocks take minutes), while a HashMap falls back to a tree for keys that collide.
        partsByBlock = Collections.unmodifiableMap(new HashMap<>(partsByBlock));
        partsByBlock.forEach((position, part) -> {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(part, "part");
            if (part < 0) {
                throw new IllegalArgumentException("Block " + position + " is in part " + part + "; parts are numbered from 0");
            }
        });
    }

    public int partAt(BlockPosition position) {
        return partsByBlock.getOrDefault(position, NO_PART);
    }
}
