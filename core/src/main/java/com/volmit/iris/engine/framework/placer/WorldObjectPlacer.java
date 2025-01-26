package com.volmit.iris.engine.framework.placer;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.core.events.IrisLootEvent;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.object.IObjectPlacer;
import com.volmit.iris.engine.object.InventorySlotType;
import com.volmit.iris.engine.object.IrisLootTable;
import com.volmit.iris.engine.object.TileData;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.IrisCustomData;
import com.volmit.iris.util.math.RNG;
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
        var a = IrisToolbelt.access(world);
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
    public void set(int x, int y, int z, BlockData d) {
        Block block = world.getBlockAt(x, y + world.getMinHeight(), z);

        if (y <= world.getMinHeight() || block.getType() == Material.BEDROCK) return;
        InventorySlotType slot = null;
        if (B.isStorageChest(d)) {
            slot = InventorySlotType.STORAGE;
        }

        if (slot != null) {
            RNG rx = new RNG(Cache.key(x, z));
            KList<IrisLootTable> tables = engine.getLootTables(rx, block);

            try {
                Bukkit.getPluginManager().callEvent(new IrisLootEvent(engine, block, slot, tables));

                if (!tables.isEmpty()){
                    Iris.debug("IrisLootEvent has been accessed");
                }

                if (tables.isEmpty())
                    return;
                InventoryHolder m = (InventoryHolder) block.getState();
                engine.addItems(false, m.getInventory(), rx, tables, slot, world, x, y, z, 15);
            } catch (Throwable e) {
                Iris.reportError(e);
            }
        }


        if (d instanceof IrisCustomData data) {
            block.setBlockData(data.getBase());
            Iris.warn("Tried to place custom block at " + x + ", " + y + ", " + z + " which is not supported!");
        } else block.setBlockData(d);
    }

    @Override
    public BlockData get(int x, int y, int z) {
        return world.getBlockAt(x, y + world.getMinHeight(), z).getBlockData();
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
        tile.toBukkitTry(world.getBlockAt(xx, yy + world.getMinHeight(), zz));
    }
}
