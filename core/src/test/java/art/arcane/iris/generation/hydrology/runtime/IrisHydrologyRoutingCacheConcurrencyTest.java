package art.arcane.iris.generation.hydrology.runtime;

import art.arcane.iris.generation.hydrology.HydrologyRoutingTerrainSampler.NaturalClassification;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class IrisHydrologyRoutingCacheConcurrencyTest {
    @Test(timeout = 15000)
    public void publishedBasisSupersedesAnEarlierClassification() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        IrisHydrologyRoutingTerrainSampler sampler = new IrisHydrologyRoutingTerrainSampler(
                new IrisHydrologyRoutingTerrainSampler.Sources(
                        (x, z, height) -> new IrisHydrologyRoutingTerrainSampler.TerrainBasis(height,
                                HydrologyTerrainSample.ocean(60, "ocean")),
                        (x, z) -> 60D,
                        (x, z) -> {
                            started.countDown();
                            await(release);
                            return false;
                        }, 63),
                IrisHydrologyRoutingTerrainSampler.SamplingOptions.serial(65536));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<NaturalClassification> classification = worker.submit(() -> sampler.classifyNatural(-8193, 257));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(sampler.sampleBasisWithoutSlope(-8193, 257).ocean());
            release.countDown();
            assertEquals(NaturalClassification.OCEAN, classification.get(5, TimeUnit.SECONDS));
            assertEquals(NaturalClassification.OCEAN, sampler.classifyNatural(-8193, 257));
            assertEquals(0, sampler.oceanClassificationCacheSize());
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 15000)
    public void concurrentSameCoordinateReturnsTheFirstPublishedBasis() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        IrisHydrologyRoutingTerrainSampler sampler = new IrisHydrologyRoutingTerrainSampler(
                new IrisHydrologyRoutingTerrainSampler.Sources(
                        (x, z, height) -> {
                            if (calls.incrementAndGet() == 1) {
                                started.countDown();
                                await(release);
                            }
                            return new IrisHydrologyRoutingTerrainSampler.TerrainBasis(height,
                                    HydrologyTerrainSample.openLand(70, 0D, "land"));
                        }, (x, z) -> 70D, (x, z) -> false, 63),
                IrisHydrologyRoutingTerrainSampler.SamplingOptions.serial(65536));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<IrisHydrologyRoutingTerrainSampler.TerrainBasis> first = worker.submit(() -> sampler.basis(4096, -37));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            IrisHydrologyRoutingTerrainSampler.TerrainBasis published = sampler.basis(4096, -37);
            release.countDown();
            assertSame(published, first.get(5, TimeUnit.SECONDS));
            assertSame(published, sampler.basis(4096, -37));
            assertEquals(2, calls.get());
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void unevenStripeCapacityStaysBoundedAndCloseClearsEveryCache() {
        int capacity = 4097;
        AtomicInteger heightCalls = new AtomicInteger();
        IrisHydrologyRoutingTerrainSampler sampler = new IrisHydrologyRoutingTerrainSampler(
                new IrisHydrologyRoutingTerrainSampler.Sources(
                        (x, z, height) -> new IrisHydrologyRoutingTerrainSampler.TerrainBasis(height,
                                HydrologyTerrainSample.openLand((int) height, 0D, "land")),
                        (x, z) -> {
                            heightCalls.incrementAndGet();
                            return 70D + Math.floorMod(x + z, 11);
                        }, (x, z) -> false, 63),
                IrisHydrologyRoutingTerrainSampler.SamplingOptions.serial(capacity));
        for (int coordinate = -10000; coordinate < 10000; coordinate++) {
            sampler.sampleBasisWithoutSlope(coordinate, coordinate * 3);
            sampler.classifyNatural(coordinate + 100000, -coordinate);
        }
        assertTrue(sampler.basisCacheSize() <= capacity);
        assertTrue(sampler.naturalHeightCacheSize() <= capacity);
        assertTrue(sampler.oceanClassificationCacheSize() <= capacity);
        assertTrue(sampler.basisCacheSize() > capacity / 2);
        assertTrue(sampler.oceanClassificationCacheSize() > capacity / 2);
        HydrologyTerrainSample before = sampler.sampleBasisWithoutSlope(7, 21);
        int previousCalls = heightCalls.get();
        sampler.close();
        assertEquals(0, sampler.basisCacheSize());
        assertEquals(0, sampler.naturalHeightCacheSize());
        assertEquals(0, sampler.oceanClassificationCacheSize());
        assertEquals(before, sampler.sampleBasisWithoutSlope(7, 21));
        assertEquals(previousCalls + 1, heightCalls.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue("Timed out waiting for provider release", latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
