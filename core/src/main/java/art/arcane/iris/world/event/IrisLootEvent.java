package art.arcane.iris.world.event;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.loot.InventorySlotType;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.world.task.J;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Random;

@Getter
public class IrisLootEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private static final LootTable EMPTY = new LootTable() {
        @NotNull
        @Override
        public NamespacedKey getKey() {
            return new NamespacedKey(BukkitPlatform.plugin(), "empty");
        }

        @NotNull
        @Override
        public Collection<ItemStack> populateLoot(@Nullable Random random, @NotNull LootContext context) {
            return List.of();
        }

        @Override
        public void fillInventory(@NotNull Inventory inventory, @Nullable Random random, @NotNull LootContext context) {
        }
    };

    private final Engine engine;
    private final Block block;
    private final InventorySlotType slot;
    private final KList<IrisLootTable> tables;

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

    /**
     * Triggers the corresponding Bukkit loot event.
     * This method integrates your custom IrisLootTables with Bukkit's LootGenerateEvent,
     * allowing other plugins to modify or cancel the loot generation.
     *
     * @return true when the event was canceled
     */
    public static boolean callLootEvent(KList<ItemStack> loot, Inventory inv, World world, int x, int y, int z) {
        InventoryHolder holder = inv.getHolder();
        Location loc = new Location(world, x, y, z);
        if (holder == null) {
            holder = new InventoryHolder() {
                @NotNull
                @Override
                public Inventory getInventory() {
                    return inv;
                }
            };
        }

        LootContext context = new LootContext.Builder(loc).build();
        LootGenerateEvent event = new LootGenerateEvent(world, null, holder, EMPTY, context, loot, true);
        if (!Bukkit.isPrimaryThread()) {
            IrisLogging.warn("LootGenerateEvent was not called on the main thread, please report this issue.");
            Thread.dumpStack();
            J.sfut(() -> {
                try {
                    Bukkit.getPluginManager().callEvent(event);
                } catch (Throwable e) {
                    IrisLogging.reportError("LootGenerateEvent dispatch failed at "
                            + world.getName() + " [" + x + "," + y + "," + z + "].", e);
                    if (e instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    if (e instanceof Error error) {
                        throw error;
                    }
                    throw new IllegalStateException(e);
                }
            }).join();
        } else {
            try {
                Bukkit.getPluginManager().callEvent(event);
            } catch (Throwable e) {
                IrisLogging.reportError("LootGenerateEvent dispatch failed at "
                        + world.getName() + " [" + x + "," + y + "," + z + "].", e);
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (e instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(e);
            }
        }

        return event.isCancelled();
    }
}
