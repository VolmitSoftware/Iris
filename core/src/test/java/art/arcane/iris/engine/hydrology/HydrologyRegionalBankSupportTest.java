package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.surface.SurfaceCourseBuilder;
import art.arcane.iris.engine.hydrology.surface.SurfaceCourseResult;
import art.arcane.iris.engine.hydrology.surface.SurfaceFootprint;
import art.arcane.iris.engine.hydrology.surface.SurfaceFootprintCompiler;
import art.arcane.iris.engine.hydrology.surface.SurfaceLayerColumn;
import art.arcane.iris.engine.hydrology.surface.SurfaceTerminal;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        List<HydrologyPoint> refined = new HydrologyRegionalTerrainRefiner(planner).refine(path, "default", false, terrain);

        assertEquals(path, refined);
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
            assertTrue(column.layer().bedY() <= column.terrain().naturalHeight());
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

        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED,
                validate(planner, path(), terrain).rejection());
        List<HydrologyPoint> refined = new HydrologyRegionalTerrainRefiner(planner).refine(path(), "default", false, terrain);
        if (!refined.isEmpty()) {
            assertFalse(build(refined, terrain).accepted());
        }
        SurfaceCourseResult built = build(path(), terrain);
        assertFalse(built.accepted());
        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, built.rejection());
        assertEquals(17, built.rejectionDetail());
    }

    @Test
    public void levelCentersNeedNoBankSamplesAndSupportedDipsCannotRaiseIncomingHead() {
        AtomicInteger samples = new AtomicInteger();
        HydrologyTerrainSampler terrain = (x, z) -> {
            assertTrue(Math.abs(x - 132) <= 5 && Math.abs(z) <= 5);
            samples.incrementAndGet();
            return depression(false).sample(x, z);
        };
        HydrologyRegionalHydraulics hydraulics = new HydrologyRegionalHydraulics(SETTINGS);

        assertEquals(79, hydraulics.supportedHead(new HydrologyRegionalHydraulics.HeadStation(
                132, 0, 1D, 0D, HydrologyTerrainSample.openLand(80, 0D, "land"), 79), terrain));
        assertEquals(0, samples.get());
        assertEquals(78, hydraulics.supportedHead(station(78), terrain));
        assertTrue(samples.get() > 0 && samples.get() < 128);
    }

    @Test
    public void unavailableCrossSectionOrPerimeterCannotCertifyAContainedDip() {
        HydrologyRegionalHydraulics hydraulics = new HydrologyRegionalHydraulics(SETTINGS);
        HydrologyTerrainSampler crossMissing = (x, z) -> x == 132 && z == 2 ? null : depression(false).sample(x, z);
        HydrologyTerrainSampler perimeterMissing = (x, z) -> x == 133 ? null : depression(false).sample(x, z);

        assertEquals(65, hydraulics.supportedHead(station(80), crossMissing));
        assertEquals(65, hydraulics.supportedHead(station(80), perimeterMissing));
    }

    @Test
    public void bankSupportCannotSamplePastTheExistingSearchBudget() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HydrologyTerrainSample ground = HydrologyTerrainSample.openLand(80, 0D, "land");
        HydrologyTerrainSampler terrain = (x, z) -> {
            requests.incrementAndGet();
            return ground;
        };
        HydrologyRegionalTerrainRefiner refiner = new HydrologyRegionalTerrainRefiner(new HydrologyPlanner(71L, SETTINGS, terrain));
        Map<Long, HydrologyTerrainSample> sampled = new HashMap<>();
        for (int index = 0; index < 65536; index++) {
            sampled.put(RiverFootprint.pack(index, 100000), ground);
        }
        Method sample = HydrologyRegionalTerrainRefiner.class.getDeclaredMethod("sample", int.class, int.class, Map.class);
        sample.setAccessible(true);
        HydrologyTerrainSampler limited = (x, z) -> {
            try {
                return (HydrologyTerrainSample) sample.invoke(refiner, x, z, sampled);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        };

        assertEquals(65, new HydrologyRegionalHydraulics(SETTINGS).supportedHead(station(80), limited));
        assertEquals(0, requests.get());
        assertEquals(65536, sampled.size());
    }

    private static HydrologyRegionalHydraulics.HeadStation station(int incomingHead) {
        return new HydrologyRegionalHydraulics.HeadStation(132, 0, 1D, 0D,
                HydrologyTerrainSample.openLand(65, 0D, "land"), incomingHead);
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
