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

package art.arcane.iris.structure.object;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Snippet("object-scale")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Scale objects")
@Data
public class IrisObjectScale {
    public static final double MINIMUM_FACTOR = 0.01D;
    public static final double MAXIMUM_FACTOR = 50D;
    private static final long CACHE_MIN_ESTIMATED_BYTES = 16L * 1024L * 1024L;
    private static final long CACHE_MAX_ESTIMATED_BYTES = 64L * 1024L * 1024L;
    private static final long CACHE_ENTRY_ESTIMATED_BYTES = 512L;
    private static final long CACHE_VARIANT_ESTIMATED_BYTES = 256L;
    private static final long CACHE_ESTIMATED_BYTES_PER_VOXEL = 128L;
    private static final int CACHE_LOAD_LOCK_COUNT = 256;
    private static final ScaleCache CACHE = new ScaleCache(resolveCacheMaximumEstimatedBytes());

    public static void invalidate(IrisData owner) {
        CACHE.invalidate(owner);
    }

    @MinNumber(0.01)
    @MaxNumber(50)
    @Description("Fixed scale multiplier for this object. 0.5 shrinks to half size, 2.0 doubles the size. When set to anything other than 1, this overrides minimumScale and maximumScale. Leave at 1 to use the minimumScale/maximumScale range.")
    private double size = 1;

    @MinNumber(1)
    @MaxNumber(32)
    @Description("Iris Objects are scaled and cached to speed up placements. Because of this extra memory is used, so we evenly distribute variations across the defined scale range, then pick one randomly. If the differences is small, use a lower number. For more possibilities on the scale spectrum, increase this at the cost of memory.")
    private int variations = 7;

    @MinNumber(0.01)
    @MaxNumber(50)
    @Description("The minimum scale. Used when size is 1 to pick a random scale per placement.")
    private double minimumScale = 1;

    @MinNumber(0.01)
    @MaxNumber(50)
    @Description("The maximum scale. Used when size is 1 to pick a random scale per placement.")
    private double maximumScale = 1;

    @Description("If this object is scaled up beyond its origin size, specify a 3D interpolator. NONE keeps blocky scaled output, TRILINEAR (LERP) smooths with linear interpolation, TRICUBIC and TRIHERMITE produce smoother but slower output.")
    private IrisObjectPlacementScaleInterpolator interpolation = IrisObjectPlacementScaleInterpolator.NONE;

    public boolean shouldScale() {
        if (size != 1) {
            return true;
        }
        if (variations <= 0) {
            return false;
        }
        return minimumScale != 1 || maximumScale != 1;
    }

    public IrisObjectScale setSize(double size) {
        this.size = requireValidFactor(size, "size");
        return this;
    }

    public IrisObjectScale setMinimumScale(double minimumScale) {
        this.minimumScale = requireValidFactor(minimumScale, "minimumScale");
        return this;
    }

    public IrisObjectScale setMaximumScale(double maximumScale) {
        this.maximumScale = requireValidFactor(maximumScale, "maximumScale");
        return this;
    }

    public int getMaxSizeFor(int indim) {
        return (int) Math.ceil(getMaxScale() * indim);
    }

    public double getMaxScale() {
        ScaleRequest request = snapshotRequest();
        if (!request.shouldScale()) {
            return 1D;
        }
        return request.size() != 1D ? request.size()
                : Math.max(request.minimumScale(), request.maximumScale());
    }

    public IrisObject get(RNG rng, IrisObject origin) {
        if (origin == null) {
            return null;
        }

        ScaleRequest request = snapshotRequest();
        if (!request.shouldScale()) {
            return origin;
        }

        return get(origin, request, request.selectVariant(rng));
    }

    public static IrisObject getFixed(IrisObject origin, double factor) {
        requireValidFactor(factor, "Object scale");
        if (origin == null || factor == 1D) {
            return origin;
        }
        return get(origin, new ScaleRequest(factor, 1D, 1D, 7,
                IrisObjectPlacementScaleInterpolator.NONE), 0);
    }

    public static double requireValidFactor(double factor, String name) {
        if (!Double.isFinite(factor) || factor < MINIMUM_FACTOR || factor > MAXIMUM_FACTOR) {
            throw new IllegalArgumentException(name + " must be finite and between "
                    + MINIMUM_FACTOR + " and " + MAXIMUM_FACTOR + ".");
        }
        return factor;
    }

    private static IrisObject get(IrisObject origin, ScaleRequest request, int variantIndex) {
        CacheKey key = CACHE.key(origin, request, variantIndex);
        CacheLookup lookup = CACHE.lookup(key);
        if (lookup.variant() != null) {
            return lookup.variant();
        }

        synchronized (CACHE.loadLock(key)) {
            CacheLookup afterWait = CACHE.lookup(key);
            if (afterWait.variant() != null) {
                return afterWait.variant();
            }

            IrisObject variant = origin.scaled(request.scaleAt(variantIndex), request.interpolation());
            long estimatedBytes = estimateVariant(variant);
            return CACHE.putIfCurrent(key, variant, estimatedBytes, lookup.generation());
        }
    }

    public boolean canScaleBeyond() {
        return shouldScale() && getMaxScale() > 1;
    }

    static int cacheEntryCount() {
        return CACHE.size();
    }

    static long cacheEstimatedBytes() {
        return CACHE.estimatedBytes();
    }

    static boolean isOriginCached(IrisObject origin) {
        return CACHE.containsOrigin(origin);
    }

    private ScaleRequest snapshotRequest() {
        requireValidFactor(size, "size");
        requireValidFactor(minimumScale, "minimumScale");
        requireValidFactor(maximumScale, "maximumScale");
        IrisObjectPlacementScaleInterpolator configuredInterpolation = interpolation == null
                ? IrisObjectPlacementScaleInterpolator.NONE
                : interpolation;
        return new ScaleRequest(size, minimumScale, maximumScale, variations, configuredInterpolation);
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
                        IrisObjectPlacementScaleInterpolator interpolation) {
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
        private final IrisData owner;
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
            this.owner = origin.getLoader();
            this.request = request;
            this.variantIndex = variantIndex;
            this.originRevision = originRevision(origin);
            int result = System.identityHashCode(origin);
            result = (31 * result) + System.identityHashCode(owner);
            result = (31 * result) + originRevision;
            result = (31 * result) + request.hashCode();
            this.loadHashCode = result;
            this.hashCode = (31 * result) + variantIndex;
        }

        boolean belongsTo(IrisData candidate) {
            return owner == candidate;
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
                    && owner == other.owner
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

    record CacheLookup(IrisObject variant, long generation) {
    }

    static final class ScaleCache {
        private final long maximumEstimatedBytes;
        private final LinkedHashMap<CacheKey, CacheValue> entries;
        private final Object[] loadLocks;
        private final ReferenceQueue<IrisObject> collectedOrigins;
        private long estimatedBytes;
        private long generation;

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

        synchronized CacheLookup lookup(CacheKey key) {
            drainCollectedOrigins();
            CacheValue value = entries.get(key);
            return new CacheLookup(value == null ? null : value.variant(), generation);
        }

        synchronized IrisObject putIfCurrent(CacheKey key, IrisObject variant,
                                             long entryEstimatedBytes, long expectedGeneration) {
            drainCollectedOrigins();
            CacheValue present = entries.get(key);
            if (present != null) {
                return present.variant();
            }
            if (generation != expectedGeneration || entryEstimatedBytes > maximumEstimatedBytes) {
                return variant;
            }

            entries.put(key, new CacheValue(variant, entryEstimatedBytes));
            estimatedBytes += entryEstimatedBytes;
            evictToBudget();
            return variant;
        }

        synchronized void invalidate(IrisData owner) {
            drainCollectedOrigins();
            generation++;
            Iterator<Map.Entry<CacheKey, CacheValue>> iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<CacheKey, CacheValue> entry = iterator.next();
                if (entry.getKey().belongsTo(owner)) {
                    estimatedBytes -= entry.getValue().estimatedBytes();
                    iterator.remove();
                }
            }
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
