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
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.plugin.Chunks;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import com.volmit.service.EntitySpawns;
import art.arcane.volmlib.util.matter.MatterMarker;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** A spawner a marker names, and the marker. */
    record MarkerSpawner(String marker, String spawner) {
    }

    Map<IrisPosition, List<MarkerSpawner>> getSpawnersFromMarkers(Chunk c) {
        Map<IrisPosition, List<MarkerSpawner>> p = new KMap<>();
        Set<IrisPosition> b = new KSet<>();

        if (J.isFolia()) {
            if (!manager.getMantle().isChunkLoaded(c.getX(), c.getZ())) {
                manager.chunkMaintenance.warmupMantleChunkAsync(c.getX(), c.getZ());
            }
            return p;
        }

        manager.getMantle().iterateChunk(c.getX(), c.getZ(), MatterMarker.class, (x, y, z, t) -> {
            Optional<EntitySpawns.MarkerRule> rule = markerRule(t);
            if (rule.isEmpty()) {
                return;
            }

            IrisPosition pos = new IrisPosition((c.getX() << 4) + x, y, (c.getZ() << 4) + z);

            if (isMarkerObstructed(c, pos, rule.get().emptyAbove())) {
                b.add(pos);
                return;
            }

            for (String spawner : rule.get().spawners()) {
                addOnce(p.computeIfAbsent(pos, (k) -> new ArrayList<>()), new MarkerSpawner(t.getTag(), spawner));
            }
        });

        for (IrisPosition i : b) {
            manager.getEngine().getMantle().getMantle().remove(i.getX(), i.getY(), i.getZ(), MatterMarker.class);
        }

        return p;
    }

    /** What the modules say the marker means; empty for the cave markers Iris keeps for itself and for markers no module knows. */
    private Optional<EntitySpawns.MarkerRule> markerRule(MatterMarker marker) {
        if (marker.getTag().equals("cave_floor") || marker.getTag().equals("cave_ceiling")) {
            return Optional.empty();
        }
        return ModuleServices.markerRule(manager.getEngine(), marker.getTag());
    }

    void forEachMarkerSpawner(Chunk c, BiConsumer<IrisPosition, List<MarkerSpawner>> consumer) {
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

    private static void addOnce(List<MarkerSpawner> spawners, MarkerSpawner spawner) {
        if (!spawners.contains(spawner)) {
            spawners.add(spawner);
        }
    }

    private Map<IrisPosition, MarkerSpawnData> collectMarkerSpawnData(int chunkX, int chunkZ) {
        Map<IrisPosition, MarkerSpawnData> markerData = new KMap<>();
        manager.getMantle().iterateChunk(chunkX, chunkZ, MatterMarker.class, (x, y, z, t) -> {
            Optional<EntitySpawns.MarkerRule> rule = markerRule(t);
            if (rule.isEmpty()) {
                return;
            }

            IrisPosition position = new IrisPosition((chunkX << 4) + x, y, (chunkZ << 4) + z);
            MarkerSpawnData data = markerData.computeIfAbsent(position, k -> new MarkerSpawnData());
            data.requiresEmptyAbove = data.requiresEmptyAbove || rule.get().emptyAbove();

            for (String spawner : rule.get().spawners()) {
                addOnce(data.spawners, new MarkerSpawner(t.getTag(), spawner));
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
        private final List<MarkerSpawner> spawners = new ArrayList<>();
        private boolean requiresEmptyAbove;
    }
}
