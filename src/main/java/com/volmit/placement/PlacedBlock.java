package com.volmit.placement;

import com.volmit.block.BlockState;
import com.volmit.object.BlockEntityData;
import com.volmit.world.BlockPosition;
import java.util.Objects;
import java.util.Optional;

/** A proposed or buffered write in world coordinates, including its block entity payload. */
public record PlacedBlock(BlockPosition position, BlockState state, Optional<BlockEntityData> blockEntity) {
    public PlacedBlock {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(blockEntity, "blockEntity");
    }
}
