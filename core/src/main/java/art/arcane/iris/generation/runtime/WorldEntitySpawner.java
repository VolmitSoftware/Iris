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
import art.arcane.iris.generation.decoration.IrisSurface;
import art.arcane.iris.world.entity.IrisSpawnGroup;
import org.bukkit.Location;
import art.arcane.iris.world.entity.IrisEntitySpawn;
import art.arcane.iris.world.entity.EntitySpawnSeed;
import art.arcane.iris.world.entity.IrisMarker;
import art.arcane.iris.world.entity.IrisEntitySpawn.SpawnContext;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.platform.bukkit.BukkitEntityType;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.platform.bukkit.plugin.Chunks;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.Chunk;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import art.arcane.iris.generation.runtime.MarkerSpawnScanner.PreparedMarkerSpawn;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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

        if (!manager.chunkMaintenance.isInitialSpawnComplete(c.getX(), c.getZ())) {
            spawnInitially(c);
            return;
        }
        if (IrisSettings.get().getWorld().isMarkerEntitySpawningSystem()) {
            manager.markerScanner.scanMarkerSpawners(c, false, markers -> {
                for (PreparedMarkerSpawn marker : markers) {
                    spawnPreparedMarker(marker, false);
                }
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
        if (manager.getEngine().isClosed() || !isEntitySpawningEnabledForCurrentWorld()
                || manager.chunkMaintenance.isInitialSpawnComplete(chunk.getX(), chunk.getZ())
                || !Boolean.TRUE.equals(chunk.getWorld().getGameRuleValue(GameRules.SPAWN_MOBS))) {
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
        List<PreparedMarkerSpawn> ordered = new ArrayList<>(markers);
        ordered.sort(Comparator.comparingInt((PreparedMarkerSpawn marker) -> marker.position().getX())
                .thenComparingInt(marker -> marker.position().getY())
                .thenComparingInt(marker -> marker.position().getZ()));
        manager.chunkMaintenance.runInitialSpawn(chunk.getWorld(), chunk.getX(), chunk.getZ(), () -> {
            for (PreparedMarkerSpawn marker : ordered) {
                spawnPreparedMarker(marker, true);
            }
            if (preparedEnvironment.isPresent()) {
                BiomeEnvironment selected = preparedEnvironment.get();
                try (BiomeEnvironment.Scope ignored = manager.getEngine().openBiomeEnvironmentScope(selected)) {
                    spawnAmbient(chunk, true, selected);
                }
            }
        });
    }

    private void spawnPreparedMarker(PreparedMarkerSpawn marker, boolean initial) {
        if (manager.getEngine().isClosed()) {
            return;
        }
        RNG random = initial ? EntitySpawnSeed.marker(manager.getEngine().getSeedManager().getEntity(),
                marker.position().getX(), marker.position().getY(), marker.position().getZ()) : new RNG(RNG.r.nextLong());
        KList<IrisSpawner> spawners = new KList<>(marker.spawners());
        if (initial) {
            spawners.sort(Comparator.comparing(spawner -> Objects.toString(spawner.getLoadKey(), "")));
        }
        IrisSpawner spawner = spawners.getRandom(random);
        if (spawner == null) {
            return;
        }
        try (BiomeEnvironment.Scope ignored = manager.getEngine().openBiomeEnvironmentScope(marker.environment())) {
            spawnMarker(marker.position(), spawner, marker.marker(), random, initial);
        }
    }

    private void spawnAmbient(Chunk chunk, boolean initial, BiomeEnvironment environment) {
        if (!ambientAllowed(chunk, initial)) {
            return;
        }
        ChunkCounter counter = new ChunkCounter(initial ? new Entity[0] : chunk.getEntities());
        List<SpawnSelection> pool = new ArrayList<>();
        collectSpawns(pool, environment.data().getSpawnerLoader().loadAll(environment.dimension().getEntitySpawners()),
                environment.biome(), chunk, initial, counter, false);
        collectSpawns(pool, environment.data().getSpawnerLoader().loadAll(environment.region().getEntitySpawners()),
                null, chunk, initial, counter, false);
        collectSpawns(pool, environment.data().getSpawnerLoader().loadAll(environment.biome().getEntitySpawners()),
                null, chunk, initial, counter, false);
        RNG random = initial ? EntitySpawnSeed.chunk(manager.getEngine().getSeedManager().getEntity(),
                chunk.getX(), chunk.getZ()) : new RNG(RNG.r.nextLong());
        if (environment.dimension().hasUndergroundSpawners(manager.getEngine())) {
            spawnUnderground(chunk, initial, counter, random.nextParallelRNG(0x5A17D3B48269C0EFL));
        }
        SpawnSelection selected = selectSpawn(pool, random);
        if (selected == null) {
            return;
        }
        int capacity = counter.remainingCapacity(selected.entry(), selected.spawner(), manager.getEngine());
        int spawned = selected.entry().spawn(manager.getEngine(), chunk, random, capacity,
                new SpawnContext(selected.spawner(), null, initial));
        actuallySpawned.addAndGet(spawned);
        counter.recordSpawned(selected.entry(), manager.getEngine(), spawned);
        if (spawned > 0 && !initial) {
            selected.spawner().spawn(manager.getEngine(), chunk.getX(), chunk.getZ());
        }
    }

    private static boolean matchesUndergroundSurface(IrisEntitySpawn entry, Engine engine, IrisSurface surface) {
        IrisEntity entity = entry.getRealEntity(engine);
        return entity != null && entity.getSurface().isFluid() == surface.isFluid()
                && (!surface.isFluid() || entity.getSurface() == surface);
    }

    private void spawnUnderground(Chunk chunk, boolean initial, ChunkCounter counter, RNG random) {
        if (J.isFolia() && !J.isOwnedByCurrentRegion(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
            return;
        }
        for (IrisSurface surface : List.of(IrisSurface.LAND, IrisSurface.WATER, IrisSurface.LAVA)) {
            Location candidate = IrisEntitySpawn.findLiveCaveSpawnLocation(
                    chunk, random.nextParallelRNG(surface.ordinal()), surface);
            if (candidate == null) {
                continue;
            }
            Engine engine = manager.getEngine();
            BiomeEnvironment environment;
            try {
                environment = engine.getBiomeOrMantleEnvironment(candidate.getBlockX(),
                        candidate.getBlockY() - engine.getWorld().minHeight(), candidate.getBlockZ());
            } catch (SavedBiomeUnavailableException unavailable) {
                continue;
            }
            try (BiomeEnvironment.Scope ignored = engine.openBiomeEnvironmentScope(environment)) {
                List<SpawnSelection> pool = new ArrayList<>();
                collectSpawns(pool, environment.data().getSpawnerLoader().loadAll(environment.dimension().getEntitySpawners()),
                        environment.biome(), chunk, initial, counter, true);
                collectSpawns(pool, environment.data().getSpawnerLoader().loadAll(environment.region().getEntitySpawners()),
                        null, chunk, initial, counter, true);
                collectSpawns(pool, environment.data().getSpawnerLoader().loadAll(environment.biome().getEntitySpawners()),
                        null, chunk, initial, counter, true);
                pool.removeIf(selection -> !matchesUndergroundSurface(selection.entry(), engine, surface));
                SpawnSelection selected = selectSpawn(pool, random);
                if (selected == null) {
                    continue;
                }
                int capacity = counter.remainingCapacity(selected.entry(), selected.spawner(), engine);
                int spawned = selected.entry().spawn(engine, candidate, random, capacity,
                        new SpawnContext(selected.spawner(), null, initial));
                actuallySpawned.addAndGet(spawned);
                counter.recordSpawned(selected.entry(), engine, spawned);
                if (spawned > 0 && !initial) {
                    selected.spawner().spawn(engine, chunk.getX(), chunk.getZ());
                }
                return;
            }
        }
    }

    private void collectSpawns(List<SpawnSelection> pool, KList<IrisSpawner> spawners, IrisBiome biome,
                               Chunk chunk, boolean initial, ChunkCounter counter, boolean underground) {
        for (IrisSpawner spawner : spawners) {
            if ((spawner.getGroup() == IrisSpawnGroup.CAVE) != underground
                    || !spawnerAllowed(spawner, manager.getEngine(), chunk.getX(), chunk.getZ(), initial)
                    || biome != null && !spawner.isValid(biome)) {
                continue;
            }
            for (IrisEntitySpawn entry : initial ? spawner.getInitialSpawns() : spawner.getSpawns()) {
                IrisEntity entity = entry.getRealEntity(manager.getEngine());
                int capacity = counter.remainingCapacity(entry, spawner, manager.getEngine());
                if (entity != null && capacity > 0) {
                    pool.add(new SpawnSelection(entry, spawner));
                }
            }
        }
    }

    static boolean spawnerAllowed(IrisSpawner spawner, Engine engine, int chunkX, int chunkZ, boolean initial) {
        return !spawner.isCompatExcluded() && (initial || spawner.canSpawn(engine, chunkX, chunkZ));
    }

    static SpawnSelection selectSpawn(List<SpawnSelection> pool, RNG random) {
        return Rarity.expandWeighted(pool).getRandom(random);
    }

    static boolean ambientAllowed(Chunk chunk, boolean initial) {
        return Boolean.TRUE.equals(chunk.getWorld().getGameRuleValue(GameRules.SPAWN_MOBS))
                && (initial || chunk.getLoadLevel() == Chunk.LoadLevel.ENTITY_TICKING);
    }

    private void spawnMarker(IrisPosition position, IrisSpawner spawner, IrisMarker marker, RNG random, boolean initial) {
        int chunkX = PowerOfTwoCoordinates.blockToChunkFloor(position.getX());
        int chunkZ = PowerOfTwoCoordinates.blockToChunkFloor(position.getZ());
        if (!spawnerAllowed(spawner, manager.getEngine(), chunkX, chunkZ, initial)) {
            return;
        }
        KList<IrisEntitySpawn> entries = initial ? spawner.getInitialSpawns() : spawner.getSpawns();
        IrisEntitySpawn selected = Rarity.expandWeighted(entries).getRandom(random);
        if (selected == null) {
            return;
        }
        int spawned = selected.spawn(manager.getEngine(), position, random, new SpawnContext(spawner, marker, initial));
        actuallySpawned.addAndGet(spawned);
        if (spawned > 0 && !initial) {
            spawner.spawn(manager.getEngine(), chunkX, chunkZ);
        }
    }

    boolean isEntitySpawningEnabledForCurrentWorld() {
        if (!manager.getEngine().isStudio()) {
            return true;
        }

        return IrisSettings.get().getStudio().isEntitySpawning();
    }

    record SpawnSelection(IrisEntitySpawn entry, IrisSpawner spawner) implements Rarity {
        @Override
        public int getRarity() {
            return entry.getRarity();
        }
    }

    static final class ChunkCounter {
        private final Map<String, Integer> counts = new HashMap<>();

        ChunkCounter(Entity[] entities) {
            for (Entity entity : entities) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    counts.merge(BukkitEntityType.of(entity.getType()).spawnCategory(), 1, Integer::sum);
                }
            }
        }

        void recordSpawned(IrisEntitySpawn entry, Engine engine, int spawned) {
            IrisEntity entity = entry.getRealEntity(engine);
            if (spawned > 0 && entity != null) {
                counts.merge(entity.spawnCategory(), spawned, Integer::sum);
            }
        }

        int remainingCapacity(IrisEntitySpawn entry, IrisSpawner spawner, Engine engine) {
            IrisEntity entity = entry.getRealEntity(engine);
            return spawner.remainingCapacity(entity, counts);
        }
    }
}
