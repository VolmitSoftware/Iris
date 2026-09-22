package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;
import art.arcane.iris.generation.hydrology.surface.SurfaceCenterline;
import org.junit.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalTerrainTest {
    @Test(timeout = 10000)
    public void clearingDuringSamplingCannotPublishOldTerrain() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        HydrologyTerrainSampler terrain = (x, z) -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                started.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
            return HydrologyTerrainSample.openLand(call == 1 ? 70 : 80, 0D, "land");
        };
        HydrologyRegionalTerrain refiner = new HydrologyRegionalTerrain(
                new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain));
        try (ExecutorService workers = Executors.newSingleThreadExecutor()) {
            Future<HydrologyTerrainSample> first = workers.submit(() -> refiner.sample(0, 0));
            try {
                assertTrue(started.await(3, TimeUnit.SECONDS));
                refiner.clear();
                assertEquals(80, refiner.sample(0, 0).naturalHeight());
            } finally {
                release.countDown();
            }
            assertEquals(70, first.get(5, TimeUnit.SECONDS).naturalHeight());
            assertEquals(80, refiner.sample(0, 0).naturalHeight());
            assertEquals(2, calls.get());
        }
    }

    @Test(timeout = 10000)
    public void independentDiagonalSamplesDoNotWaitForEachOthersTerrain() throws Exception {
        CountDownLatch sampling = new CountDownLatch(2);
        AtomicInteger calls = new AtomicInteger();
        HydrologyTerrainSampler terrain = (x, z) -> {
            calls.incrementAndGet();
            sampling.countDown();
            try {
                assertTrue("Independent terrain samples were serialized", sampling.await(3, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            return HydrologyTerrainSample.openLand(70 + x, 0D, "land");
        };
        HydrologyRegionalTerrain refiner = new HydrologyRegionalTerrain(
                new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain));
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<HydrologyTerrainSample> first = workers.submit(() -> refiner.sample(0, 0));
            Future<HydrologyTerrainSample> second = workers.submit(() -> refiner.sample(1, 1));
            assertEquals(70, first.get(5, TimeUnit.SECONDS).naturalHeight());
            assertEquals(71, second.get(5, TimeUnit.SECONDS).naturalHeight());
            assertEquals(first.get(), refiner.sample(0, 0));
            assertEquals(second.get(), refiner.sample(1, 1));
            assertEquals(2, calls.get());
        }
    }

    @Test
    public void shortRepairedPrefixDoesNotHideALongEnoughGuide() {
        HydrologyTerrainSampler terrain = (x, z) -> x >= 406 ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(x >= 400 ? 60 : 70, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(0, 70, 96),
                new HydrologyPoint(128, 70, 96), new HydrologyPoint(256, 70, 96), new HydrologyPoint(256, 70, 0),
                new HydrologyPoint(512, 70, 0));
        HydrologyRegionalRoute.Refinement selected = new HydrologyRegionalRoute(planner).select(guide, "default", false, terrain,
                candidate -> {
                    double length = 0D;
                    for (int index = 1; index < candidate.points().size(); index++) {
                        length += StrictMath.sqrt(candidate.points().get(index - 1).distanceSquared2D(candidate.points().get(index)));
                    }
                    assertTrue(length >= planner.settings.routing().regional().minimumLength());
                    return new HydrologyRegionalRoute.Attempt(candidate, HydrologyRegionalNetwork.EMPTY, null);
                }).refinement();

        assertNull(selected.toString(), selected.rejection());
        assertEquals(new HydrologyPoint(399, 63, 0), selected.oceanEntry().landward());
        assertEquals(new HydrologyPoint(406, 63, 0), selected.oceanEntry().receiving());
    }

    @Test
    public void shortRepairReversalsReceiveEnoughReachToMeetTwentyDegrees() {
        HydrologyPlanner planner = new HydrologyPlanner(15L, withMeanders(
                new HydrologyPlannerSettings.Meanders(64, 12, 0D, 0D, 0D, 1, 20D)),
                (x, z) -> HydrologyTerrainSample.openLand(70, 0D, "land"));
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(128, 70, 0),
                new HydrologyPoint(160, 70, 32), new HydrologyPoint(160, 70, 16),
                new HydrologyPoint(192, 70, 16), new HydrologyPoint(192, 70, 128), new HydrologyPoint(512, 70, 128));
        HydrologyRegionalRoute.Refinement repaired = new HydrologyRegionalRoute(planner).select(guide, "default", false, planner.sampler, candidate -> new HydrologyRegionalRoute.Attempt(candidate, HydrologyRegionalNetwork.EMPTY, null)).refinement();

        assertNull(repaired.toString(), repaired.rejection());
        assertEquals(guide.getFirst(), repaired.points().getFirst());
        assertEquals(guide.getLast(), repaired.points().getLast());
    }

    @Test
    public void roundedCurveTurnLimitIsMeasuredBeforeBlockQuantization() throws Exception {
        HydrologyPlanner planner = new HydrologyPlanner(15L, withMeanders(
                new HydrologyPlannerSettings.Meanders(64, 12, 0D, 0D, 0D, 0, 20D)),
                (x, z) -> HydrologyTerrainSample.openLand(70, 0D, "land"));
        HydrologyRegionalRoute route = new HydrologyRegionalRoute(planner);
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(128, 70, 0),
                new HydrologyPoint(128, 70, 128), new HydrologyPoint(256, 70, 128));
        Method interpolate = HydrologyRegionalRoute.class.getDeclaredMethod("interpolateLinear", List.class);
        Method round = HydrologyRegionalRoute.class.getDeclaredMethod("roundBends", List.class, double.class);
        Method resample = HydrologyRegionalRoute.class.getDeclaredMethod("resample", List.class);
        Method validate = HydrologyRegionalRoute.class.getDeclaredMethod("validateRounded", List.class, String.class, boolean.class, HydrologyTerrainSampler.class);
        interpolate.setAccessible(true);
        round.setAccessible(true);
        resample.setAccessible(true);
        validate.setAccessible(true);
        Object linear = interpolate.invoke(route, guide);
        Object rounded = round.invoke(route, linear, 128D);
        Object curve = resample.invoke(route, rounded);
        HydrologyRegionalRoute.Refinement accepted = (HydrologyRegionalRoute.Refinement) validate.invoke(route, curve, "default", false, planner.sampler);

        assertNull(accepted.toString(), accepted.rejection());
        assertEquals(guide.getFirst(), accepted.points().getFirst());
        assertEquals(guide.getLast(), accepted.points().getLast());
        Object sharper = resample.invoke(route, round.invoke(route, linear, 64D));
        HydrologyRegionalRoute.Refinement rejected = (HydrologyRegionalRoute.Refinement) validate.invoke(route, sharper, "default", false, planner.sampler);
        assertEquals(HydrologyCandidateRejection.SURFACE_SHAPE_UNSUPPORTED, rejected.rejection());
    }

    @Test
    public void splineOvershootCannotDiscardAValidNarrowDetour() {
        HydrologyTerrainSampler terrain = (x, z) -> {
            boolean first = Math.abs(z) <= 8 && x >= -8 && x <= 136;
            boolean middle = Math.abs(x - 128) <= 8 && z >= -8 && z <= 136;
            boolean last = Math.abs(z - 128) <= 8 && x >= 120 && x <= 264;
            return land(first || middle || last ? "water" : "lava");
        };
        HydrologyPlanner planner = new HydrologyPlanner(15L, withMeanders(
                new HydrologyPlannerSettings.Meanders(64, 12, 0D, 0D, 0D, 0, 150D)), terrain);
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(128, 70, 0),
                new HydrologyPoint(128, 70, 128), new HydrologyPoint(256, 70, 128));
        HydrologyRegionalRoute.Refinement repaired = new HydrologyRegionalRoute(planner).select(guide, "water", false, planner.sampler, candidate -> new HydrologyRegionalRoute.Attempt(candidate, HydrologyRegionalNetwork.EMPTY, null)).refinement();

        assertNull(repaired.toString(), repaired.rejection());
        SurfaceCenterline centerline = SurfaceCenterline.densify(repaired.points());
        for (int station = 0; station < centerline.size(); station++) {
            assertEquals(List.of("water"), terrain.sample(centerline.x()[station], centerline.z()[station]).preferredProfileKeys());
        }
    }

    private static HydrologyPlannerSettings withMeanders(HydrologyPlannerSettings.Meanders meanders) {
        HydrologyPlannerSettings settings = HydrologyRegionalPlannerTest.settings(false, 1);
        HydrologyPlannerSettings.Geometry geometry = settings.geometry();
        return new HydrologyPlannerSettings(settings.seaLevel(), settings.routing(), settings.surface(), settings.hydraulics(),
                settings.underground(), settings.outlets(), new HydrologyPlannerSettings.Geometry(meanders,
                geometry.surface(), geometry.underground(), geometry.grottos(), geometry.drops()), settings.deepFluids(),
                settings.surfacePools(), settings.widestShoreBiomeWidth(), settings.seaCaves(), settings.surfacePolicyBounds());
    }

    private static HydrologyTerrainSample land(String profile) {
        return new HydrologyTerrainSample(70, 0D, false, false, 38, 40,
                true, true, true, false, false, false,
                0D, 1D, 1D, 1D, 1D, 1D, 1D, 1D,
                "land", "land", "land", "land", "land", "land", List.of(profile), List.of(),
                Double.NaN, null, Double.NaN, true, SurfaceRiverPolicy.INHERIT);
    }
}
