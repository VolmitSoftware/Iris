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

package art.arcane.iris.engine;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.data.cache.Cache;
import art.arcane.iris.engine.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.engine.history.GenerationHistoryRuntimeRouter.SavedChunkMantle;
import art.arcane.iris.engine.history.SavedBiomeUnavailableException;
import art.arcane.iris.engine.platform.EngineBukkitOps;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.plugin.Chunks;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.matter.Matter;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chunk discovery, post-load block updates and mantle warmup for a Bukkit Iris world. Every scan is
 * scheduled from the global thread, fans out per player onto the owning entity thread and touches a
 * chunk only from its own region thread; the scan-scheduled flags and per-chunk key sets keep a
 * single pass in flight per chunk and are always cleared on the rejection path.
 */
final class WorldChunkMaintenance {
    private static final int MAX_FORCED_CHUNK_UPDATES = 128;
    private static final int MAX_PENDING_MATERIALIZATIONS = 128;
    private static final int MAX_COMPLETED_MATERIALIZATIONS = 8192;

    private final IrisWorldManager manager;
    private final Set<Long> mantleWarmupQueue = ConcurrentHashMap.newKeySet();
    private final Set<Long> markerFlagQueue = ConcurrentHashMap.newKeySet();
    private final Set<Long> discoveredFlagQueue = ConcurrentHashMap.newKeySet();
    private final Set<Long> chunkUpdateQueue = ConcurrentHashMap.newKeySet();
    private final Map<Long, RoutedMaterialization> materializations = new ConcurrentHashMap<>();
    private final Set<Long> completedMaterializations = Collections.synchronizedSet(new LinkedHashSet<>());
    private final AtomicBoolean chunkUpdateScanScheduled = new AtomicBoolean();
    private final AtomicBoolean chunkDiscoveryScanScheduled = new AtomicBoolean();
    private volatile Position2[] loadedChunkPositions = new Position2[0];
    private int forcedChunkUpdateCursor = 0;
    private volatile boolean closed;

    WorldChunkMaintenance(IrisWorldManager manager) {
        this.manager = manager;
    }

    void close() {
        synchronized (materializations) {
            closed = true;
            for (RoutedMaterialization materialization : materializations.values()) {
                materialization.cancel();
            }
        }
        completedMaterializations.clear();
    }

    void invalidateMaterialization(int chunkX, int chunkZ) {
        long key = Cache.key(chunkX, chunkZ);
        synchronized (materializations) {
            RoutedMaterialization materialization = materializations.get(key);
            if (materialization != null) {
                materialization.cancel();
            }
            completedMaterializations.remove(key);
        }
    }

    void discoverChunks() {
        World world = BukkitWorldBinding.world(manager.getEngine().getWorld());
        if (world == null) {
            return;
        }

        if (manager.entitySpawner.isPregenActiveForThisWorld()) {
            return;
        }

        if (!chunkDiscoveryScanScheduled.compareAndSet(false, true)) {
            return;
        }

        boolean scheduled = J.runGlobal(manager.managedTask("bukkit_world_manager_discover_chunks", () -> {
            try {
                if (manager.getEngine().isClosed() || !world.equals(BukkitWorldBinding.world(manager.getEngine().getWorld()))) {
                    return;
                }

                for (Player player : world.getPlayers()) {
                    if (player == null) {
                        continue;
                    }

                    J.runEntity(player, manager.managedTask("bukkit_world_manager_discover_player", () -> {
                        if (!player.isOnline() || !world.equals(player.getWorld())) {
                            return;
                        }

                        int centerX = PowerOfTwoCoordinates.blockToChunkFloor(player.getLocation().getBlockX());
                        int centerZ = PowerOfTwoCoordinates.blockToChunkFloor(player.getLocation().getBlockZ());
                        int radius = 1;
                        for (int x = -radius; x <= radius; x++) {
                            for (int z = -radius; z <= radius; z++) {
                                int chunkX = centerX + x;
                                int chunkZ = centerZ + z;
                                raiseDiscoveredChunkFlag(world, chunkX, chunkZ);
                            }
                        }
                    }));
                }
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            } finally {
                chunkDiscoveryScanScheduled.set(false);
            }
        }, () -> chunkDiscoveryScanScheduled.set(false)));
        if (!scheduled) {
            chunkDiscoveryScanScheduled.set(false);
        }
    }

    private void raiseDiscoveredChunkFlag(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }

        if (!J.isFolia()) {
            manager.getMantle().getChunk(chunkX, chunkZ).flag(MantleFlag.DISCOVERED, true);
            return;
        }

        long key = Cache.key(chunkX, chunkZ);
        if (!discoveredFlagQueue.add(key)) {
            return;
        }

        J.a(manager.managedTask("bukkit_world_manager_discovered_flag", () -> {
            try {
                Mantle<Matter> mantle = manager.getMantle();
                if (!mantle.hasFlag(chunkX, chunkZ, MantleFlag.DISCOVERED)) {
                    mantle.flag(chunkX, chunkZ, MantleFlag.DISCOVERED, true);
                }
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            } finally {
                discoveredFlagQueue.remove(key);
            }
        }, () -> discoveredFlagQueue.remove(key)));
    }

    void updateChunks() {
        World world = BukkitWorldBinding.world(manager.getEngine().getWorld());
        if (world == null) {
            return;
        }

        if (manager.entitySpawner.isPregenActiveForThisWorld()) {
            return;
        }

        if (!chunkUpdateScanScheduled.compareAndSet(false, true)) {
            return;
        }

        boolean scheduled = J.runGlobal(manager.managedTask(
                "bukkit_world_manager_update_chunks",
                () -> updateChunksOnGlobal(world),
                () -> chunkUpdateScanScheduled.set(false)));
        if (!scheduled) {
            chunkUpdateScanScheduled.set(false);
        }
    }

    private void updateChunksOnGlobal(World world) {
        try {
            if (manager.getEngine().isClosed() || !world.equals(BukkitWorldBinding.world(manager.getEngine().getWorld()))) {
                return;
            }

            List<Player> players = new ArrayList<>(world.getPlayers());
            Chunk[] loadedChunks = world.getLoadedChunks();
            Position2[] currentLoadedChunkPositions = new Position2[loadedChunks.length];
            for (int i = 0; i < loadedChunks.length; i++) {
                currentLoadedChunkPositions[i] = new Position2(loadedChunks[i].getX(), loadedChunks[i].getZ());
            }
            loadedChunkPositions = currentLoadedChunkPositions;
            manager.playersPresent = !players.isEmpty();
            manager.loadedChunkCount = currentLoadedChunkPositions.length;
            for (Player player : players) {
                if (player == null) {
                    continue;
                }

                J.runEntity(player, manager.managedTask(
                        "bukkit_world_manager_player_chunk_updates",
                        () -> schedulePlayerChunkUpdates(world, player)));
            }

            scheduleForcedChunkUpdates(world);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        } finally {
            chunkUpdateScanScheduled.set(false);
        }
    }

    private void schedulePlayerChunkUpdates(World world, Player player) {
        if (!player.isOnline() || !world.equals(player.getWorld())) {
            return;
        }

        int centerX = PowerOfTwoCoordinates.blockToChunkFloor(player.getLocation().getBlockX());
        int centerZ = PowerOfTwoCoordinates.blockToChunkFloor(player.getLocation().getBlockZ());
        int radius = 1;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                scheduleChunkUpdate(world, centerX + x, centerZ + z);
            }
        }
    }

    private void scheduleForcedChunkUpdates(World world) {
        List<Position2> forcedChunks = new ArrayList<>();
        for (Chunk chunk : world.getForceLoadedChunks()) {
            forcedChunks.add(new Position2(chunk.getX(), chunk.getZ()));
        }
        forcedChunks.sort(Comparator.comparingInt(Position2::getX).thenComparingInt(Position2::getZ));

        int forcedChunkCount = forcedChunks.size();
        if (forcedChunkCount == 0) {
            forcedChunkUpdateCursor = 0;
            return;
        }

        int updateCount = Math.min(forcedChunkCount, MAX_FORCED_CHUNK_UPDATES);
        int start = Math.floorMod(forcedChunkUpdateCursor, forcedChunkCount);
        for (int i = 0; i < updateCount; i++) {
            Position2 chunk = forcedChunks.get((start + i) % forcedChunkCount);
            scheduleChunkUpdate(world, chunk.getX(), chunk.getZ());
        }
        forcedChunkUpdateCursor = (start + updateCount) % forcedChunkCount;
    }

    private void scheduleChunkUpdate(World world, int chunkX, int chunkZ) {
        long key = Cache.key(chunkX, chunkZ);
        if (!chunkUpdateQueue.add(key)) {
            return;
        }

        try {
            boolean scheduled = J.runRegion(world, chunkX, chunkZ, manager.managedTask("bukkit_world_manager_chunk_update", () -> {
                try {
                    updateChunkRegion(world, chunkX, chunkZ);
                } finally {
                    chunkUpdateQueue.remove(key);
                }
            }, () -> chunkUpdateQueue.remove(key)));
            if (!scheduled) {
                chunkUpdateQueue.remove(key);
            }
        } catch (Throwable e) {
            chunkUpdateQueue.remove(key);
            IrisLogging.reportError(e);
        }
    }

    void updateChunkRegion(World world, int chunkX, int chunkZ) {
        if (closed || world == null || !world.isChunkLoaded(chunkX, chunkZ) || !Chunks.isSafe(world, chunkX, chunkZ)) {
            return;
        }

        Chunk chunk = world.getChunkAt(chunkX, chunkZ);

        if (IrisSettings.get().getWorld().isPostLoadBlockUpdates()) {
            GenerationHistoryRuntimeRouter router = manager.getEngine() instanceof IrisEngine engine
                    ? engine.getGenerationHistoryRuntimeRouter().orElse(null) : null;
            if (router != null && !completedMaterializations.contains(Cache.key(chunkX, chunkZ))) {
                scheduleMaterialization(new MaterializationTarget(world, chunkX, chunkZ), router);
                return;
            }
            if (router == null && !updateCurrentChunk(chunk, chunkX, chunkZ)) {
                return;
            }
        }

        spawnInitially(chunk);
    }

    private boolean updateCurrentChunk(Chunk chunk, int chunkX, int chunkZ) {
        if (!manager.getMantle().isChunkLoaded(chunkX, chunkZ)) {
            warmupMantleChunkAsync(chunkX, chunkZ);
            return false;
        }
        return materializeChunk(chunk, manager.getMantle());
    }

    private boolean materializeChunk(Chunk chunk, Mantle<Matter> mantle) {
        try {
            EngineBukkitOps.updateChunk(manager.getEngine(), chunk, mantle);
        } catch (SavedBiomeUnavailableException unavailable) {
            if (!unavailable.isLoading() || unavailable.getSuppressed().length != 0) {
                throw unavailable;
            }
            return false;
        }
        return true;
    }

    private void spawnInitially(Chunk chunk) {
        if (manager.entitySpawner.isEntitySpawningEnabledForCurrentWorld()) {
            manager.entitySpawner.spawnInitially(chunk);
        }
    }

    private void scheduleMaterialization(MaterializationTarget target, GenerationHistoryRuntimeRouter router) {
        RoutedMaterialization materialization;
        synchronized (materializations) {
            if (closed || materializations.size() >= MAX_PENDING_MATERIALIZATIONS
                    || materializations.containsKey(target.key())) {
                return;
            }
            materialization = new RoutedMaterialization(target, router);
            materializations.put(target.key(), materialization);
        }
        try {
            J.a(manager.managedTask("bukkit_world_manager_materialization_load",
                    materialization::prepare, materialization::cancel));
        } catch (Throwable failure) {
            materialization.cancel();
            IrisLogging.reportError(failure);
        }
    }

    private void recordMaterialized(long key) {
        synchronized (completedMaterializations) {
            if (closed) {
                return;
            }
            if (completedMaterializations.size() >= MAX_COMPLETED_MATERIALIZATIONS) {
                completedMaterializations.remove(completedMaterializations.iterator().next());
            }
            completedMaterializations.add(key);
        }
    }

    void raiseInitialSpawnMarkerFlag(World world, int chunkX, int chunkZ, Runnable onFirstRaise) {
        if (world == null || onFirstRaise == null) {
            return;
        }

        if (!J.isFolia()) {
            manager.getMantle().raiseFlag(chunkX, chunkZ, MantleFlag.INITIAL_SPAWNED_MARKER, onFirstRaise);
            return;
        }

        long key = Cache.key(chunkX, chunkZ);
        if (!markerFlagQueue.add(key)) {
            return;
        }

        J.a(manager.managedTask("bukkit_world_manager_spawn_marker_flag", () -> {
            boolean raised = false;
            try {
                Mantle<Matter> mantle = manager.getMantle();
                if (!mantle.hasFlag(chunkX, chunkZ, MantleFlag.INITIAL_SPAWNED_MARKER)) {
                    mantle.flag(chunkX, chunkZ, MantleFlag.INITIAL_SPAWNED_MARKER, true);
                    raised = true;
                }
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            } finally {
                markerFlagQueue.remove(key);
            }

            if (!raised) {
                return;
            }

            J.runRegion(world, chunkX, chunkZ, manager.managedTask("bukkit_world_manager_spawn_marker_callback", () -> {
                if (!world.isChunkLoaded(chunkX, chunkZ) || !Chunks.isSafe(world, chunkX, chunkZ)) {
                    return;
                }
                onFirstRaise.run();
            }));
        }, () -> markerFlagQueue.remove(key)));
    }

    void warmupMantleChunkAsync(int chunkX, int chunkZ) {
        long key = Cache.key(chunkX, chunkZ);
        if (!mantleWarmupQueue.add(key)) {
            return;
        }

        J.a(manager.managedTask("bukkit_world_manager_mantle_warmup", () -> {
            try {
                manager.getMantle().getChunk(chunkX, chunkZ);
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            } finally {
                mantleWarmupQueue.remove(key);
            }
        }, () -> mantleWarmupQueue.remove(key)));
    }

    Position2[] getLoadedChunkPositionsSnapshot(World world) {
        if (world == null) {
            return new Position2[0];
        }
        return loadedChunkPositions;
    }

    private record MaterializationTarget(World world, int x, int z) {
        private long key() {
            return Cache.key(x, z);
        }
    }

    private final class RoutedMaterialization {
        private final MaterializationTarget target;
        private final GenerationHistoryRuntimeRouter router;
        private SavedChunkMantle savedMantle;
        private MantleChunk<Matter> mantleChunk;
        private boolean cancelled;
        private boolean preparing;
        private boolean applying;
        private boolean releasing;

        private RoutedMaterialization(MaterializationTarget target, GenerationHistoryRuntimeRouter router) {
            this.target = target;
            this.router = router;
        }

        private void prepare() {
            SavedChunkMantle opened = null;
            MantleChunk<Matter> loaded = null;
            try {
                synchronized (this) {
                    if (cancelled) {
                        return;
                    }
                    preparing = true;
                }
                opened = router.openSavedChunkMantle(target.x(), target.z());
                loaded = opened.mantle().getChunk(target.x(), target.z()).use();
                opened.detachThread();
                synchronized (this) {
                    if (cancelled) {
                        return;
                    }
                    savedMantle = opened;
                    mantleChunk = loaded;
                    opened = null;
                    loaded = null;
                }
                boolean scheduled = J.runRegion(target.world(), target.x(), target.z(),
                        manager.managedTask("bukkit_world_manager_materialization_apply", this::apply, this::cancel));
                if (!scheduled) {
                    cancel();
                }
            } catch (Throwable failure) {
                cancel();
                IrisLogging.reportError("Failed to load saved chunk materialization at " + target.x() + "," + target.z() + ".", failure);
            } finally {
                try {
                    try {
                        if (loaded != null) {
                            loaded.release();
                        }
                    } finally {
                        if (opened != null) {
                            opened.close();
                        }
                    }
                } finally {
                    boolean cleanup;
                    synchronized (this) {
                        preparing = false;
                        cleanup = cancelled;
                    }
                    if (cleanup) {
                        cancel();
                    }
                }
            }
        }

        private void apply() {
            SavedChunkMantle active;
            synchronized (this) {
                if (cancelled || savedMantle == null) {
                    return;
                }
                applying = true;
                active = savedMantle;
            }
            try {
                World world = target.world();
                if (closed || !world.isChunkLoaded(target.x(), target.z())
                        || !Chunks.isSafe(world, target.x(), target.z())) {
                    return;
                }
                Chunk chunk = world.getChunkAt(target.x(), target.z());
                boolean completed;
                try (SavedChunkMantle.Scope ignored = active.openScope()) {
                    Mantle<Matter> mantle = active.mantle();
                    if (!materializeChunk(chunk, mantle)) {
                        return;
                    }
                    completed = mantle.hasFlag(target.x(), target.z(), MantleFlag.ETCHED);
                }
                synchronized (this) {
                    if (completed && !cancelled) {
                        recordMaterialized(target.key());
                    }
                }
                spawnInitially(chunk);
            } catch (Throwable failure) {
                IrisLogging.reportError("Failed to materialize saved chunk at " + target.x() + "," + target.z() + ".", failure);
            } finally {
                synchronized (this) {
                    applying = false;
                }
                cancel();
            }
        }

        private void cancel() {
            SavedChunkMantle retained;
            MantleChunk<Matter> loaded;
            synchronized (this) {
                cancelled = true;
                if (preparing || applying || releasing) {
                    return;
                }
                retained = savedMantle;
                loaded = mantleChunk;
                savedMantle = null;
                mantleChunk = null;
                releasing = retained != null;
            }
            if (retained != null) {
                ForkJoinPool.commonPool().execute(() -> release(retained, loaded));
            } else {
                materializations.remove(target.key(), this);
            }
        }

        private void release(SavedChunkMantle retained, MantleChunk<Matter> loaded) {
            try {
                try {
                    loaded.release();
                } finally {
                    retained.close();
                }
            } catch (Throwable failure) {
                completedMaterializations.remove(target.key());
                IrisLogging.reportError("Failed to release saved chunk materialization at " + target.x() + "," + target.z() + ".", failure);
            } finally {
                materializations.remove(target.key(), this);
            }
        }
    }
}
