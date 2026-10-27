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

package art.arcane.iris.engine.object.prefab;

import art.arcane.iris.builtin.objects.ObjectScale;
import art.arcane.iris.builtin.objects.ScaleInterpolation;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The scaled variants of objects a world places: each variant an {@link ObjectScale} picks is built once and kept in
 * the world's cache, within a memory budget, until its origin object is collected.
 */
public final class ScaledObjects {
    private static final long CACHE_MIN_ESTIMATED_BYTES = 16L * 1024L * 1024L;
    private static final long CACHE_MAX_ESTIMATED_BYTES = 64L * 1024L * 1024L;
    private static final long CACHE_ENTRY_ESTIMATED_BYTES = 512L;
    private static final long CACHE_VARIANT_ESTIMATED_BYTES = 256L;
    private static final long CACHE_ESTIMATED_BYTES_PER_VOXEL = 128L;
    private static final int CACHE_LOAD_LOCK_COUNT = 256;
    private static final int NO_VARIATIONS = 1;

    private ScaledObjects() {
    }

    /** The scaled variant of {@code origin} that {@code scale} picks; {@code origin} itself when it does not scale. */
    public static IrisObject variant(ObjectScale scale, RNG rng, IrisObject origin, Engine engine) {
        return variant(new ScaleRequest(scale.size(), scale.minimumScale(), scale.maximumScale(), scale.variations(),
                scale.interpolation()), rng, origin, engine);
    }

    /** {@code origin} scaled by exactly {@code factor}, without interpolation. */
    public static IrisObject resized(IrisObject origin, double factor, RNG rng, Engine engine) {
        return variant(new ScaleRequest(factor, 1D, 1D, NO_VARIATIONS, ScaleInterpolation.NONE), rng, origin, engine);
    }

    static IrisObject variant(ScaleRequest request, RNG rng, IrisObject origin, Engine engine) {
        if (origin == null) {
            return null;
        }

        if (!request.shouldScale()) {
            return origin;
        }

        int variantIndex = request.selectVariant(rng);
        ScaleCache cache = cacheOf(engine);
        CacheKey key = cache.key(origin, request, variantIndex);
        IrisObject cached = cache.lookup(key);
        if (cached != null) {
            return cached;
        }

        synchronized (cache.loadLock(key)) {
            IrisObject afterWait = cache.lookup(key);
            if (afterWait != null) {
                return afterWait;
            }

            IrisObject variant = origin.scaled(request.scaleAt(variantIndex), request.interpolation());
            long estimatedBytes = estimateVariant(variant);
            return cache.putIfAbsent(key, variant, estimatedBytes);
        }
    }

    static ScaleCache cacheOf(Engine engine) {
        return engine.getGeneration().state(ScaleCache.class, world -> new ScaleCache(resolveCacheMaximumEstimatedBytes()));
    }

    private static long estimateVariant(IrisObject variant) {
        long width = Math.max(1L, variant.getW());
        long height = Math.max(1L, variant.getH());
        long depth = Math.max(1L, variant.getD());
        long volume = saturatedMultiply(saturatedMultiply(width, height), depth);
        long populatedVoxels = saturatedAdd(variant.getBlocks().size(), variant.getStates().size());
        long voxelBytes = saturatedMultiply(Math.max(volume, populatedVoxels), CACHE_ESTIMATED_BYTES_PER_VOXEL);
        return saturatedAdd(CACHE_ENTRY_ESTIMATED_BYTES,
                saturatedAdd(CACHE_VARIANT_ESTIMATED_BYTES, voxelBytes));
    }

    private static long saturatedAdd(long first, long second) {
        if (first >= Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    private static long saturatedMultiply(long first, long second) {
        if (first == 0L || second == 0L) {
            return 0L;
        }
        if (first > Long.MAX_VALUE / second) {
            return Long.MAX_VALUE;
        }
        return first * second;
    }

    private static long resolveCacheMaximumEstimatedBytes() {
        long heapShare = Runtime.getRuntime().maxMemory() / 32L;
        return Math.max(CACHE_MIN_ESTIMATED_BYTES, Math.min(CACHE_MAX_ESTIMATED_BYTES, heapShare));
    }

    record ScaleRequest(double size, double minimumScale, double maximumScale, int variations,
                        ScaleInterpolation interpolation) {
        boolean shouldScale() {
            if (size != 1D) {
                return true;
            }
            return variations > 0 && (minimumScale != 1D || maximumScale != 1D);
        }

        int variantCount() {
            if (size != 1D || minimumScale == maximumScale) {
                return 1;
            }
            return Math.max(1, Math.min(variations, 32));
        }

        int selectVariant(RNG rng) {
            int count = variantCount();
            return count == 1 ? 0 : rng.nextInt(count);
        }

        double scaleAt(int index) {
            if (size != 1D) {
                return size;
            }
            if (minimumScale == maximumScale) {
                return minimumScale;
            }
            return minimumScale + (((maximumScale - minimumScale) / variantCount()) * index);
        }
    }

    static final class CacheKey extends WeakReference<IrisObject> {
        private final ScaleRequest request;
        private final int variantIndex;
        private final int originRevision;
        private final int loadHashCode;
        private final int hashCode;

        CacheKey(IrisObject origin, ScaleRequest request, int variantIndex) {
            this(origin, request, variantIndex, null);
        }

        CacheKey(IrisObject origin, ScaleRequest request, int variantIndex,
                 ReferenceQueue<IrisObject> collectedOrigins) {
            super(origin, collectedOrigins);
            this.request = request;
            this.variantIndex = variantIndex;
            this.originRevision = originRevision(origin);
            int result = System.identityHashCode(origin);
            result = (31 * result) + originRevision;
            result = (31 * result) + request.hashCode();
            this.loadHashCode = result;
            this.hashCode = (31 * result) + variantIndex;
        }

        boolean hasOrigin(IrisObject candidate) {
            return get() == candidate;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof CacheKey other)) {
                return false;
            }
            IrisObject origin = get();
            return origin != null
                    && origin == other.get()
                    && originRevision == other.originRevision
                    && request.equals(other.request)
                    && variantIndex == other.variantIndex;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        int loadHashCode() {
            return loadHashCode;
        }

        private static int originRevision(IrisObject origin) {
            origin.readLock.lock();
            try {
                int result = origin.getW();
                result = (31 * result) + origin.getH();
                result = (31 * result) + origin.getD();
                result = (31 * result) + System.identityHashCode(origin.getBlocks());
                result = (31 * result) + System.identityHashCode(origin.getStates());
                result = (31 * result) + Long.hashCode(origin.getBlocks().modificationRevision());
                result = (31 * result) + Long.hashCode(origin.getStates().modificationRevision());
                Vector3i center = origin.getCenter();
                if (center != null) {
                    result = (31 * result) + center.getX();
                    result = (31 * result) + center.getY();
                    result = (31 * result) + center.getZ();
                }
                return result;
            } finally {
                origin.readLock.unlock();
            }
        }
    }

    static final class ScaleCache {
        private final long maximumEstimatedBytes;
        private final LinkedHashMap<CacheKey, CacheValue> entries;
        private final Object[] loadLocks;
        private final ReferenceQueue<IrisObject> collectedOrigins;
        private long estimatedBytes;

        ScaleCache(long maximumEstimatedBytes) {
            if (maximumEstimatedBytes <= 0L) {
                throw new IllegalArgumentException("maximumEstimatedBytes must be positive");
            }
            this.maximumEstimatedBytes = maximumEstimatedBytes;
            this.entries = new LinkedHashMap<>(256, 0.75F, true);
            this.loadLocks = new Object[CACHE_LOAD_LOCK_COUNT];
            this.collectedOrigins = new ReferenceQueue<>();
            for (int index = 0; index < loadLocks.length; index++) {
                loadLocks[index] = new Object();
            }
        }

        CacheKey key(IrisObject origin, ScaleRequest request, int variantIndex) {
            return new CacheKey(origin, request, variantIndex, collectedOrigins);
        }

        Object loadLock(CacheKey key) {
            return loadLocks[key.loadHashCode() & (loadLocks.length - 1)];
        }

        synchronized IrisObject lookup(CacheKey key) {
            drainCollectedOrigins();
            CacheValue value = entries.get(key);
            return value == null ? null : value.variant();
        }

        synchronized IrisObject putIfAbsent(CacheKey key, IrisObject variant, long entryEstimatedBytes) {
            drainCollectedOrigins();
            CacheValue present = entries.get(key);
            if (present != null) {
                return present.variant();
            }
            if (entryEstimatedBytes > maximumEstimatedBytes) {
                return variant;
            }

            entries.put(key, new CacheValue(variant, entryEstimatedBytes));
            estimatedBytes += entryEstimatedBytes;
            evictToBudget();
            return variant;
        }

        synchronized int size() {
            drainCollectedOrigins();
            return entries.size();
        }

        synchronized long estimatedBytes() {
            drainCollectedOrigins();
            return estimatedBytes;
        }

        long maximumEstimatedBytes() {
            return maximumEstimatedBytes;
        }

        synchronized boolean containsOrigin(IrisObject origin) {
            drainCollectedOrigins();
            for (CacheKey key : entries.keySet()) {
                if (key.hasOrigin(origin)) {
                    return true;
                }
            }
            return false;
        }

        private void evictToBudget() {
            Iterator<Map.Entry<CacheKey, CacheValue>> iterator = entries.entrySet().iterator();
            while (estimatedBytes > maximumEstimatedBytes && iterator.hasNext()) {
                Map.Entry<CacheKey, CacheValue> entry = iterator.next();
                estimatedBytes -= entry.getValue().estimatedBytes();
                iterator.remove();
            }
        }

        private void drainCollectedOrigins() {
            CacheKey collected;
            while ((collected = (CacheKey) collectedOrigins.poll()) != null) {
                CacheValue removed = entries.remove(collected);
                if (removed != null) {
                    estimatedBytes -= removed.estimatedBytes();
                }
            }
        }
    }

    record CacheValue(IrisObject variant, long estimatedBytes) {
    }
}
