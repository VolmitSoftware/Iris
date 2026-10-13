package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.volmlib.util.cache.CacheKey;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;

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

    HydrologyTerrainSample[] sampleWithoutSlopeBatch(long[] coordinates, int count,
                                                    HydrologyNaturalTerrainSampler sampler) {
        Objects.checkFromIndexSize(0, count, coordinates.length);
        if (count > HydrologyTerrainSampler.MAXIMUM_BATCH_SIZE) {
            throw new IllegalArgumentException("Terrain basis batch exceeds its sample limit");
        }
        HydrologyTerrainSample[] samples = new HydrologyTerrainSample[count];
        Pending[] pending = new Pending[count];
        long[] ownedCoordinates = new long[count];
        Pending[] owned = new Pending[count];
        int ownedCount = 0;
        try {
            for (int index = 0; index < count; index++) {
                long packed = coordinates[index];
                Stripe stripe = stripe(withoutSlope, packed);
                synchronized (stripe) {
                    HydrologyTerrainSample cached = stripe.samples.getAndMoveToFirst(packed);
                    if (cached != null || stripe.samples.containsKey(packed)) {
                        samples[index] = cached;
                        continue;
                    }
                    Pending loading = stripe.pending.get(packed);
                    if (loading == null) {
                        loading = new Pending();
                        stripe.pending.put(packed, loading);
                        ownedCoordinates[ownedCount] = packed;
                        owned[ownedCount++] = loading;
                    }
                    pending[index] = loading;
                }
            }
            if (ownedCount > 0) {
                loadBatch(ownedCoordinates, owned, ownedCount, sampler);
            }
        } catch (RuntimeException | Error failure) {
            for (int index = 0; index < ownedCount; index++) {
                fail(withoutSlope, ownedCoordinates[index], owned[index], failure);
            }
            throw failure;
        }
        for (int index = 0; index < count; index++) {
            if (pending[index] != null) {
                samples[index] = pending[index].await();
            }
        }
        return samples;
    }

    void seedWithoutSlope(Long2ObjectMap<HydrologyTerrainSample> samples) {
        seed(samples, withoutSlope);
    }

    private HydrologyTerrainSample sample(int x, int z, HydrologyNaturalTerrainSampler sampler, boolean omitSlope) {
        long packed = RiverFootprint.pack(x, z);
        Stripe[] stripes = omitSlope ? withoutSlope : full;
        Stripe stripe = stripe(stripes, packed);
        Pending loading;
        boolean owner = false;
        synchronized (stripe) {
            HydrologyTerrainSample cached = stripe.samples.getAndMoveToFirst(packed);
            if (cached != null || stripe.samples.containsKey(packed)) {
                return cached;
            }
            loading = stripe.pending.get(packed);
            if (loading == null) {
                loading = new Pending();
                stripe.pending.put(packed, loading);
                owner = true;
            }
        }
        if (owner) {
            try {
                HydrologyTerrainSample sampled = ProvisionalSampling.memoizable(() -> omitSlope
                        ? sampler.sampleBasisWithoutSlope(x, z) : sampler.sampleBasis(x, z));
                complete(stripes, packed, loading, sampled, false);
            } catch (ProvisionalSampling.Unmemoizable provisional) {
                complete(stripes, packed, loading, provisional.value(), true);
            } catch (RuntimeException | Error failure) {
                fail(stripes, packed, loading, failure);
                throw failure;
            }
        }
        return loading.await();
    }

    private void loadBatch(long[] coordinates, Pending[] pending, int count, HydrologyNaturalTerrainSampler sampler) {
        HydrologyTerrainSample[] samples;
        boolean provisional = false;
        try {
            samples = ProvisionalSampling.memoizable(() -> sampler.sampleBasisWithoutSlopeBatch(coordinates, count));
        } catch (ProvisionalSampling.Unmemoizable value) {
            samples = value.value();
            provisional = true;
        }
        if (samples.length < count) {
            throw new IllegalStateException("Terrain basis batch returned too few samples");
        }
        for (int index = 0; index < count; index++) {
            complete(withoutSlope, coordinates[index], pending[index], samples[index], provisional);
        }
    }

    private void complete(Stripe[] stripes, long packed, Pending pending, HydrologyTerrainSample sample,
                          boolean provisional) {
        Stripe stripe = stripe(stripes, packed);
        synchronized (stripe) {
            if (stripe.samples.containsKey(packed)) {
                sample = stripe.samples.getAndMoveToFirst(packed);
                provisional = false;
            } else if (!provisional) {
                stripe.put(packed, sample);
            }
            stripe.pending.remove(packed);
            pending.sample = sample;
            pending.provisional = provisional;
            pending.complete.countDown();
        }
    }

    private void fail(Stripe[] stripes, long packed, Pending pending, Throwable failure) {
        Stripe stripe = stripe(stripes, packed);
        synchronized (stripe) {
            if (stripe.pending.remove(packed, pending)) {
                pending.failure = failure;
                pending.complete.countDown();
            }
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
        private final Long2ObjectOpenHashMap<Pending> pending = new Long2ObjectOpenHashMap<>();

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

    private static final class Pending implements ForkJoinPool.ManagedBlocker {
        private final Thread owner = Thread.currentThread();
        private final CountDownLatch complete = new CountDownLatch(1);
        private HydrologyTerrainSample sample;
        private Throwable failure;
        private boolean provisional;

        private HydrologyTerrainSample await() {
            if (!isReleasable() && owner == Thread.currentThread()) {
                throw new IllegalStateException("Recursive terrain basis sampling");
            }
            boolean interrupted = false;
            boolean managed = true;
            try {
                while (!isReleasable()) {
                    try {
                        if (managed) {
                            ForkJoinPool.managedBlock(this);
                        } else {
                            complete.await();
                        }
                    } catch (InterruptedException interruption) {
                        interrupted = true;
                        managed = false;
                    } catch (RejectedExecutionException unavailable) {
                        managed = false;
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (provisional) {
                ProvisionalSampling.mark();
            }
            return sample;
        }

        @Override
        public boolean block() throws InterruptedException {
            complete.await();
            return true;
        }

        @Override
        public boolean isReleasable() {
            return complete.getCount() == 0;
        }
    }
}
