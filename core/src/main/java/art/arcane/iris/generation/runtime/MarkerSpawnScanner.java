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

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;

import art.arcane.iris.generation.cache.Cache;
import art.arcane.iris.world.entity.IrisMarker;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.platform.bukkit.plugin.Chunks;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.matter.MatterMarker;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Resolves the mantle spawn markers of a chunk into spawners. On Folia the mantle read runs
 * asynchronously and only the obstruction check and the consumer callback hop back onto the region
 * thread that owns the chunk; obstructed markers are removed off-thread.
 */
final class MarkerSpawnScanner {
    private final IrisWorldManager manager;
    private final Set<Long> markerScanQueue = ConcurrentHashMap.newKeySet();

    MarkerSpawnScanner(IrisWorldManager manager) {
        this.manager = manager;
    }

    void scanMarkerSpawners(Chunk chunk, boolean initialOnly, Consumer<List<PreparedMarkerSpawn>> consumer) {
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        World world = chunk.getWorld();
        long key = Cache.key(chunkX, chunkZ);
        if (!markerScanQueue.add(key)) {
            return;
        }

        J.a(manager.managedTask("bukkit_world_manager_marker_scan", () -> {
            try {
                if (initialOnly && manager.getMantle().hasFlag(chunkX, chunkZ, MantleFlag.INITIAL_SPAWNED_MARKER)) {
                    markerScanQueue.remove(key);
                    return;
                }
                Map<IrisPosition, MarkerSpawnData> markerData = collectMarkerSpawnData(chunkX, chunkZ);
                boolean accepted = J.runRegion(world, chunkX, chunkZ,
                        manager.managedTask("bukkit_world_manager_marker_scan_region", () -> {
                            try {
                                if (!world.isChunkLoaded(chunkX, chunkZ) || !Chunks.isSafe(world, chunkX, chunkZ)) {
                                    return;
                                }
                                Chunk loaded = world.getChunkAt(chunkX, chunkZ);
                                int minimumY = manager.getEngine().getWorld().minHeight();
                                List<PreparedMarkerSpawn> prepared = new ArrayList<>(markerData.size());
                                for (Map.Entry<IrisPosition, MarkerSpawnData> entry : markerData.entrySet()) {
                                    IrisPosition position = entry.getKey();
                                    MarkerSpawnData data = entry.getValue();
                                    if (data.spawners.isEmpty()) {
                                        continue;
                                    }
                                    if (isMarkerObstructed(loaded, position, data.requiresEmptyAbove)) {
                                        removeMarkerAsync(position);
                                        continue;
                                    }
                                    prepared.add(new PreparedMarkerSpawn(new IrisPosition(position.getX(),
                                            position.getY() + minimumY, position.getZ()), data.spawners, data.environment));
                                }
                                consumer.accept(List.copyOf(prepared));
                            } finally {
                                markerScanQueue.remove(key);
                            }
                        }, () -> markerScanQueue.remove(key)));
                if (!accepted) {
                    markerScanQueue.remove(key);
                }
            } catch (SavedBiomeUnavailableException e) {
                markerScanQueue.remove(key);
            } catch (Throwable e) {
                markerScanQueue.remove(key);
                IrisLogging.reportError(e);
            }
        }, () -> markerScanQueue.remove(key)));
    }

    private Map<IrisPosition, MarkerSpawnData> collectMarkerSpawnData(int chunkX, int chunkZ) {
        if (manager.getEngine() instanceof IrisEngine engine) {
            engine.getGenerationHistoryRuntimeRouter().ifPresent(router -> router.biomes().prepareChunk(chunkX, chunkZ));
        }
        Map<IrisPosition, MarkerSpawnData> markerData = new KMap<>();
        manager.getMantle().iterateChunk(chunkX, chunkZ, MatterMarker.class, (x, y, z, t) -> {
            if (t.getTag().equals("cave_floor") || t.getTag().equals("cave_ceiling")) {
                return;
            }

            BiomeEnvironment environment;
            try {
                environment = manager.getEngine().getBiomeEnvironment((chunkX << 4) + x, y,
                        (chunkZ << 4) + z);
            } catch (SavedBiomeUnavailableException e) {
                if (e.isLoading()) {
                    throw e;
                }
                return;
            }
            IrisData definitions = environment.data();
            IrisMarker mark = definitions.getMarkerLoader().load(t.getTag());
            if (mark == null) {
                return;
            }

            IrisPosition position = new IrisPosition((chunkX << 4) + x, y, (chunkZ << 4) + z);
            MarkerSpawnData data = markerData.computeIfAbsent(position, k -> new MarkerSpawnData(environment));
            data.requiresEmptyAbove = data.requiresEmptyAbove || mark.isEmptyAbove();

            for (String i : mark.getSpawners()) {
                IrisSpawner spawner = definitions.getSpawnerLoader().load(i);
                if (spawner == null) {
                    IrisLogging.error("Cannot load spawner: " + i + " for marker on " + manager.getName());
                    continue;
                }
                if (spawner.isCompatExcluded()) {
                    continue;
                }
                spawner.setReferenceMarker(mark);
                data.spawners.add(spawner);
            }
        });

        return markerData;
    }

    private boolean isMarkerObstructed(Chunk chunk, IrisPosition relative, boolean requiresEmptyAbove) {
        if (!requiresEmptyAbove) {
            return false;
        }

        int minY = manager.getEngine().getWorld().minHeight();
        int markerY = WorldBlockDropRouter.toWorldY(relative.getY(), minY);
        if (markerY + 2 >= chunk.getWorld().getMaxHeight()) {
            return true;
        }

        int localX = relative.getX() & 15;
        int localZ = relative.getZ() & 15;
        return chunk.getBlock(localX, markerY + 1, localZ).getBlockData().getMaterial().isSolid()
                || chunk.getBlock(localX, markerY + 2, localZ).getBlockData().getMaterial().isSolid();
    }

    private void removeMarkerAsync(IrisPosition marker) {
        J.a(manager.managedTask("bukkit_world_manager_remove_marker", () -> {
            try {
                manager.getMantle().remove(marker.getX(), marker.getY(), marker.getZ(), MatterMarker.class);
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
        }));
    }

    record PreparedMarkerSpawn(IrisPosition position, KSet<IrisSpawner> spawners, BiomeEnvironment environment) {
    }

    private static final class MarkerSpawnData {
        private final KSet<IrisSpawner> spawners = new KSet<>();
        private final BiomeEnvironment environment;
        private boolean requiresEmptyAbove;

        private MarkerSpawnData(BiomeEnvironment environment) {
            this.environment = environment;
        }
    }
}
