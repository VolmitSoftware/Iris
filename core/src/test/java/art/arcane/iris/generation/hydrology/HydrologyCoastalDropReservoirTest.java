package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceBounds;
import org.junit.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HydrologyCoastalDropReservoirTest {
    private static final HydrologyPlannerSettings SETTINGS = HydrologyPlannerSettings.defaults();

    @Test
    public void aDeclaredCoastalGrottoReceivesTheFallThroughUnownedNaturalOceanWater() {
        HydrologyTerrainSampler terrain = terrain(54);
        RiverCourse course = course(SETTINGS.seaLevel(), 1, true);
        HydrologyGeometrySampler geometry = HydrologyGeometrySampler.deterministic(terrain);
        HydrologySurfaceDropRaster full = HydrologySurfaceDropRaster.compile(SETTINGS, terrain, geometry, course);

        assertTrue(full.connects(0, 0, 92));
        assertTrue(full.connects(0, 0, SETTINGS.seaLevel()));
        assertFalse(full.connects(1, 0, SETTINGS.seaLevel()));
        assertTrue(full.containsRequiredFluid(course, null));
        for (HydrologyColumnSample column : full.columns()) {
            assertFalse(column.ocean());
        }
        SurfaceBounds receivingWindow = new SurfaceBounds(1, -2, 2, 2);
        HydrologySurfaceDropRaster bounded = HydrologySurfaceDropRaster.compile(
                SETTINGS, terrain, geometry, course, receivingWindow);
        assertTrue(bounded.columns().isEmpty());
        assertTrue(bounded.containsRequiredFluid(course, receivingWindow));
    }

    @Test
    public void anOceanBiomeOrUnrelatedGrottoCannotReplaceTheRequiredFluidInterval() {
        HydrologyTerrainSampler dryOceanBiome = terrain(SETTINGS.seaLevel());
        RiverCourse exact = course(SETTINGS.seaLevel(), 1, true);
        assertFalse(HydrologySurfaceDropRaster.compile(SETTINGS, dryOceanBiome,
                HydrologyGeometrySampler.deterministic(dryOceanBiome), exact).containsRequiredFluid(exact, null));

        HydrologyTerrainSampler ocean = terrain(54);
        for (RiverCourse invalid : List.of(course(SETTINGS.seaLevel(), 1, false),
                course(SETTINGS.seaLevel(), 2, true), course(SETTINGS.seaLevel() + 1, 1, true))) {
            HydrologySurfaceDropRaster raster = HydrologySurfaceDropRaster.compile(
                    SETTINGS, ocean, HydrologyGeometrySampler.deterministic(ocean), invalid);
            assertFalse(raster.containsRequiredFluid(invalid, null));
        }
    }

    private static HydrologyTerrainSampler terrain(int oceanFloor) {
        return (x, z) -> x >= 1 ? HydrologyTerrainSample.ocean(oceanFloor, "ocean")
                : HydrologyTerrainSample.openLand(92, 0D, "land");
    }

    private static RiverCourse course(int head, int grottoEnd, boolean grotto) {
        HydraulicSegment falling = new HydraulicSegment(2L, 1L, HydrologyFeatureType.WATERFALL,
                92, head, 4, 3, true, true,
                List.of(new HydrologyPoint(0, 92, 0), new HydrologyPoint(1, head, 0)),
                HydraulicChannelProfile.uniform(4, 3));
        HydraulicSegment chamber = new HydraulicSegment(5L, 1L, HydrologyFeatureType.COASTAL_GROTTO,
                head, head, 4, 3, false, false,
                List.of(new HydrologyPoint(0, head, 0), new HydrologyPoint(grottoEnd, head, 0)),
                HydraulicChannelProfile.uniform(4, 3));
        return new RiverCourse(1L, RiverCourseType.SURFACE, OptionalLong.of(3L), OptionalLong.of(4L),
                "default", 1, List.of(), grotto ? List.of(falling, chamber) : List.of(falling));
    }
}
