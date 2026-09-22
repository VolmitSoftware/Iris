package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydraulicSegment;
import art.arcane.iris.generation.hydrology.HydrologyCandidateRejection;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.HydrologyGeometrySampler;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyPoint;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSampler;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
                List.of(new HydrologyPoint(0, 110, 0), new HydrologyPoint(140, 87, 0),
                        new HydrologyPoint(260, 67, 0), new HydrologyPoint(280, SEA_LEVEL, 0)),
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
                List.of(new HydrologyPoint(0, 80, 0), new HydrologyPoint(200, 80, 0)),
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
    public void coastalDropKeepsTheUpstreamGradedReachExposed() {
        HydrologyPlannerSettings.Surface defaults = HydrologyPlannerSettings.defaults().surface();
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(
                defaults.enabled(), defaults.sources(), defaults.minimumWidth(), defaults.maximumWidth(),
                defaults.minimumDepth(), defaults.maximumDepth(), defaults.maximumIncision(), defaults.shoreWidth(),
                defaults.banks().withInlet(HydrologyPlannerSettings.Inlet.none()));
        HydrologyTerrainSampler sampler = (x, z) -> x >= 40
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(100 - x, 0D, "land");
        SurfaceCourseBuilder builder = new SurfaceCourseBuilder(surface, sampler, CONSTANT_GEOMETRY, SEA_LEVEL);
        List<HydrologyPoint> path = List.of(new HydrologyPoint(0, 100, 0), new HydrologyPoint(40, SEA_LEVEL, 0));
        SurfaceCourseResult result = builder.build(7L, COURSE_ID, "water", path,
                SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL, 16);

        assertNull(result.rejection());
        assertEquals(2, result.segments().size());
        HydraulicSegment approach = result.segments().getFirst();
        HydraulicSegment drop = result.segments().getLast();
        assertEquals(HydrologyFeatureType.CASCADE, approach.type());
        assertFalse(approach.fallingFluid());
        assertEquals(100, approach.upstreamHeadY());
        assertEquals(61, approach.downstreamHeadY());
        assertEquals(new HydrologyPoint(20, 80, 0), approach.centerline().get(20));
        assertEquals(HydrologyFeatureType.RIFFLE, drop.type());
        assertTrue(drop.fallingFluid());
        assertTrue(drop.receivingPool());
        assertEquals(List.of(new HydrologyPoint(39, 61, 0), new HydrologyPoint(40, 60, 0)), drop.centerline());
        assertEquals(approach.channelProfile().widthAt(39), drop.channelProfile().widthAt(0), 0D);
        assertEquals(approach.channelProfile().depthAt(39), drop.channelProfile().depthAt(0), 0D);
        assertTrue(approach.id() != drop.id());
        assertEquals(result, builder.build(7L, COURSE_ID, "water", path,
                SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL, 16));
    }

    @Test
    public void cliffProducesAWaterfallSegment() {
        HydrologyTerrainSampler sampler = (int x, int z) -> HydrologyTerrainSample.openLand(x < 100 ? 100 : 90, 0D, "land");
        SurfaceCourseResult result = builder(sampler).build(
                7L,
                COURSE_ID,
                "water",
                List.of(new HydrologyPoint(0, 100, 0), new HydrologyPoint(99, 100, 0),
                        new HydrologyPoint(100, 90, 0), new HydrologyPoint(200, 90, 0)),
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
    public void cliffIntoTheOceanUsesFallingFluidInsteadOfAnInlandSeaLevelChannel() {
        HydrologyTerrainSampler sampler = (x, z) -> x >= 100
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(100, 0D, "land");
        SurfaceCourseResult result = builder(sampler).build(7L, COURSE_ID, "water",
                List.of(new HydrologyPoint(0, 97, 0), new HydrologyPoint(99, 97, 0),
                        new HydrologyPoint(100, SEA_LEVEL, 0)), SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL, 64);

        assertNull(result.rejection());
        HydraulicSegment fall = result.segments().getLast();
        assertEquals(HydrologyFeatureType.WATERFALL, fall.type());
        assertTrue(fall.fallingFluid());
        assertTrue(fall.receivingPool());
        assertEquals(SEA_LEVEL, fall.downstreamHeadY());
        assertEquals(100, fall.end().x());
    }

    @Test
    public void gentleSlopeProducesRifflesAndSteepSlopeProducesCascades() {
        HydrologyTerrainSampler gentle = (int x, int z) -> HydrologyTerrainSample.openLand(120 - x / 10, 0D, "land");
        SurfaceCourseResult gentleResult = builder(gentle).build(7L, COURSE_ID, "water",
                List.of(new HydrologyPoint(0, 120, 0), new HydrologyPoint(200, 100, 0)), SurfaceTerminal.SINKHOLE, 40, 64);
        HydrologyTerrainSampler steep = (int x, int z) -> HydrologyTerrainSample.openLand(300 - x, 0D, "land");
        SurfaceCourseResult steepResult = builder(steep).build(7L, COURSE_ID, "water",
                List.of(new HydrologyPoint(0, 300, 0), new HydrologyPoint(200, 100, 0)), SurfaceTerminal.SINKHOLE, 40, 64);

        assertNull(gentleResult.rejection());
        assertNull("detail=" + steepResult.rejectionDetail(), steepResult.rejection());
        assertTrue(gentleResult.segments().stream().anyMatch(segment -> segment.type() == HydrologyFeatureType.RIFFLE));
        assertTrue(gentleResult.segments().stream().noneMatch(segment -> segment.type() == HydrologyFeatureType.CASCADE));
        assertTrue(steepResult.segments().stream().anyMatch(segment -> segment.type() == HydrologyFeatureType.CASCADE));
        for (HydraulicSegment segment : steepResult.segments()) {
            for (int station = 0; station < segment.centerline().size(); station++) {
                HydrologyPoint point = segment.centerline().get(station);
                int cut = steep.sample(point.x(), point.z()).naturalHeight() - point.y()
                        + (int) StrictMath.round(segment.channelProfile().depthAt(station));
                assertTrue("cut=" + cut + " at " + point.x(),
                        cut <= HydrologyPlannerSettings.defaults().surface().maximumIncision());
            }
        }
    }

    @Test
    public void submergedCourseIsRejected() {
        HydrologyTerrainSampler sampler = (int x, int z) -> HydrologyTerrainSample.openLand(50, 0D, "land");
        SurfaceCourseResult result = builder(sampler).build(
                7L,
                COURSE_ID,
                "water",
                List.of(new HydrologyPoint(0, 50, 0), new HydrologyPoint(200, 50, 0)),
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
        List<HydrologyPoint> path = List.of(new HydrologyPoint(0, 100, 0), new HydrologyPoint(239, 61, 0),
                new HydrologyPoint(240, SEA_LEVEL, 0), new HydrologyPoint(241, SEA_LEVEL, 0));
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
