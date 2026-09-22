package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydraulicSegment;
import art.arcane.iris.generation.hydrology.HydrologyCandidateRejection;
import art.arcane.iris.generation.hydrology.HydrologyGeometrySampler;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyPoint;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSampler;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RiverProfileSolverTest {
    private static final HydrologyGeometrySampler GEOMETRY = request -> switch (request.field()) {
        case SURFACE_WIDTH -> 6;
        case SURFACE_DEPTH -> 2;
        default -> request.minimum();
    };

    @Test
    public void detailedTerrainDoesNotReplacePlannedRiverElevations() {
        List<HydrologyPoint> path = List.of(new HydrologyPoint(0, 80, 0), new HydrologyPoint(96, 80, 0));
        for (int natural : List.of(78, 83, 84)) {
            SurfaceCourseResult result = build(path, (x, z) -> HydrologyTerrainSample.openLand(natural, 0D, "land"));
            assertNull(result.rejection());
            assertEquals(80, result.lastHead());
            for (HydraulicSegment segment : result.segments()) {
                assertEquals(80, segment.upstreamHeadY());
                assertEquals(80, segment.downstreamHeadY());
            }
        }
    }

    @Test
    public void interpolatedSharedHeadsRemainNonRisingAcrossStations() {
        List<HydrologyPoint> path = List.of(new HydrologyPoint(-96, 88, -16),
                new HydrologyPoint(0, 80, 0), new HydrologyPoint(96, 72, 16));
        SurfaceCourseResult result = build(path,
                (x, z) -> HydrologyTerrainSample.openLand(82 - Math.floorDiv(x, 12), 0D, "land"));
        assertNull(result.rejection());
        int previous = Integer.MAX_VALUE;
        for (HydraulicSegment segment : result.segments()) {
            for (HydrologyPoint point : segment.centerline()) {
                assertTrue(point.y() <= previous);
                previous = point.y();
            }
        }
        assertEquals(72, result.lastHead());
    }

    @Test
    public void plannedProfileChecksTheCenterWithoutSearchingBankRings() {
        List<HydrologyPoint> path = List.of(new HydrologyPoint(0, 80, 0), new HydrologyPoint(96, 80, 0));
        SurfaceCenterline centerline = SurfaceCenterline.densify(path);
        double[] widths = new double[centerline.size()];
        double[] depths = new double[centerline.size()];
        double[] banks = new double[centerline.size()];
        Arrays.fill(widths, 3D);
        Arrays.fill(depths, 2D);
        Arrays.fill(banks, 1D);
        AtomicInteger samples = new AtomicInteger();
        HydrologyTerrainSampler sampler = (x, z) -> {
            assertEquals(0, z);
            samples.incrementAndGet();
            return HydrologyTerrainSample.openLand(82, 0D, "land");
        };
        ValleyProfile profile = new RiverProfileSolver(
                new RiverProfileSolver.Options(HydrologyPlannerSettings.defaults().surface(), 60), sampler)
                .solve(path, centerline, new ChannelProfile(widths, depths, banks), SurfaceTerminal.SINKHOLE, 40, 32);
        assertNull(profile.rejection());
        assertEquals(centerline.size(), samples.get());
    }

    @Test
    public void aFineCliffKeepsTheSourceHeadAndRaisesOnlyTheRequiredApproach() {
        HydrologyTerrainSampler sampler = (x, z) -> HydrologyTerrainSample.openLand(x < 32 ? 102 : 86, 0D, "land");
        SurfaceCourseResult result = build(List.of(new HydrologyPoint(0, 100, 0),
                new HydrologyPoint(64, 84, 0)), sampler);

        assertNull(result.rejection());
        assertEquals(100, result.segments().getFirst().upstreamHeadY());
        assertEquals(84, result.lastHead());
        int previous = 100;
        for (HydraulicSegment segment : result.segments()) {
            for (HydrologyPoint point : segment.centerline()) {
                assertTrue(point.y() <= previous);
                assertTrue(sampler.sample(point.x(), point.z()).naturalHeight() - point.y() + 2
                        <= HydrologyPlannerSettings.defaults().surface().maximumIncision());
                previous = point.y();
            }
        }
    }

    @Test
    public void anUncuttableFineTerrainRidgeStillRejectsTheCourse() {
        SurfaceCourseResult result = build(List.of(new HydrologyPoint(0, 80, 0), new HydrologyPoint(96, 80, 0)),
                (x, z) -> HydrologyTerrainSample.openLand(x == 48 ? 110 : 82, 0D, "land"));
        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, result.rejection());
    }

    private static SurfaceCourseResult build(List<HydrologyPoint> path, HydrologyTerrainSampler sampler) {
        return new SurfaceCourseBuilder(HydrologyPlannerSettings.defaults().surface(), sampler, GEOMETRY, 60)
                .build(7L, 19L, "default", path, SurfaceTerminal.SINKHOLE, 40, 32);
    }
}
