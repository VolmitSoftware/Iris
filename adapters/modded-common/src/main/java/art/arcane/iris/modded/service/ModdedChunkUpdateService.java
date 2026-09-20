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

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldMaintenance;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldGenerators;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockProperties;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.studio.view.PregeneratorJob;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.generation.cache.Cache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedBlockResolution;
import art.arcane.iris.modded.ModdedLootApplier;
import art.arcane.iris.modded.ModdedTileData;
import art.arcane.iris.modded.api.ModdedCustomContentRegistry;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockPlacement;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterUpdate;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public final class ModdedChunkUpdateService implements ModdedTickableService {
    private static final long PASS_PERIOD_MILLIS = 3_000L;
    private static final int PLAYER_CHUNK_RADIUS = 1;

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
    public void onServerTick(NativeModdedServer server) {
        long now = System.currentTimeMillis();
        if (now - lastPassAt < PASS_PERIOD_MILLIS) {
            return;
        }
        lastPassAt = now;

        if (!IrisSettings.get().getWorld().isPostLoadBlockUpdates()) {
            return;
        }

        for (NativeWorld level : server.worlds()) {
            IrisModdedChunkGenerator generator = NativeWorldGenerators.find(level, IrisModdedChunkGenerator.class);
            if (generator == null) {
                continue;
            }
            Engine engine = generator.engineIfBound();
            if (engine == null || engine.isClosed() || engine.getMantle().getMantle().isClosed()) {
                continue;
            }
            NativeWorldMaintenance nativeWorld = new NativeWorldMaintenance(level);
            if (!hasUpdateTargets(nativeWorld.hasPlayers(), nativeWorld.hasForcedChunks()) || isPregenActive(engine)) {
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

    private void updateNearPlayers(Engine engine, NativeWorld level) {
        new NativeWorldMaintenance(level).forEachPlayerPosition(player -> {
            int centerX = player.x() >> 4;
            int centerZ = player.z() >> 4;
            for (int dx = -PLAYER_CHUNK_RADIUS; dx <= PLAYER_CHUNK_RADIUS; dx++) {
                for (int dz = -PLAYER_CHUNK_RADIUS; dz <= PLAYER_CHUNK_RADIUS; dz++) {
                    updateChunk(engine, level, centerX + dx, centerZ + dz);
                }
            }
        });
    }

    private void updateForcedChunks(Engine engine, NativeWorld level) {
        new NativeWorldMaintenance(level).forEachForcedChunk(chunkKey ->
                updateChunk(engine, level, (int) chunkKey, (int) (chunkKey >> 32)));
    }

    public void updateRegeneratedChunk(Engine engine, NativeWorld level, int chunkX, int chunkZ) {
        updateChunk(engine, level, chunkX, chunkZ, false);
    }

    private void updateChunk(Engine engine, NativeWorld level, int chunkX, int chunkZ) {
        updateChunk(engine, level, chunkX, chunkZ, true);
    }

    private void updateChunk(Engine engine, NativeWorld level, int chunkX, int chunkZ, boolean requireNeighbors) {
        if (requireNeighbors) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (!level.isChunkLoaded(chunkX + x, chunkZ + z)) {
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

    private void runTilePass(Engine engine, NativeWorld level, int chunkX, int chunkZ, MantleChunk<Matter> chunk) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int minHeight = engine.getWorld().minHeight();
        materializeDeferredSlice(chunk, TileWrapper.class, () ->
                chunk.iterate(TileWrapper.class, (Integer x, Integer yf, Integer z, TileWrapper v) -> {
                    int y = yf + minHeight;
                    if (y < level.minHeight() || y >= level.maxHeight() - 1) {
                        return;
                    }
                    applyTile(level, baseX + (x & 15), y, baseZ + (z & 15), v.getData());
                }));
    }

    private void runCustomPass(Engine engine, NativeWorld level, int chunkX, int chunkZ, MantleChunk<Matter> chunk) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int minHeight = engine.getWorld().minHeight();
        materializeDeferredSlice(chunk, Identifier.class, () ->
                chunk.iterate(Identifier.class, (Integer x, Integer yf, Integer z, Identifier identifier) -> {
                    int y = yf + minHeight;
                    if (y < level.minHeight() || y >= level.maxHeight() - 1) {
                        return;
                    }
                    NativeBlockPoint position = new NativeBlockPoint(baseX + (x & 15), y, baseZ + (z & 15));
                    ModdedCustomContentRegistry.processBlockPlacement(engine, identifier.toString(),
                            () -> NativeWorldMaintenance.placement(level, position));
                }));
    }

    static void materializeDeferredSlice(MantleChunk<Matter> chunk, Class<?> sliceType, Runnable materializer) {
        materializer.run();
        chunk.deleteSlices(sliceType);
    }

    private void applyTile(NativeWorld level, int x, int y, int z, TileData tile) {
        if (!(tile instanceof ModdedTileData moddedTile)) {
            return;
        }
        NativeBlockPoint position = new NativeBlockPoint(x, y, z);
        try {
            NativeWorldMaintenance.TileResult result = NativeWorldMaintenance.applyTile(level, position, moddedTile.nativeData());
            if (result == NativeWorldMaintenance.TileResult.MISSING_ENTITY) {
                IrisLogging.warn("Iris could not create block entity at " + position + " for " + level.getBlock(x, y, z));
            } else if (result == NativeWorldMaintenance.TileResult.EMPTY_PAYLOAD) {
                IrisLogging.warn("Iris tile payload was empty at " + position + " for " + level.getBlock(x, y, z));
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
        long key = Cache.key(chunkX, chunkZ);
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

    void runUpdatePass(Engine engine, NativeWorld level, int chunkX, int chunkZ, MantleChunk<Matter> chunk) {
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
            if (y < level.minHeight() || y >= level.maxHeight() - 1) {
                return;
            }
            int lx = x & 15;
            int lz = z & 15;
            if (NativeWorldMaintenance.exposedFluid(level, baseX + lx, y, baseZ + lz)) {
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

        chunk.iterate(MatterUpdate.class, (Integer x, Integer yf, Integer z, MatterUpdate v) -> {
            if (v != null && v.isUpdate()) {
                update(engine, level, x, yf + minHeight, z, baseX, baseZ, chunk);
            }
        });
        chunk.deleteSlices(MatterUpdate.class);
        engine.getMetrics().getUpdates().put(stopwatch.getMilliseconds());
    }

    private void update(Engine engine, NativeWorld level, int x, int y, int z, int baseX, int baseZ, MantleChunk<Matter> chunk) {
        if (y < level.minHeight() || y >= level.maxHeight() - 1) {
            return;
        }
        NativeBlockPoint pos = new NativeBlockPoint(baseX + (x & 15), y, baseZ + (z & 15));
        NativeBlockState state = level.getBlock(pos.x(), pos.y(), pos.z());
        engine.blockUpdatedMetric();
        if (state.isStorage()) {
            if (!state.isStorageChest()) {
                return;
            }
            try {
                ModdedLootApplier.apply(engine, level, pos, state, chunk);
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
        } else {
            NativeWorldMaintenance.refresh(level, pos);
        }
    }
}
