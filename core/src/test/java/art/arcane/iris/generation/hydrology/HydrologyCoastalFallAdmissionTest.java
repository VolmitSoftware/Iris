package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HydrologyCoastalFallAdmissionTest {
    private static final HydrologyPlannerSettings SETTINGS = HydrologyPlannerSettings.defaults();
    private static final HydrologyTerrainSampler TERRAIN = (x, z) -> x >= 80
            ? HydrologyTerrainSample.ocean(54, "ocean") : HydrologyTerrainSample.openLand(95, 0D, "land");

    @Test
    public void aConnectedMouthAdmitsTheCoastalFallAtTheNaturalSeaSurface() {
        RiverCourse course = course(HydrologyFeatureType.MOUTH, 80);
        HydrologyFootprintCompiler compiler = new HydrologyFootprintCompiler(SETTINGS, TERRAIN);
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        assertTrue(compiler.surfaceFootprint(course).toString(), compiler.surfaceFootprint(course).accepted());
        HydrologyCaveCourseFilter.Result result = filter().filter(List.of(), List.of(), List.of(), List.of(course),
                compiler.compileValidation(List.of(course)), diagnostics);

        assertEquals(diagnostics.toString(), List.of(course), result.courses());
        assertEquals(1, result.cavePlans().size());
        assertTrue(result.cavePlans().getFirst().accepted());
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    public void anInlandTerminalOrDisconnectedMouthCannotOpenTheFallAtSeaLevel() {
        for (RiverCourse course : List.of(course(HydrologyFeatureType.SURFACE_POOL, 80),
                course(HydrologyFeatureType.MOUTH, 81))) {
            HydrologyFootprintCompiler compiler = new HydrologyFootprintCompiler(SETTINGS, TERRAIN);
            ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
            HydrologyCaveCourseFilter.Result result = filter().filter(List.of(), List.of(), List.of(), List.of(course),
                    compiler.compileValidation(List.of(course)), diagnostics);

            assertTrue(result.courses().isEmpty());
            assertFalse(diagnostics.isEmpty());
            assertEquals(HydrologyCandidateRejection.CAVE_CONTAINMENT, diagnostics.getFirst().rejection());
        }
    }

    private static HydrologyCaveCourseFilter filter() {
        return new HydrologyCaveCourseFilter(new HydrologyTerrainCaveVoxelView(TERRAIN, SETTINGS.seaLevel(), -64, 256),
                new HydrologyCaveCourseFilter.Options(false, 8192, 8192));
    }

    private static RiverCourse course(HydrologyFeatureType terminal, int receiverX) {
        int sea = SETTINGS.seaLevel();
        HydraulicSegment approach = new HydraulicSegment(1L, 4L, HydrologyFeatureType.SURFACE_POOL,
                93, 93, 4, 2, false, false, List.of(new HydrologyPoint(0, 93, 0), new HydrologyPoint(79, 93, 0)),
                HydraulicChannelProfile.uniform(4, 2));
        HydraulicSegment fall = new HydraulicSegment(2L, 4L, HydrologyFeatureType.WATERFALL,
                93, sea, 4, 2, true, true, List.of(new HydrologyPoint(79, 93, 0), new HydrologyPoint(80, sea, 0)),
                HydraulicChannelProfile.uniform(4, 2));
        HydraulicSegment receiving = new HydraulicSegment(3L, 4L, terminal, sea, sea, 4, 2, false, false,
                List.of(new HydrologyPoint(receiverX, sea, 0)), HydraulicChannelProfile.uniform(4, 2));
        return new RiverCourse(4L, RiverCourseType.SURFACE, OptionalLong.of(5L), OptionalLong.of(6L),
                "default", 1, List.of(), List.of(approach, fall, receiving));
    }
}
