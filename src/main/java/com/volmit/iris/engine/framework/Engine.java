/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.engine.framework;

import com.volmit.iris.Iris;
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.core.gui.components.Renderer;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.object.basic.IrisColor;
import com.volmit.iris.engine.object.basic.IrisPosition;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.common.IrisWorld;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.engine.object.engine.IrisEngineData;
import com.volmit.iris.engine.object.loot.IrisLootMode;
import com.volmit.iris.engine.object.loot.IrisLootReference;
import com.volmit.iris.engine.object.loot.IrisLootTable;
import com.volmit.iris.engine.object.meta.InventorySlotType;
import com.volmit.iris.engine.object.regional.IrisRegion;
import com.volmit.iris.engine.parallax.ParallaxAccess;
import com.volmit.iris.engine.scripting.EngineExecutionEnvironment;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.DataProvider;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.function.Function2;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.BlockPosition;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import com.volmit.iris.util.stream.ProceduralStream;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.awt.*;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public interface Engine extends DataProvider, Fallible, GeneratorAccess, LootProvider, BlockUpdater, Renderer {
    IrisComplex getComplex();

    void printMetrics(CommandSender sender);

    void recycle();

    EngineParallaxManager getEngineParallax();

    EngineActuator<BlockData> getTerrainActuator();

    EngineActuator<BlockData> getDecorantActuator();

    EngineActuator<Biome> getBiomeActuator();

    EngineModifier<BlockData> getCaveModifier();

    EngineModifier<BlockData> getRavineModifier();

    EngineModifier<BlockData> getDepositModifier();

    EngineModifier<BlockData> getPostModifier();

    void close();

    IrisContext getContext();

    EngineExecutionEnvironment getExecution();

    double getMaxBiomeObjectDensity();

    double getMaxBiomeDecoratorDensity();

    double getMaxBiomeLayerDensity();

    boolean isClosed();

    EngineWorldManager getWorldManager();

    void setParallelism(int parallelism);

    default UUID getBiomeID(int x, int z) {
        return getComplex().getBaseBiomeIDStream().get(x, z);
    }

    int getParallelism();

    EngineTarget getTarget();

    void setMinHeight(int min);

    default int getMinHeight()
    {
        return getTarget().getWorld().minHeight();
    }

    @BlockCoordinates
    double modifyX(double x);

    @BlockCoordinates
    double modifyZ(double z);

    @BlockCoordinates
    void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes, boolean multicore);

    EngineMetrics getMetrics();

    default void save() {
        getParallax().saveAll();
        getWorldManager().onSave();
        saveEngineData();
    }

    default void saveNow() {
        getParallax().saveAllNOW();
        saveEngineData();
    }

    void saveEngineData();

    default String getName() {
        return getDimension().getName();
    }

    default IrisData getData() {
        return getTarget().getData();
    }

    default IrisWorld getWorld() {
        return getTarget().getWorld();
    }

    default IrisDimension getDimension() {
        return getTarget().getDimension();
    }

    default ParallaxAccess getParallax() {
        return getTarget().getParallaxWorld();
    }

    @BlockCoordinates
    default Color draw(double x, double z) {
        IrisRegion region = getRegion((int) x, (int) z);
        IrisBiome biome = getSurfaceBiome((int) x, (int) z);
        int height = getHeight((int) x, (int) z);
        double heightFactor = M.lerpInverse(0, getTarget().getHeight(), height);
        Color irc = region.getColor(this.getComplex(), RenderType.BIOME);
        Color ibc = biome.getColor(this, RenderType.BIOME);
        Color rc = irc != null ? irc : Color.GREEN.darker();
        Color bc = ibc != null ? ibc : biome.isAquatic() ? Color.BLUE : Color.YELLOW;
        Color f = IrisColor.blend(rc, bc, bc, Color.getHSBColor(0, 0, (float) heightFactor));

        return IrisColor.blend(rc, bc, bc, Color.getHSBColor(0, 0, (float) heightFactor));
    }

    @BlockCoordinates
    @Override
    default IrisRegion getRegion(int x, int z) {
        return getComplex().getRegionStream().get(x, z);
    }

    @Override
    default ParallaxAccess getParallaxAccess() {
        return getParallax();
    }

    @BlockCoordinates
    @Override
    default IrisBiome getCaveBiome(int x, int z) {
        return getComplex().getCaveBiomeStream().get(x, z);
    }

    @BlockCoordinates
    @Override
    default IrisBiome getSurfaceBiome(int x, int z) {
        return getComplex().getTrueBiomeStream().get(x, z);
    }

    @BlockCoordinates
    @Override
    default int getHeight(int x, int z) {
        return getHeight(x, z, true);
    }

    @BlockCoordinates
    default int getHeight(int x, int z, boolean ignoreFluid) {
        return getEngineParallax().getHighest(x, z, getData(), ignoreFluid);
    }

    @BlockCoordinates
    @Override
    default void catchBlockUpdates(int x, int y, int z, BlockData data) {
        if (data == null) {
            return;
        }

        if (B.isUpdatable(data)) {
            getParallax().updateBlock(x, y, z);
            getParallax().getMetaRW(x >> 4, z >> 4).setUpdates(true);
        }
    }

    @ChunkCoordinates
    default void placeTiles(Chunk c) {

    }

    @ChunkCoordinates
    @Override
    default void updateChunk(Chunk c) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        if (getParallax().getMetaR(c.getX(), c.getZ()).isUpdates()) {
            Hunk<Boolean> b = getParallax().getUpdatesR(c.getX(), c.getZ());

            b.iterateSync((x, y, z, v) -> {

                if (v != null && v) {
                    int vx = x & 15;
                    int vz = z & 15;
                    update(x, y, z, c, new RNG(Cache.key(c.getX(), c.getZ())));

                    if (vx > 0 && vx < 15 && vz > 0 && vz < 15) {
                        updateLighting(x, y, z, c);
                    }
                }
            });
        }

        getMetrics().getUpdates().put(p.getMilliseconds());
    }

    @BlockCoordinates
    default void updateLighting(int x, int y, int z, Chunk c) {
        Block block = c.getBlock(x, y, z);
        BlockData data = block.getBlockData();

        if (B.isLit(data)) {
            try {
                block.setType(Material.AIR, false);
                block.setBlockData(data, true);
            } catch (Exception e) {
                Iris.reportError(e);
                // Issue when adding block data. Suppress massive warnings and stack-traces to console.
            }
        }
    }

    @BlockCoordinates
    @Override
    default void update(int x, int y, int z, Chunk c, RNG rf) {
        Block block = c.getBlock(x, y, z);
        BlockData data = block.getBlockData();

        if (B.isStorage(data)) {
            RNG rx = rf.nextParallelRNG(BlockPosition.toLong(x, y, z));
            InventorySlotType slot = null;

            if (B.isStorageChest(data)) {
                slot = InventorySlotType.STORAGE;
            }

            if (slot != null) {
                KList<IrisLootTable> tables = getLootTables(rx, block);

                try {
                    InventoryHolder m = (InventoryHolder) block.getState();
                    addItems(false, m.getInventory(), rx, tables, slot, x, y, z, 15);
                } catch (Throwable e) {
                    Iris.reportError(e);
                }
            }
        }
    }

    @Override
    default void scramble(Inventory inventory, RNG rng) {
        org.bukkit.inventory.ItemStack[] items = inventory.getContents();
        org.bukkit.inventory.ItemStack[] nitems = new org.bukkit.inventory.ItemStack[inventory.getSize()];
        System.arraycopy(items, 0, nitems, 0, items.length);
        boolean packedFull = false;

        splitting:
        for (int i = 0; i < nitems.length; i++) {
            ItemStack is = nitems[i];

            if (is != null && is.getAmount() > 1 && !packedFull) {
                for (int j = 0; j < nitems.length; j++) {
                    if (nitems[j] == null) {
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

        for (int i = 0; i < 4; i++) {
            try {
                Arrays.parallelSort(nitems, (a, b) -> rng.nextInt());
                break;
            } catch (Throwable e) {
                Iris.reportError(e);

            }
        }

        inventory.setContents(nitems);
    }

    @Override
    default void injectTables(KList<IrisLootTable> list, IrisLootReference r) {
        if (r.getMode().equals(IrisLootMode.CLEAR) || r.getMode().equals(IrisLootMode.REPLACE)) {
            list.clear();
        }

        list.addAll(r.getLootTables(getComplex()));
    }

    @BlockCoordinates
    @Override
    default KList<IrisLootTable> getLootTables(RNG rng, Block b) {
        int rx = b.getX();
        int rz = b.getZ();
        double he = getComplex().getHeightStream().get(rx, rz);
        PlacedObject po = getObjectPlacement(rx, b.getY(), rz);
        if (po != null && po.getPlacement() != null) {

            if (B.isStorageChest(b.getBlockData())) {
                IrisLootTable table = po.getPlacement().getTable(b.getBlockData(), getData());
                if (table != null) {
                    return new KList<>(table);
                }
            }
        }
        IrisRegion region = getComplex().getRegionStream().get(rx, rz);
        IrisBiome biomeSurface = getComplex().getTrueBiomeStream().get(rx, rz);
        IrisBiome biomeUnder = b.getY() < he ? getComplex().getCaveBiomeStream().get(rx, rz) : biomeSurface;
        KList<IrisLootTable> tables = new KList<>();
        double multiplier = 1D * getDimension().getLoot().getMultiplier() * region.getLoot().getMultiplier() * biomeSurface.getLoot().getMultiplier() * biomeUnder.getLoot().getMultiplier();
        injectTables(tables, getDimension().getLoot());
        injectTables(tables, region.getLoot());
        injectTables(tables, biomeSurface.getLoot());
        injectTables(tables, biomeUnder.getLoot());

        if (tables.isNotEmpty()) {
            int target = (int) Math.round(tables.size() * multiplier);

            while (tables.size() < target && tables.isNotEmpty()) {
                tables.add(tables.get(rng.i(tables.size() - 1)));
            }

            while (tables.size() > target && tables.isNotEmpty()) {
                tables.remove(rng.i(tables.size() - 1));
            }
        }

        return tables;
    }

    @Override
    default void addItems(boolean debug, Inventory inv, RNG rng, KList<IrisLootTable> tables, InventorySlotType slot, int x, int y, int z, int mgf) {
        KList<ItemStack> items = new KList<>();

        int b = 4;
        for (IrisLootTable i : tables) {
            b++;
            items.addAll(i.getLoot(debug, items.isEmpty(), rng, slot, x, y, z, b + b, mgf + b));
        }

        for (ItemStack i : items) {
            inv.addItem(i);
        }

        scramble(inv, rng);
    }

    EngineEffects getEffects();

    default MultiBurst burst() {
        return getTarget().getBurster();
    }

    default void clean() {
        burst().lazy(() -> getParallax().cleanup());
    }

    @BlockCoordinates
    default IrisBiome getBiome(Location l) {
        return getBiome(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    @BlockCoordinates
    default IrisRegion getRegion(Location l) {
        return getRegion(l.getBlockX(), l.getBlockZ());
    }

    IrisBiome getFocus();

    IrisEngineData getEngineData();

    default IrisBiome getSurfaceBiome(Chunk c) {
        return getSurfaceBiome((c.getX() << 4) + 8, (c.getZ() << 4) + 8);
    }

    default IrisRegion getRegion(Chunk c) {
        return getRegion((c.getX() << 4) + 8, (c.getZ() << 4) + 8);
    }

    default KList<IrisBiome> getAllBiomes() {
        KMap<String, IrisBiome> v = new KMap<>();

        IrisDimension dim = getDimension();
        dim.getAllBiomes(this).forEach((i) -> v.put(i.getLoadKey(), i));

        try {
            dim.getDimensionalComposite().forEach((m) -> getData().getDimensionLoader().load(m.getDimension()).getAllBiomes(this).forEach((i) -> v.put(i.getLoadKey(), i)));
        } catch (Throwable ignored) {
            Iris.reportError(ignored);

        }

        return v.v();
    }

    int getGenerated();

    default <T> IrisPosition lookForStreamResult(T find, ProceduralStream<T> stream, Function2<T, T, Boolean> matcher, long timeout)
    {
        AtomicInteger checked = new AtomicInteger();
        AtomicLong time = new AtomicLong(M.ms());
        AtomicReference<IrisPosition> r = new AtomicReference<>();
        BurstExecutor b = burst().burst();

        while(M.ms() - time.get() < timeout && r.get() == null)
        {
            b.queue(() -> {
                for(int i = 0; i < 1000; i++)
                {
                    if(M.ms() - time.get() > timeout)
                    {
                        return;
                    }

                    int x = RNG.r.i(-29999970, 29999970);
                    int z = RNG.r.i(-29999970, 29999970);
                    checked.incrementAndGet();
                    if(matcher.apply(stream.get(x, z), find))
                    {
                        r.set(new IrisPosition(x, 120, z));
                        time.set(0);
                    }
                }
            });
        }

        return r.get();
    }

    default IrisPosition lookForBiome(IrisBiome biome, long timeout, Consumer<Integer> triesc) {
        if (!getWorld().hasRealWorld()) {
            Iris.error("Cannot GOTO without a bound world (headless mode)");
            return null;
        }

        ChronoLatch cl = new ChronoLatch(250, false);
        long s = M.ms();
        int cpus = (Runtime.getRuntime().availableProcessors());

        if (!getDimension().getAllBiomes(this).contains(biome)) {
            return null;
        }

        AtomicInteger tries = new AtomicInteger(0);
        AtomicBoolean found = new AtomicBoolean(false);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<IrisPosition> location = new AtomicReference<>();
        for (int i = 0; i < cpus; i++) {
            J.a(() -> {
                try {
                    Engine e;
                    IrisBiome b;
                    int x, z;

                    while (!found.get() && running.get()) {
                        try {
                            x = RNG.r.i(-29999970, 29999970);
                            z = RNG.r.i(-29999970, 29999970);
                            b = getSurfaceBiome(x, z);

                            if (b != null && b.getLoadKey() == null) {
                                continue;
                            }

                            if (b != null && b.getLoadKey().equals(biome.getLoadKey())) {
                                found.lazySet(true);
                                location.lazySet(new IrisPosition(x, getHeight(x, z), z));
                            }

                            tries.getAndIncrement();
                        } catch (Throwable ex) {
                            Iris.reportError(ex);
                            ex.printStackTrace();
                            return;
                        }
                    }
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                }
            });
        }

        while (!found.get() || location.get() == null) {
            J.sleep(50);

            if (cl.flip()) {
                triesc.accept(tries.get());
            }

            if (M.ms() - s > timeout) {
                running.set(false);
                return null;
            }
        }

        running.set(false);
        return location.get();
    }

    default IrisPosition lookForRegion(IrisRegion reg, long timeout, Consumer<Integer> triesc) {
        if (getWorld().hasRealWorld()) {
            Iris.error("Cannot GOTO without a bound world (headless mode)");
            return null;
        }

        ChronoLatch cl = new ChronoLatch(3000, false);
        long s = M.ms();
        int cpus = (Runtime.getRuntime().availableProcessors());

        if (!getDimension().getRegions().contains(reg.getLoadKey())) {
            return null;
        }

        AtomicInteger tries = new AtomicInteger(0);
        AtomicBoolean found = new AtomicBoolean(false);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<IrisPosition> location = new AtomicReference<>();

        for (int i = 0; i < cpus; i++) {
            J.a(() -> {
                Engine e;
                IrisRegion b;
                int x, z;

                while (!found.get() && running.get()) {
                    try {
                        x = RNG.r.i(-29999970, 29999970);
                        z = RNG.r.i(-29999970, 29999970);
                        b = getRegion(x, z);

                        if (b != null && b.getLoadKey() != null && b.getLoadKey().equals(reg.getLoadKey())) {
                            found.lazySet(true);
                            location.lazySet(new IrisPosition(x, getHeight(x, z), z));
                        }

                        tries.getAndIncrement();
                    } catch (Throwable xe) {
                        Iris.reportError(xe);
                        xe.printStackTrace();
                        return;
                    }
                }
            });
        }

        while (!found.get() || location.get() != null) {
            J.sleep(50);

            if (cl.flip()) {
                triesc.accept(tries.get());
            }

            if (M.ms() - s > timeout) {
                triesc.accept(tries.get());
                running.set(false);
                return null;
            }
        }

        triesc.accept(tries.get());
        running.set(false);
        return location.get();
    }

    double getGeneratedPerSecond();

    default int getHeight()
    {
        return getWorld().getHeight();
    }

    boolean isStudio();
}
