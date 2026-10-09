package art.arcane.iris.engine.object.loot;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.volmlib.util.collection.KList;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisVanillaLootTable extends IrisLootTable {
    /**
     * The vanilla loot table key ("minecraft:chests/simple_dungeon"), not the LootTable itself. A
     * raw org.bukkit.loot.LootTable field - and the constructor lombok would generate for it - makes
     * every reflective walk over the IrisLootTable hierarchy (Gson, schema generation, the purity
     * gate) resolve a Bukkit class. The host resolves the table when it pulls loot from it. The key text is
     * identical to what {@code LootTable.getKey().toString()} produced before, so
     * {@code LootTables.identity} (which falls back to
     * {@link #getName()}) yields the same loot seed.
     */
    private final String lootTableKey;

    @Override
    public String getName() {
        return "Vanilla " + lootTableKey;
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
}
