package art.arcane.iris.generation.cache;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LazyBoundedCacheTest {
    @Test
    public void backingStorageIsLazy() {
        LazyBoundedCache<String, Object> cache = new LazyBoundedCache<>(8);

        assertFalse(cache.isInitialized());
        assertEquals(0, cache.size());
        assertNull(cache.computeIfAbsent("missing", ignored -> null));
        assertFalse(cache.isInitialized());

        Object expected = new Object();
        assertSame(expected, cache.computeIfAbsent("present", ignored -> expected));
        assertTrue(cache.isInitialized());
        assertEquals(1, cache.size());
    }

    @Test
    public void capacityUsesAccessOrder() {
        LazyBoundedCache<Integer, Object> cache = new LazyBoundedCache<>(8);
        Object first = new Object();
        cache.computeIfAbsent(0, ignored -> first);
        for (int key = 1; key < 8; key++) {
            cache.computeIfAbsent(key, ignored -> new Object());
        }

        assertSame(first, cache.computeIfAbsent(0, ignored -> new Object()));
        cache.computeIfAbsent(8, ignored -> new Object());

        AtomicInteger reloads = new AtomicInteger();
        cache.computeIfAbsent(1, ignored -> {
            reloads.incrementAndGet();
            return new Object();
        });
        assertEquals(1, reloads.get());
        assertEquals(8, cache.size());
        assertSame(first, cache.computeIfAbsent(0, ignored -> new Object()));
    }

    @Test
    public void concurrentSameKeyComputesOnce() throws Exception {
        int threadCount = 16;
        LazyBoundedCache<String, Object> cache = new LazyBoundedCache<>(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch resolverEntered = new CountDownLatch(1);
        CountDownLatch releaseResolver = new CountDownLatch(1);
        AtomicInteger computations = new AtomicInteger();
        Object expected = new Object();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Object>> futures = new ArrayList<>(threadCount);
        try {
            for (int index = 0; index < threadCount; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return cache.computeIfAbsent("shared", ignored -> {
                        computations.incrementAndGet();
                        resolverEntered.countDown();
                        await(releaseResolver);
                        return expected;
                    });
                }));
            }
            start.countDown();
            assertTrue(resolverEntered.await(5L, TimeUnit.SECONDS));
            releaseResolver.countDown();
            for (Future<Object> future : futures) {
                assertSame(expected, future.get(5L, TimeUnit.SECONDS));
            }
        } finally {
            releaseResolver.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }

        assertEquals(1, computations.get());
        assertEquals(1, cache.size());
    }

    @Test
    public void keysRetainIdentityEqualitySemantics() {
        Object firstIdentity = new String("same");
        Object secondIdentity = new String("same");
        LazyBoundedCache<IdentityKey, Object> cache = new LazyBoundedCache<>(8);
        Object firstValue = new Object();

        assertSame(firstValue, cache.computeIfAbsent(new IdentityKey(firstIdentity), ignored -> firstValue));
        assertSame(firstValue, cache.computeIfAbsent(new IdentityKey(firstIdentity), ignored -> new Object()));
        assertNotSame(firstValue, cache.computeIfAbsent(new IdentityKey(secondIdentity), ignored -> new Object()));
        assertEquals(2, cache.size());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for cache test latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static final class IdentityKey {
        private final Object identity;

        private IdentityKey(Object identity) {
            this.identity = identity;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof IdentityKey key && identity == key.identity;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(identity);
        }
    }
}
