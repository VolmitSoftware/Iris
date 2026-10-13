package art.arcane.iris.generation.runtime;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SharedCornerBoundsTest {
    @Test
    public void storedCornersComeBackAndUnknownCornersMiss() {
        SharedCornerBounds cache = new SharedCornerBounds(1 << 10);
        long key = SharedCornerBounds.key(12, -40);

        assertEquals(Long.MIN_VALUE, cache.get(key, 1));
        cache.put(key, 1, 0x1234_5678_9ABC_DEF0L);

        assertEquals(0x1234_5678_9ABC_DEF0L, cache.get(key, 1));
        assertEquals(Long.MIN_VALUE, cache.get(key, 0));
    }

    @Test
    public void zeroKeyMissesUntilTheOriginIsStored() {
        SharedCornerBounds cache = new SharedCornerBounds(2);
        long key = SharedCornerBounds.key(0, 0);

        assertEquals(0L, key);
        assertEquals(Long.MIN_VALUE, cache.get(key, 0));
        cache.put(key, 0, 73L);
        assertEquals(73L, cache.get(key, 0));
    }

    @Test
    public void fullCoordinateDomainKeepsDistinctCorners() {
        int[] coordinates = {0, 1, -1, 1 << 21, -(1 << 21), 30_000_000, -30_000_000,
                Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int x : coordinates) {
            for (int z : coordinates) {
                SharedCornerBounds cache = new SharedCornerBounds(2);
                long key = SharedCornerBounds.key(x, z);
                cache.put(key, 0, 73L);
                for (int otherX : coordinates) {
                    for (int otherZ : coordinates) {
                        long otherKey = SharedCornerBounds.key(otherX, otherZ);
                        if (x == otherX && z == otherZ) {
                            assertEquals(73L, cache.get(otherKey, 0));
                        } else {
                            assertNotEquals(key, otherKey);
                            assertEquals(Long.MIN_VALUE, cache.get(otherKey, 0));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void interpolatorsKeepTheirFullIdentity() {
        int[] interpolators = {0, 1, -1, 1 << 21, -(1 << 21), Integer.MIN_VALUE, Integer.MAX_VALUE};
        long key = SharedCornerBounds.key(0, 0);
        for (int interpolator : interpolators) {
            SharedCornerBounds cache = new SharedCornerBounds(2);
            cache.put(key, interpolator, 73L);
            for (int other : interpolators) {
                assertEquals(interpolator == other ? 73L : Long.MIN_VALUE, cache.get(key, other));
            }
        }
    }

    @Test
    public void aCollidingCornerReplacesTheOldOne() {
        SharedCornerBounds cache = new SharedCornerBounds(2);
        long first = SharedCornerBounds.key(0, 0);
        long colliding = first;
        for (int gx = 1; gx < 100_000; gx++) {
            long candidate = SharedCornerBounds.key(gx, 0);
            if (cache.slot(candidate, 0) == cache.slot(first, 0)) {
                colliding = candidate;
                break;
            }
        }
        assertNotEquals(first, colliding);

        cache.put(first, 0, 1L);
        cache.put(colliding, 0, 2L);

        assertEquals(Long.MIN_VALUE, cache.get(first, 0));
        assertEquals(2L, cache.get(colliding, 0));
    }

    @Test
    public void concurrentReadersNeverSeeAnotherCornersValue() throws InterruptedException {
        SharedCornerBounds cache = new SharedCornerBounds(8);
        AtomicBoolean torn = new AtomicBoolean();
        AtomicInteger hits = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        int[] coordinates = {0, 1 << 21, -(1 << 21), 30_000_000, -30_000_000,
                Integer.MIN_VALUE, Integer.MAX_VALUE};
        int[] interpolators = {0, 1, -1, 1 << 21, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int worker = 0; worker < 4; worker++) {
            int seed = worker;
            threads.add(new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    torn.set(true);
                    return;
                }
                long state = 0x9E3779B97F4A7C15L * (seed + 1);
                for (int step = 0; step < 200_000; step++) {
                    state ^= state << 13;
                    state ^= state >>> 7;
                    state ^= state << 17;
                    int gx = coordinates[(int) ((state & Long.MAX_VALUE) % coordinates.length)];
                    int gz = coordinates[(int) ((state >>> 10) % coordinates.length)];
                    int interpolator = interpolators[(int) ((state >>> 20) % interpolators.length)];
                    long key = SharedCornerBounds.key(gx, gz);
                    long expected = expectedValue(key, interpolator);
                    cache.put(key, interpolator, expected);
                    long actual = cache.get(key, interpolator);
                    if (actual != Long.MIN_VALUE) {
                        hits.incrementAndGet();
                        if (actual != expected) {
                            torn.set(true);
                        }
                    }
                }
            }));
        }
        for (Thread thread : threads) {
            thread.start();
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        assertTrue(hits.get() > 0);
        assertFalse(torn.get());
    }

    private static long expectedValue(long key, int interpolator) {
        long value = Long.rotateLeft(key, 17) ^ (0x9E3779B97F4A7C15L * interpolator);
        return value == Long.MIN_VALUE ? 0L : value;
    }
}
