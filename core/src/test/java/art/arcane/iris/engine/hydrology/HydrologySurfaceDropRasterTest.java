package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxel;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelView;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCandidate;
import art.arcane.iris.engine.hydrology.policy.SurfaceRiverPolicy;
import art.arcane.iris.engine.hydrology.surface.SurfaceBounds;
import art.arcane.iris.engine.hydrology.surface.SurfaceCenterline;
import art.arcane.iris.engine.hydrology.surface.SurfaceExcavationMetrics;
import art.arcane.iris.engine.hydrology.surface.SurfaceFootprint;
import art.arcane.iris.engine.hydrology.surface.SurfaceFootprintCompiler;
import art.arcane.iris.engine.hydrology.surface.SurfaceLayerColumn;
import art.arcane.iris.engine.hydrology.surface.SurfaceRole;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class HydrologySurfaceDropRasterTest {
    private static final HydrologyPlannerSettings SETTINGS = HydrologyPlannerSettings.defaults();
    private static final HydrologyTerrainSampler TERRAIN = (x, z) -> HydrologyTerrainSample.openLand(89, 0D, "parent");
    private static final HydrologyGeometrySampler GEOMETRY = HydrologyGeometrySampler.deterministic(TERRAIN);

    @Test
    public void fallingRasterPublishesExactThroatAndReceiverFluidIntervals() {
        RiverCourse course = course(true);
        HydrologySurfaceDropRaster raster = HydrologySurfaceDropRaster.compile(SETTINGS, TERRAIN, GEOMETRY, course);
        assertFalse(raster.columns().isEmpty());
        assertTrue(raster.connects(0, 0, 80));
        assertTrue(raster.connects(0, 0, 90));
        assertFalse(raster.connects(0, 0, 79));
        assertFalse(raster.connects(0, 0, 91));
        assertFalse(raster.connects(100, 100, 85));
        assertTrue(raster.columns().stream().flatMap(column -> column.layers().stream())
                .anyMatch(HydrologyColumnLayer::receivingPool));
        assertThrows(UnsupportedOperationException.class, () -> raster.columns().clear());
    }

    @Test
    public void surfaceDropsDoNotOwnSubmergedGroundOrRaiseWaterOnSeaLevelLand() {
        RiverCourse course = course(true);
        for (int naturalHeight : new int[] {SETTINGS.seaLevel() - 1, SETTINGS.seaLevel()}) {
            HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(naturalHeight, 0D, "parent");
            HydrologySurfaceDropRaster raster = HydrologySurfaceDropRaster.compile(SETTINGS, terrain, GEOMETRY, course);
            assertTrue(raster.columns().isEmpty());
            assertFalse(raster.containsRequiredFluid(course, null));
            SurfaceFootprint surface = new SurfaceFootprintCompiler(SETTINGS, terrain, GEOMETRY).compile(course);
            assertFalse(surface.accepted());
            assertEquals(HydrologyCandidateRejection.SURFACE_DROP_UNSUPPORTED, surface.rejection());
            HydrologyFootprintCompiler compiler = new HydrologyFootprintCompiler(SETTINGS, terrain, GEOMETRY);
            assertTrue(compiler.compile(List.of(course)).columns().isEmpty());
            assertTrue(compiler.compileValidation(List.of(course)).columnsForCourse(course.id()).isEmpty());
        }
        HydraulicSegment fall = new HydraulicSegment(2L, 1L, HydrologyFeatureType.WATERFALL,
                SETTINGS.seaLevel() + 10, SETTINGS.seaLevel(), 4, 3, true, true,
                List.of(new HydrologyPoint(0, SETTINGS.seaLevel() + 10, 0), new HydrologyPoint(12, SETTINGS.seaLevel(), 0)),
                HydraulicChannelProfile.uniform(4, 3));
        RiverCourse coastal = new RiverCourse(1L, RiverCourseType.SURFACE, OptionalLong.of(3L), OptionalLong.of(4L),
                "water", 1, List.of(), List.of(fall));
        HydrologyTerrainSampler sill = (x, z) -> HydrologyTerrainSample.openLand(
                x < 6 ? SETTINGS.seaLevel() + 10 : SETTINGS.seaLevel(), 0D, "parent");
        HydrologySurfaceDropRaster raster = HydrologySurfaceDropRaster.compile(SETTINGS, sill, GEOMETRY, coastal);
        assertTrue(raster.connects(12, 0, SETTINGS.seaLevel()));
        HydrologyColumnSample receiver = raster.sample(12, 0).orElseThrow();
        assertEquals(SETTINGS.seaLevel(), receiver.naturalHeight());
        assertTrue(receiver.layers().stream().anyMatch(layer -> layer.fluidOwned() && layer.fluidHeadY() == SETTINGS.seaLevel()));
    }

    @Test
    public void nonfallingSurfaceTypesDoNotEnterDropRasterOrCaveValidation() {
        RiverCourse course = course(false);
        HydrologySurfaceDropRaster raster = HydrologySurfaceDropRaster.compile(SETTINGS, TERRAIN, GEOMETRY, course);
        assertSame(HydrologySurfaceDropRaster.empty(), raster);
        assertFalse(new HydrologyFootprintCompiler(SETTINGS, TERRAIN, GEOMETRY).hasCaveSegment(course));
        assertEquals(null, HydrologyCaveCourseFilter.representativeCaveSegment(course));
    }

    @Test
    public void dryDropGradingHonorsDepthAndWidthWithoutChangingTheWetThroat() {
        RiverCourse course = course(true);
        HydraulicSegment segment = course.segments().getFirst();
        HydrologyFootprintCompiler compiler = new HydrologyFootprintCompiler(SETTINGS, TERRAIN, GEOMETRY);
        HydrologyPlannerSettings.Excavation limits = SETTINGS.surface().banks().erosion().excavation();
        FootprintLayerShape shape = new FootprintLayerShape(2, 2D, 40D, 79, 90, 90,
                false, false, false, false, true);
        HydrologyFeatureRef feature = new HydrologyFeatureRef(5L, segment.type(), course.id(), segment.id(),
                0, 90, 0, 1, 0, false);
        HydrologyTerrainSample highTerrain = HydrologyTerrainSample.openLand(160, 0D, "parent");
        HydrologyColumnLayer shore = compiler.regularLayer(course, segment, shape, highTerrain, 3D,
                0, 3, false, true, false, false, false, feature);
        assertEquals(160 - limits.maximumDepth(), shore.bedY());
        assertTrue(shore.terrainOwned());
        assertTrue(shore.shore());
        double outsideWidth = shape.channelRadius() + limits.maximumWidth() + 1D;
        HydrologyColumnLayer outside = compiler.regularLayer(course, segment, shape, highTerrain, outsideWidth,
                0, (int) outsideWidth, false, false, true, false, false, feature);
        assertEquals(160, outside.bedY());
        assertFalse(outside.terrainOwned());
        assertFalse(outside.grading());
        HydrologyColumnLayer throat = compiler.regularLayer(course, segment, shape, TERRAIN.sample(0, 0), 0D,
                0, 0, true, false, false, true, false, feature);
        assertEquals(79, throat.bedY());
        assertEquals(90, throat.fluidHeadY());
        assertFalse(throat.terrainOwned());
        assertTrue(throat.fluidOwned());
        assertTrue(throat.fallingFluid());
    }

    @Test
    public void combinedExcavationCountsTheDeepestDryCutOnceAndExcludesWetColumns() {
        RiverCourse course = course(true);
        HydrologySurfaceDropRaster drops = HydrologySurfaceDropRaster.compile(SETTINGS, TERRAIN, GEOMETRY, course);
        SurfaceCenterline centerline = SurfaceCenterline.densify(course.segments().getFirst().centerline());
        int[] baselineVolumes = new int[centerline.size()];
        long baseline = SurfaceExcavationMetrics.accumulate(course, centerline, SurfaceFootprint.empty(), drops,
                null, baselineVolumes);
        assertTrue(baseline > 0L);
        HydrologyColumnSample bank = drops.columns().stream()
                .filter(column -> column.layers().stream().noneMatch(HydrologyColumnLayer::fluidOwned))
                .filter(column -> column.layers().stream().anyMatch(layer -> layer.terrainOwned() && layer.bedY() < column.naturalHeight()))
                .findFirst().orElseThrow();
        int existingCut = bank.layers().stream().filter(HydrologyColumnLayer::terrainOwned)
                .mapToInt(layer -> bank.naturalHeight() - layer.bedY()).max().orElseThrow();
        SurfaceLayerColumn deeper = dryColumn(course, bank.x(), bank.z(), existingCut + 3, 1);
        SurfaceFootprint exposed = new SurfaceFootprint(List.of(deeper, deeper,
                dryColumn(course, 0, 0, 8, 0), dryColumn(course, 100, 100, 7, 5)), 0, null, 0, 0L);
        int[] combinedVolumes = new int[centerline.size()];
        long combined = SurfaceExcavationMetrics.accumulate(course, centerline, exposed, drops, null, combinedVolumes);
        assertEquals(baseline + 10L, combined);
        int[] boundedVolumes = new int[centerline.size()];
        long bounded = SurfaceExcavationMetrics.accumulate(course, centerline, exposed, drops,
                new SurfaceBounds(-100, -100, -1, 100), boundedVolumes);
        bounded += SurfaceExcavationMetrics.accumulate(course, centerline, exposed, drops,
                new SurfaceBounds(0, -100, 100, 100), boundedVolumes);
        assertEquals(combined, bounded);
        assertArrayEquals(combinedVolumes, boundedVolumes);
        int[] bankVolumes = new int[centerline.size()];
        assertEquals(existingCut + 3L, SurfaceExcavationMetrics.accumulate(course, centerline, exposed, drops,
                new SurfaceBounds(bank.x(), bank.z(), bank.x(), bank.z()), bankVolumes));
        assertEquals(existingCut + 3, bankVolumes[1]);
        SurfaceFootprint footprint = new SurfaceFootprintCompiler(SETTINGS, TERRAIN, GEOMETRY).compile(course);
        assertEquals(baseline, footprint.bankExcavation());
    }

    @Test
    public void receiverBasinRespectsLocalIncisionAndRejectsAnImpossibleDrop() {
        RiverCourse course = course(true);
        SurfaceRiverPolicy policy = new SurfaceRiverPolicy("", null, null, null, null, null, null, 4);
        HydrologyTerrainSampler shallow = (x, z) -> HydrologyTerrainSample.openLand(81, 0D, "parent").withSurfacePolicy(policy);
        HydrologySurfaceDropRaster capped = HydrologySurfaceDropRaster.compile(SETTINGS, shallow, GEOMETRY, course);
        assertTrue(capped.containsRequiredFluid(course, null));
        assertTrue(capped.connects(12, 0, 80));
        for (HydrologyColumnSample column : capped.columns()) {
            for (HydrologyColumnLayer layer : column.layers()) {
                if (layer.channel()) {
                    assertTrue(column.naturalHeight() - layer.bedY() <= 4);
                }
            }
        }
        HydrologyColumnLayer receiver = capped.sample(12, 0).orElseThrow().layers().stream()
                .filter(HydrologyColumnLayer::receivingPool).findFirst().orElseThrow();
        assertEquals(77, receiver.bedY());
        HydrologyTerrainSampler blocked = (x, z) -> HydrologyTerrainSample.openLand(84, 0D, "parent").withSurfacePolicy(policy);
        HydrologySurfaceDropRaster invalid = HydrologySurfaceDropRaster.compile(SETTINGS, blocked, GEOMETRY, course);
        assertFalse(invalid.containsRequiredFluid(course, null));
        assertFalse(invalid.connects(12, 0, 80));
        for (HydrologyColumnSample column : invalid.columns()) {
            for (HydrologyColumnLayer layer : column.layers()) {
                assertTrue(!layer.fluidOwned() || layer.bedY() < layer.fluidHeadY());
                if (!layer.channel() && layer.terrainOwned()) {
                    assertTrue(column.naturalHeight() - layer.bedY()
                            <= SETTINGS.surface().banks().erosion().excavation().maximumDepth());
                }
            }
        }
        HydrologyTerrainSampler uneven = (x, z) -> HydrologyTerrainSample.openLand(z == 0 ? 81 : 84, 0D, "parent")
                .withSurfacePolicy(policy);
        HydrologySurfaceDropRaster partial = HydrologySurfaceDropRaster.compile(SETTINGS, uneven, GEOMETRY, course);
        assertTrue(partial.containsRequiredFluid(course, null));
        assertTrue(partial.sample(12, 1).orElseThrow().layers().stream()
                .anyMatch(layer -> !layer.channel() && layer.terrainOwned() && !layer.fluidOwned()));
        SurfaceCenterline centerline = SurfaceCenterline.densify(course.segments().getFirst().centerline());
        int[] dryVolumes = new int[centerline.size()];
        assertTrue(SurfaceExcavationMetrics.accumulate(course, centerline, SurfaceFootprint.empty(), partial,
                new SurfaceBounds(12, 1, 12, 1), dryVolumes) > 0L);
        SurfaceFootprint rejected = new SurfaceFootprintCompiler(SETTINGS, blocked, GEOMETRY).compile(course);
        assertFalse(rejected.accepted());
        assertEquals(HydrologyCandidateRejection.SURFACE_DROP_UNSUPPORTED, rejected.rejection());
    }

    @Test
    public void oneBlockBankWidthFollowsTheSurvivingWetReceiverInEveryRasterPath() {
        RiverCourse course = course(true);
        SurfaceRiverPolicy policy = new SurfaceRiverPolicy("", null, null, null, null, null, null, 4);
        HydrologyTerrainSampler uneven = (x, z) -> HydrologyTerrainSample.openLand(z == 0 ? 81 : 84, 0D, "parent")
                .withSurfacePolicy(policy);
        HydrologyPlannerSettings narrow = withBankWidth(1);
        HydrologySurfaceDropRaster drops = HydrologySurfaceDropRaster.compile(narrow, uneven, GEOMETRY, course);
        assertTrue(drops.containsRequiredFluid(course, null));
        HydrologyColumnSample near = drops.sample(12, 1).orElseThrow();
        HydrologyColumnSample far = drops.sample(12, 2).orElseThrow();
        assertTrue(near.layers().stream().anyMatch(layer -> !layer.channel() && layer.terrainOwned()));
        assertTrue(far.layers().stream().noneMatch(HydrologyColumnLayer::terrainOwned));
        assertEquals(far.naturalHeight(), far.terrainHeight());
        HashSet<Long> wet = new HashSet<>();
        for (HydrologyColumnSample column : drops.columns()) {
            if (column.layers().stream().anyMatch(layer -> layer.fluidOwned() && layer.bedY() < layer.fluidHeadY())) {
                wet.add(RiverFootprint.pack(column.x(), column.z()));
            }
        }
        for (HydrologyColumnSample column : drops.columns()) {
            if (column.layers().stream().anyMatch(layer -> !layer.channel() && layer.terrainOwned())) {
                assertTrue("Dry ownership at " + column.x() + "," + column.z() + ": " + column.layers(),
                        wet.contains(RiverFootprint.pack(column.x(), column.z()))
                        || wet.contains(RiverFootprint.pack(column.x() - 1, column.z()))
                        || wet.contains(RiverFootprint.pack(column.x() + 1, column.z()))
                        || wet.contains(RiverFootprint.pack(column.x(), column.z() - 1))
                        || wet.contains(RiverFootprint.pack(column.x(), column.z() + 1)));
            }
        }
        RiverFootprint full = new HydrologyFootprintCompiler(narrow, uneven, GEOMETRY).compile(List.of(course));
        assertEquals(far, full.sample(12, 2).orElseThrow());
        HydrologyFootprintCompiler.ValidationRaster validation = new HydrologyFootprintCompiler(narrow, uneven, GEOMETRY)
                .compileValidation(List.of(course));
        assertTrue(validation.columnsForCourse(course.id()).contains(far));
        HydrologySurfaceDropRaster bounded = HydrologySurfaceDropRaster.compile(narrow, uneven, GEOMETRY, course,
                new SurfaceBounds(12, 1, 12, 3));
        assertEquals(far, bounded.sample(12, 2).orElseThrow());
    }

    @Test
    public void overlappingDryCutsThatRestoreNaturalHeightDoNotOwnTerrain() {
        RiverCourse course = course(true);
        HydrologyColumnLayer cut = dryColumn(course, 0, 0, 3, 0).layer();
        HydrologyColumnLayer natural = new HydrologyColumnLayer(cut.feature(), 89, 89, 89,
                false, false, true, false, false, false, false, false, false,
                "water", "parent", "parent", "parent", "parent", "parent");
        FootprintMutableColumn column = new FootprintMutableColumn(0, 0, TERRAIN.sample(0, 0), SETTINGS.seaLevel());
        column.add(cut);
        column.add(natural);
        HydrologyColumnLayer merged = column.build().layers().getFirst();
        assertEquals(89, merged.bedY());
        assertFalse(merged.terrainOwned());
        assertTrue(merged.grading());
    }

    @Test
    public void boundedDropRasterMatchesTheSameSubsetOfFullGeometry() {
        RiverCourse course = course(true);
        SurfaceBounds bounds = new SurfaceBounds(-2, -2, 1, 2);
        HydrologySurfaceDropRaster full = HydrologySurfaceDropRaster.compile(SETTINGS, TERRAIN, GEOMETRY, course);
        HydrologySurfaceDropRaster bounded = HydrologySurfaceDropRaster.compile(SETTINGS, TERRAIN, GEOMETRY, course, bounds);
        List<HydrologyColumnSample> expected = full.columns().stream()
                .filter(column -> bounds.contains(column.x(), column.z())).toList();
        assertEquals(expected, bounded.columns());
        AtomicInteger samples = new AtomicInteger();
        HydrologyTerrainSampler counting = (x, z) -> {
            samples.incrementAndGet();
            return TERRAIN.sample(x, z);
        };
        assertSame(HydrologySurfaceDropRaster.empty(), HydrologySurfaceDropRaster.compile(
                SETTINGS, counting, GEOMETRY, course, new SurfaceBounds(1000, 1000, 1015, 1015)));
        assertEquals(0, samples.get());
    }

    @Test
    public void directValidationRetainsEveryDropActionAndOnlyOpensAboveTheReceiver() {
        RiverCourse course = course(true);
        HydrologySurfaceDropRaster raster = HydrologySurfaceDropRaster.compile(SETTINGS, TERRAIN, GEOMETRY, course);
        HydrologyFootprintCompiler compiler = new HydrologyFootprintCompiler(SETTINGS, TERRAIN, GEOMETRY);
        HydrologyFootprintCompiler.ValidationRaster validation = compiler.compileSurfaceDropValidation(
                course, SurfaceFootprint.empty(), raster);
        HydrologyCaveCourseFilter filter = filter(null);
        CaveCandidateBuilder builder = filter.candidateBuilders(List.of(course), validation).get(course.id());
        HydrologyCaveCandidate candidate = builder.build(filter.options);
        for (HydrologyColumnSample column : raster.columns()) {
            for (HydrologyColumnLayer layer : column.layers()) {
                if (!layer.channel() || !layer.fluidOwned()) {
                    continue;
                }
                assertTrue(filter.isCaveLayer(layer, course));
                for (int y = layer.bedY() + 1; y <= layer.fluidHeadY(); y++) {
                    assertTrue(candidate.actions().containsKey(new CavePosition(column.x(), y, column.z())));
                }
            }
        }
        assertTrue(candidate.intentionalOpenings().contains(new CavePosition(0, 82, 0)));
        assertFalse(candidate.intentionalOpenings().stream().anyMatch(position -> position.y() <= 81));
        assertEquals(HydrologyCaveAction.FALLING_FLUID, candidate.actions().get(new CavePosition(0, 85, 0)));
        assertTrue(validation.surfaceColumnAt(0, 0).layers().stream().anyMatch(HydrologyColumnLayer::fallingFluid));
        assertEquals(89, validation.plannedSurface().resolve(0, 0, 89));
        assertFalse(validation.plannedSurface().ownsTerrain(0, 0));
    }

    @Test
    public void biomeOnlyShoreDoesNotReplaceNaturalCaveTerrain() {
        RiverCourse course = course(true);
        HydrologyColumnLayer original = dryColumn(course, 100, 100, 0, 12).layer();
        HydrologyColumnLayer shore = new HydrologyColumnLayer(original.feature(), 89, 89, 89,
                false, true, false, false, false, false, false, false, false,
                "water", "parent", "parent", "parent", "parent", "parent");
        SurfaceLayerColumn column = new SurfaceLayerColumn(100, 100, TERRAIN.sample(100, 100), shore,
                SurfaceRole.SHORE, false, 12);
        SurfaceFootprint surface = new SurfaceFootprint(List.of(column), 0, null, 0, 0L);
        HydrologyFootprintCompiler compiler = new HydrologyFootprintCompiler(SETTINGS, TERRAIN, GEOMETRY);
        HydrologyFootprintCompiler.ValidationRaster validation = compiler.compileSurfaceDropValidation(
                course, surface, HydrologySurfaceDropRaster.compile(SETTINGS, TERRAIN, GEOMETRY, course));
        assertFalse(validation.plannedSurface().ownsTerrain(100, 100));
        assertEquals(89, validation.plannedSurface().resolve(100, 100, 89));
        HydrologyColumnSample sample = new HydrologyColumnSample(100, 100, 89, SETTINGS.seaLevel(),
                false, "parent", List.of(shore));
        HydrologyFootprintCompiler.ValidationRaster materialized = validation.withMaterializedSurface(
                new RiverFootprint(Map.of(RiverFootprint.pack(100, 100), sample)));
        assertFalse(materialized.plannedSurface().ownsTerrain(100, 100));
    }

    @Test
    public void fullCourseContainmentAcceptsTheFallButRejectsReceiverExposure() {
        RiverCourse course = course(true);
        HydrologySurfaceDropRaster raster = HydrologySurfaceDropRaster.compile(SETTINGS, TERRAIN, GEOMETRY, course);
        HydrologyFootprintCompiler compiler = new HydrologyFootprintCompiler(SETTINGS, TERRAIN, GEOMETRY);
        HydrologyFootprintCompiler.ValidationRaster validation = compiler.compileSurfaceDropValidation(
                course, SurfaceFootprint.empty(), raster);
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        HydrologyCaveCourseFilter.Result accepted = filter(new CavePosition(0, 82, 0)).filter(
                nodes(), List.of(), outlets(), List.of(course), validation, diagnostics);
        assertEquals(1, accepted.courses().size());
        assertEquals(1, accepted.cavePlans().size());
        Map<Long, HydrologyColumnSample> columns = new HashMap<>();
        for (HydrologyColumnSample sample : raster.columns()) {
            columns.put(RiverFootprint.pack(sample.x(), sample.z()), sample);
        }
        HydrologyTile tile = new HydrologyTile(new HydrologyTileKey(0, 0), 1L, 1L, 128,
                accepted.nodes(), accepted.edges(), accepted.outlets(), accepted.courses(), accepted.cavePlans(), List.of(), new RiverFootprint(columns));
        assertEquals(1, tile.courses().size());
        for (boolean connectToExistingCaves : new boolean[] {false, true}) {
            diagnostics.clear();
            HydrologyCaveCourseFilter.Result rejected = filter(new CavePosition(12, 80, 0), connectToExistingCaves).filter(
                    nodes(), List.of(), outlets(), List.of(course), validation, diagnostics);
            assertTrue(rejected.courses().isEmpty());
            assertFalse(diagnostics.isEmpty());
        }
    }

    private static RiverCourse course(boolean falling) {
        HydraulicSegment segment = new HydraulicSegment(2L, 1L, HydrologyFeatureType.WATERFALL,
                90, 80, 4, 3, falling, true,
                List.of(new HydrologyPoint(0, 90, 0), new HydrologyPoint(12, 80, 0)),
                HydraulicChannelProfile.uniform(4, 3));
        return new RiverCourse(1L, RiverCourseType.SURFACE, OptionalLong.of(3L), OptionalLong.of(4L),
                "water", 1, List.of(), List.of(segment));
    }

    private static SurfaceLayerColumn dryColumn(RiverCourse course, int x, int z, int cut, int station) {
        HydrologyFeatureRef feature = new HydrologyFeatureRef(10L, HydrologyFeatureType.SURFACE_POOL,
                course.id(), course.segments().getFirst().id(), x, 89 - cut, z, 1, 0, false);
        HydrologyColumnLayer layer = new HydrologyColumnLayer(feature, 89 - cut, 89 - cut, 89 - cut,
                false, false, true, false, false, false, true, false, false,
                "water", "parent", "parent", "parent", "parent", "parent");
        return new SurfaceLayerColumn(x, z, TERRAIN.sample(x, z), layer, SurfaceRole.BANK, false, station);
    }

    private static HydrologyPlannerSettings withBankWidth(int width) {
        HydrologyPlannerSettings.Surface surface = SETTINGS.surface();
        HydrologyPlannerSettings.Banks banks = surface.banks();
        HydrologyPlannerSettings.Erosion erosion = banks.erosion();
        HydrologyPlannerSettings.Erosion bounded = new HydrologyPlannerSettings.Erosion(erosion.enabled(),
                erosion.smoothingRadius(), erosion.thalwegFraction(), erosion.blendCurve(), erosion.bedNoise(),
                erosion.style(), erosion.terraceSteps(), erosion.cliffFraction(), erosion.bedProfile(), erosion.shoreRise(),
                erosion.blendBaseWidth(), new HydrologyPlannerSettings.Excavation(erosion.excavation().maximumDepth(),
                width, erosion.excavation().maximumVolumePerBlock()));
        HydrologyPlannerSettings.Banks boundedBanks = new HydrologyPlannerSettings.Banks(banks.sink(), banks.blendSlope(),
                banks.minimumBlendWidth(), banks.maximumBlendWidth(), banks.roughness(), banks.roughnessWavelength(),
                banks.cascadeRun(), banks.waterfallMinimumDrop(), banks.mouthFlareRatio(), banks.inlet(),
                banks.springWidthRatio(), banks.springLength(), banks.exposeCutStrata(), bounded, banks.ponds(),
                banks.channel(), banks.flow());
        HydrologyPlannerSettings.Surface boundedSurface = new HydrologyPlannerSettings.Surface(surface.enabled(),
                surface.sources(), surface.minimumWidth(), surface.maximumWidth(), surface.minimumDepth(), surface.maximumDepth(),
                surface.maximumIncision(), surface.shoreWidth(), boundedBanks);
        return new HydrologyPlannerSettings(SETTINGS.seaLevel(), SETTINGS.routing(), boundedSurface, SETTINGS.hydraulics(),
                SETTINGS.underground(), SETTINGS.outlets(), SETTINGS.geometry(), SETTINGS.deepFluids(), SETTINGS.surfacePools(),
                SETTINGS.widestShoreBiomeWidth(), SETTINGS.seaCaves(), SETTINGS.surfacePolicyBounds());
    }

    private static List<DrainageNode> nodes() {
        return List.of(new DrainageNode(3L, 0, 0, TERRAIN.sample(0, 0), 1D, 4L));
    }

    private static List<RiverOutlet> outlets() {
        HydrologyPoint receiver = new HydrologyPoint(12, 80, 0);
        return List.of(new RiverOutlet(4L, HydrologyFeatureType.INLAND_GROTTO, 3L,
                receiver, receiver, SETTINGS.seaLevel(), false));
    }

    private static HydrologyCaveCourseFilter filter(CavePosition exposed) {
        return filter(exposed, false);
    }

    private static HydrologyCaveCourseFilter filter(CavePosition exposed, boolean connectToExistingCaves) {
        CaveVoxelView view = new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                return position.y() >= 0 && position.y() < 128;
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
                return position.equals(exposed);
            }
        };
        return new HydrologyCaveCourseFilter(view, new HydrologyCaveCourseFilter.Options(connectToExistingCaves, 8192, 8192));
    }
}
