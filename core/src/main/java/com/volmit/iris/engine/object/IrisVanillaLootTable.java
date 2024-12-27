package com.volmit.iris.engine.object;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

import java.io.File;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisVanillaLootTable extends IrisLootTable {
    private final LootTable lootTable;

    @Override
    public String getName() {
        return "Vanilla " + lootTable.getKey();
    }

    @Override
    public int getRarity() {
        return 0;
    }

    @Override
    public int getMaxPicked() {
        return 0;
    }

    @Override
    public int getMinPicked() {
        return 0;
    }

    @Override
    public int getMaxTries() {
        return 0;
    }

    @Override
    public KList<IrisLoot> getLoot() {
        return new KList<>();
    }

    @Override
    public KList<ItemStack> getLoot(boolean debug, RNG rng, InventorySlotType slot, World world, int x, int y, int z) {
        return new KList<>(lootTable.populateLoot(rng, new LootContext.Builder(new Location(world, x, y, z)).build()));
    }

    @Override
    public String getFolderName() {
        throw new UnsupportedOperationException("VanillaLootTables do not have a folder name");
    }

    @Override
    public String getTypeName() {
        throw new UnsupportedOperationException("VanillaLootTables do not have a type name");
    }

    @Override
    public File getLoadFile() {
        throw new UnsupportedOperationException("VanillaLootTables do not have a load file");
    }

    @Override
    public IrisData getLoader() {
        throw new UnsupportedOperationException("VanillaLootTables do not have a loader");
    }

    @Override
    public KList<String> getPreprocessors() {
        return new KList<>();
    }
}
