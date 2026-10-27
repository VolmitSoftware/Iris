package art.arcane.iris.engine.object.prefab;

import art.arcane.iris.builtin.objects.ScaleInterpolation;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.RNG;
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ScaledObjectsCacheTest {
    private static final ScaledObjects.ScaleRequest REQUEST = new ScaledObjects.ScaleRequest(
            2D,
            1D,
            1D,
            7,
            ScaleInterpolation.NONE
    );
    private static final ScaledObjects.ScaleRequest RANGED = new ScaledObjects.ScaleRequest(1D, 1D, 2D, 4, ScaleInterpolation.NONE);

    private Engine engine;

    @Before
    public void setUp() {
        engine = mock(Engine.class);
        when(engine.getData()).thenReturn(mock(IrisData.class));
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
    }

    private int cacheEntryCount() {
        return ScaledObjects.cacheOf(engine).size();
    }

    private long cacheEstimatedBytes() {
        return ScaledObjects.cacheOf(engine).estimatedBytes();
    }

    private IrisObject doubled(RNG rng, IrisObject origin) {
        return ScaledObjects.variant(REQUEST, rng, origin, engine);
    }

    @Test
    public void mutatedOriginCreatesStableReplacement() {
        IrisObject origin = new IrisObject(2, 2, 2);

        IrisObject first = doubled(new RNG(1L), origin);
        ScaledObjects.ScaleCache keyCache = new ScaledObjects.ScaleCache(10L);
        ScaledObjects.CacheKey originalKey = keyCache.key(origin, REQUEST, 0);
        int originalHash = originalKey.hashCode();

        origin.setW(3);
        IrisObject second = doubled(new RNG(1L), origin);

        assertEquals(originalHash, originalKey.hashCode());
        assertNotEquals(originalKey, keyCache.key(origin, REQUEST, 0));
        assertNotSame(first, second);
        assertNotEquals(first.getW(), second.getW());
        assertEquals(2, cacheEntryCount());
    }

    @Test
    public void voxelMutationReplacesCachedVariants() {
        IrisObject origin = new IrisObject(2, 2, 2);
        IrisObject empty = doubled(new RNG(1L), origin);

        origin.setUnsigned(0, 0, 0, mock(PlatformBlockState.class));
        IrisObject populated = doubled(new RNG(1L), origin);

        assertEquals(0, empty.getBlocks().size());
        assertTrue(populated.getBlocks().size() > 0);
        assertNotSame(empty, populated);
        assertEquals(2, cacheEntryCount());
    }

    @Test
    public void rangedScaleBuildsSelectedVariantsLazilyWithStableSequence() {
        IrisObject origin = new IrisObject(10, 10, 10);
        SequenceRng rng = new SequenceRng();

        IrisObject first = ScaledObjects.variant(RANGED, rng, origin, engine);
        IrisObject second = ScaledObjects.variant(RANGED, rng, origin, engine);
        IrisObject third = ScaledObjects.variant(RANGED, rng, origin, engine);
        IrisObject fourth = ScaledObjects.variant(RANGED, rng, origin, engine);
        IrisObject repeated = ScaledObjects.variant(RANGED, rng, origin, engine);

        assertSame(first, repeated);
        assertNotEquals(first.getW(), second.getW());
        assertNotEquals(second.getW(), third.getW());
        assertNotEquals(third.getW(), fourth.getW());
        assertEquals(4, cacheEntryCount());
        assertTrue(cacheEstimatedBytes() > 0L);
    }

    @Test
    public void overweightScaledVariantIsNotRetained() {
        OverweightScaleObject origin = new OverweightScaleObject();
        IrisObject result = doubled(new RNG(1L), origin);

        assertEquals(1_000, result.getW());
        assertEquals(1, origin.scaleCalls.get());
        assertEquals(0, cacheEntryCount());
        assertEquals(0L, cacheEstimatedBytes());
    }

    @Test
    public void concurrentSameVariantScalesOnlyOnce() throws Exception {
        BlockingScaleObject origin = new BlockingScaleObject();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch secondInvoked = new CountDownLatch(1);
        AtomicReference<Thread> secondThread = new AtomicReference<>();

        try {
            Future<IrisObject> first = executor.submit(() -> doubled(new RNG(1L), origin));
            assertTrue(origin.firstScaleEntered.await(5L, TimeUnit.SECONDS));
            Future<IrisObject> second = executor.submit(() -> {
                secondThread.set(Thread.currentThread());
                secondInvoked.countDown();
                return doubled(new RNG(1L), origin);
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
        ScaledObjects.ScaleCache cache = new ScaledObjects.ScaleCache(10L);
        IrisObject firstOrigin = new IrisObject(1, 1, 1);
        IrisObject secondOrigin = new IrisObject(1, 1, 1);
        IrisObject thirdOrigin = new IrisObject(1, 1, 1);
        ScaledObjects.CacheKey firstKey = key(firstOrigin);
        ScaledObjects.CacheKey secondKey = key(secondOrigin);
        ScaledObjects.CacheKey thirdKey = key(thirdOrigin);

        put(cache, firstKey, new IrisObject(1, 1, 1), 4L);
        put(cache, secondKey, new IrisObject(1, 1, 1), 4L);
        assertTrue(cache.lookup(firstKey) != null);
        put(cache, thirdKey, new IrisObject(1, 1, 1), 4L);

        assertEquals(2, cache.size());
        assertEquals(8L, cache.estimatedBytes());
        assertTrue(cache.estimatedBytes() <= cache.maximumEstimatedBytes());
        assertTrue(cache.lookup(firstKey) != null);
        assertNull(cache.lookup(secondKey));
        assertTrue(cache.lookup(thirdKey) != null);
    }

    @Test
    public void overweightEntryIsReturnedWithoutRetention() {
        ScaledObjects.ScaleCache cache = new ScaledObjects.ScaleCache(10L);
        IrisObject origin = new IrisObject(1, 1, 1);
        ScaledObjects.CacheKey key = key(origin);
        IrisObject variant = new IrisObject(1, 1, 1);

        IrisObject result = cache.putIfAbsent(key, variant, 11L);

        assertSame(variant, result);
        assertEquals(0, cache.size());
        assertEquals(0L, cache.estimatedBytes());
    }

    @Test
    public void collectedOriginReleasesScaledValueWithOwnerReference() {
        ScaledObjects.ScaleCache cache = new ScaledObjects.ScaleCache(20L);
        IrisData owner = mock(IrisData.class);
        IrisObject origin = new IrisObject(1, 1, 1);
        IrisObject variant = new IrisObject(1, 1, 1);
        origin.setLoader(owner);
        variant.setLoader(owner);
        ScaledObjects.CacheKey collectedKey = cache.key(origin, REQUEST, 0);
        put(cache, collectedKey, variant, 5L);

        collectedKey.clear();
        assertTrue(collectedKey.enqueue());

        assertEquals(0, cache.size());
        assertEquals(0L, cache.estimatedBytes());
    }

    private static ScaledObjects.CacheKey key(IrisObject origin) {
        return new ScaledObjects.CacheKey(origin, REQUEST, 0);
    }

    private static void put(ScaledObjects.ScaleCache cache, ScaledObjects.CacheKey key,
                            IrisObject variant, long estimatedBytes) {
        cache.putIfAbsent(key, variant, estimatedBytes);
    }

    private static final class BlockingScaleObject extends IrisObject {
        private final AtomicInteger scaleCalls = new AtomicInteger();
        private final CountDownLatch firstScaleEntered = new CountDownLatch(1);
        private final CountDownLatch releaseScale = new CountDownLatch(1);

        private BlockingScaleObject() {
            super(2, 2, 2);
        }

        @Override
        public IrisObject scaled(double scale, ScaleInterpolation interpolation) {
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
        public IrisObject scaled(double scale, ScaleInterpolation interpolation) {
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
