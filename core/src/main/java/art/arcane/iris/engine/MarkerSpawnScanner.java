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

import art.arcane.iris.engine.data.cache.Cache;
import art.arcane.iris.engine.object.marker.IrisMarker;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.engine.object.entity.spawn.IrisSpawner;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.plugin.Chunks;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.matter.MatterMarker;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

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

    Map<IrisPosition, KSet<IrisSpawner>> getSpawnersFromMarkers(Chunk c) {
        Map<IrisPosition, KSet<IrisSpawner>> p = new KMap<>();
        Set<IrisPosition> b = new KSet<>();

        if (J.isFolia()) {
            if (!manager.getMantle().isChunkLoaded(c.getX(), c.getZ())) {
                manager.chunkMaintenance.warmupMantleChunkAsync(c.getX(), c.getZ());
            }
            return p;
        }

        manager.getMantle().iterateChunk(c.getX(), c.getZ(), MatterMarker.class, (x, y, z, t) -> {
            if (t.getTag().equals("cave_floor") || t.getTag().equals("cave_ceiling")) {
                return;
            }

            IrisMarker mark = manager.getData().getMarkerLoader().load(t.getTag());
            if (mark == null) {
                return;
            }

            IrisPosition pos = new IrisPosition((c.getX() << 4) + x, y, (c.getZ() << 4) + z);

            if (isMarkerObstructed(c, pos, mark.isEmptyAbove())) {
                b.add(pos);
                return;
            }

            for (String i : mark.getSpawners()) {
                IrisSpawner m = manager.getData().getSpawnerLoader().load(i);
                if (m == null) {
                    IrisLogging.error("Cannot load spawner: " + i + " for marker on " + manager.getName());
                    continue;
                }
                m.setReferenceMarker(mark);

                // This is so fucking incorrect its a joke
                //noinspection ConstantConditions
                if (m != null) {
                    p.computeIfAbsent(pos, (k) -> new KSet<>()).add(m);
                }
            }
        });

        for (IrisPosition i : b) {
            manager.getEngine().getMantle().getMantle().remove(i.getX(), i.getY(), i.getZ(), MatterMarker.class);
        }

        return p;
    }

    void forEachMarkerSpawner(Chunk c, BiConsumer<IrisPosition, KSet<IrisSpawner>> consumer) {
        if (c == null || consumer == null) {
            return;
        }

        if (!J.isFolia()) {
            int minY = manager.getEngine().getWorld().minHeight();
            getSpawnersFromMarkers(c).forEach((relative, spawners) -> {
                if (spawners.isEmpty()) {
                    return;
                }

                consumer.accept(new IrisPosition(relative.getX(), relative.getY() + minY, relative.getZ()), spawners);
            });
            return;
        }

        int chunkX = c.getX();
        int chunkZ = c.getZ();
        World world = c.getWorld();
        long key = Cache.key(chunkX, chunkZ);
        if (!markerScanQueue.add(key)) {
            return;
        }

        J.a(manager.managedTask("bukkit_world_manager_marker_scan", () -> {
            try {
                Map<IrisPosition, MarkerSpawnData> markerData = collectMarkerSpawnData(chunkX, chunkZ);
                if (markerData.isEmpty()) {
                    return;
                }

                J.runRegion(world, chunkX, chunkZ, manager.managedTask("bukkit_world_manager_marker_scan_region", () -> {
                    if (!world.isChunkLoaded(chunkX, chunkZ) || !Chunks.isSafe(world, chunkX, chunkZ)) {
                        return;
                    }

                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    int minY = manager.getEngine().getWorld().minHeight();
                    markerData.forEach((relative, data) -> {
                        if (data.spawners.isEmpty()) {
                            return;
                        }

                        if (isMarkerObstructed(chunk, relative, data.requiresEmptyAbove)) {
                            removeMarkerAsync(relative);
                            return;
                        }

                        consumer.accept(new IrisPosition(relative.getX(), relative.getY() + minY, relative.getZ()), data.spawners);
                    });
                }));
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            } finally {
                markerScanQueue.remove(key);
            }
        }, () -> markerScanQueue.remove(key)));
    }

    private Map<IrisPosition, MarkerSpawnData> collectMarkerSpawnData(int chunkX, int chunkZ) {
        Map<IrisPosition, MarkerSpawnData> markerData = new KMap<>();
        manager.getMantle().iterateChunk(chunkX, chunkZ, MatterMarker.class, (x, y, z, t) -> {
            if (t.getTag().equals("cave_floor") || t.getTag().equals("cave_ceiling")) {
                return;
            }

            IrisMarker mark = manager.getData().getMarkerLoader().load(t.getTag());
            if (mark == null) {
                return;
            }

            IrisPosition position = new IrisPosition((chunkX << 4) + x, y, (chunkZ << 4) + z);
            MarkerSpawnData data = markerData.computeIfAbsent(position, k -> new MarkerSpawnData());
            data.requiresEmptyAbove = data.requiresEmptyAbove || mark.isEmptyAbove();

            for (String i : mark.getSpawners()) {
                IrisSpawner spawner = manager.getData().getSpawnerLoader().load(i);
                if (spawner == null) {
                    IrisLogging.error("Cannot load spawner: " + i + " for marker on " + manager.getName());
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

    private static final class MarkerSpawnData {
        private final KSet<IrisSpawner> spawners = new KSet<>();
        private boolean requiresEmptyAbove;
    }
}
