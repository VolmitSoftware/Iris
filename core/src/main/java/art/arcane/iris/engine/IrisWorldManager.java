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

import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.data.cache.Cache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedWorldManager;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.Looper;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Chunk;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@EqualsAndHashCode(callSuper = true)
@Data
public class IrisWorldManager extends EngineAssignedWorldManager {
    private static final long CLOSE_AWAIT_MS = 3_000;

    private final Looper looper;
    private final KList<Runnable> updateQueue = new KList<>();
    final ChronoLatch cl;
    private final ChronoLatch clw;
    private final ChronoLatch cln;
    private final ChronoLatch chunkUpdater;
    private final ChronoLatch chunkDiscovery;
    private final KMap<Long, Future<?>> cleanup = new KMap<>();
    private final ScheduledThreadPoolExecutor cleanupService;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final WorldEntitySpawner entitySpawner = new WorldEntitySpawner(this);
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final WorldChunkMaintenance chunkMaintenance = new WorldChunkMaintenance(this);
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final MarkerSpawnScanner markerScanner = new MarkerSpawnScanner(this);
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final WorldBlockDropRouter blockDropRouter = new WorldBlockDropRouter(this);
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    final WorldTeleportWarmup teleportWarmup = new WorldTeleportWarmup();
    private boolean looperStopped;
    private volatile boolean cleanupServiceStopped;
    volatile int entityCount = 0;
    volatile boolean entityCountValid = false;
    volatile boolean playersPresent = false;
    private KSet<Position2> injectBiomes = new KSet<>();
    volatile int loadedChunkCount = 0;

    public IrisWorldManager() {
        super(null);
        cl = null;
        cln = null;
        clw = null;
        looper = null;
        chunkUpdater = null;
        chunkDiscovery = null;
        cleanupService = null;
    }

    public IrisWorldManager(Engine engine) {
        super(engine);
        chunkUpdater = new ChronoLatch(3000);
        chunkDiscovery = new ChronoLatch(5000);
        cln = new ChronoLatch(60000);
        cl = new ChronoLatch(3000);
        clw = new ChronoLatch(1000, true);
        cleanupService = createCleanupExecutor(getTarget().getWorld().name());
        looper = new Looper() {
            @Override
            protected long loop() {
                if (!isManagerStarted()) {
                    return -1L;
                }
                return callManagerTask(
                        "bukkit_world_manager_loop",
                        IrisWorldManager.this::runLoop,
                        250L);
            }
        };
        looper.setPriority(Thread.MIN_PRIORITY);
        looper.setName("Iris World Manager " + getTarget().getWorld().name());
    }

    static ScheduledThreadPoolExecutor createCleanupExecutor(String worldName) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "Iris Mantle Cleanup " + worldName);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    private long runLoop() {
        if (getEngine().isClosed()) {
            looper.interrupt();
            return -1L;
        }

        if (!getEngine().getWorld().hasPlatformWorld() && clw.flip()) {
            J.runGlobal(() -> runManagerTask(
                    "bukkit_world_manager_bind",
                    () -> BukkitWorldBinding.tryBind(getEngine().getWorld())));
        }

        if (getEngine().getWorld().hasPlatformWorld()) {
            if (chunkUpdater.flip()) {
                chunkMaintenance.updateChunks();
            }

            if (!playersPresent) {
                return 5000L;
            }

            if (chunkDiscovery.flip()) {
                chunkMaintenance.discoverChunks();
            }

            if (cln.flip()) {
                getEngine().getEngineData().cleanup(getEngine());
            }

            if (!IrisSettings.get().getWorld().isMarkerEntitySpawningSystem()
                    && !IrisSettings.get().getWorld().isAmbientEntitySpawningSystem()) {
                return 3000L;
            }

            entitySpawner.onAsyncTick();
        }

        return IrisSettings.get().getWorld().getAsyncTickIntervalMS();
    }

    @Override
    public void start() {
        super.start();
        if (!looper.isAlive()) {
            looper.start();
        }
    }

    Runnable managedTask(String operation, Runnable task) {
        return () -> runManagerTask(operation, task);
    }

    Runnable managedTask(String operation, Runnable task, Runnable unavailable) {
        return () -> {
            if (!runManagerTask(operation, task)) {
                unavailable.run();
            }
        };
    }

    @Override
    public void onTick() {

    }

    @Override
    public void onSave() {
        getEngine().getMantle().save();
    }

    public void requestBiomeInject(Position2 p) {
        injectBiomes.add(p);
    }

    @Override
    public void onChunkLoad(Chunk e, boolean generated) {
        if (getEngine().isClosed()) {
            return;
        }

        if (cleanupServiceStopped || cleanupService == null || cleanupService.isShutdown()) {
            return;
        }

        int cX = e.getX(), cZ = e.getZ();
        Long key = Cache.key(cX, cZ);
        long delay = Math.max(IrisSettings.get().getPerformance().mantleCleanupDelay * 50L, 0);
        final Future<?>[] self = new Future<?>[1];
        Runnable forget = () -> cleanup.remove(key, self[0]);
        Runnable task = managedTask("bukkit_world_manager_chunk_cleanup", () -> {
            forget.run();
            getEngine().cleanupMantleChunk(cX, cZ);
        }, forget);

        try {
            // compute() keys the bin so the task cannot drop a newer mapping than its own.
            cleanup.compute(key, (k, displaced) -> {
                if (displaced != null) {
                    displaced.cancel(false);
                }

                return self[0] = cleanupService.schedule(task, delay, TimeUnit.MILLISECONDS);
            });
        } catch (RejectedExecutionException ex) {
            IrisLogging.debug("Skipped mantle cleanup schedule for " + cX + ", " + cZ + "; cleanup executor is stopped.");
        }
    }

    @Override
    public void onChunkUnload(Chunk e) {
        final var future = cleanup.remove(Cache.key(e.getX(), e.getZ()));
        if (future != null) {
            future.cancel(false);
        }
    }

    public Mantle<Matter> getMantle() {
        return getEngine().getMantle().getMantle();
    }

    @Override
    public void teleportAsync(PlayerTeleportEvent e) {
        teleportWarmup.teleportAsync(e);
    }

    @Override
    public void onBlockBreak(BlockBreakEvent e) {
        blockDropRouter.onBlockBreak(e);
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent e) {
        blockDropRouter.onBlockPlace(e);
    }

    @Override
    public synchronized void close() {
        Throwable failure = null;
        try {
            super.close();
        } catch (Throwable e) {
            failure = e;
        }
        if (!looperStopped) {
            try {
                if (looper != null) {
                    looper.interrupt();
                    awaitLooper(looper);
                }
                looperStopped = true;
            } catch (Throwable e) {
                failure = appendCloseFailure(failure, e);
            }
        }
        if (!cleanupServiceStopped) {
            try {
                if (cleanupService != null) {
                    for (Future<?> future : cleanup.values()) {
                        future.cancel(false);
                    }
                    cleanup.clear();
                    cleanupService.shutdownNow();
                    awaitCleanupService(cleanupService);
                }
                cleanupServiceStopped = true;
            } catch (Throwable e) {
                failure = appendCloseFailure(failure, e);
            }
        }
        if (failure != null) {
            throw new IllegalStateException("Failed to completely stop the Bukkit Iris world manager.", failure);
        }
    }

    private void awaitLooper(Thread thread) {
        if (thread == Thread.currentThread()) {
            throw new IllegalStateException("Cannot finish world manager shutdown from its own loop thread.");
        }

        try {
            thread.join(CLOSE_AWAIT_MS);
            if (thread.isAlive()) {
                throw new IllegalStateException("Thread " + thread.getName() + " did not stop within " + CLOSE_AWAIT_MS + "ms.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the world manager loop to stop.", e);
        }
    }

    private void awaitCleanupService(ScheduledExecutorService service) {
        try {
            if (!service.awaitTermination(CLOSE_AWAIT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Mantle cleanup executor did not stop within " + CLOSE_AWAIT_MS + "ms.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for mantle cleanup to stop.", e);
        }
    }

    @Override
    public int getChunkCount() {
        return loadedChunkCount;
    }

    @Override
    public double getEntitySaturation() {
        if (!getEngine().getWorld().hasPlatformWorld()) {
            return 1;
        }

        return (double) entityCount / (loadedChunkCount + 1) * 1.28;
    }

    private static Throwable appendCloseFailure(Throwable failure, Throwable next) {
        if (failure == null) {
            return next;
        }
        if (failure != next) {
            failure.addSuppressed(next);
        }
        return failure;
    }
}
