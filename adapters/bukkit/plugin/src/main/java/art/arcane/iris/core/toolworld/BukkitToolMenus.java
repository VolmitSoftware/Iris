/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.toolworld;

import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIPaneDecorator;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.WindowResolution;
import com.volmit.tooling.world.MenuButton;
import com.volmit.tooling.world.MenuClick;
import com.volmit.tooling.world.ToolItem;
import com.volmit.tooling.world.ToolMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The chest menus tools open for players, as VolmLib inventory windows. */
final class BukkitToolMenus {
    private static final int MAX_TITLE_LENGTH = 32;
    private static final int SLOTS_PER_ROW = ToolMenu.SLOTS_PER_ROW;
    private static final int CENTER_SLOT_OFFSET = SLOTS_PER_ROW / 2;

    private final Map<UUID, OpenMenu> windows = new ConcurrentHashMap<>();

    /**
     * The window a player has open and what it was opened with. A menu with the rows and title of the open one fills the
     * same window, as a page of buttons changing under the player's cursor; the close handler is the latest menu's.
     */
    private static final class OpenMenu {
        private final UIWindow window;
        private final int rows;
        private final String title;
        private volatile Runnable onClose;

        private OpenMenu(UIWindow window, int rows, String title, Runnable onClose) {
            this.window = window;
            this.rows = rows;
            this.title = title;
            this.onClose = onClose;
        }

        private boolean showsSameFrame(ToolMenu menu) {
            return rows == menu.rows() && title.equals(shortened(menu.title()));
        }
    }

    /** Opens {@code menu} for {@code viewer}, replacing the one they had. Runs on the viewer's thread. */
    void open(BukkitToolPlayer viewer, ToolMenu menu) {
        Player player = viewer.bukkit();
        OpenMenu open = windows.get(player.getUniqueId());
        if (open != null && open.showsSameFrame(menu)) {
            fill(open, viewer, menu);
            return;
        }
        close(player);
        UUID playerId = player.getUniqueId();
        UIWindow window = new UIWindow(BukkitPlatform.volmitPlugin(), player);
        OpenMenu created = new OpenMenu(window, menu.rows(), shortened(menu.title()), menu.onClose());
        window.setResolution(WindowResolution.W9_H6);
        window.setViewportHeight(menu.rows());
        window.setDecorator(new UIPaneDecorator(Material.BLACK_STAINED_GLASS_PANE));
        window.setTitle(created.title);
        window.onClosed(closed -> {
            windows.remove(playerId, created);
            runGuarded(created.onClose, "menu close handler of " + player.getName());
        });
        window.batch(() -> addButtons(window, viewer, menu));
        windows.put(playerId, created);
        window.open();
    }

    /** Puts the buttons of {@code menu} in the window {@code open} holds, telling the menu it replaces that it closed. */
    private void fill(OpenMenu open, BukkitToolPlayer viewer, ToolMenu menu) {
        Runnable replaced = open.onClose;
        open.onClose = menu.onClose();
        open.window.batch(() -> {
            open.window.clearElements();
            addButtons(open.window, viewer, menu);
        });
        runGuarded(replaced, "menu close handler of " + viewer.name());
    }

    private static void addButtons(UIWindow window, BukkitToolPlayer viewer, ToolMenu menu) {
        for (MenuButton button : menu.buttons()) {
            window.setElement(positionOf(button.slot()), button.slot() / SLOTS_PER_ROW, element(viewer, button));
        }
    }

    /** Closes the player's menu; does nothing when none is open. Runs on the player's thread. */
    void close(Player player) {
        OpenMenu open = windows.remove(player.getUniqueId());
        if (open != null) {
            open.window.close();
        }
    }

    void forget(UUID playerId) {
        windows.remove(playerId);
    }

    private static UIElement element(BukkitToolPlayer viewer, MenuButton button) {
        ToolItem icon = button.icon();
        Material material = Material.matchMaterial(icon.item().toString());
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("The platform has no item " + icon.item());
        }
        UIElement element = new UIElement("slot-" + button.slot())
                .setMaterial(new MaterialBlock(material))
                .setName(icon.name())
                .setEnchanted(icon.glowing());
        icon.lore().forEach(element::addLore);
        element.onLeftClick(clicked -> click(viewer, button, false, false));
        element.onRightClick(clicked -> click(viewer, button, true, false));
        element.onShiftLeftClick(clicked -> click(viewer, button, false, true));
        element.onShiftRightClick(clicked -> click(viewer, button, true, true));
        element.onMiddleClick(clicked -> runGuarded(
                () -> button.action().accept(new MenuClick(viewer, false, false, true)),
                "menu button of " + viewer.name()));
        return element;
    }

    private static void click(BukkitToolPlayer viewer, MenuButton button, boolean right, boolean shift) {
        runGuarded(() -> button.action().accept(new MenuClick(viewer, right, shift)), "menu button of " + viewer.name());
    }

    /** The window counts positions from its middle column, left of it negative; a slot counts from the left. */
    static int positionOf(int slot) {
        return slot % SLOTS_PER_ROW - CENTER_SLOT_OFFSET;
    }

    static String shortened(String title) {
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }

    private static void runGuarded(Runnable task, String what) {
        try {
            task.run();
        } catch (RuntimeException failure) {
            IrisLogging.reportError("A tool failed in the " + what + ".", failure);
        }
    }
}
