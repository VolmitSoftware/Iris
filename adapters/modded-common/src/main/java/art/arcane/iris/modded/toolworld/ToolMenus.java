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

package art.arcane.iris.modded.toolworld;

import art.arcane.iris.modded.ModdedIrisLog;
import com.volmit.tooling.world.MenuButton;
import com.volmit.tooling.world.MenuClick;
import com.volmit.tooling.world.ToolMenu;
import com.volmit.tooling.world.ToolPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The chest menus tools open for players: a server-side chest whose slots are cancelled and routed to the menu's buttons. */
final class ToolMenus {
    private static final int SLOTS_PER_ROW = ToolMenu.SLOTS_PER_ROW;
    private static final int RIGHT_BUTTON = 1;
    private static final List<MenuType<ChestMenu>> TYPES_BY_ROWS = List.of(
            MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
            MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6);

    private final Map<UUID, Screen> screens = new ConcurrentHashMap<>();

    /** Opens {@code menu} for {@code viewer}, replacing the one they had. Runs on the server thread. */
    void open(ServerPlayer holder, ToolPlayer viewer, ToolMenu menu) {
        Screen open = screens.get(holder.getUUID());
        Component title = ToolTexts.component(menu.title());
        if (open != null && open.showsSameFrame(menu, title) && holder.containerMenu == open) {
            open.refill(viewer, menu);
            return;
        }
        close(holder);
        holder.openMenu(new SimpleMenuProvider(
                (id, inventory, player) -> created(id, inventory, holder, viewer, menu, title), title));
    }

    private ChestMenu created(int id, Inventory inventory, ServerPlayer holder, ToolPlayer viewer, ToolMenu menu, Component title) {
        Screen screen = new Screen(id, inventory, menu, title, () -> screens.remove(holder.getUUID()));
        screen.refill(viewer, menu);
        screens.put(holder.getUUID(), screen);
        return screen;
    }

    /** Closes the player's menu; does nothing when none is open. Runs on the server thread. */
    void close(ServerPlayer holder) {
        Screen open = screens.get(holder.getUUID());
        if (open != null && holder.containerMenu == open) {
            holder.closeContainer();
        }
        screens.remove(holder.getUUID(), open);
    }

    void forget(UUID playerId) {
        screens.remove(playerId);
    }

    private static final class Screen extends ChestMenu {
        private final int rows;
        private final Component title;
        private final Container container;
        private final Runnable forgotten;
        private volatile Runnable onClose;
        private volatile Map<Integer, MenuButton> buttons = Map.of();
        private volatile ToolPlayer viewer;

        private Screen(int id, Inventory inventory, ToolMenu menu, Component title, Runnable forgotten) {
            this(id, inventory, menu, title, forgotten, new SimpleContainer(menu.rows() * SLOTS_PER_ROW));
        }

        private Screen(int id, Inventory inventory, ToolMenu menu, Component title, Runnable forgotten, Container container) {
            super(TYPES_BY_ROWS.get(menu.rows() - 1), id, inventory, container, menu.rows());
            this.rows = menu.rows();
            this.title = title;
            this.container = container;
            this.forgotten = forgotten;
            this.onClose = menu.onClose();
        }

        private boolean showsSameFrame(ToolMenu menu, Component otherTitle) {
            return rows == menu.rows() && title.equals(otherTitle);
        }

        /** Puts the buttons of {@code menu} in, telling the menu it replaces that it closed. */
        private void refill(ToolPlayer by, ToolMenu menu) {
            Runnable replaced = onClose;
            onClose = menu.onClose();
            viewer = by;
            Map<Integer, MenuButton> placed = new ConcurrentHashMap<>();
            for (int slot = 0; slot < rows * SLOTS_PER_ROW; slot++) {
                container.setItem(slot, filler());
            }
            for (MenuButton button : menu.buttons()) {
                container.setItem(button.slot(), ToolItems.create(button.icon()));
                placed.put(button.slot(), button);
            }
            buttons = placed;
            if (replaced != onClose) {
                guarded(replaced, "menu close handler");
            }
            broadcastChanges();
        }

        private static ItemStack filler() {
            ItemStack pane = new ItemStack(Items.STAINED_GLASS_PANE.black());
            pane.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
            return pane;
        }

        @Override
        public void clicked(int slot, int button, ContainerInput input, Player player) {
            MenuButton pressed = slot >= 0 ? buttons.get(slot) : null;
            sendAllDataToRemote();
            if (pressed == null || viewer == null) {
                return;
            }
            MenuClick click = clickOf(input, button);
            if (click == null) {
                return;
            }
            guarded(() -> pressed.action().accept(click), "menu button");
        }

        private MenuClick clickOf(ContainerInput input, int button) {
            boolean right = button == RIGHT_BUTTON;
            return switch (input) {
                case PICKUP -> new MenuClick(viewer, right, false);
                case QUICK_MOVE -> new MenuClick(viewer, right, true);
                case CLONE -> new MenuClick(viewer, false, false, true);
                default -> null;
            };
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            forgotten.run();
            guarded(onClose, "menu close handler");
        }

        private static void guarded(Runnable task, String what) {
            try {
                task.run();
            } catch (RuntimeException failure) {
                ModdedIrisLog.error("A tool failed in the {}.", what, failure);
            }
        }
    }
}
