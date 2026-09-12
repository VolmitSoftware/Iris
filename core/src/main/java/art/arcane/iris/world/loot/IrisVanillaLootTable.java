package art.arcane.iris.world.loot;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

import java.io.File;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisVanillaLootTable extends IrisLootTable {
    /**
     * The vanilla loot table key ("minecraft:chests/simple_dungeon"), not the LootTable itself. A
     * raw org.bukkit.loot.LootTable field - and the constructor lombok would generate for it - makes
     * every reflective walk over the IrisLootTable hierarchy (Gson, schema generation, the purity
     * gate) resolve a Bukkit class. The table is resolved lazily at pull time, which only ever
     * happens on Bukkit. The key text is identical to what {@code LootTable.getKey().toString()}
     * produced before, so {@code LootResolver.tableIdentity} (which falls back to
     * {@link #getName()}) yields the same loot seed.
     */
    private final String lootTableKey;

    /**
     * The resolved table, looked up once. Every chest pull resolved it again - a NamespacedKey parse plus a registry
     * lookup per container, and a dungeon room is a lot of containers.
     * <p>
     * Transient for the same reason the field above is a String: it keeps the Bukkit type argument out of the Gson
     * field walk and out of the pack purity gate, both of which resolve generic field types for the fields they visit.
     */
    private final transient AtomicCache<LootTable> resolvedTable = new AtomicCache<>();

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
    public KList<ItemStack> getLoot(boolean debug, long lootSeed, InventorySlotType slot, World world, int x, int y, int z) {
        LootTable table = resolveTable();
        if (table == null) {
            IrisLogging.warn("Unknown vanilla loot table " + lootTableKey);
            return new KList<>();
        }
        RNG rng = LootResolver.tableRng(lootSeed, this, x, y, z);
        return new KList<>(table.populateLoot(rng, new LootContext.Builder(new Location(world, x, y, z)).build()));
    }

    private LootTable resolveTable() {
        return resolvedTable.aquire(() -> {
            NamespacedKey key = NamespacedKey.fromString(lootTableKey);
            return key == null ? null : Bukkit.getLootTable(key);
        });
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
