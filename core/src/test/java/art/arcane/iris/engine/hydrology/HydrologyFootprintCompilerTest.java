package art.arcane.iris.engine.hydrology;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HydrologyFootprintCompilerTest {
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * Published dry cells cardinally adjacent to owned water whose final terrain sits below the
     * water head plus the configured sink.
     */
    private static int unsupportedBankCells(RiverFootprint footprint, long courseId) {
        int sink = HydrologyPlannerSettings.defaults().surface().banks().sink();
        int unsupported = 0;
        for (HydrologyColumnSample sample : footprint.columns().values()) {
            HydrologyColumnLayer fluid = sample.primarySurfaceFluidLayer().orElse(null);
            if (fluid == null
                    || fluid.feature().courseId() != courseId
                    || fluid.oceanApron()
                    || fluid.fallingFluid()) {
                continue;
            }
            for (int[] offset : CARDINALS) {
                HydrologyColumnSample neighbor = footprint
                        .sample(sample.x() + offset[0], sample.z() + offset[1])
                        .orElse(null);
                if (neighbor == null || neighbor.primarySurfaceFluidLayer().isPresent()) {
                    continue;
                }
                if (neighbor.terrainHeight() < fluid.fluidHeadY() + sink) {
                    unsupported++;
                }
            }
        }
        return unsupported;
    }

    /** Deepest cut between natural terrain and the published water head over an owned channel. */
    private static int maximumSurfaceCut(RiverFootprint footprint, long courseId) {
        int maximum = 0;
        for (HydrologyColumnSample sample : footprint.columns().values()) {
            HydrologyColumnLayer fluid = sample.primarySurfaceFluidLayer().orElse(null);
            if (fluid == null
                    || fluid.feature().courseId() != courseId
                    || fluid.feature().type() == HydrologyFeatureType.MOUTH
                    || fluid.oceanApron()
                    || fluid.fallingFluid()
                    || !fluid.channel()
                    || !fluid.fluidOwned()) {
                continue;
            }
            maximum = Math.max(maximum, sample.naturalHeight() - fluid.fluidHeadY());
        }
        return maximum;
    }

    @Test
    public void slopeFreeBasisPreservesCompleteSurfaceAndCaveFootprints() {
        List<HydrologyFeatureType> types = List.of(
                HydrologyFeatureType.SURFACE_POOL,
                HydrologyFeatureType.MOUTH,
                HydrologyFeatureType.UNDERGROUND_POOL,
                HydrologyFeatureType.COASTAL_GROTTO
        );
        for (HydrologyFeatureType type : types) {
            int head = type == HydrologyFeatureType.MOUTH || type == HydrologyFeatureType.COASTAL_GROTTO
                    ? 63 : 70;
            HydraulicSegment segment = new HydraulicSegment(
                    9001L, 9000L, type, head, head, 6, 3, false, false,
                    List.of(new HydrologyPoint(-12, head, -3), new HydrologyPoint(32, head, 5)));
            RiverCourse course = course(9000L,
                    type.isSurface() ? RiverCourseType.SURFACE : RiverCourseType.UNDERGROUND, segment);
            SlopeCountingSampler original = new SlopeCountingSampler(false);
            SlopeCountingSampler slopeFree = new SlopeCountingSampler(true);
            HydrologyFootprintCompiler baseline = new HydrologyFootprintCompiler(
                    HydrologyPlannerSettings.defaults(),
                    new HydrologyFootprintCompiler.Sampling(original, request -> request.minimum(), original));
            HydrologyFootprintCompiler optimized = new HydrologyFootprintCompiler(
                    HydrologyPlannerSettings.defaults(),
                    new HydrologyFootprintCompiler.Sampling(slopeFree, request -> request.minimum(), slopeFree));

            HydrologyFootprintCompiler.ValidationRaster expectedValidation = baseline.compileValidation(List.of(course));
            HydrologyFootprintCompiler.ValidationRaster actualValidation = optimized.compileValidation(List.of(course));
            RiverFootprint expected = baseline.compile(List.of(course));
            RiverFootprint actual = optimized.compile(List.of(course));

            assertFalse(type.toString(), expected.isEmpty());
            assertEquals(type.toString(), expected, actual);
            assertEquals(type.toString(), expectedValidation.columns(), actualValidation.columns());
            for (HydrologyColumnSample column : expected.columns().values()) {
                assertEquals(type.toString(),
                        expectedValidation.plannedSurface().resolve(column.x(), column.z(), column.naturalHeight()),
                        actualValidation.plannedSurface().resolve(column.x(), column.z(), column.naturalHeight()));
            }
            assertTrue(type.toString(), original.basisCalls > 0);
            assertEquals(type.toString(), 0, slopeFree.basisCalls);
            assertEquals(type.toString(), original.basisCalls, slopeFree.slopeFreeCalls);
            assertEquals(type.toString(), original.detailedCalls, slopeFree.detailedCalls);
        }
    }

    @Test
    public void surfaceChannelNeverRaisesTerrainOrPublishesUnsupportedFluid() {
        HydraulicSegment segment = new HydraulicSegment(
                9L,
                8L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                10,
                4,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(24, 70, 0))
        );
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(
                z < 0 ? 54 : 82,
                0D,
                "parent"
        );
        RiverFootprint footprint = new HydrologyFootprintCompiler(
                HydrologyPlannerSettings.defaults(),
                terrain,
                request -> request.minimum()
        ).compile(List.of(course(8L, RiverCourseType.SURFACE, segment)));

        HydrologyColumnSample lowCenter = footprint.sample(12, -1).orElse(null);
        assertTrue(lowCenter == null || lowCenter.primarySurfaceFluidLayer().isEmpty());
        for (HydrologyColumnSample sample : footprint.columns().values()) {
            assertTrue(sample.terrainHeight() <= sample.naturalHeight());
            sample.primarySurfaceFluidLayer().ifPresent((HydrologyColumnLayer layer) ->
                    assertTrue(layer.fluidHeadY() < sample.naturalHeight()));
        }
    }

    @Test
    public void sparseCenterlinePublishesAContinuousOwnedFluidChannel() {
        HydraulicSegment segment = new HydraulicSegment(
                11L,
                10L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(8, 70, 0))
        );
        RiverCourse course = new RiverCourse(
                10L,
                RiverCourseType.SURFACE,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "water",
                1,
                List.of(),
                List.of(segment)
        );
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(75, 1D, "parent");
        RiverFootprint footprint = new HydrologyFootprintCompiler(
                HydrologyPlannerSettings.defaults(),
                terrain,
                HydrologyGeometrySampler.deterministic(terrain)
        ).compile(List.of(course));

        for (int x = 0; x <= 8; x++) {
            HydrologyColumnLayer layer = footprint.sample(x, 0)
                    .flatMap(HydrologyColumnSample::primarySurfaceFluidLayer)
                    .orElseThrow();
            assertTrue(layer.channel());
            assertTrue(layer.connectedFluid());
            assertTrue(layer.fluidOwned());
        }
        HydrologyColumnLayer firstShared = footprint.sample(2, 0)
                .flatMap(HydrologyColumnSample::primarySurfaceFluidLayer)
                .orElseThrow();
        HydrologyColumnLayer secondShared = footprint.sample(3, 0)
                .flatMap(HydrologyColumnSample::primarySurfaceFluidLayer)
                .orElseThrow();
        assertSame(firstShared.feature(), secondShared.feature());
    }

    @Test
    public void sweptSurfaceChannelHasABroadThalwegAndContinuousOrganicBanks() {
        HydraulicSegment segment = new HydraulicSegment(
                13L,
                12L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                12,
                5,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(96, 70, 0))
        );
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(82, 0D, "parent");
        RiverFootprint footprint = new HydrologyFootprintCompiler(
                HydrologyPlannerSettings.defaults(),
                terrain,
                request -> request.minimum()
        ).compile(List.of(course(12L, RiverCourseType.SURFACE, segment)));

        ArrayList<Integer> crossSection = new ArrayList<>();
        for (int z = 0; z <= 8; z++) {
            HydrologyColumnLayer layer = footprint.sample(72, z)
                    .flatMap(HydrologyColumnSample::primarySurfaceFluidLayer)
                    .orElse(null);
            if (layer == null) {
                break;
            }
            crossSection.add(layer.bedY());
        }
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        assertTrue(crossSection.size() >= 3);
        assertTrue(crossSection.getFirst() < 70);
        assertTrue(70 - crossSection.getFirst() <= settings.surface().maximumDepth() + 1);
        int thalwegMinimum = crossSection.subList(0, 2).stream().mapToInt(Integer::intValue).min().orElseThrow();
        int thalwegMaximum = crossSection.subList(0, 2).stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertTrue(thalwegMaximum - thalwegMinimum <= 1);
        for (int index = 1; index < crossSection.size(); index++) {
            assertTrue(crossSection.get(index) >= crossSection.get(index - 1));
            assertTrue(
                    "crossSection=" + crossSection,
                    crossSection.get(index) - crossSection.get(index - 1) <= 3
            );
        }

        for (int x = 60; x < 84; x++) {
            for (int z = -5; z < 5; z++) {
                HydrologyColumnLayer northWest = surfaceFluidLayer(footprint, x, z);
                HydrologyColumnLayer northEast = surfaceFluidLayer(footprint, x + 1, z);
                HydrologyColumnLayer southWest = surfaceFluidLayer(footprint, x, z + 1);
                HydrologyColumnLayer southEast = surfaceFluidLayer(footprint, x + 1, z + 1);
                if (northWest == null || northEast == null || southWest == null || southEast == null) {
                    continue;
                }
                boolean checker = northWest.bedY() == southEast.bedY()
                        && northEast.bedY() == southWest.bedY()
                        && northWest.bedY() != northEast.bedY();
                assertFalse(checker);
            }
        }
    }

    @Test
    public void surfaceChannelAtTerrainLevelIsHeldByTheGroundBesideIt() {
        HydraulicSegment segment = new HydraulicSegment(
                15L,
                14L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                8,
                3,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(24, 70, 0))
        );
        RiverCourse course = course(14L, RiverCourseType.SURFACE, segment);
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(70, 0D, "parent");
        HydrologyFootprintCompiler compiler = compiler(terrain);
        RiverFootprint footprint = compiler.compile(List.of(course));

        assertEquals(0, unsupportedBankCells(footprint, 14L));
        for (HydrologyColumnSample sample : footprint.columns().values()) {
            assertTrue(sample.terrainHeight() <= sample.naturalHeight());
        }
    }
    @Test
    public void deepPoolIsOneConnectedAsymmetricMultiLobedBowl() {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.DeepFluid deepFluid = new HydrologyPlannerSettings.DeepFluid(
                "lava",
                true,
                1D,
                512,
                -64,
                32,
                20,
                20,
                8,
                8,
                0,
                0,
                4,
                2,
                5,
                65536,
                1,
                true,
                false
        );
        HydrologyPlannerSettings settings = new HydrologyPlannerSettings(
                base.seaLevel(),
                base.routing(),
                base.surface(),
                base.hydraulics(),
                base.underground(),
                base.outlets(),
                HydrologyPlannerSettings.Geometry.defaults(),
                List.of(deepFluid), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
        HydraulicSegment segment = new HydraulicSegment(
                15L,
                14L,
                HydrologyFeatureType.DEEP_POOL,
                -20,
                -20,
                40,
                8,
                false,
                false,
                List.of(new HydrologyPoint(0, -20, 0))
        );
        RiverCourse course = new RiverCourse(
                14L,
                RiverCourseType.DEEP_FLUID,
                OptionalLong.empty(),
                OptionalLong.empty(),
                "lava",
                1,
                List.of(),
                List.of(segment)
        );
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(80, 0D, "parent");
        RiverFootprint footprint = new HydrologyFootprintCompiler(settings, terrain, request -> request.minimum())
                .compile(List.of(course));

        HashSet<Long> wetColumns = new HashSet<>();
        int shallowestBed = Integer.MIN_VALUE;
        for (HydrologyColumnSample sample : footprint.columns().values()) {
            HydrologyColumnLayer layer = sample.layers().getFirst();
            if (!layer.channel()) {
                continue;
            }
            wetColumns.add(RiverFootprint.pack(sample.x(), sample.z()));
            shallowestBed = Math.max(shallowestBed, layer.bedY());
        }
        assertTrue(wetColumns.size() > 100);
        assertEquals(wetColumns.size(), connectedColumnCount(wetColumns));
        assertTrue(layerAt(footprint, 0, 0).bedY() < shallowestBed);

        boolean rotationalMismatch = false;
        for (long packed : wetColumns) {
            int x = RiverFootprint.unpackX(packed);
            int z = RiverFootprint.unpackZ(packed);
            if (wetColumns.contains(RiverFootprint.pack(-z, x)) != wetColumns.contains(packed)) {
                rotationalMismatch = true;
                break;
            }
        }
        assertTrue(rotationalMismatch);
    }

    @Test
    public void overlappingRasterPointsPreserveSortedKeysAndCanonicalAnchors() {
        HydraulicSegment segment = new HydraulicSegment(
                111L,
                110L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(16, 70, 0))
        );
        RiverCourse course = course(110L, RiverCourseType.SURFACE, segment);
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 1D, "parent");
        CountingNaturalSampler naturalSampler = new CountingNaturalSampler(
                terrain,
                HydrologyRoutingTerrainSampler.NaturalClassification.LAND
        );
        RiverFootprint footprint = new HydrologyFootprintCompiler(
                HydrologyPlannerSettings.defaults(),
                new HydrologyFootprintCompiler.Sampling(
                        (int x, int z) -> terrain,
                        request -> request.minimum(),
                        naturalSampler
                )
        ).compile(List.of(course));

        assertTrue(footprint.size() > 0);
        ArrayList<Long> keys = new ArrayList<>(footprint.columns().keySet());
        ArrayList<Long> sortedKeys = new ArrayList<>(keys);
        sortedKeys.sort(Long::compareTo);
        assertEquals(sortedKeys, keys);
        HydrologyFeatureRef channelFeature = footprint.sample(1, 0)
                .flatMap(HydrologyColumnSample::primarySurfaceFluidLayer)
                .orElseThrow()
                .feature();
        assertEquals(70, channelFeature.y());
        assertEquals(111L, channelFeature.segmentId());
        assertTrue(footprint.sample(channelFeature.x(), channelFeature.z()).isPresent());
    }

    @Test
    public void styledBlendAndHeadroomSampleOnlySelectedCenterlines() {
        HydraulicSegment surfaceSegment = new HydraulicSegment(
                21L,
                20L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0))
        );
        HydraulicSegment undergroundSegment = new HydraulicSegment(
                31L,
                30L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                50,
                50,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(20, 50, 0))
        );
        RiverCourse surface = course(20L, RiverCourseType.SURFACE, surfaceSegment);
        RiverCourse underground = course(30L, RiverCourseType.UNDERGROUND, undergroundSegment);
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(80, 1D, "parent");
        AtomicInteger geometrySamples = new AtomicInteger();
        HydrologyGeometrySampler geometry = request -> {
            geometrySamples.incrementAndGet();
            return request.x() < 10 ? request.maximum() : request.minimum();
        };

        RiverFootprint first = new HydrologyFootprintCompiler(settings, terrain, geometry)
                .compile(List.of(surface, underground));
        RiverFootprint repeated = new HydrologyFootprintCompiler(settings, terrain, geometry)
                .compile(List.of(surface, underground));

        assertEquals(first, repeated);
        assertTrue(first.size() > 1000);
        assertTrue(geometrySamples.get() <= 8);
        int outerX = 0;
        while (first.sample(outerX + 1, 0).isPresent()) {
            outerX++;
        }
        HydrologyColumnLayer grading = first.sample(outerX, 0).orElseThrow().layers().getFirst();
        assertTrue(grading.grading());
        HydrologyColumnLayer channel = first.sample(20, 0).orElseThrow().layers().getFirst();
        assertEquals(settings.underground().minimumHeadroom(), channel.ceilingY() - channel.fluidHeadY());
    }

    @Test
    public void surfaceChannelUsesSlopeFreeBasesWithIdenticalFootprint() {
        HydraulicSegment segment = new HydraulicSegment(
                41L,
                40L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0))
        );
        RiverCourse course = course(40L, RiverCourseType.SURFACE, segment);
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        HydrologyTerrainSample detailed = HydrologyTerrainSample.openLand(80, 20D, "parent");
        AtomicInteger baselineCalls = new AtomicInteger();
        HydrologyTerrainSampler baselineSampler = (int x, int z) -> {
            baselineCalls.incrementAndGet();
            return detailed;
        };
        RiverFootprint baseline = new HydrologyFootprintCompiler(
                settings,
                baselineSampler,
                request -> request.minimum()
        ).compile(List.of(course));
        AtomicInteger detailedCalls = new AtomicInteger();
        HydrologyTerrainSampler detailedSampler = (int x, int z) -> {
            detailedCalls.incrementAndGet();
            return detailed;
        };
        CountingNaturalSampler naturalSampler = new CountingNaturalSampler(
                detailed.withSlope(0D),
                HydrologyRoutingTerrainSampler.NaturalClassification.LAND
        );

        RiverFootprint optimized = new HydrologyFootprintCompiler(
                settings,
                new HydrologyFootprintCompiler.Sampling(
                        detailedSampler,
                        request -> request.minimum(),
                        naturalSampler
                )
        ).compile(List.of(course));

        assertEquals(baseline, optimized);
        assertTrue(baselineCalls.get() > 221);
        assertEquals(baselineCalls.get(), naturalSampler.basisCalls());
        assertEquals(0, detailedCalls.get());
    }

    @Test
    public void surfaceChannelRoundsFromItsDeepCenterIntoParentTerrain() {
        HydraulicSegment segment = new HydraulicSegment(
                43L,
                42L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                12,
                10,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0))
        );
        RiverCourse course = course(42L, RiverCourseType.SURFACE, segment);
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(80, 1D, "parent");
        RiverFootprint footprint = new HydrologyFootprintCompiler(
                HydrologyPlannerSettings.defaults(),
                terrain,
                request -> request.minimum()
        ).compile(List.of(course));

        int channelEdge = 0;
        while (layerAt(footprint, channelEdge + 1, 0).channel()) {
            channelEdge++;
        }
        int shoreX = channelEdge + 1;
        while (footprint.sample(shoreX, 0).isPresent() && !layerAt(footprint, shoreX, 0).shore()) {
            shoreX++;
        }
        int outerX = shoreX;
        while (footprint.sample(outerX + 1, 0).isPresent()) {
            outerX++;
        }
        HydrologyColumnLayer center = layerAt(footprint, 0, 0);
        HydrologyColumnLayer wetEdge = layerAt(footprint, channelEdge, 0);
        HydrologyColumnLayer shore = layerAt(footprint, shoreX, 0);
        HydrologyColumnLayer outerGrade = layerAt(footprint, outerX, 0);

        assertTrue(center.channel());
        assertTrue(wetEdge.channel());
        assertTrue(center.bedY() < center.fluidHeadY());
        assertTrue(center.bedY() <= wetEdge.bedY());
        assertTrue(wetEdge.bedY() < shore.bedY());
        assertTrue(shore.bedY() <= outerGrade.bedY());
        assertTrue(shore.shore());
        assertTrue(outerGrade.grading());
        assertEquals(footprint.sample(outerX, 0).orElseThrow().naturalHeight(), outerGrade.bedY());
        for (HydrologyColumnSample sample : footprint.columns().values()) {
            assertTrue(sample.terrainHeight() <= sample.naturalHeight());
        }
    }

    @Test
    public void surfaceChannelOutlineVariesAlongItsRun() {
        HydraulicSegment segment = new HydraulicSegment(
                47L,
                46L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                12,
                3,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(96, 70, 0))
        );
        RiverCourse course = course(46L, RiverCourseType.SURFACE, segment);
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(84, 1D, "parent");
        RiverFootprint footprint = new HydrologyFootprintCompiler(
                HydrologyPlannerSettings.defaults(),
                terrain,
                request -> request.minimum()
        ).compile(List.of(course));

        HashSet<Integer> widths = new HashSet<>();
        for (int x = 8; x <= 88; x++) {
            widths.add(wetHalfWidth(footprint, x));
        }

        assertTrue(widths.size() > 1);
    }
    @Test
    public void waterfallGradingUsesOnlySlopeFreeBasesWithIdenticalFootprint() {
        HydraulicSegment segment = new HydraulicSegment(
                51L,
                50L,
                HydrologyFeatureType.WATERFALL,
                80,
                68,
                4,
                2,
                true,
                true,
                List.of(new HydrologyPoint(0, 80, 0), new HydrologyPoint(1, 68, 0))
        );
        RiverCourse course = course(50L, RiverCourseType.SURFACE, segment);
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        HydrologyTerrainSample detailed = HydrologyTerrainSample.openLand(90, 50D, "parent");
        AtomicInteger baselineCalls = new AtomicInteger();
        HydrologyTerrainSampler baselineSampler = (int x, int z) -> {
            baselineCalls.incrementAndGet();
            return detailed;
        };
        RiverFootprint baseline = new HydrologyFootprintCompiler(
                settings,
                baselineSampler,
                request -> request.minimum()
        ).compile(List.of(course));
        AtomicInteger detailedCalls = new AtomicInteger();
        HydrologyTerrainSampler detailedSampler = (int x, int z) -> {
            detailedCalls.incrementAndGet();
            return detailed;
        };
        CountingNaturalSampler naturalSampler = new CountingNaturalSampler(
                detailed.withSlope(0D),
                HydrologyRoutingTerrainSampler.NaturalClassification.LAND
        );

        RiverFootprint optimized = new HydrologyFootprintCompiler(
                settings,
                new HydrologyFootprintCompiler.Sampling(
                        detailedSampler,
                        request -> request.minimum(),
                        naturalSampler
                )
        ).compile(List.of(course));

        assertEquals(baseline, optimized);
        assertTrue(baselineCalls.get() > 0);
        assertTrue(baselineCalls.get() < 1024);
        assertEquals(0, detailedCalls.get());
        assertEquals(baselineCalls.get(), naturalSampler.basisCalls());
        assertEquals(baselineCalls.get(), naturalSampler.classificationCalls());

        int fallingColumns = 0;
        int minimumX = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (HydrologyColumnSample column : baseline.columns().values()) {
            for (HydrologyColumnLayer layer : column.layers()) {
                if (!layer.fallingFluid()) {
                    continue;
                }
                fallingColumns++;
                minimumX = Math.min(minimumX, column.x());
                maximumX = Math.max(maximumX, column.x());
                minimumZ = Math.min(minimumZ, column.z());
                maximumZ = Math.max(maximumZ, column.z());
            }
        }
        assertTrue(fallingColumns > 0);
        assertTrue(fallingColumns < 13);
        assertTrue(maximumZ - minimumZ > maximumX - minimumX);
    }

    @Test
    public void undergroundPassageIsContinuousArchedAndVerticallyVaried() {
        HydraulicSegment segment = new HydraulicSegment(
                61L,
                60L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                48,
                48,
                12,
                2,
                false,
                false,
                List.of(
                        new HydrologyPoint(0, 48, 0),
                        new HydrologyPoint(12, 48, 5),
                        new HydrologyPoint(24, 48, 0)
                )
        );
        RiverCourse course = course(60L, RiverCourseType.UNDERGROUND, segment);
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(84, 1D, "parent");
        RiverFootprint footprint = new HydrologyFootprintCompiler(
                HydrologyPlannerSettings.defaults(),
                terrain,
                request -> request.minimum() + Math.floorMod(request.x(), request.maximum() - request.minimum() + 1)
        ).compile(List.of(course));

        for (int x = 0; x <= 24; x++) {
            int z = x <= 12
                    ? (int) StrictMath.round(x * 5D / 12D)
                    : (int) StrictMath.round((24 - x) * 5D / 12D);
            HydrologyColumnLayer layer = layerAt(footprint, x, z);
            assertTrue(layer.channel());
            assertTrue(layer.connectedFluid());
        }

        HashSet<Integer> bedLevels = new HashSet<>();
        HashSet<Integer> ceilingLevels = new HashSet<>();
        int centerBed = layerAt(footprint, 12, 5).bedY();
        int shallowestBed = Integer.MIN_VALUE;
        int lowestCeiling = Integer.MAX_VALUE;
        for (HydrologyColumnSample column : footprint.columns().values()) {
            for (HydrologyColumnLayer layer : column.layers()) {
                if (!layer.channel()) {
                    continue;
                }
                bedLevels.add(layer.bedY());
                ceilingLevels.add(layer.ceilingY());
                shallowestBed = Math.max(shallowestBed, layer.bedY());
                lowestCeiling = Math.min(lowestCeiling, layer.ceilingY());
            }
        }

        assertTrue(bedLevels.size() >= 3);
        assertTrue(ceilingLevels.size() >= 3);
        assertTrue(centerBed < shallowestBed);
        assertTrue(lowestCeiling >= 49);
    }

    @Test
    public void compilerReusesSingleCourseFootprintsAndCombinesThemExactly() {
        RiverCourse surface = course(
                71L,
                RiverCourseType.SURFACE,
                new HydraulicSegment(
                        72L,
                        71L,
                        HydrologyFeatureType.SURFACE_POOL,
                        70,
                        70,
                        4,
                        2,
                        false,
                        false,
                        List.of(new HydrologyPoint(0, 70, 0))
                )
        );
        RiverCourse underground = course(
                81L,
                RiverCourseType.UNDERGROUND,
                new HydraulicSegment(
                        82L,
                        81L,
                        HydrologyFeatureType.UNDERGROUND_POOL,
                        40,
                        40,
                        4,
                        2,
                        false,
                        false,
                        List.of(new HydrologyPoint(96, 40, 0))
                )
        );
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 1D, "parent");
        AtomicInteger terrainCalls = new AtomicInteger();
        AtomicInteger geometryCalls = new AtomicInteger();
        HydrologyFootprintCompiler compiler = new HydrologyFootprintCompiler(
                settings,
                (int x, int z) -> {
                    terrainCalls.incrementAndGet();
                    return terrain;
                },
                request -> {
                    geometryCalls.incrementAndGet();
                    return request.minimum();
                }
        );

        RiverFootprint surfaceFootprint = compiler.compile(List.of(surface));
        int terrainAfterSurface = terrainCalls.get();
        int geometryAfterSurface = geometryCalls.get();
        RiverFootprint repeatedSurface = compiler.compile(List.of(surface));

        assertSame(surfaceFootprint, repeatedSurface);
        assertEquals(terrainAfterSurface, terrainCalls.get());
        assertEquals(geometryAfterSurface, geometryCalls.get());

        RiverFootprint combined = compiler.compile(List.of(surface, underground));
        int terrainAfterCombined = terrainCalls.get();
        int geometryAfterCombined = geometryCalls.get();
        RiverFootprint repeatedCombined = compiler.compile(List.of(surface, underground));
        RiverFootprint freshCombined = new HydrologyFootprintCompiler(
                settings,
                (int x, int z) -> terrain,
                request -> request.minimum()
        ).compile(List.of(surface, underground));

        assertEquals(freshCombined, combined);
        assertEquals(combined, repeatedCombined);
        assertTrue(terrainAfterCombined > terrainAfterSurface);
        assertTrue(geometryAfterCombined > geometryAfterSurface);
        assertEquals(terrainAfterCombined, terrainCalls.get());
        assertEquals(geometryAfterCombined, geometryCalls.get());
    }

    @Test
    public void overlappingCourseMergePreservesCanonicalLayerOrder() {
        HydraulicSegment pool = new HydraulicSegment(
                211L,
                210L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0))
        );
        HydraulicSegment waterfall = new HydraulicSegment(
                221L,
                220L,
                HydrologyFeatureType.WATERFALL,
                74,
                68,
                4,
                2,
                true,
                true,
                List.of(new HydrologyPoint(0, 74, 0))
        );
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 1D, "parent");
        RiverFootprint footprint = new HydrologyFootprintCompiler(
                HydrologyPlannerSettings.defaults(),
                (int x, int z) -> terrain,
                request -> request.minimum()
        ).compile(List.of(
                course(210L, RiverCourseType.SURFACE, pool),
                course(220L, RiverCourseType.SURFACE, waterfall)
        ));

        List<HydrologyColumnLayer> layers = footprint.sample(0, 0).orElseThrow().layers();
        assertEquals(2, layers.size());
        assertEquals(HydrologyFeatureType.WATERFALL, layers.get(0).feature().type());
        assertEquals(HydrologyFeatureType.SURFACE_POOL, layers.get(1).feature().type());
    }

    @Test
    public void oneCoursePublishesOnlyOneNonFallingSurfaceHeadPerColumn() {
        HydraulicSegment upper = new HydraulicSegment(
                231L,
                230L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(-2, 70, 0), new HydrologyPoint(0, 70, 0))
        );
        HydraulicSegment lower = new HydraulicSegment(
                232L,
                230L,
                HydrologyFeatureType.SURFACE_POOL,
                68,
                68,
                4,
                2,
                false,
                true,
                List.of(new HydrologyPoint(0, 68, 0), new HydrologyPoint(2, 68, 0))
        );
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 1D, "parent");
        RiverFootprint footprint = new HydrologyFootprintCompiler(
                HydrologyPlannerSettings.defaults(),
                (int x, int z) -> terrain,
                request -> request.minimum()
        ).compile(List.of(new RiverCourse(
                230L,
                RiverCourseType.SURFACE,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "water",
                1,
                List.of(),
                List.of(upper, lower)
        )));

        HashSet<Integer> heads = new HashSet<>();
        for (HydrologyColumnLayer layer : footprint.sample(0, 0).orElseThrow().layers()) {
            if (layer.feature().type().isSurface()
                    && layer.channel()
                    && layer.connectedFluid()
                    && layer.fluidOwned()
                    && !layer.fallingFluid()) {
                heads.add(layer.fluidHeadY());
            }
        }
        assertEquals(1, heads.size());
    }

    @Test
    public void oceanClassifiedTerrainPublishesNoSurfaceColumns() {
        HydraulicSegment segment = new HydraulicSegment(
                61L,
                60L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0))
        );
        RiverCourse course = course(60L, RiverCourseType.SURFACE, segment);
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        HydrologyTerrainSample ocean = HydrologyTerrainSample.ocean(62, "ocean");
        AtomicInteger baselineCalls = new AtomicInteger();
        HydrologyTerrainSampler baselineSampler = (int x, int z) -> {
            baselineCalls.incrementAndGet();
            return ocean;
        };
        RiverFootprint baseline = new HydrologyFootprintCompiler(
                settings,
                baselineSampler,
                request -> request.minimum()
        ).compile(List.of(course));
        AtomicInteger detailedCalls = new AtomicInteger();
        HydrologyTerrainSampler detailedSampler = (int x, int z) -> {
            detailedCalls.incrementAndGet();
            return ocean;
        };
        CountingNaturalSampler naturalSampler = new CountingNaturalSampler(
                ocean,
                HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN
        );

        RiverFootprint optimized = new HydrologyFootprintCompiler(
                settings,
                new HydrologyFootprintCompiler.Sampling(
                        detailedSampler,
                        request -> request.minimum(),
                        naturalSampler
                )
        ).compile(List.of(course));

        assertEquals(baseline, optimized);
        assertTrue(baseline.isEmpty());
        assertTrue(optimized.isEmpty());
        assertTrue(baselineCalls.get() > 221);
        assertTrue(naturalSampler.basisCalls() > 0);
        assertEquals(0, detailedCalls.get());
    }

    @Test
    public void naturallySubmergedLandColumnsRejectOwnedSurfaceWritesAtAndBelowSeaLevel() {
        HydraulicSegment segment = new HydraulicSegment(
                71L,
                70L,
                HydrologyFeatureType.SURFACE_POOL,
                63,
                63,
                6,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 63, 0))
        );
        RiverCourse course = course(70L, RiverCourseType.SURFACE, segment);

        for (int naturalHeight : List.of(62, 63)) {
            HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(
                    naturalHeight,
                    0D,
                    "shore"
            );
            RiverFootprint footprint = compiler(terrain).compile(List.of(course));

            assertTrue(footprint.isEmpty());
        }

        HydrologyTerrainSampler raisedTerrain = (int x, int z) -> HydrologyTerrainSample.openLand(
                64,
                0D,
                "shore"
        );
        RiverFootprint raised = compiler(raisedTerrain).compile(List.of(course));

        assertTrue(raised.sample(0, 0).flatMap(HydrologyColumnSample::primarySurfaceFluidLayer).isPresent());
    }

    @Test
    public void naturallySubmergedLandAtAMouthPublishesOnlyANonOwningApron() {
        HydraulicSegment mouth = new HydraulicSegment(
                81L,
                80L,
                HydrologyFeatureType.MOUTH,
                63,
                63,
                6,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 63, 0))
        );
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(63, 0D, "shore");
        RiverFootprint footprint = compiler(terrain).compile(List.of(course(80L, RiverCourseType.SURFACE, mouth)));

        HydrologyColumnSample sample = footprint.sample(0, 0).orElseThrow();
        assertEquals(63, sample.terrainHeight());
        assertTrue(sample.layers().stream().allMatch(HydrologyColumnLayer::oceanApron));
        assertTrue(sample.layers().stream().noneMatch(HydrologyColumnLayer::terrainOwned));
        assertTrue(sample.layers().stream().noneMatch(HydrologyColumnLayer::fluidOwned));
    }

    @Test
    public void validationRetriesDeferTheOnlyFullFootprintMaterialization() {
        HydraulicSegment surface = new HydraulicSegment(
                302L,
                301L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(8, 70, 0))
        );
        HydraulicSegment cave = new HydraulicSegment(
                303L,
                301L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                66,
                66,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(8, 66, 0), new HydrologyPoint(12, 66, 0))
        );
        RiverCourse course = new RiverCourse(
                301L,
                RiverCourseType.SURFACE,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "water",
                1,
                List.of(),
                List.of(surface, cave)
        );
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 1D, "parent");
        AtomicInteger terrainSamples = new AtomicInteger();
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            terrainSamples.incrementAndGet();
            return terrain;
        };
        HydrologyFootprintCompiler compiler = new HydrologyFootprintCompiler(
                HydrologyPlannerSettings.defaults(),
                sampler,
                request -> request.minimum()
        );

        HydrologyFootprintCompiler.ValidationRaster first = compiler.compileValidation(List.of(course));
        HydrologyFootprintCompiler.ValidationRaster retry = compiler.compileValidation(List.of(course));
        int validationSamples = terrainSamples.get();

        assertEquals(0, compiler.fullMaterializationCount());
        assertEquals(first.columns(), retry.columns());
        assertTrue(first.columns().stream().flatMap(
                (HydrologyColumnSample sample) -> sample.layers().stream()
        ).anyMatch((HydrologyColumnLayer layer) -> layer.feature().segmentId() == cave.id()));

        RiverFootprint footprint = compiler.compile(List.of(course));
        RiverFootprint freshFootprint = new HydrologyFootprintCompiler(
                HydrologyPlannerSettings.defaults(),
                (int x, int z) -> terrain,
                request -> request.minimum()
        ).compile(List.of(course));

        assertEquals(1, compiler.fullMaterializationCount());
        assertEquals(validationSamples, terrainSamples.get());
        assertEquals(freshFootprint, footprint);
        for (HydrologyColumnSample sample : footprint.columns().values()) {
            assertEquals(
                    "planned surface at " + sample.x() + "," + sample.z(),
                    sample.terrainHeight(),
                    first.plannedSurface().resolve(sample.x(), sample.z(), sample.naturalHeight())
            );
        }
    }

    @Test
    public void surfaceUndergroundPoolPreservesASolidTerrainFollowingRoof() {
        HydraulicSegment pool = new HydraulicSegment(
                316L,
                315L,
                HydrologyFeatureType.UNDERGROUND_POOL,
                70,
                70,
                6,
                3,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(8, 70, 0))
        );
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(
                x < 4 ? 90 : 77,
                0D,
                "parent"
        );
        RiverFootprint footprint = compiler(terrain).compile(List.of(course(
                315L,
                RiverCourseType.SURFACE,
                pool
        )));

        HydrologyColumnLayer deepRoof = layerAt(footprint, 2, 0);
        HydrologyColumnLayer taperedRoof = layerAt(footprint, 6, 0);
        assertTrue(deepRoof.ceilingY() < footprint.sample(2, 0).orElseThrow().naturalHeight());
        assertEquals(76, taperedRoof.ceilingY());
        for (HydrologyColumnSample sample : footprint.columns().values()) {
            for (HydrologyColumnLayer layer : sample.layers()) {
                if (layer.feature().type() == HydrologyFeatureType.UNDERGROUND_POOL) {
                    assertTrue(layer.ceilingY() < sample.naturalHeight());
                }
            }
        }
    }

    @Test
    public void compactValidationMatchesFullRasterAcrossOverlapsOceanAndSurfaceOnlyCourses() {
        RiverCourse mixed = new RiverCourse(
                401L,
                RiverCourseType.SURFACE,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "water",
                1,
                List.of(),
                List.of(
                        new HydraulicSegment(
                                402L,
                                401L,
                                HydrologyFeatureType.SURFACE_POOL,
                                70,
                                70,
                                6,
                                2,
                                false,
                                false,
                                List.of(new HydrologyPoint(-8, 70, 0), new HydrologyPoint(8, 70, 0))
                        ),
                        new HydraulicSegment(
                                403L,
                                401L,
                                HydrologyFeatureType.UNDERGROUND_POOL,
                                60,
                                60,
                                4,
                                2,
                                false,
                                false,
                                List.of(new HydrologyPoint(8, 60, 0), new HydrologyPoint(14, 60, 0))
                        ),
                        new HydraulicSegment(
                                404L,
                                401L,
                                HydrologyFeatureType.MOUTH,
                                63,
                                63,
                                6,
                                2,
                                false,
                                false,
                                List.of(new HydrologyPoint(14, 63, 0), new HydrologyPoint(20, 63, 0))
                        )
                )
        );
        RiverCourse surfaceOnly = course(
                411L,
                RiverCourseType.SURFACE,
                new HydraulicSegment(
                        412L,
                        411L,
                        HydrologyFeatureType.SURFACE_POOL,
                        74,
                        74,
                        4,
                        2,
                        false,
                        false,
                        List.of(new HydrologyPoint(0, 74, -4), new HydrologyPoint(12, 74, -4))
                )
        );
        RiverCourse underground = new RiverCourse(
                421L,
                RiverCourseType.UNDERGROUND,
                OptionalLong.of(3L),
                OptionalLong.of(4L),
                "water",
                1,
                List.of(),
                List.of(
                        new HydraulicSegment(
                                422L,
                                421L,
                                HydrologyFeatureType.UNDERGROUND_POOL,
                                45,
                                45,
                                4,
                                2,
                                false,
                                false,
                                List.of(new HydrologyPoint(4, 45, 2), new HydrologyPoint(16, 45, 2))
                        ),
                        new HydraulicSegment(
                                423L,
                                421L,
                                HydrologyFeatureType.MOUTH,
                                63,
                                63,
                                4,
                                2,
                                false,
                                false,
                                List.of(new HydrologyPoint(16, 63, 2), new HydrologyPoint(20, 63, 2))
                        )
                )
        );
        HydrologyTerrainSampler terrain = (int x, int z) -> x >= 17
                ? HydrologyTerrainSample.ocean(63, "ocean")
                : HydrologyTerrainSample.openLand(80 + Math.floorMod(z, 3), Math.abs(z), "parent");
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        List<RiverCourse> courses = List.of(mixed, surfaceOnly, underground);
        HydrologyFootprintCompiler compactCompiler = new HydrologyFootprintCompiler(
                settings,
                terrain,
                request -> request.minimum()
        );
        HydrologyFootprintCompiler.ValidationRaster validation = compactCompiler.compileValidation(courses);
        RiverFootprint full = new HydrologyFootprintCompiler(
                settings,
                terrain,
                request -> request.minimum()
        ).compile(courses);

        for (HydrologyColumnSample sample : full.columns().values()) {
            assertEquals(sample.primarySurfaceLayer().isPresent(),
                    validation.plannedSurface().ownsTerrain(sample.x(), sample.z()));
            assertEquals(sample.primarySurfaceLayer().isPresent(),
                    validation.withMaterializedSurface(full).plannedSurface().ownsTerrain(sample.x(), sample.z()));
            assertEquals(
                    "planned surface at " + sample.x() + "," + sample.z(),
                    sample.terrainHeight(),
                    validation.plannedSurface().resolve(
                            sample.x(),
                            sample.z(),
                            terrain.sample(sample.x(), sample.z()).naturalHeight()
                    )
            );
        }
        HashMap<Long, ArrayList<HydrologyColumnLayer>> validationLayers = new HashMap<>();
        for (HydrologyColumnSample column : validation.columns()) {
            HydrologyColumnSample merged = full.sample(column.x(), column.z()).orElseThrow();
            assertEquals(merged.naturalHeight(), column.naturalHeight());
            assertEquals(merged.ocean(), column.ocean());
            assertEquals(merged.parentBiomeKey(), column.parentBiomeKey());
            assertTrue(merged.layers().containsAll(column.layers()));
            validationLayers
                    .computeIfAbsent(RiverFootprint.pack(column.x(), column.z()),
                            (Long key) -> new ArrayList<>())
                    .addAll(column.layers());
        }
        for (HydrologyColumnSample sample : full.columns().values()) {
            for (HydrologyColumnLayer layer : sample.layers()) {
                if (!layer.feature().type().isUnderground()
                        && !layer.feature().type().isDeepFluid()
                        && !layer.oceanApron()) {
                    continue;
                }
                assertTrue(
                        "missing cave layer at " + sample.x() + "," + sample.z(),
                        validationLayers
                                .getOrDefault(RiverFootprint.pack(sample.x(), sample.z()), new ArrayList<>())
                                .contains(layer)
                );
            }
        }
        assertEquals(91, validation.plannedSurface().resolve(80, 80, 91));
        assertEquals(92, validation.plannedSurface().resolve(80, 80, 92));
        assertFalse(validation.plannedSurface().ownsTerrain(80, 80));
        assertFalse(validation.withMaterializedSurface(full).plannedSurface().ownsTerrain(80, 80));
    }

    @Test
    public void adjacentSurfaceSegmentsMatchOneContinuousSurfaceSweep() {
        HydraulicSegment continuous = new HydraulicSegment(
                501L,
                500L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                8,
                3,
                false,
                false,
                List.of(
                        new HydrologyPoint(0, 70, 0),
                        new HydrologyPoint(32, 70, 0),
                        new HydrologyPoint(64, 70, 0)
                )
        );
        HydraulicSegment first = new HydraulicSegment(
                502L,
                500L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                8,
                3,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(32, 70, 0))
        );
        HydraulicSegment second = new HydraulicSegment(
                503L,
                500L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                8,
                3,
                false,
                false,
                List.of(new HydrologyPoint(32, 70, 0), new HydrologyPoint(64, 70, 0))
        );
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(82, 0D, "parent");
        RiverFootprint continuousFootprint = compiler(terrain).compile(List.of(new RiverCourse(
                500L,
                RiverCourseType.SURFACE,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "water",
                1,
                List.of(),
                List.of(continuous)
        )));
        RiverFootprint adjacentFootprint = compiler(terrain).compile(List.of(new RiverCourse(
                500L,
                RiverCourseType.SURFACE,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "water",
                1,
                List.of(),
                List.of(first, second)
        )));

        assertEquals(continuousFootprint.columns().keySet(), adjacentFootprint.columns().keySet());
        for (long packed : continuousFootprint.columns().keySet()) {
            HydrologyColumnLayer continuousLayer = continuousFootprint.columns().get(packed)
                    .primarySurfaceLayer()
                    .orElse(null);
            HydrologyColumnLayer adjacentLayer = adjacentFootprint.columns().get(packed)
                    .primarySurfaceLayer()
                    .orElse(null);
            assertEquals(continuousLayer == null, adjacentLayer == null);
            if (continuousLayer == null) {
                continue;
            }
            assertEquals(continuousLayer.channel(), adjacentLayer.channel());
            assertEquals(continuousLayer.shore(), adjacentLayer.shore());
            assertEquals(continuousLayer.grading(), adjacentLayer.grading());
            assertEquals(continuousLayer.fluidHeadY(), adjacentLayer.fluidHeadY());
        }
    }

    @Test
    public void continuousSurfaceBendHasNoJoinLayerOrDiagonalSpur() {
        HydraulicSegment first = new HydraulicSegment(
                511L,
                510L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                8,
                3,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(32, 70, 0))
        );
        HydraulicSegment second = new HydraulicSegment(
                512L,
                510L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                8,
                3,
                false,
                false,
                List.of(new HydrologyPoint(32, 70, 0), new HydrologyPoint(32, 70, 32))
        );
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(82, 0D, "parent");
        RiverFootprint footprint = compiler(terrain).compile(List.of(new RiverCourse(
                510L,
                RiverCourseType.SURFACE,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "water",
                1,
                List.of(),
                List.of(first, second)
        )));

        HydrologyColumnSample join = footprint.sample(32, 0).orElseThrow();
        long joinedChannels = join.layers().stream()
                .filter((HydrologyColumnLayer layer) -> layer.feature().type().isSurface() && layer.channel())
                .count();
        assertEquals(1L, joinedChannels);
        assertTrue(join.primarySurfaceFluidLayer().isPresent());
        assertTrue(surfaceFluidLayer(footprint, 39, -7) == null);
    }

    @Test
    public void surfaceHeadwaterOpensAsASpringPoolThatNarrowsToTheChannel() {
        HydraulicSegment segment = new HydraulicSegment(
                521L,
                520L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                8,
                3,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(64, 70, 0))
        );
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(82, 0D, "parent");
        RiverCourse course = course(520L, RiverCourseType.SURFACE, segment);
        HydrologyFootprintCompiler compiler = compiler(terrain);
        RiverFootprint footprint = compiler.compile(List.of(course));

        assertTrue(wetHalfWidth(footprint, 0) > wetHalfWidth(footprint, 32));
        assertTrue(wetHalfWidth(footprint, 0) >= 2 * wetHalfWidth(footprint, 56));
        assertTrue(Math.abs(wetHalfWidth(footprint, 32) - wetHalfWidth(footprint, 56)) <= 1);
        for (int x = 1; x <= 24; x++) {
            assertTrue(wetHalfWidth(footprint, x) <= wetHalfWidth(footprint, x - 1) + 1);
        }
        HydrologyColumnLayer source = surfaceFluidLayer(footprint, 0, 0);
        HydrologyColumnLayer cruise = surfaceFluidLayer(footprint, 56, 0);
        assertTrue(source != null);
        assertTrue(cruise != null);
        assertTrue(source.feature().source());
        assertFalse(surfaceFluidLayer(footprint, 0, 3).feature().source());
        int sourceDepth = source.fluidHeadY() - source.bedY();
        int cruiseDepth = cruise.fluidHeadY() - cruise.bedY();
        assertTrue(sourceDepth >= cruiseDepth + 1);
    }

    @Test
    public void descendingSurfaceBanksRemainAboveEveryAdjacentFluidHead() {
        HydraulicSegment cascade = new HydraulicSegment(
                528L,
                527L,
                HydrologyFeatureType.CASCADE,
                74,
                70,
                8,
                3,
                false,
                false,
                List.of(new HydrologyPoint(0, 74, 0), new HydrologyPoint(24, 70, 0))
        );
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(82, 0D, "parent");
        RiverFootprint footprint = compiler(terrain).compile(List.of(course(527L, RiverCourseType.SURFACE, cascade)));
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (HydrologyColumnSample sample : footprint.columns().values()) {
            HydrologyColumnLayer fluid = sample.primarySurfaceFluidLayer().orElse(null);
            if (fluid == null) {
                continue;
            }
            for (int[] offset : offsets) {
                HydrologyColumnSample neighbor = footprint.sample(
                        sample.x() + offset[0],
                        sample.z() + offset[1]
                ).orElse(null);
                assertTrue(neighbor != null);
                if (neighbor.primarySurfaceFluidLayer().isPresent()) {
                    continue;
                }
                assertTrue(neighbor.terrainHeight() >= fluid.fluidHeadY());
            }
        }
    }

    @Test
    public void aOneBlockLowBankIsLippedAndADeeperOneIsReportedWithoutRaising() {
        HydraulicSegment channel = new HydraulicSegment(
                529L,
                528L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                8,
                3,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(24, 70, 0))
        );
        RiverCourse course = course(528L, RiverCourseType.SURFACE, channel);
        HydrologyTerrainSampler highTerrain = (int x, int z) ->
                HydrologyTerrainSample.openLand(82, 0D, "parent");
        HydrologyFootprintCompiler highCompiler = compiler(highTerrain);
        RiverFootprint highFootprint = highCompiler.compile(List.of(course));
        int bankZ = 0;
        while (surfaceFluidLayer(highFootprint, 12, bankZ) != null) {
            bankZ++;
        }
        int lowBankZ = bankZ;
        HydrologyTerrainSampler lowBankTerrain = (int x, int z) -> HydrologyTerrainSample.openLand(
                x == 12 && z == lowBankZ ? 69 : 82,
                0D,
                "parent"
        );
        HydrologyFootprintCompiler lowBankCompiler = compiler(lowBankTerrain);
        RiverFootprint lowBankFootprint = lowBankCompiler.compile(List.of(course));
        HydrologyColumnSample lowBank = lowBankFootprint.sample(12, lowBankZ).orElseThrow();
        HydrologyTerrainSampler deepBankTerrain = (int x, int z) -> HydrologyTerrainSample.openLand(
                x == 12 && z == lowBankZ ? 68 : 82,
                0D,
                "parent"
        );
        RiverFootprint deepBankFootprint = compiler(deepBankTerrain).compile(List.of(course));
        HydrologyColumnSample deepBank = deepBankFootprint.sample(12, lowBankZ).orElseThrow();

        assertEquals(0, unsupportedBankCells(highFootprint, 528L));
        // One block short of the water: a one-block lip holds it and nothing is reported.
        assertEquals(69, lowBank.naturalHeight());
        assertEquals(70, lowBank.terrainHeight());
        assertEquals(0, unsupportedBankCells(lowBankFootprint, 528L));
        for (HydrologyColumnSample sample : lowBankFootprint.columns().values()) {
            assertTrue(sample.terrainHeight() <= sample.naturalHeight() + 1);
        }
        // Two blocks short: the bank keeps its natural height and the edge is reported.
        assertEquals(68, deepBank.naturalHeight());
        assertEquals(68, deepBank.terrainHeight());
        assertTrue(unsupportedBankCells(deepBankFootprint, 528L) > 0);
    }

    @Test
    public void publishedSurfaceChannelStaysWithinTheConfiguredIncision() {
        HydraulicSegment channel = new HydraulicSegment(
                529L,
                528L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                8,
                3,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(24, 70, 0))
        );
        RiverCourse course = course(528L, RiverCourseType.SURFACE, channel);
        HydrologyFootprintCompiler compiler = compiler((int x, int z) ->
                HydrologyTerrainSample.openLand(76, 0D, "parent"));

        RiverFootprint footprint = compiler.compile(List.of(course));

        assertTrue(
                "center=" + footprint.sample(12, 0),
                maximumSurfaceCut(footprint, 528L)
                        <= HydrologyPlannerSettings.defaults().surface().maximumIncision()
        );
    }

    @Test
    public void surfaceContainmentRejectsAnUnpublishedCascadeCenterline() {
        HydraulicSegment cascade = new HydraulicSegment(
                530L,
                529L,
                HydrologyFeatureType.CASCADE,
                74,
                70,
                8,
                3,
                false,
                true,
                List.of(
                        new HydrologyPoint(0, 74, 0),
                        new HydrologyPoint(1, 73, 0),
                        new HydrologyPoint(2, 72, 0),
                        new HydrologyPoint(3, 71, 0),
                        new HydrologyPoint(4, 70, 0)
                )
        );
        RiverCourse course = course(529L, RiverCourseType.SURFACE, cascade);
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.ocean(82, "ocean");
        HydrologyFootprintCompiler compiler = compiler(terrain);

        assertTrue(compiler.compile(List.of(course)).isEmpty());
    }

    @Test
    public void oceanMouthWidensGraduallyIntoItsTerminalGrade() {
        HydraulicSegment channel = new HydraulicSegment(
                531L,
                530L,
                HydrologyFeatureType.SURFACE_POOL,
                63,
                63,
                8,
                3,
                false,
                false,
                List.of(new HydrologyPoint(0, 63, 0), new HydrologyPoint(32, 63, 0))
        );
        HydraulicSegment mouth = new HydraulicSegment(
                532L,
                530L,
                HydrologyFeatureType.MOUTH,
                63,
                63,
                8,
                3,
                false,
                false,
                List.of(new HydrologyPoint(32, 63, 0), new HydrologyPoint(64, 63, 0))
        );
        HydrologyTerrainSampler terrain = (int x, int z) -> x >= 64
                ? HydrologyTerrainSample.ocean(48, "ocean")
                : HydrologyTerrainSample.openLand(76, 0D, "parent");
        RiverCourse course = new RiverCourse(
                530L,
                RiverCourseType.SURFACE,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "water",
                1,
                List.of(),
                List.of(channel, mouth)
        );
        HydrologyFootprintCompiler compiler = compiler(terrain);
        RiverFootprint footprint = compiler.compile(List.of(course));

        int upstreamWidth = wetHalfWidth(footprint, 32);
        int transitionWidth = wetHalfWidth(footprint, 52);
        int terminalWidth = wetHalfWidth(footprint, 62);
        assertTrue(transitionWidth >= upstreamWidth);
        assertTrue(terminalWidth >= transitionWidth);
        assertTrue(terminalWidth > upstreamWidth);
        assertTrue(surfaceFluidLayer(footprint, 63, 0) != null);
        HydrologyColumnSample apron = footprint.sample(64, 0).orElseThrow();
        assertTrue(apron.ocean());
        assertFalse(apron.layers().isEmpty());
        int apronLimit = HydrologyPlannerSettings.defaults().outlets().maximumOceanApron();
        assertTrue(footprint.sample(64 + apronLimit, 0).isEmpty());
        for (HydrologyColumnSample sample : footprint.columns().values()) {
            if (!sample.ocean()) {
                assertTrue(sample.naturalHeight() > sample.seaLevel());
                continue;
            }
            assertTrue(sample.x() - 64 < apronLimit);
            for (HydrologyColumnLayer layer : sample.layers()) {
                assertTrue(layer.oceanApron());
                assertFalse(layer.terrainOwned());
                assertFalse(layer.fluidOwned());
                assertFalse(layer.shore());
                assertFalse(layer.grading());
            }
        }
    }

    @Test
    public void surfaceSinkholeContinuesThroughOneBorePortal() {
        HydraulicSegment channel = new HydraulicSegment(
                541L,
                540L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                8,
                3,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(48, 70, 0))
        );
        HydraulicSegment sinkhole = new HydraulicSegment(
                542L,
                540L,
                HydrologyFeatureType.SINKHOLE,
                70,
                60,
                4,
                2,
                false,
                true,
                List.of(new HydrologyPoint(48, 70, 0), new HydrologyPoint(52, 60, 0))
        );
        HydraulicSegment grotto = new HydraulicSegment(
                543L,
                540L,
                HydrologyFeatureType.INLAND_GROTTO,
                60,
                60,
                8,
                4,
                false,
                true,
                List.of(new HydrologyPoint(52, 60, 0))
        );
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(82, 0D, "parent");
        RiverFootprint footprint = compiler(terrain).compile(List.of(new RiverCourse(
                540L,
                RiverCourseType.SURFACE,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "water",
                1,
                List.of(),
                List.of(channel, sinkhole, grotto)
        )));

        HydrologyColumnLayer entry = footprint.sample(48, 0).orElseThrow().layers().stream()
                .filter((HydrologyColumnLayer layer) -> layer.feature().segmentId() == sinkhole.id())
                .findFirst()
                .orElseThrow();
        HydrologyColumnLayer receiver = footprint.sample(52, 0).orElseThrow().layers().stream()
                .filter((HydrologyColumnLayer layer) -> layer.feature().segmentId() == sinkhole.id())
                .findFirst()
                .orElseThrow();
        assertTrue(wetHalfWidth(footprint, 46) >= wetHalfWidth(footprint, 24));
        assertTrue(entry.channel());
        assertTrue(entry.connectedFluid());
        assertFalse(entry.receivingPool());
        assertTrue(receiver.receivingPool());
    }

    @Test
    public void midCourseReceivingPoolWidensWithoutAHalfEdgeSpur() {
        HydraulicSegment approach = new HydraulicSegment(
                551L,
                550L,
                HydrologyFeatureType.SURFACE_POOL,
                70,
                70,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(64, 70, 0))
        );
        HydraulicSegment receiver = new HydraulicSegment(
                552L,
                550L,
                HydrologyFeatureType.CASCADE,
                70,
                69,
                4,
                2,
                false,
                true,
                List.of(new HydrologyPoint(64, 70, 0), new HydrologyPoint(80, 69, 0))
        );
        HydraulicSegment outflow = new HydraulicSegment(
                553L,
                550L,
                HydrologyFeatureType.SURFACE_POOL,
                69,
                69,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(80, 69, 0), new HydrologyPoint(160, 69, 0))
        );
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(82, 0D, "parent");
        RiverFootprint footprint = compiler(terrain).compile(List.of(new RiverCourse(
                550L,
                RiverCourseType.SURFACE,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "water",
                1,
                List.of(),
                List.of(approach, receiver, outflow)
        )));

        int previousWidth = wetHalfWidth(footprint, 40);
        for (int x = 44; x <= 120; x += 4) {
            int width = wetHalfWidth(footprint, x);
            assertTrue(Math.abs(width - previousWidth) <= 1);
            previousWidth = width;
        }
    }




    @Test
    public void coastalGrottoApronReachesTheChamberRadius() {
        HydrologyTerrainSampler coast = (int x, int z) -> x >= 112
                ? HydrologyTerrainSample.ocean(48, "ocean")
                : HydrologyTerrainSample.openLand(80, 0D, "parent");
        RiverCourse seaCave = seaCaveCourse(900L);
        RiverFootprint footprint = new HydrologyFootprintCompiler(
                seaCaveSettings(8, 12),
                coast,
                request -> request.minimum()
        ).compile(List.of(seaCave));
        RiverFootprint wideApron = new HydrologyFootprintCompiler(
                seaCaveSettings(12, 12),
                coast,
                request -> request.minimum()
        ).compile(List.of(seaCave));

        int chamberColumns = 0;
        int apronColumns = 0;
        double farthestApron = 0D;
        for (HydrologyColumnSample sample : footprint.columns().values()) {
            for (HydrologyColumnLayer layer : sample.layers()) {
                if (layer.oceanApron()) {
                    apronColumns++;
                    assertTrue(sample.ocean());
                    farthestApron = Math.max(farthestApron, StrictMath.hypot(sample.x() - 112, sample.z()));
                    assertTrue(
                            "apron beyond the chamber at " + sample.x() + "," + sample.z(),
                            nearestCenterlineDistance(seaCave, sample.x(), sample.z()) <= 12.25D
                                    || touchesChamber(footprint, sample.x(), sample.z())
                    );
                    continue;
                }
                if (!chamberLayer(layer)) {
                    continue;
                }
                chamberColumns++;
                assertFalse(sample.ocean());
                for (int[] cardinal : CARDINALS) {
                    int neighborX = sample.x() + cardinal[0];
                    int neighborZ = sample.z() + cardinal[1];
                    if (!coast.sample(neighborX, neighborZ).ocean()) {
                        continue;
                    }
                    assertTrue(
                            "sea column " + neighborX + "," + neighborZ + " beside the chamber has no apron",
                            apronAt(footprint, neighborX, neighborZ)
                    );
                }
            }
        }
        assertTrue(chamberColumns > 0);
        assertTrue(apronColumns > 0);
        assertTrue("farthest apron " + farthestApron, farthestApron > 8.25D);
        assertEquals(wideApron, footprint);

        HydraulicSegment mouth = new HydraulicSegment(
                902L,
                901L,
                HydrologyFeatureType.MOUTH,
                63,
                63,
                8,
                3,
                false,
                false,
                List.of(new HydrologyPoint(96, 63, 0), new HydrologyPoint(112, 63, 0))
        );
        RiverFootprint mouthFootprint = new HydrologyFootprintCompiler(
                seaCaveSettings(8, 12),
                coast,
                request -> request.minimum()
        ).compile(List.of(course(901L, RiverCourseType.SURFACE, mouth)));
        assertTrue(mouthFootprint.sample(112, 0).isPresent());
        assertTrue(mouthFootprint.sample(112 + 8, 0).isEmpty());
        for (HydrologyColumnSample sample : mouthFootprint.columns().values()) {
            if (sample.ocean()) {
                assertTrue(sample.x() - 112 < 8);
            }
        }
    }

    @Test
    public void seaCaveChamberStaysUnderTheNaturalSurface() {
        HydrologyTerrainSampler coast = this::cliffCoast;
        RiverCourse seaCave = seaCaveCourse(910L);
        RiverFootprint footprint = new HydrologyFootprintCompiler(
                seaCaveSettings(8, 12),
                coast,
                request -> request.minimum()
        ).compile(List.of(seaCave));

        int chamberColumns = 0;
        for (HydrologyColumnSample sample : footprint.columns().values()) {
            for (HydrologyColumnLayer layer : sample.layers()) {
                if (!chamberLayer(layer)) {
                    continue;
                }
                chamberColumns++;
                String where = "chamber at " + sample.x() + "," + sample.z() + " " + layer;
                assertFalse(where, sample.ocean());
                assertTrue(where, sample.naturalHeight() != 64);
                assertEquals(where, 63, layer.fluidHeadY());
                assertTrue(where, layer.bedY() < 63);
                assertTrue(where, layer.ceilingY() >= 64);
                assertTrue(where, layer.ceilingY() <= sample.naturalHeight() - 1);
                for (int[] cardinal : CARDINALS) {
                    HydrologyTerrainSample neighbor = coast.sample(sample.x() + cardinal[0], sample.z() + cardinal[1]);
                    if (neighbor.ocean()) {
                        continue;
                    }
                    assertTrue(where, layer.ceilingY() <= neighbor.naturalHeight() - 1);
                }
            }
        }
        assertTrue(chamberColumns > 0);
        HydrologyColumnLayer deepRoof = chamberLayerAt(footprint, 100, 0);
        assertTrue(deepRoof != null);
        assertEquals(73, deepRoof.ceilingY());
        HydrologyColumnLayer lowRoof = chamberLayerAt(footprint, 108, 0);
        assertTrue(lowRoof != null);
        assertEquals(65, lowRoof.ceilingY());
        HydrologyColumnLayer face = chamberLayerAt(footprint, 111, 0);
        assertTrue(face != null);
        assertTrue(face.ceilingY() >= 64 && face.ceilingY() <= 73);
        assertTrue(chamberLayerAt(footprint, 111, 9) == null);
        assertTrue(chamberLayerAt(footprint, 100, 9) == null);
    }

    private HydrologyTerrainSample cliffCoast(int x, int z) {
        if (x >= 112) {
            return HydrologyTerrainSample.ocean(48, "ocean");
        }
        if (z >= 9) {
            return HydrologyTerrainSample.openLand(64, 0D, "beach");
        }
        if (x >= 110) {
            return HydrologyTerrainSample.openLand(74, 0D, "face");
        }
        if (x >= 104) {
            return HydrologyTerrainSample.openLand(66, 0D, "low");
        }
        return HydrologyTerrainSample.openLand(92, 0D, "cliff");
    }

    private RiverCourse seaCaveCourse(long id) {
        HydraulicSegment chamber = new HydraulicSegment(
                id + 1L,
                id,
                HydrologyFeatureType.COASTAL_GROTTO,
                63,
                63,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(100, 63, 0), new HydrologyPoint(112, 63, 0))
        );
        return new RiverCourse(
                id,
                RiverCourseType.SEA_CAVE,
                OptionalLong.empty(),
                OptionalLong.empty(),
                "water",
                1,
                List.of(),
                List.of(chamber)
        );
    }

    @Test
    public void waterfallThalwegFractionDefaultsReproduceTheThroatBed() {
        HydraulicSegment segment = new HydraulicSegment(
                121L,
                120L,
                HydrologyFeatureType.WATERFALL,
                90,
                70,
                16,
                12,
                true,
                false,
                List.of(new HydrologyPoint(0, 90, 0), new HydrologyPoint(32, 70, 0))
        );
        RiverCourse course = course(120L, RiverCourseType.SURFACE, segment);
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(96, 1D, "parent");

        RiverFootprint defaults = compile(HydrologyPlannerSettings.defaults(), terrain, course);
        RiverFootprint explicit = compile(withFlow(new HydrologyPlannerSettings.Flow(0.65D, 2, 2D, 1)), terrain, course);
        RiverFootprint broad = compile(withFlow(new HydrologyPlannerSettings.Flow(0.2D, 2, 2D, 1)), terrain, course);

        assertEquals(defaults, explicit);
        int shallower = 0;
        for (HydrologyColumnSample sample : defaults.columns().values()) {
            HydrologyColumnLayer deepThalweg = surfaceFluidLayer(defaults, sample.x(), sample.z());
            HydrologyColumnLayer broadThalweg = surfaceFluidLayer(broad, sample.x(), sample.z());
            if (deepThalweg == null || broadThalweg == null || !deepThalweg.channel() || deepThalweg.fallingFluid()) {
                continue;
            }
            String where = "bed at " + sample.x() + "," + sample.z();
            assertTrue(where, broadThalweg.bedY() >= deepThalweg.bedY());
            if (broadThalweg.bedY() > deepThalweg.bedY()) {
                shallower++;
            }
        }
        assertTrue("no column changed depth", shallower > 0);
    }

    @Test
    public void radialKnobsDefaultsReproduceThePassageAndANarrowerBaseShrinksIt() {
        RiverCourse course = undergroundCourse(130L, 24);
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(84, 1D, "parent");
        HydrologyPlannerSettings.ChannelShape shorthand = HydrologyPlannerSettings.ChannelShape.of(2.4D, 0.28D, 0.24D, 11);
        HydrologyPlannerSettings.ChannelShape explicit = new HydrologyPlannerSettings.ChannelShape(
                2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, 1.18D, 0.08D, 0.06D, 0D, 0.62D, 0.2D);
        HydrologyPlannerSettings.ChannelShape narrow = new HydrologyPlannerSettings.ChannelShape(
                2.4D, 0.28D, 0.24D, 11, 0.6D, 0.58D, 1.18D, 0.08D, 0.06D, 0D, 0.62D, 0.2D);

        RiverFootprint defaults = compile(HydrologyPlannerSettings.defaults(), terrain, course);
        RiverFootprint shorthandFootprint = compile(withUndergroundShape(shorthand), terrain, course);
        RiverFootprint explicitFootprint = compile(withUndergroundShape(explicit), terrain, course);
        RiverFootprint narrowFootprint = compile(withUndergroundShape(narrow), terrain, course);

        assertEquals(defaults, shorthandFootprint);
        assertEquals(defaults, explicitFootprint);
        assertTrue(
                "narrow " + narrowFootprint.columns().size() + " vs " + defaults.columns().size(),
                narrowFootprint.columns().size() < defaults.columns().size()
        );
    }

    @Test
    public void ceilingRoughnessZeroKeepsTheRoofSmoothAndAPositiveValueVariesIt() {
        RiverCourse course = undergroundCourse(135L, 0);
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(84, 1D, "parent");
        HydrologyPlannerSettings.ChannelShape smooth = new HydrologyPlannerSettings.ChannelShape(
                2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, 1.18D, 0.08D, 0.06D, 0D, 0.62D, 0.2D);
        HydrologyPlannerSettings.ChannelShape rough = new HydrologyPlannerSettings.ChannelShape(
                2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, 1.18D, 0.08D, 0.06D, 0.5D, 0.62D, 0.2D);

        RiverFootprint defaults = compile(HydrologyPlannerSettings.defaults(), terrain, course);
        RiverFootprint smoothFootprint = compile(withUndergroundShape(smooth), terrain, course);
        RiverFootprint roughFootprint = compile(withUndergroundShape(rough), terrain, course);

        assertEquals(defaults, smoothFootprint);
        assertTrue(axisCeilings(roughFootprint).size() > 1);
        assertEquals(1, axisCeilings(smoothFootprint).size());
    }

    @Test
    public void grottoAspectRangeZeroGivesACircularPlanAtAspectOne() {
        HydraulicSegment chamber = new HydraulicSegment(
                141L,
                140L,
                HydrologyFeatureType.INLAND_GROTTO,
                48,
                48,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 48, 0))
        );
        RiverCourse course = course(140L, RiverCourseType.UNDERGROUND, chamber);
        HydrologyTerrainSampler terrain = (int x, int z) -> HydrologyTerrainSample.openLand(84, 1D, "parent");
        HydrologyPlannerSettings.ChannelShape circle = new HydrologyPlannerSettings.ChannelShape(
                2.4D, 0D, 0D, 11, 0.86D, 0.58D, 1.18D, 0D, 0D, 0D, 1D, 0D);
        HydrologyPlannerSettings.ChannelShape ellipse = new HydrologyPlannerSettings.ChannelShape(
                2.4D, 0D, 0D, 11, 0.86D, 0.58D, 1.18D, 0D, 0D, 0D, 0.62D, 0D);

        RiverFootprint circular = compile(withGrottoShape(circle), terrain, course);
        RiverFootprint elliptical = compile(withGrottoShape(ellipse), terrain, course);

        int east = planExtent(circular, 1, 0);
        assertTrue(east > 0);
        assertEquals(east, planExtent(circular, -1, 0));
        assertEquals(east, planExtent(circular, 0, 1));
        assertEquals(east, planExtent(circular, 0, -1));
        assertTrue(
                "elliptical " + elliptical.columns().size() + " vs " + circular.columns().size(),
                elliptical.columns().size() < circular.columns().size()
        );
    }

    /** Distinct ceilings over the centreline axis of a straight underground passage. */
    private static HashSet<Integer> axisCeilings(RiverFootprint footprint) {
        HashSet<Integer> ceilings = new HashSet<>();
        for (int x = 0; x <= 48; x++) {
            HydrologyColumnSample sample = footprint.sample(x, 0).orElse(null);
            if (sample == null) {
                continue;
            }
            for (HydrologyColumnLayer layer : sample.layers()) {
                if (layer.channel()) {
                    ceilings.add(layer.ceilingY());
                }
            }
        }
        return ceilings;
    }

    /** How far the published plan reaches from the origin along one cardinal. */
    private static int planExtent(RiverFootprint footprint, int stepX, int stepZ) {
        int reach = 0;
        while (footprint.sample(stepX * (reach + 1), stepZ * (reach + 1)).isPresent()) {
            reach++;
        }
        return reach;
    }

    private static RiverCourse undergroundCourse(long id, int bend) {
        HydraulicSegment segment = new HydraulicSegment(
                id + 1L,
                id,
                HydrologyFeatureType.UNDERGROUND_POOL,
                48,
                48,
                12,
                2,
                false,
                false,
                bend == 0
                        ? List.of(new HydrologyPoint(0, 48, 0), new HydrologyPoint(48, 48, 0))
                        : List.of(
                                new HydrologyPoint(0, 48, 0),
                                new HydrologyPoint(bend / 2, 48, 5),
                                new HydrologyPoint(bend, 48, 0)
                        )
        );
        return new RiverCourse(
                id,
                RiverCourseType.UNDERGROUND,
                OptionalLong.of(id + 2L),
                OptionalLong.of(id + 3L),
                "water",
                1,
                List.of(),
                List.of(segment)
        );
    }

    private static RiverFootprint compile(
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler terrain,
            RiverCourse course
    ) {
        return new HydrologyFootprintCompiler(settings, terrain, request -> request.minimum()).compile(List.of(course));
    }

    private static HydrologyPlannerSettings withGeometry(HydrologyPlannerSettings.Geometry geometry) {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        return new HydrologyPlannerSettings(
                base.seaLevel(), base.routing(), base.surface(), base.hydraulics(), base.underground(),
                base.outlets(), geometry, base.deepFluids(), base.surfacePools(), base.widestShoreBiomeWidth(),
                base.seaCaves());
    }

    private static HydrologyPlannerSettings withUndergroundShape(HydrologyPlannerSettings.ChannelShape shape) {
        HydrologyPlannerSettings.Geometry base = HydrologyPlannerSettings.defaults().geometry();
        return withGeometry(new HydrologyPlannerSettings.Geometry(
                base.meanders(), base.surface(), shape, base.grottos(), base.drops()));
    }

    private static HydrologyPlannerSettings withGrottoShape(HydrologyPlannerSettings.ChannelShape shape) {
        HydrologyPlannerSettings.Geometry base = HydrologyPlannerSettings.defaults().geometry();
        return withGeometry(new HydrologyPlannerSettings.Geometry(
                base.meanders(), base.surface(), base.underground(), shape, base.drops()));
    }

    private static HydrologyPlannerSettings withFlow(HydrologyPlannerSettings.Flow flow) {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Banks banks = base.surface().banks();
        HydrologyPlannerSettings.Banks tuned = new HydrologyPlannerSettings.Banks(
                banks.sink(), banks.blendSlope(), banks.minimumBlendWidth(), banks.maximumBlendWidth(),
                banks.roughness(), banks.roughnessWavelength(), banks.cascadeRun(), banks.waterfallMinimumDrop(),
                banks.mouthFlareRatio(), banks.inlet(), banks.springWidthRatio(), banks.springLength(),
                banks.exposeCutStrata(), banks.erosion(), banks.ponds(), banks.channel(), flow);
        HydrologyPlannerSettings.Surface surface = base.surface();
        HydrologyPlannerSettings.Surface tunedSurface = new HydrologyPlannerSettings.Surface(
                surface.enabled(), surface.sources(), surface.minimumWidth(), surface.maximumWidth(),
                surface.minimumDepth(), surface.maximumDepth(), surface.maximumIncision(), surface.shoreWidth(), tuned);
        return new HydrologyPlannerSettings(
                base.seaLevel(), base.routing(), tunedSurface, base.hydraulics(), base.underground(),
                base.outlets(), base.geometry(), base.deepFluids(), base.surfacePools(), base.widestShoreBiomeWidth(),
                base.seaCaves());
    }

    private static HydrologyPlannerSettings seaCaveSettings(int maximumOceanApron, int chamberRadius) {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Outlets outlets = HydrologyPlannerSettings.Outlets.of(
                true,
                new HydrologyPlannerSettings.Grotto(true, chamberRadius, 7, 10, 32768),
                base.outlets().inlandGrotto(),
                base.outlets().surfaceSinkholesEnabled(),
                base.outlets().coastalCliffMinimumHeight(),
                base.outlets().mouthLevelingDistance(),
                maximumOceanApron,
                base.outlets().maximumPerTile(),
                base.outlets().maximumPerTile()
        );
        HydrologyPlannerSettings.Geometry geometry = new HydrologyPlannerSettings.Geometry(
                base.geometry().meanders(),
                base.geometry().surface(),
                base.geometry().underground(),
                HydrologyPlannerSettings.ChannelShape.of(2.4D, 0D, 0D, 11),
                base.geometry().drops()
        );
        return new HydrologyPlannerSettings(
                base.seaLevel(),
                base.routing(),
                base.surface(),
                base.hydraulics(),
                base.underground(),
                outlets,
                geometry,
                base.deepFluids(),
                base.surfacePools(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }

    private static boolean chamberLayer(HydrologyColumnLayer layer) {
        return layer.feature().type() == HydrologyFeatureType.COASTAL_GROTTO
                && layer.terrainOwned()
                && !layer.oceanApron();
    }

    private HydrologyColumnLayer chamberLayerAt(RiverFootprint footprint, int x, int z) {
        return footprint.sample(x, z)
                .flatMap((HydrologyColumnSample sample) -> sample.layers().stream()
                        .filter(HydrologyFootprintCompilerTest::chamberLayer)
                        .findFirst())
                .orElse(null);
    }

    private boolean apronAt(RiverFootprint footprint, int x, int z) {
        return footprint.sample(x, z)
                .map((HydrologyColumnSample sample) -> sample.layers().stream().anyMatch(HydrologyColumnLayer::oceanApron))
                .orElse(false);
    }

    private boolean touchesChamber(RiverFootprint footprint, int x, int z) {
        for (int[] cardinal : CARDINALS) {
            if (chamberLayerAt(footprint, x + cardinal[0], z + cardinal[1]) != null) {
                return true;
            }
        }
        return false;
    }

    private double nearestCenterlineDistance(RiverCourse course, int x, int z) {
        double nearest = Double.MAX_VALUE;
        for (HydraulicSegment segment : course.segments()) {
            for (HydrologyPoint point : segment.centerline()) {
                nearest = Math.min(nearest, StrictMath.hypot(x - point.x(), z - point.z()));
            }
        }
        return nearest;
    }

    private RiverCourse course(long id, RiverCourseType type, HydraulicSegment segment) {
        return new RiverCourse(
                id,
                type,
                OptionalLong.of(id + 1L),
                OptionalLong.of(id + 2L),
                "water",
                1,
                List.of(),
                List.of(segment)
        );
    }

    private HydrologyColumnLayer layerAt(RiverFootprint footprint, int x, int z) {
        return footprint.sample(x, z).orElseThrow().layers().getFirst();
    }

    private HydrologyColumnLayer surfaceFluidLayer(RiverFootprint footprint, int x, int z) {
        return footprint.sample(x, z)
                .flatMap(HydrologyColumnSample::primarySurfaceFluidLayer)
                .orElse(null);
    }

    private HydrologyFootprintCompiler compiler(HydrologyTerrainSampler terrain) {
        return new HydrologyFootprintCompiler(
                HydrologyPlannerSettings.defaults(),
                terrain,
                request -> request.minimum()
        );
    }

    private int wetHalfWidth(RiverFootprint footprint, int x) {
        int halfWidth = 0;
        for (int z = 0; z <= 32; z++) {
            if (surfaceFluidLayer(footprint, x, z) == null) {
                break;
            }
            halfWidth = z;
        }
        return halfWidth;
    }

    private int connectedColumnCount(HashSet<Long> columns) {
        if (columns.isEmpty()) {
            return 0;
        }
        HashSet<Long> visited = new HashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(columns.iterator().next());
        while (!pending.isEmpty()) {
            long packed = pending.removeFirst();
            if (!visited.add(packed)) {
                continue;
            }
            int x = RiverFootprint.unpackX(packed);
            int z = RiverFootprint.unpackZ(packed);
            long[] neighbors = {
                    RiverFootprint.pack(x - 1, z),
                    RiverFootprint.pack(x + 1, z),
                    RiverFootprint.pack(x, z - 1),
                    RiverFootprint.pack(x, z + 1)
            };
            for (long neighbor : neighbors) {
                if (columns.contains(neighbor) && !visited.contains(neighbor)) {
                    pending.addLast(neighbor);
                }
            }
        }
        return visited.size();
    }

    private static final class SlopeCountingSampler implements HydrologyNaturalTerrainSampler, HydrologyTerrainSampler {
        private final boolean omitSlope;
        private int basisCalls;
        private int slopeFreeCalls;
        private int detailedCalls;

        private SlopeCountingSampler(boolean omitSlope) {
            this.omitSlope = omitSlope;
        }

        @Override
        public HydrologyTerrainSample sample(int x, int z) {
            detailedCalls++;
            return terrain(x, z);
        }

        @Override
        public HydrologyTerrainSample sampleBasis(int x, int z) {
            basisCalls++;
            return terrain(x, z);
        }

        @Override
        public HydrologyTerrainSample sampleBasisWithoutSlope(int x, int z) {
            if (!omitSlope) {
                return sampleBasis(x, z);
            }
            slopeFreeCalls++;
            return terrain(x, z).withSlope(0D);
        }

        @Override
        public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NaturalClassification classifyNatural(int x, int z) {
            return x >= 24 ? NaturalClassification.OCEAN : NaturalClassification.LAND;
        }

        private HydrologyTerrainSample terrain(int x, int z) {
            double slope = 1D + Math.floorMod(x * 13 - z * 7, 101) * 0.5D;
            int relief = Math.floorMod(x * 3 + z * 5, 7);
            return x >= 24
                    ? HydrologyTerrainSample.ocean(52 + relief, "ocean").withSlope(slope)
                    : HydrologyTerrainSample.openLand(80 + relief, slope, z < 0 ? "north" : "south");
        }
    }

    private static final class CountingNaturalSampler implements HydrologyNaturalTerrainSampler {
        private final HydrologyTerrainSample basis;
        private final NaturalClassification classification;
        private final AtomicInteger basisCalls;
        private final AtomicInteger classificationCalls;

        private CountingNaturalSampler(HydrologyTerrainSample basis, NaturalClassification classification) {
            this.basis = basis;
            this.classification = classification;
            this.basisCalls = new AtomicInteger();
            this.classificationCalls = new AtomicInteger();
        }

        @Override
        public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NaturalClassification classifyNatural(int blockX, int blockZ) {
            classificationCalls.incrementAndGet();
            return classification;
        }

        @Override
        public HydrologyTerrainSample sampleBasis(int blockX, int blockZ) {
            basisCalls.incrementAndGet();
            return basis;
        }

        private int basisCalls() {
            return basisCalls.get();
        }

        private int classificationCalls() {
            return classificationCalls.get();
        }
    }
}
