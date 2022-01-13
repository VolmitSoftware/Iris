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

import org.bukkit.event.inventory.InventoryType;

public enum WindowResolution {
    W9_H6(9, 6, InventoryType.CHEST),
    W5_H1(5, 1, InventoryType.HOPPER),
    W3_H3(3, 3, InventoryType.DROPPER);

    private final int width;
    private final int maxHeight;
    private final InventoryType type;

    WindowResolution(int w, int h, InventoryType type) {
        this.width = w;
        this.maxHeight = h;
        this.type = type;
    }

    public int getMaxWidthOffset() {
        return (getWidth() - 1) / 2;
    }

    public int getWidth() {
        return width;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public InventoryType getType() {
        return type;
    }
}
