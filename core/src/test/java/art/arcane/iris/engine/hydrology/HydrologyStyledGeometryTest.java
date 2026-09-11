package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.policy.SurfaceRiverPolicy;

import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxel;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelView;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HydrologyStyledGeometryTest {
    private static final HydrologyTileKey TILE = new HydrologyTileKey(0, 0);

    @Test
    public void nonFlatGeometryIsResolvedIntoAcceptedPlansDeterministically() {
        HydrologyPlannerSettings.DeepFluid deepFluid = new HydrologyPlannerSettings.DeepFluid(
                "deep_water",
                true,
                2D,
                32,
                20,
                40,
                3,
                3,
                2,
                2,
                8,
                16,
                3,
                2,
                3,
                8192,
                2,
                false,
                true
        );
        HydrologyPlannerSettings surfaceSettings = settings(2D, 0D, List.of(deepFluid));
        HydrologyPlannerSettings undergroundSettings = settings(0D, 2D, List.of());
        HydrologyTerrainSampler terrain = this::terrain;
        HashSet<HydrologyGeometrySampler.Field> sampledFields = new HashSet<>();
        HydrologyGeometrySampler geometry = request -> {
            sampledFields.add(request.field());
            return styledValue(request);
        };
        HydrologyPlanner surfacePlanner = planner(7711L, surfaceSettings, terrain, geometry);
        HydrologyPlanner undergroundPlanner = planner(7711L, undergroundSettings, terrain, geometry);

        HydrologyTile first = surfacePlanner.plan(TILE);
        HydrologyTile repeated = surfacePlanner.plan(TILE);
        HydrologyTile undergroundFirst = undergroundPlanner.plan(TILE);
        HydrologyTile undergroundRepeated = undergroundPlanner.plan(TILE);

        assertEquals(first, repeated);
        assertEquals(undergroundFirst, undergroundRepeated);
        assertEquals(
                Set.of(
                        HydrologyGeometrySampler.Field.SURFACE_WIDTH,
                        HydrologyGeometrySampler.Field.SURFACE_DEPTH,
                        HydrologyGeometrySampler.Field.UNDERGROUND_FLUID_LEVEL,
                        HydrologyGeometrySampler.Field.UNDERGROUND_WIDTH,
                        HydrologyGeometrySampler.Field.UNDERGROUND_DEPTH,
                        HydrologyGeometrySampler.Field.UNDERGROUND_HEADROOM,
                        HydrologyGeometrySampler.Field.DEEP_FLUID_HEIGHT
                ),
                sampledFields
        );

        RiverCourse surface = courses(first, RiverCourseType.SURFACE).getFirst();
        DrainageNode surfaceSource = first.node(surface.sourceNodeId().orElseThrow()).orElseThrow();
        HydraulicSegment surfaceStart = surface.segments().getFirst();
        // The first segment carries the spring pool: wider by the spring ratio and one block deeper.
        int maximumSurfaceWidth = (int) Math.ceil(surfaceSettings.surface().maximumWidth()
                * surfaceSettings.surface().banks().springWidthRatio())
                + (surfaceStart.receivingPool() ? 2 : 0);
        int maximumSurfaceDepth = surfaceSettings.surface().maximumDepth() + 1
                + (surfaceStart.receivingPool() ? 1 : 0);
        assertTrue(surfaceStart.width() >= surfaceSettings.surface().minimumWidth());
        assertTrue(surfaceStart.width() <= maximumSurfaceWidth);
        assertTrue(surfaceStart.depth() >= surfaceSettings.surface().minimumDepth());
        assertTrue(surfaceStart.depth() <= maximumSurfaceDepth);
        assertTrue(
                "head=" + surfaceStart.upstreamHeadY() + " natural="
                        + terrain(surfaceSource.naturalPoint().x(), surfaceSource.naturalPoint().z()).naturalHeight(),
                surfaceStart.upstreamHeadY() <=
                        terrain(surfaceSource.naturalPoint().x(), surfaceSource.naturalPoint().z()).naturalHeight()
                                - surfaceSettings.surface().banks().sink()
        );
        int widestSurfaceWidth = 0;
        for (HydraulicSegment segment : surface.segments()) {
            widestSurfaceWidth = Math.max(widestSurfaceWidth, segment.width());
        }
        assertTrue(widestSurfaceWidth >= surfaceStart.width());
        assertTrue(widestSurfaceWidth <= (int) Math.ceil(surfaceSettings.surface().maximumWidth()
                * Math.max(2D, surfaceSettings.surface().banks().springWidthRatio())));

        RiverCourse underground = courses(undergroundFirst, RiverCourseType.UNDERGROUND).getFirst();
        DrainageNode undergroundSource = undergroundFirst.node(underground.sourceNodeId().orElseThrow()).orElseThrow();
        HydraulicSegment undergroundStart = underground.segments().getFirst();
        assertEquals(undergroundSettings.underground().maximumFluidY(), undergroundStart.upstreamHeadY());
        int expectedUndergroundWidth = undergroundStart.type().isDrop()
                ? undergroundSettings.geometry().drops().flowWidth(undergroundSettings.underground().maximumWidth())
                : undergroundSettings.underground().maximumWidth();
        int expectedUndergroundDepth = undergroundStart.type().isDrop()
                ? undergroundSettings.geometry().drops().flowDepth(undergroundSettings.underground().maximumDepth())
                : undergroundSettings.underground().maximumDepth();
        assertEquals(expectedUndergroundWidth, undergroundStart.width());
        assertEquals(expectedUndergroundDepth, undergroundStart.depth());
        assertEquals(
                styledValue(request(
                        HydrologyGeometrySampler.Field.UNDERGROUND_FLUID_LEVEL,
                        underground.profileKey(),
                        undergroundSource.naturalPoint(),
                        undergroundSettings.underground().minimumFluidY(),
                        undergroundSettings.underground().maximumFluidY()
                )),
                undergroundStart.upstreamHeadY()
        );

        List<RiverCourse> deepCourses = courses(first, RiverCourseType.DEEP_FLUID);
        assertFalse(deepCourses.isEmpty());
        for (RiverCourse course : deepCourses) {
            HydraulicSegment segment = course.segments().getFirst();
            assertEquals(
                    styledValue(request(
                            HydrologyGeometrySampler.Field.DEEP_FLUID_HEIGHT,
                            course.profileKey(),
                            segment.start(),
                            deepFluid.minimumY(),
                            deepFluid.maximumY()
                    )),
                    segment.upstreamHeadY()
            );
        }
    }

    private HydrologyPlanner planner(
            long seed,
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler terrain,
            HydrologyGeometrySampler geometry
    ) {
        return new HydrologyPlanner(seed, settings, terrain, geometry, -4096, footprint -> solidCaveView());
    }

    private CaveVoxelView solidCaveView() {
        return new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                return position.y() > -4096 && position.y() < 4096;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                return CaveVoxel.SOLID;
            }

            @Override
            public boolean isOpenToSurface(CavePosition position) {
                return false;
            }

            @Override
            public boolean isAboveTerrainSurface(CavePosition position) {
                return false;
            }
        };
    }

    private HydrologyPlannerSettings settings(
            double surfaceDensity,
            double undergroundDensity,
            List<HydrologyPlannerSettings.DeepFluid> deepFluids
    ) {
        HydrologyPlannerSettings.Source surfaceSources = new HydrologyPlannerSettings.Source(
                true,
                surfaceDensity,
                80,
                0,
                4,
                24
        );
        HydrologyPlannerSettings.Source undergroundSources = new HydrologyPlannerSettings.Source(
                true,
                undergroundDensity,
                Integer.MIN_VALUE,
                0,
                4,
                32
        );
        return new HydrologyPlannerSettings(
                63,
                new HydrologyPlannerSettings.Routing(128, 16, 512, 256, 16, 8, 0.5D, 12D, 0.5D, 0.1D, 1D, 0, HydrologyPlannerSettings.Regional.disabled()),
                new HydrologyPlannerSettings.Surface(
                        surfaceDensity > 0D,
                        surfaceSources,
                        4,
                        18,
                        2,
                        4,
                        10,
                        1.5D,
                        HydrologyPlannerSettings.Banks.defaults()),
                new HydrologyPlannerSettings.Hydraulics(4),
                HydrologyPlannerSettings.Underground.of(
                        undergroundDensity > 0D,
                        undergroundSources,
                        68,
                        82,
                        4,
                        12,
                        2,
                        4,
                        5,
                        9,
                        true,
                        0
                ),
                HydrologyPlannerSettings.Outlets.of(
                        true,
                        new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                        new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                        false,
                        12,
                        32,
                        2,
                        4,
                        4
                ),
                HydrologyPlannerSettings.Geometry.defaults(),
                deepFluids, List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private HydrologyTerrainSample terrain(int x, int z) {
        if (x >= 112) {
            return new HydrologyTerrainSample(
                    54,
                    0D,
                    true,
                    false,
                    30,
                    32,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    0D,
                    0D,
                    0D,
                    1D,
                    1D,
                    1D,
                    1D,
                    1D,
                    "ocean",
                    "surface",
                    "mouth",
                    "shore",
                    "dry",
                    "flooded",
                    List.of("water"), List.of(),
                    Double.NaN,
                    null,
                    Double.NaN,
                    true,
                    SurfaceRiverPolicy.INHERIT
            );
        }
        int height = 118 - Math.floorDiv(x, 12) + (int) StrictMath.round(StrictMath.sin(z / 18D) * 2D);
        boolean source = x >= 0 && x <= 24;
        return new HydrologyTerrainSample(
                height,
                1D,
                false,
                true,
                70,
                74,
                true,
                true,
                source,
                source,
                source,
                source,
                0D,
                source ? 1D : 0D,
                source ? 1D : 0D,
                1D,
                1D,
                1D,
                1D,
                1D,
                "parent",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded",
                List.of("water"), List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true,
                SurfaceRiverPolicy.INHERIT
        );
    }

    private int styledValue(HydrologyGeometrySampler.Request request) {
        return request.x() < 56 ? request.maximum() : request.minimum();
    }

    private HydrologyGeometrySampler.Request request(
            HydrologyGeometrySampler.Field field,
            String profileKey,
            HydrologyPoint point,
            int minimum,
            int maximum
    ) {
        return new HydrologyGeometrySampler.Request(field, profileKey, point.x(), point.z(), 0L, minimum, maximum);
    }

    private List<RiverCourse> courses(HydrologyTile tile, RiverCourseType type) {
        ArrayList<RiverCourse> selected = new ArrayList<>();
        for (RiverCourse course : tile.courses()) {
            if (course.type() == type) {
                selected.add(course);
            }
        }
        return List.copyOf(selected);
    }

}
