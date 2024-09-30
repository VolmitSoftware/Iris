package com.volmit.iris.engine.framework;

import com.volmit.iris.engine.object.InventorySlotType;
import com.volmit.iris.engine.object.IrisLootTable;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.LootGenerateEvent;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
public class IrisLootEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Engine engine;
    private final Block block;
    private final InventorySlotType slot;
    private final KList<IrisLootTable> tables;
    private final Mode mode; // New field to represent the mode

    // Define the different modes for the event
    public enum Mode {
        NORMAL,
        BUKKIT_LOOT
    }

    /**
     * Constructor for IrisLootEvent with mode selection.
     *
     * @param engine The engine instance.
     * @param block The block associated with the event.
     * @param slot The inventory slot type.
     * @param tables The list of IrisLootTables. (mutable*)
     */
    public IrisLootEvent(Engine engine, Block block, InventorySlotType slot, KList<IrisLootTable> tables) {
        this.engine = engine;
        this.block = block;
        this.slot = slot;
        this.tables = tables;
        this.mode = Mode.BUKKIT_LOOT;

        if (this.mode == Mode.BUKKIT_LOOT) {
            triggerBukkitLootEvent();
        }
    }

    /**
     * Triggers the corresponding Bukkit loot event.
     * This method integrates your custom IrisLootTables with Bukkit's LootGenerateEvent,
     * allowing other plugins to modify or cancel the loot generation.
     */
    private Inventory triggerBukkitLootEvent() {
        if (block.getState() instanceof InventoryHolder holder) {
            Inventory inventory = holder.getInventory();
            inventory.clear();

            List<ItemStack> loot = new ArrayList<>();
            RNG rng = new RNG();
            int x = block.getX(), y = block.getY(), z = block.getZ();

            for (IrisLootTable table : tables)
                loot.addAll(table.getLoot(false, rng, slot, block.getWorld(), x, y, z));

            LootContext context = new LootContext.Builder(block.getLocation()).build();

            LootTable lootTable = LootTables.EMPTY.getLootTable(); // todo: Correct structure

            LootGenerateEvent bukkitEvent = new LootGenerateEvent(engine.getWorld().realWorld(), null, holder, lootTable, context, loot, true); // todo: Use the iris loottable
            Bukkit.getServer().getPluginManager().callEvent(bukkitEvent);

            if (!bukkitEvent.isCancelled())
                inventory.setContents(bukkitEvent.getLoot().toArray(new ItemStack[0]));
            return inventory;
        }
        return null;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    /**
     * Required method to get the HandlerList for this event.
     *
     * @return The HandlerList.
     */
    public static HandlerList getHandlerList() {
        return handlers;
    }
}