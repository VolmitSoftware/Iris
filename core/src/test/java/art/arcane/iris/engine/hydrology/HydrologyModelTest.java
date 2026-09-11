package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxel;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelPrecondition;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveMode;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveRejection;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyModelTest {
    @Test
    public void footprintAndColumnCollectionsAreImmutableCopies() {
        HydrologyFeatureRef feature = feature(HydrologyFeatureType.SURFACE_POOL, 10L, 70);
        ArrayList<HydrologyColumnLayer> mutableLayers = new ArrayList<>();
        mutableLayers.add(layer(feature, 67, 70, 70, true, false, false));
        HydrologyColumnSample column = new HydrologyColumnSample(4, 9, 74, 63, false, "parent", mutableLayers);
        HashMap<Long, HydrologyColumnSample> mutableColumns = new HashMap<>();
        mutableColumns.put(RiverFootprint.pack(4, 9), column);
        RiverFootprint footprint = new RiverFootprint(mutableColumns);

        mutableLayers.clear();
        mutableColumns.clear();

        assertEquals(1, column.layers().size());
        assertEquals(1, footprint.size());
        assertThrows(UnsupportedOperationException.class, () -> column.layers().add(layer(
                feature,
                67,
                70,
                70,
                true,
                false,
                false
        )));
        assertThrows(UnsupportedOperationException.class, () -> footprint.columns().clear());
    }

    @Test
    public void positiveHeadTransitionsCanShareOneReceivingPoolAcrossAComplex() {
        List<HydrologyPoint> centerline = List.of(
                new HydrologyPoint(0, 80, 0),
                new HydrologyPoint(1, 74, 0)
        );

        HydraulicSegment graded = new HydraulicSegment(
                1L,
                2L,
                HydrologyFeatureType.WATERFALL,
                80,
                74,
                4,
                2,
                false,
                true,
                centerline, HydraulicChannelProfile.uniform(4, 2)
        );
        assertEquals(6, graded.drop());
        assertFalse(graded.fallingFluid());
        HydraulicSegment intermediate = new HydraulicSegment(
                1L,
                2L,
                HydrologyFeatureType.WATERFALL,
                80,
                74,
                4,
                2,
                false,
                false,
                centerline, HydraulicChannelProfile.uniform(4, 2)
        );
        assertFalse(intermediate.receivingPool());
        HydraulicSegment accepted = new HydraulicSegment(
                1L,
                2L,
                HydrologyFeatureType.WATERFALL,
                80,
                74,
                4,
                2,
                true,
                true,
                centerline, HydraulicChannelProfile.uniform(4, 2)
        );
        assertEquals(6, accepted.drop());
    }

    @Test
    public void surfaceInlandGrottoRequiresOneExplicitSinkholeLinkInTheSameCourse() {
        HydraulicSegment lip = new HydraulicSegment(
                1L,
                9L,
                HydrologyFeatureType.SURFACE_POOL,
                80,
                80,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 80, 0)), HydraulicChannelProfile.uniform(4, 2)
        );
        HydraulicSegment sinkhole = new HydraulicSegment(
                2L,
                9L,
                HydrologyFeatureType.SINKHOLE,
                80,
                74,
                4,
                2,
                true,
                true,
                List.of(new HydrologyPoint(0, 80, 0), new HydrologyPoint(1, 74, 0)), HydraulicChannelProfile.uniform(4, 2)
        );
        HydraulicSegment grotto = new HydraulicSegment(
                3L,
                9L,
                HydrologyFeatureType.INLAND_GROTTO,
                74,
                74,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(1, 74, 0)), HydraulicChannelProfile.uniform(4, 2)
        );

        RiverCourse accepted = new RiverCourse(
                9L,
                RiverCourseType.SURFACE,
                OptionalLong.of(4L),
                OptionalLong.of(5L),
                "water",
                1,
                List.of(),
                List.of(lip, sinkhole, grotto)
        );

        assertTrue(accepted.surfaceSinkholeContinuation());
        HydraulicSegment containedLip = new HydraulicSegment(
                4L,
                9L,
                HydrologyFeatureType.SURFACE_POOL,
                80,
                80,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 80, 0)), HydraulicChannelProfile.uniform(4, 2)
        );
        RiverCourse containedApproach = new RiverCourse(
                9L,
                RiverCourseType.SURFACE,
                OptionalLong.of(4L),
                OptionalLong.of(5L),
                "water",
                1,
                List.of(),
                List.of(containedLip, sinkhole, grotto)
        );
        assertTrue(containedApproach.surfaceSinkholeContinuation());
        assertThrows(IllegalArgumentException.class, () -> new RiverCourse(
                9L,
                RiverCourseType.SURFACE,
                OptionalLong.of(4L),
                OptionalLong.of(5L),
                "water",
                1,
                List.of(),
                List.of(lip, grotto)
        ));
    }

    @Test
    public void seaCaveCoursesAreIndependent() {
        HydraulicSegment chamber = new HydraulicSegment(
                1L,
                21L,
                HydrologyFeatureType.COASTAL_GROTTO,
                63,
                63,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(100, 63, 0), new HydrologyPoint(112, 63, 0)), HydraulicChannelProfile.uniform(4, 2)
        );

        RiverCourse seaCave = new RiverCourse(
                21L,
                RiverCourseType.SEA_CAVE,
                OptionalLong.empty(),
                OptionalLong.empty(),
                "water",
                1,
                List.of(),
                List.of(chamber)
        );

        assertEquals(RiverCourseType.SEA_CAVE, seaCave.type());
        assertTrue(seaCave.sourceNodeId().isEmpty());
        assertTrue(seaCave.outletId().isEmpty());
        assertFalse(seaCave.surfaceSinkholeContinuation());
        assertThrows(IllegalArgumentException.class, () -> new RiverCourse(
                21L,
                RiverCourseType.SEA_CAVE,
                OptionalLong.of(4L),
                OptionalLong.empty(),
                "water",
                1,
                List.of(),
                List.of(chamber)
        ));
        assertThrows(IllegalArgumentException.class, () -> new RiverCourse(
                21L,
                RiverCourseType.SEA_CAVE,
                OptionalLong.empty(),
                OptionalLong.of(5L),
                "water",
                1,
                List.of(),
                List.of(chamber)
        ));
        assertThrows(IllegalArgumentException.class, () -> new RiverCourse(
                21L,
                RiverCourseType.SEA_CAVE,
                OptionalLong.empty(),
                OptionalLong.empty(),
                "water",
                1,
                List.of(),
                List.of()
        ));
    }

    @Test
    public void oceanColumnsRejectOwnedOrElevatedWrites() {
        HydrologyFeatureRef feature = feature(HydrologyFeatureType.MOUTH, 7L, 63);
        HydrologyColumnLayer apron = layer(feature, 63, 63, 63, true, false, true);
        HydrologyColumnSample accepted = new HydrologyColumnSample(
                4,
                9,
                50,
                63,
                true,
                "ocean_parent",
                List.of(apron)
        );
        assertTrue(accepted.layers().getFirst().oceanApron());

        HydrologyColumnLayer owned = layer(feature, 61, 63, 63, true, true, false);
        assertThrows(IllegalArgumentException.class, () -> new HydrologyColumnSample(
                4,
                9,
                50,
                63,
                true,
                "ocean_parent",
                List.of(owned)
        ));
    }

    @Test
    public void submergedNonOceanColumnsRejectSurfaceOwnershipButAllowSubterrainHydrology() {
        HydrologyColumnLayer surface = layer(
                feature(HydrologyFeatureType.SURFACE_POOL, 8L, 63),
                60,
                63,
                63,
                true,
                true,
                false
        );
        assertThrows(IllegalArgumentException.class, () -> new HydrologyColumnSample(
                4,
                9,
                62,
                63,
                false,
                "shore_parent",
                List.of(surface)
        ));

        HydrologyColumnSample raised = new HydrologyColumnSample(
                4,
                9,
                63,
                63,
                false,
                "shore_parent",
                List.of(surface)
        );
        assertTrue(raised.primarySurfaceFluidLayer().isPresent());

        HydrologyColumnLayer underground = layer(
                feature(HydrologyFeatureType.UNDERGROUND_POOL, 9L, 40),
                36,
                40,
                45,
                true,
                true,
                false
        );
        HydrologyColumnSample subterrain = new HydrologyColumnSample(
                4,
                9,
                50,
                63,
                false,
                "shore_parent",
                List.of(underground)
        );
        assertTrue(subterrain.hasFeature(HydrologyFeatureType.UNDERGROUND_POOL));
    }

    @Test
    public void outerGradingUsesTheBankBiome() {
        HydrologyFeatureRef feature = feature(HydrologyFeatureType.SURFACE_POOL, 3L, 70);
        HydrologyColumnLayer grading = layer(feature, 72, 72, 72, false, false, false);
        grading = new HydrologyColumnLayer(
                grading.feature(),
                grading.bedY(),
                grading.fluidHeadY(),
                grading.ceilingY(),
                false,
                false,
                true,
                false,
                false,
                false,
                true,
                false,
                false,
                "alpha",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded"
        );
        HydrologyColumnSample sample = new HydrologyColumnSample(0, 0, 74, 63, false, "exact_parent", List.of(grading));

        assertEquals("dry", grading.biomeKey());
        assertEquals("exact_parent", sample.parentBiomeKey());
        assertFalse(sample.hasConnectedFluid());
    }

    @Test
    public void overlappingDrySurfaceGradesKeepTheHighestContainedBank() {
        HydrologyColumnLayer lowerCascadeGrade = surfaceLayer(
                feature(HydrologyFeatureType.CASCADE, 30L, 70),
                70,
                70,
                false,
                false,
                true,
                true,
                false
        );
        HydrologyColumnLayer higherPoolGrade = surfaceLayer(
                feature(HydrologyFeatureType.SURFACE_POOL, 31L, 72),
                72,
                72,
                false,
                false,
                true,
                true,
                false
        );
        HydrologyColumnSample sample = new HydrologyColumnSample(
                0,
                0,
                75,
                63,
                false,
                "parent",
                List.of(lowerCascadeGrade, higherPoolGrade)
        );

        assertEquals(72, sample.terrainHeight());
    }

    @Test
    public void uncutShoreRetainsItsBiomeWithoutOverridingAnOwnedBank() {
        HydrologyColumnLayer shore = surfaceLayer(feature(HydrologyFeatureType.SURFACE_POOL, 30L, 75),
                75, 75, false, true, true, false, false);
        HydrologyColumnLayer bank = surfaceLayer(feature(HydrologyFeatureType.SURFACE_POOL, 31L, 72),
                72, 72, false, false, true, true, false);
        HydrologyColumnSample uncut = new HydrologyColumnSample(0, 0, 75, 63, false, "parent", List.of(shore));
        HydrologyColumnSample cut = new HydrologyColumnSample(0, 0, 75, 63, false, "parent", List.of(shore, bank));

        assertEquals(shore, uncut.primarySurfaceLayer().orElseThrow());
        assertEquals("shore", uncut.primarySurfaceLayer().orElseThrow().biomeKey());
        assertEquals(75, uncut.terrainHeight());
        assertTrue(uncut.primarySurfaceFluidLayer().isEmpty());
        assertEquals(bank, cut.primarySurfaceLayer().orElseThrow());
        assertEquals(72, cut.terrainHeight());
    }

    @Test
    public void acceptedSurfaceArbitrationPrefersChannelThenHydraulicType() {
        HydrologyColumnLayer grading = surfaceLayer(
                feature(HydrologyFeatureType.WATERFALL, 4L, 78),
                73,
                78,
                false,
                false,
                true,
                true,
                false
        );
        HydrologyColumnLayer pool = surfaceLayer(
                feature(HydrologyFeatureType.SURFACE_POOL, 5L, 74),
                70,
                74,
                true,
                false,
                false,
                true,
                true
        );
        HydrologyColumnLayer waterfall = surfaceLayer(
                feature(HydrologyFeatureType.WATERFALL, 6L, 72),
                66,
                72,
                true,
                false,
                false,
                true,
                true
        );
        HydrologyColumnSample sample = new HydrologyColumnSample(
                4,
                9,
                80,
                63,
                false,
                "parent",
                List.of(grading, pool, waterfall)
        );

        assertEquals(waterfall, sample.primarySurfaceLayer().orElseThrow());
        assertEquals(waterfall, sample.primarySurfaceFluidLayer().orElseThrow());
        assertEquals(66, sample.terrainHeight());
    }

    @Test
    public void oceanApronDoesNotOwnSurfaceArbitration() {
        HydrologyFeatureRef feature = feature(HydrologyFeatureType.MOUTH, 8L, 63);
        HydrologyColumnLayer apron = layer(feature, 63, 63, 63, true, false, true);
        HydrologyColumnSample sample = new HydrologyColumnSample(
                4,
                9,
                52,
                63,
                true,
                "ocean_parent",
                List.of(apron)
        );

        assertTrue(sample.primarySurfaceLayer().isEmpty());
        assertTrue(sample.primarySurfaceFluidLayer().isEmpty());
        assertEquals(52, sample.terrainHeight());
    }

    @Test
    public void rendererClassificationIsExactlyTheAcceptedColumnClassification() {
        HydrologyColumnLayer pool = surfaceLayer(
                feature(HydrologyFeatureType.SURFACE_POOL, 10L, 70),
                67,
                70,
                true,
                false,
                false,
                true,
                true
        );
        HydrologyColumnLayer waterfall = surfaceLayer(
                feature(HydrologyFeatureType.WATERFALL, 11L, 76),
                64,
                76,
                true,
                false,
                false,
                true,
                true
        );
        HydrologyColumnSample sample = new HydrologyColumnSample(
                4,
                9,
                80,
                63,
                false,
                "parent",
                List.of(pool, waterfall)
        );

        HydrologyRenderSample render = sample.renderSample();
        assertEquals(2, render.features().size());
        assertEquals(HydrologyFeatureType.WATERFALL, render.primaryFeature().orElseThrow().type());
        assertTrue(render.hasFeature(HydrologyFeatureType.SURFACE_POOL));
        assertTrue(render.hasFeature(HydrologyFeatureType.WATERFALL));
    }

    @Test
    public void locatorIndexKeepsOnePublishedRepresentativePerSegment() {
        HydrologyFeatureRef gradingFeature = new HydrologyFeatureRef(
                10L, HydrologyFeatureType.WATERFALL, 1L, 100L, 0, 74, 0, 1, 0, false
        );
        HydrologyFeatureRef fallingFeature = new HydrologyFeatureRef(
                11L, HydrologyFeatureType.WATERFALL, 1L, 100L, 1, 74, 0, 1, 0, false
        );
        HydrologyFeatureRef poolFeature = new HydrologyFeatureRef(
                12L, HydrologyFeatureType.SURFACE_POOL, 1L, 101L, 2, 70, 0, 1, 0, false
        );
        HydrologyColumnLayer grading = surfaceLayer(
                gradingFeature, 74, 74, false, false, true, true, false
        );
        HydrologyColumnLayer falling = new HydrologyColumnLayer(
                fallingFeature,
                68,
                74,
                74,
                true,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                false,
                "alpha",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded"
        );
        HydrologyColumnLayer pool = surfaceLayer(
                poolFeature, 67, 70, true, false, false, true, true
        );
        Map<Long, HydrologyColumnSample> columns = Map.of(
                RiverFootprint.pack(0, 0), new HydrologyColumnSample(0, 0, 80, 63, false, "parent", List.of(grading)),
                RiverFootprint.pack(1, 0), new HydrologyColumnSample(1, 0, 80, 63, false, "parent", List.of(falling)),
                RiverFootprint.pack(2, 0), new HydrologyColumnSample(2, 0, 80, 63, false, "parent", List.of(pool))
        );
        RiverFootprint footprint = new RiverFootprint(columns);

        List<HydrologyFeatureRef> features = HydrologyTile.collectFeatures(footprint);

        assertEquals(2, features.size());
        assertTrue(features.contains(fallingFeature));
        assertTrue(features.contains(poolFeature));
        for (HydrologyFeatureRef feature : features) {
            assertTrue(footprint.sample(feature.x(), feature.z()).orElseThrow().layers().stream()
                    .anyMatch(layer -> layer.feature().id() == feature.id()));
        }
    }

    @Test
    public void featureQuerySuggestionsIncludeConfiguredDeepFluidsWithoutHardcodedProfiles() {
        List<String> suggestions = HydrologyFeatureQuery.suggestions(
                List.of("acid", "deep_lava", "acid", "surface", "coastal-grotto")
        );

        assertEquals(List.of(
                "surface",
                "waterfall",
                "sinkhole",
                "underground",
                "grotto",
                "coastal_grotto",
                "inland_grotto",
                "mouth",
                "deep",
                "pool",
                "acid",
                "deep_lava"
        ), suggestions);
    }

    @Test
    public void featureQueryReservesEveryBuiltInKeywordAndEquivalentHyphenSpelling() {
        List<String> builtInKeywords = List.of(
                "surface",
                "waterfall",
                "sinkhole",
                "underground",
                "grotto",
                "coastal_grotto",
                "inland_grotto",
                "mouth",
                "deep",
                "pool"
        );

        assertEquals(builtInKeywords, HydrologyFeatureQuery.suggestions(List.of()));
        for (String keyword : builtInKeywords) {
            assertTrue(keyword, HydrologyFeatureQuery.isReservedKeyword(keyword));
            assertTrue(keyword, HydrologyFeatureQuery.isReservedKeyword(keyword.replace('_', '-')));
        }
        assertFalse(HydrologyFeatureQuery.isReservedKeyword("acid"));
        assertFalse(HydrologyFeatureQuery.isReservedKeyword("ridge_tunnel"));
        assertFalse(HydrologyFeatureQuery.isReservedKeyword("ridge-tunnel"));
        assertFalse(HydrologyFeatureQuery.isReservedKeyword(null));
        assertEquals(Set.of(HydrologyFeatureType.SINKHOLE), HydrologyFeatureQuery.parse("sinkhole").types());
    }

    @Test
    public void compactLocatorIndexFindsThePublishedEndOfALongSegment() {
        HydraulicSegment segment = new HydraulicSegment(
                200L,
                201L,
                HydrologyFeatureType.DEEP_CHANNEL,
                20,
                20,
                4,
                2,
                false,
                false,
                List.of(
                        new HydrologyPoint(0, 20, 0),
                        new HydrologyPoint(500, 20, 0),
                        new HydrologyPoint(1000, 20, 0)
                ), HydraulicChannelProfile.uniform(4, 2)
        );
        RiverCourse course = new RiverCourse(
                201L,
                RiverCourseType.DEEP_FLUID,
                OptionalLong.empty(),
                OptionalLong.empty(),
                "acid",
                1,
                List.of(),
                List.of(segment)
        );
        HydrologyFeatureRef endpoint = new HydrologyFeatureRef(
                202L,
                HydrologyFeatureType.DEEP_CHANNEL,
                course.id(),
                segment.id(),
                1000,
                20,
                0,
                1,
                0,
                false
        );
        HydrologyColumnLayer layer = new HydrologyColumnLayer(
                endpoint,
                18,
                20,
                24,
                true,
                false,
                false,
                true,
                false,
                false,
                true,
                true,
                false,
                "acid",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded"
        );
        HydrologyColumnSample column = new HydrologyColumnSample(
                1000, 0, 80, 63, false, "parent", List.of(layer)
        );
        HydrologyFeatureRef middle = new HydrologyFeatureRef(
                203L,
                HydrologyFeatureType.DEEP_CHANNEL,
                course.id(),
                segment.id(),
                500,
                20,
                0,
                1,
                0,
                false
        );
        HydrologyColumnLayer middleLayer = new HydrologyColumnLayer(
                middle,
                18,
                20,
                24,
                true,
                false,
                false,
                true,
                false,
                false,
                true,
                true,
                false,
                "acid",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded"
        );
        HydrologyColumnSample middleColumn = new HydrologyColumnSample(
                500, 0, 80, 63, false, "parent", List.of(middleLayer)
        );
        HashMap<CavePosition, HydrologyCaveAction> actions = new HashMap<>();
        HashMap<CavePosition, CaveVoxelPrecondition> preconditions = new HashMap<>();
        for (HydrologyColumnSample plannedColumn : List.of(column, middleColumn)) {
            HydrologyColumnLayer plannedLayer = plannedColumn.layers().getFirst();
            for (int y = plannedLayer.bedY() + 1; y <= plannedLayer.ceilingY(); y++) {
                CavePosition position = new CavePosition(plannedColumn.x(), y, plannedColumn.z());
                actions.put(
                        position,
                        y > plannedLayer.fluidHeadY()
                                ? HydrologyCaveAction.DRY_AIR
                                : HydrologyCaveAction.WET_SOURCE
                );
                preconditions.put(position, new CaveVoxelPrecondition(CaveVoxel.SOLID, false));
            }
        }
        HydrologyCavePlan plan = new HydrologyCavePlan(
                new HydrologyCaveSource(
                        course.id(),
                        new CavePosition(column.x(), layer.ceilingY(), column.z()),
                        new CavePosition(column.x(), layer.fluidHeadY(), column.z()),
                        layer.fluidHeadY(),
                        HydrologyCaveMode.GENERATED_GROTTO
                ),
                HydrologyCaveRejection.NONE,
                actions,
                preconditions,
                OptionalLong.empty()
        );
        HydrologyTile tile = new HydrologyTile(
                new HydrologyTileKey(0, 0),
                1L,
                2L,
                2048,
                List.of(),
                List.of(),
                List.of(),
                List.of(course),
                List.of(plan),
                List.of(),
                new RiverFootprint(Map.of(
                        RiverFootprint.pack(1000, 0), column,
                        RiverFootprint.pack(500, 0), middleColumn
                ))
        );

        assertEquals(endpoint, tile.nearestFeature(
                Set.of(HydrologyFeatureType.DEEP_CHANNEL), "acid", 1000, 0, 10).orElseThrow());
        assertTrue(tile.nearestFeature(
                Set.of(HydrologyFeatureType.DEEP_CHANNEL), "deep_lava", 1000, 0, 10).isEmpty());
        assertEquals(middle, tile.nearestFeature(
                Set.of(HydrologyFeatureType.DEEP_CHANNEL),
                "acid",
                1000,
                0,
                1000,
                feature -> feature.x() != 1000
        ).orElseThrow());
    }

    private HydrologyFeatureRef feature(HydrologyFeatureType type, long id, int y) {
        return new HydrologyFeatureRef(id, type, 1L, 2L, 4, y, 9, 1, 0, false);
    }

    private HydrologyColumnLayer layer(
            HydrologyFeatureRef feature,
            int bed,
            int head,
            int ceiling,
            boolean channel,
            boolean owned,
            boolean apron
    ) {
        return new HydrologyColumnLayer(
                feature,
                bed,
                head,
                ceiling,
                channel,
                false,
                false,
                channel,
                false,
                false,
                owned,
                owned,
                apron,
                "alpha",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded"
        );
    }

    private HydrologyColumnLayer surfaceLayer(
            HydrologyFeatureRef feature,
            int bed,
            int head,
            boolean channel,
            boolean shore,
            boolean grading,
            boolean terrainOwned,
            boolean fluidOwned
    ) {
        return new HydrologyColumnLayer(
                feature,
                bed,
                head,
                head,
                channel,
                shore,
                grading,
                fluidOwned,
                false,
                false,
                terrainOwned,
                fluidOwned,
                false,
                "alpha",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded"
        );
    }
}
