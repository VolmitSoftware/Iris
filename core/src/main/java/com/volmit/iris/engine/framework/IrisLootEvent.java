/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.framework;

import com.volmit.iris.engine.object.InventorySlotType;
import com.volmit.iris.engine.object.IrisLootTable;
import com.volmit.iris.util.collection.KList;
import lombok.Getter;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@Getter
public class IrisLootEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Engine engine;
    private final Block block;
    private final InventorySlotType slot;
    private final KList<IrisLootTable> tables;

    public IrisLootEvent(Engine engine, Block block, InventorySlotType slot, KList<IrisLootTable> tables) {
        this.engine = engine;
        this.block = block;
        this.slot = slot;
        this.tables = tables;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
