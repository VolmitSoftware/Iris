package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;

import art.arcane.iris.generation.hydrology.cave.CavePosition;
import art.arcane.iris.generation.hydrology.cave.CaveVoxel;
import art.arcane.iris.generation.hydrology.cave.CaveVoxelPrecondition;
import art.arcane.iris.generation.hydrology.cave.CaveVoxelView;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveConflictPolicy;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class HydrologyPlannerCrossTileCaveAdmissionIntegrationTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final long SEED = 77L;
    private static final List<TilePair> ADJACENT_PAIRS = List.of(
            new TilePair(new HydrologyTileKey(0, -2), new HydrologyTileKey(0, -1)),
            new TilePair(new HydrologyTileKey(0, 0), new HydrologyTileKey(0, 1))
    );

    @Test
    public void seed77AdjacentTilesRejectIncompatibleCaveOverlapsRegardlessOfRequestOrder() {
        List<HydrologyTileKey> forwardOrder = List.of(
                ADJACENT_PAIRS.get(0).first(),
                ADJACENT_PAIRS.get(0).second(),
                ADJACENT_PAIRS.get(1).first(),
                ADJACENT_PAIRS.get(1).second()
        );
        ArrayList<HydrologyTileKey> reverseOrder = new ArrayList<>(forwardOrder);
        Collections.reverse(reverseOrder);

        Map<HydrologyTileKey, HydrologyTile> forward = plans(planner(), forwardOrder);
        Map<HydrologyTileKey, HydrologyTile> reverse = plans(planner(), reverseOrder);

        assertEquals(forward, reverse);
        for (TilePair pair : ADJACENT_PAIRS) {
            assertNoIncompatibleCaveOverlaps(
                    pair,
                    forward.get(pair.first()),
                    forward.get(pair.second())
            );
        }
    }

    @Test
    public void clusteredRequiredSourcesResolveToStableNonOverlappingNetworksRegardlessOfRequestOrder() {
        TilePair pair = ADJACENT_PAIRS.getFirst();
        List<HydrologyTileKey> forwardOrder = List.of(pair.first(), pair.second());
        ArrayList<HydrologyTileKey> reverseOrder = new ArrayList<>(forwardOrder);
        Collections.reverse(reverseOrder);

        Map<HydrologyTileKey, HydrologyTile> forward = plans(requiredFallbackPlanner(), forwardOrder);
        Map<HydrologyTileKey, HydrologyTile> reverse = plans(requiredFallbackPlanner(), reverseOrder);

        assertEquals(forward, reverse);
        List<HydrologyPoint> firstSources = sourcePoints(forward.get(pair.first()), RiverCourseType.SURFACE);
        List<HydrologyPoint> secondSources = sourcePoints(forward.get(pair.second()), RiverCourseType.SURFACE);
        assertEquals(forward.get(pair.first()).diagnosticCandidates().toString(), 1, firstSources.size());
        assertEquals(forward.get(pair.second()).diagnosticCandidates().toString(), 1, secondSources.size());
        assertPointNear(new HydrologyPoint(32, 121, -272), firstSources.getFirst(), 8);
        // The second tile keeps the mouth on its own coast, so its heaviest required source no longer
        // competes with the first tile's network and is accepted ahead of the lighter fallback source.
        assertPointNear(new HydrologyPoint(16, 126, -224), secondSources.getFirst(), 8);
        assertNoIncompatibleCaveOverlaps(pair, forward.get(pair.first()), forward.get(pair.second()));
    }

    @Test
    public void boundPlatformResolvesNeighbourOwnersInParallelToTheSameTiles() {
        List<HydrologyTileKey> corner = List.of(
                new HydrologyTileKey(-1, -1),
                new HydrologyTileKey(0, -1),
                new HydrologyTileKey(-1, 0),
                new HydrologyTileKey(0, 0)
        );
        Map<HydrologyTileKey, HydrologyTile> lazy = plans(planner(), corner);

        IrisSettings previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisPlatforms.unbind();
        IrisPlatforms.bind(mock(IrisPlatform.class));
        Map<HydrologyTileKey, HydrologyTile> parallel;
        try {
            parallel = plans(planner(), corner);
        } finally {
            IrisPlatforms.unbind();
            IrisSettings.settings = previousSettings;
        }

        assertEquals(lazy, parallel);
    }

    @Test
    public void colorRankBoundsTheCompleteOwnerDependencyGeometry() {
        HydrologyPlanner planner = planner();

        assertEquals(1, planner.maximumCrossTileDependencyOwners(new HydrologyTileKey(0, 0)));
        // The mouth flare reaches maximumWidth * flareRatio / 2 beside the coast, which pushes these
        // 256-block tiles to a three-colour period: rank 8 at (-1, -1), two tiles of offset per rank.
        assertEquals(3, settings().crossTileColorPeriod());
        assertEquals(1089, planner.maximumCrossTileDependencyOwners(new HydrologyTileKey(-1, -1)));
    }

    @Test
    public void acceptedCavePlansMatchTheirMaterializedSurfaceAfterCrossTileAdmission() {
        HydrologyTerrainSampler terrain = terrain();
        HydrologyPlanner planner = new HydrologyPlanner(
                SEED,
                settings(),
                terrain,
                -4096,
                (HydrologyCaveVoxelViewFactory.PlannedSurface surface) ->
                        new PlannedSurfaceCaveView(terrain, surface)
        );

        for (TilePair pair : ADJACENT_PAIRS) {
            assertMaterializedPreconditions(planner.plan(pair.first()), terrain);
            assertMaterializedPreconditions(planner.plan(pair.second()), terrain);
        }
    }

    private HydrologyPlanner planner() {
        return new HydrologyPlanner(SEED, settings(), terrain());
    }

    private HydrologyPlanner requiredFallbackPlanner() {
        HydrologyTerrainSampler terrain = requiredFallbackTerrain();
        return new HydrologyPlanner(
                SEED,
                requiredFallbackSettings(),
                terrain,
                -4096,
                (HydrologyCaveVoxelViewFactory.PlannedSurface surface) ->
                        new PlannedSurfaceCaveView(terrain, surface)
        );
    }

    private void assertMaterializedPreconditions(HydrologyTile tile, HydrologyTerrainSampler terrain) {
        HydrologyCaveVoxelViewFactory.PlannedSurface surface = new HydrologyCaveVoxelViewFactory.PlannedSurface() {
            @Override
            public int resolve(int x, int z, int naturalHeight) {
                return tile.footprint().sample(x, z).map(HydrologyColumnSample::terrainHeight).orElse(naturalHeight);
            }

            @Override
            public boolean ownsTerrain(int x, int z) {
                HydrologyColumnSample sample = tile.footprint().sample(x, z).orElse(null);
                return sample != null && sample.primarySurfaceLayer().isPresent();
            }
        };
        CaveVoxelView view = new PlannedSurfaceCaveView(terrain, surface);
        for (HydrologyCavePlan plan : tile.cavePlans()) {
            for (Map.Entry<CavePosition, CaveVoxelPrecondition> entry
                    : plan.baselinePreconditions().entrySet()) {
                CavePosition position = entry.getKey();
                CaveVoxelPrecondition expected = entry.getValue();
                assertEquals(expected.voxel(), view.voxelAt(position));
                assertEquals(expected.openToSurface(), view.isOpenToSurface(position));
            }
        }
    }

    private Map<HydrologyTileKey, HydrologyTile> plans(
            HydrologyPlanner planner,
            List<HydrologyTileKey> requestOrder
    ) {
        LinkedHashMap<HydrologyTileKey, HydrologyTile> plans = new LinkedHashMap<>();
        for (HydrologyTileKey key : requestOrder) {
            plans.put(key, planner.plan(key));
        }
        return Map.copyOf(plans);
    }

    private void assertNoIncompatibleCaveOverlaps(
            TilePair pair,
            HydrologyTile first,
            HydrologyTile second
    ) {
        Map<Long, String> profileKeys = profileKeys(first, second);
        int comparedPlanPairs = 0;
        for (HydrologyCavePlan firstPlan : first.cavePlans()) {
            if (!firstPlan.accepted()) {
                continue;
            }
            for (HydrologyCavePlan secondPlan : second.cavePlans()) {
                if (!secondPlan.accepted()
                        || firstPlan.source().sourceId() == secondPlan.source().sourceId()) {
                    continue;
                }
                comparedPlanPairs++;
                long firstCourseId = firstPlan.source().sourceId();
                long secondCourseId = secondPlan.source().sourceId();
                assertFalse(
                        "Incompatible seed 77 cave overlap between " + pair.first() + " course "
                                + firstCourseId + " and " + pair.second() + " course " + secondCourseId,
                        HydrologyCaveConflictPolicy.hasIncompatibleOverlap(
                                requiredProfileKey(profileKeys, firstCourseId),
                                firstPlan.actions(),
                                requiredProfileKey(profileKeys, secondCourseId),
                                secondPlan.actions()
                        )
                );
            }
        }
        assertTrue("Seed 77 tile pair must retain accepted cave plans " + pair, comparedPlanPairs > 0);
    }

    private Map<Long, String> profileKeys(HydrologyTile first, HydrologyTile second) {
        LinkedHashMap<Long, String> profileKeys = new LinkedHashMap<>();
        addProfileKeys(profileKeys, first);
        addProfileKeys(profileKeys, second);
        return Map.copyOf(profileKeys);
    }

    private void addProfileKeys(Map<Long, String> profileKeys, HydrologyTile tile) {
        for (RiverCourse course : tile.courses()) {
            String existing = profileKeys.putIfAbsent(course.id(), course.profileKey());
            if (existing != null) {
                assertEquals(existing, course.profileKey());
            }
        }
    }

    private String requiredProfileKey(Map<Long, String> profileKeys, long courseId) {
        String profileKey = profileKeys.get(courseId);
        assertTrue("Missing profile for cave course " + courseId, profileKey != null);
        return profileKey;
    }

    private List<HydrologyPoint> sourcePoints(HydrologyTile tile, RiverCourseType type) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (RiverCourse course : tile.courses()) {
            if (course.type() != type) {
                continue;
            }
            DrainageNode source = tile.node(course.sourceNodeId().orElseThrow()).orElseThrow();
            points.add(source.naturalPoint());
        }
        return List.copyOf(points);
    }

    private void assertPointNear(HydrologyPoint expected, HydrologyPoint actual, int radius) {
        assertTrue(expected + " vs " + actual, StrictMath.abs(expected.y() - actual.y()) <= 2);
        assertTrue(expected + " vs " + actual, actual.distanceSquared2D(expected) <= (long) radius * radius);
    }

    private HydrologyPlannerSettings settings() {
        HydrologyPlannerSettings.Source surfaceSources = new HydrologyPlannerSettings.Source(
                true,
                4D,
                80,
                1,
                6,
                32
        );
        HydrologyPlannerSettings.Source undergroundSources = new HydrologyPlannerSettings.Source(
                true,
                4D,
                Integer.MIN_VALUE,
                1,
                4,
                48
        );
        HydrologyPlannerSettings.DeepFluid deepFluid = new HydrologyPlannerSettings.DeepFluid(
                "deep_lava",
                true,
                3D,
                48,
                18,
                42,
                3,
                5,
                2,
                3,
                8,
                24,
                4,
                2,
                3,
                4096,
                4,
                true,
                true
        );
        return new HydrologyPlannerSettings(
                63,
                new HydrologyPlannerSettings.Routing(256, 16, 1_089, 256, 16, 8, 0.5D, 12D, 0.5D, 0.2D, 1D, 0, HydrologyPlannerSettings.Regional.disabled()),
                new HydrologyPlannerSettings.Surface(
                        true,
                        surfaceSources,
                        4,
                        20,
                        2,
                        5,
                        12,
                        1.5D,
                        HydrologyPlannerSettings.Banks.defaults()),
                new HydrologyPlannerSettings.Hydraulics(4),
                HydrologyPlannerSettings.Underground.of(
                        true,
                        undergroundSources,
                        68,
                        82,
                        4,
                        14,
                        2,
                        5,
                        6,
                        12,
                        true,
                        0
                ),
                HydrologyPlannerSettings.Outlets.of(
                        true,
                        new HydrologyPlannerSettings.Grotto(true, 4, 3, 3, 4096),
                        new HydrologyPlannerSettings.Grotto(true, 4, 3, 3, 4096),
                        true,
                        12,
                        48,
                        2,
                        8,
                        8
                ),
                stableGeometry(),
                List.of(deepFluid), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private HydrologyPlannerSettings.Geometry stableGeometry() {
        HydrologyPlannerSettings.ChannelShape stableChannel =
                HydrologyPlannerSettings.ChannelShape.of(2D, 0D, 0D, 11);
        return new HydrologyPlannerSettings.Geometry(
                new HydrologyPlannerSettings.Meanders(224, 72, 0D, 0D, 0D, 0, 75D),
                stableChannel,
                stableChannel,
                stableChannel,
                HydrologyPlannerSettings.Geometry.defaults().drops()
        );
    }

    private HydrologyPlannerSettings requiredFallbackSettings() {
        HydrologyPlannerSettings base = settings();
        HydrologyPlannerSettings.Surface baseSurface = base.surface();
        HydrologyPlannerSettings.Underground baseUnderground = base.underground();
        HydrologyPlannerSettings.Source surfaceSources = new HydrologyPlannerSettings.Source(
                true,
                0D,
                80,
                1,
                1,
                32
        );
        HydrologyPlannerSettings.Source disabledSources = new HydrologyPlannerSettings.Source(
                false,
                0D,
                Integer.MIN_VALUE,
                0,
                0,
                0
        );
        return new HydrologyPlannerSettings(
                base.seaLevel(),
                base.routing(),
                new HydrologyPlannerSettings.Surface(
                        true,
                        surfaceSources,
                        baseSurface.minimumWidth(),
                        baseSurface.maximumWidth(),
                        baseSurface.minimumDepth(),
                        baseSurface.maximumDepth(),
                        64,
                        baseSurface.shoreWidth(),
                        HydrologyPlannerSettings.Banks.defaults()),
                base.hydraulics(),
                HydrologyPlannerSettings.Underground.of(
                        false,
                        disabledSources,
                        baseUnderground.minimumFluidY(),
                        baseUnderground.maximumFluidY(),
                        baseUnderground.minimumWidth(),
                        baseUnderground.maximumWidth(),
                        baseUnderground.minimumDepth(),
                        baseUnderground.maximumDepth(),
                        baseUnderground.minimumHeadroom(),
                        baseUnderground.maximumHeadroom(),
                        baseUnderground.connectToExistingCaves(),
                        0
                ),
                HydrologyPlannerSettings.Outlets.of(
                        base.outlets().oceanEnabled(),
                        new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                        base.outlets().inlandGrotto(),
                        base.outlets().surfaceSinkholesEnabled(),
                        base.outlets().coastalCliffMinimumHeight(),
                        base.outlets().mouthLevelingDistance(),
                        base.outlets().maximumOceanApron(),
                        base.outlets().maximumPerTile(),
                        base.outlets().maximumPerTile()
                ),
                base.geometry(),
                base.deepFluids(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private HydrologyTerrainSampler terrain() {
        return (int x, int z) -> {
            if (x >= 224) {
                return oceanTerrain();
            }
            double wave = StrictMath.sin((z + SEED % 37L) / 19D) * 3D;
            double ridge = x >= 96 && x <= 128 ? 34D : 0D;
            int height = 126 - Math.floorDiv(x, 13) + (int) StrictMath.round(wave + ridge);
            double slope = ridge > 0D ? 18D : 1D + StrictMath.abs(wave) * 0.25D;
            boolean surfaceSource = x >= 0 && x <= 48;
            boolean undergroundSource = x >= 0 && x <= 64;
            return new HydrologyTerrainSample(
                    height,
                    slope,
                    false,
                    true,
                    70,
                    74 + Math.floorMod(x + z, 7),
                    true,
                    true,
                    surfaceSource,
                    surfaceSource,
                    undergroundSource,
                    undergroundSource,
                    0D,
                    surfaceSource ? 1D : 0D,
                    undergroundSource ? 1D : 0D,
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
        };
    }

    private HydrologyTerrainSampler requiredFallbackTerrain() {
        HydrologyTerrainSampler base = terrain();
        return (int x, int z) -> {
            HydrologyTerrainSample sample = base.sample(x, z);
            boolean winner = x == 32 && z == -272;
            boolean initialLoser = x == 16 && z == -224;
            boolean fallback = x == 16 && z == -192;
            boolean required = winner || initialLoser || fallback;
            double weight = initialLoser ? 64D : required ? 1D : 0D;
            return new HydrologyTerrainSample(
                    sample.naturalHeight(),
                    sample.slope(),
                    sample.ocean(),
                    sample.caveAvailable(),
                    sample.caveFloorY(),
                    sample.caveFluidY(),
                    sample.transitAllowed(),
                    sample.outletAllowed(),
                    required,
                    required,
                    false,
                    false,
                    sample.routingCost(),
                    weight,
                    0D,
                    sample.widthMultiplier(),
                    sample.depthMultiplier(),
                    sample.incisionMultiplier(),
                    sample.routingMultiplier(),
                    1D,
                    sample.parentBiomeKey(),
                    sample.surfaceBiomeKey(),
                    sample.mouthBiomeKey(),
                    sample.shoreBiomeKey(),
                    sample.bankBiomeKey(),
                    sample.floodedCaveBiomeKey(),
                    sample.preferredProfileKeys(), List.of(),
                    Double.NaN,
                    null,
                    Double.NaN,
                    true,
                    SurfaceRiverPolicy.INHERIT
            );
        };
    }

    private HydrologyTerrainSample oceanTerrain() {
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
                "ocean_parent",
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

    private record TilePair(HydrologyTileKey first, HydrologyTileKey second) {
    }

    private static final class PlannedSurfaceCaveView implements CaveVoxelView {
        private final HydrologyTerrainSampler terrain;
        private final HydrologyCaveVoxelViewFactory.PlannedSurface surface;

        private PlannedSurfaceCaveView(
                HydrologyTerrainSampler terrain,
                HydrologyCaveVoxelViewFactory.PlannedSurface surface
        ) {
            this.terrain = terrain;
            this.surface = surface;
        }

        @Override
        public boolean isInWorld(CavePosition position) {
            return position.y() > -4096 && position.y() < 4095;
        }

        @Override
        public CaveVoxel voxelAt(CavePosition position) {
            return position.y() <= surfaceHeight(position.x(), position.z())
                    ? CaveVoxel.SOLID
                    : CaveVoxel.CAVE_AIR;
        }

        @Override
        public boolean isOpenToSurface(CavePosition position) {
            return position.y() > surfaceHeight(position.x(), position.z());
        }

        @Override
        public boolean isAboveTerrainSurface(CavePosition position) {
            return isOpenToSurface(position);
        }

        @Override
        public boolean hasAboveTerrainSurface(int x, int z, int minimumY, int maximumY) {
            return maximumY > surfaceHeight(x, z);
        }

        private int surfaceHeight(int x, int z) {
            HydrologyTerrainSample sample = terrain.sample(x, z);
            return surface.resolve(x, z, sample.naturalHeight());
        }
    }
}
