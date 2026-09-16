package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalCorridorSamplingTest {
    @Test
    public void parallelSamplesPreserveCorridorOrderAcrossSegmentsAndBatches() throws Exception {
        List<HydrologyPoint> points = List.of(new HydrologyPoint(-137, 70, -67),
                new HydrologyPoint(-7, 70, 19), new HydrologyPoint(-7, 70, 19),
                new HydrologyPoint(113, 70, 39), new HydrologyPoint(237, 70, 97));
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(70 + Math.floorMod(x + z, 9), 0D, "land");
        HydrologyRegionalRoute.Refinement serial = validate(points, terrain);
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            HydrologyRegionalRoute.Refinement parallel = pool.submit(() -> validate(points, terrain)).get(10, TimeUnit.SECONDS);
            assertNull(serial.rejection());
            assertEquals(points, serial.points());
            assertEquals(serial, parallel);
        }
    }

    @Test
    public void independentColumnsRunTogetherWithinOneBoundedBatch() throws Exception {
        CountDownLatch overlapping = new CountDownLatch(2);
        AtomicInteger samples = new AtomicInteger();
        HydrologyTerrainSampler terrain = (x, z) -> {
            samples.incrementAndGet();
            overlapping.countDown();
            try {
                assertTrue(overlapping.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
            return x == 0 ? null : HydrologyTerrainSample.openLand(70, 0D, "land");
        };
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            HydrologyRegionalRoute.Refinement result = pool.submit(() -> validate(straight(), terrain)).get(10, TimeUnit.SECONDS);
            assertEquals(HydrologyCandidateRejection.POLICY_EXCLUDED, result.rejection());
            assertEquals(new HydrologyPoint(0, 70, 0), result.failure());
            assertEquals(128, samples.get());
        }
    }

    @Test
    public void rejectionKeepsPrecedenceOverLaterSpeculativeFailure() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> {
            if (x == 64) {
                throw new IllegalStateException("Unavailable later sample");
            }
            return x == 31 ? null : HydrologyTerrainSample.openLand(70, 0D, "land");
        };
        HydrologyRegionalRoute.Refinement serial = validate(straight(), terrain);
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            HydrologyRegionalRoute.Refinement parallel = pool.submit(() -> validate(straight(), terrain)).get(10, TimeUnit.SECONDS);
            assertEquals(serial, parallel);
            assertEquals(new HydrologyPoint(31, 70, 0), parallel.failure());
        }
    }

    @Test
    public void consumedSampleFailurePropagatesInEncounterOrder() throws Exception {
        IllegalStateException first = new IllegalStateException("First failed sample");
        IllegalStateException second = new IllegalStateException("Second failed sample");
        HydrologyTerrainSampler terrain = (x, z) -> {
            if (x == 7) {
                throw first;
            }
            if (x == 64) {
                throw second;
            }
            return HydrologyTerrainSample.openLand(70, 0D, "land");
        };
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            IllegalStateException actual = pool.submit(() -> assertThrows(IllegalStateException.class,
                    () -> validate(straight(), terrain))).get(10, TimeUnit.SECONDS);
            assertSame(first, actual);
        }
    }

    @Test
    public void oceanEntryAndRetracingRejectionsMatchSerialValidation() throws Exception {
        HydrologyTerrainSampler ocean = (x, z) -> x >= 701 ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(70, 0D, "land");
        List<HydrologyPoint> retracing = List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(193, 70, 0),
                new HydrologyPoint(0, 70, 0), new HydrologyPoint(900, 70, 0));
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            HydrologyRegionalRoute.Refinement oceanSerial = validate(straight(), ocean);
            HydrologyRegionalRoute.Refinement oceanParallel = pool.submit(() -> validate(straight(), ocean)).get(10, TimeUnit.SECONDS);
            assertNull(oceanSerial.rejection());
            assertEquals(new HydrologyPoint(700, 63, 0), oceanSerial.oceanEntry().landward());
            assertEquals(oceanSerial, oceanParallel);
            HydrologyRegionalRoute.Refinement retracingSerial = validate(retracing, ocean);
            HydrologyRegionalRoute.Refinement retracingParallel = pool.submit(() -> validate(retracing, ocean)).get(10, TimeUnit.SECONDS);
            assertEquals(HydrologyCandidateRejection.SURFACE_SHAPE_UNSUPPORTED, retracingSerial.rejection());
            assertEquals(retracingSerial, retracingParallel);
        }
    }

    private static List<HydrologyPoint> straight() {
        return List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(900, 70, 0));
    }

    private static HydrologyRegionalRoute.Refinement validate(List<HydrologyPoint> points,
                                                              HydrologyTerrainSampler terrain) throws Exception {
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        HydrologyRegionalRoute route = new HydrologyRegionalRoute(planner);
        Method method = HydrologyRegionalRoute.class.getDeclaredMethod("validateCorridor", List.class, String.class, boolean.class);
        method.setAccessible(true);
        try {
            return (HydrologyRegionalRoute.Refinement) method.invoke(route, points, "default", false);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure.getCause() instanceof Error error) {
                throw error;
            }
            throw failure;
        }
    }
}
