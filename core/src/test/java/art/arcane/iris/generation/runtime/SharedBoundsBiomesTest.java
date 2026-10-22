package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SharedBoundsBiomesTest {
    private static final Object SOURCE = new Object();

    @Test
    public void storedColumnsComeBackAndUnknownColumnsMiss() {
        SharedBoundsBiomes cache = new SharedBoundsBiomes(SOURCE, 1 << 10);
        IrisBiome biome = new IrisBiome();

        assertNull(cache.get(12, -40));
        cache.put(12, -40, biome);

        assertSame(biome, cache.get(12, -40));
        assertNull(cache.get(-40, 12));
        assertSame(SOURCE, cache.source());
    }

    @Test
    public void originMissesUntilItIsStored() {
        SharedBoundsBiomes cache = new SharedBoundsBiomes(SOURCE, 2);
        IrisBiome biome = new IrisBiome();

        assertNull(cache.get(0, 0));
        cache.put(0, 0, biome);
        assertSame(biome, cache.get(0, 0));
    }

    @Test
    public void nullBiomesAreNeverStored() {
        SharedBoundsBiomes cache = new SharedBoundsBiomes(SOURCE, 2);
        IrisBiome biome = new IrisBiome();
        cache.put(3, 4, biome);
        cache.put(3, 4, null);

        assertSame(biome, cache.get(3, 4));
    }

    @Test
    public void fullCoordinateDomainKeepsDistinctColumns() {
        int[] coordinates = {0, 1, -1, 1 << 21, -(1 << 21), 30_000_000, -30_000_000,
                Integer.MIN_VALUE, Integer.MAX_VALUE};
        IrisBiome biome = new IrisBiome();
        for (int x : coordinates) {
            for (int z : coordinates) {
                SharedBoundsBiomes cache = new SharedBoundsBiomes(SOURCE, 2);
                cache.put(x, z, biome);
                for (int otherX : coordinates) {
                    for (int otherZ : coordinates) {
                        if (x == otherX && z == otherZ) {
                            assertSame(biome, cache.get(otherX, otherZ));
                        } else {
                            assertNull(cache.get(otherX, otherZ));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void aCollidingColumnReplacesTheOldOne() {
        SharedBoundsBiomes cache = new SharedBoundsBiomes(SOURCE, 2);
        int colliding = 0;
        for (int x = 1; x < 100_000; x++) {
            if (cache.slot(x, 0) == cache.slot(0, 0)) {
                colliding = x;
                break;
            }
        }
        assertNotEquals(0, colliding);
        IrisBiome first = new IrisBiome();
        IrisBiome second = new IrisBiome();

        cache.put(0, 0, first);
        cache.put(colliding, 0, second);

        assertNull(cache.get(0, 0));
        assertSame(second, cache.get(colliding, 0));
    }

    @Test
    public void capacityFollowsTheHeapWithinItsBounds() {
        long megabyte = 1024L * 1024L;
        assertEquals(SharedBoundsBiomes.MINIMUM_CAPACITY, SharedBoundsBiomes.capacityFor(0L));
        assertEquals(SharedBoundsBiomes.MINIMUM_CAPACITY, SharedBoundsBiomes.capacityFor(256L * megabyte));
        assertEquals(1 << 20, SharedBoundsBiomes.capacityFor(6L * 1024L * megabyte));
        assertEquals(SharedBoundsBiomes.MAXIMUM_CAPACITY, SharedBoundsBiomes.capacityFor(64L * 1024L * megabyte));
        long previous = 0L;
        for (long heap = 64L * megabyte; heap <= 128L * 1024L * megabyte; heap *= 2L) {
            int capacity = SharedBoundsBiomes.capacityFor(heap);
            assertEquals(1, Integer.bitCount(capacity));
            assertTrue(capacity >= previous);
            assertTrue((long) capacity * SharedBoundsBiomes.BYTES_PER_ENTRY <= Math.max(heap / SharedBoundsBiomes.HEAP_SHARE,
                    (long) SharedBoundsBiomes.MINIMUM_CAPACITY * SharedBoundsBiomes.BYTES_PER_ENTRY));
            previous = capacity;
        }
    }

    @Test
    public void concurrentReadersNeverSeeAnotherColumnsBiome() throws InterruptedException {
        SharedBoundsBiomes cache = new SharedBoundsBiomes(SOURCE, 8);
        int[] coordinates = {0, 1 << 21, -(1 << 21), 30_000_000, -30_000_000,
                Integer.MIN_VALUE, Integer.MAX_VALUE};
        IrisBiome[][] biomes = new IrisBiome[coordinates.length][coordinates.length];
        for (int x = 0; x < coordinates.length; x++) {
            for (int z = 0; z < coordinates.length; z++) {
                biomes[x][z] = new IrisBiome();
            }
        }
        AtomicBoolean torn = new AtomicBoolean();
        AtomicInteger hits = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
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
                    int xi = (int) ((state & Long.MAX_VALUE) % coordinates.length);
                    int zi = (int) ((state >>> 10) % coordinates.length);
                    cache.put(coordinates[xi], coordinates[zi], biomes[xi][zi]);
                    IrisBiome actual = cache.get(coordinates[xi], coordinates[zi]);
                    if (actual != null) {
                        hits.incrementAndGet();
                        if (actual != biomes[xi][zi]) {
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
}
