package com.volmit.iris.scaffold.engine;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.manager.gui.Renderer;
import com.volmit.iris.object.*;
import com.volmit.iris.scaffold.cache.Cache;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.scaffold.parallax.ParallaxAccess;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.*;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.awt.*;
import java.util.Arrays;

public interface Engine extends DataProvider, Fallible, GeneratorAccess, LootProvider, BlockUpdater, Renderer, Hotloadable {
    public void close();

    public boolean isClosed();

    public EngineWorldManager getWorldManager();

    public void setParallelism(int parallelism);

    public int getParallelism();

    public EngineTarget getTarget();

    public EngineFramework getFramework();

    public void setMinHeight(int min);

    public void recycle();

    public int getIndex();

    public int getMinHeight();

    public double modifyX(double x);

    public double modifyZ(double z);

    public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes);

    public EngineMetrics getMetrics();

    default void save()
    {
       getParallax().saveAll();
    }

    default void saveNow()
    {
        getParallax().saveAllNOW();
    }

    default String getName()
    {
        return getDimension().getName();
    }

    public default int getHeight()
    {
        return getTarget().getHeight();
    }

    public default IrisDataManager getData()
    {
        return getTarget().getData();
    }

    public default World getWorld()
    {
        return getTarget().getWorld();
    }

    public default IrisDimension getDimension()
    {
        return getTarget().getDimension();
    }

    public default ParallaxAccess getParallax()
    {
        return getTarget().getParallaxWorld();
    }

    public default Color draw(double x, double z)
    {
        IrisRegion region = getRegion((int)x, (int)z);
        IrisBiome biome = getSurfaceBiome((int)x, (int)z);
        int height = getHeight((int) x, (int) z);
        double heightFactor = M.lerpInverse(0, getHeight(), height);
        IrisColor irc = region.getColor();
        IrisColor ibc = biome.getColor();
        Color rc = irc != null ? irc.getColor() : Color.GREEN.darker();
        Color bc = ibc != null ? ibc.getColor() : biome.isAquatic() ? Color.BLUE : Color.YELLOW;
        Color f = IrisColor.blend(rc, bc, bc, Color.getHSBColor(0, 0, (float)heightFactor));

        return f;
    }

    @Override
    public default IrisRegion getRegion(int x, int z) {
        return getFramework().getComplex().getRegionStream().get(x, z);
    }

    @Override
    public default ParallaxAccess getParallaxAccess()
    {
        return getParallax();
    }

    @Override
    public default IrisBiome getCaveBiome(int x, int z)
    {
        return getFramework().getComplex().getCaveBiomeStream().get(x, z);
    }

    @Override
    public default IrisBiome getSurfaceBiome(int x, int z)
    {
        return getFramework().getComplex().getTrueBiomeStream().get(x, z);
    }

    @Override
    public default int getHeight(int x, int z)
    {
        return getFramework().getEngineParallax().getHighest(x, z, true);
    }

    @Override
    public default void catchBlockUpdates(int x, int y, int z, BlockData data) {
        if(data == null)
        {
            return;
        }

        if(B.isUpdatable(data))
        {
            synchronized (getParallax())
            {
                getParallax().updateBlock(x,y,z);
                getParallax().getMetaRW(x>>4, z>>4).setUpdates(true);
            }
        }
    }

    public default void placeTiles(Chunk c) {

    }

    @Override
    public default void updateChunk(Chunk c)
    {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        if(getParallax().getMetaR(c.getX(), c.getZ()).isUpdates())
        {
            Hunk<Boolean> b = getParallax().getUpdatesR(c.getX(), c.getZ());

            b.iterateSync((x,y,z,v) -> {

                if(v != null && v)
                {
                    int vx = x & 15;
                    int vz = z & 15;
                    update(x,y,z, c, new RNG(Cache.key(c.getX(), c.getZ())));

                    if(vx > 0 && vx < 15 && vz > 0 && vz < 15)
                    {
                        updateLighting(x,y,z,c);
                    }
                }
            });
        }

        getMetrics().getUpdates().put(p.getMilliseconds());
    }

    public default void updateLighting(int x, int y, int z, Chunk c)
    {
        Block block = c.getBlock(x,y,z);
        BlockData data = block.getBlockData();

        if(B.isLit(data))
        {
            try {
                block.setType(Material.AIR, false);
                block.setBlockData(data, true);
            } catch (Exception e){
                // Issue when adding block data. Suppress massive warnings and stack-traces to console.
            }
        }
    }

    @Override
    public default void update(int x, int y, int z, Chunk c, RNG rf)
    {
        Block block = c.getBlock(x,y,z);
        BlockData data = block.getBlockData();

        if(B.isStorage(data))
        {
            RNG rx = rf.nextParallelRNG(x).nextParallelRNG(z).nextParallelRNG(y);
            InventorySlotType slot = null;

            if(B.isStorageChest(data))
            {
                slot = InventorySlotType.STORAGE;
            }

            if(slot != null)
            {
                KList<IrisLootTable> tables = getLootTables(rx.nextParallelRNG(4568111), block);
                InventorySlotType slott = slot;

                try
                {
                    InventoryHolder m = (InventoryHolder) block.getState();
                    addItems(false, m.getInventory(), rx, tables, slott, x, y, z, 15);
                }

                catch(Throwable ignored)
                {

                }
            }
        }
    }

    @Override
    public default void scramble(Inventory inventory, RNG rng)
    {
        org.bukkit.inventory.ItemStack[] items = inventory.getContents();
        org.bukkit.inventory.ItemStack[] nitems = new org.bukkit.inventory.ItemStack[inventory.getSize()];
        System.arraycopy(items, 0, nitems, 0, items.length);
        boolean packedFull = false;

        splitting: for(int i = 0; i < nitems.length; i++)
        {
            ItemStack is = nitems[i];

            if(is != null && is.getAmount() > 1 && !packedFull)
            {
                for(int j = 0; j < nitems.length; j++)
                {
                    if(nitems[j] == null)
                    {
                        int take = rng.nextInt(is.getAmount());
                        take = take == 0 ? 1 : take;
                        is.setAmount(is.getAmount() - take);
                        nitems[j] = is.clone();
                        nitems[j].setAmount(take);
                        continue splitting;
                    }
                }

                packedFull = true;
            }
        }

        for(int i = 0; i < 4; i++)
        {
            try
            {
                Arrays.parallelSort(nitems, (a, b) -> rng.nextInt());
                break;
            }

            catch(Throwable e)
            {

            }
        }

        inventory.setContents(nitems);
    }

    @Override
    public default void injectTables(KList<IrisLootTable> list, IrisLootReference r)
    {
        if(r.getMode().equals(LootMode.CLEAR) || r.getMode().equals(LootMode.REPLACE))
        {
            list.clear();
        }

        list.addAll(r.getLootTables(getFramework().getComplex()));
    }

    @Override
    public default KList<IrisLootTable> getLootTables(RNG rng, Block b)
    {
        int rx = b.getX();
        int rz = b.getZ();
        double he = getFramework().getComplex().getHeightStream().get(rx, rz);
        IrisRegion region = getFramework().getComplex().getRegionStream().get(rx, rz);
        IrisBiome biomeSurface = getFramework().getComplex().getTrueBiomeStream().get(rx, rz);
        IrisBiome biomeUnder = b.getY() < he ? getFramework().getComplex().getCaveBiomeStream().get(rx, rz) : biomeSurface;
        KList<IrisLootTable> tables = new KList<>();
        double multiplier = 1D * getDimension().getLoot().getMultiplier() * region.getLoot().getMultiplier() * biomeSurface.getLoot().getMultiplier() * biomeUnder.getLoot().getMultiplier();
        injectTables(tables, getDimension().getLoot());
        injectTables(tables, region.getLoot());
        injectTables(tables, biomeSurface.getLoot());
        injectTables(tables, biomeUnder.getLoot());

        if(tables.isNotEmpty())
        {
            int target = (int) Math.round(tables.size() * multiplier);

            while(tables.size() < target && tables.isNotEmpty())
            {
                tables.add(tables.get(rng.i(tables.size() - 1)));
            }

            while(tables.size() > target && tables.isNotEmpty())
            {
                tables.remove(rng.i(tables.size() - 1));
            }
        }

        return tables;
    }

    @Override
    public default void addItems(boolean debug, Inventory inv, RNG rng, KList<IrisLootTable> tables, InventorySlotType slot, int x, int y, int z, int mgf)
    {
        KList<ItemStack> items = new KList<>();

        int b = 4;
        for(IrisLootTable i : tables)
        {
            b++;
            items.addAll(i.getLoot(debug, items.isEmpty(), rng.nextParallelRNG(345911), slot, x, y, z, b + b, mgf + b));
        }

        for(ItemStack i : items)
        {
            inv.addItem(i);
        }

        scramble(inv, rng);
    }

    public default int getMaxHeight()
    {
        return getHeight() + getMinHeight();
    }

    public EngineEffects getEffects();

    public EngineCompound getCompound();

    public default boolean isStudio()
    {
        return getCompound().isStudio();
    }

    public default void clean()
    {
        MultiBurst.burst.lazy(() -> getParallax().cleanup());
    }

    default IrisBiome getBiome(Location l)
    {
        return getBiome(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    default IrisRegion getRegion(Location l)
    {
        return getRegion(l.getBlockX(), l.getBlockZ());
    }

    default boolean contains(Location l)
    {
        return l.getBlockY() >= getMinHeight() && l.getBlockY() <= getMaxHeight();
    }
}
