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

    public KList<ItemStack> getLoot(RNG rng, World world, int x, int y, int z) {
        return new KList<>(lootTable.populateLoot(rng, new LootContext.Builder(new Location(world, x, y, z)).build()));
    }

    @Override
    public String getName() {
        throw new IllegalStateException("Vanilla loot tables should not be used in Iris");
    }

    @Override
    public int getRarity() {
        throw new IllegalStateException("Vanilla loot tables should not be used in Iris");
    }

    @Override
    public int getMaxPicked() {
        throw new IllegalStateException("Vanilla loot tables should not be used in Iris");
    }

    @Override
    public int getMinPicked() {
        throw new IllegalStateException("Vanilla loot tables should not be used in Iris");
    }

    @Override
    public int getMaxTries() {
        throw new IllegalStateException("Vanilla loot tables should not be used in Iris");
    }

    @Override
    public KList<IrisLoot> getLoot() {
        throw new IllegalStateException("Vanilla loot tables should not be used in Iris");
    }

    @Override
    public KList<ItemStack> getLoot(boolean debug, RNG rng, InventorySlotType slot, World world, int x, int y, int z) {
        return new KList<>(lootTable.populateLoot(rng, new LootContext.Builder(new Location(world, x, y, z)).build()));
    }

    @Override
    public String getFolderName() {
        throw new IllegalStateException("Vanilla loot tables should not be used in Iris");
    }

    @Override
    public String getTypeName() {
        throw new IllegalStateException("Vanilla loot tables should not be used in Iris");
    }

    @Override
    public File getLoadFile() {
        throw new IllegalStateException("Vanilla loot tables should not be used in Iris");
    }

    @Override
    public IrisData getLoader() {
        throw new IllegalStateException("Vanilla loot tables should not be used in Iris");
    }

    @Override
    public KList<String> getPreprocessors() {
        throw new IllegalStateException("Vanilla loot tables should not be used in Iris");
    }
}
