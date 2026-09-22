package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceBankSupport;
import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalSamplingTest {
    private static final HydrologyPlannerSettings SETTINGS = HydrologyRegionalPlannerTest.settings(false, 1);

    @Test
    public void bankSamplesPreserveSamplingOrderAndResultsForRotatedWidths() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> x == 17 ? null
                : HydrologyTerrainSample.openLand(70 + Math.floorMod(x + z, 13), 0D, "land");
        SurfaceBankSupport support = new SurfaceBankSupport(SETTINGS.surface(), SETTINGS.seaLevel());
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            for (double angle : new double[]{0D, 0.37D, 0.7853981633974483D}) {
                for (double width : new double[]{2D, 5D, 16D, 48D}) {
                    SurfaceBankSupport.Station station = new SurfaceBankSupport.Station(11, -7,
                            StrictMath.cos(angle), StrictMath.sin(angle), width);
                    RecordingSampler serial = new RecordingSampler(terrain);
                    SurfaceBankSupport.CrossSection cross = support.crossSection(serial, station, true);
                    SurfaceBankSupport.Perimeter perimeter = support.perimeter(serial, station, 80);
                    RecordingSampler parallel = new RecordingSampler(refiner(terrain));
                    pool.submit(() -> {
                        assertEquals(cross, support.crossSection(parallel, station, true));
                        assertEquals(perimeter, support.perimeter(parallel, station, 80));
                    }).get(10, TimeUnit.SECONDS);
                    assertEquals(serial.requests, parallel.requests);
                }
            }
        }
    }

    @Test
    public void unavailableBankStopsBeforeALaterProviderFailure() throws Exception {
        IllegalStateException failure = new IllegalStateException("Later bank sample");
        HydrologyTerrainSampler terrain = (x, z) -> {
            if (z == 0) {
                throw failure;
            }
            return z == -5 ? null : HydrologyTerrainSample.openLand(70, 0D, "land");
        };
        SurfaceBankSupport support = new SurfaceBankSupport(SETTINGS.surface(), SETTINGS.seaLevel());
        SurfaceBankSupport.Station station = new SurfaceBankSupport.Station(0, 0, 1D, 0D, 4D);
        SurfaceBankSupport.CrossSection expected = support.crossSection(terrain, station, true);
        HydrologyRegionalTerrain samples = refiner(terrain);
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            assertEquals(expected, pool.submit(() -> support.crossSection(samples, station, true)).get(10, TimeUnit.SECONDS));
        }
        assertTrue(expected.blocked());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> samples.sample(0, 0)));
    }

    private static HydrologyRegionalTerrain refiner(HydrologyTerrainSampler terrain) {
        return new HydrologyRegionalTerrain(new HydrologyPlanner(71L, SETTINGS, terrain));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }

    private static final class RecordingSampler implements HydrologyTerrainSampler {
        private final HydrologyTerrainSampler sampler;
        private final List<Long> requests = new ArrayList<>();

        private RecordingSampler(HydrologyTerrainSampler sampler) {
            this.sampler = sampler;
        }

        @Override
        public HydrologyTerrainSample sample(int x, int z) {
            requests.add(RiverFootprint.pack(x, z));
            return sampler.sample(x, z);
        }


    }
}
