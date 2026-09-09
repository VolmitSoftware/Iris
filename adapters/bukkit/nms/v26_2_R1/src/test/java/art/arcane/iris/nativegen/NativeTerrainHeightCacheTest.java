package art.arcane.iris.nativegen;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.OptionalInt;
import java.util.function.IntSupplier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class NativeTerrainHeightCacheTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void coordinatesRuntimePredicatesAndBoundsHaveSeparateEntries() {
        NativeTerrainHeightCache cache = new NativeTerrainHeightCache();
        AtomicInteger calls = new AtomicInteger();
        NativeTerrainHeightCache.Query[] queries = {
                query(1, -1, 1),
                query(1, 1, -1),
                query(2, -1, 1),
                new NativeTerrainHeightCache.Query(1, -1, 1, Heightmap.Types.OCEAN_FLOOR, -256, 768),
                new NativeTerrainHeightCache.Query(1, -1, 1, Heightmap.Types.WORLD_SURFACE, -64, 768),
                new NativeTerrainHeightCache.Query(1, -1, 1, Heightmap.Types.WORLD_SURFACE, -256, 256)
        };
        for (int index = 0; index < queries.length; index++) {
            assertEquals(index + 1, cached(cache, queries[index], calls::incrementAndGet));
        }
        for (int index = 0; index < queries.length; index++) {
            assertEquals(index + 1, cached(cache, queries[index], calls::incrementAndGet));
        }
        assertEquals(queries.length, calls.get());
        cache.evictRuntime(1);
        assertEquals(3, cached(cache, queries[2], calls::incrementAndGet));
        assertEquals(7, cached(cache, queries[0], calls::incrementAndGet));
    }

    @Test
    public void allIntegerResultsAreCacheable() {
        NativeTerrainHeightCache cache = new NativeTerrainHeightCache();
        NativeTerrainHeightCache.Query query = query(1, 0, 0);
        assertEquals(Integer.MIN_VALUE, cached(cache, query, () -> Integer.MIN_VALUE));
        assertEquals(Integer.MIN_VALUE, cached(cache, query, () -> 0));
    }

    @Test
    public void failedResolutionCanBeRetried() {
        NativeTerrainHeightCache cache = new NativeTerrainHeightCache();
        NativeTerrainHeightCache.Query query = query(1, 0, 0);
        assertThrows(IllegalStateException.class, () -> cached(cache, query, () -> {
            throw new IllegalStateException("terrain unavailable");
        }));
        assertEquals(42, cached(cache, query, () -> 42));
    }

    @Test
    public void coldResolutionDoesNotHoldCacheMonitor() throws Exception {
        NativeTerrainHeightCache cache = new NativeTerrainHeightCache();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> slow = executor.submit(() -> cached(cache, query(1, 0, 0), () -> {
                entered.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interruption);
                }
                return 9;
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<Integer> independent = executor.submit(() -> cached(cache, query(1, 1, 0), () -> 17));
            assertEquals(Integer.valueOf(17), independent.get(1, TimeUnit.SECONDS));
            release.countDown();
            assertEquals(Integer.valueOf(9), slow.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void boundedCacheKeepsRecentlyReadCoordinates() {
        NativeTerrainHeightCache cache = new NativeTerrainHeightCache();
        for (int x = 0; x < 65_536; x++) {
            cached(cache, query(1, x, 0), () -> 8);
        }
        assertEquals(8, cached(cache, query(1, 0, 0), () -> 99));
        cached(cache, query(1, 65_536, 0), () -> 9);
        assertEquals(8, cached(cache, query(1, 0, 0), () -> 99));
        assertEquals(99, cached(cache, query(1, 1, 0), () -> 99));
    }

    @Test
    public void unavailableResolvedHeightIsNotCached() {
        NativeTerrainHeightCache cache = new NativeTerrainHeightCache();
        NativeTerrainHeightCache.Query query = query(1, 0, 0);
        assertTrue(cache.resolvedHeight(query, OptionalInt::empty).isEmpty());
        assertEquals(42, cache.resolvedHeight(query, () -> OptionalInt.of(42)).orElseThrow());
        assertEquals(42, cache.resolvedHeight(query, OptionalInt::empty).orElseThrow());
    }

    private static int cached(NativeTerrainHeightCache cache, NativeTerrainHeightCache.Query query,
                              IntSupplier resolver) {
        return cache.resolvedHeight(query, () -> OptionalInt.of(resolver.getAsInt())).orElseThrow();
    }

    private static NativeTerrainHeightCache.Query query(int runtimeId, int x, int z) {
        return new NativeTerrainHeightCache.Query(runtimeId, x, z, Heightmap.Types.WORLD_SURFACE, -256, 768);
    }
}
