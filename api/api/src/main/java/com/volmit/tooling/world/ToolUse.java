package com.volmit.tooling.world;

import com.volmit.geometry.BlockFace;
import com.volmit.geometry.BlockPosition;
import java.util.Objects;
import java.util.Optional;

/**
 * A player using their hand in a tool world, handed to {@link ToolWorldListener#used}.
 *
 * @param hand   the hand that was used
 * @param action what the player did
 * @param item   the tool item in that hand; empty when the item is not one
 * @param block  the block clicked; empty for a click in the air
 * @param face   the face of the block clicked; empty for a click in the air
 */
public record ToolUse(Hand hand, Action action, Optional<ToolItem> item, Optional<BlockPosition> block, Optional<BlockFace> face) {
    public enum Hand { MAIN, OFF }

    public enum Action {
        LEFT_CLICK_AIR,
        LEFT_CLICK_BLOCK,
        RIGHT_CLICK_AIR,
        RIGHT_CLICK_BLOCK,
        /** Stepping on a pressure plate, trampling farmland, touching a tripwire. */
        PHYSICAL
    }

    public ToolUse {
        Objects.requireNonNull(hand, "hand");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(face, "face");
    }
}
