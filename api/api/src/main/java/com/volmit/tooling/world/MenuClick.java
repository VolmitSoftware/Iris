package com.volmit.tooling.world;

import java.util.Objects;

/**
 * A click on a {@link MenuButton}. A left click has {@code right} false; holding shift is {@code shift}.
 *
 * @param player the player who clicked
 */
public record MenuClick(ToolPlayer player, boolean right, boolean shift) {
    public MenuClick {
        Objects.requireNonNull(player, "player");
    }
}
