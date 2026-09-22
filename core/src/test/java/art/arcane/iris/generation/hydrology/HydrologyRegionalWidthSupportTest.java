package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceBankSupport;
import art.arcane.iris.generation.hydrology.surface.SurfaceCourseBuilder;
import art.arcane.iris.generation.hydrology.surface.SurfaceCourseResult;
import art.arcane.iris.generation.hydrology.surface.SurfaceFootprint;
import art.arcane.iris.generation.hydrology.surface.SurfaceFootprintCompiler;
import art.arcane.iris.generation.hydrology.surface.SurfaceLayerColumn;
import art.arcane.iris.generation.hydrology.surface.SurfaceTerminal;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalWidthSupportTest {
    private static final HydrologyPlannerSettings SETTINGS = HydrologyRegionalPlannerTest.settings(false, 1);
    private static final List<HydrologyPoint> PATH = List.of(new HydrologyPoint(0, 80, 0), new HydrologyPoint(512, 80, 0));

    @Test
    public void aWiderChannelContainsTheCenterDepressionWithoutRaisingLand() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(
                x >= 128 && x <= 136 && Math.abs(z) <= 1 ? 65 : x >= 256 && x <= 288 ? 86 : 80, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(71L, SETTINGS, terrain);
        Method validate = HydrologyRegionalRoute.class.getDeclaredMethod("validateHydraulics",
                HydrologyRegionalRoute.Refinement.class, String.class, boolean.class, HydrologyTerrainSampler.class);
        validate.setAccessible(true);
        HydrologyRegionalRoute.Refinement refined = (HydrologyRegionalRoute.Refinement) validate.invoke(new HydrologyRegionalRoute(planner),
                new HydrologyRegionalRoute.Refinement(PATH, null, null, 0, null), "default", false, terrain);
        assertNull(refined.toString(), refined.rejection());

        HydrologyGeometrySampler geometry = geometry(8);
        SurfaceCourseResult built = build(terrain, geometry);
        assertTrue(built.accepted());
        RiverCourse course = new RiverCourse(91L, RiverCourseType.SURFACE, OptionalLong.of(1L), OptionalLong.of(2L),
                "default", 1, List.of(), built.segments());
        SurfaceFootprint footprint = new SurfaceFootprintCompiler(SETTINGS, terrain, geometry).compile(course);
        assertTrue(footprint.accepted());
        assertEquals(0, footprint.uncontainedWetCells());
        assertTrue(course.hydraulicallyNonRising());
        SurfaceLayerColumn center = null;
        for (SurfaceLayerColumn column : footprint.columns()) {
            assertTrue(column.layer().bedY() - column.terrain().naturalHeight() <= SETTINGS.surface().maximumIncision());
            assertTrue(column.terrain().naturalHeight() - column.layer().bedY() <= SETTINGS.surface().maximumIncision());
            if (column.x() == 132 && column.z() == 0) {
                center = column;
            }
        }
        assertTrue(center != null);
        assertEquals(65, center.layer().bedY());
        assertEquals(80, center.layer().fluidHeadY());

        SurfaceCourseResult narrow = build(terrain, geometry(4));
        assertTrue(narrow.accepted());
        assertTrue(narrow.segments().stream().allMatch(segment -> segment.upstreamHeadY() >= segment.downstreamHeadY()));
    }

    private static HydrologyGeometrySampler geometry(int width) {
        return request -> switch (request.field()) {
            case SURFACE_WIDTH -> width;
            case SURFACE_DEPTH -> 2;
            default -> request.minimum();
        };
    }

    private static SurfaceCourseResult build(HydrologyTerrainSampler terrain, HydrologyGeometrySampler geometry) {
        return new SurfaceCourseBuilder(SETTINGS.surface(), terrain, geometry, SETTINGS.seaLevel())
                .build(71L, 91L, "default", PATH, SurfaceTerminal.SINKHOLE, 40, 256);
    }

}
