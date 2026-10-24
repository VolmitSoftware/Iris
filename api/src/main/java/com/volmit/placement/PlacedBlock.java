package com.volmit.placement;

import com.volmit.block.BlockState;
import com.volmit.object.BlockEntityData;
import com.volmit.world.BlockPosition;
import java.util.Objects;
import java.util.Optional;

/** A proposed or buffered write, including its block entity payload. The position has world X and Z and a
 * Y that is the height above the dimension's bottom (0 = bottom).
 */
public record PlacedBlock(BlockPosition position, BlockState state, Optional<BlockEntityData> blockEntity) {
    public PlacedBlock {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(blockEntity, "blockEntity");
    }
}
