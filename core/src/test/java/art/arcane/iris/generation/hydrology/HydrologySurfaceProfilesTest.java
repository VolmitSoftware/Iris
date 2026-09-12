package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;
import art.arcane.iris.generation.hydrology.surface.SurfaceFootprint;
import art.arcane.iris.generation.hydrology.surface.SurfaceLayerColumn;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HydrologySurfaceProfilesTest {
    @Test
    public void volcanicParentAndChildShareTheirExclusiveProfile() {
        HydrologyTerrainSample plains = sample(List.of("volcanic_lava"), false, "volcanic-plains");
        HydrologyTerrainSample volcano = sample(List.of("volcanic_lava"), false, "volcanoes");

        assertTrue(HydrologySurfaceProfiles.sharesProfile(plains, volcano));
        assertTrue(HydrologySurfaceProfiles.sharesProfile(volcano, plains));
        assertFalse(HydrologySurfaceProfiles.sharesProfile(plains, sample(List.of("water"), false, "land")));
        assertFalse(HydrologySurfaceProfiles.sharesProfile(plains, sample(List.of("lava"), false, "land")));
        assertFalse(HydrologySurfaceProfiles.sharesProfile(plains, sample(List.of("default"), false, "land")));
        assertFalse(HydrologySurfaceProfiles.sharesProfile(plains, null));
    }

    @Test
    public void unchangedPermissionsKeepTheExistingDeterministicProfileChoice() {
        HydrologyTerrainSample terrain = sample(List.of("first", "second"), false, "land");
        HydrologyPlanner planner = planner((x, z) -> terrain);
        HydrologyCoursePath path = inlandPath(List.of(point(0), point(16)));

        assertEquals(planner.segments.chooseProfile(terrain, path.outlet().id()),
                HydrologySurfaceProfiles.chooseProfile(planner, path, terrain));
    }

    @Test
    public void sourcePermissionsRemainEffectiveWhenItsAnchorMoves() {
        HydrologyTerrainSample anchor = sample(List.of("volcanic_lava", "water"), false, "land");
        HydrologyTerrainSample source = sample(List.of("volcanic_lava"), false, "volcanoes");
        HydrologyPlanner planner = planner((x, z) -> anchor);

        assertEquals("volcanic_lava", HydrologySurfaceProfiles.chooseProfile(
                planner, inlandPath(List.of(point(0), point(16))), source));
    }

    @Test
    public void profileIntersectionCoversTheWholeRoute() {
        HydrologyTerrainSample first = sample(List.of("a", "b"), false, "first");
        HydrologyTerrainSample middle = sample(List.of("b", "c"), false, "middle");
        HydrologyTerrainSample last = sample(List.of("a", "c"), false, "last");
        HydrologyPlanner planner = planner((x, z) -> x < 4 ? first : x < 8 ? middle : last);

        assertTrue(HydrologySurfaceProfiles.sharesProfile(first, middle));
        assertTrue(HydrologySurfaceProfiles.sharesProfile(middle, last));
        assertNull(HydrologySurfaceProfiles.chooseProfile(
                planner, inlandPath(List.of(point(0), point(12))), first));
    }

    @Test
    public void anExcludedColumnBetweenRouteStationsRejectsTheCrossing() {
        HydrologyTerrainSample water = sample(List.of("water"), false, "land");
        HydrologyTerrainSample volcanic = sample(List.of("volcanic_lava"), false, "volcanoes");
        HydrologyPlanner planner = planner((x, z) -> x == 7 ? volcanic : water);
        HydrologyCoursePath path = inlandPath(List.of(point(0), point(16)));

        assertNull(HydrologySurfaceProfiles.chooseProfile(planner, path, water));
        assertFalse(HydrologySurfaceProfiles.allowsProfile(planner, path, "water"));
    }

    @Test
    public void directOceanOutletsMustPermitTheChosenProfile() {
        HydrologyTerrainSample volcanic = sample(List.of("volcanic_lava"), false, "volcanoes");
        HydrologyPlanner planner = planner((x, z) -> x >= 17
                ? sample(List.of("water"), true, "ocean") : volcanic);
        List<HydrologyPoint> points = List.of(point(0), point(16));
        RiverOutlet outlet = new RiverOutlet(99L, HydrologyFeatureType.MOUTH, 1L,
                point(16), new HydrologyPoint(17, 63, 0), 63, true);
        HydrologyCoursePath path = new HydrologyCoursePath(points, List.of(), List.of(), outlet, true, false, null);

        assertNull(HydrologySurfaceProfiles.chooseProfile(planner, path, volcanic));
    }

    @Test
    public void oceanNeighborSelectionCanSkipAnIncompatibleOcean() {
        HydrologyTerrainSample land = sample(List.of("water"), false, "land");
        HydrologyGridNode source = node(0, 0, 0, land);
        HydrologyGridNode incompatible = node(1, 1, 0, sample(List.of("volcanic_lava"), true, "ocean"));
        HydrologyGridNode compatible = node(2, 0, 1, sample(List.of("water"), true, "ocean"));
        HydrologySampledGrid grid = new HydrologySampledGrid(0, 0, 0, 0, 32, 2, 16,
                List.of(source, incompatible, compatible, node(3, 1, 1, land)));
        HydrologyPlanner planner = planner((x, z) -> land);

        assertEquals(compatible, planner.outletPlanner.firstOceanNeighbor(grid, source, true));
        assertEquals(incompatible, planner.outletPlanner.firstOceanNeighbor(grid, source, false));
    }

    @Test
    public void refinedCoastProfilesRejectSurfaceMouthsWithoutChangingUndergroundAdmission() {
        HydrologyTerrainSample land = sample(List.of("water"), false, "land");
        HydrologyTerrainSample coarseOcean = sample(List.of("water"), true, "ocean");
        HydrologySampledGrid grid = new HydrologySampledGrid(0, 0, 0, 0, 32, 2, 16, List.of(
                node(0, 0, 0, land), node(1, 1, 0, coarseOcean),
                node(2, 0, 1, land), node(3, 1, 1, coarseOcean)));
        HydrologyPlanner planner = planner((x, z) -> x >= 8
                ? sample(List.of("volcanic_lava"), true, "ocean") : land);

        assertTrue(planner.outletPlanner.oceanOutletCandidates(grid, true).isEmpty());
        assertFalse(planner.outletPlanner.oceanOutletCandidates(grid, false).isEmpty());
    }

    @Test
    public void aTributaryUsesThePermittedStemProfileAtTheJoin() {
        HydrologyTerrainSample terrain = sample(List.of("volcanic_lava", "water"), false, "land");
        HydrologyPlanner planner = planner((x, z) -> terrain);
        SurfaceCourseDraft draft = draft(terrain);
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        RiverCourse tributary = planner.tributaries.buildSurfaceTributary(draft, stem("volcanic_lava"), diagnostics);

        assertNotNull(diagnostics.toString(), tributary);
        assertEquals("volcanic_lava", tributary.profileKey());
        assertEquals(256, tributary.segments().getLast().end().x());
    }

    @Test
    public void aJoinCannotReplaceTheSourcesExclusiveProfile() {
        HydrologyTerrainSample terrain = sample(List.of("volcanic_lava", "water"), false, "land");
        HydrologyPlanner planner = planner((x, z) -> terrain);
        SurfaceCourseDraft draft = draft(sample(List.of("water"), false, "source"));
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        assertNull(planner.tributaries.buildSurfaceTributary(draft, stem("volcanic_lava"), diagnostics));
        assertEquals(HydrologyCandidateRejection.NO_DRAINAGE_PATH, diagnostics.getFirst().rejection());
    }

    @Test
    public void wetChannelWidthCannotEscapeThePermittedProfile() {
        HydrologyTerrainSample volcanic = sample(List.of("volcanic_lava"), false, "volcanoes");
        HydrologyTerrainSample ambient = sample(List.of("water"), false, "land");
        HydrologyTerrainSampler sampler = (x, z) -> z == 0 ? volcanic : ambient;
        HydrologyFootprintCompiler compiler = new HydrologyFootprintCompiler(HydrologyPlannerSettings.defaults(), sampler);
        ArrayList<RiverCourse> courses = new ArrayList<>(List.of(stem("volcanic_lava")));
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologySurfaceProfiles.rejectExcludedWetFootprints(compiler, courses, diagnostics);

        assertTrue(courses.isEmpty());
        assertEquals(HydrologyCandidateRejection.POLICY_EXCLUDED, diagnostics.getFirst().rejection());
        assertTrue(diagnostics.getFirst().detail() > 0);
    }

    @Test
    public void dryValleyBanksMayExtendBeyondThePermittedProfile() {
        HydrologyTerrainSample volcanic = sample(List.of("volcanic_lava"), false, "volcanoes");
        HydrologyTerrainSample ambient = sample(List.of("water"), false, "land");
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        RiverCourse course = stem("volcanic_lava");
        SurfaceFootprint reference = new HydrologyFootprintCompiler(settings, (x, z) -> volcanic).surfaceFootprint(course);
        int wetRadius = 0;
        for (SurfaceLayerColumn column : reference.columns()) {
            if (column.layer().fluidOwned() && column.layer().bedY() < column.layer().fluidHeadY()) {
                wetRadius = Math.max(wetRadius, Math.abs(column.z()));
            }
        }
        int permittedRadius = wetRadius;
        HydrologyTerrainSampler sampler = (x, z) -> Math.abs(z) <= permittedRadius ? volcanic : ambient;
        HydrologyFootprintCompiler compiler = new HydrologyFootprintCompiler(settings, sampler);
        assertTrue(compiler.surfaceFootprint(course).columns().stream()
                .anyMatch(column -> !column.layer().fluidOwned() && column.terrain().equals(ambient)));
        ArrayList<RiverCourse> courses = new ArrayList<>(List.of(course));
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologySurfaceProfiles.rejectExcludedWetFootprints(compiler, courses, diagnostics);

        assertEquals(List.of(course), courses);
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    public void rejectingTheMainStemAlsoRemovesItsDependentTributary() {
        HydrologyTerrainSample volcanic = sample(List.of("volcanic_lava"), false, "volcanoes");
        HydrologyTerrainSample ambient = sample(List.of("water"), false, "land");
        HydrologyTerrainSampler sampler = (x, z) -> x < 600 && z != 0 ? ambient : volcanic;
        RiverCourse main = new RiverCourse(30L, RiverCourseType.SURFACE,
                OptionalLong.of(1L), OptionalLong.of(99L), "volcanic_lava", 1, List.of(), List.of(
                new HydraulicSegment(31L, 30L, HydrologyFeatureType.MOUTH,
                        84, 84, 4, 2, false, false, List.of(point(256), point(512)), HydraulicChannelProfile.uniform(4, 2))));
        RiverCourse tributary = new RiverCourse(40L, RiverCourseType.SURFACE,
                OptionalLong.of(2L), OptionalLong.of(99L), "volcanic_lava", 1, List.of(), List.of(
                new HydraulicSegment(41L, 40L, HydrologyFeatureType.SURFACE_POOL,
                        84, 84, 4, 2, false, false, List.of(point(800), point(1024)), HydraulicChannelProfile.uniform(4, 2))));
        ArrayList<RiverCourse> courses = new ArrayList<>(List.of(tributary, main));
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologySurfaceProfiles.rejectExcludedWetFootprints(
                new HydrologyFootprintCompiler(HydrologyPlannerSettings.defaults(), sampler), courses, diagnostics);

        assertTrue(courses.isEmpty());
        assertEquals(2, diagnostics.size());
        assertTrue(diagnostics.stream().anyMatch(candidate -> candidate.rejection() == HydrologyCandidateRejection.NO_DRAINAGE_PATH));
    }

    @Test
    public void independentStandingPoolsKeepTheirOwnProfilePolicy() {
        RiverCourse stem = stem("volcanic_pool");
        RiverCourse pool = new RiverCourse(stem.id(), RiverCourseType.SURFACE_POOL,
                OptionalLong.empty(), OptionalLong.empty(), stem.profileKey(), stem.discharge(),
                List.of(), stem.segments());
        HydrologyTerrainSampler sampler = (x, z) -> sample(List.of("water"), false, "land");
        ArrayList<RiverCourse> courses = new ArrayList<>(List.of(pool));
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();

        HydrologySurfaceProfiles.rejectExcludedWetFootprints(
                new HydrologyFootprintCompiler(HydrologyPlannerSettings.defaults(), sampler), courses, diagnostics);

        assertEquals(List.of(pool), courses);
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    public void surfaceOutletNormalizationDoesNotAdoptAnUndergroundFluidsProfile() {
        HydrologyPoint lip = point(256);
        HydrologyPoint pool = new HydrologyPoint(256, 70, 0);
        RiverCourse surface = new RiverCourse(30L, RiverCourseType.SURFACE,
                OptionalLong.of(1L), OptionalLong.of(99L), "volcanic_lava", 1, List.of(), List.of(
                new HydraulicSegment(31L, 30L, HydrologyFeatureType.SURFACE_POOL,
                        84, 84, 4, 2, false, false, List.of(point(0), lip), HydraulicChannelProfile.uniform(4, 2)),
                new HydraulicSegment(32L, 30L, HydrologyFeatureType.SINKHOLE,
                        84, 70, 4, 2, true, true, List.of(lip, pool), HydraulicChannelProfile.uniform(4, 2)),
                new HydraulicSegment(33L, 30L, HydrologyFeatureType.INLAND_GROTTO,
                        70, 70, 4, 2, false, true, List.of(pool), HydraulicChannelProfile.uniform(4, 2))));
        RiverCourse underground = new RiverCourse(40L, RiverCourseType.UNDERGROUND,
                OptionalLong.of(2L), OptionalLong.of(99L), "water", 1, List.of(), List.of(
                new HydraulicSegment(41L, 40L, HydrologyFeatureType.INLAND_GROTTO,
                        70, 70, 4, 2, false, true, List.of(pool), HydraulicChannelProfile.uniform(4, 2))));
        HydrologyPlanner planner = planner((x, z) -> sample(List.of("volcanic_lava"), false, "volcanoes"));

        assertEquals(List.of(surface, underground),
                planner.tributaries.normalizeOutletContinuations(List.of(surface, underground)));
    }

    private static HydrologyPlanner planner(HydrologyTerrainSampler sampler) {
        return new HydrologyPlanner(19L, HydrologyPlannerSettings.defaults(), sampler);
    }

    private static HydrologyTerrainSample sample(List<String> profiles, boolean ocean, String biome) {
        int height = ocean ? 60 : 84;
        return new HydrologyTerrainSample(
                height, 0D, ocean, false, height - 32, height - 30,
                !ocean, !ocean, !ocean, false, false, false,
                0D, ocean ? 0D : 1D, 0D, 1D, 1D, 1D, 1D, 1D,
                biome, biome, biome, biome, biome, biome,
                profiles, List.of(), Double.NaN, null, Double.NaN, true,
                SurfaceRiverPolicy.INHERIT
        );
    }

    private static HydrologyPoint point(int x) {
        return new HydrologyPoint(x, 84, 0);
    }

    private static HydrologyGridNode node(int index, int gridX, int gridZ, HydrologyTerrainSample terrain) {
        return new HydrologyGridNode(index, gridX, gridZ, gridX * 16, gridZ * 16, index, terrain);
    }

    private static HydrologyCoursePath inlandPath(List<HydrologyPoint> points) {
        RiverOutlet outlet = new RiverOutlet(99L, HydrologyFeatureType.INLAND_GROTTO, 1L,
                points.getLast(), points.getLast(), 63, false);
        ArrayList<DrainageEdge> edges = new ArrayList<>();
        for (int index = 1; index < points.size(); index++) {
            edges.add(new DrainageEdge(index, index, index + 1, outlet.id(), 1D, 1, 0,
                    List.of(points.get(index - 1), points.get(index))));
        }
        return new HydrologyCoursePath(points, edges, edges, outlet, true, false, null);
    }

    private static SurfaceCourseDraft draft(HydrologyTerrainSample terrain) {
        return new SurfaceCourseDraft(node(1, 0, 0, terrain), 20L, "water",
                inlandPath(List.of(point(0), point(128), point(256), point(512))));
    }

    private static RiverCourse stem(String profile) {
        HydraulicSegment segment = new HydraulicSegment(31L, 30L, HydrologyFeatureType.SURFACE_POOL,
                84, 84, 4, 2, false, false, List.of(point(256), point(512)), HydraulicChannelProfile.uniform(4, 2));
        return new RiverCourse(30L, RiverCourseType.SURFACE, OptionalLong.of(2L), OptionalLong.of(99L),
                profile, 1, List.of(), List.of(segment));
    }
}
