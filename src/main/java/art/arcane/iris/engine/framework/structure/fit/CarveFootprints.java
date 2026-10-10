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

package art.arcane.iris.engine.framework.structure.fit;

import art.arcane.iris.engine.mantle.components.StructureCarvingFootprint;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-column occupancy of whole starts for organic carving, kept because a structure spans many chunks and every one of
 * them carves against the same shrinkwrapped footprint. Keyed weakly on the platform's own key of the start, so retired
 * starts (world unload, generation done) are collectable; values hold only primitives, so no value pins its key. The
 * cached cells stay inside a budget, the oldest starts giving way first.
 */
final class CarveFootprints {
    private static final int MAX_CACHED_CARVE_CELLS = 2_000_000;
    private static final int MAX_CARVE_COLUMNS = 2_000_000;
    private static final Map<Object, Map<Integer, CachedCarveFootprint>> CARVE_FOOTPRINTS = new WeakHashMap<>(16);
    private static final ConcurrentHashMap<CarveFootprintKey, CompletableFuture<StructureCarvingFootprint>> BUILDS =
            new ConcurrentHashMap<>();
    private static int cachedCarveCells;

    private CarveFootprints() {
    }

    static StructureCarvingFootprint of(FitStart start, int horizontalPadding) {
        CarveFootprintKey key = new CarveFootprintKey(start.key(), horizontalPadding);
        StructureCarvingFootprint cached = cached(key);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<StructureCarvingFootprint> build = new CompletableFuture<>();
        CompletableFuture<StructureCarvingFootprint> active = BUILDS.putIfAbsent(key, build);
        if (active != null) {
            return await(active);
        }
        // Close the check-then-claim window: a racer whose cache check missed before the previous builder cached can
        // claim the build slot after that builder retired it, and would rebuild a second instance without this re-check.
        StructureCarvingFootprint published = cached(key);
        if (published != null) {
            build.complete(published);
            BUILDS.remove(key, build);
            return published;
        }
        try {
            StructureCarvingFootprint footprint = StructureCarvingFootprint.fromColumns(sink -> {
                for (FitPiece piece : start.pieces()) {
                    piece.columns(sink::column);
                }
                return true;
            }, horizontalPadding, MAX_CARVE_COLUMNS);
            if (footprint == null) {
                throw new IllegalStateException("Native structure carve footprint is empty or exceeds "
                        + MAX_CARVE_COLUMNS + " columns");
            }
            cache(key, footprint);
            build.complete(footprint);
            return footprint;
        } catch (RuntimeException | Error error) {
            build.completeExceptionally(error);
            throw error;
        } finally {
            BUILDS.remove(key, build);
        }
    }

    static int cachedCells() {
        synchronized (CARVE_FOOTPRINTS) {
            cachedCarveCells = liveCells();
            return cachedCarveCells;
        }
    }

    static int maximumCachedCells() {
        return MAX_CACHED_CARVE_CELLS;
    }

    private static int liveCells() {
        int total = 0;
        for (Map<Integer, CachedCarveFootprint> perStart : CARVE_FOOTPRINTS.values()) {
            for (CachedCarveFootprint cached : perStart.values()) {
                total += cached.cells();
            }
        }
        return total;
    }

    private static StructureCarvingFootprint cached(CarveFootprintKey key) {
        synchronized (CARVE_FOOTPRINTS) {
            Map<Integer, CachedCarveFootprint> perStart = CARVE_FOOTPRINTS.get(key.start());
            CachedCarveFootprint cached = perStart == null ? null : perStart.get(key.padding());
            return cached == null ? null : cached.footprint();
        }
    }

    /** The loser of a build race surfaces the builder's own exception, not a CompletionException wrapper. */
    private static StructureCarvingFootprint await(CompletableFuture<StructureCarvingFootprint> future) {
        try {
            return future.join();
        } catch (CompletionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            if (cause == null) {
                throw error;
            }
            throw new IllegalStateException("Native structure carve footprint build failed", cause);
        }
    }

    private static void cache(CarveFootprintKey key, StructureCarvingFootprint footprint) {
        int cells = Math.multiplyExact(footprint.width(), footprint.depth());
        synchronized (CARVE_FOOTPRINTS) {
            // Weak keys expunge silently as the GC drops retired starts; re-derive the live total so the cell budget
            // tracks reality instead of drifting.
            cachedCarveCells = liveCells();
            Map<Integer, CachedCarveFootprint> perStart =
                    CARVE_FOOTPRINTS.computeIfAbsent(key.start(), ignored -> new LinkedHashMap<>(4));
            CachedCarveFootprint previous = perStart.remove(key.padding());
            if (previous != null) {
                cachedCarveCells -= previous.cells();
            }
            Iterator<Map.Entry<Object, Map<Integer, CachedCarveFootprint>>> eviction =
                    CARVE_FOOTPRINTS.entrySet().iterator();
            while (cachedCarveCells + cells > MAX_CACHED_CARVE_CELLS && eviction.hasNext()) {
                Map.Entry<Object, Map<Integer, CachedCarveFootprint>> victim = eviction.next();
                if (victim.getKey() == key.start()) {
                    continue;
                }
                for (CachedCarveFootprint evicted : victim.getValue().values()) {
                    cachedCarveCells -= evicted.cells();
                }
                eviction.remove();
            }
            perStart.put(key.padding(), new CachedCarveFootprint(footprint, cells));
            cachedCarveCells += cells;
        }
    }

    /** The platform's start has no value equality, so the key pins the exact start a chunk carves against. */
    private record CarveFootprintKey(Object start, int padding) {
    }

    private record CachedCarveFootprint(StructureCarvingFootprint footprint, int cells) {
    }
}
