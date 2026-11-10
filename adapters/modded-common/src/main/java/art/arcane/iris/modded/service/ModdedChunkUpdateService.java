/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded.service;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.gui.PregeneratorJob;
import art.arcane.iris.core.link.Identifier;
import art.arcane.volmlib.util.cache.CacheKey;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedBlockResolution;
import art.arcane.iris.modded.ModdedLootApplier;
import art.arcane.iris.modded.ModdedServerLevels;
import art.arcane.iris.modded.ModdedTileData;
import art.arcane.iris.modded.api.ModdedCustomContentRegistry;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.project.matter.TileWrapper;
import art.arcane.volmlib.util.function.Consumer3;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterUpdate;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class ModdedChunkUpdateService implements ModdedTickableService {
    private static final long PASS_PERIOD_MILLIS = 3_000L;
    private static final int PLAYER_CHUNK_RADIUS = 1;
    private static final int SILENT_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;

    private final Set<Long> warmupQueue = ConcurrentHashMap.newKeySet();
    private volatile ExecutorService warmupExecutor;
    private long lastPassAt;

    @Override
    public void onEnable() {
        if (warmupExecutor != null) {
            return;
        }
        warmupExecutor = Executors.newSingleThreadExecutor((Runnable runnable) -> {
            Thread thread = new Thread(runnable, "Iris Mantle Warmup");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        lastPassAt = 0L;
    }

    @Override
    public void onDisable() {
        ExecutorService active = warmupExecutor;
        warmupExecutor = null;
        if (active != null) {
            // NEVER interrupt first: an interrupt inside a FileChannel plate read closes the
            // channel (ClosedByInterruptException) and the read-failure fallback installs an
            // EMPTY plate that the very next shutdown stage flushes over real data. Queued
            // warm-ups self-cancel (warmupExecutor is already null), so shutdown() + await
            // only waits on the single in-flight load; escalate only on timeout.
            active.shutdown();
            try {
                if (!active.awaitTermination(5L, TimeUnit.SECONDS)) {
                    IrisLogging.warn("Iris mantle warm-up did not stop before engine close; forcing interrupt");
                    active.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        warmupQueue.clear();
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastPassAt < PASS_PERIOD_MILLIS) {
            return;
        }
        lastPassAt = now;

        if (!IrisSettings.get().getWorld().isPostLoadBlockUpdates()) {
            return;
        }

        for (ServerLevel level : ModdedServerLevels.levels(server)) {
            if (!(level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator generator)) {
                continue;
            }
            Engine engine = generator.engineIfBound();
            if (engine == null || engine.isClosed() || engine.getMantle().getMantle().isClosed()) {
                continue;
            }
            if (!hasUpdateTargets(!level.players().isEmpty(), !level.getForceLoadedChunks().isEmpty()) || isPregenActive(engine)) {
                continue;
            }
            try {
                updateNearPlayers(engine, level);
                updateForcedChunks(engine, level);
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
        }
    }

    static boolean hasUpdateTargets(boolean hasPlayers, boolean hasForcedChunks) {
        return hasPlayers || hasForcedChunks;
    }

    private boolean isPregenActive(Engine engine) {
        PregeneratorJob job = PregeneratorJob.getInstance();
        return job != null && job.targetsWorldIdentity(engine.getWorld().identity());
    }

    private void updateNearPlayers(Engine engine, ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            int centerX = player.blockPosition().getX() >> 4;
            int centerZ = player.blockPosition().getZ() >> 4;
            for (int dx = -PLAYER_CHUNK_RADIUS; dx <= PLAYER_CHUNK_RADIUS; dx++) {
                for (int dz = -PLAYER_CHUNK_RADIUS; dz <= PLAYER_CHUNK_RADIUS; dz++) {
                    updatePassChunk(engine, level, centerX + dx, centerZ + dz);
                }
            }
        }
    }

    private void updateForcedChunks(Engine engine, ServerLevel level) {
        for (long chunkKey : level.getForceLoadedChunks()) {
            updatePassChunk(engine, level, ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
        }
    }

    public void updateRegeneratedChunk(Engine engine, ServerLevel level, int chunkX, int chunkZ) {
        updateChunk(engine, level, chunkX, chunkZ, false);
    }

    private void updatePassChunk(Engine engine, ServerLevel level, int chunkX, int chunkZ) {
        isolateChunkFailure(
                () -> updateChunk(engine, level, chunkX, chunkZ, true),
                () -> engine.isClosed() || engine.getMantle().getMantle().isClosed(),
                String.valueOf(level.dimension().identifier()),
                chunkX,
                chunkZ);
    }

    static void isolateChunkFailure(Runnable chunkUpdate, BooleanSupplier passEnded, String levelName, int chunkX, int chunkZ) {
        try {
            chunkUpdate.run();
        } catch (RuntimeException failure) {
            if (passEnded.getAsBoolean()) {
                throw failure;
            }
            boolean firstFailure = IrisLogging.errorOnce(
                    "chunk-update:" + levelName + ":" + chunkX + "," + chunkZ + ":" + failure,
                    "Iris chunk update failed at %s,%s in %s, retrying each pass: %s",
                    chunkX, chunkZ, levelName, failure);
            if (firstFailure) {
                IrisLogging.reportError(failure);
            }
        }
    }

    private void updateChunk(Engine engine, ServerLevel level, int chunkX, int chunkZ, boolean requireNeighbors) {
        if (requireNeighbors) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (level.getChunkSource().getChunkNow(chunkX + x, chunkZ + z) == null) {
                        return;
                    }
                }
            }
        }

        Mantle<Matter> mantle = engine.getMantle().getMantle();
        if (!mantle.isChunkLoaded(chunkX, chunkZ)) {
            warmupMantleChunk(mantle, chunkX, chunkZ);
            return;
        }
        if (mantle.hasFlag(chunkX, chunkZ, MantleFlag.ETCHED)) {
            return;
        }

        MantleChunk<Matter> chunk = mantle.getChunk(chunkX, chunkZ).use();
        try {
            chunk.raiseFlagUnchecked(MantleFlag.ETCHED, () -> {
                chunk.raiseFlagUnchecked(MantleFlag.TILE, () -> runTilePass(engine, level, chunkX, chunkZ, chunk));
                chunk.raiseFlagUnchecked(MantleFlag.CUSTOM, () -> runCustomPass(engine, level, chunkX, chunkZ, chunk));
                chunk.raiseFlagUnchecked(MantleFlag.UPDATE, () -> runUpdatePass(engine, level, chunkX, chunkZ, chunk));
            });
        } finally {
            chunk.release();
        }
    }

    private void runTilePass(Engine engine, ServerLevel level, int chunkX, int chunkZ, MantleChunk<Matter> chunk) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int minHeight = engine.getWorld().minHeight();
        materializeDeferredSlice(chunk, TileWrapper.class, () ->
                chunk.iterate(TileWrapper.class, (Integer x, Integer yf, Integer z, TileWrapper v) -> {
                    int y = yf + minHeight;
                    if (y < level.getMinY() || y >= level.getMaxY()) {
                        return;
                    }
                    applyTile(level, baseX + (x & 15), y, baseZ + (z & 15), v.getData());
                }));
    }

    private void runCustomPass(Engine engine, ServerLevel level, int chunkX, int chunkZ, MantleChunk<Matter> chunk) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int minHeight = engine.getWorld().minHeight();
        materializeDeferredSlice(chunk, Identifier.class, () ->
                chunk.iterate(Identifier.class, (Integer x, Integer yf, Integer z, Identifier identifier) -> {
                    int y = yf + minHeight;
                    if (y < level.getMinY() || y >= level.getMaxY()) {
                        return;
                    }
                    BlockPos position = new BlockPos(baseX + (x & 15), y, baseZ + (z & 15));
                    ModdedCustomContentRegistry.processBlockPlacement(engine, level, position, identifier.toString());
                }));
    }

    static void materializeDeferredSlice(MantleChunk<Matter> chunk, Class<?> sliceType, Runnable materializer) {
        materializer.run();
        chunk.deleteSlices(sliceType);
    }

    private void applyTile(ServerLevel level, int x, int y, int z, TileData tile) {
        if (!(tile instanceof ModdedTileData moddedTile)) {
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        BlockState adjusted = moddedTile.adjustBlockState(state);
        if (adjusted != state) {
            level.setBlock(pos, adjusted, SILENT_FLAGS);
            state = adjusted;
        }
        if (!state.hasBlockEntity() || !(state.getBlock() instanceof EntityBlock entityBlock)) {
            return;
        }
        try {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                blockEntity = entityBlock.newBlockEntity(pos, state);
            }
            if (blockEntity == null) {
                IrisLogging.warn("Iris could not create block entity at " + pos + " for " + state);
                return;
            }
            if (!moddedTile.isApplicable(state, blockEntity)) {
                return;
            }
            level.setBlockEntity(blockEntity);
            if (!moddedTile.apply(blockEntity, level)) {
                IrisLogging.warn("Iris tile payload was empty at " + pos + " for " + state);
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    private void warmupMantleChunk(Mantle<Matter> mantle, int chunkX, int chunkZ) {
        ExecutorService active = warmupExecutor;
        if (active == null) {
            return;
        }
        long key = CacheKey.key(chunkX, chunkZ);
        if (!warmupQueue.add(key)) {
            return;
        }
        try {
            active.execute(() -> {
                // Self-cancel on shutdown: warmupExecutor is nulled first in onDisable, so
                // queued prefetches no-op instantly and only an in-flight load is awaited.
                if (warmupExecutor == null || mantle.isClosed()) {
                    warmupQueue.remove(key);
                    return;
                }
                try {
                    mantle.getChunk(chunkX, chunkZ);
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                } finally {
                    warmupQueue.remove(key);
                }
            });
        } catch (RejectedExecutionException rejected) {
            warmupQueue.remove(key);
        }
    }

    private void runUpdatePass(Engine engine, ServerLevel level, int chunkX, int chunkZ, MantleChunk<Matter> chunk) {
        PrecisionStopwatch stopwatch = PrecisionStopwatch.start();
        int minHeight = engine.getWorld().minHeight();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int[][] grid = new int[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                grid[x][z] = Integer.MIN_VALUE;
            }
        }

        chunk.iterate(MatterCavern.class, (Integer x, Integer yf, Integer z, MatterCavern v) -> {
            int y = yf + minHeight;
            if (y < level.getMinY() || y >= level.getMaxY()) {
                return;
            }
            int lx = x & 15;
            int lz = z & 15;
            BlockPos pos = new BlockPos(baseX + lx, y, baseZ + lz);
            if (!ModdedBlockResolution.isFluid(level.getBlockState(pos))) {
                return;
            }
            boolean exposed = ModdedBlockResolution.isAir(level.getBlockState(pos.below()))
                    || ModdedBlockResolution.isAir(level.getBlockState(pos.west()))
                    || ModdedBlockResolution.isAir(level.getBlockState(pos.east()))
                    || ModdedBlockResolution.isAir(level.getBlockState(pos.south()))
                    || ModdedBlockResolution.isAir(level.getBlockState(pos.north()));

            if (exposed) {
                grid[lx][lz] = Math.max(grid[lx][lz], y);
            }
        });

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (grid[x][z] == Integer.MIN_VALUE) {
                    continue;
                }
                update(engine, level, x, grid[x][z], z, baseX, baseZ, chunk);
            }
        }

        applyQueuedUpdates(chunk, (Integer x, Integer yf, Integer z) ->
                update(engine, level, x, yf + minHeight, z, baseX, baseZ, chunk));
        engine.getMetrics().getUpdates().put(stopwatch.getMilliseconds());
    }

    static void applyQueuedUpdates(MantleChunk<Matter> chunk, Consumer3<Integer, Integer, Integer> update) {
        IntArrayList completed = new IntArrayList();
        try {
            chunk.iterate(MatterUpdate.class, (Integer x, Integer yf, Integer z, MatterUpdate v) -> {
                if (v != null && v.isUpdate()) {
                    update.accept(x, yf, z);
                    completed.add(packUpdatePosition(x, yf, z));
                }
            });
        } catch (RuntimeException | Error failure) {
            forgetCompletedUpdates(chunk, completed, failure);
            throw failure;
        }
        chunk.deleteSlices(MatterUpdate.class);
    }

    private static int packUpdatePosition(int x, int yf, int z) {
        return (yf << 8) | ((x & 15) << 4) | (z & 15);
    }

    private static void forgetCompletedUpdates(MantleChunk<Matter> chunk, IntArrayList completed, Throwable failure) {
        try {
            for (int index = 0; index < completed.size(); index++) {
                int position = completed.getInt(index);
                int yf = position >>> 8;
                Matter section = chunk.get(yf >> 4);
                if (section != null && section.hasSlice(MatterUpdate.class)) {
                    section.<MatterUpdate>getSlice(MatterUpdate.class)
                            .set((position >> 4) & 15, yf & 15, position & 15, null);
                }
            }
        } catch (RuntimeException | Error cleanupFailure) {
            if (cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private void update(Engine engine, ServerLevel level, int x, int y, int z, int baseX, int baseZ, MantleChunk<Matter> chunk) {
        if (y < level.getMinY() || y >= level.getMaxY()) {
            return;
        }
        BlockPos pos = new BlockPos(baseX + (x & 15), y, baseZ + (z & 15));
        BlockState state = level.getBlockState(pos);
        engine.blockUpdatedMetric();
        if (ModdedBlockResolution.isStorage(state)) {
            if (!ModdedBlockResolution.isStorageChest(state)) {
                return;
            }
            try {
                ModdedLootApplier.apply(engine, level, pos, state, chunk);
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), SILENT_FLAGS);
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
    }
}
