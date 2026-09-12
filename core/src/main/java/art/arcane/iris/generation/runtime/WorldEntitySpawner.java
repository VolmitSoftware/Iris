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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.studio.view.PregeneratorJob;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.volmlib.util.math.Rarity;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.world.entity.IrisEntitySpawn;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.platform.bukkit.plugin.Chunks;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.math.RNG;
import lombok.Data;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.Optional;
import art.arcane.iris.generation.runtime.MarkerSpawnScanner.PreparedMarkerSpawn;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ambient and marker entity spawning for a Bukkit Iris world. Every count and spawn hops onto the
 * thread that owns the data it reads (global for synchronized whole-world snapshots, region for
 * the chunk being populated) and spawning is paused whenever a count could not be completed, so
 * an incomplete saturation reading can never authorize a spawn.
 */
final class WorldEntitySpawner {
    private final IrisWorldManager manager;
    private final AtomicInteger actuallySpawned = new AtomicInteger();
    private final AtomicBoolean entityCountWarningReported = new AtomicBoolean();
    private final AtomicBoolean entityCountErrorReported = new AtomicBoolean();
    private int cooldown = 0;

    WorldEntitySpawner(IrisWorldManager manager) {
        this.manager = manager;
    }

    boolean onAsyncTick() {
        if (manager.getEngine().isClosing() || manager.getEngine().isClosed()) {
            return false;
        }

        if (isPregenActiveForThisWorld()) {
            J.sleep(500);
            return false;
        }

        actuallySpawned.set(0);

        if (!manager.getEngine().getWorld().hasPlatformWorld()) {
            IrisLogging.debug("Can't spawn. No real world");
            J.sleep(5000);
            return false;
        }

        if (manager.cl.flip()) {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            try {
                World realWorld = BukkitWorldBinding.world(manager.getEngine().getWorld());
                if (realWorld == null) {
                    manager.entityCount = 0;
                    manager.entityCountValid = false;
                } else {
                    boolean scheduled = J.runGlobal(managedCompletion(
                            "bukkit_world_manager_entity_count", future, () -> livingEntityCount(realWorld)));
                    if (scheduled) {
                        Integer count = future.get(2, TimeUnit.SECONDS);
                        if (count == null || manager.getEngine().isClosing() || manager.getEngine().isClosed()) {
                            manager.entityCountValid = false;
                            return false;
                        }
                        manager.entityCount = count;
                        manager.entityCountValid = true;
                        resetEntityCountFailures();
                    } else {
                        reportEntityCountFailure("Unable to schedule the global entity count; pausing Iris entity spawning until a complete count is available.", null);
                    }
                }
            } catch (InterruptedException e) {
                manager.entityCountValid = false;
                Thread.currentThread().interrupt();
                return false;
            } catch (TimeoutException e) {
                reportEntityCountFailure("Timed out while counting entities; pausing Iris entity spawning until a complete count is available.", null);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                reportEntityCountFailure("Failed to count entities; pausing Iris entity spawning until a complete count is available.", cause);
            } catch (Throwable e) {
                reportEntityCountFailure("Failed to count entities; pausing Iris entity spawning until a complete count is available.", e);
            } finally {
                future.complete(null);
            }
        }

        if (!manager.entityCountValid) {
            return false;
        }

        double epx = manager.getEntitySaturation();
        if (epx > IrisSettings.get().getWorld().getTargetSpawnEntitiesPerChunk()) {
            IrisLogging.debug("Can't spawn. The entity per chunk ratio is at " + Form.pc(epx, 2) + " > 100% (total entities " + manager.entityCount + ")");
            J.sleep(5000);
            return false;
        }

        int spawnBuffer = RNG.r.i(2, 12);
        World world = BukkitWorldBinding.world(manager.getEngine().getWorld());
        if (world == null) {
            return false;
        }

        Position2[] cc = manager.chunkMaintenance.getLoadedChunkPositionsSnapshot(world);
        while (spawnBuffer-- > 0) {
            if (manager.getEngine().isClosing() || manager.getEngine().isClosed()) {
                return actuallySpawned.get() > 0;
            }

            if (cc.length == 0) {
                IrisLogging.debug("Can't spawn. No chunks!");
                return false;
            }

            Position2 c = cc[RNG.r.nextInt(cc.length)];
            if (!spawnChunkSafely(world, c.getX(), c.getZ(), false)) {
                return actuallySpawned.get() > 0;
            }
        }

        return actuallySpawned.get() > 0;
    }

    static int livingEntityCount(World world) {
        return world.getLivingEntities().size();
    }

    boolean isPregenActiveForThisWorld() {
        World world = BukkitWorldBinding.world(manager.getEngine().getWorld());
        if (world == null) {
            return false;
        }

        if (IrisToolbelt.isWorldMaintenanceActive(world)) {
            return true;
        }

        PregeneratorJob job = PregeneratorJob.getInstance();
        if (job == null) {
            return false;
        }

        return job.targetsWorldIdentity(WorldIdentity.serialize(world));
    }

    private boolean spawnChunkSafely(World world, int chunkX, int chunkZ, boolean initial) {
        if (world == null) {
            return false;
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        AtomicBoolean failureReported = new AtomicBoolean();
        future.whenComplete((ignored, failure) -> {
            if (failure != null) {
                reportSpawnFailure(chunkX, chunkZ, failure, failureReported);
            }
        });
        boolean scheduled;
        try {
            scheduled = J.runRegion(world, chunkX, chunkZ, managedCompletion(
                    "bukkit_world_manager_entity_spawn", future, () -> {
                        if (!world.isChunkLoaded(chunkX, chunkZ) || !Chunks.isSafe(world, chunkX, chunkZ)) {
                            return true;
                        }
                        spawnIn(world.getChunkAt(chunkX, chunkZ), initial);
                        return true;
                    }));
        } catch (Throwable e) {
            future.complete(null);
            IrisLogging.reportError("Failed to schedule an Iris entity spawn for chunk " + chunkX + "," + chunkZ + ".", e);
            return false;
        }

        if (!scheduled) {
            future.complete(null);
            IrisLogging.debug("Skipped Iris entity spawning because the region task was not accepted for chunk " + chunkX + "," + chunkZ + ".");
            return false;
        }

        try {
            return Boolean.TRUE.equals(future.get(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (TimeoutException e) {
            IrisLogging.warn("Timed out waiting for Iris entity spawning in chunk %d,%d; deferring the remaining spawn buffer.", chunkX, chunkZ);
            return false;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            reportSpawnFailure(chunkX, chunkZ, cause, failureReported);
            return false;
        } finally {
            future.complete(null);
        }
    }

    private <T> Runnable managedCompletion(String operation, CompletableFuture<T> future, Supplier<T> task) {
        Runnable managed = manager.managedTask(operation, () -> {
            if (!future.isDone()) {
                future.complete(task.get());
            }
        }, () -> future.complete(null));
        return () -> {
            try {
                managed.run();
            } catch (Throwable failure) {
                if (!future.completeExceptionally(failure)) {
                    IrisLogging.reportError("Failed to finish " + operation + ".", failure);
                }
            }
        };
    }

    private void reportEntityCountFailure(String message, Throwable error) {
        manager.entityCountValid = false;
        if (error != null) {
            if (entityCountErrorReported.compareAndSet(false, true)) {
                IrisLogging.reportError(message, error);
            }
            return;
        }

        if (entityCountWarningReported.compareAndSet(false, true)) {
            IrisLogging.warn(message);
        }
    }

    private void resetEntityCountFailures() {
        entityCountWarningReported.set(false);
        entityCountErrorReported.set(false);
    }

    private void reportSpawnFailure(int chunkX, int chunkZ, Throwable failure, AtomicBoolean failureReported) {
        if (!failureReported.compareAndSet(false, true)) {
            return;
        }
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        IrisLogging.reportError("Failed to spawn Iris entities in chunk " + chunkX + "," + chunkZ + ".", cause);
    }

    void spawnIn(Chunk c, boolean initial) {
        if (initial) {
            spawnInitially(c);
            return;
        }
        if (manager.getEngine().isClosed()) {
            return;
        }

        if (!isEntitySpawningEnabledForCurrentWorld()) {
            return;
        }

        IrisComplex complex = manager.getEngine().getComplex();
        if (complex == null) {
            return;
        }

        if (IrisSettings.get().getWorld().isMarkerEntitySpawningSystem()) {
            manager.markerScanner.scanMarkerSpawners(c, false, markers -> {
                for (PreparedMarkerSpawn marker : markers) {
                    spawnPreparedMarker(marker, false);
                }
                prepareInitialSpawn(c, markers);
            });
        }

        if (!IrisSettings.get().getWorld().isAmbientEntitySpawningSystem()) {
            return;
        }

        try {
            BiomeEnvironment environment = manager.getEngine().getSurfaceBiomeEnvironment(
                    (c.getX() << 4) + 8, (c.getZ() << 4) + 8);
            try (BiomeEnvironment.Scope ignored = manager.getEngine().openBiomeEnvironmentScope(environment)) {
                spawnAmbient(c, initial, environment);
            }
        } catch (SavedBiomeUnavailableException e) {
            return;
        }
    }

    void spawnInitially(Chunk chunk) {
        if (manager.getEngine().isClosed() || !isEntitySpawningEnabledForCurrentWorld()) {
            return;
        }
        if (IrisSettings.get().getWorld().isMarkerEntitySpawningSystem()) {
            manager.markerScanner.scanMarkerSpawners(chunk, true, markers -> prepareInitialSpawn(chunk, markers));
        } else if (IrisSettings.get().getWorld().isAmbientEntitySpawningSystem()) {
            prepareInitialSpawn(chunk, List.of());
        }
    }

    void prepareInitialSpawn(Chunk chunk, List<PreparedMarkerSpawn> markers) {
        Optional<BiomeEnvironment> environment = Optional.empty();
        if (IrisSettings.get().getWorld().isAmbientEntitySpawningSystem()) {
            try {
                environment = Optional.of(manager.getEngine().getSurfaceBiomeEnvironment(
                        (chunk.getX() << 4) + 8, (chunk.getZ() << 4) + 8));
            } catch (SavedBiomeUnavailableException e) {
                if (e.isLoading()) {
                    return;
                }
            }
        }
        Optional<BiomeEnvironment> preparedEnvironment = environment;
        manager.chunkMaintenance.raiseInitialSpawnMarkerFlag(chunk.getWorld(), chunk.getX(), chunk.getZ(), () -> {
            for (PreparedMarkerSpawn marker : markers) {
                spawnPreparedMarker(marker, true);
            }
            if (preparedEnvironment.isPresent()) {
                J.runRegion(chunk.getWorld(), chunk.getX(), chunk.getZ(),
                        manager.managedTask("bukkit_world_manager_initial_spawn_followup", () -> {
                            if (!chunk.getWorld().isChunkLoaded(chunk.getX(), chunk.getZ())) {
                                return;
                            }
                            BiomeEnvironment selected = preparedEnvironment.get();
                            try (BiomeEnvironment.Scope ignored = manager.getEngine().openBiomeEnvironmentScope(selected)) {
                                spawnAmbient(chunk, true, selected);
                            }
                        }), RNG.r.i(5, 200));
            }
        });
    }

    private void spawnPreparedMarker(PreparedMarkerSpawn marker, boolean initial) {
        IrisSpawner spawner = new KList<>(marker.spawners()).getRandom();
        if (spawner == null || manager.getEngine().isClosed()) {
            return;
        }
        try (BiomeEnvironment.Scope ignored = manager.getEngine().openBiomeEnvironmentScope(marker.environment())) {
            spawnMarker(marker.position(), spawner, initial);
        }
    }

    private void spawnAmbient(Chunk c, boolean initial, BiomeEnvironment environment) {
        //@builder
        // Excluded spawners cannot spawn anything on this Minecraft version, so they never enter the selection pool.
        Predicate<IrisSpawner> filter = i -> !i.isCompatExcluded() && i.canSpawn(manager.getEngine(), c.getX(), c.getZ());
        ChunkCounter counter = new ChunkCounter(c.getEntities());

        IrisBiome biome = environment.biome();
        IrisEntitySpawn v = spawnRandomly(Stream.concat(environment.data().getSpawnerLoader()
                                .loadAll(environment.dimension().getEntitySpawners())
                                .shuffleCopy(RNG.r)
                                .stream()
                                .filter(filter)
                                .filter((i) -> i.isValid(biome)),
                        Stream.concat(environment.data()
                                        .getSpawnerLoader()
                                        .loadAll(environment.region().getEntitySpawners())
                                        .shuffleCopy(RNG.r)
                                        .stream()
                                        .filter(filter),
                                environment.data().getSpawnerLoader()
                                        .loadAll(environment.biome().getEntitySpawners())
                                        .shuffleCopy(RNG.r)
                                        .stream()
                                        .filter(filter)))
                .filter(counter)
                .flatMap((i) -> stream(i, initial))
                .collect(Collectors.toList()))
                .getRandom();
        //@done
        if (v == null || v.getReferenceSpawner() == null)
            return;

        spawn(c, v);
    }

    private void spawn(Chunk c, IrisEntitySpawn i) {
        IrisSpawner ref = i.getReferenceSpawner();
        int s = i.spawn(manager.getEngine(), c, RNG.r);
        actuallySpawned.addAndGet(s);
        if (s > 0) {
            ref.spawn(manager.getEngine(), c.getX(), c.getZ());
        }
    }

    private void spawn(IrisPosition pos, IrisEntitySpawn i) {
        IrisSpawner ref = i.getReferenceSpawner();
        if (!ref.canSpawn(manager.getEngine(), PowerOfTwoCoordinates.blockToChunkFloor(pos.getX()), PowerOfTwoCoordinates.blockToChunkFloor(pos.getZ())))
            return;

        int s = i.spawn(manager.getEngine(), pos, RNG.r);
        actuallySpawned.addAndGet(s);
        if (s > 0) {
            ref.spawn(manager.getEngine(), PowerOfTwoCoordinates.blockToChunkFloor(pos.getX()), PowerOfTwoCoordinates.blockToChunkFloor(pos.getZ()));
        }
    }

    private void spawnMarker(IrisPosition block, IrisSpawner spawner, boolean initial) {
        KList<IrisEntitySpawn> s = initial ? spawner.getInitialSpawns() : spawner.getSpawns();
        if (s.isEmpty()) {
            return;
        }

        IrisEntitySpawn ss = spawnRandomly(s).getRandom();
        ss.setReferenceSpawner(spawner);
        ss.setReferenceMarker(spawner.getReferenceMarker());
        spawn(block, ss);
    }

    private Stream<IrisEntitySpawn> stream(IrisSpawner s, boolean initial) {
        for (IrisEntitySpawn i : initial ? s.getInitialSpawns() : s.getSpawns()) {
            i.setReferenceSpawner(s);
            i.setReferenceMarker(s.getReferenceMarker());
        }

        return (initial ? s.getInitialSpawns() : s.getSpawns()).stream();
    }

    boolean isEntitySpawningEnabledForCurrentWorld() {
        if (!manager.getEngine().isStudio()) {
            return true;
        }

        return IrisSettings.get().getStudio().isEntitySpawning();
    }

    private KList<IrisEntitySpawn> spawnRandomly(List<IrisEntitySpawn> types) {
        return Rarity.expandWeighted(types);
    }

    @Data
    private static class ChunkCounter implements Predicate<IrisSpawner> {
        private final Entity[] entities;
        private transient int index = 0;
        private transient int count = 0;

        @Override
        public boolean test(IrisSpawner spawner) {
            int max = spawner.getMaxEntitiesPerChunk();
            if (max <= count)
                return false;

            while (index < entities.length) {
                if (entities[index++] instanceof LivingEntity) {
                    if (++count >= max)
                        return false;
                }
            }

            return true;
        }
    }
}
