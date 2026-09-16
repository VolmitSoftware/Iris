package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalShorelineTest {
    @Test(timeout = 15000)
    public void successfulBoundariesMatchSerialCandidatesInGridOrderAcrossWindows() throws Exception {
        HydrologySampledGrid grid = grid();
        HydrologyPlanner serial = planner(HydrologyRegionalShorelineTest::terrain);
        List<OutletCandidate> expected = serial.outletPlanner.oceanOutletCandidates(grid, true);
        CountDownLatch overlapping = new CountDownLatch(4);
        Set<Long> threads = ConcurrentHashMap.newKeySet();
        HydrologyPlanner parallel = planner((x, z) -> {
            if (x == 1 && z < 64) {
                threads.add(Thread.currentThread().threadId());
                overlapping.countDown();
                await(overlapping);
            }
            return terrain(x, z);
        });
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            List<OutletCandidate> actual = pool.submit(() -> parallel.outletPlanner.regionalOceanOutletCandidates(grid)).get(10, TimeUnit.SECONDS);
            assertEquals(32, expected.size());
            assertEquals(expected, actual);
            assertEquals(4, threads.size());
            for (int index = 0; index < actual.size(); index++) {
                assertEquals(index * 32, actual.get(index).landIndex());
                assertEquals(new HydrologyPoint(9, 63, index * 16), actual.get(index).outlet().landwardPoint());
                assertEquals(new HydrologyPoint(10, 63, index * 16), actual.get(index).outlet().connectionPoint());
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 15000)
    public void unavailableShorelinesKeepTheSameAcceptedOrder() throws Exception {
        HydrologyTerrainSampler sampler = (x, z) -> x == 5 && z % 48 == 0 ? null : terrain(x, z);
        HydrologySampledGrid grid = grid();
        List<OutletCandidate> expected = planner(sampler).outletPlanner.oceanOutletCandidates(grid, true);
        HydrologyPlanner parallel = planner(sampler);
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            List<OutletCandidate> actual = pool.submit(() -> parallel.outletPlanner.regionalOceanOutletCandidates(grid)).get(10, TimeUnit.SECONDS);
            assertEquals(21, expected.size());
            assertEquals(expected, actual);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 15000)
    public void firstFailureDrainsStartedWorkAndDoesNotStartTheNextWindow() throws Exception {
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean();
        AtomicBoolean restored = new AtomicBoolean();
        AtomicInteger laterWindow = new AtomicInteger();
        IllegalStateException expected = new IllegalStateException("First shoreline provider failure");
        IllegalArgumentException laterFailure = new IllegalArgumentException("Later shoreline provider failure");
        HydrologyPlanner planner = planner((x, z) -> {
            if (x == 1 && z == 0) {
                await(blocked);
                failed.countDown();
                throw expected;
            }
            if (x == 1 && z == 16) {
                blocked.countDown();
                await(release);
                finished.set(true);
            }
            if (x == 1 && z == 32) {
                throw laterFailure;
            }
            if (x == 1 && z >= 64) {
                laterWindow.incrementAndGet();
            }
            return terrain(x, z);
        });
        HydrologySampledGrid grid = grid();
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            Future<List<OutletCandidate>> pending = pool.submit(() -> {
                HydrologyPlanner.PlanningSamples previous = new HydrologyPlanner.PlanningSamples();
                planner.planningSamples.set(previous);
                try {
                    return planner.outletPlanner.regionalOceanOutletCandidates(grid);
                } finally {
                    restored.set(planner.planningSamples.get() == previous);
                    planner.planningSamples.remove();
                }
            });
            assertTrue(blocked.await(5, TimeUnit.SECONDS));
            assertTrue(failed.await(5, TimeUnit.SECONDS));
            assertFalse(pending.isDone());
            release.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS));
            Throwable cause = failure.getCause();
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertSame(expected, cause);
            assertEquals(List.of(laterFailure), List.of(expected.getSuppressed()));
            assertTrue(finished.get());
            assertTrue(restored.get());
            assertEquals(0, laterWindow.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static HydrologyPlanner planner(HydrologyTerrainSampler sampler) {
        return new HydrologyPlanner(71L, HydrologyRegionalPlannerTest.settings(false, 1), sampler);
    }

    private static HydrologyTerrainSample terrain(int x, int z) {
        return x >= 10 ? HydrologyTerrainSample.ocean(60, "ocean") : HydrologyTerrainSample.openLand(66, 0D, "land");
    }

    private static HydrologySampledGrid grid() {
        ArrayList<HydrologyGridNode> nodes = new ArrayList<>(1024);
        for (int z = 0; z < 32; z++) {
            for (int x = 0; x < 32; x++) {
                int index = z * 32 + x;
                nodes.add(new HydrologyGridNode(index, x, z, x * 16, z * 16, index + 1L, terrain(x * 16, z * 16)));
            }
        }
        return new HydrologySampledGrid(0, 0, 0, 0, 512, 32, 16, nodes);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }
}
