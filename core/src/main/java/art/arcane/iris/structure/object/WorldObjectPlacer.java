package art.arcane.iris.structure.object;

import art.arcane.iris.platform.bukkit.BukkitBlockResolution;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.iris.world.event.IrisLootEvent;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.platform.generation.EngineBukkitOps;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.world.loot.InventorySlotType;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.generation.block.IrisCustomData;
import art.arcane.volmlib.util.math.RNG;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.InventoryHolder;

@Getter
@EqualsAndHashCode(exclude = {"engine", "mantle"})
public class WorldObjectPlacer implements IObjectPlacer {
    private final World world;
    private final Engine engine;
    private final EngineMantle mantle;

    public WorldObjectPlacer(World world) {
        PlatformChunkGenerator a = IrisToolbelt.access(world);
        if (a == null || a.getEngine() == null) throw new IllegalStateException(world.getName() + " is not an Iris World!");
        this.world = world;
        this.engine = a.getEngine();
        this.mantle = engine.getMantle();
    }

    @Override
    public int getHighest(int x, int z, IrisData data) {
        return mantle.getHighest(x, z, data);
    }

    @Override
    public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        return mantle.getHighest(x, z, data, ignoreFluid);
    }

    @Override
    public void set(int x, int y, int z, PlatformBlockState state) {
        BlockData d = (BlockData) state.nativeHandle();
        int worldY = y + world.getMinHeight();
        if (worldY < world.getMinHeight() || worldY >= world.getMaxHeight()) return;
        Block block = world.getBlockAt(x, worldY, z);

        if (block.getType() == Material.BEDROCK) return;
        boolean storageChest = BukkitBlockResolution.isStorageChest(d);

        if (d instanceof IrisCustomData data) {
            block.setBlockData(data.getBase(), false);
            // Reached per block for every custom block in the pack; the pack is what needs changing, and
            // one statement of that is enough.
            IrisLogging.warnOnce("custom-block-placer", "Tried to place custom block at " + x + ", " + y + ", " + z + " which is not supported.");
        } else block.setBlockData(d, false);

        if (storageChest && !J.runRegion(world, x >> 4, z >> 4, () -> fillLoot(block), 1)) {
            IrisLogging.warn("Failed to schedule loot resolution at " + x + ", " + worldY + ", " + z);
        }
    }

    private void fillLoot(Block block) {
        if (!BukkitBlockResolution.isStorageChest(block.getBlockData()) || !EngineBukkitOps.isCanonicalContainer(block)) {
            return;
        }
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        RNG rng = LootResolver.containerRng(engine.getSeedManager().getLoot(), x, y, z);
        KList<IrisLootTable> tables = EngineBukkitOps.getLootTables(engine, rng, block);

        try {
            Bukkit.getPluginManager().callEvent(new IrisLootEvent(engine, block, InventorySlotType.STORAGE, tables));
            if (tables.isEmpty()) {
                return;
            }
            InventoryHolder holder = (InventoryHolder) block.getState();
            EngineBukkitOps.addItems(engine, false, holder.getInventory(), tables, InventorySlotType.STORAGE, world, x, y, z);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    @Override
    public PlatformBlockState get(int x, int y, int z) {
        return BukkitBlockState.of(world.getBlockAt(x, y + world.getMinHeight(), z).getBlockData());
    }

    @Override
    public boolean isPreventingDecay() {
        return mantle.isPreventingDecay();
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        return mantle.isCarved(x, y, z);
    }

    @Override
    public boolean isSurfaceSolid(int x, int y, int z) {
        return isSolid(x, y, z);
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return world.getBlockAt(x, y + world.getMinHeight(), z).getType().isSolid();
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        return mantle.isUnderwater(x, z);
    }

    @Override
    public int getFluidHeight() {
        return mantle.getFluidHeight();
    }

    @Override
    public boolean isDebugSmartBore() {
        return mantle.isDebugSmartBore();
    }

    @Override
    public void setTile(int xx, int yy, int zz, TileData tile) {
        int worldY = yy + world.getMinHeight();
        if (worldY < world.getMinHeight() || worldY >= world.getMaxHeight()) return;
        tile.toBukkitTry(world.getBlockAt(xx, worldY, zz));
    }

    @Override
    public <T> void setData(int xx, int yy, int zz, T data) {
        if (data == null || yy < 0 || yy >= engine.getHeight()) {
            return;
        }
        mantle.getMantle().set(xx, yy, zz, data);
    }

    @Override
    public <T> T getData(int xx, int yy, int zz, Class<T> t) {
        if (yy < 0 || yy >= engine.getHeight()) {
            return null;
        }
        return mantle.getMantle().get(xx, yy, zz, t);
    }
}
