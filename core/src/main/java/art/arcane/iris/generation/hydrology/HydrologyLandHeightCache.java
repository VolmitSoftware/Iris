package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.cache.CacheKey;
import it.unimi.dsi.fastutil.longs.Long2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;

final class HydrologyLandHeightCache {
    private static final int MAXIMUM_ENTRIES = 131_072;
    private static final int MAXIMUM_STRIPES = 64;
    private final Stripe[] stripes;

    HydrologyLandHeightCache() {
        this(MAXIMUM_ENTRIES);
    }

    HydrologyLandHeightCache(int maximumEntries) {
        if (maximumEntries < 1 || Integer.bitCount(maximumEntries) != 1) {
            throw new IllegalArgumentException("Land height cache size must be a positive power of two.");
        }
        int stripeCount = Math.min(MAXIMUM_STRIPES, maximumEntries);
        stripes = new Stripe[stripeCount];
        for (int index = 0; index < stripeCount; index++) {
            stripes[index] = new Stripe(maximumEntries / stripeCount);
        }
    }

    double sample(int blockX, int blockZ, HydrologyNaturalTerrainSampler sampler) {
        long packed = RiverFootprint.pack(blockX, blockZ);
        Stripe stripe = stripe(packed);
        synchronized (stripe) {
            double cached = stripe.heights.getAndMoveToFirst(packed);
            if (!Double.isNaN(cached) || stripe.heights.containsKey(packed)) {
                return cached;
            }
            double sampled = sampler.sampleLandHeight(blockX, blockZ);
            stripe.put(packed, sampled);
            return sampled;
        }
    }

    void seed(Long2DoubleMap samples) {
        for (Long2DoubleMap.Entry entry : samples.long2DoubleEntrySet()) {
            Stripe stripe = stripe(entry.getLongKey());
            synchronized (stripe) {
                stripe.put(entry.getLongKey(), entry.getDoubleValue());
            }
        }
    }

    private Stripe stripe(long packed) {
        return stripes[(int) CacheKey.mix(packed) & (stripes.length - 1)];
    }

    private static final class Stripe {
        private final int maximumEntries;
        private final Long2DoubleLinkedOpenHashMap heights;

        private Stripe(int maximumEntries) {
            this.maximumEntries = maximumEntries;
            heights = new Long2DoubleLinkedOpenHashMap(maximumEntries);
            heights.defaultReturnValue(Double.NaN);
        }

        private void put(long packed, double height) {
            if (heights.size() >= maximumEntries && !heights.containsKey(packed)) {
                heights.removeLastDouble();
            }
            heights.putAndMoveToFirst(packed, height);
        }
    }
}
