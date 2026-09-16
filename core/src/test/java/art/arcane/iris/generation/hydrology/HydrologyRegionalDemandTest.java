package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceBounds;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class HydrologyRegionalDemandTest {
    private static final HydrologyTileKey ORIGIN = new HydrologyTileKey(0, 0);
    private static final SurfaceBounds EXCLUDED = new SurfaceBounds(1792, 1792, 1807, 1807);

    @Test(timeout = 120000)
    public void excludedWindowDefersGeometryUntilDiagnosticsAndPreservesLaterRiver() {
        HydrologyPlannerSettings settings = localizedSettings();
        HydrologyTerrainSampler terrain = localizedCoast();
        AtomicInteger eagerGeometry = new AtomicInteger();
        HydrologyPlanner eager = planner(settings, terrain, eagerGeometry);
        HydrologyRegionalNetwork expected = eager.regional.draft(ORIGIN);
        assertFalse("The excluded basin must contain a successful river", expected.courses().isEmpty());
        assertTrue(eagerGeometry.get() > 0);
        AtomicInteger geometry = new AtomicInteger();
        HydrologyPlanner demand = planner(settings, terrain, geometry);

        assertEquals(HydrologyRegionalNetwork.EMPTY, demand.regional.coursesIn(EXCLUDED));
        assertEquals("A distant query must not build fine source geometry", 0, geometry.get());
        assertEquals(expected.diagnostics(), demand.regional.diagnosticsIn(EXCLUDED));
        assertTrue("Diagnostics must finish the complete source/outlet competition", geometry.get() > 0);
        assertEquals(expected, demand.regional.draft(ORIGIN));

        SurfaceBounds nearby = window(expected.courses().getFirst().segments().getFirst().start());
        HydrologyRegionalNetwork expectedWindow = eager.regional.coursesIn(nearby);
        assertFalse(expectedWindow.courses().isEmpty());
        HydrologyRegionalNetwork actual = demand.regional.coursesIn(nearby);
        assertEquals(expectedWindow, actual);
        assertEquals(eager.regional.materialize(expectedWindow, nearby).columns(),
                demand.regional.materialize(actual, nearby).columns());

        demand.regional.clear();
        geometry.set(0);
        assertEquals(HydrologyRegionalNetwork.EMPTY, demand.regional.coursesIn(EXCLUDED));
        assertEquals(0, geometry.get());
        assertEquals(expectedWindow, demand.regional.coursesIn(nearby));
    }

    @Test(timeout = 120000)
    public void coldQueriesRetainCurvedRiverExtremesAndTheirFootprints() {
        HydrologyPlannerSettings settings = localizedSettings();
        HydrologyTerrainSampler terrain = localizedCoast();
        HydrologyPlanner eager = planner(settings, terrain, new AtomicInteger());
        HydrologyRegionalNetwork full = eager.regional.draft(ORIGIN);
        assertFalse(full.courses().isEmpty());
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (HydraulicSegment segment : full.courses().getFirst().segments()) {
            points.addAll(segment.centerline());
        }
        HydrologyPoint minimumX = points.getFirst();
        HydrologyPoint maximumX = minimumX;
        HydrologyPoint minimumZ = minimumX;
        HydrologyPoint maximumZ = minimumX;
        for (HydrologyPoint point : points) {
            if (point.x() < minimumX.x()) {
                minimumX = point;
            }
            if (point.x() > maximumX.x()) {
                maximumX = point;
            }
            if (point.z() < minimumZ.z()) {
                minimumZ = point;
            }
            if (point.z() > maximumZ.z()) {
                maximumZ = point;
            }
        }
        assertTrue("The route must contain a lateral bend", maximumZ.z() - minimumZ.z() > 4);
        for (HydrologyPoint point : List.of(minimumX, maximumX, minimumZ, maximumZ)) {
            SurfaceBounds bounds = window(point);
            HydrologyRegionalNetwork expected = eager.regional.coursesIn(bounds);
            assertFalse(expected.courses().isEmpty());
            HydrologyPlanner demand = planner(settings, terrain, new AtomicInteger());
            HydrologyRegionalNetwork actual = demand.regional.coursesIn(bounds);
            assertEquals(expected, actual);
            assertEquals(eager.regional.materialize(expected, bounds).columns(),
                    demand.regional.materialize(actual, bounds).columns());
        }
    }

    @Test(timeout = 120000)
    public void aLowerRankedNeighborStillRejectsAnOverlappingAcceptedCourse() {
        HydrologyPlanner planner = planner(localizedSettings(), localizedCoast(), new AtomicInteger());
        HydrologyRegionalNetwork full = planner.regional.draft(ORIGIN);
        assertFalse(full.courses().isEmpty());
        RiverCourse course = full.courses().getFirst();
        HydrologyTileKey neighbor = new HydrologyTileKey(0, -1);
        assertTrue(Long.compareUnsigned(HydrologyHash.mix(71L, 0x524547424153494eL, 0, -1),
                HydrologyHash.mix(71L, 0x524547424153494eL, 0, 0)) < 0);
        HydrologyRegionalPlanner regional = spy(planner.regional);
        RiverCourse blocker = copyIdentity(course);
        assertTrue(regional.conflicts(course, List.of(blocker)));
        doReturn(new HydrologyRegionalNetwork(List.of(), List.of(), List.of(), List.of(blocker), List.of(), List.of()))
                .when(regional).draft(neighbor);

        HydrologyRegionalNetwork actual = regional.coursesIn(window(course.segments().getFirst().start()));

        verify(regional).draft(neighbor);
        assertFalse(actual.courses().stream().anyMatch(candidate -> candidate.id() == course.id()));
    }

    @Test(timeout = 120000)
    public void coastalBasinsKeepBothReceivingSeasWhenQueriedAwayFromTheChannel() {
        HydrologyPlannerSettings settings = HydrologyRegionalPlannerTest.settings(true, 8);
        HydrologyTerrainSampler terrain = (x, z) -> x < 128 || x >= 1664
                ? HydrologyTerrainSample.ocean(60, "ocean") : HydrologyTerrainSample.openLand(65, 0D, "land");
        HydrologyPlanner eager = new HydrologyPlanner(19L, settings, terrain);
        HydrologyRegionalNetwork expected = eager.regional.draft(ORIGIN);
        assertEquals(HydrologyFeatureType.MOUTH, expected.courses().getFirst().segments().getFirst().type());
        assertEquals(2, expected.outlets().size());
        AtomicInteger geometry = new AtomicInteger();
        HydrologyGeometrySampler delegate = HydrologyGeometrySampler.deterministic(terrain);
        HydrologyPlanner demand = new HydrologyPlanner(19L, settings, terrain, request -> {
            geometry.incrementAndGet();
            return delegate.sample(request);
        }, 0, footprint -> new HydrologyTerrainCaveVoxelView(terrain, 63, 0, 128));

        demand.regional.coursesIn(new SurfaceBounds(1536, 1536, 1551, 1551));

        assertTrue("A seeded coastal channel must remain unpruned", geometry.get() > 0);
        assertEquals(expected, demand.regional.draft(ORIGIN));
        assertEquals(expected.diagnostics(), demand.regional.diagnosticsIn(EXCLUDED));
    }

    @Test(timeout = 15000)
    public void clearSeparatesConcurrentCoarseAndCompleteDraftPublications() throws Exception {
        AtomicInteger grids = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(85, 0D, "land");
        HydrologyRoutingTerrainSampler routing = new HydrologyRoutingTerrainSampler() {
            @Override
            public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
                int height = grids.incrementAndGet() == 1 ? 70 : 85;
                if (height == 70) {
                    started.countDown();
                    await(release);
                }
                HydrologyTerrainSample[] samples = new HydrologyTerrainSample[request.width() * request.width()];
                Arrays.fill(samples, HydrologyTerrainSample.openLand(height, 0D, "land"));
                return samples;
            }

            @Override
            public NaturalClassification classifyNatural(int blockX, int blockZ) {
                return NaturalClassification.LAND;
            }
        };
        HydrologyPlanner planner = new HydrologyPlanner(71L, HydrologyRegionalPlannerTest.settings(false, 8), terrain,
                routing, HydrologyGeometrySampler.deterministic(terrain), 0,
                footprint -> new HydrologyTerrainCaveVoxelView(terrain, 63, 0, 128));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<HydrologyRegionalNetwork> old = worker.submit(() -> planner.regional.coursesIn(new SurfaceBounds(1024, 1024, 1039, 1039)));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            planner.regional.clear();
            HydrologyRegionalNetwork current = planner.regional.draft(ORIGIN);
            assertEquals(85, current.diagnostics().getFirst().point().y());
            release.countDown();
            assertEquals(HydrologyRegionalNetwork.EMPTY, old.get(5, TimeUnit.SECONDS));
            assertSame(current, planner.regional.draft(ORIGIN));
            assertEquals(current.diagnostics(), planner.regional.diagnosticsIn(new SurfaceBounds(1024, 1024, 1039, 1039)));
            assertEquals(2, grids.get());
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static RiverCourse copyIdentity(RiverCourse course) {
        long id = course.id() + 1L;
        ArrayList<HydraulicSegment> segments = new ArrayList<>();
        for (HydraulicSegment segment : course.segments()) {
            segments.add(new HydraulicSegment(segment.id(), id, segment.type(), segment.upstreamHeadY(),
                    segment.downstreamHeadY(), segment.width(), segment.depth(), segment.fallingFluid(),
                    segment.receivingPool(), segment.centerline(), segment.channelProfile()));
        }
        return new RiverCourse(id, course.type(), course.sourceNodeId(), OptionalLong.of(course.outletId().orElseThrow() + 1L),
                course.profileKey(), course.discharge(), course.drainageEdges(), segments);
    }

    private static SurfaceBounds window(HydrologyPoint point) {
        return new SurfaceBounds(point.x() - 32, point.z() - 32, point.x() + 32, point.z() + 32);
    }

    private static HydrologyPlanner planner(HydrologyPlannerSettings settings, HydrologyTerrainSampler terrain, AtomicInteger geometry) {
        HydrologyGeometrySampler delegate = HydrologyGeometrySampler.deterministic(terrain);
        return new HydrologyPlanner(71L, settings, terrain, request -> {
            geometry.incrementAndGet();
            return delegate.sample(request);
        }, 0, footprint -> new HydrologyTerrainCaveVoxelView(terrain, 63, 0, 128));
    }

    private static HydrologyTerrainSampler localizedCoast() {
        return (x, z) -> x < 128 ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(x >= 768 && x <= 1024 && z >= 0 && z <= 256 ? 70 : 66, 0D, "land");
    }

    private static HydrologyPlannerSettings localizedSettings() {
        HydrologyPlannerSettings base = HydrologyRegionalPlannerTest.settings(false, 8);
        HydrologyPlannerSettings.Surface surface = base.surface();
        HydrologyPlannerSettings.Source source = new HydrologyPlannerSettings.Source(true, 0D, 70, 0, 0, 128);
        return new HydrologyPlannerSettings(base.seaLevel(), base.routing(),
                new HydrologyPlannerSettings.Surface(surface.enabled(), source, surface.minimumWidth(), surface.maximumWidth(),
                        surface.minimumDepth(), surface.maximumDepth(), surface.maximumIncision(), surface.shoreWidth(), surface.banks()),
                base.hydraulics(), base.underground(), base.outlets(), base.geometry(), base.deepFluids(), base.surfacePools(),
                base.widestShoreBiomeWidth(), base.seaCaves(), base.surfacePolicyBounds());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
