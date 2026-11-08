package com.volmit.tooling.world;

import java.util.Objects;

/**
 * A click on a {@link MenuButton}. A left click has {@code right} false; holding shift is {@code shift}; the middle
 * mouse button is {@code middle}, with {@code right} and {@code shift} false.
 *
 * @param player the player who clicked
 */
public record MenuClick(ToolPlayer player, boolean right, boolean shift, boolean middle) {
    public MenuClick {
        Objects.requireNonNull(player, "player");
    }

    /** A left or right click, with or without shift. */
    public MenuClick(ToolPlayer player, boolean right, boolean shift) {
        this(player, right, shift, false);
    }
}
