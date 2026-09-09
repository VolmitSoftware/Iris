package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.policy.SurfaceRiverPolicy;
import art.arcane.iris.engine.hydrology.surface.ChannelProfile;
import art.arcane.iris.engine.hydrology.surface.SurfaceCenterline;
import art.arcane.iris.engine.hydrology.surface.SurfaceTerminal;
import art.arcane.iris.engine.hydrology.surface.ValleyProfile;
import art.arcane.iris.engine.hydrology.surface.ValleyProfileSolver;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HydrologySurfaceGeometryPolicyTest {
    private static final SurfaceRiverPolicy LOCAL = new SurfaceRiverPolicy("region:tropical", null, null,
            null, null, null, 64, 24);

    @Test
    public void aShortCourseUsesItsSourcePolicyAfterTheAnchorMoves() {
        HydrologyPlanner planner = planner((x, z) -> land(100, SurfaceRiverPolicy.INHERIT));
        HydrologyCoursePath path = new HydrologyCoursePath(
                List.of(new HydrologyPoint(0, 100, 0), new HydrologyPoint(96, 100, 0)),
                List.of(), List.of(), outlet(), false, false, null);
        HydrologyGridNode ordinary = node(0, land(100, SurfaceRiverPolicy.INHERIT));
        HydrologyGridNode local = node(0, land(100, LOCAL));

        assertEquals(HydrologyCandidateRejection.COURSE_TOO_SHORT,
                planner.surfaceCourses.buildSurfaceCourseAtPath(8L, "default", ordinary, path).rejection());
        assertNotNull(planner.surfaceCourses.buildSurfaceCourseAtPath(8L, "default", local, path).course());
        assertEquals(planner.settings.routing().minimumUndergroundCourseLength(),
                planner.sourcePlanner.minimumCourseLength(local.terrain(), false));
    }

    @Test
    public void anUphillEdgeUsesTheDownstreamCutLimit() {
        HydrologyPlanner planner = planner((x, z) -> land(100, SurfaceRiverPolicy.INHERIT));
        HydrologySampledGrid localDownstream = grid(SurfaceRiverPolicy.INHERIT, LOCAL);
        HydrologySampledGrid ordinaryDownstream = grid(LOCAL, SurfaceRiverPolicy.INHERIT);
        List<OutletCandidate> outlets = List.of(new OutletCandidate(1, -1, outlet()));

        assertEquals(1, planner.sourcePlanner.buildRouting(localDownstream, outlets, true).parent()[0]);
        assertEquals(-1, planner.sourcePlanner.buildRouting(ordinaryDownstream, outlets, true).parent()[0]);
        assertEquals(1, planner.sourcePlanner.buildRouting(ordinaryDownstream, outlets, false).parent()[0]);
        assertTrue(planner.outletPlanner.outletReachable(localDownstream, outlets, true)[0]);
    }

    @Test
    public void geometryOverridesDoNotPreventAnchorsWithinOneBudgetArea() {
        SurfaceRiverPolicy neighbor = new SurfaceRiverPolicy("region:tropical", null, null,
                null, null, null, 128, 16);
        HydrologyPlanner planner = planner((x, z) -> land(99, neighbor));

        assertTrue(Double.isFinite(planner.routeGeometry.anchorScore(node(0, land(100, LOCAL)),
                2, 0, 2, 0, 0.3D, true)));
    }

    @Test
    public void localIncisionCanContainATwentyBlockCut() {
        ValleyProfile ordinary = valley(false);
        ValleyProfile local = valley(true);

        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, ordinary.rejection());
        assertEquals(20, ordinary.rejectionDetail());
        assertNull(local.rejection());
    }

    @Test
    public void aLocalIncisionLimitDoesNotCarryIntoOrdinaryGround() {
        HydrologyTerrainSampler sampler = (x, z) -> land(x >= 32 && x <= 64 && z != 0 ? 82 : 100,
                x < 72 ? LOCAL : SurfaceRiverPolicy.INHERIT);

        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, solve(sampler).rejection());
    }

    private ValleyProfile valley(boolean local) {
        return solve((x, z) -> land(x >= 32 && x <= 64 && z != 0 ? 82 : 100,
                local ? LOCAL : SurfaceRiverPolicy.INHERIT));
    }

    private ValleyProfile solve(HydrologyTerrainSampler sampler) {
        SurfaceCenterline line = SurfaceCenterline.densify(
                List.of(new HydrologyPoint(0, 100, 0), new HydrologyPoint(96, 100, 0)));
        double[] widths = new double[line.size()];
        double[] depths = new double[line.size()];
        double[] banks = new double[line.size()];
        Arrays.fill(widths, 2D);
        Arrays.fill(depths, 2D);
        Arrays.fill(banks, 1D);
        return new ValleyProfileSolver(HydrologyPlannerSettings.defaults().surface(), sampler, 63, 64)
                .solve(line, new ChannelProfile(widths, depths, banks), SurfaceTerminal.SINKHOLE, 40);
    }

    private HydrologySampledGrid grid(SurfaceRiverPolicy upstream, SurfaceRiverPolicy downstream) {
        return new HydrologySampledGrid(0, 0, 0, 0, 128, 2, 64,
                List.of(node(0, land(100, upstream)), node(1, land(120, downstream)),
                        node(2, HydrologyTerrainSample.ocean(50, "ocean")),
                        node(3, HydrologyTerrainSample.ocean(50, "ocean"))));
    }

    private HydrologyGridNode node(int index, HydrologyTerrainSample terrain) {
        return new HydrologyGridNode(index, index % 2, index / 2, index % 2 * 64, index / 2 * 64,
                index + 1L, terrain);
    }

    private RiverOutlet outlet() {
        return new RiverOutlet(4L, HydrologyFeatureType.INLAND_GROTTO, 2L,
                new HydrologyPoint(64, 120, 0), new HydrologyPoint(64, 40, 0), 63, false);
    }

    private HydrologyTerrainSample land(int height, SurfaceRiverPolicy policy) {
        return HydrologyTerrainSample.openLand(height, 0D, "land").withSurfacePolicy(policy);
    }

    private HydrologyPlanner planner(HydrologyTerrainSampler sampler) {
        return new HydrologyPlanner(91L, HydrologyPlannerSettings.defaults(), sampler);
    }
}
