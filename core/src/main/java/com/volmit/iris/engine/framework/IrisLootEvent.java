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
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
