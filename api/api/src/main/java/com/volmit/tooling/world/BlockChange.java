package com.volmit.tooling.world;

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Something that is about to change blocks in a tool world, handed to {@link ToolWorldListener#blockChanging}.
 *
 * @param cause     what is changing them
 * @param positions the blocks a protected area is checked against: the block a player breaks or places, the blocks an
 *                  explosion would destroy one at a time, the blocks a piston moves with the blocks they leave and
 *                  enter. Empty only when {@code changed} is not, such as a piston that moves nothing.
 * @param changed   the blocks whose content changes, which a tool may want to save; the same as {@code positions}
 *                  unless the cause touches other blocks than it is checked against, as a piston does with its own block
 * @param player    the player behind it, when there is one
 * @param placed    the state being placed, for {@link Cause#PLACE} and {@link Cause#BUCKET}; empty when it is not known
 */
public record BlockChange(
        Cause cause,
        List<BlockPosition> positions,
        List<BlockPosition> changed,
        Optional<ToolPlayer> player,
        Optional<BlockState> placed
) {
    /** What changes a block. */
    public enum Cause {
        /** A player places a block. */
        PLACE,
        /** A player breaks a block. */
        BREAK,
        /** A player fills or empties a bucket. */
        BUCKET,
        /** A block forms, fades or spreads by itself, such as ice, snow, grass or coral. */
        PHYSICS,
        /** Water or lava flows. */
        FLUID,
        /** A plant or a sapling grows. */
        GROWTH,
        /** A block burns. */
        FIRE,
        /** Redstone power changes; cancelling keeps the old power. */
        REDSTONE,
        /** A piston pushes or pulls blocks. */
        PISTON,
        /** An explosion destroys blocks. */
        EXPLOSION,
        /** A mob or a falling block changes a block. */
        ENTITY,
        /** A player or a hopper changes what a container block holds. */
        CONTAINER,
        /** A furnace, a brewing stand, a crafter or a dispenser works. */
        MACHINE
    }

    public BlockChange {
        Objects.requireNonNull(cause, "cause");
        positions = List.copyOf(positions);
        changed = List.copyOf(changed);
        if (positions.isEmpty() && changed.isEmpty()) {
            throw new IllegalArgumentException("A block change names at least one block");
        }
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(placed, "placed");
    }

    /** A change that is checked against the blocks it changes. */
    public BlockChange(Cause cause, List<BlockPosition> positions, Optional<ToolPlayer> player, Optional<BlockState> placed) {
        this(cause, positions, positions, player, placed);
    }
}
