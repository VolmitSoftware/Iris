package com.volmit.tooling;

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import java.util.Objects;
import java.util.Optional;

/**
 * One block of a {@link StructureTemplate} or {@link CapturedStructure}.
 *
 * @param position        where the block is, from the template's corner
 * @param state           the block as the platform stores it; a jigsaw block is the jigsaw block itself
 * @param blockEntity     the block entity data of a block that has some; empty for a jigsaw block, which tools resolve
 *                        through {@code jigsawFinalState}
 * @param jigsawFinalState what a jigsaw block turns into when its piece is placed: the {@code final_state} the block
 *                         names, as the platform writes it; empty for any other block and for a jigsaw block that names
 *                         none
 */
public record TemplateBlock(BlockPosition position, BlockState state, Optional<BlockEntityData> blockEntity,
                            Optional<String> jigsawFinalState) {
    public TemplateBlock {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(blockEntity, "blockEntity");
        Objects.requireNonNull(jigsawFinalState, "jigsawFinalState");
    }

    /** A block with no jigsaw final state. */
    public static TemplateBlock of(BlockPosition position, BlockState state, Optional<BlockEntityData> blockEntity) {
        return new TemplateBlock(position, state, blockEntity, Optional.empty());
    }
}
