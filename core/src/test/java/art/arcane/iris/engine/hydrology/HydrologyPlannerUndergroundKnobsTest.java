package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.policy.SurfaceRiverPolicy;

import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxel;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelView;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The dimension-scoped underground shaping knobs: rock cover over a passage, floor cover under it,
 * the source count the tributary widening saturates at, the underground cascade run, the coastal
 * cliff slope factor and the sea cave sweep jitter. Every default reproduces the previous arithmetic.
 */
public class HydrologyPlannerUndergroundKnobsTest {
    private static final HydrologyTileKey TILE = new HydrologyTileKey(0, 0);
    private static final int SEA_LEVEL = 63;
    private static final CaveVoxelView SOLID_CAVE_VIEW = new SolidCaveVoxelView();
    private static final HydrologyPlannerSettings.Grotto CHAMBER =
            new HydrologyPlannerSettings.Grotto(true, 12, 7, 10, 32768);

    @Test
    public void rockCoverDefaultsReproduceThePlanAndAThickerCoverLowersOrRejectsPassages() {
        HydrologyPlannerSettings base = undergroundSettings();
        HydrologyTerrainSampler coast = undergroundCoast();

        HydrologyTile fromFactory = new HydrologyPlanner(331L, base, coast, SOLID_CAVE_VIEW).plan(TILE);
        HydrologyTile explicit = new HydrologyPlanner(
                331L,
                withCover(base, 1, 1, base.underground().wideningSources()),
                coast,
                SOLID_CAVE_VIEW
        ).plan(TILE);
        List<RiverCourse> planned = courses(fromFactory, RiverCourseType.UNDERGROUND);

        assertFalse("diagnostics=" + fromFactory.diagnosticCandidates(), planned.isEmpty());
        assertEquals(fromFactory.courses(), explicit.courses());
        assertCoveredByRock(coast, planned, base.underground().minimumHeadroom(), 1);

        HydrologyTile covered = new HydrologyPlanner(
                331L,
                withCover(base, 16, 1, base.underground().wideningSources()),
                coast,
                SOLID_CAVE_VIEW
        ).plan(TILE);
        assertCoveredByRock(coast, courses(covered, RiverCourseType.UNDERGROUND), base.underground().minimumHeadroom(), 16);

        // No column on this coast stands 48 blocks over the configured fluid range, so every passage is refused.
        HydrologyTile buried = new HydrologyPlanner(
                331L,
                withCover(base, 48, 1, base.underground().wideningSources()),
                coast,
                SOLID_CAVE_VIEW
        ).plan(TILE);
        assertTrue(
                "buried=" + courses(buried, RiverCourseType.UNDERGROUND).size()
                        + " planned=" + planned.size(),
                courses(buried, RiverCourseType.UNDERGROUND).size() < planned.size()
        );
        assertCoveredByRock(coast, courses(buried, RiverCourseType.UNDERGROUND), base.underground().minimumHeadroom(), 48);
    }

    @Test
    public void floorCoverKeepsBedsAboveTheWorldFloor() {
        HydrologyPlannerSettings base = inlandUndergroundSettings();
        HydrologyTerrainSampler inland = inlandTerrain();
        int worldFloor = 0;
        // The head solver reserves the deepest possible basin under every node, then the floor cover.
        int reserved = Math.max(base.underground().maximumDepth(), base.geometry().drops().maximumBasinDepth());
        int floorCover = 48;
        int bound = worldFloor + reserved + floorCover;

        HydrologyTile shallow = new HydrologyPlanner(
                8821L,
                base,
                inland,
                worldFloor,
                plannedSurface -> SOLID_CAVE_VIEW
        ).plan(TILE);
        HydrologyTile raised = new HydrologyPlanner(
                8821L,
                withCover(base, base.underground().minimumRockCover(), floorCover, base.underground().wideningSources()),
                inland,
                worldFloor,
                plannedSurface -> SOLID_CAVE_VIEW
        ).plan(TILE);

        List<RiverCourse> shallowCourses = courses(shallow, RiverCourseType.UNDERGROUND);
        assertFalse("diagnostics=" + shallow.diagnosticCandidates(), shallowCourses.isEmpty());
        assertTrue(
                "heads=" + heads(shallowCourses) + " bound=" + bound,
                heads(shallowCourses).stream().anyMatch((Integer head) -> head < bound)
        );
        for (int head : heads(courses(raised, RiverCourseType.UNDERGROUND))) {
            assertTrue("head=" + head + " bound=" + bound, head >= bound);
        }
        assertNotEquals(shallowCourses, courses(raised, RiverCourseType.UNDERGROUND));
    }

    @Test
    public void wideningSourcesChangesTheDischargeWidening() {
        HydrologyPlannerSettings base = undergroundSettings();
        HydrologyTerrainSampler coast = undergroundCoast();

        HydrologyTile saturatingEarly = new HydrologyPlanner(331L, base, coast, SOLID_CAVE_VIEW).plan(TILE);
        HydrologyTile saturatingLate = new HydrologyPlanner(
                331L,
                withCover(base, base.underground().minimumRockCover(), base.underground().minimumFloorCover(), 64),
                coast,
                SOLID_CAVE_VIEW
        ).plan(TILE);

        int wide = widestUndergroundSegment(saturatingEarly);
        int narrow = widestUndergroundSegment(saturatingLate);

        assertTrue("wide=" + wide, wide > 0);
        assertTrue("narrow=" + narrow, narrow > 0);
        assertTrue("wide=" + wide + " narrow=" + narrow, narrow < wide);
    }

    @Test
    public void undergroundCascadeRunZeroKeepsTheShortestRunAndAPositiveValueLengthensIt() {
        HydrologyPlannerSettings base = terracedUndergroundSettings();
        HydrologyTerrainSampler terraced = terracedTerrain();

        HydrologyTile shortest = new HydrologyPlanner(1442L, base, terraced, SOLID_CAVE_VIEW).plan(TILE);
        HydrologyTile lengthened = new HydrologyPlanner(
                1442L,
                withUndergroundCascadeRun(base, 4),
                terraced,
                SOLID_CAVE_VIEW
        ).plan(TILE);

        int shortRun = cascadePoints(shortest);
        int longRun = cascadePoints(lengthened);

        assertTrue("shortRun=" + shortRun, shortRun > 0);
        assertTrue("shortRun=" + shortRun + " longRun=" + longRun, longRun > shortRun);
    }

    @Test
    public void cliffSlopeFactorScalesTheSlopeRuleAndZeroTurnsItOff() {
        // A coast five blocks over the sea, well under the twelve-block cliff height, sloping at one.
        HydrologyPlannerSettings base = coastalSettings();
        HydrologyTerrainSampler lowCoast = lowCoast();

        HydrologyTile atTheDefault = new HydrologyPlanner(4711L, base, lowCoast, SOLID_CAVE_VIEW).plan(TILE);
        HydrologyTile sensitive = new HydrologyPlanner(
                4711L,
                withCliffSlopeFactor(base, 0.05D),
                lowCoast,
                SOLID_CAVE_VIEW
        ).plan(TILE);
        HydrologyTile slopeRuleOff = new HydrologyPlanner(
                4711L,
                withCliffSlopeFactor(base, 0D),
                lowCoast,
                SOLID_CAVE_VIEW
        ).plan(TILE);

        assertFalse("diagnostics=" + atTheDefault.diagnosticCandidates(), atTheDefault.outlets().isEmpty());
        for (RiverOutlet outlet : atTheDefault.outlets()) {
            assertNotEquals(HydrologyFeatureType.COASTAL_GROTTO, outlet.type());
        }
        assertFalse("diagnostics=" + sensitive.diagnosticCandidates(), sensitive.outlets().isEmpty());
        boolean grotto = false;
        for (RiverOutlet outlet : sensitive.outlets()) {
            grotto |= outlet.type() == HydrologyFeatureType.COASTAL_GROTTO;
        }
        assertTrue("outlets=" + sensitive.outlets(), grotto);
        assertFalse("diagnostics=" + slopeRuleOff.diagnosticCandidates(), slopeRuleOff.outlets().isEmpty());
        for (RiverOutlet outlet : slopeRuleOff.outlets()) {
            assertNotEquals(HydrologyFeatureType.COASTAL_GROTTO, outlet.type());
        }
    }

    @Test
    public void sweepJitterZeroSweepsSeaCavesStraightInland() {
        HydrologyPlannerSettings.SeaCaves jittered = HydrologyPlannerSettings.SeaCaves.of(true, 4, 32, 8, 12);
        HydrologyPlannerSettings base = seaCaveSettings(jittered);
        HydrologyTerrainSampler cliffCoast = cliffCoast();

        HydrologyTile turned = new HydrologyPlanner(994L, base, cliffCoast).plan(TILE);
        HydrologyTile straight = new HydrologyPlanner(
                994L,
                seaCaveSettings(new HydrologyPlannerSettings.SeaCaves(
                        jittered.enabled(),
                        jittered.maximumPerTile(),
                        jittered.minimumSpacing(),
                        jittered.minimumCoastHeight(),
                        jittered.depth(),
                        0D
                )),
                cliffCoast
        ).plan(TILE);

        List<RiverCourse> turnedCaves = courses(turned, RiverCourseType.SEA_CAVE);
        List<RiverCourse> straightCaves = courses(straight, RiverCourseType.SEA_CAVE);

        assertFalse("diagnostics=" + turned.diagnosticCandidates(), turnedCaves.isEmpty());
        assertFalse("diagnostics=" + straight.diagnosticCandidates(), straightCaves.isEmpty());
        boolean offNormal = false;
        for (RiverCourse cave : turnedCaves) {
            HydraulicSegment chamber = cave.segments().getFirst();
            offNormal |= chamber.start().z() != chamber.end().z();
        }
        assertTrue("caves=" + turnedCaves, offNormal);
        for (RiverCourse cave : straightCaves) {
            HydraulicSegment chamber = cave.segments().getFirst();
            assertEquals("chamber=" + chamber, chamber.end().z(), chamber.start().z());
        }
    }

    private void assertCoveredByRock(
            HydrologyTerrainSampler sampler,
            List<RiverCourse> courses,
            int minimumHeadroom,
            int rockCover
    ) {
        for (RiverCourse course : courses) {
            for (HydraulicSegment segment : course.segments()) {
                for (HydrologyPoint point : List.of(segment.start(), segment.end())) {
                    HydrologyTerrainSample terrain = sampler.sample(point.x(), point.z());
                    if (terrain.ocean()) {
                        continue;
                    }
                    int head = Math.max(segment.upstreamHeadY(), segment.downstreamHeadY());
                    assertTrue(
                            "head=" + head + " cover=" + rockCover + " natural=" + terrain.naturalHeight(),
                            head + minimumHeadroom + rockCover <= terrain.naturalHeight()
                    );
                }
            }
        }
    }

    private List<Integer> heads(List<RiverCourse> courses) {
        ArrayList<Integer> heads = new ArrayList<>();
        for (RiverCourse course : courses) {
            for (HydraulicSegment segment : course.segments()) {
                heads.add(segment.upstreamHeadY());
                heads.add(segment.downstreamHeadY());
            }
        }
        return List.copyOf(heads);
    }

    private int widestUndergroundSegment(HydrologyTile tile) {
        int widest = 0;
        for (RiverCourse course : courses(tile, RiverCourseType.UNDERGROUND)) {
            for (HydraulicSegment segment : course.segments()) {
                widest = Math.max(widest, segment.width());
            }
        }
        return widest;
    }

    private int cascadePoints(HydrologyTile tile) {
        int points = 0;
        for (RiverCourse course : courses(tile, RiverCourseType.UNDERGROUND)) {
            for (HydraulicSegment segment : course.segments()) {
                if (segment.type() == HydrologyFeatureType.UNDERGROUND_DROP && segment.drop() > 0) {
                    points += segment.centerline().size();
                }
            }
        }
        return points;
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

    private HydrologyPlannerSettings withCover(
            HydrologyPlannerSettings settings,
            int minimumRockCover,
            int minimumFloorCover,
            int wideningSources
    ) {
        HydrologyPlannerSettings.Underground underground = settings.underground();
        return replaceUnderground(settings, new HydrologyPlannerSettings.Underground(
                underground.enabled(),
                underground.sources(),
                underground.minimumFluidY(),
                underground.maximumFluidY(),
                underground.minimumWidth(),
                underground.maximumWidth(),
                underground.minimumDepth(),
                underground.maximumDepth(),
                underground.minimumHeadroom(),
                underground.maximumHeadroom(),
                underground.connectToExistingCaves(),
                underground.tributaries(),
                minimumRockCover,
                minimumFloorCover,
                wideningSources
        ));
    }

    private HydrologyPlannerSettings replaceUnderground(
            HydrologyPlannerSettings settings,
            HydrologyPlannerSettings.Underground underground
    ) {
        return new HydrologyPlannerSettings(
                settings.seaLevel(),
                settings.routing(),
                settings.surface(),
                settings.hydraulics(),
                underground,
                settings.outlets(),
                settings.geometry(),
                settings.deepFluids(),
                settings.surfacePools(),
                settings.widestShoreBiomeWidth(),
                settings.seaCaves(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private HydrologyPlannerSettings withUndergroundCascadeRun(HydrologyPlannerSettings settings, int runPerBlock) {
        HydrologyPlannerSettings.Drops drops = settings.geometry().drops();
        HydrologyPlannerSettings.Geometry geometry = settings.geometry();
        return new HydrologyPlannerSettings(
                settings.seaLevel(),
                settings.routing(),
                settings.surface(),
                settings.hydraulics(),
                settings.underground(),
                settings.outlets(),
                new HydrologyPlannerSettings.Geometry(
                        geometry.meanders(),
                        geometry.surface(),
                        geometry.underground(),
                        geometry.grottos(),
                        new HydrologyPlannerSettings.Drops(
                                drops.cascadeRunPerBlock(),
                                drops.cascadeExponent(),
                                drops.maximumCascadeStep(),
                                drops.flowWidthRatio(),
                                drops.maximumFlowDepth(),
                                drops.basinWidthRatio(),
                                drops.maximumBasinDepth(),
                                runPerBlock
                        )
                ),
                settings.deepFluids(),
                settings.surfacePools(),
                settings.widestShoreBiomeWidth(),
                settings.seaCaves(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private HydrologyPlannerSettings withCliffSlopeFactor(HydrologyPlannerSettings settings, double factor) {
        HydrologyPlannerSettings.Outlets outlets = settings.outlets();
        return new HydrologyPlannerSettings(
                settings.seaLevel(),
                settings.routing(),
                settings.surface(),
                settings.hydraulics(),
                settings.underground(),
                new HydrologyPlannerSettings.Outlets(
                        outlets.oceanEnabled(),
                        outlets.coastalGrotto(),
                        outlets.inlandGrotto(),
                        outlets.surfaceSinkholesEnabled(),
                        outlets.coastalCliffMinimumHeight(),
                        outlets.mouthLevelingDistance(),
                        outlets.maximumOceanApron(),
                        outlets.maximumPerTile(),
                        outlets.maximumCoastalPerTile(),
                        factor
                ),
                settings.geometry(),
                settings.deepFluids(),
                settings.surfacePools(),
                settings.widestShoreBiomeWidth(),
                settings.seaCaves(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private HydrologyTerrainSampler undergroundCoast() {
        return (int x, int z) -> {
            if (x >= 112) {
                return oceanColumn();
            }
            int height = 118 - Math.floorDiv(x, 12) + (int) StrictMath.round(StrictMath.sin(z / 18D) * 2D);
            return undergroundColumn(height, 72, 74, x >= 0 && x <= 24, true);
        };
    }

    private HydrologyTerrainSampler inlandTerrain() {
        return (int x, int z) -> undergroundColumn(118 - Math.floorDiv(x, 12), 44, 48, x >= 0 && x <= 24, true);
    }

    private HydrologyTerrainSampler terracedTerrain() {
        return (int x, int z) -> {
            if (x >= 112) {
                return oceanColumn();
            }
            int fluidY = 84 - Math.max(0, Math.floorDiv(x, 32)) * 2;
            return undergroundColumn(120, fluidY - 4, fluidY, x == 0 && z == 0, z == 0);
        };
    }

    private HydrologyTerrainSampler lowCoast() {
        return (int x, int z) -> x >= 112
                ? oceanColumn()
                : HydrologyTerrainSample.openLand(70 - Math.floorDiv(x, 40), 1D, "land");
    }

    private HydrologyTerrainSampler cliffCoast() {
        return (int x, int z) -> x >= 112
                ? HydrologyTerrainSample.ocean(54, "ocean")
                : HydrologyTerrainSample.openLand(92, 2D, "land");
    }

    private HydrologyTerrainSample oceanColumn() {
        return HydrologyTerrainSample.ocean(54, "ocean");
    }

    private HydrologyTerrainSample undergroundColumn(
            int height,
            int caveFloorY,
            int caveFluidY,
            boolean source,
            boolean transit
    ) {
        return new HydrologyTerrainSample(
                height,
                1D,
                false,
                true,
                caveFloorY,
                caveFluidY,
                transit,
                transit,
                false,
                false,
                source,
                source,
                0D,
                0D,
                source ? 1D : 0D,
                1D,
                1D,
                1D,
                1D,
                1D,
                "land",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded",
                List.of("default"),
                List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true,
                SurfaceRiverPolicy.INHERIT
        );
    }

    private HydrologyPlannerSettings undergroundSettings() {
        return plannerSettings(
                disabledSource(),
                new HydrologyPlannerSettings.Source(true, 3D, Integer.MIN_VALUE, 0, 4, 32),
                false,
                true,
                68,
                82,
                4,
                12,
                2,
                4,
                5,
                9,
                new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                true,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }

    private HydrologyPlannerSettings inlandUndergroundSettings() {
        return plannerSettings(
                disabledSource(),
                new HydrologyPlannerSettings.Source(true, 3D, Integer.MIN_VALUE, 0, 4, 32),
                false,
                true,
                40,
                60,
                4,
                12,
                2,
                4,
                5,
                9,
                new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                new HydrologyPlannerSettings.Grotto(true, 4, 3, 3, 4096),
                false,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }

    private HydrologyPlannerSettings terracedUndergroundSettings() {
        return plannerSettings(
                disabledSource(),
                new HydrologyPlannerSettings.Source(true, 1D, Integer.MIN_VALUE, 1, 1, 16),
                false,
                true,
                64,
                84,
                2,
                2,
                1,
                2,
                2,
                4,
                new HydrologyPlannerSettings.Grotto(false, 2, 2, 2, 512),
                new HydrologyPlannerSettings.Grotto(false, 2, 2, 2, 512),
                true,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }

    private HydrologyPlannerSettings coastalSettings() {
        return plannerSettings(
                new HydrologyPlannerSettings.Source(true, 4D, 0, 0, 6, 24),
                disabledSource(),
                true,
                false,
                68,
                82,
                4,
                12,
                2,
                4,
                5,
                9,
                CHAMBER,
                new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                true,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }

    // A riverless cliff coast: the drainage lattice still runs, so the sea cave pass has a grid to walk.
    private HydrologyPlannerSettings seaCaveSettings(HydrologyPlannerSettings.SeaCaves seaCaves) {
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(
                true,
                new HydrologyPlannerSettings.Source(true, 0D, 80, 0, 6, 24),
                4,
                18,
                2,
                4,
                10,
                1.5D,
                HydrologyPlannerSettings.Banks.defaults()
        );
        return new HydrologyPlannerSettings(
                SEA_LEVEL,
                new HydrologyPlannerSettings.Routing(128, 16, 512, 256, 0, 0, 0.5D, 12D, 0.5D, 0.1D, 1D, 0),
                surface,
                new HydrologyPlannerSettings.Hydraulics(4),
                HydrologyPlannerSettings.Underground.of(
                        false,
                        new HydrologyPlannerSettings.Source(true, 0D, Integer.MIN_VALUE, 0, 4, 32),
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
                        CHAMBER,
                        new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                        false,
                        12,
                        32,
                        12,
                        4,
                        4
                ),
                HydrologyPlannerSettings.Geometry.defaults(),
                List.of(),
                List.of(),
                surface.shoreWidth(),
                seaCaves,
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private HydrologyPlannerSettings.Source disabledSource() {
        return new HydrologyPlannerSettings.Source(false, 0D, Integer.MIN_VALUE, 0, 0, 0);
    }

    private HydrologyPlannerSettings plannerSettings(
            HydrologyPlannerSettings.Source surfaceSources,
            HydrologyPlannerSettings.Source undergroundSources,
            boolean surfaceEnabled,
            boolean undergroundEnabled,
            int minimumFluidY,
            int maximumFluidY,
            int minimumWidth,
            int maximumWidth,
            int minimumDepth,
            int maximumDepth,
            int minimumHeadroom,
            int maximumHeadroom,
            HydrologyPlannerSettings.Grotto coastalGrotto,
            HydrologyPlannerSettings.Grotto inlandGrotto,
            boolean oceanEnabled,
            HydrologyPlannerSettings.SeaCaves seaCaves
    ) {
        return new HydrologyPlannerSettings(
                SEA_LEVEL,
                new HydrologyPlannerSettings.Routing(128, 16, 512, 256, 0, 0, 0.5D, 12D, 0.5D, 0.1D, 1D, 0),
                new HydrologyPlannerSettings.Surface(
                        surfaceEnabled,
                        surfaceSources,
                        4,
                        18,
                        2,
                        4,
                        10,
                        1.5D,
                        HydrologyPlannerSettings.Banks.defaults()
                ),
                new HydrologyPlannerSettings.Hydraulics(4),
                HydrologyPlannerSettings.Underground.of(
                        undergroundEnabled,
                        undergroundSources,
                        minimumFluidY,
                        maximumFluidY,
                        minimumWidth,
                        maximumWidth,
                        minimumDepth,
                        maximumDepth,
                        minimumHeadroom,
                        maximumHeadroom,
                        false,
                        0
                ),
                HydrologyPlannerSettings.Outlets.of(
                        oceanEnabled,
                        coastalGrotto,
                        inlandGrotto,
                        false,
                        12,
                        32,
                        8,
                        4,
                        4
                ),
                HydrologyPlannerSettings.Geometry.defaults(),
                List.of(),
                List.of(),
                0D,
                seaCaves,
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private static final class SolidCaveVoxelView implements CaveVoxelView {
        @Override
        public boolean isInWorld(CavePosition position) {
            return position.y() > -2048 && position.y() < 2048;
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
    }
}
