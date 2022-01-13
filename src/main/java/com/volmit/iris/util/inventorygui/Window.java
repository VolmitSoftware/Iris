/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.inventorygui;

import com.volmit.iris.util.scheduling.Callback;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface Window {
    WindowDecorator getDecorator();

    Window setDecorator(WindowDecorator decorator);

    WindowResolution getResolution();

    Window setResolution(WindowResolution resolution);

    Window clearElements();

    Window close();

    Window open();

    Window callClosed();

    Window updateInventory();

    ItemStack computeItemStack(int viewportSlot);

    int getLayoutRow(int viewportSlottedPosition);

    int getLayoutPosition(int viewportSlottedPosition);

    int getRealLayoutPosition(int viewportSlottedPosition);

    int getRealPosition(int position, int row);

    int getRow(int realPosition);

    int getPosition(int realPosition);

    boolean isVisible();

    Window setVisible(boolean visible);

    int getViewportPosition();

    Window setViewportPosition(int position);

    int getViewportSlots();

    int getMaxViewportPosition();

    Window scroll(int direction);

    int getViewportHeight();

    Window setViewportHeight(int height);

    String getTitle();

    Window setTitle(String title);

    boolean hasElement(int position, int row);

    Window setElement(int position, int row, Element e);

    Element getElement(int position, int row);

    Player getViewer();

    Window reopen();

    Window onClosed(Callback<Window> window);
}
