package art.arcane.iris.generation.cache;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ConcurrentClockCacheTest {
    @Test
    public void firstPublishedValueWins() {
        ConcurrentClockCache<Object> cache = new ConcurrentClockCache<>(16);
        Object first = new Object();
        assertNull(cache.get(7L));
        assertSame(first, cache.putIfAbsent(7L, first));
        assertSame(first, cache.putIfAbsent(7L, new Object()));
        assertSame(first, cache.get(7L));
        cache.clear();
        assertNull(cache.get(7L));
    }

    @Test
    public void capacityBoundsRetainedEntries() {
        ConcurrentClockCache<Long> cache = new ConcurrentClockCache<>(64);
        for (long key = 0; key < 10_000; key++) {
            assertEquals(Long.valueOf(key), cache.putIfAbsent(key, key));
        }
        AtomicInteger retained = new AtomicInteger();
        cache.forEach(value -> {
            retained.incrementAndGet();
            assertEquals(value, cache.get(value));
        });
        assertEquals(cache.capacity(), retained.get());
        assertTrue(cache.capacity() >= 64);
    }

    @Test
    public void referencedEntriesSurviveAStreamOfOneTimeKeys() {
        ConcurrentClockCache<Long> cache = new ConcurrentClockCache<>(4);
        cache.putIfAbsent(-1L, -1L);
        for (long key = 0; key < 1_000; key++) {
            assertEquals(Long.valueOf(-1L), cache.get(-1L));
            cache.putIfAbsent(key, key);
        }
        assertEquals(Long.valueOf(-1L), cache.get(-1L));
    }

    @Test
    public void concurrentWritersAgreeWhenSetRequiresEviction() throws Exception {
        ConcurrentClockCache<Object> cache = new ConcurrentClockCache<>(4);
        CyclicBarrier barrier = new CyclicBarrier(3);
        Object[] published = new Object[2];
        int rounds = 100_000;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Void>> writers = new ArrayList<>(2);
            for (int writer = 0; writer < 2; writer++) {
                int index = writer;
                writers.add(executor.submit(() -> {
                    for (int round = 0; round < rounds; round++) {
                        barrier.await(10, TimeUnit.SECONDS);
                        published[index] = cache.putIfAbsent(999L, new Object());
                        barrier.await(10, TimeUnit.SECONDS);
                    }
                    return null;
                }));
            }
            int disagreements = 0;
            int duplicatePublications = 0;
            for (int round = 0; round < rounds; round++) {
                cache.clear();
                for (long key = 0; key < 4; key++) {
                    cache.putIfAbsent(key, new Object());
                    cache.get(key);
                }
                barrier.await(10, TimeUnit.SECONDS);
                barrier.await(10, TimeUnit.SECONDS);
                if (published[0] != published[1]) {
                    disagreements++;
                }
                AtomicInteger retainedPublications = new AtomicInteger();
                cache.forEach(value -> {
                    if (value == published[0] || value == published[1]) {
                        retainedPublications.incrementAndGet();
                    }
                });
                if (retainedPublications.get() != 1) {
                    duplicatePublications++;
                }
            }
            for (Future<Void> writer : writers) {
                writer.get(10, TimeUnit.SECONDS);
            }
            assertEquals(0, disagreements);
            assertEquals(0, duplicatePublications);
            assertSame(published[0], cache.get(999L));
        }
    }

    @Test
    public void concurrentWritersAgreeOnOneValuePerKey() throws Exception {
        ConcurrentClockCache<Object> cache = new ConcurrentClockCache<>(1 << 12);
        List<Future<Object[]>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            for (int thread = 0; thread < 8; thread++) {
                results.add(executor.submit(() -> {
                    Object[] seen = new Object[256];
                    for (int key = 0; key < seen.length; key++) {
                        seen[key] = cache.putIfAbsent(key, new Object());
                    }
                    return seen;
                }));
            }
            Object[] reference = results.getFirst().get(10, TimeUnit.SECONDS);
            for (Future<Object[]> result : results) {
                Object[] seen = result.get(10, TimeUnit.SECONDS);
                for (int key = 0; key < seen.length; key++) {
                    Object cached = cache.get(key);
                    if (cached != null) {
                        assertSame(cached, seen[key]);
                        assertSame(reference[key], seen[key]);
                    }
                }
            }
        }
    }
}
