package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.policy.SurfaceRiverPolicy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A 256-block tile whose western half is one region and whose eastern half is another. The only sea is
 * along the south edge of the eastern half, plus an optional pocket of sea in the south-western corner;
 * terrain falls to the south and slightly to the east, so rivers born in the west naturally want to
 * cross into the east.
 */
public class HydrologyPlannerConfinementTest {
    private static final HydrologyTileKey TILE = new HydrologyTileKey(0, 0);
    private static final int BOUNDARY = 128;
    private static final String WEST = "region:west";

    @Test
    public void unconfinedWestSourcesMayDrainAcrossTheRegionBoundary() {
        HydrologyTile tile = new HydrologyPlanner(91L, settings(), terrain(false, true)).plan(TILE);

        List<RiverCourse> westCourses = westCourses(tile);
        assertFalse("diagnostics=" + tile.diagnosticCandidates(), westCourses.isEmpty());
        assertTrue("no western course crossed into the east: " + describe(westCourses),
                westCourses.stream().anyMatch(course -> maximumX(course) >= BOUNDARY));
    }

    @Test
    public void confinedWestSourcesStayWestAllTheWayToTheirOutlet() {
        HydrologyTile tile = new HydrologyPlanner(91L, settings(), terrain(true, true)).plan(TILE);

        List<RiverCourse> westCourses = westCourses(tile);
        assertFalse("diagnostics=" + tile.diagnosticCandidates(), westCourses.isEmpty());
        for (RiverCourse course : westCourses) {
            assertTrue("western course left its region: " + describe(List.of(course)), maximumX(course) < BOUNDARY);
        }
    }

    @Test
    public void confinedWestSourcesWithoutAnOutletInsideAreRejectedAndSayWhy() {
        HydrologyTile tile = new HydrologyPlanner(91L, settings(), terrain(true, false)).plan(TILE);

        assertTrue(describe(westCourses(tile)), westCourses(tile).isEmpty());
        assertTrue("diagnostics=" + tile.diagnosticCandidates(), tile.diagnosticCandidates().stream()
                .anyMatch(candidate -> candidate.rejection() == HydrologyCandidateRejection.CONFINED_NO_OUTLET));
    }

    private static List<RiverCourse> westCourses(HydrologyTile tile) {
        ArrayList<RiverCourse> courses = new ArrayList<>();
        for (RiverCourse course : tile.courses()) {
            if (course.type() == RiverCourseType.SURFACE && sourceX(course) < BOUNDARY) {
                courses.add(course);
            }
        }
        return courses;
    }

    private static int sourceX(RiverCourse course) {
        return course.segments().getFirst().centerline().getFirst().x();
    }

    private static int maximumX(RiverCourse course) {
        int maximum = Integer.MIN_VALUE;
        for (HydraulicSegment segment : course.segments()) {
            for (HydrologyPoint point : segment.centerline()) {
                maximum = Math.max(maximum, point.x());
            }
        }
        return maximum;
    }

    private static String describe(List<RiverCourse> courses) {
        StringBuilder out = new StringBuilder();
        for (RiverCourse course : courses) {
            out.append(" course ").append(course.id()).append(" source x=").append(sourceX(course))
                    .append(" max x=").append(maximumX(course));
        }
        return out.toString();
    }

    private static HydrologyTerrainSampler terrain(boolean confineWest, boolean westPocket) {
        return (int x, int z) -> {
            String confines = confineWest && x < BOUNDARY ? WEST : null;
            boolean eastSea = x >= BOUNDARY && z < 24;
            boolean pocket = westPocket && x < 24 && z < 24;
            if (eastSea || pocket) {
                return sample(60, true, confines);
            }
            int height = (int) StrictMath.round(66D + z * 0.3D - x * 0.05D);
            return sample(height, false, confines);
        };
    }

    private static HydrologyTerrainSample sample(int height, boolean ocean, String confines) {
        return new HydrologyTerrainSample(
                height, 1D, ocean, false, height - 32, height - 30,
                !ocean, !ocean, !ocean, false, false, false,
                0D, ocean ? 0D : 1D, 0D, 1D, 1D, 1D, 1D, 1D,
                "land", "land", "land", "land", "land", "land",
                List.of("default"), List.of(), Double.NaN, confines, Double.NaN, true,
                SurfaceRiverPolicy.INHERIT
        );
    }

    private static HydrologyPlannerSettings settings() {
        HydrologyPlannerSettings.Source surfaceSources = new HydrologyPlannerSettings.Source(true, 4D, 80, 1, 6, 32);
        HydrologyPlannerSettings.Source undergroundSources = new HydrologyPlannerSettings.Source(false, 0D, Integer.MIN_VALUE, 0, 1, 48);
        HydrologyPlannerSettings.ChannelShape stableChannel = HydrologyPlannerSettings.ChannelShape.of(2D, 0D, 0D, 11);
        return new HydrologyPlannerSettings(
                63,
                new HydrologyPlannerSettings.Routing(256, 16, 1_089, 256, 16, 8, 0.5D, 12D, 0.5D, 0.2D, 1D, 0, HydrologyPlannerSettings.Regional.disabled()),
                new HydrologyPlannerSettings.Surface(true, surfaceSources, 4, 12, 2, 4, 12, 1.5D, HydrologyPlannerSettings.Banks.defaults()),
                new HydrologyPlannerSettings.Hydraulics(4),
                HydrologyPlannerSettings.Underground.of(false, undergroundSources, 68, 82, 4, 14, 2, 5, 6, 12, false, 0),
                HydrologyPlannerSettings.Outlets.of(
                        true,
                        new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                        new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                        false, 12, 48, 2, 8, 8),
                new HydrologyPlannerSettings.Geometry(
                        new HydrologyPlannerSettings.Meanders(224, 72, 0D, 0D, 0D, 0, 75D),
                        stableChannel, stableChannel, stableChannel,
                        HydrologyPlannerSettings.Geometry.defaults().drops()),
                List.of(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }
}
