package art.arcane.iris.generation.hydrology;

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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HydrologyLandHeightCacheTest {
    @Test
    public void evictionAndFailuresRetainExactScalarValues() {
        HydrologyLandHeightCache cache = new HydrologyLandHeightCache(1);
        HydrologyNaturalTerrainSampler sampler = mock(HydrologyNaturalTerrainSampler.class);
        AtomicInteger calls = new AtomicInteger();
        when(sampler.sampleLandHeight(anyInt(), anyInt())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            int x = invocation.getArgument(0);
            return x == Integer.MIN_VALUE ? Double.NaN : -0D;
        });
        assertEquals(Double.NaN, cache.sample(Integer.MIN_VALUE, Integer.MAX_VALUE, sampler), 0D);
        assertEquals(Double.NaN, cache.sample(Integer.MIN_VALUE, Integer.MAX_VALUE, sampler), 0D);
        assertEquals(1, calls.get());
        assertEquals(Double.doubleToRawLongBits(-0D), Double.doubleToRawLongBits(cache.sample(0, 0, sampler)));
        assertEquals(Double.doubleToRawLongBits(-0D), Double.doubleToRawLongBits(cache.sample(0, 0, sampler)));
        assertEquals(2, calls.get());
        assertEquals(Double.NaN, cache.sample(Integer.MIN_VALUE, Integer.MAX_VALUE, sampler), 0D);
        assertEquals(3, calls.get());
        when(sampler.sampleLandHeight(7, 11)).thenThrow(new IllegalStateException("retry")).thenReturn(83D);
        assertThrows(IllegalStateException.class, () -> cache.sample(7, 11, sampler));
        assertEquals(83D, cache.sample(7, 11, sampler), 0D);
        assertEquals(83D, cache.sample(7, 11, sampler), 0D);
    }

    @Test
    public void parallelAlternativesShareOneMissingSample() throws Exception {
        HydrologyLandHeightCache cache = new HydrologyLandHeightCache(64);
        HydrologyNaturalTerrainSampler sampler = mock(HydrologyNaturalTerrainSampler.class);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(4);
        when(sampler.sampleLandHeight(-19, 33)).thenAnswer(invocation -> {
            calls.incrementAndGet();
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return Double.NaN;
        });
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Double>> results = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    return cache.sample(-19, 33, sampler);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
            release.countDown();
            for (Future<Double> result : results) {
                assertEquals(Double.NaN, result.get(5, TimeUnit.SECONDS), 0D);
            }
            assertEquals(1, calls.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
