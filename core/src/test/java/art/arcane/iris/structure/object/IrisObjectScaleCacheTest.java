package art.arcane.iris.structure.object;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.RNG;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class IrisObjectScaleCacheTest {
    private static final IrisObjectScale.ScaleRequest REQUEST = new IrisObjectScale.ScaleRequest(
            2D,
            1D,
            1D,
            7,
            IrisObjectPlacementScaleInterpolator.NONE
    );

    @Before
    public void setUp() {
        IrisObjectScale.invalidate(null);
    }

    @After
    public void tearDown() {
        IrisObjectScale.invalidate(null);
    }

    @Test
    public void mutatedOriginCreatesStableReplacementAndCanBeReleased() {
        IrisObject origin = new IrisObject(2, 2, 2);
        IrisObjectScale scale = new IrisObjectScale().setSize(2D);

        IrisObject first = scale.get(new RNG(1L), origin);
        IrisObjectScale.ScaleCache keyCache = new IrisObjectScale.ScaleCache(10L);
        IrisObjectScale.CacheKey originalKey = keyCache.key(origin, REQUEST, 0);
        int originalHash = originalKey.hashCode();

        origin.setW(3);
        IrisObject second = scale.get(new RNG(1L), origin);

        assertEquals(originalHash, originalKey.hashCode());
        assertNotEquals(originalKey, keyCache.key(origin, REQUEST, 0));
        assertNotSame(first, second);
        assertNotEquals(first.getW(), second.getW());
        assertEquals(2, IrisObjectScale.cacheEntryCount());

        IrisObjectScale.invalidate(null);

        assertEquals(0, IrisObjectScale.cacheEntryCount());
        assertEquals(0L, IrisObjectScale.cacheEstimatedBytes());
        assertFalse(IrisObjectScale.isOriginCached(origin));
    }

    @Test
    public void voxelMutationReplacesCachedVariants() {
        IrisObject origin = new IrisObject(2, 2, 2);
        IrisObjectScale scale = new IrisObjectScale().setSize(2D);
        IrisObject empty = scale.get(new RNG(1L), origin);

        origin.setUnsigned(0, 0, 0, mock(PlatformBlockState.class));
        IrisObject populated = scale.get(new RNG(1L), origin);

        assertEquals(0, empty.getBlocks().size());
        assertTrue(populated.getBlocks().size() > 0);
        assertNotSame(empty, populated);
        assertEquals(2, IrisObjectScale.cacheEntryCount());
    }

    @Test
    public void rangedScaleBuildsSelectedVariantsLazilyWithStableSequence() {
        IrisObject origin = new IrisObject(10, 10, 10);
        IrisObjectScale scale = new IrisObjectScale()
                .setVariations(4)
                .setMinimumScale(1D)
                .setMaximumScale(2D);
        SequenceRng rng = new SequenceRng();

        IrisObject first = scale.get(rng, origin);
        IrisObject second = scale.get(rng, origin);
        IrisObject third = scale.get(rng, origin);
        IrisObject fourth = scale.get(rng, origin);
        IrisObject repeated = scale.get(rng, origin);

        assertSame(first, repeated);
        assertNotEquals(first.getW(), second.getW());
        assertNotEquals(second.getW(), third.getW());
        assertNotEquals(third.getW(), fourth.getW());
        assertEquals(4, IrisObjectScale.cacheEntryCount());
        assertTrue(IrisObjectScale.cacheEstimatedBytes() > 0L);
    }

    @Test
    public void overweightScaledVariantIsNotRetained() {
        OverweightScaleObject origin = new OverweightScaleObject();
        IrisObjectScale scale = new IrisObjectScale().setSize(2D);

        IrisObject result = scale.get(new RNG(1L), origin);

        assertEquals(1_000, result.getW());
        assertEquals(1, origin.scaleCalls.get());
        assertEquals(0, IrisObjectScale.cacheEntryCount());
        assertEquals(0L, IrisObjectScale.cacheEstimatedBytes());
    }

    @Test
    public void concurrentSameVariantScalesOnlyOnce() throws Exception {
        BlockingScaleObject origin = new BlockingScaleObject();
        IrisObjectScale scale = new IrisObjectScale().setSize(2D);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch secondInvoked = new CountDownLatch(1);
        AtomicReference<Thread> secondThread = new AtomicReference<>();

        try {
            Future<IrisObject> first = executor.submit(() -> scale.get(new RNG(1L), origin));
            assertTrue(origin.firstScaleEntered.await(5L, TimeUnit.SECONDS));
            Future<IrisObject> second = executor.submit(() -> {
                secondThread.set(Thread.currentThread());
                secondInvoked.countDown();
                return scale.get(new RNG(1L), origin);
            });
            assertTrue(secondInvoked.await(5L, TimeUnit.SECONDS));

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
            while (secondThread.get().getState() != Thread.State.BLOCKED
                    && origin.scaleCalls.get() == 1
                    && System.nanoTime() < deadline) {
                LockSupport.parkNanos(100_000L);
            }

            assertEquals(1, origin.scaleCalls.get());
            assertEquals(Thread.State.BLOCKED, secondThread.get().getState());
            origin.releaseScale.countDown();
            assertSame(first.get(5L, TimeUnit.SECONDS), second.get(5L, TimeUnit.SECONDS));
        } finally {
            origin.releaseScale.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void weightedEvictionKeepsStrictBudgetAndUsesRecentAccess() {
        IrisObjectScale.ScaleCache cache = new IrisObjectScale.ScaleCache(10L);
        IrisObject firstOrigin = new IrisObject(1, 1, 1);
        IrisObject secondOrigin = new IrisObject(1, 1, 1);
        IrisObject thirdOrigin = new IrisObject(1, 1, 1);
        IrisObjectScale.CacheKey firstKey = key(firstOrigin);
        IrisObjectScale.CacheKey secondKey = key(secondOrigin);
        IrisObjectScale.CacheKey thirdKey = key(thirdOrigin);

        put(cache, firstKey, new IrisObject(1, 1, 1), 4L);
        put(cache, secondKey, new IrisObject(1, 1, 1), 4L);
        assertTrue(cache.lookup(firstKey).variant() != null);
        put(cache, thirdKey, new IrisObject(1, 1, 1), 4L);

        assertEquals(2, cache.size());
        assertEquals(8L, cache.estimatedBytes());
        assertTrue(cache.estimatedBytes() <= cache.maximumEstimatedBytes());
        assertTrue(cache.lookup(firstKey).variant() != null);
        assertNull(cache.lookup(secondKey).variant());
        assertTrue(cache.lookup(thirdKey).variant() != null);
    }

    @Test
    public void overweightEntryIsReturnedWithoutRetention() {
        IrisObjectScale.ScaleCache cache = new IrisObjectScale.ScaleCache(10L);
        IrisObject origin = new IrisObject(1, 1, 1);
        IrisObjectScale.CacheKey key = key(origin);
        IrisObject variant = new IrisObject(1, 1, 1);
        IrisObjectScale.CacheLookup lookup = cache.lookup(key);

        IrisObject result = cache.putIfCurrent(key, variant, 11L, lookup.generation());

        assertSame(variant, result);
        assertEquals(0, cache.size());
        assertEquals(0L, cache.estimatedBytes());
    }

    @Test
    public void invalidationReleasesOnlyTheRequestedRuntime() {
        IrisObjectScale.ScaleCache cache = new IrisObjectScale.ScaleCache(20L);
        IrisData firstOwner = mock(IrisData.class);
        IrisData secondOwner = mock(IrisData.class);
        IrisObject firstOrigin = new IrisObject(1, 1, 1);
        IrisObject secondOrigin = new IrisObject(1, 1, 1);
        firstOrigin.setLoader(firstOwner);
        secondOrigin.setLoader(secondOwner);
        IrisObjectScale.CacheKey firstKey = key(firstOrigin);
        IrisObjectScale.CacheKey secondKey = key(secondOrigin);

        put(cache, firstKey, new IrisObject(1, 1, 1), 5L);
        put(cache, secondKey, new IrisObject(1, 1, 1), 5L);

        cache.invalidate(firstOwner);

        assertNull(cache.lookup(firstKey).variant());
        assertTrue(cache.lookup(secondKey).variant() != null);
        assertEquals(1, cache.size());
        assertEquals(5L, cache.estimatedBytes());
    }

    @Test
    public void invalidationRejectsAnInFlightStaleInsertion() {
        IrisObjectScale.ScaleCache cache = new IrisObjectScale.ScaleCache(10L);
        IrisData owner = mock(IrisData.class);
        IrisObject origin = new IrisObject(1, 1, 1);
        origin.setLoader(owner);
        IrisObjectScale.CacheKey key = key(origin);
        IrisObjectScale.CacheLookup lookup = cache.lookup(key);

        cache.invalidate(owner);
        cache.putIfCurrent(key, new IrisObject(1, 1, 1), 5L, lookup.generation());

        assertEquals(0, cache.size());
        assertEquals(0L, cache.estimatedBytes());
        assertNull(cache.lookup(key).variant());
    }

    @Test
    public void collectedOriginReleasesScaledValueWithOwnerReference() {
        IrisObjectScale.ScaleCache cache = new IrisObjectScale.ScaleCache(20L);
        IrisData owner = mock(IrisData.class);
        IrisObject origin = new IrisObject(1, 1, 1);
        IrisObject variant = new IrisObject(1, 1, 1);
        origin.setLoader(owner);
        variant.setLoader(owner);
        IrisObjectScale.CacheKey collectedKey = cache.key(origin, REQUEST, 0);
        put(cache, collectedKey, variant, 5L);

        collectedKey.clear();
        assertTrue(collectedKey.enqueue());

        assertEquals(0, cache.size());
        assertEquals(0L, cache.estimatedBytes());
    }

    private static IrisObjectScale.CacheKey key(IrisObject origin) {
        return new IrisObjectScale.CacheKey(origin, REQUEST, 0);
    }

    private static void put(IrisObjectScale.ScaleCache cache, IrisObjectScale.CacheKey key,
                            IrisObject variant, long estimatedBytes) {
        IrisObjectScale.CacheLookup lookup = cache.lookup(key);
        cache.putIfCurrent(key, variant, estimatedBytes, lookup.generation());
    }

    private static final class BlockingScaleObject extends IrisObject {
        private final AtomicInteger scaleCalls = new AtomicInteger();
        private final CountDownLatch firstScaleEntered = new CountDownLatch(1);
        private final CountDownLatch releaseScale = new CountDownLatch(1);

        private BlockingScaleObject() {
            super(2, 2, 2);
        }

        @Override
        public IrisObject scaled(double scale, IrisObjectPlacementScaleInterpolator interpolation) {
            scaleCalls.incrementAndGet();
            firstScaleEntered.countDown();
            try {
                if (!releaseScale.await(5L, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to release scale computation");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            return new IrisObject(4, 4, 4);
        }
    }

    private static final class OverweightScaleObject extends IrisObject {
        private final AtomicInteger scaleCalls = new AtomicInteger();

        private OverweightScaleObject() {
            super(2, 2, 2);
        }

        @Override
        public IrisObject scaled(double scale, IrisObjectPlacementScaleInterpolator interpolation) {
            scaleCalls.incrementAndGet();
            return new IrisObject(1_000, 1_000, 1_000);
        }
    }

    private static final class SequenceRng extends RNG {
        private int next;

        @Override
        public int nextInt(int bound) {
            int value = next % bound;
            next++;
            return value;
        }
    }
}
