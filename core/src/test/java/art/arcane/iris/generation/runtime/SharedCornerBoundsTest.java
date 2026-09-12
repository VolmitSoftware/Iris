package art.arcane.iris.generation.runtime;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SharedCornerBoundsTest {
    @Test
    public void storedCornersComeBackAndUnknownCornersMiss() {
        SharedCornerBounds cache = new SharedCornerBounds(1 << 10);
        long key = SharedCornerBounds.key(12, -40, 1);

        assertFalse(cache.contains(key));
        cache.put(key, 0x1234_5678_9ABC_DEF0L);

        assertTrue(cache.contains(key));
        assertEquals(0x1234_5678_9ABC_DEF0L, cache.get(key));
        assertFalse(cache.contains(SharedCornerBounds.key(12, -40, 0)));
    }

    @Test
    public void keysSeparateCoordinatesAndInterpolators() {
        assertNotEquals(SharedCornerBounds.key(1, 2, 0), SharedCornerBounds.key(2, 1, 0));
        assertNotEquals(SharedCornerBounds.key(1, 2, 0), SharedCornerBounds.key(1, 2, 1));
        assertNotEquals(SharedCornerBounds.key(-1, 2, 0), SharedCornerBounds.key(1, 2, 0));
        assertNotEquals(0L, SharedCornerBounds.key(0, 0, 0));
    }

    @Test
    public void aCollidingCornerReplacesTheOldOne() {
        SharedCornerBounds cache = new SharedCornerBounds(2);
        long first = SharedCornerBounds.key(0, 0, 0);
        long colliding = first;
        for (int gx = 1; gx < 100_000; gx++) {
            long candidate = SharedCornerBounds.key(gx, 0, 0);
            if (cache.slot(candidate) == cache.slot(first)) {
                colliding = candidate;
                break;
            }
        }
        assertNotEquals(first, colliding);

        cache.put(first, 1L);
        cache.put(colliding, 2L);

        assertFalse(cache.contains(first));
        assertEquals(2L, cache.get(colliding));
    }

    @Test
    public void concurrentReadersNeverSeeAnotherCornersValue() throws InterruptedException {
        SharedCornerBounds cache = new SharedCornerBounds(1 << 8);
        AtomicBoolean torn = new AtomicBoolean();
        List<Thread> threads = new ArrayList<>();
        for (int worker = 0; worker < 8; worker++) {
            int seed = worker;
            threads.add(new Thread(() -> {
                long state = 0x9E3779B97F4A7C15L * (seed + 1);
                for (int step = 0; step < 400_000; step++) {
                    state ^= state << 13;
                    state ^= state >>> 7;
                    state ^= state << 17;
                    int gx = (int) (state & 1023);
                    int gz = (int) ((state >>> 10) & 1023);
                    long key = SharedCornerBounds.key(gx, gz, (int) (state & 1));
                    if ((state & 2) == 0) {
                        cache.put(key, key * 31L);
                    } else if (cache.contains(key) && cache.get(key) != key * 31L) {
                        torn.set(true);
                    }
                }
            }));
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertFalse(torn.get());
    }

}
