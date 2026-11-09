package com.volmit.tooling;

import com.volmit.geometry.BlockPosition;
import java.util.List;
import java.util.Objects;

/**
 * A structure the platform generated in a scratch area and read back.
 *
 * @param size   the width, height and depth of the box the structure filled
 * @param blocks every block in that box that is not plain air, with positions from the box's corner
 */
public record CapturedStructure(BlockPosition size, List<TemplateBlock> blocks) {
    public CapturedStructure {
        Objects.requireNonNull(size, "size");
        blocks = List.copyOf(blocks);
    }
}
