package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.cache.CacheKey;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

final class HydrologyBasisCache {
    private static final int MAXIMUM_ENTRIES = 32_768;
    private static final int MAXIMUM_STRIPES = 64;
    private final Stripe[] full;
    private final Stripe[] withoutSlope;

    HydrologyBasisCache() {
        this(MAXIMUM_ENTRIES);
    }

    HydrologyBasisCache(int maximumEntries) {
        if (maximumEntries < 1 || Integer.bitCount(maximumEntries) != 1) {
            throw new IllegalArgumentException("Terrain basis cache size must be a positive power of two.");
        }
        int stripeCount = Math.min(MAXIMUM_STRIPES, maximumEntries);
        full = new Stripe[stripeCount];
        withoutSlope = new Stripe[stripeCount];
        for (int index = 0; index < stripeCount; index++) {
            full[index] = new Stripe(maximumEntries / stripeCount);
            withoutSlope[index] = new Stripe(maximumEntries / stripeCount);
        }
    }

    HydrologyTerrainSample sample(int x, int z, HydrologyNaturalTerrainSampler sampler) {
        return sample(x, z, sampler, false);
    }

    HydrologyTerrainSample sampleWithoutSlope(int x, int z, HydrologyNaturalTerrainSampler sampler) {
        return sample(x, z, sampler, true);
    }

    void seed(Long2ObjectMap<HydrologyTerrainSample> samples) {
        seed(samples, full);
    }

    boolean findWithoutSlope(long packed, HydrologyTerrainSample[] samples, int index) {
        Stripe stripe = stripe(withoutSlope, packed);
        synchronized (stripe) {
            samples[index] = stripe.samples.getAndMoveToFirst(packed);
            return samples[index] != null || stripe.samples.containsKey(packed);
        }
    }

    HydrologyTerrainSample rememberWithoutSlope(long packed, HydrologyTerrainSample sample) {
        Stripe stripe = stripe(withoutSlope, packed);
        synchronized (stripe) {
            if (stripe.samples.containsKey(packed)) {
                return stripe.samples.getAndMoveToFirst(packed);
            }
            stripe.put(packed, sample);
            return sample;
        }
    }

    void seedWithoutSlope(Long2ObjectMap<HydrologyTerrainSample> samples) {
        seed(samples, withoutSlope);
    }

    private HydrologyTerrainSample sample(int x, int z, HydrologyNaturalTerrainSampler sampler, boolean omitSlope) {
        long packed = RiverFootprint.pack(x, z);
        Stripe stripe = stripe(omitSlope ? withoutSlope : full, packed);
        synchronized (stripe) {
            HydrologyTerrainSample cached = stripe.samples.getAndMoveToFirst(packed);
            if (cached != null || stripe.samples.containsKey(packed)) {
                return cached;
            }
            HydrologyTerrainSample sampled = omitSlope ? sampler.sampleBasisWithoutSlope(x, z) : sampler.sampleBasis(x, z);
            stripe.put(packed, sampled);
            return sampled;
        }
    }

    private void seed(Long2ObjectMap<HydrologyTerrainSample> samples, Stripe[] stripes) {
        for (Long2ObjectMap.Entry<HydrologyTerrainSample> entry : samples.long2ObjectEntrySet()) {
            Stripe stripe = stripe(stripes, entry.getLongKey());
            synchronized (stripe) {
                stripe.put(entry.getLongKey(), entry.getValue());
            }
        }
    }

    private Stripe stripe(Stripe[] stripes, long packed) {
        return stripes[(int) CacheKey.mix(packed) & (stripes.length - 1)];
    }

    private static final class Stripe {
        private final int maximumEntries;
        private final Long2ObjectLinkedOpenHashMap<HydrologyTerrainSample> samples;

        private Stripe(int maximumEntries) {
            this.maximumEntries = maximumEntries;
            samples = new Long2ObjectLinkedOpenHashMap<>(Math.min(16, maximumEntries));
        }

        private void put(long packed, HydrologyTerrainSample value) {
            if (samples.size() >= maximumEntries && !samples.containsKey(packed)) {
                samples.removeLast();
            }
            samples.putAndMoveToFirst(packed, value);
        }
    }
}
