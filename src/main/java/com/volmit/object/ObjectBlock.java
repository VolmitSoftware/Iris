package com.volmit.object;

import com.volmit.block.BlockState;
import com.volmit.world.BlockPosition;
import java.util.Objects;
import java.util.Optional;

/** One sparse block in object-local coordinates. Absence is distinct from an explicit air block. */
public record ObjectBlock(BlockPosition position, BlockState state, Optional<BlockEntityData> blockEntity) {
    public ObjectBlock {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(blockEntity, "blockEntity");
    }

    public ObjectBlock(BlockPosition position, BlockState state) { this(position, state, Optional.empty()); }
}
