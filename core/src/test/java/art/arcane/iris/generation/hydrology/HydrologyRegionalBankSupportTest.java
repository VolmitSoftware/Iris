package art.arcane.iris.generation.hydrology;

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

public class HydrologyRegionalBankSupportTest {
    private static final HydrologyPlannerSettings SETTINGS = HydrologyPlannerSettings.defaults();
    private static final HydrologyGeometrySampler GEOMETRY = request -> switch (request.field()) {
        case SURFACE_WIDTH -> 8;
        case SURFACE_DEPTH -> 2;
        default -> request.minimum();
    };

    @Test
    public void containedCenterDepressionKeepsTheDirectRouteAndFinalWaterWithoutRaisingLand() throws Exception {
        HydrologyTerrainSampler terrain = depression(false);
        List<HydrologyPoint> path = path();
        HydrologyPlanner planner = new HydrologyPlanner(71L, SETTINGS, terrain);

        assertNull(validate(planner, path, terrain).rejection());
        SurfaceCourseResult built = build(path, terrain);
        assertTrue(built.accepted());
        RiverCourse course = new RiverCourse(91L, RiverCourseType.SURFACE, OptionalLong.of(1L), OptionalLong.of(2L),
                "default", 1, List.of(), built.segments());
        SurfaceFootprint footprint = new SurfaceFootprintCompiler(SETTINGS, terrain, GEOMETRY).compile(course);
        assertTrue(footprint.accepted());
        assertEquals(0, footprint.uncontainedWetCells());
        assertTrue(course.hydraulicallyNonRising());
        for (SurfaceLayerColumn column : footprint.columns()) {
            assertTrue(column.layer().bedY() - column.terrain().naturalHeight() <= SETTINGS.surface().maximumIncision());
        }
        SurfaceLayerColumn center = footprint.columns().stream()
                .filter(column -> column.x() == 132 && column.z() == 0).findFirst().orElseThrow();
        assertEquals(65, center.terrain().naturalHeight());
        assertEquals(65, center.layer().bedY());
        assertEquals(80, center.layer().fluidHeadY());
    }

    @Test
    public void depressionAcrossTheBanksStillFailsTheUnchangedIncisionLimit() throws Exception {
        HydrologyTerrainSampler terrain = depression(true);
        HydrologyPlanner planner = new HydrologyPlanner(71L, SETTINGS, terrain);

        assertNull(validate(planner, path(), terrain).rejection());
        SurfaceCourseResult built = build(path(), terrain);
        if (built.accepted()) {
            RiverCourse course = new RiverCourse(91L, RiverCourseType.SURFACE, OptionalLong.of(1L), OptionalLong.of(2L),
                    "default", 1, List.of(), built.segments());
            SurfaceFootprint footprint = new SurfaceFootprintCompiler(SETTINGS, terrain, GEOMETRY).compile(course);
            assertFalse(footprint.accepted());
        } else {
            assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, built.rejection());
        }
    }

    private static HydrologyTerrainSampler depression(boolean acrossBanks) {
        return (x, z) -> HydrologyTerrainSample.openLand(
                x >= 128 && x <= 136 && (acrossBanks || z == 0) ? 65 : 80, 0D, "land");
    }

    private static List<HydrologyPoint> path() {
        return List.of(new HydrologyPoint(0, 80, 0), new HydrologyPoint(512, 80, 0));
    }

    private static SurfaceCourseResult build(List<HydrologyPoint> path, HydrologyTerrainSampler terrain) {
        return new SurfaceCourseBuilder(SETTINGS.surface(), terrain, GEOMETRY, SETTINGS.seaLevel())
                .build(71L, 91L, "default", path, SurfaceTerminal.SINKHOLE, 40, 256);
    }

    private static HydrologyRegionalRoute.Refinement validate(HydrologyPlanner planner, List<HydrologyPoint> path,
                                                               HydrologyTerrainSampler terrain) throws Exception {
        Method validate = HydrologyRegionalRoute.class.getDeclaredMethod("validateHydraulics",
                HydrologyRegionalRoute.Refinement.class, String.class, boolean.class, HydrologyTerrainSampler.class);
        validate.setAccessible(true);
        return (HydrologyRegionalRoute.Refinement) validate.invoke(new HydrologyRegionalRoute(planner),
                new HydrologyRegionalRoute.Refinement(path, null, null, 0, null), "default", false, terrain);
    }
}
