package art.arcane.iris.generation.hydrology;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyBasisCacheTest {
    @Test
    public void batchLookupPreservesNullAndFirstPublishedSample() {
        HydrologyBasisCache cache = new HydrologyBasisCache(1);
        HydrologyTerrainSample[] samples = new HydrologyTerrainSample[1];
        long key = RiverFootprint.pack(-17, Integer.MAX_VALUE);
        assertTrue(!cache.findWithoutSlope(key, samples, 0));
        assertNull(cache.rememberWithoutSlope(key, null));
        assertTrue(cache.findWithoutSlope(key, samples, 0));
        assertNull(samples[0]);
        HydrologyTerrainSample later = HydrologyTerrainSample.openLand(73, 0D, "land");
        assertNull(cache.rememberWithoutSlope(key, later));
        cache.rememberWithoutSlope(key + 1, later);
        assertTrue(!cache.findWithoutSlope(key, samples, 0));
        assertEquals(later, cache.rememberWithoutSlope(key + 1, null));
    }

    @Test
    public void channelsNullsFailuresAndEvictionPreserveSamples() {
        CountingSampler sampler = new CountingSampler();
        HydrologyBasisCache cache = new HydrologyBasisCache(1);
        assertEquals(3D, cache.sample(1, -2, sampler).slope(), 0D);
        assertEquals(0D, cache.sampleWithoutSlope(1, -2, sampler).slope(), 0D);
        assertEquals(3D, cache.sample(1, -2, sampler).slope(), 0D);
        assertEquals(0D, cache.sampleWithoutSlope(1, -2, sampler).slope(), 0D);
        assertEquals(1, sampler.fullCalls.get());
        assertEquals(1, sampler.slopeFreeCalls.get());
        assertNull(cache.sampleWithoutSlope(Integer.MIN_VALUE, Integer.MAX_VALUE, sampler));
        assertNull(cache.sampleWithoutSlope(Integer.MIN_VALUE, Integer.MAX_VALUE, sampler));
        assertEquals(2, sampler.slopeFreeCalls.get());
        assertThrows(IllegalStateException.class, () -> cache.sampleWithoutSlope(99, 0, sampler));
        assertEquals(87, cache.sampleWithoutSlope(99, 0, sampler).naturalHeight());
        assertEquals(87, cache.sampleWithoutSlope(99, 0, sampler).naturalHeight());
        assertEquals(4, sampler.slopeFreeCalls.get());
        assertEquals(87, cache.sampleWithoutSlope(1, -2, sampler).naturalHeight());
        assertEquals(5, sampler.slopeFreeCalls.get());
    }

    @Test
    public void plannerTrialsPreserveLocalSlopePrecedenceAndOwnerIsolation() {
        CountingSampler natural = new CountingSampler();
        HydrologyTerrainSampler detailed = (x, z) -> {
            throw new AssertionError("Basis caching must not sample caves");
        };
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        HydrologyPlanner planner = new HydrologyPlanner(23L, settings, detailed, natural,
                HydrologyGeometrySampler.deterministic(detailed), -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(detailed, settings.seaLevel(), -4096, 4096));
        planner.planningSamples.set(new HydrologyPlanner.PlanningSamples());
        try {
            assertEquals(3D, planner.sampleBasis(1, 1).slope(), 0D);
            assertEquals(0D, planner.sampleBasisWithoutSlope(2, 1).slope(), 0D);
            HydrologyBasisCache shared = planner.planningSamples.get()
                    .fallbackBasis(new Long2ObjectOpenHashMap<>());
            for (int trial = 0; trial < 4; trial++) {
                planner.planningSamples.set(new HydrologyPlanner.PlanningSamples(null, shared));
                assertEquals(0D, planner.sampleBasisWithoutSlope(1, 1).slope(), 0D);
                assertEquals(3D, planner.sampleBasis(1, 1).slope(), 0D);
                assertEquals(3D, planner.sampleBasisWithoutSlope(1, 1).slope(), 0D);
                assertEquals(0D, planner.sampleBasisWithoutSlope(2, 1).slope(), 0D);
            }
            assertEquals(1, natural.fullCalls.get());
            assertEquals(2, natural.slopeFreeCalls.get());
            planner.planningSamples.set(new HydrologyPlanner.PlanningSamples());
            assertEquals(0D, planner.sampleBasisWithoutSlope(1, 1).slope(), 0D);
            assertEquals(3, natural.slopeFreeCalls.get());
        } finally {
            planner.planningSamples.remove();
        }
    }

    @Test
    public void parallelTrialsComputeEachCoordinateOnce() throws Exception {
        CountingSampler sampler = new CountingSampler();
        HydrologyBasisCache cache = new HydrologyBasisCache();
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Future<List<HydrologyTerrainSample>>> results = new ArrayList<>();
            for (int trial = 0; trial < 4; trial++) {
                results.add(workers.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    List<HydrologyTerrainSample> values = new ArrayList<>();
                    for (int x = -128; x < 0; x++) {
                        values.add(cache.sampleWithoutSlope(x, -17, sampler));
                    }
                    return values;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<HydrologyTerrainSample> expected = results.getFirst().get(5, TimeUnit.SECONDS);
            for (Future<List<HydrologyTerrainSample>> result : results) {
                assertEquals(expected, result.get(5, TimeUnit.SECONDS));
            }
            assertEquals(128, sampler.slopeFreeCalls.get());
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static final class CountingSampler implements HydrologyNaturalTerrainSampler {
        private final AtomicInteger fullCalls = new AtomicInteger();
        private final AtomicInteger slopeFreeCalls = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();

        @Override
        public HydrologyTerrainSample sampleBasis(int x, int z) {
            fullCalls.incrementAndGet();
            return HydrologyTerrainSample.openLand(87, 3D, "land");
        }

        @Override
        public HydrologyTerrainSample sampleBasisWithoutSlope(int x, int z) {
            slopeFreeCalls.incrementAndGet();
            if (x == Integer.MIN_VALUE) {
                return null;
            }
            if (x == 99 && failures.getAndIncrement() == 0) {
                throw new IllegalStateException("retry");
            }
            return HydrologyTerrainSample.openLand(87, 0D, "land");
        }

        @Override
        public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
            throw new AssertionError("Basis caching must not sample grids");
        }

        @Override
        public NaturalClassification classifyNatural(int x, int z) {
            throw new AssertionError("Basis caching must not classify separately");
        }
    }
}
