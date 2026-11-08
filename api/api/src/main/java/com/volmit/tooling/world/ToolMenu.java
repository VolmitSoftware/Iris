package com.volmit.tooling.world;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * A chest-style menu a tool opens for a player with {@link ToolPlayer#openMenu}. Menus are the host's own inventory
 * windows, so they work the same on every platform; a tool that wants another page opens a new menu from a click.
 *
 * @param title   the title as plain text; the host shortens it to what the window can show
 * @param rows    the number of rows, 1 to {@value #MAX_ROWS}
 * @param buttons the slots that do something; the others show a filler. No slot is used twice and every slot is inside
 *                the rows.
 * @param onClose called on the player's thread when the menu closes for any reason: the player closed it, the world
 *                closed, or the tool closed or replaced it. Null stands for nothing to do.
 */
public record ToolMenu(String title, int rows, List<MenuButton> buttons, Runnable onClose) {
    public static final int MAX_ROWS = 6;
    public static final int SLOTS_PER_ROW = 9;

    public ToolMenu {
        Objects.requireNonNull(title, "title");
        if (rows < 1 || rows > MAX_ROWS) {
            throw new IllegalArgumentException("A menu has 1 to " + MAX_ROWS + " rows: " + rows);
        }
        buttons = List.copyOf(buttons);
        var used = new HashSet<Integer>();
        for (var button : buttons) {
            if (button.slot() >= rows * SLOTS_PER_ROW) {
                throw new IllegalArgumentException("Slot " + button.slot() + " is outside a menu of " + rows + " rows");
            }
            if (!used.add(button.slot())) {
                throw new IllegalArgumentException("Slot " + button.slot() + " is used twice");
            }
        }
        if (onClose == null) {
            onClose = () -> { };
        }
    }
}
