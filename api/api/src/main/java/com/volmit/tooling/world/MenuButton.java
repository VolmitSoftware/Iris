package com.volmit.tooling.world;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * One slot of a {@link ToolMenu}.
 *
 * @param slot   the slot counted along the rows from the top left, 0 to 8 in the first row, 9 to 17 in the second
 * @param icon   what the slot shows
 * @param action called on the player's thread for every click on the slot; it must return quickly
 */
public record MenuButton(int slot, ToolItem icon, Consumer<MenuClick> action) {
    public MenuButton {
        if (slot < 0) {
            throw new IllegalArgumentException("A menu slot is not negative: " + slot);
        }
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(action, "action");
    }
}
