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
                    RecordingSampler parallel = new RecordingSampler(refiner(terrain).new Samples());
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
    public void searchLimitCountsNullsAndRetainsAlreadyAdmittedCoordinates() {
        AtomicInteger reads = new AtomicInteger();
        HydrologyTerrainSampler terrain = (x, z) -> {
            reads.incrementAndGet();
            return x == 0 ? null : HydrologyTerrainSample.openLand(70, 0D, "land");
        };
        HydrologyRegionalTerrainRefiner refiner = refiner(terrain);
        refiner.sample(100000, 0);
        HydrologyRegionalTerrainRefiner.Samples samples = refiner.new Samples();
        assertNull(samples.sample(0, 0));
        for (int x = 1; x < 65536; x++) {
            assertEquals(70, samples.sample(x, 0).naturalHeight());
        }
        assertNull(samples.sample(0, 0));
        assertEquals(70, samples.sample(1, 0).naturalHeight());
        assertNull(samples.sample(65536, 0));
        assertNull(samples.sample(100000, 0));
        assertEquals(65537, reads.get());
    }

    @Test
    public void failedRequiredSampleRetainsItsExceptionAndDoesNotConsumeAdmission() {
        AtomicInteger failedReads = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("Required terrain unavailable");
        HydrologyTerrainSampler terrain = (x, z) -> {
            if (x == -1 && failedReads.incrementAndGet() == 1) {
                throw failure;
            }
            return HydrologyTerrainSample.openLand(70, 0D, "land");
        };
        HydrologyRegionalTerrainRefiner.Samples samples = refiner(terrain).new Samples();
        for (int x = 0; x < 65535; x++) {
            samples.sample(x, 0);
        }
        assertSame(failure, assertThrows(IllegalStateException.class, () -> samples.sample(-1, 0)));
        assertEquals(70, samples.sample(-1, 0).naturalHeight());
        assertEquals(70, samples.sample(0, 0).naturalHeight());
        assertNull(samples.sample(-2, 0));
        assertEquals(2, failedReads.get());
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
        HydrologyRegionalTerrainRefiner.Samples samples = refiner(terrain).new Samples();
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            assertEquals(expected, pool.submit(() -> support.crossSection(samples, station, true)).get(10, TimeUnit.SECONDS));
        }
        assertTrue(expected.blocked());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> samples.sample(0, 0)));
    }

    @Test
    public void hydraulicBudgetAndLazyWidthsPreserveConsumptionOrder() throws Exception {
        HydrologyPlannerSettings.Surface surface = SETTINGS.surface();
        HydrologyPlannerSettings settings = new HydrologyPlannerSettings(SETTINGS.seaLevel(), SETTINGS.routing(),
                new HydrologyPlannerSettings.Surface(surface.enabled(), surface.sources(), surface.minimumWidth(), 256,
                        surface.minimumDepth(), surface.maximumDepth(), surface.maximumIncision(), surface.shoreWidth(), surface.banks()),
                SETTINGS.hydraulics(), SETTINGS.underground(), SETTINGS.outlets(), SETTINGS.geometry(), SETTINGS.deepFluids(),
                SETTINGS.surfacePools(), SETTINGS.widestShoreBiomeWidth(), SETTINGS.seaCaves(), SETTINGS.surfacePolicyBounds());
        HydrologyTerrainSample ground = new HydrologyTerrainSample(65, 0D, false, false, 0, 0,
                true, true, true, false, true, false, 0D, 1D, 1D, 0.75D, 1D, 1D, 1D, 1D,
                "land", "land", "land", "land", "land", "land", List.of("default"), List.of(),
                Double.NaN, null, Double.NaN, true, SurfaceRiverPolicy.INHERIT);
        HydrologyTerrainSampler terrain = (x, z) -> x == 133 ? null : x == 132 && z == 0 ? ground
                : HydrologyTerrainSample.openLand(80, 0D, "land");
        HydrologyRegionalHydraulics hydraulics = new HydrologyRegionalHydraulics(settings);
        HydrologyRegionalHydraulics.HeadStation station = new HydrologyRegionalHydraulics.HeadStation(
                132, 0, 1D, 0D, terrain.sample(132, 0), 80);
        RecordingSampler serial = new RecordingSampler(terrain);
        int expected = hydraulics.supportedHead(station, serial);
        RecordingSampler parallel = new RecordingSampler(refiner(terrain).new Samples());
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            assertEquals(expected, pool.submit(() -> hydraulics.supportedHead(station, parallel)).get(10, TimeUnit.SECONDS).intValue());
        }
        assertEquals(serial.requests, parallel.requests);
        assertTrue(serial.requests.size() > 2048);
    }

    @Test
    public void routeRefinementMatchesSerialTerrainAndHydraulics() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(
                x >= 96 && x <= 160 && Math.abs(z) < 32 ? 95 : 70, 0D, "land");
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(256, 70, 0));
        List<HydrologyPoint> expected = refiner(terrain).refine(guide, "default", false, terrain);
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            List<HydrologyPoint> actual = pool.submit(() -> refiner(terrain).refine(guide, "default", false, terrain))
                    .get(15, TimeUnit.SECONDS);
            assertFalse(expected.isEmpty());
            assertEquals(expected, actual);
        }
    }

    @Test
    public void coastalCenterlineRetainsNegativeAndFractionalTraversalOrder() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(64, 0D, "land");
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(-173, 64, -117), new HydrologyPoint(174, 64, 138));
        List<HydrologyPoint> expected = refiner(terrain).refine(guide, "default", true, terrain);
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            assertFalse(expected.isEmpty());
            assertEquals(expected, pool.submit(() -> refiner(terrain).refine(guide, "default", true, terrain))
                    .get(15, TimeUnit.SECONDS));
        }
    }

    @Test
    public void rejectedTraversalDoesNotReadALaterProviderFailure() throws Exception {
        IllegalStateException failure = new IllegalStateException("Later centerline sample");
        AtomicInteger failures = new AtomicInteger();
        HydrologyTerrainSampler terrain = (x, z) -> {
            if (x == 32 && z == 0) {
                failures.incrementAndGet();
                throw failure;
            }
            return z == 0 && (x == 0 || x == 128)
                    ? HydrologyTerrainSample.openLand(70, 0D, "land") : null;
        };
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(128, 70, 0));
        assertTrue(refiner(terrain).refine(guide, "default", false, terrain).isEmpty());
        assertEquals(0, failures.get());
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            assertTrue(pool.submit(() -> refiner(terrain).refine(guide, "default", false, terrain))
                    .get(15, TimeUnit.SECONDS).isEmpty());
            assertEquals(0, failures.get());
        }
    }

    @Test
    public void clearingAnActiveReachCannotPublishIntoTheNewCache() throws Exception {
        AtomicBoolean changed = new AtomicBoolean();
        AtomicBoolean blocked = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HydrologyTerrainSampler terrain = (x, z) -> {
            boolean elevated = changed.get();
            if (x == 64 && z == 0 && blocked.compareAndSet(false, true)) {
                started.countDown();
                await(release);
            }
            return HydrologyTerrainSample.openLand(elevated && x == 64 && z == 0 ? 100 : 70, 0D, "land");
        };
        HydrologyRegionalTerrainRefiner refiner = refiner(terrain);
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(256, 70, 0));
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<List<HydrologyPoint>> previous = executor.submit(() -> refiner.refine(guide, "default", false, terrain));
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                changed.set(true);
                refiner.clear();
                assertEquals(100, refiner.sample(64, 0).naturalHeight());
            } finally {
                release.countDown();
            }
            previous.get(15, TimeUnit.SECONDS);
            assertEquals(refiner(terrain).refine(guide, "default", false, terrain),
                    refiner.refine(guide, "default", false, terrain));
            assertEquals(100, refiner.sample(64, 0).naturalHeight());
        }
    }

    @Test
    public void signedCoordinateTiesRemainStableInEveryDirection() throws Exception {
        try (ForkJoinPool pool = new ForkJoinPool(8)) {
            for (int[] direction : new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
                HydrologyTerrainSampler terrain = (x, z) -> {
                    int along = x * direction[0] + z * direction[1];
                    int across = -x * direction[1] + z * direction[0];
                    return HydrologyTerrainSample.openLand(
                            along >= 96 && along <= 160 && Math.abs(across) < 32 ? 95 : 70, 0D, "land");
                };
                List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 70, 0),
                        new HydrologyPoint(256 * direction[0], 70, 256 * direction[1]));
                List<HydrologyPoint> expected = refiner(terrain).refine(guide, "default", false, terrain);
                List<HydrologyPoint> actual = pool.submit(() -> refiner(terrain).refine(guide, "default", false, terrain))
                        .get(15, TimeUnit.SECONDS);
                assertFalse(expected.isEmpty());
                assertEquals(expected, actual);
            }
        }
    }

    private static HydrologyRegionalTerrainRefiner refiner(HydrologyTerrainSampler terrain) {
        return new HydrologyRegionalTerrainRefiner(new HydrologyPlanner(71L, SETTINGS, terrain));
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
