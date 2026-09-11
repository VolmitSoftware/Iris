package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.policy.SurfaceRiverPolicy;
import art.arcane.iris.engine.hydrology.surface.SurfaceCenterline;
import org.junit.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalTerrainRefinerTest {
    @Test
    public void aHigherHeadDetourSurvivesACheaperInfeasibleArrival() {
        int[][] heights = {
                {86, 94, 77, 91, 75, 85, 82, 88, 74},
                {84, 94, 83, 85, 72, 76, 73, 80, 72},
                {73, 85, 78, 77, 73, 82, 84, 81, 79},
                {83, 77, 92, 81, 84, 80, 93, 94, 96},
                {90, 73, 76, 84, 74, 73, 91, 80, 85},
                {91, 96, 90, 75, 91, 84, 94, 96, 95},
                {84, 76, 80, 92, 72, 73, 96, 75, 86},
                {94, 93, 86, 81, 80, 89, 75, 93, 77},
                {85, 95, 85, 73, 88, 82, 81, 94, 92}
        };
        HydrologyTerrainSampler terrain = (x, z) -> {
            int gridX = Math.floorDiv(x, 16);
            int gridZ = Math.floorDiv(z, 16) + 4;
            return gridX < 0 || gridX >= 9 || gridZ < 0 || gridZ >= 9
                    ? HydrologyTerrainSample.ocean(60, "outside")
                    : HydrologyTerrainSample.openLand(heights[gridZ][gridX], 0D, "land");
        };
        HydrologyPlanner planner = new HydrologyPlanner(1L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 90, 0), new HydrologyPoint(128, 85, 0));
        HydrologyRegionalTerrainRefiner refiner = new HydrologyRegionalTerrainRefiner(planner);
        List<HydrologyPoint> path = refiner.refine(guide, "default", false, terrain);

        assertFalse(path.isEmpty());
        assertEquals(guide.getFirst(), path.getFirst());
        assertEquals(guide.getLast(), path.getLast());
        HydrologyRegionalHydraulics hydraulics = new HydrologyRegionalHydraulics(planner.settings);
        SurfaceCenterline centerline = SurfaceCenterline.densify(path);
        int available = 90;
        for (int station = 0; station < centerline.size(); station++) {
            HydrologyTerrainSample sample = terrain.sample(centerline.x()[station], centerline.z()[station]);
            assertFalse(sample.ocean());
            int supported = hydraulics.supportedHead(new HydrologyRegionalHydraulics.HeadStation(
                    centerline.x()[station], centerline.z()[station], centerline.tangentX()[station],
                    centerline.tangentZ()[station], sample, available), terrain);
            assertTrue(supported <= available);
            available = supported;
            assertTrue("station=" + station, available >= hydraulics.minimumHead(sample));
        }
        assertEquals(path, refiner.refine(guide, "default", false, terrain));
        assertEquals(path, new HydrologyRegionalTerrainRefiner(planner).refine(guide, "default", false, terrain));
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
    public void onlyTheRetainedDryPrefixNeedsTerrainRepair() {
        HydrologyTerrainSampler terrain = (x, z) -> x >= 806 ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(x >= 800 ? 60 : x >= 96 && x <= 160 && Math.abs(z) < 32 ? 86 : 70,
                0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(256, 70, 0),
                new HydrologyPoint(512, 70, 0), new HydrologyPoint(1024, 70, 0));
        assertTrue(new HydrologyRegionalTerrainRefiner(planner).refine(guide, "default", false, terrain).isEmpty());
        HydrologyRegionalRoute.Refinement repaired = new HydrologyRegionalRoute(planner).select(guide, "default", false, terrain, candidate -> new HydrologyRegionalRoute.Attempt(candidate, HydrologyRegionalNetwork.EMPTY, null)).refinement();

        assertNull(repaired.toString(), repaired.rejection());
        assertEquals(new HydrologyPoint(799, 63, 0), repaired.oceanEntry().landward());
        assertEquals(new HydrologyPoint(806, 63, 0), repaired.oceanEntry().receiving());
        assertTrue(repaired.points().stream().anyMatch(point -> Math.abs(point.z()) >= 32));
        assertTrue(repaired.points().stream().allMatch(point -> point.x() < 800));
    }

    @Test
    public void aCoarseOceanHitBeforeMinimumLengthStillAllowsADryTerrainDetour() {
        HydrologyTerrainSampler terrain = (x, z) -> x >= 800 || x >= 96 && x <= 160 && Math.abs(z) < 32
                ? HydrologyTerrainSample.ocean(60, "ocean") : HydrologyTerrainSample.openLand(70, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(256, 70, 0),
                new HydrologyPoint(512, 70, 0), new HydrologyPoint(799, 70, 0));
        HydrologyRegionalRoute.Refinement repaired = new HydrologyRegionalRoute(planner).select(guide, "default", false, terrain, candidate -> new HydrologyRegionalRoute.Attempt(candidate, HydrologyRegionalNetwork.EMPTY, null)).refinement();

        assertNull(repaired.toString(), repaired.rejection());
        assertTrue(repaired.points().stream().anyMatch(point -> Math.abs(point.z()) >= 32));
        assertEquals(guide.getLast(), repaired.points().getLast());
    }

    @Test
    public void naturallyFloodedLandCannotBeUsedAsAnOwnedDryReach() {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(
                x >= 96 && x <= 160 && Math.abs(z) < 32 ? 60 : 70, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(256, 70, 0));
        List<HydrologyPoint> repaired = new HydrologyRegionalTerrainRefiner(planner).refine(guide, "default", false, terrain);

        assertFalse(repaired.isEmpty());
        SurfaceCenterline centerline = SurfaceCenterline.densify(repaired);
        for (int station = 0; station < centerline.size(); station++) {
            HydrologyTerrainSample sampled = terrain.sample(centerline.x()[station], centerline.z()[station]);
            assertFalse(sampled.ocean());
            assertTrue(sampled.naturalHeight() >= planner.settings.seaLevel());
        }
    }

    @Test
    public void repeatedCoarseAnchorsCannotCreateRetracedWaterways() {
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1),
                (x, z) -> HydrologyTerrainSample.openLand(70, 0D, "land"));
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(128, 70, 0),
                new HydrologyPoint(128, 70, 128), new HydrologyPoint(128, 70, 0), new HydrologyPoint(256, 70, 0));
        List<HydrologyPoint> refined = new HydrologyRegionalTerrainRefiner(planner).refine(guide, "default", false, planner.sampler);

        assertEquals(List.of(guide.getFirst(), guide.getLast()), refined);
    }

    @Test
    public void redundantCoarseAnchorHairpinsUseTheClearTerrainShortcut() {
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1),
                (x, z) -> HydrologyTerrainSample.openLand(70, 0D, "land"));
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(128, 70, 0),
                new HydrologyPoint(160, 70, 32), new HydrologyPoint(144, 70, 48), new HydrologyPoint(160, 70, 48),
                new HydrologyPoint(192, 70, 16), new HydrologyPoint(256, 70, 0));
        List<HydrologyPoint> refined = new HydrologyRegionalTerrainRefiner(planner).refine(guide, "default", false, planner.sampler);

        assertEquals(guide.getFirst(), refined.getFirst());
        assertEquals(guide.getLast(), refined.getLast());
        assertTrue(refined.size() <= 3);
        for (int index = 1; index < refined.size(); index++) {
            assertTrue(refined.get(index).x() > refined.get(index - 1).x());
        }
    }

    @Test
    public void repairsUnsampledOceanPocketsAtPositiveAndNegativeCoordinates() {
        for (int origin : new int[]{0, -512}) {
            HydrologyTerrainSampler terrain = (x, z) -> x >= origin + 96 && x <= origin + 160 && Math.abs(z) < 32
                    ? HydrologyTerrainSample.ocean(60, "ocean") : HydrologyTerrainSample.openLand(70, 0D, "land");
            List<HydrologyPoint> guide = List.of(new HydrologyPoint(origin, 70, 0), new HydrologyPoint(origin + 256, 70, 0));
            HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
            HydrologyRegionalTerrainRefiner refiner = new HydrologyRegionalTerrainRefiner(planner);
            List<HydrologyPoint> first = refiner.refine(guide, "default", false, planner.sampler);

            assertFalse(first.isEmpty());
            assertEquals(guide.getFirst(), first.getFirst());
            assertEquals(guide.getLast(), first.getLast());
            SurfaceCenterline centerline = SurfaceCenterline.densify(first);
            for (int station = 0; station < centerline.size(); station++) {
                assertFalse(terrain.sample(centerline.x()[station], centerline.z()[station]).ocean());
            }
            assertEquals(first, refiner.refine(guide, "default", false, planner.sampler));
            assertEquals(first, new HydrologyRegionalTerrainRefiner(planner).refine(guide, "default", false, planner.sampler));
        }
    }

    @Test
    public void aWaterCourseDetoursAroundALavaOnlyBiome() {
        HydrologyTerrainSampler terrain = (x, z) -> land(x >= 96 && x <= 160 && Math.abs(z) < 32 ? "lava" : "water");
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(256, 70, 0));
        List<HydrologyPoint> refined = new HydrologyRegionalTerrainRefiner(planner).refine(guide, "water", false, planner.sampler);

        assertFalse(refined.isEmpty());
        SurfaceCenterline centerline = SurfaceCenterline.densify(refined);
        for (int station = 0; station < centerline.size(); station++) {
            assertEquals(List.of("water"), terrain.sample(centerline.x()[station], centerline.z()[station]).preferredProfileKeys());
        }
    }

    @Test
    public void repairedTerrainRouteStillPassesOrganicShapeValidation() {
        HydrologyTerrainSampler terrain = (x, z) -> x >= 96 && x <= 160 && Math.abs(z) < 32
                ? HydrologyTerrainSample.ocean(60, "ocean") : HydrologyTerrainSample.openLand(70, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        HydrologyRegionalRoute.Refinement refined = new HydrologyRegionalRoute(planner).select(
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(256, 70, 0)), "default", false, planner.sampler, candidate -> new HydrologyRegionalRoute.Attempt(candidate, HydrologyRegionalNetwork.EMPTY, null)).refinement();

        assertNull(refined.toString(), refined.rejection());
        assertTrue(refined.points().stream().anyMatch(point -> Math.abs(point.z()) >= 32));
    }

    @Test
    public void anUnavoidableOceanBarrierRejectsWithinTheTerrainSampleBudget() {
        AtomicInteger calls = new AtomicInteger();
        HydrologyTerrainSampler terrain = (x, z) -> {
            calls.incrementAndGet();
            return x >= 96 && x <= 160 ? HydrologyTerrainSample.ocean(60, "ocean")
                    : HydrologyTerrainSample.openLand(70, 0D, "land");
        };
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        List<HydrologyPoint> refined = new HydrologyRegionalTerrainRefiner(planner).refine(
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(256, 70, 0)), "default", false, planner.sampler);

        assertTrue(refined.isEmpty());
        assertTrue("terrain samples=" + calls.get(), calls.get() <= 65536);
    }

    @Test
    public void unavailableTerrainIsSampledOnceAndCountsTowardTheBudget() {
        Map<Long, Integer> unavailable = new HashMap<>();
        AtomicInteger calls = new AtomicInteger();
        HydrologyTerrainSampler terrain = (x, z) -> {
            calls.incrementAndGet();
            if (x >= 96 && x <= 160 && Math.abs(z) < 32) {
                unavailable.merge(RiverFootprint.pack(x, z), 1, Integer::sum);
                return null;
            }
            return HydrologyTerrainSample.openLand(70, 0D, "land");
        };
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        List<HydrologyPoint> repaired = new HydrologyRegionalTerrainRefiner(planner).refine(
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(256, 70, 0)), "default", false, planner.sampler);

        assertFalse(repaired.isEmpty());
        assertFalse(unavailable.isEmpty());
        assertTrue(unavailable.values().stream().allMatch(count -> count == 1));
        assertTrue(calls.get() <= 65536);
    }

    @Test
    public void anUnsampledRidgeUsesTheLowerValleyWithoutIncreasingIncision() {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(
                x >= 96 && x <= 160 && Math.abs(z) < 32 ? 94 : 70, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(256, 70, 0));
        List<HydrologyPoint> repaired = new HydrologyRegionalTerrainRefiner(planner).refine(guide, "default", false, planner.sampler);

        assertFalse(repaired.isEmpty());
        SurfaceCenterline centerline = SurfaceCenterline.densify(repaired);
        for (int station = 0; station < centerline.size(); station++) {
            assertEquals(70, terrain.sample(centerline.x()[station], centerline.z()[station]).naturalHeight());
        }
    }

    @Test
    public void anImpossibleIntermediateAnchorCanBeSkippedWithinTheSameValley() {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(
                x < 48 || x >= 120 && x <= 136 && Math.abs(z) < 8 ? 90 : 65, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 90, 0), new HydrologyPoint(128, 90, 0),
                new HydrologyPoint(256, 65, 0));
        HydrologyRegionalTerrainRefiner refiner = new HydrologyRegionalTerrainRefiner(planner);
        List<HydrologyPoint> repaired = refiner.refine(guide, "default", false, planner.sampler);

        assertFalse(repaired.isEmpty());
        assertEquals(guide.getFirst(), repaired.getFirst());
        assertEquals(guide.getLast(), repaired.getLast());
        assertFalse(repaired.contains(guide.get(1)));
        SurfaceCenterline centerline = SurfaceCenterline.densify(repaired);
        HydrologyRegionalHydraulics hydraulics = new HydrologyRegionalHydraulics(planner.settings);
        int head = Integer.MAX_VALUE;
        for (int station = 0; station < centerline.size(); station++) {
            HydrologyTerrainSample sample = terrain.sample(centerline.x()[station], centerline.z()[station]);
            head = Math.min(head, hydraulics.maximumHead(sample));
            assertTrue(hydraulics.minimumHead(sample) <= head);
        }
        assertEquals(repaired, refiner.refine(guide, "default", false, planner.sampler));
    }

    @Test
    public void aLowFineDetourCannotResetTheHeadBeforeTheNextRidge() {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(
                x >= 96 && x <= 160 && Math.abs(z) < 32 ? 65 : x >= 384 ? 92 : 80, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        List<HydrologyPoint> guide = List.of(new HydrologyPoint(0, 80, 0), new HydrologyPoint(256, 80, 0),
                new HydrologyPoint(512, 92, 0));
        List<HydrologyPoint> repaired = new HydrologyRegionalTerrainRefiner(planner).refine(guide, "default", false, planner.sampler);

        assertFalse(repaired.isEmpty());
        assertTrue(repaired.stream().anyMatch(point -> Math.abs(point.z()) >= 32));
        SurfaceCenterline centerline = SurfaceCenterline.densify(repaired);
        HydrologyRegionalHydraulics hydraulics = new HydrologyRegionalHydraulics(planner.settings);
        int head = Integer.MAX_VALUE;
        for (int station = 0; station < centerline.size(); station++) {
            HydrologyTerrainSample sample = terrain.sample(centerline.x()[station], centerline.z()[station]);
            head = Math.min(head, hydraulics.maximumHead(sample));
            assertTrue(hydraulics.minimumHead(sample) <= head);
        }
    }

    @Test
    public void ridgeRepairRespectsTheLocalIncisionPolicy() {
        SurfaceRiverPolicy limited = new SurfaceRiverPolicy("valley", null, null, null, null, null, null, 4);
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(
                x >= 96 && x <= 160 && Math.abs(z) < 32 ? 76 : 70, 0D, "land").withSurfacePolicy(limited);
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        List<HydrologyPoint> repaired = new HydrologyRegionalTerrainRefiner(planner).refine(
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(256, 70, 0)), "default", false, planner.sampler);

        assertFalse(repaired.isEmpty());
        assertTrue(repaired.stream().anyMatch(point -> Math.abs(point.z()) >= 32));
    }

    @Test
    public void detourLengthRejectsBeforeDetailedSamplingOfTheRemainingCourse() {
        AtomicInteger maximumX = new AtomicInteger(Integer.MIN_VALUE);
        HydrologyTerrainSampler terrain = (x, z) -> {
            boolean coarseAnchor = Math.floorMod(x + 1, 256) <= 2 && Math.abs(z) <= 1;
            if (!coarseAnchor) {
                maximumX.accumulateAndGet(x, Math::max);
            }
            return x >= 96 && x <= 160 && Math.abs(z) < 32 ? HydrologyTerrainSample.ocean(60, "ocean")
                    : HydrologyTerrainSample.openLand(70, 0D, "land");
        };
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        ArrayList<HydrologyPoint> guide = new ArrayList<>();
        for (int x = 0; x <= planner.settings.routing().maximumRouteLength(); x += 256) {
            guide.add(new HydrologyPoint(x, 70, 0));
        }
        assertTrue(new HydrologyRegionalTerrainRefiner(planner).refine(guide, "default", false, planner.sampler).isEmpty());
        assertTrue("A rejected first detour must not sample later reaches", maximumX.get() < 512);
    }

    @Test
    public void anAlreadyOverlongGuideDoesNotSampleTerrain() {
        AtomicInteger calls = new AtomicInteger();
        HydrologyTerrainSampler terrain = (x, z) -> {
            calls.incrementAndGet();
            return HydrologyTerrainSample.openLand(70, 0D, "land");
        };
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        int before = calls.get();
        assertTrue(new HydrologyRegionalTerrainRefiner(planner).refine(
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(4096, 70, 0)), "default", false, planner.sampler).isEmpty());
        assertEquals(before, calls.get());
    }

    @Test
    public void clearancePreferenceDoesNotExcludeTheOnlyNarrowCorridor() {
        HydrologyTerrainSampler terrain = (x, z) -> {
            boolean horizontal = Math.abs(z) <= 3 && (x <= 80 || x >= 176);
            boolean detour = Math.abs(z - 16) <= 3 && x >= 61 && x <= 195;
            boolean vertical = (Math.abs(x - 64) <= 3 || Math.abs(x - 192) <= 3) && z >= -3 && z <= 19;
            return land(horizontal || detour || vertical ? "water" : "lava");
        };
        HydrologyPlanner planner = new HydrologyPlanner(15L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        List<HydrologyPoint> repaired = new HydrologyRegionalTerrainRefiner(planner).refine(
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(256, 70, 0)), "water", false, planner.sampler);

        assertFalse(repaired.isEmpty());
        SurfaceCenterline centerline = SurfaceCenterline.densify(repaired);
        for (int station = 0; station < centerline.size(); station++) {
            assertEquals(List.of("water"), terrain.sample(centerline.x()[station], centerline.z()[station]).preferredProfileKeys());
        }
    }

    @Test
    public void oceanDetourPreservesTheShippingTwentyDegreeTurnLimit() {
        HydrologyTerrainSampler terrain = (x, z) -> x >= 96 && x <= 160 && Math.abs(z) < 32
                ? HydrologyTerrainSample.ocean(60, "ocean") : HydrologyTerrainSample.openLand(70, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(15L, withMeanders(
                new HydrologyPlannerSettings.Meanders(64, 12, 0.34D, 0.42D, 0.48D, 1, 20D)), terrain);
        HydrologyRegionalRoute.Refinement repaired = new HydrologyRegionalRoute(planner).select(
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(256, 70, 0)), "default", false, planner.sampler, candidate -> new HydrologyRegionalRoute.Attempt(candidate, HydrologyRegionalNetwork.EMPTY, null)).refinement();

        assertNull(repaired.toString(), repaired.rejection());
        assertTrue(repaired.points().stream().anyMatch(point -> Math.abs(point.z()) >= 32));
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
        assertEquals(guide, new HydrologyRegionalTerrainRefiner(planner).refine(guide, "water", false, planner.sampler));
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
