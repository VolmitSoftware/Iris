/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.core.gui.components.Renderer;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.data.chunk.TerrainChunk;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.engine.scripting.EngineExecutionEnvironment;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.DataProvider;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.function.Function2;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.BlockPosition;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterCavern;
import com.volmit.iris.util.matter.MatterUpdate;
import com.volmit.iris.util.matter.TileWrapper;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import com.volmit.iris.util.stream.ProceduralStream;
import io.papermc.lib.PaperLib;
import net.minecraft.core.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import oshi.util.tuples.Pair;

import java.awt.*;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public interface Engine extends DataProvider, Fallible, LootProvider, BlockUpdater, Renderer, Hotloadable {
    IrisComplex getComplex();

    EngineMode getMode();

    int getBlockUpdatesPerSecond();

    void printMetrics(CommandSender sender);

    EngineMantle getMantle();

    void hotloadSilently();

    void hotloadComplex();

    void recycle();

    void close();

    IrisContext getContext();

    EngineExecutionEnvironment getExecution();

    double getMaxBiomeObjectDensity();

    double getMaxBiomeDecoratorDensity();

    double getMaxBiomeLayerDensity();

    boolean isClosed();

    EngineWorldManager getWorldManager();

    default UUID getBiomeID(int x, int z) {
        return getComplex().getBaseBiomeIDStream().get(x, z);
    }

    int getParallelism();

    void setParallelism(int parallelism);

    EngineTarget getTarget();

    default int getMaxHeight() {
        return getTarget().getWorld().maxHeight();
    }

    default int getMinHeight() {
        return getTarget().getWorld().minHeight();
    }

    void setMinHeight(int min);

    @BlockCoordinates
    default void generate(int x, int z, TerrainChunk tc, boolean multicore) throws WrongEngineBroException {
        generate(x, z, Hunk.view(tc), Hunk.view(tc, tc.getMinHeight(), tc.getMaxHeight()), multicore);
    }

    @BlockCoordinates
    void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes, boolean multicore) throws WrongEngineBroException;

    EngineMetrics getMetrics();

    default void save() {
        getMantle().save();
        getWorldManager().onSave();
        saveEngineData();
    }

    default void saveNow() {
        getMantle().saveAllNow();
        saveEngineData();
    }

    SeedManager getSeedManager();

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
    default IrisRegion getRegion(int x, int z) {
        return getComplex().getRegionStream().get(x, z);
    }

    void generateMatter(int x, int z, boolean multicore, ChunkContext context);

    @BlockCoordinates
    default IrisBiome getCaveOrMantleBiome(int x, int y, int z) {
        MatterCavern m = getMantle().getMantle().get(x, y, z, MatterCavern.class);

        if (m != null && m.getCustomBiome() != null && !m.getCustomBiome().isEmpty()) {
            IrisBiome biome = getData().getBiomeLoader().load(m.getCustomBiome());

            if (biome != null) {
                return biome;
            }
        }

        return getCaveBiome(x, z);
    }

    @ChunkCoordinates
    Set<String> getObjectsAt(int x, int z);

    @ChunkCoordinates
    Set<Pair<String, BlockPos>> getPOIsAt(int x, int z);

    @ChunkCoordinates
    IrisJigsawStructure getStructureAt(int x, int z);

    @BlockCoordinates
    default IrisBiome getCaveBiome(int x, int z) {
        return getComplex().getCaveBiomeStream().get(x, z);
    }

    @BlockCoordinates
    default IrisBiome getSurfaceBiome(int x, int z) {
        return getComplex().getTrueBiomeStream().get(x, z);
    }

    @BlockCoordinates
    default int getHeight(int x, int z) {
        return getHeight(x, z, true);
    }

    @BlockCoordinates
    default int getHeight(int x, int z, boolean ignoreFluid) {
        return getMantle().getHighest(x, z, getData(), ignoreFluid);
    }

    @BlockCoordinates
    @Override
    default void catchBlockUpdates(int x, int y, int z, BlockData data) {
        if (data == null) {
            return;
        }

        if (B.isUpdatable(data)) {
            getMantle().updateBlock(x, y, z);
        }
    }

    void blockUpdatedMetric();

    @ChunkCoordinates
    @Override
    default void updateChunk(Chunk c) {
        if (c.getWorld().isChunkLoaded(c.getX() + 1, c.getZ() + 1)
                && c.getWorld().isChunkLoaded(c.getX(), c.getZ() + 1)
                && c.getWorld().isChunkLoaded(c.getX() + 1, c.getZ())
                && c.getWorld().isChunkLoaded(c.getX() - 1, c.getZ() - 1)
                && c.getWorld().isChunkLoaded(c.getX(), c.getZ() - 1)
                && c.getWorld().isChunkLoaded(c.getX() - 1, c.getZ())
                && c.getWorld().isChunkLoaded(c.getX() + 1, c.getZ() - 1)
                && c.getWorld().isChunkLoaded(c.getX() - 1, c.getZ() + 1) && getMantle().getMantle().isLoaded(c)) {

            getMantle().getMantle().raiseFlag(c.getX(), c.getZ(), MantleFlag.TILE, () -> J.s(() -> {
                getMantle().getMantle().iterateChunk(c.getX(), c.getZ(), TileWrapper.class, (x, y, z, tile) -> {
                    int betterY = y + getWorld().minHeight();
                    if (!TileData.setTileState(c.getBlock(x, betterY, z), tile.getData()))
                        Iris.warn("Failed to set tile entity data at [%d %d %d | %s] for tile %s!", x, betterY, z, c.getBlock(x, betterY, z).getBlockData().getMaterial().getKey(), tile.getData().getTileId());
                });
            }));

            getMantle().getMantle().raiseFlag(c.getX(), c.getZ(), MantleFlag.UPDATE, () -> J.s(() -> {
                PrecisionStopwatch p = PrecisionStopwatch.start();
                KMap<Long, Integer> updates = new KMap<>();
                RNG r = new RNG(Cache.key(c.getX(), c.getZ()));
                getMantle().getMantle().iterateChunk(c.getX(), c.getZ(), MatterCavern.class, (x, yf, z, v) -> {
                    int y = yf + getWorld().minHeight();
                    if (!B.isFluid(c.getBlock(x & 15, y, z & 15).getBlockData())) {
                        return;
                    }
                    boolean u = false;
                    if (B.isAir(c.getBlock(x & 15, y, z & 15).getRelative(BlockFace.DOWN).getBlockData())) {
                        u = true;
                    } else if (B.isAir(c.getBlock(x & 15, y, z & 15).getRelative(BlockFace.WEST).getBlockData())) {
                        u = true;
                    } else if (B.isAir(c.getBlock(x & 15, y, z & 15).getRelative(BlockFace.EAST).getBlockData())) {
                        u = true;
                    } else if (B.isAir(c.getBlock(x & 15, y, z & 15).getRelative(BlockFace.SOUTH).getBlockData())) {
                        u = true;
                    } else if (B.isAir(c.getBlock(x & 15, y, z & 15).getRelative(BlockFace.NORTH).getBlockData())) {
                        u = true;
                    }

                    if (u) {
                        updates.compute(Cache.key(x & 15, z & 15), (k, vv) -> {
                            if (vv != null) {
                                return Math.max(vv, y);
                            }

                            return y;
                        });
                    }
                });

                updates.forEach((k, v) -> update(Cache.keyX(k), v, Cache.keyZ(k), c, r));
                getMantle().getMantle().iterateChunk(c.getX(), c.getZ(), MatterUpdate.class, (x, yf, z, v) -> {
                    int y = yf + getWorld().minHeight();
                    if (v != null && v.isUpdate()) {
                        int vx = x & 15;
                        int vz = z & 15;
                        update(x, y, z, c, new RNG(Cache.key(c.getX(), c.getZ())));
                        if (vx > 0 && vx < 15 && vz > 0 && vz < 15) {
                            updateLighting(x, y, z, c);
                        }
                    }
                });
                getMantle().getMantle().deleteChunkSlice(c.getX(), c.getZ(), MatterUpdate.class);
                getMetrics().getUpdates().put(p.getMilliseconds());
            }, RNG.r.i(0, 20)));
        }
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
            }
        }
    }

    @BlockCoordinates
    @Override
    default void update(int x, int y, int z, Chunk c, RNG rf) {
        Block block = c.getBlock(x, y, z);
        BlockData data = block.getBlockData();
        blockUpdatedMetric();
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
        } else {
            block.setType(Material.AIR, false);
            block.setBlockData(data, true);
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
        int ry = b.getY() - getWorld().minHeight();
        double he = getComplex().getHeightStream().get(rx, rz);
        KList<IrisLootTable> tables = new KList<>();

        PlacedObject po = getObjectPlacement(rx, ry, rz);
        if (po != null && po.getPlacement() != null) {
            if (B.isStorageChest(b.getBlockData())) {
                IrisLootTable table = po.getPlacement().getTable(b.getBlockData(), getData());
                if (table != null) {
                    tables.add(table);
                    if (po.getPlacement().isOverrideGlobalLoot()) {
                        return new KList<>(table);
                    }
                }
            }
        }

        IrisRegion region = getComplex().getRegionStream().get(rx, rz);
        IrisBiome biomeSurface = getComplex().getTrueBiomeStream().get(rx, rz);
        IrisBiome biomeUnder = ry < he ? getComplex().getCaveBiomeStream().get(rx, rz) : biomeSurface;

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
            if (i == null)
                continue;
            b++;
            items.addAll(i.getLoot(debug, rng, slot, x, y, z));
        }

        if (PaperLib.isPaper() && getWorld().hasRealWorld()) {
            PaperLib.getChunkAtAsync(getWorld().realWorld(), x >> 4, z >> 4).thenAccept((c) -> {
                Runnable r = () -> {
                    for (ItemStack i : items) {
                        inv.addItem(i);
                    }

                    scramble(inv, rng);
                };

                if (Bukkit.isPrimaryThread()) {
                    r.run();
                } else {
                    J.s(r);
                }
            });
        } else {
            for (ItemStack i : items) {
                inv.addItem(i);
            }

            scramble(inv, rng);
        }
    }

    EngineEffects getEffects();

    default MultiBurst burst() {
        return getTarget().getBurster();
    }

    default void clean() {
        burst().lazy(() -> getMantle().trim());
    }

    @BlockCoordinates
    default IrisBiome getBiome(Location l) {
        return getBiome(l.getBlockX(), l.getBlockY() - getWorld().minHeight(), l.getBlockZ());
    }

    @BlockCoordinates
    default IrisRegion getRegion(Location l) {
        return getRegion(l.getBlockX(), l.getBlockZ());
    }

    IrisBiome getFocus();

    IrisRegion getFocusRegion();


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

        return v.v();
    }

    int getGenerated();

    default <T> IrisPosition lookForStreamResult(T find, ProceduralStream<T> stream, Function2<T, T, Boolean> matcher, long timeout) {
        AtomicInteger checked = new AtomicInteger();
        AtomicLong time = new AtomicLong(M.ms());
        AtomicReference<IrisPosition> r = new AtomicReference<>();
        BurstExecutor b = burst().burst();

        while (M.ms() - time.get() < timeout && r.get() == null) {
            b.queue(() -> {
                for (int i = 0; i < 1000; i++) {
                    if (M.ms() - time.get() > timeout) {
                        return;
                    }

                    int x = RNG.r.i(-29999970, 29999970);
                    int z = RNG.r.i(-29999970, 29999970);
                    checked.incrementAndGet();
                    if (matcher.apply(stream.get(x, z), find)) {
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

    default int getHeight() {
        return getWorld().getHeight();
    }

    boolean isStudio();

    default IrisBiome getBiome(int x, int y, int z) {
        if (y <= getHeight(x, z) - 2) {
            return getCaveBiome(x, z);
        }

        return getSurfaceBiome(x, z);
    }

    default IrisBiome getBiomeOrMantle(int x, int y, int z) {
        if (y <= getHeight(x, z) - 2) {
            return getCaveOrMantleBiome(x, y, z);
        }

        return getSurfaceBiome(x, z);
    }

    default String getObjectPlacementKey(int x, int y, int z) {
        PlacedObject o = getObjectPlacement(x, y, z);

        if (o != null && o.getObject() != null) {
            return o.getObject().getLoadKey() + "@" + o.getId();
        }

        return null;
    }

    default PlacedObject getObjectPlacement(int x, int y, int z) {
        String objectAt = getMantle().getMantle().get(x, y, z, String.class);
        if (objectAt == null || objectAt.isEmpty()) {
            return null;
        }

        String[] v = objectAt.split("\\Q@\\E");
        String object = v[0];
        int id = Integer.parseInt(v[1]);
        IrisRegion region = getRegion(x, z);

        for (IrisObjectPlacement i : region.getObjects()) {
            if (i.getPlace().contains(object)) {
                return new PlacedObject(i, getData().getObjectLoader().load(object), id, x, z);
            }
        }

        IrisBiome biome = getBiome(x, y, z);

        for (IrisObjectPlacement i : biome.getObjects()) {
            if (i.getPlace().contains(object)) {
                return new PlacedObject(i, getData().getObjectLoader().load(object), id, x, z);
            }
        }

        return new PlacedObject(null, getData().getObjectLoader().load(object), id, x, z);
    }


    int getCacheID();

    default IrisBiome getBiomeOrMantle(Location l) {
        return getBiomeOrMantle(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    default void gotoBiome(IrisBiome biome, Player player) {
        Set<String> regionKeys = getDimension()
                .getAllRegions(this).stream()
                .filter((i) -> i.getAllBiomes(this).contains(biome))
                .map(IrisRegistrant::getLoadKey)
                .collect(Collectors.toSet());
        Locator<IrisBiome> lb = Locator.surfaceBiome(biome.getLoadKey());
        Locator<IrisBiome> locator = (engine, chunk)
                -> regionKeys.contains(getRegion((chunk.getX() << 4) + 8, (chunk.getZ() << 4) + 8).getLoadKey())
                && lb.matches(engine, chunk);

        if (!regionKeys.isEmpty()) {
            locator.find(player);
        } else {
            player.sendMessage(C.RED + biome.getName() + " is not in any defined regions!");
        }
    }

    default void gotoJigsaw(IrisJigsawStructure s, Player player) {
        if (s.getLoadKey().equals(getDimension().getStronghold())) {
            KList<Position2> p = getDimension().getStrongholds(getSeedManager().getSpawn());

            if (p.isEmpty()) {
                player.sendMessage(C.GOLD + "No strongholds in world.");
            }

            Position2 px = new Position2(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
            Position2 pr = null;
            double d = Double.MAX_VALUE;

            Iris.debug("Ps: " + p.size());

            for (Position2 i : p) {
                Iris.debug("- " + i.getX() + " " + i.getZ());
            }

            for (Position2 i : p) {
                double dx = i.distance(px);
                if (dx < d) {
                    d = dx;
                    pr = i;
                }
            }

            if (pr != null) {
                Location ll = new Location(player.getWorld(), pr.getX(), 40, pr.getZ());
                J.s(() -> player.teleport(ll));
            }

            return;
        }

        if (getDimension().getJigsawStructures().stream()
                .map(IrisJigsawStructurePlacement::getStructure)
                .collect(Collectors.toSet()).contains(s.getLoadKey())) {
            Locator.jigsawStructure(s.getLoadKey()).find(player);
        } else {
            Set<String> biomeKeys = getDimension().getAllBiomes(this).stream()
                    .filter((i) -> i.getJigsawStructures()
                            .stream()
                            .anyMatch((j) -> j.getStructure().equals(s.getLoadKey())))
                    .map(IrisRegistrant::getLoadKey)
                    .collect(Collectors.toSet());
            Set<String> regionKeys = getDimension().getAllRegions(this).stream()
                    .filter((i) -> i.getAllBiomeIds().stream().anyMatch(biomeKeys::contains)
                            || i.getJigsawStructures()
                            .stream()
                            .anyMatch((j) -> j.getStructure().equals(s.getLoadKey())))
                    .map(IrisRegistrant::getLoadKey)
                    .collect(Collectors.toSet());

            Locator<IrisJigsawStructure> sl = Locator.jigsawStructure(s.getLoadKey());
            Locator<IrisBiome> locator = (engine, chunk) -> {
                if (biomeKeys.contains(getSurfaceBiome((chunk.getX() << 4) + 8, (chunk.getZ() << 4) + 8).getLoadKey())) {
                    return sl.matches(engine, chunk);
                } else if (regionKeys.contains(getRegion((chunk.getX() << 4) + 8, (chunk.getZ() << 4) + 8).getLoadKey())) {
                    return sl.matches(engine, chunk);
                }
                return false;
            };

            if (!regionKeys.isEmpty()) {
                locator.find(player);
            } else {
                player.sendMessage(C.RED + s.getLoadKey() + " is not in any defined regions, biomes or dimensions!");
            }
        }

    }

    default void gotoObject(String s, Player player) {
        Set<String> biomeKeys = getDimension().getAllBiomes(this).stream()
                .filter((i) -> i.getObjects().stream().anyMatch((f) -> f.getPlace().contains(s)))
                .map(IrisRegistrant::getLoadKey)
                .collect(Collectors.toSet());
        Set<String> regionKeys = getDimension().getAllRegions(this).stream()
                .filter((i) -> i.getAllBiomeIds().stream().anyMatch(biomeKeys::contains)
                        || i.getObjects().stream().anyMatch((f) -> f.getPlace().contains(s)))
                .map(IrisRegistrant::getLoadKey)
                .collect(Collectors.toSet());

        Locator<IrisObject> sl = Locator.object(s);
        Locator<IrisBiome> locator = (engine, chunk) -> {
            if (biomeKeys.contains(getSurfaceBiome((chunk.getX() << 4) + 8, (chunk.getZ() << 4) + 8).getLoadKey())) {
                return sl.matches(engine, chunk);
            } else if (regionKeys.contains(getRegion((chunk.getX() << 4) + 8, (chunk.getZ() << 4) + 8).getLoadKey())) {
                return sl.matches(engine, chunk);
            }

            return false;
        };

        if (!regionKeys.isEmpty()) {
            locator.find(player);
        } else {
            player.sendMessage(C.RED + s + " is not in any defined regions or biomes!");
        }
    }

    default void gotoRegion(IrisRegion r, Player player) {
        if (!getDimension().getAllRegions(this).contains(r)) {
            player.sendMessage(C.RED + r.getName() + " is not defined in the dimension!");
            return;
        }

        Locator.region(r.getLoadKey()).find(player);
    }

    default void gotoPOI(String type, Player p) {
        Locator.poi(type).find(p);
    }

    default void cleanupMantleChunk(int x, int z) {
        if (IrisSettings.get().getPerformance().isTrimMantleInStudio() || !isStudio()) {
            J.a(() -> getMantle().cleanupChunk(x, z));
        }
    }
}
