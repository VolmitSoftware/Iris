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

package art.arcane.iris.engine.platform;

import art.arcane.iris.engine.history.GenerationFindCatalog;

import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.core.events.IrisLootEvent;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.engine.framework.Locator;
import art.arcane.iris.engine.framework.LootResolver;
import art.arcane.iris.engine.framework.PlacedObject;
import art.arcane.iris.engine.framework.WrongEngineBroException;
import art.arcane.iris.engine.object.InventorySlotType;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisLootTable;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.TileData;
import art.arcane.iris.platform.bukkit.BukkitBlockResolution;
import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.reflect.KeyedType;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.iris.util.common.scheduling.jobs.SingleJob;
import art.arcane.iris.util.project.matter.TileWrapper;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterUpdate;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import io.papermc.lib.PaperLib;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.Lootable;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import art.arcane.iris.core.localization.BukkitRuntimeMessages;
import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.localization.RuntimeUiMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
public final class EngineBukkitOps {
    private static final ConcurrentHashMap<UUID, CompletableFuture<Position2>> ACTIVE_LOCATE_REQUESTS = new ConcurrentHashMap<>();

    private EngineBukkitOps() {
    }

    public static void updateChunk(Engine engine, Chunk c) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (c.getWorld().isChunkLoaded(c.getX() + x, c.getZ() + z))
                    continue;
                String msg = "Chunk %s, %s [%s, %s] is not loaded".formatted(c.getX() + x, c.getZ() + z, x, z);
                IrisLogging.debug(msg);
                return;
            }
        }
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        if (!mantle.isLoaded(c)) {
            String msg = "Mantle Chunk " + c.getX() + "," + c.getZ() + " is not loaded";
            IrisLogging.debug(msg);
            return;
        }

        if (!J.isFolia() && !J.isPrimaryThread()) {
            CompletableFuture<?> scheduled = J.sfut(() -> updateChunk(engine, c));
            if (scheduled != null) {
                try {
                    scheduled.join();
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                }
            }
            return;
        }

        MantleChunk<Matter> chunk = mantle.getChunk(c).use();
        try {
            Runnable tileTask = () -> materializeTiles(engine, c, chunk);
            Runnable customTask = () -> materializeCustomBlocks(engine, c, chunk);
            Runnable updateTask = () -> materializeUpdates(engine, c, chunk);

            if (shouldRunChunkUpdateInline(c)) {
                runMaterializationPasses(chunk, tileTask, customTask, updateTask);
                return;
            }

            runScheduledMaterializationPasses(c, () -> runMaterializationPasses(chunk, tileTask, customTask, updateTask));
        } finally {
            chunk.release();
        }
    }

    static void materializeTiles(Engine engine, Chunk chunk, MantleChunk<Matter> mantleChunk) {
        mantleChunk.iterate(TileWrapper.class, (x, y, z, value) -> {
            int worldY = y + engine.getWorld().minHeight();
            if (worldY < chunk.getWorld().getMinHeight() || worldY >= chunk.getWorld().getMaxHeight()) {
                return;
            }
            Block block = chunk.getBlock(x & 15, worldY, z & 15);
            if (!TileData.setTileState(block, value.getData())) {
                NamespacedKey blockTypeKey = KeyedType.getKey(block.getType());
                String blockType = blockTypeKey == null ? block.getType().name() : blockTypeKey.toString();
                String tileType = value.getData().getMaterialKey();
                IrisLogging.warn("Failed to set tile entity data at [%d %d %d | %s] for tile %s!", block.getX(), block.getY(), block.getZ(), blockType, tileType);
            }
        });
        mantleChunk.deleteSlices(TileWrapper.class);
    }

    static void materializeCustomBlocks(Engine engine, Chunk chunk, MantleChunk<Matter> mantleChunk) {
        mantleChunk.iterate(Identifier.class, (x, y, z, value) -> {
            int worldY = y + engine.getWorld().minHeight();
            if (worldY < chunk.getWorld().getMinHeight() || worldY >= chunk.getWorld().getMaxHeight()) {
                return;
            }
            IrisServices.get(ExternalDataSVC.class).processUpdate(
                    engine,
                    chunk.getBlock(x & 15, worldY, z & 15),
                    value
            );
        });
        mantleChunk.deleteSlices(Identifier.class);
    }

    static void materializeUpdates(Engine engine, Chunk chunk, MantleChunk<Matter> mantleChunk) {
        materializeUpdates(
                engine,
                chunk,
                mantleChunk,
                (x, y, z) -> update(engine, x, y, z, chunk, mantleChunk)
        );
    }

    static void materializeUpdates(
            Engine engine,
            Chunk chunk,
            MantleChunk<Matter> mantleChunk,
            UpdateDispatcher dispatcher
    ) {
        PrecisionStopwatch stopwatch = PrecisionStopwatch.start();
        int[][] grid = new int[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                grid[x][z] = Integer.MIN_VALUE;
            }
        }

        mantleChunk.iterate(MatterCavern.class, (x, yf, z, value) -> {
            int y = yf + engine.getWorld().minHeight();
            x &= 15;
            z &= 15;
            Block block = chunk.getBlock(x, y, z);
            if (!BukkitBlockResolution.isFluid(block.getBlockData())) {
                return;
            }
            boolean exposed = BukkitBlockResolution.isAir(block.getRelative(BlockFace.DOWN).getBlockData())
                    || BukkitBlockResolution.isAir(block.getRelative(BlockFace.WEST).getBlockData())
                    || BukkitBlockResolution.isAir(block.getRelative(BlockFace.EAST).getBlockData())
                    || BukkitBlockResolution.isAir(block.getRelative(BlockFace.SOUTH).getBlockData())
                    || BukkitBlockResolution.isAir(block.getRelative(BlockFace.NORTH).getBlockData());

            if (exposed) {
                grid[x][z] = Math.max(grid[x][z], y);
            }
        });

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (grid[x][z] == Integer.MIN_VALUE) {
                    continue;
                }
                dispatcher.update(x, grid[x][z], z);
            }
        }

        IntArrayList completed = new IntArrayList();
        try {
            mantleChunk.iterate(MatterUpdate.class, (x, yf, z, value) -> {
                int y = yf + engine.getWorld().minHeight();
                if (value != null && value.isUpdate()) {
                    dispatcher.update(x, y, z);
                    completed.add((yf << 8) | ((x & 15) << 4) | (z & 15));
                }
            });
        } catch (RuntimeException | Error failure) {
            try {
                for (int index = 0; index < completed.size(); index++) {
                    int position = completed.getInt(index);
                    int y = position >>> 8;
                    Matter section = mantleChunk.get(y >> 4);
                    if (section != null && section.hasSlice(MatterUpdate.class)) {
                        section.<MatterUpdate>getSlice(MatterUpdate.class)
                                .set((position >> 4) & 15, y & 15, position & 15, null);
                    }
                }
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
        mantleChunk.deleteSlices(MatterUpdate.class);
        engine.getMetrics().getUpdates().put(stopwatch.getMilliseconds());
    }

    static void runMaterializationPasses(
            MantleChunk<Matter> chunk,
            Runnable tileTask,
            Runnable customTask,
            Runnable updateTask
    ) {
        chunk.raiseFlagUnchecked(MantleFlag.ETCHED, () -> {
            chunk.raiseFlagUnchecked(MantleFlag.TILE, tileTask);
            chunk.raiseFlagUnchecked(MantleFlag.CUSTOM, customTask);
            chunk.raiseFlagUnchecked(MantleFlag.UPDATE, updateTask);
        });
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

    private static void runScheduledMaterializationPasses(Chunk contextChunk, Runnable runnable) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        boolean scheduled = J.runRegion(contextChunk.getWorld(), contextChunk.getX(), contextChunk.getZ(), () -> {
            try {
                runnable.run();
                completion.complete(null);
            } catch (Throwable error) {
                completion.completeExceptionally(error);
            }
        });

        if (!scheduled) {
            if (J.isPrimaryThread()) {
                runnable.run();
            }
            return;
        }

        try {
            completion.join();
        } catch (CompletionException error) {
            Throwable cause = error.getCause();
            IrisLogging.reportError(
                    "Failed deferred chunk materialization for " + contextChunk.getWorld().getName()
                            + " at chunk " + contextChunk.getX() + "," + contextChunk.getZ() + ".",
                    cause == null ? error : cause
            );
        }
    }

    public static void update(Engine engine, int x, int y, int z, Chunk c, MantleChunk<Matter> mc) {
        World world = c.getWorld();
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return;
        }
        Block block = c.getBlock(x & 15, y, z & 15);
        BlockData data = block.getBlockData();
        engine.blockUpdatedMetric();
        if (BukkitBlockResolution.isStorage(data)) {
            InventorySlotType slot = null;

            if (BukkitBlockResolution.isStorageChest(data)) {
                slot = InventorySlotType.STORAGE;
            }

            if (slot != null) {
                if (!isCanonicalContainer(block)) {
                    return;
                }
                int worldX = block.getX();
                int worldZ = block.getZ();
                RNG rx = LootResolver.containerRng(engine.getSeedManager().getLoot(), worldX, y, worldZ);
                KList<IrisLootTable> tables = getLootTables(engine, rx, block, mc, hasNativeLootTable(block));

                try {
                    Bukkit.getPluginManager().callEvent(new IrisLootEvent(engine, block, slot, tables));
                    if (tables.isEmpty()) return;
                    InventoryHolder m = (InventoryHolder) block.getState();
                    addItems(engine, false, m.getInventory(), tables, slot, c.getWorld(), worldX, y, worldZ);

                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                }
            }
        } else {
            applyPhysicsUpdate(block, data);
        }
    }

    static void applyPhysicsUpdate(Block block, BlockData data) {
        block.setType(Material.AIR, false);
        block.setBlockData(data, true);
    }

    @FunctionalInterface
    interface UpdateDispatcher {
        void update(int x, int y, int z);
    }

    public static boolean isCanonicalContainer(Block block) {
        if (!(block.getBlockData() instanceof Chest chest) || chest.getType() == Chest.Type.SINGLE) {
            return true;
        }
        BlockFace connectedFace = connectedFace(chest);
        int connectedX = block.getX() + connectedFace.getModX();
        int connectedZ = block.getZ() + connectedFace.getModZ();
        return block.getX() < connectedX || block.getX() == connectedX && block.getZ() <= connectedZ;
    }

    public static boolean hasNativeLootTable(Block block) {
        if (hasNativeLootTableAt(block)) {
            return true;
        }
        if (!(block.getBlockData() instanceof Chest chest) || chest.getType() == Chest.Type.SINGLE) {
            return false;
        }
        BlockFace connectedFace = connectedFace(chest);
        int connectedX = block.getX() + connectedFace.getModX();
        int connectedZ = block.getZ() + connectedFace.getModZ();
        if (!block.getWorld().isChunkLoaded(connectedX >> 4, connectedZ >> 4)) {
            return false;
        }
        return hasNativeLootTableAt(block.getWorld().getBlockAt(connectedX, block.getY(), connectedZ));
    }

    private static boolean hasNativeLootTableAt(Block block) {
        BlockState state = block.getState();
        return state instanceof Lootable lootable && lootable.getLootTable() != null;
    }

    private static BlockFace connectedFace(Chest chest) {
        return chest.getType() == Chest.Type.LEFT
                ? clockwise(chest.getFacing())
                : counterClockwise(chest.getFacing());
    }

    private static BlockFace clockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.SELF;
        };
    }

    private static BlockFace counterClockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> BlockFace.SELF;
        };
    }

    public static void scramble(Engine engine, Inventory inventory, RNG rng) {
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

    public static KList<IrisLootTable> getLootTables(Engine engine, RNG rng, Block b) {
        MantleChunk<Matter> mc = engine.getMantle().getMantle().getChunk(b.getChunk()).use();
        try {
            return getLootTables(engine, rng, b, mc, hasNativeLootTable(b));
        } finally {
            mc.release();
        }
    }

    public static KList<IrisLootTable> getLootTables(Engine engine, RNG rng, Block b, MantleChunk<Matter> mc) {
        return getLootTables(engine, rng, b, mc, hasNativeLootTable(b));
    }

    public static KList<IrisLootTable> getLootTables(Engine engine, RNG rng, Block b, MantleChunk<Matter> mc, boolean nativeLootDefined) {
        int rx = b.getX();
        int rz = b.getZ();
        int ry = b.getY() - engine.getWorld().minHeight();
        KList<IrisLootTable> tables = new KList<>();
        boolean objectLootDefined = nativeLootDefined;

        PlacedObject po = engine.getObjectPlacement(rx, ry, rz, mc);
        if (po != null && po.getPlacement() != null) {
            if (BukkitBlockResolution.isStorageChest(b.getBlockData())) {
                objectLootDefined = objectLootDefined
                        || po.getPlacement().getLoot().isNotEmpty()
                        || po.getPlacement().getVanillaLoot().isNotEmpty();
                IrisLootTable table = po.getPlacement().getTable(
                        BukkitBlockState.of(b.getBlockData()), engine.getData(), rng);
                if (table != null) {
                    tables.add(table);
                    if (po.getPlacement().isOverrideGlobalLoot()) {
                        return new KList<>(table);
                    }
                }
            }
        }

        LootResolver.resolveEnvironmentSources(
                tables,
                engine,
                rng,
                rx,
                ry,
                rz,
                objectLootDefined,
                table -> table
        );

        return tables;
    }

    public static void addItems(Engine engine, boolean debug, Inventory inv, KList<IrisLootTable> tables, InventorySlotType slot, World world, int x, int y, int z) {
        KList<ItemStack> items = new KList<>();

        for (IrisLootTable i : tables) {
            if (i == null)
                continue;
            items.addAll(i.getLoot(debug, engine.getSeedManager().getLoot(), slot, world, x, y, z));
        }
        if (IrisLootEvent.callLootEvent(items, inv, world, x, y, z))
            return;

        if (PaperLib.isPaper() && engine.getWorld().hasPlatformWorld()) {
            PaperLib.getChunkAtAsync(BukkitWorldBinding.world(engine.getWorld()), x >> 4, z >> 4).thenAccept((c) -> {
                Runnable mutation = () -> {
                    for (ItemStack i : items) {
                        inv.addItem(i);
                    }

                    scramble(engine, inv, LootResolver.containerRng(engine.getSeedManager().getLoot(), x, y, z));
                };

                LootMutationTask task = new LootMutationTask(world, x >> 4, z >> 4, mutation);
                boolean scheduled = dispatchLootMutation(
                        task,
                        J.isFolia(),
                        Bukkit::isPrimaryThread,
                        regionTask -> J.runRegion(
                                regionTask.world(),
                                regionTask.chunkX(),
                                regionTask.chunkZ(),
                                regionTask.mutation()),
                        J::s
                );
                if (!scheduled) {
                    IrisLogging.error("Failed to schedule loot inventory mutation for " + world.getName() + "@" + (x >> 4) + "," + (z >> 4) + ".");
                }
            });
        } else {
            for (ItemStack i : items) {
                inv.addItem(i);
            }

            scramble(engine, inv, LootResolver.containerRng(engine.getSeedManager().getLoot(), x, y, z));
        }
    }

    static boolean dispatchLootMutation(
            LootMutationTask task,
            boolean folia,
            BooleanSupplier primaryThread,
            Predicate<LootMutationTask> regionScheduler,
            Consumer<Runnable> syncScheduler) {
        if (folia) {
            return regionScheduler.test(task);
        }

        if (primaryThread.getAsBoolean()) {
            task.mutation().run();
        } else {
            syncScheduler.accept(task.mutation());
        }
        return true;
    }

    public static IrisBiome getBiome(Engine engine, Location l) {
        return engine.getBiome(l.getBlockX(), l.getBlockY() - engine.getWorld().minHeight(), l.getBlockZ());
    }

    public static IrisRegion getRegion(Engine engine, Location l) {
        return engine.getRegion(
                l.getBlockX(), l.getBlockY() - engine.getWorld().minHeight(), l.getBlockZ());
    }

    public static IrisBiome getBiomeOrMantle(Engine engine, Location l) {
        return engine.getBiomeOrMantle(
                l.getBlockX(),
                l.getBlockY() - engine.getWorld().minHeight(),
                l.getBlockZ());
    }

    public static IrisBiome getSurfaceBiome(Engine engine, Chunk c) {
        return engine.getSurfaceBiome((c.getX() << 4) + 8, (c.getZ() << 4) + 8);
    }

    public static IrisRegion getRegion(Engine engine, Chunk c) {
        return engine.getRegion((c.getX() << 4) + 8, (c.getZ() << 4) + 8);
    }

    public static void gotoBiome(Engine engine, IrisBiome biome, Player player, boolean teleport) {
        find(engine, Locator.surfaceBiome(biome.getLoadKey()), player, teleport, "Biome " + biome.getName());
    }

    public static void gotoObject(Engine engine, String s, Player player, boolean teleport) {
        find(engine, Locator.object(s), player, teleport, "Object " + s);
    }

    public static void gotoRegion(Engine engine, IrisRegion r, Player player, boolean teleport) {
        if (GenerationFindCatalog.region(engine, r.getLoadKey()) == null) {
            ComponentMessenger.sendSection(player, IrisLanguage.text(BukkitRuntimeMessages.ENGINE_BUKKIT_OPS_IS_NOT_DEFINED_DIMENSION, MessageArgument.untrusted("name", String.valueOf(r.getName()))));
            return;
        }

        find(engine, Locator.region(r.getLoadKey()), player, teleport, "Region " + r.getName());
    }

    public static void gotoPOI(Engine engine, String type, Player p, boolean teleport) {
        find(engine, Locator.poi(type), p, teleport, "POI " + type);
    }

    public static void gotoStructure(Engine engine, String key, Player player, boolean teleport) {
        find(engine, Locator.structure(key), player, teleport, "Structure " + key);
    }

    private static void find(Engine engine, Locator<?> locator, Player player, boolean teleport, String message) {
        find(engine, locator, player, 120_000, location -> {
            if (location == null) {
                ComponentMessenger.sendSection(player, IrisLanguage.text(BukkitRuntimeMessages.ENGINE_BUKKIT_OPS_COULD_NOT_FIND_WITHIN_SEARCH_RANGE, MessageArgument.untrusted("message", String.valueOf(message))));
                return;
            }
            if (teleport) {
                J.runEntity(player, () -> teleportAsyncSafely(player, location));
                ComponentMessenger.sendSection(player, IrisLanguage.text(BukkitRuntimeMessages.ENGINE_BUKKIT_OPS_TELEPORTING, MessageArgument.untrusted("message", String.valueOf(message))));
            } else {
                ComponentMessenger.sendSection(player, IrisLanguage.text(BukkitRuntimeMessages.ENGINE_BUKKIT_OPS_AT, MessageArgument.untrusted("message", String.valueOf(message)), MessageArgument.untrusted("blockX", String.valueOf(location.getBlockX())), MessageArgument.untrusted("blockY", String.valueOf(location.getBlockY())), MessageArgument.untrusted("blockZ", String.valueOf(location.getBlockZ()))));
            }
        });
    }

    private static void find(Engine engine, Locator<?> locator, Player player, long timeout, Consumer<Location> consumer) {
        AtomicLong checks = new AtomicLong();
        long ms = M.ms();
        World world = player.getWorld();
        Location origin = player.getLocation();
        int originChunkX = origin.getBlockX() >> 4;
        int originChunkZ = origin.getBlockZ() >> 4;
        new SingleJob(IrisLanguage.text(RuntimeUiMessages.JOB_SEARCHED_CHUNKS, MessageArgument.trusted("chunks", 0)), () -> {
            CompletableFuture<Position2> search = null;
            boolean resultDispatched = false;
            UUID playerId = player.getUniqueId();
            try {
                search = locator.find(engine, new Position2(originChunkX, originChunkZ), timeout, checks::set);
                CompletableFuture<Position2> previous = ACTIVE_LOCATE_REQUESTS.put(playerId, search);
                if (previous != null && previous != search) {
                    previous.cancel(true);
                }
                Position2 at = search.get();
                if (ACTIVE_LOCATE_REQUESTS.get(playerId) != search) {
                    return;
                }

                if (at != null) {
                    int bx = (at.getX() << 4) + 8;
                    int bz = (at.getZ() << 4) + 8;
                    try (GenerationSessionLease lease = engine.acquireGenerationLease("bukkit_locator_result");
                         IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
                        int by = engine.getMinHeight() + engine.getHeight(bx, bz, false) + 2;
                        resultDispatched = dispatchLocateResult(
                                player, world, consumer, new Location(world, bx, by, bz), playerId, search);
                    }
                } else {
                    resultDispatched = dispatchLocateResult(player, world, consumer, null, playerId, search);
                }
            } catch (CancellationException ignored) {
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (WrongEngineBroException | ExecutionException e) {
                IrisLogging.reportError(e);
            } finally {
                if (search != null && !resultDispatched) {
                    ACTIVE_LOCATE_REQUESTS.remove(playerId, search);
                }
            }
        }) {
            @Override
            public String getName() {
                return IrisLanguage.text(
                        RuntimeUiMessages.JOB_SEARCHED_CHUNKS,
                        MessageArgument.trusted("chunks", Form.f(checks.get()))
                );
            }

            @Override
            public int getTotalWork() {
                return (int) timeout;
            }

            @Override
            public int getWorkCompleted() {
                return (int) Math.min(M.ms() - ms, timeout - 1);
            }
        }.execute(new VolmitSender(player));
    }

    private static boolean dispatchLocateResult(Player player, World world, Consumer<Location> consumer,
                                                Location location, UUID playerId,
                                                CompletableFuture<Position2> search) {
        boolean scheduled = J.runEntity(player, () -> {
            if (!ACTIVE_LOCATE_REQUESTS.remove(playerId, search)) {
                return;
            }
            if (!player.isOnline() || !world.equals(player.getWorld())) {
                return;
            }
            consumer.accept(location);
        });
        if (!scheduled) {
            IrisLogging.warn("Could not schedule an Iris locator result for " + player.getName() + ".");
        }
        return scheduled;
    }

    private static void teleportAsyncSafely(Player player, Location location) {
        if (player == null || location == null) {
            return;
        }

        if (invokeNativeTeleportAsync(player, location)) {
            return;
        }

        try {
            CompletableFuture<Boolean> teleportFuture = PaperLib.teleportAsync(player, location);
            if (teleportFuture != null) {
                teleportFuture.exceptionally(throwable -> {
                    IrisLogging.reportError(throwable);
                    return false;
                });
            }
        } catch (Throwable throwable) {
            IrisLogging.reportError(throwable);
        }
    }

    private static boolean invokeNativeTeleportAsync(Player player, Location location) {
        try {
            Method teleportAsyncMethod = player.getClass().getMethod("teleportAsync", Location.class);
            teleportAsyncMethod.invoke(player, location);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Throwable throwable) {
            IrisLogging.reportError(throwable);
            return false;
        }
    }

    record LootMutationTask(World world, int chunkX, int chunkZ, Runnable mutation) {
    }
}
