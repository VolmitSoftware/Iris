package com.volmit.iris.scaffold.engine;

import com.volmit.iris.object.InventorySlotType;
import com.volmit.iris.object.IrisLootReference;
import com.volmit.iris.object.IrisLootTable;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;

public interface LootProvider {
    void scramble(Inventory inventory, RNG rng);

    void injectTables(KList<IrisLootTable> list, IrisLootReference r);

    KList<IrisLootTable> getLootTables(RNG rng, Block b);

    void addItems(boolean debug, Inventory inv, RNG rng, KList<IrisLootTable> tables, InventorySlotType slot, int x, int y, int z, int mgf);
}
