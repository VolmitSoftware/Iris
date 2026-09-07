package art.arcane.iris.engine.hydrology;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HydrologyPlannerTributaryTest {
    private static final HydrologyTileKey TILE = new HydrologyTileKey(0, 0);
    private static final HydrologyTile SPLIT_TILE =
            new HydrologyPlanner(19L, settings(1), HydrologyPlannerTributaryTest::valley).plan(TILE);

    @Test
    public void heightOnlySamplingPreservesSurfacePlansWithFewerSlopeSamples() {
        HydrologyPlannerSettings settings = settings(1);
        HydrologyTerrainSampler terrain = (int x, int z) -> valley(x, z).withSlope(0.5D);
        CountingNaturalSampler referenceSampler = new CountingNaturalSampler(terrain, false);
        CountingNaturalSampler optimizedSampler = new CountingNaturalSampler(terrain, true);

        HydrologyTile reference = planner(settings, terrain, referenceSampler).plan(TILE);
        HydrologyTile optimized = planner(settings, terrain, optimizedSampler).plan(TILE);

        assertFalse(reference.courses().isEmpty());
        assertEquals(reference, optimized);
        assertTrue(optimizedSampler.heightOnlyCalls > 0);
        assertTrue("reference=" + referenceSampler.slopeCalls + ", optimized=" + optimizedSampler.slopeCalls,
                optimizedSampler.slopeCalls < referenceSampler.slopeCalls);
    }

    private static HydrologyPlanner planner(
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler terrain,
            HydrologyNaturalTerrainSampler naturalSampler
    ) {
        return new HydrologyPlanner(
                19L,
                settings,
                terrain,
                naturalSampler,
                HydrologyGeometrySampler.deterministic(terrain),
                -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(terrain, settings.seaLevel(), -4096, 4096)
        );
    }

    @Test
    public void aSecondSourceJoinsTheMainStemAsATributaryWhenAllowed() {
        HydrologyTerrainSampler terrain = HydrologyPlannerTributaryTest::valley;

        HydrologyTile single = new HydrologyPlanner(19L, settings(0), terrain).plan(TILE);
        HydrologyTile split = SPLIT_TILE;

        List<RiverCourse> singleCourses = surface(single);
        List<RiverCourse> splitCourses = surface(split);
        assertEquals(single.diagnosticCandidates().toString(), 1, singleCourses.size());
        assertEquals(split.diagnosticCandidates().toString(), 2, splitCourses.size());
        RiverCourse stem = null;
        RiverCourse tributary = null;
        for (RiverCourse course : splitCourses) {
            if (stem == null || stations(course) > stations(stem)) {
                tributary = stem;
                stem = course;
            } else {
                tributary = course;
            }
        }
        assertNotNull(stem);
        assertNotNull(tributary);
        assertEquals(stem.outletId(), tributary.outletId());
        assertTrue(stem.sourceNodeId().getAsLong() != tributary.sourceNodeId().getAsLong());

        HydrologyPoint confluence = tributary.segments().getLast().end();
        Integer stemHead = null;
        for (HydraulicSegment segment : stem.segments()) {
            for (HydrologyPoint point : segment.centerline()) {
                if (point.x() == confluence.x() && point.z() == confluence.z()) {
                    stemHead = point.y();
                }
            }
        }
        assertNotNull("tributary ends on the stem", stemHead);
        assertTrue(confluence.y() >= stemHead);
        assertTrue(stations(tributary) >= 192);
        assertTrue(stations(tributary) < stations(stem));
        for (HydraulicSegment segment : tributary.segments()) {
            assertTrue(segment.upstreamHeadY() >= segment.downstreamHeadY());
        }
    }

    @Test
    public void theApproachIntoTheStemNeverRisesWhereTheGroundHoldsAStationUp() {
        // A level tributary tail at 120 hangs twenty blocks above its stem. The ground beside the junction
        // falls to the stem, but a knoll a few stations back stands too high for the channel to cut it
        // down: the stations over the knoll keep their head, and every station upstream of them stays at
        // least as high, so the graded reach still descends into the stem instead of rising over the knoll.
        HydrologyTerrainSampler terrain = (x, z) -> land(x >= 14 && x <= 17 ? 130 : x >= 18 ? 100 + (20 - x) * 4 : 121);
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        HydrologyPlanner planner = new HydrologyPlanner(19L, settings, terrain);
        ArrayList<HydrologyPoint> centerline = new ArrayList<>();
        for (int x = 0; x <= 20; x++) {
            centerline.add(new HydrologyPoint(x, 120, 0));
        }
        ArrayList<HydraulicSegment> segments = new ArrayList<>(List.of(new HydraulicSegment(
                1L, 7L, HydrologyFeatureType.SURFACE_POOL, 120, 120, 4, 2, false, false, List.copyOf(centerline))));

        planner.levelApproach(segments, 100);

        List<HydrologyPoint> graded = new ArrayList<>();
        int previousHead = Integer.MAX_VALUE;
        for (HydraulicSegment segment : segments) {
            assertTrue(segment.upstreamHeadY() <= previousHead);
            assertTrue(segment.upstreamHeadY() >= segment.downstreamHeadY());
            previousHead = segment.downstreamHeadY();
            graded.addAll(segment.centerline());
        }
        assertEquals(21, graded.size());
        assertEquals(100, graded.getLast().y());
        int previous = Integer.MAX_VALUE;
        for (HydrologyPoint point : graded) {
            assertTrue("head rises downstream at x=" + point.x(), point.y() <= previous);
            assertTrue("cut below the incision floor at x=" + point.x(),
                    point.y() >= terrain.sample(point.x(), point.z()).naturalHeight() - settings.surface().maximumIncision());
            previous = point.y();
        }
        assertEquals(120, graded.get(14).y());
        assertEquals(120, graded.get(17).y());
        assertTrue(graded.get(18).y() < 120);
    }

    @Test
    public void aLevelReachWithABumpStaysGradedOnceItsTailIsCutDown() {
        // A pool reach may carry stations above its heads. Cutting its tail down to the stem turns it
        // into a graded transition, which must descend monotonically: the bump is held at the head.
        HydrologyTerrainSampler terrain = (x, z) -> land(121);
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        HydrologyPlanner planner = new HydrologyPlanner(23L, settings, terrain);
        ArrayList<HydrologyPoint> centerline = new ArrayList<>();
        for (int x = 0; x <= 12; x++) {
            centerline.add(new HydrologyPoint(x, x >= 4 && x <= 6 ? 122 : 120, 0));
        }
        ArrayList<HydraulicSegment> segments = new ArrayList<>(List.of(new HydraulicSegment(
                1L, 7L, HydrologyFeatureType.SURFACE_POOL, 120, 120, 4, 2, false, false, List.copyOf(centerline))));

        planner.levelApproach(segments, 112);

        List<HydrologyPoint> graded = segments.getFirst().centerline();
        assertEquals(13, graded.size());
        assertEquals(112, graded.getLast().y());
        int previous = Integer.MAX_VALUE;
        for (HydrologyPoint point : graded) {
            assertTrue("head rises downstream at x=" + point.x(), point.y() <= previous);
            previous = point.y();
        }
        assertTrue("bump above the head at x=5", graded.get(5).y() <= 120);
        assertTrue(graded.getFirst().y() <= 120);
        assertTrue(segments.getFirst().upstreamHeadY() >= segments.getFirst().downstreamHeadY());
    }

    private static HydrologyTerrainSample land(int height) {
        return new HydrologyTerrainSample(
                height,
                0D,
                false,
                false,
                height - 40,
                height - 38,
                true,
                true,
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
                "land",
                "land",
                "land",
                "land",
                "land",
                "land",
                List.of("default"),
                List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true
        );
    }

    private static int stations(RiverCourse course) {
        int stations = 0;
        for (HydraulicSegment segment : course.segments()) {
            stations += segment.centerline().size();
        }
        return stations;
    }

    private static List<RiverCourse> surface(HydrologyTile tile) {
        ArrayList<RiverCourse> courses = new ArrayList<>();
        for (RiverCourse course : tile.courses()) {
            if (course.type() == RiverCourseType.SURFACE) {
                courses.add(course);
            }
        }
        return courses;
    }

    /** A plain sloping east into the sea with a valley along z = 0 that both flanks drain into. */
    private static HydrologyTerrainSample valley(int x, int z) {
        if (x >= 900) {
            return HydrologyTerrainSample.ocean(50, "sea");
        }
        // The valley floor wanders so the stem never runs straight along the lattice, and the eastward
        // fall is steeper than the valley flanks so routes drift into the valley at a shallow angle.
        int valleyCenter = (int) Math.round(Math.sin(x / 150D) * 40D);
        int valley = Math.max(0, 12 - Math.abs(z - valleyCenter) / 20);
        int wave = (int) Math.round(Math.sin(x / 29D + z / 43D) * 3D + Math.sin(z / 19D - x / 61D + 1D) * 2D);
        int height = 240 - x / 5 - valley + wave;
        boolean source = x >= 32 && x <= 96 && (Math.abs(z - 300) <= 40 || Math.abs(z - 150) <= 40);
        return new HydrologyTerrainSample(
                height,
                0D,
                false,
                false,
                height - 40,
                height - 38,
                true,
                true,
                source,
                false,
                false,
                false,
                0D,
                source ? 1D : 0D,
                0D,
                1D,
                1D,
                1D,
                1D,
                1D,
                "land",
                "land",
                "land",
                "land",
                "land",
                "land",
                List.of("default"),
                List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true
        );
    }

    private static HydrologyPlannerSettings settings(int tributaries) {
        return settings(tributaries, 4D, 4);
    }

    private static HydrologyPlannerSettings settings(int tributaries, double density, int maximumPerTile) {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Routing routing = new HydrologyPlannerSettings.Routing(
                1024, 64, base.routing().maximumRouteNodes(), 4096, 384, 192, 1.5D, 24D, 2D, 0.2D, 1D, tributaries);
        HydrologyPlannerSettings.Source sources = new HydrologyPlannerSettings.Source(true, density, 0, 0, maximumPerTile, 128);
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(
                true, sources, 4, 8, 2, 4, 40, 1.5D, HydrologyPlannerSettings.Banks.defaults());
        HydrologyPlannerSettings.Underground underground = HydrologyPlannerSettings.Underground.of(
                false, base.underground().sources(), -48, 72, 3, 8, 1, 3, 6, 14, true, 0);
        HydrologyPlannerSettings.Outlets outlets = HydrologyPlannerSettings.Outlets.of(
                true,
                base.outlets().coastalGrotto(),
                base.outlets().inlandGrotto(),
                false,
                base.outlets().coastalCliffMinimumHeight(),
                base.outlets().mouthLevelingDistance(),
                base.outlets().maximumOceanApron(),
                1,
                1
        );
        return new HydrologyPlannerSettings(
                base.seaLevel(),
                routing,
                surface,
                base.hydraulics(),
                underground,
                outlets,
                base.geometry(),
                List.of(),
                List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }
    @Test
    public void aTributaryOwnsOnlyTheDrainageUpstreamOfItsJunctionAndMeetsTheStemWater() {
        HydrologyTile split = SPLIT_TILE;

        List<RiverCourse> courses = surface(split);
        assertEquals(split.diagnosticCandidates().toString(), 2, courses.size());
        RiverCourse stem = stations(courses.get(0)) >= stations(courses.get(1)) ? courses.get(0) : courses.get(1);
        RiverCourse tributary = stem == courses.get(0) ? courses.get(1) : courses.get(0);
        java.util.Set<Long> stemEdges = new java.util.HashSet<>();
        for (DrainageEdge edge : stem.drainageEdges()) {
            stemEdges.add(edge.id());
        }
        assertFalse(tributary.drainageEdges().isEmpty());
        for (DrainageEdge edge : tributary.drainageEdges()) {
            assertFalse("tributary shares stem drainage " + edge.id(), stemEdges.contains(edge.id()));
        }
        HydrologyPoint confluence = tributary.segments().getLast().end();
        HydrologyPoint stemEnd = stem.segments().getLast().end();
        assertTrue("junction sits at the stem's mouth", confluence.x() != stemEnd.x() || confluence.z() != stemEnd.z());
        Integer stemHead = null;
        for (HydraulicSegment segment : stem.segments()) {
            for (HydrologyPoint point : segment.centerline()) {
                if (point.x() == confluence.x() && point.z() == confluence.z()) {
                    stemHead = point.y();
                }
            }
        }
        assertNotNull("tributary ends on the stem", stemHead);
        assertEquals(stemHead.intValue(), tributary.segments().getLast().downstreamHeadY());
        for (HydrologyDiagnosticCandidate candidate : split.diagnosticCandidates()) {
            assertTrue(candidate.toString(), candidate.kind() != HydrologyCandidateKind.TRIBUTARY);
        }
    }

    @Test
    public void tributariesAreBudgetedOnTopOfTheSourceDensity() {
        HydrologyTerrainSampler terrain = HydrologyPlannerTributaryTest::valley;

        HydrologyTile single = new HydrologyPlanner(19L, settings(0, 1D, 1), terrain).plan(TILE);
        HydrologyTile joined = new HydrologyPlanner(19L, settings(1, 1D, 1), terrain).plan(TILE);

        assertEquals(single.diagnosticCandidates().toString(), 1, surface(single).size());
        assertEquals(joined.diagnosticCandidates().toString(), 2, surface(joined).size());
    }

    @Test
    public void undergroundSourcesJoinTheirStemWhereTheirPassagesMeet() {
        HydrologyTerrainSampler terrain = HydrologyPlannerTributaryTest::cavernousCoast;

        HydrologyTile single = new HydrologyPlanner(23L, undergroundSettings(0), terrain).plan(TILE);
        HydrologyTile joined = new HydrologyPlanner(23L, undergroundSettings(1), terrain).plan(TILE);

        assertEquals(single.diagnosticCandidates().toString(), 1, underground(single).size());
        List<RiverCourse> courses = underground(joined);
        assertEquals(joined.diagnosticCandidates().toString(), 2, courses.size());
        RiverCourse stem = stations(courses.get(0)) >= stations(courses.get(1)) ? courses.get(0) : courses.get(1);
        RiverCourse tributary = stem == courses.get(0) ? courses.get(1) : courses.get(0);
        assertEquals(stem.outletId(), tributary.outletId());
        HydrologyPoint junction = tributary.segments().getLast().end();
        Integer stemHead = null;
        for (HydraulicSegment segment : stem.segments()) {
            for (HydrologyPoint point : segment.centerline()) {
                if (point.x() == junction.x() && point.z() == junction.z()) {
                    stemHead = point.y();
                }
            }
        }
        assertNotNull("underground tributary ends on its stem", stemHead);
        assertTrue(tributary.segments().getLast().downstreamHeadY() >= stemHead);
        java.util.Set<Long> stemEdges = new java.util.HashSet<>();
        for (DrainageEdge edge : stem.drainageEdges()) {
            stemEdges.add(edge.id());
        }
        for (DrainageEdge edge : tributary.drainageEdges()) {
            assertFalse(stemEdges.contains(edge.id()));
        }
        for (HydraulicSegment segment : tributary.segments()) {
            assertTrue(segment.upstreamHeadY() >= segment.downstreamHeadY());
        }
    }

    @Test
    public void undergroundTributariesAreBounded() {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Underground.of(
                true, base.underground().sources(), -48, 72, 3, 8, 1, 3, 6, 14, true, 5));
    }

    private static List<RiverCourse> underground(HydrologyTile tile) {
        List<RiverCourse> courses = new java.util.ArrayList<>();
        for (RiverCourse course : tile.courses()) {
            if (course.type() == RiverCourseType.UNDERGROUND) {
                courses.add(course);
            }
        }
        return courses;
    }

    /** A small coast with caves: two underground source zones at the west edge drain east into one sea outlet. */
    private static HydrologyTerrainSample cavernousCoast(int x, int z) {
        if (x >= 112) {
            return new HydrologyTerrainSample(
                    54, 0D, true, false, 30, 32, false, false, false, false, false, false,
                    0D, 0D, 0D, 1D, 1D, 1D, 1D, 1D,
                    "ocean_parent", "surface", "mouth", "shore", "dry", "flooded",
                    List.of("default"), List.of(), Double.NaN, null, Double.NaN, true);
        }
        int height = 118 - Math.floorDiv(x, 12) + (int) StrictMath.round(StrictMath.sin(z / 18D) * 2D);
        boolean source = x >= 0 && x <= 24 && (z >= 8 && z <= 40 || z >= 72 && z <= 104);
        return new HydrologyTerrainSample(
                height, 1D, false, true, 70, 74, true, true, false, false, source, false,
                0D, 0D, source ? 1D : 0D, 1D, 1D, 1D, 1D, 1D,
                "parent", "surface", "mouth", "shore", "dry", "flooded",
                List.of("default"), List.of(), Double.NaN, null, Double.NaN, true);
    }

    private static HydrologyPlannerSettings undergroundSettings(int tributaries) {
        HydrologyPlannerSettings.Source none = new HydrologyPlannerSettings.Source(false, 0D, 80, 0, 0, 24);
        HydrologyPlannerSettings.Source sources = new HydrologyPlannerSettings.Source(true, 1D, Integer.MIN_VALUE, 0, 1, 32);
        HydrologyPlannerSettings.ChannelShape stableChannel = HydrologyPlannerSettings.ChannelShape.of(2D, 0D, 0D, 11);
        return new HydrologyPlannerSettings(
                63,
                new HydrologyPlannerSettings.Routing(128, 16, 512, 256, 0, 0, 0.5D, 12D, 0.5D, 0.1D, 1D, 0),
                new HydrologyPlannerSettings.Surface(false, none, 4, 18, 2, 4, 10, 1.5D, HydrologyPlannerSettings.Banks.defaults()),
                new HydrologyPlannerSettings.Hydraulics(4),
                HydrologyPlannerSettings.Underground.of(true, sources, 68, 82, 4, 12, 2, 4, 5, 9, true, tributaries),
                HydrologyPlannerSettings.Outlets.of(
                        true,
                        new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                        new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                        false,
                        12,
                        32,
                        2,
                        1,
                        1
                ),
                new HydrologyPlannerSettings.Geometry(
                        new HydrologyPlannerSettings.Meanders(224, 72, 0D, 0D, 0D, 0, 75D),
                        stableChannel,
                        stableChannel,
                        stableChannel,
                        HydrologyPlannerSettings.Geometry.defaults().drops()
                ),
                List.of(),
                List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }

    private static final class CountingNaturalSampler implements HydrologyNaturalTerrainSampler {
        private final HydrologyTerrainSampler terrain;
        private final boolean omitUnusedSlope;
        private int slopeCalls;
        private int heightOnlyCalls;

        private CountingNaturalSampler(HydrologyTerrainSampler terrain, boolean omitUnusedSlope) {
            this.terrain = terrain;
            this.omitUnusedSlope = omitUnusedSlope;
        }

        @Override
        public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
            HydrologyTerrainSample[] samples = new HydrologyTerrainSample[request.width() * request.width()];
            for (int gridZ = 0; gridZ < request.width(); gridZ++) {
                for (int gridX = 0; gridX < request.width(); gridX++) {
                    int x = request.minimumX() + gridX * request.spacing();
                    int z = request.minimumZ() + gridZ * request.spacing();
                    samples[gridZ * request.width() + gridX] = terrain.sample(x, z);
                }
            }
            return samples;
        }

        @Override
        public NaturalClassification classifyNatural(int blockX, int blockZ) {
            return terrain.sample(blockX, blockZ).ocean() ? NaturalClassification.OCEAN : NaturalClassification.LAND;
        }

        @Override
        public HydrologyTerrainSample sampleBasis(int blockX, int blockZ) {
            slopeCalls++;
            return terrain.sample(blockX, blockZ);
        }

        @Override
        public HydrologyTerrainSample sampleBasisWithoutSlope(int blockX, int blockZ) {
            if (!omitUnusedSlope) {
                return sampleBasis(blockX, blockZ);
            }
            heightOnlyCalls++;
            return terrain.sample(blockX, blockZ).withSlope(0D);
        }
    }
}
