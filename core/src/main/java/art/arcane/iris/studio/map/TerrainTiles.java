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

package art.arcane.iris.studio.map;

import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.spi.IrisLogging;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Tiles of one noise, sampled on worker threads in the order last asked for, and kept as long as memory allows. Tiles
 * no longer asked for are not sampled, so moving the camera never waits on ground it has left behind. Ground the view
 * showed before the noise or its heights changed stands in until its tile is sampled anew, so the view never empties
 * while it catches up.
 */
final class TerrainTiles {
    private static final long FIRST_RETRY_NANOS = TimeUnit.MILLISECONDS.toNanos(500L);
    private static final long MAX_RETRY_NANOS = TimeUnit.SECONDS.toNanos(8L);
    // The map runs inside the server, so the tiles stay well clear of what the server itself needs.
    private static final double SHARE_OF_HEAP = 0.25D;
    // A column's height and colour, and its share of the block faces drawn for it.
    private static final long ESTIMATED_BYTES_PER_COLUMN = 64L;

    private final TerrainSampling sampling;
    private final ExecutorService workers;
    private final int workerCount;
    private final Runnable onTileSampled;
    private final int mostTiles = mostTilesInMemory();
    private final Object lock = new Object();
    // Least recently asked for first.
    private final LinkedHashMap<TerrainTile, TerrainLayer> sampled = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<TerrainTile, TerrainLayer> standIns;
    private final Map<TerrainTile, Retry> retries = new HashMap<>();
    private final Set<TerrainTile> inProgress = new HashSet<>();
    private List<TerrainTile> wanted = List.of();
    private Set<TerrainTile> inUse = Set.of();
    private int nextWanted;
    private int runningWorkers;
    private boolean closed;

    /**
     * @param onTileSampled told, on a worker thread, whenever another tile is ready
     * @param standIns      ground shown in place of tiles until they are sampled, such as {@link #layersInUse} of the
     *                      tiles these replace
     */
    TerrainTiles(TerrainSampling sampling, ExecutorService workers, int workerCount, Runnable onTileSampled,
                 Map<TerrainTile, TerrainLayer> standIns) {
        this.sampling = sampling;
        this.workers = workers;
        this.workerCount = workerCount;
        this.onTileSampled = onTileSampled;
        this.standIns = new HashMap<>(standIns);
    }

    static long mostColumnsInMemory() {
        return (long) (Runtime.getRuntime().maxMemory() * SHARE_OF_HEAP) / ESTIMATED_BYTES_PER_COLUMN;
    }

    private static int mostTilesInMemory() {
        return (int) Math.max(1L, mostColumnsInMemory() / ((long) TerrainTile.COLUMNS * TerrainTile.COLUMNS));
    }

    TerrainSampling sampling() {
        return sampling;
    }

    /**
     * The tile as a whole layer, or what stands in for it, or null while there is neither.
     */
    TerrainLayer layerOf(TerrainTile tile) {
        synchronized (lock) {
            TerrainLayer layer = sampled.get(tile);
            return layer != null ? layer : standIns.get(tile);
        }
    }

    /**
     * Whether {@link #layerOf} has ground for the tile. Unlike layerOf, asking doesn't count as using the tile, so it
     * isn't kept in memory any longer for it.
     */
    boolean isSampled(TerrainTile tile) {
        synchronized (lock) {
            return sampled.containsKey(tile) || standIns.containsKey(tile);
        }
    }

    // Ground that only stands in still wants sampling.
    boolean isStandingIn(TerrainTile tile) {
        synchronized (lock) {
            return !sampled.containsKey(tile) && standIns.containsKey(tile);
        }
    }

    /**
     * The ground last drawn from these tiles, to stand in for the tiles that replace them.
     */
    Map<TerrainTile, TerrainLayer> layersInUse() {
        synchronized (lock) {
            Map<TerrainTile, TerrainLayer> layers = new HashMap<>();
            for (TerrainTile tile : inUse) {
                TerrainLayer layer = sampled.containsKey(tile) ? sampled.get(tile) : standIns.get(tile);
                if (layer != null) {
                    layers.put(tile, layer);
                }
            }
            return layers;
        }
    }

    /**
     * Samples these tiles, first to last, instead of any asked for before. Tiles beyond what memory holds are forgotten,
     * those asked for least recently first, but never any in {@code inUse}.
     */
    void sampleOnly(List<TerrainTile> nextWanted, Set<TerrainTile> inUse) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            wanted = nextWanted;
            this.nextWanted = 0;
            this.inUse = Set.copyOf(inUse);
            Set<TerrainTile> retained = new HashSet<>(nextWanted);
            retained.addAll(inUse);
            retries.keySet().removeIf(tile -> !retained.contains(tile));
            standIns.keySet().removeIf(tile -> !retained.contains(tile));
            forgetBeyondMemory(inUse);
            startWorkers();
        }
    }

    int pendingCount() {
        synchronized (lock) {
            int waiting = 0;
            for (TerrainTile tile : wanted) {
                if (!sampled.containsKey(tile) && !inProgress.contains(tile)) {
                    waiting++;
                }
            }
            return waiting + inProgress.size();
        }
    }

    long retryDelayNanos() {
        synchronized (lock) {
            long earliest = Long.MAX_VALUE;
            for (TerrainTile tile : wanted) {
                Retry retry = retries.get(tile);
                if (retry != null && !inProgress.contains(tile)) {
                    earliest = Math.min(earliest, retry.readyAtNanos());
                }
            }
            return earliest == Long.MAX_VALUE ? -1L : Math.max(0L, earliest - System.nanoTime());
        }
    }

    void close() {
        synchronized (lock) {
            closed = true;
            wanted = List.of();
            sampled.clear();
            standIns.clear();
            retries.clear();
        }
    }

    private void forgetBeyondMemory(Set<TerrainTile> inUse) {
        Iterator<Map.Entry<TerrainTile, TerrainLayer>> oldestFirst = sampled.entrySet().iterator();
        while (sampled.size() > mostTiles && oldestFirst.hasNext()) {
            if (!inUse.contains(oldestFirst.next().getKey())) {
                oldestFirst.remove();
            }
        }
    }

    private void startWorkers() {
        while (runningWorkers < workerCount && nextWanted < wanted.size()) {
            runningWorkers++;
            try {
                workers.execute(this::sampleWhileWanted);
            } catch (RejectedExecutionException rejected) {
                runningWorkers--;
                if (!workers.isShutdown()) {
                    throw rejected;
                }
                return;
            }
        }
    }

    private void sampleWhileWanted() {
        TerrainTile tile;
        while ((tile = takeNextWanted()) != null) {
            long revision = ProvisionalSampling.revision();
            TerrainLayer layer = sampleSafely(tile);
            boolean provisional = revision != ProvisionalSampling.revision();
            synchronized (lock) {
                inProgress.remove(tile);
                if (closed) {
                    runningWorkers--;
                    return;
                }
                if (provisional) {
                    standIns.put(tile, layer);
                    Retry previous = retries.get(tile);
                    long delay = previous == null ? FIRST_RETRY_NANOS : Math.min(MAX_RETRY_NANOS, previous.delayNanos() * 2L);
                    retries.put(tile, new Retry(System.nanoTime() + delay, delay));
                } else {
                    sampled.put(tile, layer);
                    standIns.remove(tile);
                    retries.remove(tile);
                }
            }
            onTileSampled.run();
        }
    }

    private TerrainTile takeNextWanted() {
        synchronized (lock) {
            while (!closed && nextWanted < wanted.size()) {
                TerrainTile tile = wanted.get(nextWanted++);
                Retry retry = retries.get(tile);
                if (retry != null && System.nanoTime() < retry.readyAtNanos()) {
                    continue;
                }
                if (!sampled.containsKey(tile) && inProgress.add(tile)) {
                    return tile;
                }
            }
            runningWorkers--;
            return null;
        }
    }

    // Noise that fails somewhere shows a gap there rather than stopping the whole view.
    private TerrainLayer sampleSafely(TerrainTile tile) {
        try {
            NoiseTerrain terrain = NoiseTerrain.sample(sampling, tile.grid());
            return TerrainLayer.of(terrain, sampling.floorY());
        } catch (RuntimeException error) {
            IrisLogging.reportError(error);
            return TerrainLayer.of(NoiseTerrain.empty(tile.grid()), sampling.floorY());
        }
    }

    private record Retry(long readyAtNanos, long delayNanos) {
    }
}
