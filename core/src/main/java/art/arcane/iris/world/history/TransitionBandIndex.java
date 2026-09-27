package art.arcane.iris.world.history;

import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

final class TransitionBandIndex {
    private static final int MAXIMUM_CACHED_REGIONS = 1_024;

    private final GenerationBoundary boundary;
    private final TransitionBoundarySampler sampler;
    private final AtomicReferenceArray<Region> regions = new AtomicReferenceArray<>(MAXIMUM_CACHED_REGIONS);
    private final ThreadLocal<Region> lastRegion = new ThreadLocal<>();

    TransitionBandIndex(GenerationBoundary boundary, TransitionBoundarySampler sampler) {
        this.boundary = boundary;
        this.sampler = sampler;
    }

    boolean contains(int chunkX, int chunkZ) {
        long key = GenerationBoundary.packChunk(chunkX >> 5, chunkZ >> 5);
        Region region = lastRegion.get();
        if (region == null || region.key != key) {
            region = region(key);
            lastRegion.set(region);
        }
        int index = ((chunkZ & 31) << 5) | (chunkX & 31);
        int word = index >>> 5;
        int shift = (index & 31) << 1;
        long state = (region.states.get(word) >>> shift) & 3L;
        if (state != 0L) {
            return state == 3L;
        }
        synchronized (region) {
            long states = region.states.get(word);
            state = (states >>> shift) & 3L;
            if (state == 0L) {
                state = intersects(chunkX, chunkZ) ? 3L : 1L;
                region.states.set(word, states | (state << shift));
            }
            return state == 3L;
        }
    }

    int cachedRegionCount() {
        int count = 0;
        for (int index = 0; index < regions.length(); index++) {
            if (regions.get(index) != null) {
                count++;
            }
        }
        return count;
    }

    private Region region(long key) {
        long mixed = key;
        mixed = (mixed ^ (mixed >>> 33)) * 0xff51afd7ed558ccdL;
        mixed = (mixed ^ (mixed >>> 33)) * 0xc4ceb9fe1a85ec53L;
        int slot = (int) (mixed ^ (mixed >>> 33)) & (MAXIMUM_CACHED_REGIONS - 1);
        Region replacement = null;
        while (true) {
            Region current = regions.get(slot);
            if (current != null && current.key == key) {
                return current;
            }
            if (replacement == null) {
                replacement = new Region(key);
            }
            if (regions.compareAndSet(slot, current, replacement)) {
                return replacement;
            }
        }
    }

    private boolean intersects(int chunkX, int chunkZ) {
        if (boundary.isHistoricalChunk(chunkX, chunkZ)) {
            return false;
        }
        int minimumX = Math.multiplyExact(chunkX, GenerationBoundary.CHUNK_SIZE);
        int minimumZ = Math.multiplyExact(chunkZ, GenerationBoundary.CHUNK_SIZE);
        return sampler.intersectsTerrainBand(minimumX, minimumZ,
                Math.addExact(minimumX, GenerationBoundary.CHUNK_SIZE - 1),
                Math.addExact(minimumZ, GenerationBoundary.CHUNK_SIZE - 1));
    }

    private static final class Region {
        private final long key;
        private final AtomicLongArray states = new AtomicLongArray(32);

        private Region(long key) {
            this.key = key;
        }
    }
}
