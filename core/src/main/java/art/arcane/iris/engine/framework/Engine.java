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

package art.arcane.iris.engine.framework;

import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.events.IrisLootEvent;
import art.arcane.iris.core.gui.components.RenderType;
import art.arcane.iris.core.gui.components.Renderer;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.nms.container.BlockPos;
import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.core.pregenerator.ChunkUpdater;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.data.cache.Cache;
import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.*;
import art.arcane.iris.util.project.matter.TileWrapper;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.common.data.DataProvider;
import art.arcane.iris.util.common.data.IrisCustomData;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.function.Function2;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterUpdate;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.common.reflect.KeyedType;
import art.arcane.iris.util.common.reflect.W;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.iris.util.project.stream.ProceduralStream;
import io.papermc.lib.PaperLib;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
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

    default void setMinHeight(int min) {
        getTarget().getWorld().minHeight(min);
    }

    @BlockCoordinates
    default void generate(int x, int z, TerrainChunk tc, boolean multicore) throws WrongEngineBroException {
        generate(x, z, Hunk.view(tc), Hunk.viewBiomes(tc), multicore);
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

        return getCaveBiome(x, y, z);
    }

    @ChunkCoordinates
    Set<String> getObjectsAt(int x, int z);

    @ChunkCoordinates
    Set<Pair<String, BlockPos>> getPOIsAt(int x, int z);

    @BlockCoordinates
    default IrisBiome getCaveBiome(int x, int z) {
        return getComplex().getCaveBiomeStream().get(x, z);
    }

    @BlockCoordinates
    default IrisBiome getCaveBiome(int x, int y, int z) {
        return getCaveBiome(x, y, z, null);
    }

    @BlockCoordinates
    default IrisBiome getCaveBiome(int x, int y, int z, IrisDimensionCarvingResolver.State state) {
        IrisBiome surfaceBiome = getSurfaceBiome(x, z);
        int worldY = y + getWorld().minHeight();
        IrisDimensionCarvingEntry rootCarvingEntry = IrisDimensionCarvingResolver.resolveRootEntry(this, worldY, state);
        if (rootCarvingEntry != null) {
            IrisDimensionCarvingEntry resolvedCarvingEntry = IrisDimensionCarvingResolver.resolveFromRoot(this, rootCarvingEntry, x, z, state);
            IrisBiome resolvedCarvingBiome = IrisDimensionCarvingResolver.resolveEntryBiome(this, resolvedCarvingEntry, state);
            if (resolvedCarvingBiome != null) {
                return resolvedCarvingBiome;
            }
        }

        IrisBiome caveBiome = getCaveBiome(x, z);
        if (caveBiome == null) {
            return surfaceBiome;
        }

        int surfaceY = getComplex().getHeightStream().get(x, z).intValue();
        int depthBelowSurface = surfaceY - y;
        if (depthBelowSurface <= 0) {
            return surfaceBiome;
        }

        int minDepth = Math.max(0, caveBiome.getCaveMinDepthBelowSurface());
        if (depthBelowSurface < minDepth) {
            return surfaceBiome;
        }

        return caveBiome;
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
        if (data instanceof IrisCustomData) {
            getMantle().getMantle().flag(x >> 4, z >> 4, MantleFlag.CUSTOM_ACTIVE, true);
        }
    }

    void blockUpdatedMetric();

    @ChunkCoordinates
    @Override
    default void updateChunk(Chunk c) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (c.getWorld().isChunkLoaded(c.getX() + x, c.getZ() + z))
                    continue;
                var msg = "Chunk %s, %s [%s, %s] is not loaded".formatted(c.getX() + x, c.getZ() + z, x, z);
                if (W.getStack().getCallerClass().equals(ChunkUpdater.class)) Iris.warn(msg);
                else Iris.debug(msg);
                return;
            }
        }
        var mantle = getMantle().getMantle();
        if (!mantle.isLoaded(c)) {
            var msg = "Mantle Chunk " + c.getX() + "," + c.getZ() + " is not loaded";
            if (W.getStack().getCallerClass().equals(ChunkUpdater.class)) Iris.warn(msg);
            else Iris.debug(msg);
            return;
        }

        if (!J.isFolia() && !J.isPrimaryThread()) {
            CompletableFuture<?> scheduled = J.sfut(() -> updateChunk(c));
            if (scheduled != null) {
                try {
                    scheduled.join();
                } catch (Throwable e) {
                    Iris.reportError(e);
                }
            }
            return;
        }

        var chunk = mantle.getChunk(c).use();
        try {
            Runnable tileTask = () -> {
                chunk.iterate(TileWrapper.class, (x, y, z, v) -> {
                    Block block = c.getBlock(x & 15, y + getWorld().minHeight(), z & 15);
                    if (!TileData.setTileState(block, v.getData())) {
                        NamespacedKey blockTypeKey = KeyedType.getKey(block.getType());
                        NamespacedKey tileTypeKey = KeyedType.getKey(v.getData().getMaterial());
                        String blockType = blockTypeKey == null ? block.getType().name() : blockTypeKey.toString();
                        String tileType = tileTypeKey == null ? v.getData().getMaterial().name() : tileTypeKey.toString();
                        Iris.warn("Failed to set tile entity data at [%d %d %d | %s] for tile %s!", block.getX(), block.getY(), block.getZ(), blockType, tileType);
                    }
                });
            };

            Runnable customTask = () -> {
                chunk.iterate(Identifier.class, (x, y, z, v) -> {
                    Iris.service(ExternalDataSVC.class).processUpdate(this, c.getBlock(x & 15, y + getWorld().minHeight(), z & 15), v);
                });
            };

            Runnable updateTask = () -> {
                PrecisionStopwatch p = PrecisionStopwatch.start();
                int[][] grid = new int[16][16];
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        grid[x][z] = Integer.MIN_VALUE;
                    }
                }

                RNG rng = new RNG(Cache.key(c.getX(), c.getZ()));
                chunk.iterate(MatterCavern.class, (x, yf, z, v) -> {
                    int y = yf + getWorld().minHeight();
                    x &= 15;
                    z &= 15;
                    Block block = c.getBlock(x, y, z);
                    if (!B.isFluid(block.getBlockData())) {
                        return;
                    }
                    boolean u = B.isAir(block.getRelative(BlockFace.DOWN).getBlockData())
                            || B.isAir(block.getRelative(BlockFace.WEST).getBlockData())
                            || B.isAir(block.getRelative(BlockFace.EAST).getBlockData())
                            || B.isAir(block.getRelative(BlockFace.SOUTH).getBlockData())
                            || B.isAir(block.getRelative(BlockFace.NORTH).getBlockData());

                    if (u) grid[x][z] = Math.max(grid[x][z], y);
                });

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (grid[x][z] == Integer.MIN_VALUE) {
                            continue;
                        }
                        update(x, grid[x][z], z, c, chunk, rng);
                    }
                }

                chunk.iterate(MatterUpdate.class, (x, yf, z, v) -> {
                    int y = yf + getWorld().minHeight();
                    if (v != null && v.isUpdate()) {
                        update(x, y, z, c, chunk, rng);
                    }
                });
                chunk.deleteSlices(MatterUpdate.class);
                getMetrics().getUpdates().put(p.getMilliseconds());
            };

            if (shouldRunChunkUpdateInline(c)) {
                chunk.raiseFlagUnchecked(MantleFlag.ETCHED, () -> {
                    chunk.raiseFlagUnchecked(MantleFlag.TILE, tileTask);
                    chunk.raiseFlagUnchecked(MantleFlag.CUSTOM, customTask);
                    chunk.raiseFlagUnchecked(MantleFlag.UPDATE, updateTask);
                });
                return;
            }

            Semaphore semaphore = new Semaphore(1024);
            chunk.raiseFlagUnchecked(MantleFlag.ETCHED, () -> {
                chunk.raiseFlagUnchecked(MantleFlag.TILE, run(semaphore, c, tileTask, 0));
                chunk.raiseFlagUnchecked(MantleFlag.CUSTOM, run(semaphore, c, customTask, 0));
                chunk.raiseFlagUnchecked(MantleFlag.UPDATE, run(semaphore, c, updateTask, RNG.r.i(1, 20)));
            });

            try {
                semaphore.acquire(1024);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                Iris.reportError(ex);
            }
        } finally {
            chunk.release();
        }
    }

    private static boolean shouldRunChunkUpdateInline(Chunk chunk) {
        if (chunk == null) {
            return false;
        }

        if (!J.isFolia()) {
            return true;
        }

        return J.isOwnedByCurrentRegion(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    private static Runnable run(Semaphore semaphore, Chunk contextChunk, Runnable runnable, int delay) {
        return () -> {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            int effectiveDelay = J.isFolia() ? 0 : delay;
            boolean scheduled = J.runRegion(contextChunk.getWorld(), contextChunk.getX(), contextChunk.getZ(), () -> {
                try {
                    runnable.run();
                } finally {
                    semaphore.release();
                }
            }, effectiveDelay);

            if (!scheduled) {
                try {
                    if (J.isPrimaryThread()) {
                        runnable.run();
                    }
                } finally {
                    semaphore.release();
                }
            }
        };
    }

    @BlockCoordinates
    @Override

    default void update(int x, int y, int z, Chunk c, MantleChunk<Matter> mc, RNG rf) {
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
                KList<IrisLootTable> tables = getLootTables(rx, block, mc);

                try {
                    Bukkit.getPluginManager().callEvent(new IrisLootEvent(this, block, slot, tables));
                    if (tables.isEmpty()) return;
                    InventoryHolder m = (InventoryHolder) block.getState();
                    addItems(false, m.getInventory(), rx, tables, slot, c.getWorld(), x, y, z, 15);

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

        for (int i = nitems.length; i > 1; i--) {
            int j = rng.nextInt(i);
            ItemStack tmp = nitems[i - 1];
            nitems[i - 1] = nitems[j];
            nitems[j] = tmp;
        }

        inventory.setContents(nitems);
    }

    @Override
    default void injectTables(KList<IrisLootTable> list, IrisLootReference r, boolean fallback) {
        if (r.getMode().equals(IrisLootMode.FALLBACK) && !fallback)
            return;

        if (r.getMode().equals(IrisLootMode.CLEAR) || r.getMode().equals(IrisLootMode.REPLACE)) {
            list.clear();
        }

        list.addAll(r.getLootTables(getComplex()));
    }

    @BlockCoordinates
    @Override
    default KList<IrisLootTable> getLootTables(RNG rng, Block b) {
        MantleChunk<Matter> mc = getMantle().getMantle().getChunk(b.getChunk()).use();
        try {
            return getLootTables(rng, b, mc);
        } finally {
            mc.release();
        }
    }

    @BlockCoordinates
    default KList<IrisLootTable> getLootTables(RNG rng, Block b, MantleChunk<Matter> mc) {
        int rx = b.getX();
        int rz = b.getZ();
        int ry = b.getY() - getWorld().minHeight();
        double he = getComplex().getHeightStream().get(rx, rz);
        KList<IrisLootTable> tables = new KList<>();

        PlacedObject po = getObjectPlacement(rx, ry, rz, mc);
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
        IrisBiome biomeUnder = ry < he ? getCaveBiome(rx, ry, rz) : biomeSurface;

        double multiplier = 1D * getDimension().getLoot().getMultiplier() * region.getLoot().getMultiplier() * biomeSurface.getLoot().getMultiplier() * biomeUnder.getLoot().getMultiplier();
        boolean fallback = tables.isEmpty();
        injectTables(tables, getDimension().getLoot(), fallback);
        injectTables(tables, region.getLoot(), fallback);
        injectTables(tables, biomeSurface.getLoot(), fallback);
        injectTables(tables, biomeUnder.getLoot(), fallback);

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
    default void addItems(boolean debug, Inventory inv, RNG rng, KList<IrisLootTable> tables, InventorySlotType slot, World world, int x, int y, int z, int mgf) {
        KList<ItemStack> items = new KList<>();

        for (IrisLootTable i : tables) {
            if (i == null)
                continue;
            items.addAll(i.getLoot(debug, rng, slot, world, x, y, z));
        }
        if (IrisLootEvent.callLootEvent(items, inv, world, x, y, z))
            return;

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
        burst().lazy(() -> getMantle().trim(10));
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

    CompletableFuture<Long> getHash32();

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
        if (!getWorld().hasRealWorld()) {
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
            return getCaveBiome(x, y, z);
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
        MantleChunk<Matter> chunk = getMantle().getMantle().getChunk(x >> 4, z >> 4).use();
        try {
            return getObjectPlacement(x, y, z, chunk);
        } finally {
            chunk.release();
        }
    }

    default PlacedObject getObjectPlacement(int x, int y, int z, MantleChunk<Matter> chunk) {
        String objectAt = chunk.get(x & 15, y, z & 15, String.class);
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

        IrisBiome biome = getSurfaceBiome(x, z);

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

    default void gotoBiome(IrisBiome biome, Player player, boolean teleport) {
        Set<String> regionKeys = getDimension()
                .getAllRegions(this).stream()
                .filter((i) -> i.getAllBiomeIds().contains(biome.getLoadKey()))
                .map(IrisRegistrant::getLoadKey)
                .collect(Collectors.toSet());
        Locator<IrisBiome> lb = Locator.surfaceBiome(biome.getLoadKey());
        Locator<IrisBiome> locator = (engine, chunk)
                -> regionKeys.contains(getRegion((chunk.getX() << 4) + 8, (chunk.getZ() << 4) + 8).getLoadKey())
                && lb.matches(engine, chunk);

        if (!regionKeys.isEmpty()) {
            locator.find(player, teleport, "Biome " + biome.getName());
        } else {
            player.sendMessage(C.RED + biome.getName() + " is not in any defined regions!");
        }
    }

    default void gotoObject(String s, Player player, boolean teleport) {
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
            locator.find(player, teleport, "Object " + s);
        } else {
            player.sendMessage(C.RED + s + " is not in any defined regions or biomes!");
        }
    }

    default boolean hasObjectPlacement(String objectKey) {
        String normalizedObjectKey = normalizeObjectPlacementKey(objectKey);
        if (normalizedObjectKey.isBlank()) {
            return false;
        }

        Set<String> biomeKeys = getDimension().getAllBiomes(this).stream()
                .filter((i) -> containsObjectPlacement(i.getObjects(), normalizedObjectKey))
                .map(IrisRegistrant::getLoadKey)
                .collect(Collectors.toSet());
        Set<String> regionKeys = getDimension().getAllRegions(this).stream()
                .filter((i) -> i.getAllBiomeIds().stream().anyMatch(biomeKeys::contains)
                        || containsObjectPlacement(i.getObjects(), normalizedObjectKey))
                .map(IrisRegistrant::getLoadKey)
                .collect(Collectors.toSet());
        return !regionKeys.isEmpty();
    }

    default void gotoRegion(IrisRegion r, Player player, boolean teleport) {
        if (!getDimension().getRegions().contains(r.getLoadKey())) {
            player.sendMessage(C.RED + r.getName() + " is not defined in the dimension!");
            return;
        }

        Locator.region(r.getLoadKey()).find(player, teleport, "Region " + r.getName());
    }

    default void gotoPOI(String type, Player p, boolean teleport) {
        Locator.poi(type).find(p, teleport, "POI " + type);
    }

    private static boolean containsObjectPlacement(KList<IrisObjectPlacement> placements, String normalizedObjectKey) {
        if (placements == null || placements.isEmpty() || normalizedObjectKey.isBlank()) {
            return false;
        }

        for (IrisObjectPlacement placement : placements) {
            if (placement == null || placement.getPlace() == null || placement.getPlace().isEmpty()) {
                continue;
            }

            for (String placedObject : placement.getPlace()) {
                String normalizedPlacedObject = normalizeObjectPlacementKey(placedObject);
                if (!normalizedPlacedObject.isBlank() && normalizedPlacedObject.equals(normalizedObjectKey)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static String normalizeObjectPlacementKey(String objectKey) {
        if (objectKey == null) {
            return "";
        }

        String normalized = objectKey.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(".iob")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    default void cleanupMantleChunk(int x, int z) {
        World world = getWorld().realWorld();
        if (world != null && IrisToolbelt.isWorldMaintenanceActive(world)) {
            return;
        }
        if (IrisSettings.get().getPerformance().isTrimMantleInStudio() || !isStudio()) {
            getMantle().cleanupChunk(x, z);
        }
    }
}
