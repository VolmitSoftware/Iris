package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalMorphologyTest {
    @Test
    public void flatOpenTerrainRetainsFullFreedomWithoutSamplingDuringQueries() {
        AtomicInteger calls = new AtomicInteger();
        HydrologyRegionalMorphology morphology = HydrologyRegionalMorphology.sample(guide(4096, 256), (x, z) -> {
            calls.incrementAndGet();
            return HydrologyTerrainSample.openLand(76, 0D, "land");
        }, settings(8192, 256));
        int samples = calls.get();
        for (int x = -128; x <= 4224; x++) {
            assertEquals(1D, morphology.freedomAt(x, 7), 0D);
        }
        assertEquals(samples, calls.get());
        assertEquals(85, samples);
    }

    @Test
    public void steepGradesAndNarrowValleysReduceFreedomIndependently() {
        List<HydrologyPoint> guide = guide(4096, 256);
        HydrologyPlannerSettings settings = settings(8192, 256);
        HydrologyRegionalMorphology gentle = HydrologyRegionalMorphology.sample(guide,
                (x, z) -> HydrologyTerrainSample.openLand(1200 - Math.floorDiv(x, 128), 0D, "land"), settings);
        HydrologyRegionalMorphology steep = HydrologyRegionalMorphology.sample(guide,
                (x, z) -> HydrologyTerrainSample.openLand(1200 - Math.floorDiv(x, 4), 0D, "land"), settings);
        HydrologyRegionalMorphology confined = HydrologyRegionalMorphology.sample(guide,
                (x, z) -> HydrologyTerrainSample.openLand(Math.abs(z) < 12 ? 76 : 108, 0D, "land"), settings);
        for (int x = 0; x <= 4096; x += 16) {
            assertEquals(1D, gentle.freedomAt(x, 0), 0D);
            assertEquals(0D, steep.freedomAt(x, 0), 0D);
            assertEquals(0D, confined.freedomAt(x, 0), 0D);
        }
    }

    @Test
    public void terrainTransitionsInterpolateAcrossGuideAndTileBoundaries() {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(
                x >= 1024 && Math.abs(z) >= 12 ? 108 : 76, 0D, "land");
        HydrologyPlannerSettings settings = settings(8192, 256);
        HydrologyRegionalMorphology coarse = HydrologyRegionalMorphology.sample(guide(4096, 256), terrain, settings);
        HydrologyRegionalMorphology subdivided = HydrologyRegionalMorphology.sample(guide(4096, 64), terrain, settings);
        assertEquals(1D, coarse.freedomAt(0, 0), 0D);
        assertEquals(0D, coarse.freedomAt(2048, 0), 0D);
        for (int x = 0; x < 4096; x++) {
            assertEquals(coarse.freedomAt(x, 0), subdivided.freedomAt(x, 0), 0D);
            assertTrue(StrictMath.abs(coarse.freedomAt(x + 1, 0) - coarse.freedomAt(x, 0)) < 0.01D);
        }
        assertTrue(StrictMath.abs(coarse.freedomAt(1024D - 1e-4D, 0) - coarse.freedomAt(1024D + 1e-4D, 0)) < 1e-8D);
    }

    @Test
    public void longGuidesHaveAtMost645DistinctTerrainSamples() {
        AtomicInteger calls = new AtomicInteger();
        Set<Long> locations = new HashSet<>();
        HydrologyRegionalMorphology morphology = HydrologyRegionalMorphology.sample(guide(32768, 4), (x, z) -> {
            calls.incrementAndGet();
            assertTrue(locations.add(RiverFootprint.pack(x, z)));
            assertTrue(Math.abs(z) <= 64);
            return HydrologyTerrainSample.openLand(76, 0D, "land");
        }, settings(32768, 128));
        assertEquals(645, calls.get());
        assertEquals(1D, morphology.freedomAt(16000, 0), 0D);
    }

    @Test
    public void missingOrFloodedBanksReduceFreedomWithoutExpandingSampling() {
        for (boolean missing : List.of(false, true)) {
            AtomicInteger calls = new AtomicInteger();
            HydrologyRegionalMorphology morphology = HydrologyRegionalMorphology.sample(guide(4096, 256), (x, z) -> {
                calls.incrementAndGet();
                return z == 0 ? HydrologyTerrainSample.openLand(76, 0D, "land")
                        : missing ? null : HydrologyTerrainSample.ocean(60, "ocean");
            }, settings(8192, 256));
            assertEquals(0D, morphology.freedomAt(2048, 0), 0D);
            assertTrue(calls.get() <= 85);
        }
    }

    @Test
    public void degenerateAndOverlengthGuidesDoNotSampleTerrain() {
        HydrologyTerrainSampler terrain = (x, z) -> {
            throw new AssertionError("Invalid guides must not sample terrain");
        };
        for (List<HydrologyPoint> guide : List.of(List.<HydrologyPoint>of(), List.of(new HydrologyPoint(0, 76, 0)), guide(16384, 256))) {
            assertEquals(0D, HydrologyRegionalMorphology.sample(guide, terrain, settings(8192, 256)).freedomAt(0, 0), 0D);
        }
    }

    static HydrologyPlannerSettings settings(int maximumLength, int spacing) {
        HydrologyPlannerSettings base = HydrologyRegionalPlannerTest.settings(false, 1);
        HydrologyPlannerSettings.Routing routing = base.routing();
        HydrologyPlannerSettings.Geometry geometry = base.geometry();
        HydrologyPlannerSettings.Regional regional = new HydrologyPlannerSettings.Regional(true, spacing, 2048,
                1, 1, 32768, false, 1D, 8);
        return new HydrologyPlannerSettings(base.seaLevel(), new HydrologyPlannerSettings.Routing(routing.tileSize(),
                routing.sampleSpacing(), routing.maximumRouteNodes(), maximumLength, routing.minimumSurfaceCourseLength(),
                routing.minimumUndergroundCourseLength(), routing.valleyPreference(), routing.uphillPenalty(),
                routing.slopePenalty(), routing.confluenceAttraction(), routing.lengthPreference(), routing.tributaries(), regional),
                base.surface(), base.hydraulics(), base.underground(), base.outlets(),
                new HydrologyPlannerSettings.Geometry(new HydrologyPlannerSettings.Meanders(64, 12,
                        0.34D, 0.42D, 0.48D, 1, 20D), geometry.surface(), geometry.underground(),
                        geometry.grottos(), geometry.drops()), base.deepFluids(), base.surfacePools(),
                base.widestShoreBiomeWidth(), base.seaCaves(), base.surfacePolicyBounds());
    }

    private static List<HydrologyPoint> guide(int length, int spacing) {
        ArrayList<HydrologyPoint> guide = new ArrayList<>();
        for (int x = 0; x <= length; x += spacing) {
            guide.add(new HydrologyPoint(x, 76, 0));
        }
        return guide;
    }
}
