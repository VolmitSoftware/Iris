package com.volmit.iris.scaffold.engine;

import com.volmit.iris.object.InventorySlotType;
import com.volmit.iris.object.IrisLootReference;
import com.volmit.iris.object.IrisLootTable;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;

public interface LootProvider {
    public void scramble(Inventory inventory, RNG rng);

    public void injectTables(KList<IrisLootTable> list, IrisLootReference r);

    public KList<IrisLootTable> getLootTables(RNG rng, Block b);

    public void addItems(boolean debug, Inventory inv, RNG rng, KList<IrisLootTable> tables, InventorySlotType slot, int x, int y, int z, int mgf);
}
