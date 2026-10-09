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
import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.engine.platform.BukkitEntitySpawner;
import art.arcane.iris.engine.platform.BukkitSurfaces;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.math.Vector3d;
import art.arcane.volmlib.util.matter.MatterMarker;
import com.volmit.entity.EntitySpec;
import com.volmit.service.EntitySpawns;
import com.volmit.world.BlockPosition;
import art.arcane.iris.core.gui.PregeneratorJob;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.plugin.Chunks;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
            try {
                World realWorld = BukkitWorldBinding.world(manager.getEngine().getWorld());
                if (realWorld == null) {
                    manager.entityCount = 0;
                    manager.entityCountValid = false;
                } else {
                    CompletableFuture<Integer> future = new CompletableFuture<>();
                    boolean scheduled = J.runGlobal(() -> {
                        try {
                            future.complete(livingEntityCount(realWorld));
                        } catch (Throwable ex) {
                            future.completeExceptionally(ex);
                        }
                    });
                    if (scheduled) {
                        manager.entityCount = future.get(2, TimeUnit.SECONDS);
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

        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicBoolean failureReported = new AtomicBoolean();
        future.whenComplete((ignored, failure) -> {
            if (failure != null) {
                reportSpawnFailure(chunkX, chunkZ, failure, failureReported);
            }
        });
        boolean scheduled;
        try {
            scheduled = J.runRegion(world, chunkX, chunkZ, () -> {
                try {
                    if (!world.isChunkLoaded(chunkX, chunkZ) || !Chunks.isSafe(world, chunkX, chunkZ)) {
                        future.complete(null);
                        return;
                    }

                    spawnIn(world.getChunkAt(chunkX, chunkZ), initial);
                    future.complete(null);
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (Throwable e) {
            IrisLogging.reportError("Failed to schedule an Iris entity spawn for chunk " + chunkX + "," + chunkZ + ".", e);
            return false;
        }

        if (!scheduled) {
            IrisLogging.debug("Skipped Iris entity spawning because the region task was not accepted for chunk " + chunkX + "," + chunkZ + ".");
            return false;
        }

        try {
            future.get(5, TimeUnit.SECONDS);
            return true;
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
        }
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
            manager.markerScanner.forEachMarkerSpawner(c, (block, spawners) -> {
                MarkerSpawnScanner.MarkerSpawner s = spawners.get(RNG.r.nextInt(spawners.size()));
                spawnAtMarker(block, s, false);
                J.runRegion(c.getWorld(), c.getX(), c.getZ(), manager.managedTask(
                        "bukkit_world_manager_marker_spawn_followup",
                        () -> manager.chunkMaintenance.raiseInitialSpawnMarkerFlag(c.getWorld(), c.getX(), c.getZ(),
                                () -> spawnAtMarker(block, s, true))));
            });
        }

        if (!IrisSettings.get().getWorld().isAmbientEntitySpawningSystem()) {
            return;
        }

        int livingEntities = (int) Arrays.stream(c.getEntities()).filter(LivingEntity.class::isInstance).count();
        ModuleServices.planSpawn(manager.getEngine(), new EntitySpawns.Chunk(c.getX(), c.getZ(), livingEntities, initial))
                .ifPresent(planned -> spawn(c.getWorld(), planned));
    }

    /** Spawns at a marker: the spawner of the marker the caller picked, with its initial spawns or its ordinary ones. */
    void spawnAtMarker(IrisPosition block, MarkerSpawnScanner.MarkerSpawner spawner, boolean initial) {
        if (manager.getEngine().isClosed() || !BukkitWorldBinding.tryBind(manager.getEngine().getWorld())) {
            return;
        }

        BlockPosition position = new BlockPosition(block.getX(), block.getY() - manager.getEngine().getWorld().minHeight(), block.getZ());
        EntitySpawns.Marker site = new EntitySpawns.Marker(position, spawner.marker(), spawner.spawner(), initial);
        ModuleServices.planSpawn(manager.getEngine(), site).ifPresent(planned -> {
            if (planned.plan().exhaustsMarker()) {
                removeExhaustedMarker(block);
            }
            spawn(BukkitWorldBinding.world(manager.getEngine().getWorld()), planned);
        });
    }

    private void removeExhaustedMarker(IrisPosition marker) {
        int relativeY = marker.getY() - manager.getEngine().getWorld().minHeight();
        Runnable remove = () -> manager.getEngine().getMantle().getMantle().remove(marker.getX(), relativeY, marker.getZ(), MatterMarker.class);
        if (J.isFolia()) {
            J.a(remove);
            return;
        }
        remove.run();
    }

    private void spawn(World world, ModuleServices.PlannedSpawn planned) {
        int spawned = 0;
        for (EntitySpawns.Attempt attempt : planned.plan().attempts()) {
            if (spawnIfAllowed(world, attempt)) {
                spawned++;
            }
        }
        actuallySpawned.addAndGet(spawned);
        planned.spawned(spawned);
    }

    /** Spawns the attempt's entity unless the light, the surface below or the space around it forbid it. */
    private boolean spawnIfAllowed(World world, EntitySpawns.Attempt attempt) {
        EntitySpec entity = attempt.entity();
        try {
            Location l = new Location(world, attempt.at().x(), attempt.at().y() + world.getMinHeight(), attempt.at().z());
            if (attempt.restrictsLight() && !lightAllows(l, attempt)) {
                return false;
            }

            if (attempt.checkSurface() && !surfaceAllows(l, attempt)) {
                return false;
            }

            Entity e = BukkitEntitySpawner.spawn(manager.getEngine(), entity, l.add(0.5, 0.5, 0.5));
            if (e != null) {
                IrisLogging.debug("Spawned " + C.DARK_AQUA + "Entity<" + entity.type() + "> " + C.GREEN + e.getType() + C.LIGHT_PURPLE + " @ " + C.GRAY + e.getLocation().getX() + ", " + e.getLocation().getY() + ", " + e.getLocation().getZ());
            }
            return e != null;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            IrisLogging.error("      Failed to retrieve real entity @ " + attempt.at() + " (entity: " + entity.type() + ")");
            return false;
        }
    }

    private static boolean lightAllows(Location l, EntitySpawns.Attempt attempt) {
        int light = l.getBlock().getLightLevel();
        return light >= attempt.minLight() && light <= attempt.maxLight();
    }

    private static boolean surfaceAllows(Location l, EntitySpawns.Attempt attempt) {
        if (!BukkitSurfaces.matches(attempt.surface(), l.clone().subtract(0, 1, 0).getBlock())) {
            return false;
        }

        Vector3d boundingBox = BukkitPlatform.entityBoundingBox(BukkitEntitySpawner.bukkitType(attempt.entity()));
        return boundingBox == null || isAreaClearForSpawn(l, boundingBox);
    }

    private static boolean isAreaClearForSpawn(Location center, Vector3d boundingBox) {
        World world = center.getWorld();
        int startX = center.getBlockX() - (int) (boundingBox.x / 2);
        int endX = center.getBlockX() + (int) (boundingBox.x / 2);
        int startY = center.getBlockY();
        int endY = center.getBlockY() + (int) boundingBox.y;
        int startZ = center.getBlockZ() - (int) (boundingBox.z / 2);
        int endZ = center.getBlockZ() + (int) (boundingBox.z / 2);

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    if (world.getBlockAt(x, y, z).getType() != Material.AIR) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    boolean isEntitySpawningEnabledForCurrentWorld() {
        if (!manager.getEngine().isStudio()) {
            return true;
        }

        return IrisSettings.get().getStudio().isEntitySpawning();
    }
}
