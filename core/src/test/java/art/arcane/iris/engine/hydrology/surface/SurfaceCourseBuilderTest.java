package art.arcane.iris.engine.hydrology.surface;

import art.arcane.iris.engine.hydrology.HydraulicSegment;
import art.arcane.iris.engine.hydrology.HydrologyCandidateRejection;
import art.arcane.iris.engine.hydrology.HydrologyFeatureType;
import art.arcane.iris.engine.hydrology.HydrologyGeometrySampler;
import art.arcane.iris.engine.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.engine.hydrology.HydrologyPoint;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSample;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSampler;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SurfaceCourseBuilderTest {
    private static final int SEA_LEVEL = 60;
    private static final long COURSE_ID = 4242L;
    private static final HydrologyGeometrySampler CONSTANT_GEOMETRY = request -> switch (request.field()) {
        case SURFACE_WIDTH -> 6;
        case SURFACE_DEPTH -> 3;
        default -> request.minimum();
    };

    @Test
    public void slopedCourseToTheSeaProducesNonRisingLabelledSegmentsEndingAtSeaLevel() {
        HydrologyTerrainSampler sampler = (int x, int z) -> x >= 260
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(110 - x / 6, 0D, "land");
        SurfaceCourseResult result = builder(sampler).build(
                7L,
                COURSE_ID,
                "water",
                List.of(new HydrologyPoint(0, 0, 0), new HydrologyPoint(140, 0, 0), new HydrologyPoint(280, 0, 0)),
                SurfaceTerminal.OCEAN_MOUTH,
                SEA_LEVEL,
                64
        );

        assertNull(result.rejection());
        List<HydraulicSegment> segments = result.segments();
        assertTrue(segments.size() > 3);
        assertEquals(0, segments.getFirst().start().x());
        assertEquals(SEA_LEVEL, result.lastHead());
        assertEquals(SEA_LEVEL, segments.getLast().downstreamHeadY());
        int previous = Integer.MAX_VALUE;
        for (HydraulicSegment segment : segments) {
            assertEquals(COURSE_ID, segment.courseId());
            assertTrue(segment.upstreamHeadY() <= previous);
            assertTrue(segment.downstreamHeadY() <= segment.upstreamHeadY());
            assertTrue(segment.type().isSurface());
            previous = segment.downstreamHeadY();
        }
        for (int index = 1; index < segments.size(); index++) {
            HydraulicSegment before = segments.get(index - 1);
            HydraulicSegment after = segments.get(index);
            assertEquals(before.end().x(), after.start().x());
            assertEquals(before.end().z(), after.start().z());
            assertEquals(before.downstreamHeadY(), after.upstreamHeadY());
        }
        assertTrue(result.pathEnd().x() >= 250);
        assertTrue(result.lastWidth() >= 6);
    }

    @Test
    public void flatCourseIsOneSurfacePool() {
        HydrologyTerrainSampler sampler = (int x, int z) -> HydrologyTerrainSample.openLand(80, 0D, "land");
        SurfaceCourseResult result = builder(sampler).build(
                7L,
                COURSE_ID,
                "water",
                List.of(new HydrologyPoint(0, 0, 0), new HydrologyPoint(200, 0, 0)),
                SurfaceTerminal.SINKHOLE,
                40,
                64
        );

        assertNull(result.rejection());
        assertEquals(1, result.segments().size());
        assertEquals(HydrologyFeatureType.SURFACE_POOL, result.segments().getFirst().type());
        assertEquals(80, result.lastHead());
        assertEquals(201, result.segments().getFirst().centerline().size());
    }

    @Test
    public void cliffProducesAWaterfallSegment() {
        HydrologyTerrainSampler sampler = (int x, int z) -> HydrologyTerrainSample.openLand(x < 100 ? 100 : 90, 0D, "land");
        SurfaceCourseResult result = builder(sampler).build(
                7L,
                COURSE_ID,
                "water",
                List.of(new HydrologyPoint(0, 0, 0), new HydrologyPoint(200, 0, 0)),
                SurfaceTerminal.SINKHOLE,
                40,
                64
        );

        assertNull(result.rejection());
        assertEquals(3, result.segments().size());
        assertEquals(HydrologyFeatureType.SURFACE_POOL, result.segments().get(0).type());
        assertEquals(HydrologyFeatureType.WATERFALL, result.segments().get(1).type());
        assertEquals(10, result.segments().get(1).drop());
        assertEquals(HydrologyFeatureType.SURFACE_POOL, result.segments().get(2).type());
    }

    @Test
    public void gentleSlopeProducesRifflesAndSteepSlopeProducesCascades() {
        HydrologyTerrainSampler gentle = (int x, int z) -> HydrologyTerrainSample.openLand(120 - x / 10, 0D, "land");
        SurfaceCourseResult gentleResult = builder(gentle).build(7L, COURSE_ID, "water",
                List.of(new HydrologyPoint(0, 0, 0), new HydrologyPoint(200, 0, 0)), SurfaceTerminal.SINKHOLE, 40, 64);
        HydrologyTerrainSampler steep = (int x, int z) -> HydrologyTerrainSample.openLand(300 - x, 0D, "land");
        SurfaceCourseResult steepResult = builder(steep).build(7L, COURSE_ID, "water",
                List.of(new HydrologyPoint(0, 0, 0), new HydrologyPoint(200, 0, 0)), SurfaceTerminal.SINKHOLE, 40, 64);

        assertNull(gentleResult.rejection());
        assertNull(steepResult.rejection());
        assertTrue(gentleResult.segments().stream().anyMatch(segment -> segment.type() == HydrologyFeatureType.RIFFLE));
        assertTrue(gentleResult.segments().stream().noneMatch(segment -> segment.type() == HydrologyFeatureType.CASCADE));
        assertTrue(steepResult.segments().stream().anyMatch(segment -> segment.type() == HydrologyFeatureType.CASCADE));
    }

    @Test
    public void submergedCourseIsRejected() {
        HydrologyTerrainSampler sampler = (int x, int z) -> HydrologyTerrainSample.openLand(50, 0D, "land");
        SurfaceCourseResult result = builder(sampler).build(
                7L,
                COURSE_ID,
                "water",
                List.of(new HydrologyPoint(0, 0, 0), new HydrologyPoint(200, 0, 0)),
                SurfaceTerminal.OCEAN_MOUTH,
                SEA_LEVEL,
                64
        );

        assertEquals(HydrologyCandidateRejection.COURSE_TOO_SHORT, result.rejection());
        assertTrue(result.segments().isEmpty());
    }

    @Test
    public void aCourseWithAnInletEndsAtSeaLevelWithoutACoastalDropStation() {
        HydrologyTerrainSampler sampler = (int x, int z) -> x >= 240
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(100 - x / 6, 0D, "land");
        List<HydrologyPoint> path = List.of(new HydrologyPoint(0, 0, 0), new HydrologyPoint(241, 0, 0));
        HydrologyPlannerSettings.Surface defaults = HydrologyPlannerSettings.defaults().surface();
        HydrologyPlannerSettings.Inlet inlet = defaults.banks().inlet();
        SurfaceCourseResult result = builder(sampler).build(7L, COURSE_ID, "water", path, SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL, 64);
        SurfaceCourseResult plain = builder(defaults.banks().withInlet(HydrologyPlannerSettings.Inlet.none()), sampler)
                .build(7L, COURSE_ID, "water", path, SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL, 64);

        assertNull(result.rejection());
        assertEquals(SEA_LEVEL, result.lastHead());
        assertEquals(239, result.pathEnd().x());
        HydraulicSegment last = result.segments().getLast();
        assertEquals(HydrologyFeatureType.SURFACE_POOL, last.type());
        assertEquals(SEA_LEVEL, last.upstreamHeadY());
        assertEquals(SEA_LEVEL, last.downstreamHeadY());
        assertEquals(inlet.length(), last.centerline().size());
        assertEquals(240 - inlet.length(), last.start().x());
        assertNull(plain.rejection());
        assertEquals(SEA_LEVEL, plain.lastHead());
        assertEquals(240, plain.pathEnd().x());
        assertTrue(plain.segments().getLast().upstreamHeadY() > SEA_LEVEL);
    }

    private static SurfaceCourseBuilder builder(HydrologyTerrainSampler sampler) {
        return new SurfaceCourseBuilder(HydrologyPlannerSettings.defaults().surface(), sampler, CONSTANT_GEOMETRY, SEA_LEVEL);
    }

    private static SurfaceCourseBuilder builder(HydrologyPlannerSettings.Banks banks, HydrologyTerrainSampler sampler) {
        HydrologyPlannerSettings.Surface defaults = HydrologyPlannerSettings.defaults().surface();
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(
                defaults.enabled(), defaults.sources(), defaults.minimumWidth(), defaults.maximumWidth(),
                defaults.minimumDepth(), defaults.maximumDepth(), defaults.maximumIncision(), defaults.shoreWidth(), banks);
        return new SurfaceCourseBuilder(surface, sampler, CONSTANT_GEOMETRY, SEA_LEVEL);
    }
}
