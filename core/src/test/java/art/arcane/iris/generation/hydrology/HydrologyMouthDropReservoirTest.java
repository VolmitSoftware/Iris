package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceBounds;
import org.junit.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HydrologyMouthDropReservoirTest {
    private static final HydrologyPlannerSettings SETTINGS = HydrologyPlannerSettings.defaults();
    private static final HydrologyTerrainSample FLOODED = HydrologyTerrainSample.openLand(54, 0D, "coast");

    @Test
    public void aMouthReceivesTheFallThroughProvenConnectedWaterWithoutOwningTheFloodedCoast() {
        HydrologyTerrainSampler source = coast();
        RiverCourse course = course(SETTINGS.seaLevel(), 1);
        HydrologyTerrainSampler scoped = HydrologyOceanReceiver.forCourse(SETTINGS, source, course);
        HydrologyGeometrySampler geometry = HydrologyGeometrySampler.deterministic(source);
        HydrologySurfaceDropRaster full = HydrologySurfaceDropRaster.compile(SETTINGS, scoped, geometry, course);

        assertTrue(full.connects(0, 0, 92));
        assertFalse(full.connects(1, 0, SETTINGS.seaLevel()));
        assertTrue(full.containsRequiredFluid(course, null));
        assertSame(FLOODED, scoped.sample(1, 0));
        assertFalse(scoped.sample(1, 0).ocean());
        for (HydrologyColumnSample column : full.columns()) {
            assertTrue(column.naturalHeight() >= SETTINGS.seaLevel());
        }
        SurfaceBounds receivingWindow = new SurfaceBounds(1, -1, 2, 1);
        HydrologySurfaceDropRaster bounded = HydrologySurfaceDropRaster.compile(
                SETTINGS, scoped, geometry, course, receivingWindow);
        assertTrue(bounded.columns().isEmpty());
        assertTrue(bounded.containsRequiredFluid(course, receivingWindow));
    }

    @Test
    public void aRaisedMouthOrBrokenConnectionCannotReplaceTheRequiredReceiverInterval() {
        HydrologyTerrainSampler source = coast();
        for (RiverCourse course : List.of(course(SETTINGS.seaLevel() + 1, 1), course(SETTINGS.seaLevel(), 2))) {
            HydrologyTerrainSampler scoped = HydrologyOceanReceiver.forCourse(SETTINGS, source, course);
            HydrologySurfaceDropRaster raster = HydrologySurfaceDropRaster.compile(
                    SETTINGS, scoped, HydrologyGeometrySampler.deterministic(source), course);
            assertFalse(raster.containsRequiredFluid(course, null));
        }
        RiverCourse course = course(SETTINGS.seaLevel(), 1);
        for (HydrologyTerrainSampler terrain : List.<HydrologyTerrainSampler>of(
                (x, z) -> x == 4 ? HydrologyTerrainSample.openLand(70, 0D, "sill") : source.sample(x, z),
                (x, z) -> x >= 8 ? FLOODED : source.sample(x, z))) {
            HydrologyTerrainSampler scoped = HydrologyOceanReceiver.forCourse(SETTINGS, terrain, course);
            HydrologySurfaceDropRaster raster = HydrologySurfaceDropRaster.compile(
                    SETTINGS, scoped, HydrologyGeometrySampler.deterministic(terrain), course);
            assertFalse(raster.containsRequiredFluid(course, null));
        }
        assertFalse(HydrologySurfaceDropRaster.compile(SETTINGS, source,
                HydrologyGeometrySampler.deterministic(source), course).containsRequiredFluid(course, null));
    }

    private static HydrologyTerrainSampler coast() {
        return (x, z) -> x <= 0 ? HydrologyTerrainSample.openLand(92, 0D, "land")
                : x >= 8 ? HydrologyTerrainSample.ocean(54, "ocean") : FLOODED;
    }

    private static RiverCourse course(int mouthHead, int mouthStart) {
        HydraulicSegment falling = new HydraulicSegment(2L, 1L, HydrologyFeatureType.WATERFALL,
                92, SETTINGS.seaLevel(), 4, 3, true, true,
                List.of(new HydrologyPoint(0, 92, 0), new HydrologyPoint(1, SETTINGS.seaLevel(), 0)),
                HydraulicChannelProfile.uniform(4, 3));
        HydraulicSegment mouth = new HydraulicSegment(5L, 1L, HydrologyFeatureType.MOUTH,
                mouthHead, mouthHead, 4, 3, false, false,
                List.of(new HydrologyPoint(mouthStart, mouthHead, 0), new HydrologyPoint(8, mouthHead, 0)),
                HydraulicChannelProfile.uniform(4, 3));
        return new RiverCourse(1L, RiverCourseType.SURFACE, OptionalLong.of(3L), OptionalLong.of(4L),
                "default", 1, List.of(), List.of(falling, mouth));
    }
}
