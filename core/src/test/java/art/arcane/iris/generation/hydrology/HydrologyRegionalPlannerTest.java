package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceBounds;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalPlannerTest {
    @Test(timeout = 120000)
    public void reusedRegionalTilePreservesAdjacentPlanningWithoutSamplingDuringRestore() {
        HydrologyPlannerSettings settings = settings(false, 8);
        HydrologyTerrainSampler terrain = coast();
        HydrologyPlanner baseline = new HydrologyPlanner(71L, settings, terrain);
        RiverCourse regional = baseline.regional.draft(new HydrologyTileKey(0, 0)).courses().getFirst();
        HydrologyTileKey key = crossedTiles(regional, settings.routing().tileSize()).stream()
                .min(Comparator.comparingInt(HydrologyTileKey::tileX).thenComparingInt(HydrologyTileKey::tileZ))
                .orElseThrow();
        HydrologyTile original = baseline.plan(key);
        assertTrue(original.regionalCourseIds().contains(regional.id()));
        HydrologyTileKey adjacent = new HydrologyTileKey(key.tileX(), key.tileZ() + 1);
        HydrologyTile expected = baseline.plan(adjacent);
        AtomicBoolean planning = new AtomicBoolean();
        HydrologyPlanner restored = new HydrologyPlanner(71L, settings, (x, z) -> {
            assertTrue("Restoring final ownership must not sample terrain", planning.get());
            return terrain.sample(x, z);
        });

        restored.reuseResolvedTile(original);
        planning.set(true);

        assertEquals(expected, restored.plan(adjacent));
    }

    @Test(timeout = 120000)
    public void oneRegionalCourseCrossesSeveralGenerationTilesWithStableHydraulics() {
        HydrologyPlanner planner = new HydrologyPlanner(71L, settings(false, 8), coast());
        HydrologyRegionalNetwork network = planner.regional.draft(new HydrologyTileKey(0, 0));
        assertFalse("The regional coast must accept a complete trunk", network.courses().isEmpty());
        RiverCourse course = network.courses().getFirst();
        assertGraphEndpoints(network, course);
        Set<HydrologyTileKey> keys = crossedTiles(course, 512);
        assertTrue("A regional trunk must continue across generation tiles", keys.size() >= 3);
        assertTrue(course.hydraulicallyNonRising());
        for (HydrologyTileKey key : keys) {
            RiverFootprint footprint = planner.regional.materialize(network, planner.regional.tileBounds(key));
            for (HydrologyColumnSample column : footprint.columns().values()) {
                assertTrue(planner.regional.tileBounds(key).contains(column.x(), column.z()));
                assertTrue(column.layers().stream().allMatch(layer -> layer.feature().courseId() == course.id()));
            }
        }
        SurfaceBounds firstBounds = new SurfaceBounds(400, -128, 650, 2176);
        SurfaceBounds secondBounds = new SurfaceBounds(500, -128, 750, 2176);
        RiverFootprint first = planner.regional.materialize(network, firstBounds);
        RiverFootprint second = planner.regional.materialize(network, secondBounds);
        int overlap = 0;
        for (HydrologyColumnSample column : first.columns().values()) {
            if (secondBounds.contains(column.x(), column.z())) {
                assertEquals(column, second.sample(column.x(), column.z()).orElseThrow());
                overlap++;
            }
        }
        assertTrue("Tile windows must share identical accepted bank and water columns", overlap > 0);
    }

    @Test(timeout = 120000)
    public void coastalChannelHasTwoReceivingSeasAndOneWaterLevel() {
        HydrologyTerrainSampler terrain = (x, z) -> x < 128 || x >= 1664
                ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(65, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(19L, settings(true, 8), terrain);
        HydrologyRegionalNetwork network = planner.regional.draft(new HydrologyTileKey(0, 0));
        assertFalse("The lowland strip must accept a coastal channel", network.courses().isEmpty());
        RiverCourse course = network.courses().getFirst();
        assertGraphEndpoints(network, course);
        assertEquals(HydrologyFeatureType.MOUTH, course.segments().getFirst().type());
        assertEquals(HydrologyFeatureType.MOUTH, course.segments().getLast().type());
        assertEquals(2, network.outlets().size());
        for (RiverOutlet outlet : network.outlets()) {
            HydrologyTerrainSample ocean = terrain.sample(outlet.connectionPoint().x(), outlet.connectionPoint().z());
            assertTrue(ocean.ocean());
            assertTrue(ocean.naturalHeight() < 63);
        }
        for (HydraulicSegment segment : course.segments()) {
            assertEquals(63, segment.upstreamHeadY());
            assertEquals(63, segment.downstreamHeadY());
        }
        assertTrue(crossedTiles(course, 512).size() >= 3);
    }

    @Test(timeout = 120000)
    public void rejectedBasinKeepsBoundedDeterministicDiagnostics() {
        HydrologyPlanner planner = new HydrologyPlanner(71L, settings(false, 8),
                (x, z) -> HydrologyTerrainSample.openLand(70, 0D, "land"));
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        HydrologyRegionalNetwork first = planner.regional.draft(key);
        assertTrue(first.courses().isEmpty());
        assertFalse(first.diagnostics().isEmpty());
        assertTrue(first.diagnostics().size() <= 64);
        assertEquals(HydrologyCandidateKind.REGIONAL_SOURCE, first.diagnostics().getFirst().kind());
        assertEquals(HydrologyCandidateRejection.NO_LEGAL_OUTLET, first.diagnostics().getFirst().rejection());
        planner.regional.clear();
        assertEquals(first.diagnostics(), planner.regional.draft(key).diagnostics());
        HydrologyRegionalNetwork window = planner.regional.coursesIn(new SurfaceBounds(0, 0, 511, 511));
        assertTrue(window.diagnostics().isEmpty());
        assertEquals(first.diagnostics(), planner.regional.diagnosticsIn(new SurfaceBounds(0, 0, 511, 511)));
    }

    @Test(timeout = 120000)
    public void regionalCacheEvictionAndConcurrentRequestsKeepTheSameCourse() throws Exception {
        HydrologyPlannerSettings settings = settings(false, 1);
        HydrologyTerrainSampler terrain = coast();
        Set<Thread> samplingThreads = ConcurrentHashMap.newKeySet();
        HydrologyPlanner planner = new HydrologyPlanner(71L, settings, (x, z) -> {
            samplingThreads.add(Thread.currentThread());
            return terrain.sample(x, z);
        });
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        HydrologyRegionalNetwork first = planner.regional.draft(key);
        assertFalse(first.courses().isEmpty());
        assertTrue(first.courses().stream().allMatch(course -> course.type() == RiverCourseType.SURFACE));
        SurfaceBounds bounds = networkBounds(first).expand(settings.publicationRadius());
        RiverFootprint footprint = planner.regional.materialize(first, bounds);
        assertFalse(footprint.columns().isEmpty());
        assertTrue(footprint.columns().values().stream().anyMatch(column -> column.layers().stream().anyMatch(HydrologyColumnLayer::fluidOwned)));
        planner.regional.draft(new HydrologyTileKey(0, 1));
        assertEquals(first, planner.regional.draft(key));
        planner.regional.clear();
        samplingThreads.clear();
        ForkJoinPool workers = new ForkJoinPool(4);
        try {
            ArrayList<CompletableFuture<HydrologyRegionalNetwork>> futures = new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                futures.add(CompletableFuture.supplyAsync(() -> planner.regional.draft(key), workers));
            }
            HydrologyRegionalNetwork parallel = futures.getFirst().get(90, TimeUnit.SECONDS);
            for (CompletableFuture<HydrologyRegionalNetwork> future : futures) {
                assertEquals(first, future.get(90, TimeUnit.SECONDS));
            }
            assertTrue("A cold successful basin must sample on multiple workers: " + samplingThreads, samplingThreads.size() > 1);
            assertNetworkRawBits(first, parallel);
            RiverFootprint actual = workers.submit(() -> planner.regional.materialize(parallel, bounds)).get(30, TimeUnit.SECONDS);
            assertEquals(footprint.columns(), actual.columns());
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 120000)
    public void aHighIsthmusCannotBecomeACoastalChannel() {
        HydrologyTerrainSampler terrain = (x, z) -> x < 128 || x >= 1664
                ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(x >= 768 && x <= 1024 ? 90 : 65, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(19L, settings(true, 8), terrain);
        HydrologyRegionalNetwork network = planner.regional.draft(new HydrologyTileKey(0, 0));
        for (RiverCourse course : network.courses()) {
            assertFalse("The coastal incision budget must exclude the ridge",
                    course.segments().getFirst().type() == HydrologyFeatureType.MOUTH);
        }
    }

    private static SurfaceBounds networkBounds(HydrologyRegionalNetwork network) {
        int minimumX = Integer.MAX_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (RiverCourse course : network.courses()) {
            for (HydraulicSegment segment : course.segments()) {
                for (HydrologyPoint point : segment.centerline()) {
                    minimumX = Math.min(minimumX, point.x());
                    minimumZ = Math.min(minimumZ, point.z());
                    maximumX = Math.max(maximumX, point.x());
                    maximumZ = Math.max(maximumZ, point.z());
                }
            }
        }
        return new SurfaceBounds(minimumX, minimumZ, maximumX, maximumZ);
    }

    private static void assertNetworkRawBits(HydrologyRegionalNetwork expected, HydrologyRegionalNetwork actual) {
        assertEquals(expected, actual);
        for (int index = 0; index < expected.nodes().size(); index++) {
            assertEquals(Double.doubleToRawLongBits(expected.nodes().get(index).potential()),
                    Double.doubleToRawLongBits(actual.nodes().get(index).potential()));
        }
        for (int index = 0; index < expected.edges().size(); index++) {
            assertEquals(Double.doubleToRawLongBits(expected.edges().get(index).cost()),
                    Double.doubleToRawLongBits(actual.edges().get(index).cost()));
        }
        for (int courseIndex = 0; courseIndex < expected.courses().size(); courseIndex++) {
            List<HydraulicSegment> expectedSegments = expected.courses().get(courseIndex).segments();
            List<HydraulicSegment> actualSegments = actual.courses().get(courseIndex).segments();
            for (int segmentIndex = 0; segmentIndex < expectedSegments.size(); segmentIndex++) {
                HydraulicChannelProfile first = expectedSegments.get(segmentIndex).channelProfile();
                HydraulicChannelProfile second = actualSegments.get(segmentIndex).channelProfile();
                for (int station = 0; station < first.size(); station++) {
                    assertEquals(Double.doubleToRawLongBits(first.widthAt(station)), Double.doubleToRawLongBits(second.widthAt(station)));
                    assertEquals(Double.doubleToRawLongBits(first.depthAt(station)), Double.doubleToRawLongBits(second.depthAt(station)));
                }
            }
        }
    }

    private static void assertGraphEndpoints(HydrologyRegionalNetwork network, RiverCourse course) {
        DrainageNode source = network.nodes().stream()
                .filter(node -> node.id() == course.sourceNodeId().orElseThrow()).findFirst().orElseThrow();
        HydrologyPoint start = course.segments().getFirst().type() == HydrologyFeatureType.MOUTH
                ? course.segments().getFirst().end() : course.segments().getFirst().start();
        assertEquals(start.x(), source.x());
        assertEquals(start.z(), source.z());
        for (RiverOutlet outlet : network.outlets()) {
            DrainageNode terminal = network.nodes().stream()
                    .filter(node -> node.id() == outlet.drainageNodeId()).findFirst().orElseThrow();
            assertEquals(outlet.landwardPoint().x(), terminal.x());
            assertEquals(outlet.landwardPoint().z(), terminal.z());
        }
    }

    private static Set<HydrologyTileKey> crossedTiles(RiverCourse course, int tileSize) {
        Set<HydrologyTileKey> keys = new HashSet<>();
        for (HydraulicSegment segment : course.segments()) {
            for (HydrologyPoint point : segment.centerline()) {
                keys.add(HydrologyTileKey.fromBlock(point.x(), point.z(), tileSize));
            }
        }
        return keys;
    }

    private static HydrologyTerrainSampler coast() {
        return (x, z) -> x < 128 ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(66 + Math.max(0, x - 128) / 256, 0D, "land");
    }

    static HydrologyPlannerSettings settings(boolean coastalChannels, int cachedBasins) {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Source source = new HydrologyPlannerSettings.Source(true, 0D, 64, 0, 0, 128);
        HydrologyPlannerSettings.Source undergroundSource = new HydrologyPlannerSettings.Source(false, 0D, 0, 0, 0, 128);
        HydrologyPlannerSettings.Regional regional = new HydrologyPlannerSettings.Regional(true, 128, 512, 1,
                cachedBasins, 32768, coastalChannels, 1D, 8);
        return new HydrologyPlannerSettings(63,
                new HydrologyPlannerSettings.Routing(512, 64, 1024, 2048, 128, 0,
                        1.5D, 24D, 2D, 0.2D, 1D, 0, regional),
                new HydrologyPlannerSettings.Surface(true, source, 4, 8, 2, 3, 16, 1.5D, base.surface().banks()),
                base.hydraulics(),
                HydrologyPlannerSettings.Underground.of(false, undergroundSource, 0, 50, 3, 8, 1, 3, 6, 14, false, 0),
                HydrologyPlannerSettings.Outlets.of(true, new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                        new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096), false, 12, 48, 8, 1, 2),
                base.geometry(), List.of(), List.of(), 0D, HydrologyPlannerSettings.SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE);
    }
}
