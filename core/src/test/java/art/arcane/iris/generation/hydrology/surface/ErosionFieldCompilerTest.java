package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;

import art.arcane.iris.generation.hydrology.HydraulicChannelProfile;
import art.arcane.iris.generation.hydrology.HydraulicSegment;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.HydrologySurfaceDropRaster;
import art.arcane.iris.generation.hydrology.RiverCourse;
import art.arcane.iris.generation.hydrology.RiverCourseType;
import art.arcane.iris.generation.hydrology.HydrologyGeometrySampler;
import art.arcane.iris.generation.hydrology.HydrologyCandidateRejection;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyPoint;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSampler;
import art.arcane.iris.generation.hydrology.RiverFootprint;
import art.arcane.iris.generation.hydrology.IrisRiverBedProfile;
import art.arcane.iris.generation.hydrology.IrisRiverBlendStyle;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ErosionFieldCompilerTest {
    private static final int SEA_LEVEL = 60;
    private static final HydrologyGeometrySampler CONSTANT_GEOMETRY = request -> switch (request.field()) {
        case SURFACE_WIDTH -> 6;
        case SURFACE_DEPTH -> 3;
        default -> request.minimum();
    };

    @Test
    public void wetIncisionPreflightMatchesFullVerdictsAcrossPondsCrossingsStepsAndPolicies() {
        int accepted = 0;
        int unsupported = 0;
        for (int scenario = 0; scenario < 24; scenario++) {
            int fixture = scenario;
            boolean crossing = scenario % 3 == 1;
            boolean coastal = scenario % 6 == 5;
            HydrologyPlannerSettings.Ponds ponds = scenario % 4 == 0 ? noPonds()
                    : new HydrologyPlannerSettings.Ponds(new HydrologyPlannerSettings.Pond(true, 3, 8, 3),
                    new HydrologyPlannerSettings.Pond(true, 3, 7, 2));
            HydrologyPlannerSettings.Erosion erosion = shapedErosion(
                    IrisRiverBlendStyle.values()[scenario % IrisRiverBlendStyle.values().length], 4, 0.5D,
                    IrisRiverBedProfile.values()[scenario % IrisRiverBedProfile.values().length], 1D, 2D);
            HydrologyPlannerSettings.Surface surface = surfaceWith(scenario % 2 == 0 ? 0D : 0.6D,
                    0, 1.5D, erosion, ponds, HydrologyPlannerSettings.Channel.defaults(),
                    HydrologyPlannerSettings.Flow.defaults());
            HydrologyTerrainSampler terrain = (x, z) -> {
                if (coastal && x > 12) {
                    return HydrologyTerrainSample.ocean(50, "ocean");
                }
                int natural = coastal ? SEA_LEVEL : 90;
                if (fixture >= 12 && x >= -2 && x <= 2) {
                    natural += 30;
                }
                if (fixture % 4 == 2 && z > 4) {
                    natural += 12;
                }
                HydrologyTerrainSample sample = HydrologyTerrainSample.openLand(natural, 0D, "land")
                        .withErosion(fixture % 7 != 0).withShoreWidth(z < 0 ? 0.5D : 3D);
                return fixture % 3 == 2 ? sample.withSurfacePolicy(new SurfaceRiverPolicy(
                        "local", null, null, null, null, null, null, x < 0 ? 3 : 16)) : sample;
            };
            List<HydrologyPoint> path = crossing
                    ? List.of(new HydrologyPoint(-18, 90, -8), new HydrologyPoint(18, 90, 8),
                    new HydrologyPoint(-18, 90, 8), new HydrologyPoint(18, 90, -8))
                    : List.of(new HydrologyPoint(-18, 90, 0), new HydrologyPoint(18, 90, 0));
            SurfaceCenterline centerline = SurfaceCenterline.densify(path);
            double[] widths = new double[centerline.size()];
            double[] depths = new double[centerline.size()];
            double[] multipliers = new double[centerline.size()];
            int[] heads = new int[centerline.size()];
            Arrays.fill(widths, 3D);
            Arrays.fill(depths, 3D);
            Arrays.fill(multipliers, 1D);
            for (int station = 0; station < heads.length; station++) {
                heads[station] = coastal ? SEA_LEVEL : 89 - (scenario % 4 == 3 ? station / 8 : 0);
            }
            ChannelProfile channel = new ChannelProfile(widths, depths, multipliers);
            ValleyProfile valley = ValleyProfile.fromHeads(heads, heads.length);
            SurfaceTerminal terminal = coastal ? SurfaceTerminal.OCEAN_MOUTH : SurfaceTerminal.SINKHOLE;
            HydrologyPlannerSettings settings = rasterSettings(surface);
            HydrologySurfaceDropRaster drops = HydrologySurfaceDropRaster.empty();
            if (scenario % 4 == 3 && !coastal) {
                HydraulicSegment fall = new HydraulicSegment(2L, 1L, HydrologyFeatureType.WATERFALL,
                        89, 81, 6, 3, true, true,
                        List.of(new HydrologyPoint(-5, 89, 0), new HydrologyPoint(-4, 81, 0)),
                        HydraulicChannelProfile.uniform(6, 3));
                RiverCourse course = new RiverCourse(1L, RiverCourseType.SURFACE, OptionalLong.of(9L),
                        OptionalLong.of(8L), "water", 1, List.of(), List.of(fall));
                drops = HydrologySurfaceDropRaster.compile(settings, terrain, CONSTANT_GEOMETRY, course);
                assertTrue("Drop raster scenario " + scenario, !drops.columns().isEmpty());
            }
            SurfaceRasterContext context = new SurfaceRasterContext(null, drops,
                    scenario % 4 == 1 ? new SurfaceRunBoundary(
                            new SurfaceRunBoundary.Plane(-18, 0, 1, 0, 10D),
                            new SurfaceRunBoundary.Plane(18, 0, 1, 0, 10D)) : SurfaceRunBoundary.unbounded());
            ErosionFieldCompiler compiler = new ErosionFieldCompiler(settings, terrain);
            ErosionFieldCompiler.PreparedField prepared = compiler.prepare(centerline, channel, valley,
                    terminal, coastal ? Math.max(0, centerline.size() - 12) : centerline.size());
            for (long seed = 0; seed < 4; seed++) {
                ErosionField full = compiler.compile(seed, centerline, channel, valley, terminal, 8,
                        ponds, context, prepared);
                ErosionField publication = compiler.compileForPublication(seed, centerline, channel, valley,
                        terminal, 8, ponds, context, prepared);
                assertEquals("scenario=" + scenario + " seed=" + seed, full.rejection(), publication.rejection());
                if (full.rejection() == HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED) {
                    unsupported++;
                    for (SurfaceColumn wet : publication.columns().values()) {
                        if (wet.role() == SurfaceRole.CHANNEL) {
                            assertEquals(full.column(wet.x(), wet.z()), wet);
                        }
                    }
                } else {
                    assertEqualFields(full, publication);
                    if (full.rejection() == null) {
                        accepted++;
                    }
                }
            }
        }
        assertTrue("Accepted oracle cases: " + accepted, accepted >= 8);
        assertTrue("Unsupported oracle cases: " + unsupported, unsupported >= 8);
    }

    @Test
    public void rasterBatchesPreserveExactDemandAndColumnOrder() {
        Map<Long, Integer> serialCalls = new HashMap<>();
        Map<Long, Integer> batchCalls = new HashMap<>();
        AtomicInteger batches = new AtomicInteger();
        HydrologyTerrainSampler serial = (x, z) -> {
            serialCalls.merge(RiverFootprint.pack(x, z), 1, Integer::sum);
            return HydrologyTerrainSample.openLand(80 + Math.floorMod(z, 7), 0D, "land");
        };
        HydrologyTerrainSampler batched = new HydrologyTerrainSampler() {
            @Override
            public HydrologyTerrainSample sample(int x, int z) {
                batchCalls.merge(RiverFootprint.pack(x, z), 1, Integer::sum);
                return HydrologyTerrainSample.openLand(80 + Math.floorMod(z, 7), 0D, "land");
            }

            public HydrologyTerrainSample[] sampleBatch(long[] coordinates, int count) {
                assertTrue(count > 0 && count <= 4096);
                batches.incrementAndGet();
                HydrologyTerrainSample[] samples = new HydrologyTerrainSample[count];
                for (int index = 0; index < count; index++) {
                    samples[index] = sample(RiverFootprint.unpackX(coordinates[index]), RiverFootprint.unpackZ(coordinates[index]));
                }
                return samples;
            }
        };
        ErosionField expected = compile(serial, 300, SurfaceTerminal.SINKHOLE, 40).field();
        ErosionField actual = compile(batched, 300, SurfaceTerminal.SINKHOLE, 40).field();
        assertEquals(new ArrayList<>(expected.columns().values()), new ArrayList<>(actual.columns().values()));
        assertEquals(expected.rejection(), actual.rejection());
        assertEquals(expected.rejectionDetail(), actual.rejectionDetail());
        assertEquals(expected.uncontainedWetCells(), actual.uncontainedWetCells());
        assertEquals(expected.bankExcavation(), actual.bankExcavation());
        assertEquals(serialCalls, batchCalls);
        assertTrue(batches.get() > 1);
    }

    @Test
    public void pondExclusionsPreserveCompleteAndClippedFieldsWithFewerTerrainSamples() throws Exception {
        List<String> fingerprints = new ArrayList<>();
        List<Integer> sampleCounts = new ArrayList<>();
        for (int scenario = 0; scenario < 8; scenario++) {
            double shore = switch (scenario) {
                case 0 -> Math.nextDown(1.75D);
                case 1 -> 1.75D;
                case 2 -> Math.nextUp(1.75D);
                default -> 1.5D;
            };
            boolean enabled = scenario != 4;
            boolean erode = scenario != 5;
            boolean coastal = scenario == 7;
            HydrologyPlannerSettings.Erosion defaults = HydrologyPlannerSettings.Erosion.defaults();
            HydrologyPlannerSettings.Erosion erosion = new HydrologyPlannerSettings.Erosion(enabled,
                    defaults.smoothingRadius(), defaults.thalwegFraction(), defaults.blendCurve(), defaults.bedNoise(),
                    defaults.style(), defaults.terraceSteps(), defaults.cliffFraction(), defaults.bedProfile(),
                    defaults.shoreRise(), defaults.blendBaseWidth(),
                    new HydrologyPlannerSettings.Excavation(16, scenario == 6 ? 5 : 48, 256));
            HydrologyPlannerSettings.Ponds ponds = new HydrologyPlannerSettings.Ponds(
                    new HydrologyPlannerSettings.Pond(true, 6, 6, 3),
                    new HydrologyPlannerSettings.Pond(true, 5, 5, 2));
            HydrologyPlannerSettings.Surface surface = surfaceWith(scenario == 3 ? 0.65D : 0D, 0, shore,
                    erosion, ponds, HydrologyPlannerSettings.Channel.defaults(), HydrologyPlannerSettings.Flow.defaults());
            HydrologyTerrainSampler terrain = (x, z) -> coastal && x >= 4
                    ? HydrologyTerrainSample.ocean(50, "ocean")
                    : HydrologyTerrainSample.openLand(coastal ? SEA_LEVEL : 80 + Math.max(0, z) / 3, 0D, "land")
                    .withErosion(erode);
            SurfaceCenterline centerline = SurfaceCenterline.densify(List.of(
                    new HydrologyPoint(-12, 80, 0), new HydrologyPoint(12, 80, 0)));
            double[] widths = new double[centerline.size()];
            double[] depths = new double[centerline.size()];
            double[] multipliers = new double[centerline.size()];
            int[] heads = new int[centerline.size()];
            Arrays.fill(widths, 3D);
            Arrays.fill(depths, 2D);
            Arrays.fill(multipliers, 1D);
            Arrays.fill(heads, coastal ? SEA_LEVEL : 80);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            AtomicInteger samples = new AtomicInteger();
            ErosionField complete = null;
            for (SurfaceBounds bounds : new SurfaceBounds[]{null, new SurfaceBounds(-20, -12, -1, 12),
                    new SurfaceBounds(0, -12, 20, 12)}) {
                ErosionField field = new ErosionFieldCompiler(rasterSettings(surface), (x, z) -> {
                    samples.incrementAndGet();
                    return terrain.sample(x, z);
                }).compile(1234L, centerline, new ChannelProfile(widths, depths, multipliers),
                        ValleyProfile.fromHeads(heads, coastal ? 17 : heads.length),
                        coastal ? SurfaceTerminal.OCEAN_MOUTH : SurfaceTerminal.SINKHOLE, 8, ponds,
                        SurfaceRasterContext.bounded(bounds));
                List<SurfaceColumn> ordered = new ArrayList<>(field.columns().values());
                ordered.sort(Comparator.comparingLong(column -> RiverFootprint.pack(column.x(), column.z())));
                for (SurfaceColumn column : ordered) {
                    digest.update(column.toString().getBytes(StandardCharsets.UTF_8));
                    if (bounds != null && bounds.contains(column.x(), column.z())) {
                        assertEquals(complete.column(column.x(), column.z()), column);
                    }
                }
                digest.update((field.rejection() + ":" + field.rejectionDetail() + ":"
                        + field.uncontainedWetCells() + ":" + field.bankExcavation()).getBytes(StandardCharsets.UTF_8));
                if (bounds == null) {
                    complete = field;
                }
            }
            fingerprints.add(HexFormat.of().formatHex(digest.digest()));
            sampleCounts.add(samples.get());
        }
        assertEquals(List.of(
                "66595850077d44389c5c78594276de4568f85f08854d3333137dfc05ebf3455a",
                "66595850077d44389c5c78594276de4568f85f08854d3333137dfc05ebf3455a",
                "66595850077d44389c5c78594276de4568f85f08854d3333137dfc05ebf3455a",
                "744b15e684ba996f9c730ab1409ada2643985c902a6da1aadb5cc2b4882d640b",
                "2e7e4af1e433ccd0a7392f0d2e93bb85ac122088cd2cf8d55107f7dcfb4ec283",
                "7beee0b78c37b1287510fa0719375e14e0f0013eb8af81df8e20a3b5628612b7",
                "ad35016194318efa42dc862df4e2bb3d9ec398d3bacec79588576d74bfd1723a",
                "a2fe6d20c87be6b6ebf1f4503da75ee767e65d5c8e8c43d5c344957f48f3085a"), fingerprints);
        int[] previousSamples = {16724, 16724, 16724, 19594, 15164, 15464, 16724, 7367};
        int[] maximumSamples = {15500, 15500, 15500, 17000, 3000, 3300, 5000, 7367};
        for (int scenario = 0; scenario < previousSamples.length; scenario++) {
            assertTrue("scenario " + scenario + " samples " + sampleCounts.get(scenario)
                            + "/" + previousSamples[scenario], sampleCounts.get(scenario) <= maximumSamples[scenario]);
        }
    }

    @Test
    public void geometricExclusionAvoidsTerrainSamplesWithoutChangingAsymmetricBanks() {
        HydrologyPlannerSettings.Surface surface = zeroRoughnessSurface();
        AtomicInteger referenceSamples = new AtomicInteger();
        AtomicInteger boundedSamples = new AtomicInteger();
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(z > 4 ? 100 : 80, 0D, "land");
        ErosionField reference = rasterWithShoreBound(surface, (x, z) -> {
            referenceSamples.incrementAndGet();
            return terrain.sample(x, z);
        }, 128D, null);
        ErosionField bounded = rasterWithShoreBound(surface, (x, z) -> {
            boundedSamples.incrementAndGet();
            return terrain.sample(x, z);
        }, surface.shoreWidth(), null);

        assertEqualFields(reference, bounded);
        assertNull(bounded.rejection());
        assertTrue("sample counts " + boundedSamples.get() + "/" + referenceSamples.get(),
                boundedSamples.get() < referenceSamples.get() * 0.8D);
        assertNotNull(bounded.column(0, 18));
        assertNull(bounded.column(0, -18));
    }

    @Test
    public void geometricExclusionRetainsPolicyBandsAtExactAndAdjacentThresholds() {
        HydrologyPlannerSettings.Surface surface = zeroRoughnessSurface();
        for (double width : new double[]{Math.nextDown(1.75D), 1.75D, Math.nextUp(1.75D), 12D}) {
            HydrologyTerrainSampler terrain = (x, z) -> land(80, z >= 0 ? width : 0D)
                    .withShoreWidth(z < 0 ? width : surface.shoreWidth());
            double bound = Math.max(surface.shoreWidth(), width);
            ErosionField reference = rasterWithShoreBound(surface, terrain, 128D, null);
            ErosionField complete = rasterWithShoreBound(surface, terrain, bound, null);
            assertEqualFields(reference, complete);
            for (SurfaceBounds bounds : List.of(new SurfaceBounds(-16, -16, -1, 15),
                    new SurfaceBounds(0, -16, 15, 15))) {
                ErosionField bounded = rasterWithShoreBound(surface, terrain, bound, bounds);
                for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z++) {
                    for (int x = bounds.minimumX(); x <= bounds.maximumX(); x++) {
                        assertEquals("policy width " + width + " at " + x + "," + z,
                                complete.column(x, z), bounded.column(x, z));
                    }
                }
            }
            if (width >= 1.75D) {
                assertEquals(SurfaceRole.SHORE, complete.column(0, 5).role());
            }
            if (width == 12D) {
                assertEquals(SurfaceRole.SHORE, complete.column(0, 15).role());
                assertEquals(SurfaceRole.BANK, complete.column(0, -15).role());
            }
        }
    }

    private static ErosionField rasterWithShoreBound(HydrologyPlannerSettings.Surface surface,
                                                    HydrologyTerrainSampler terrain, double bound,
                                                    SurfaceBounds bounds) {
        HydrologyPlannerSettings defaults = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings settings = new HydrologyPlannerSettings(SEA_LEVEL, defaults.routing(), surface,
                defaults.hydraulics(), defaults.underground(), defaults.outlets(), defaults.geometry(),
                defaults.deepFluids(), defaults.surfacePools(), bound, defaults.seaCaves(), defaults.surfacePolicyBounds());
        SurfaceCenterline centerline = SurfaceCenterline.densify(List.of(
                new HydrologyPoint(-48, 80, 0), new HydrologyPoint(48, 80, 0)));
        double[] widths = new double[centerline.size()];
        double[] depths = new double[centerline.size()];
        double[] multipliers = new double[centerline.size()];
        int[] heads = new int[centerline.size()];
        Arrays.fill(widths, 3D);
        Arrays.fill(depths, 2D);
        Arrays.fill(multipliers, 1D);
        Arrays.fill(heads, 80);
        return new ErosionFieldCompiler(settings, terrain).compile(42L, centerline,
                new ChannelProfile(widths, depths, multipliers), ValleyProfile.fromHeads(heads, heads.length),
                SurfaceTerminal.SINKHOLE, 0, noPonds(), SurfaceRasterContext.bounded(bounds));
    }

    private static void assertEqualFields(ErosionField expected, ErosionField actual) {
        assertEquals(expected.columns(), actual.columns());
        assertEquals(expected.rejection(), actual.rejection());
        assertEquals(expected.rejectionDetail(), actual.rejectionDetail());
        assertEquals(expected.uncontainedWetCells(), actual.uncontainedWetCells());
        assertEquals(expected.bankExcavation(), actual.bankExcavation());
    }

    @Test
    public void mouthCutsDrySeaLevelSillBeforeReachingOceanWater() {
        HydrologyTerrainSampler coast = (int x, int z) -> x >= 100
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(x >= 92 ? SEA_LEVEL : 66, 0D, "land");
        Compiled compiled = compile(zeroRoughnessSurface(), coast, 110, SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);

        for (int x = 92; x < 100; x++) {
            SurfaceColumn sill = compiled.field().column(x, 0);
            assertNotNull("dry sill at " + x, sill);
            assertTrue("sill must be carved at " + x, !sill.apron());
            assertEquals(SEA_LEVEL, sill.headY());
            assertTrue(sill.height() < SEA_LEVEL);
        }
        assertNull(compiled.field().rejection());
    }

    @Test
    public void mouthLabelWithoutReceivingOceanIsRejected() {
        Compiled compiled = compile(300, (x, z) -> 66, SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);

        assertEquals(HydrologyCandidateRejection.SURFACE_MOUTH_DISCONNECTED, compiled.field().rejection());
    }

    @Test
    public void bankExcavationPreservesHighSideTerrainWithinDepthAndWidthLimits() {
        HydrologyTerrainSampler hillside = (int x, int z) -> HydrologyTerrainSample.openLand(90 + Math.max(0, z - 4) * 4, 0D, "land");
        Compiled compiled = compile(zeroRoughnessSurface(), hillside, 300, SurfaceTerminal.SINKHOLE, 40);

        for (SurfaceColumn column : compiled.field().columns().values()) {
            if (column.role() == SurfaceRole.CHANNEL || column.apron()) {
                continue;
            }
            assertTrue(column.terrain().naturalHeight() - column.height() <= 8);
            if (column.x() > 30 && column.x() < 270 && Math.abs(column.z()) > 19) {
                assertEquals(column.terrain().naturalHeight(), column.height());
            }
        }
        SurfaceColumn bank = compiled.field().column(150, 9);
        assertNotNull(bank);
        assertTrue(bank.height() >= hillside.sample(150, 9).naturalHeight() - 8);
    }

    @Test
    public void boundedRasterMatchesTheCompleteCourseAcrossTileCuts() {
        HydrologyPlannerSettings.Surface surface = zeroRoughnessSurface();
        HydrologyTerrainSampler hillside = (int x, int z) -> HydrologyTerrainSample.openLand(90 + Math.max(0, z) / 2, 0D, "land");
        SurfaceCenterline centerline = SurfaceCenterline.densify(List.of(new HydrologyPoint(-150, 0, 0), new HydrologyPoint(150, 0, 0)));
        ChannelProfile channel = new ChannelProfileBuilder(surface, hillside, CONSTANT_GEOMETRY).build(centerline, "water", false);
        ValleyProfile valley = new ValleyProfileSolver(surface, hillside, SEA_LEVEL, 64).solve(centerline, channel, SurfaceTerminal.SINKHOLE, 40);
        ErosionFieldCompiler compiler = new ErosionFieldCompiler(rasterSettings(surface), hillside);
        ErosionField complete = compiler.compile(42L, centerline, channel, valley, SurfaceTerminal.SINKHOLE, 8);
        SurfaceBounds bounds = new SurfaceBounds(-16, -16, 15, 15);
        ErosionField bounded = compiler.compile(42L, centerline, channel, valley, SurfaceTerminal.SINKHOLE, 8, surface.banks().ponds(), SurfaceRasterContext.bounded(bounds));

        for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z++) {
            for (int x = bounds.minimumX(); x <= bounds.maximumX(); x++) {
                assertEquals("bounded column " + x + "," + z, complete.column(x, z), bounded.column(x, z));
            }
        }
    }

    @Test
    public void boundedRasterRaisesBanksFromHigherWaterAcrossTheChunkEdge() {
        HydrologyPlannerSettings.Surface surface = zeroRoughnessSurface();
        HydrologyTerrainSampler lowGround = (x, z) -> HydrologyTerrainSample.openLand(77, 0D, "land");
        SurfaceCenterline centerline = SurfaceCenterline.densify(
                List.of(new HydrologyPoint(-48, 80, 0), new HydrologyPoint(48, 78, 0)));
        int count = centerline.size();
        double[] widths = new double[count];
        double[] depths = new double[count];
        double[] multipliers = new double[count];
        int[] heads = new int[count];
        Arrays.fill(widths, 3D);
        Arrays.fill(depths, 2D);
        Arrays.fill(multipliers, 1D);
        for (int station = 0; station < count; station++) {
            heads[station] = centerline.x()[station] < 0 ? 80 : 78;
        }
        ChannelProfile channel = new ChannelProfile(widths, depths, multipliers);
        ValleyProfile valley = ValleyProfile.fromHeads(heads, count);
        ErosionFieldCompiler compiler = new ErosionFieldCompiler(rasterSettings(surface), lowGround);
        ErosionField complete = compiler.compile(42L, centerline, channel, valley, SurfaceTerminal.SINKHOLE, 0);

        assertNull(complete.rejection());
        assertEquals(80, complete.column(0, 4).height());
        for (SurfaceBounds bounds : List.of(new SurfaceBounds(-16, -16, -1, 15),
                new SurfaceBounds(0, -16, 15, 15), new SurfaceBounds(0, 4, 15, 19))) {
            ErosionField bounded = compiler.compile(42L, centerline, channel, valley,
                    SurfaceTerminal.SINKHOLE, 0, noPonds(), SurfaceRasterContext.bounded(bounds));
            assertNull(bounded.rejection());
            assertEquals(0, bounded.uncontainedWetCells());
            for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z++) {
                for (int x = bounds.minimumX(); x <= bounds.maximumX(); x++) {
                    assertEquals("bounded column " + x + "," + z, complete.column(x, z), bounded.column(x, z));
                }
            }
        }
    }

    @Test
    public void flatTerrainHoldsTheWaterFlushWithTheBankAndContainedByDefault() {
        Compiled compiled = compile(300, (x, z) -> 80, SurfaceTerminal.SINKHOLE, 40);

        assertEquals(0, compiled.field().uncontainedWetCells());
        SurfaceColumn center = compiled.field().column(150, 0);
        assertNotNull(center);
        assertEquals(SurfaceRole.CHANNEL, center.role());
        assertEquals(80, center.headY());
        assertTrue(center.height() <= 77);
        SurfaceColumn edge = compiled.field().column(150, 3);
        assertNotNull(edge);
        assertEquals(SurfaceRole.CHANNEL, edge.role());
        assertTrue(edge.height() > center.height());
        assertTrue(edge.height() <= 79);
        SurfaceColumn shore = compiled.field().column(150, 4);
        assertNotNull(shore);
        assertEquals(SurfaceRole.SHORE, shore.role());
        assertEquals(80, shore.height());
        assertNoWriteAboveNatural(compiled);
        assertChannelContained(compiled);
    }

    @Test
    public void aConfiguredSinkLowersTheWaterBelowTheBankByThatMuch() {
        HydrologyTerrainSampler flat = (int x, int z) -> HydrologyTerrainSample.openLand(80, 0D, "land");
        Compiled sunk = compile(zeroRoughnessSurface(1, HydrologyPlannerSettings.Erosion.defaults()), flat, 300, SurfaceTerminal.SINKHOLE, 40);

        assertEquals(0, sunk.field().uncontainedWetCells());
        assertEquals(79, sunk.field().column(150, 0).headY());
        SurfaceColumn shore = sunk.field().column(150, 4);
        assertEquals(SurfaceRole.SHORE, shore.role());
        assertEquals(80, shore.height());
        assertChannelContained(sunk);
    }

    @Test
    public void disabledErosionKeepsTheChannelShoreAndLipButNoValley() {
        HydrologyTerrainSampler hillside = (int x, int z) -> HydrologyTerrainSample.openLand(90 + Math.max(0, z) / 2, 0D, "land");
        HydrologyPlannerSettings.Erosion off = HydrologyPlannerSettings.Erosion.of(false, 12, 0.45D, 1D, 0.5D);
        Compiled compiled = compile(zeroRoughnessSurface(0, off), hillside, 300, SurfaceTerminal.SINKHOLE, 40);

        assertEquals(0, compiled.field().uncontainedWetCells());
        assertChannelContained(compiled);
        assertNoWriteAboveNatural(compiled);
        for (SurfaceColumn column : compiled.field().columns().values()) {
            assertTrue(column.role() != SurfaceRole.BANK);
        }
        assertNotNull(compiled.field().column(150, 0));
        assertEquals(SurfaceRole.SHORE, compiled.field().column(150, 4).role());
    }

    @Test
    public void erosionDefaultsReproduceTheValleyBitForBitAndACurveReshapesIt() {
        HydrologyTerrainSampler hillside = (int x, int z) -> HydrologyTerrainSample.openLand(90 + Math.max(0, z) / 2, 0D, "land");
        Compiled defaults = compile(zeroRoughnessSurface(0, HydrologyPlannerSettings.Erosion.defaults()), hillside, 300, SurfaceTerminal.SINKHOLE, 40);
        Compiled explicit = compile(zeroRoughnessSurface(0, HydrologyPlannerSettings.Erosion.of(true, 12, 0.45D, 1D, 0.5D)), hillside, 300, SurfaceTerminal.SINKHOLE, 40);
        Compiled steep = compile(zeroRoughnessSurface(0, HydrologyPlannerSettings.Erosion.of(true, 12, 0.45D, 2D, 0.5D)), hillside, 300, SurfaceTerminal.SINKHOLE, 40);

        assertEquals(defaults.field().columns().size(), explicit.field().columns().size());
        for (SurfaceColumn column : defaults.field().columns().values()) {
            SurfaceColumn same = explicit.field().column(column.x(), column.z());
            assertNotNull(same);
            assertEquals(column.height(), same.height());
            assertEquals(column.headY(), same.headY());
            assertEquals(column.role(), same.role());
        }
        boolean differs = false;
        for (SurfaceColumn column : defaults.field().columns().values()) {
            SurfaceColumn other = steep.field().column(column.x(), column.z());
            if (column.role() == SurfaceRole.BANK && other != null && other.height() != column.height()) {
                differs = true;
                break;
            }
        }
        assertTrue(differs);
        assertChannelContained(steep);
        assertNoWriteAboveNatural(steep);
    }

    @Test
    public void blendCurveOneIsTheEasedBlendAndHigherCurvesHoldTheBankTopLonger() {
        assertEquals(SurfaceNoise.smoothStep(0.3D), ErosionFieldCompiler.blend(0.3D, 1D), 0D);
        assertTrue(ErosionFieldCompiler.blend(0.3D, 2D) < ErosionFieldCompiler.blend(0.3D, 1D));
        assertTrue(ErosionFieldCompiler.blend(0.3D, 0.5D) > ErosionFieldCompiler.blend(0.3D, 1D));
        assertEquals(1D, ErosionFieldCompiler.blend(1D, 3D), 0D);
    }

    @Test
    public void hillsideCutGetsABankWhoseWidthGrowsWithTheCut() {
        Compiled compiled = compile(300, (x, z) -> 90 + Math.max(0, z) / 2, SurfaceTerminal.SINKHOLE, 40);

        assertEquals(0, compiled.field().uncontainedWetCells());
        assertNoWriteAboveNatural(compiled);
        assertChannelContained(compiled);
        int lastBank = -1;
        int previous = Integer.MIN_VALUE;
        for (int z = 4; z < 60; z++) {
            SurfaceColumn column = compiled.field().column(150, z);
            if (column == null) {
                break;
            }
            if (column.role() == SurfaceRole.BANK) {
                lastBank = z;
                assertTrue(column.height() >= previous);
                previous = column.height();
            }
        }
        assertTrue(lastBank > 12);
        int stepOutsideChannel = 0;
        for (int z = 6; z <= lastBank; z++) {
            stepOutsideChannel = Math.max(
                    stepOutsideChannel,
                    Math.abs(compiled.field().column(150, z).height() - compiled.field().column(150, z - 1).height())
            );
        }
        assertTrue(stepOutsideChannel <= 1);
    }

    @Test
    public void slopingTerrainKeepsBanksSmoothAndContained() {
        Compiled compiled = compile(400, (x, z) -> 120 - x / 8, SurfaceTerminal.SINKHOLE, 40);

        assertEquals(0, compiled.field().uncontainedWetCells());
        assertNoWriteAboveNatural(compiled);
        assertChannelContained(compiled);
        for (int x = 20; x < 380; x++) {
            for (int z = 5; z < 12; z++) {
                SurfaceColumn column = compiled.field().column(x, z);
                SurfaceColumn next = compiled.field().column(x + 1, z);
                if (column == null || next == null || column.role() == SurfaceRole.CHANNEL || next.role() == SurfaceRole.CHANNEL) {
                    continue;
                }
                assertTrue(Math.abs(column.height() - next.height()) <= 1);
            }
        }
    }

    @Test
    public void oceanAndSubmergedCellsReceiveNoWrites() {
        HydrologyTerrainSampler sampler = (int x, int z) -> x >= 240 || z > 20
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(z <= -20 ? SEA_LEVEL : 100 - x / 6, 0D, "land");
        Compiled compiled = compile(sampler, 300, SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);

        assertEquals(0, compiled.field().uncontainedWetCells());
        for (SurfaceColumn column : compiled.field().columns().values()) {
            HydrologyTerrainSample terrain = sampler.sample(column.x(), column.z());
            if (terrain.ocean() || terrain.naturalHeight() <= SEA_LEVEL) {
                assertTrue(column.apron());
                assertEquals(terrain.naturalHeight(), column.height());
            }
        }
        assertNoWriteAboveNatural(compiled);
    }

    @Test
    public void inletBanksPreserveHighTerrainAndBlendBackWithinTheirExcavationBudget() {
        HydrologyTerrainSampler coast = (int x, int z) -> {
            if (x >= 240) {
                return HydrologyTerrainSample.ocean(50, "ocean");
            }
            int height = x < 200 ? 100 - x / 6 : 66 + (x - 200) * 18 / 40;
            return HydrologyTerrainSample.openLand(height, 0D, "land");
        };
        HydrologyPlannerSettings.Inlet inlet = HydrologyPlannerSettings.Inlet.of(32, 3, 32);
        HydrologyPlannerSettings.Surface surface = withBanks(zeroRoughnessSurface(), zeroRoughnessSurface().banks().withInlet(inlet));
        Compiled compiled = compile(surface, coast, 244, SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);

        assertEquals(0, compiled.field().uncontainedWetCells());
        assertNoWriteAboveNatural(compiled);
        assertChannelContained(compiled);
        int oceanWrites = 0;
        for (SurfaceColumn column : compiled.field().columns().values()) {
            HydrologyTerrainSample terrain = coast.sample(column.x(), column.z());
            if ((terrain.ocean() || terrain.naturalHeight() <= SEA_LEVEL) && !(column.apron() && column.height() == terrain.naturalHeight())) {
                oceanWrites++;
            }
        }
        assertEquals(0, oceanWrites);
        for (int x = 240 - inlet.length(); x < 240; x++) {
            SurfaceColumn center = compiled.field().column(x, 0);
            assertNotNull(center);
            assertEquals(SurfaceRole.CHANNEL, center.role());
            assertEquals(SEA_LEVEL, center.headY());
            assertTrue(center.height() < SEA_LEVEL);
        }
        int pinned = 220;
        int shoreZ = -1;
        for (int z = 1; z < 12; z++) {
            SurfaceColumn column = compiled.field().column(pinned, z);
            assertNotNull(column);
            if (column.role() != SurfaceRole.CHANNEL) {
                shoreZ = z;
                break;
            }
        }
        assertTrue(shoreZ > 0);
        SurfaceColumn shore = compiled.field().column(pinned, shoreZ);
        assertEquals(SurfaceRole.SHORE, shore.role());
        assertEquals(shore.terrain().naturalHeight() - surface.banks().erosion().excavation().maximumDepth(), shore.height());
        assertTrue(coast.sample(pinned, shoreZ).naturalHeight() > SEA_LEVEL + 10);
        int previous = shore.height();
        int banks = 0;
        int lastHeight = shore.height();
        for (int z = shoreZ + 1; z < 60; z++) {
            SurfaceColumn column = compiled.field().column(pinned, z);
            if (column == null) {
                break;
            }
            assertTrue(column.role() == SurfaceRole.SHORE || column.role() == SurfaceRole.BANK);
            assertTrue(column.height() >= previous);
            assertTrue(column.terrain().naturalHeight() - column.height() <= surface.banks().erosion().excavation().maximumDepth());
            previous = column.height();
            lastHeight = column.height();
            banks++;
        }
        assertTrue(banks > 0);
        assertTrue(lastHeight >= coast.sample(pinned, 0).naturalHeight() - 1);
    }

    private static HydrologyPlannerSettings.Surface withBanks(HydrologyPlannerSettings.Surface surface, HydrologyPlannerSettings.Banks banks) {
        return new HydrologyPlannerSettings.Surface(
                surface.enabled(), surface.sources(), surface.minimumWidth(), surface.maximumWidth(),
                surface.minimumDepth(), surface.maximumDepth(), surface.maximumIncision(), surface.shoreWidth(), banks);
    }

    private static HydrologyPlannerSettings.Surface withMaximumIncision(HydrologyPlannerSettings.Surface surface, int maximumIncision) {
        return new HydrologyPlannerSettings.Surface(
                surface.enabled(), surface.sources(), surface.minimumWidth(), surface.maximumWidth(),
                surface.minimumDepth(), surface.maximumDepth(), maximumIncision, surface.shoreWidth(), surface.banks());
    }

    @Test
    public void bankColumnsBeyondTheBlendAreNotWritten() {
        Compiled compiled = compile(300, (x, z) -> 80, SurfaceTerminal.SINKHOLE, 40);

        assertNull(compiled.field().column(150, 40));
    }

    private static void assertNoWriteAboveNatural(Compiled compiled) {
        for (SurfaceColumn column : compiled.field().columns().values()) {
            assertTrue(column.height() <= column.terrain().naturalHeight());
        }
    }

    private static void assertBoundedBankFill(Compiled compiled, int maximumFill) {
        for (SurfaceColumn column : compiled.field().columns().values()) {
            int fill = column.height() - column.terrain().naturalHeight();
            assertTrue(column.x() + "," + column.z() + " fill " + fill,
                    fill <= (column.role() == SurfaceRole.CHANNEL ? 0 : maximumFill));
        }
    }

    private static void assertChannelContained(Compiled compiled) {
        for (SurfaceColumn column : compiled.field().columns().values()) {
            if (column.role() != SurfaceRole.CHANNEL || column.apron()) {
                continue;
            }
            int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] offset : offsets) {
                SurfaceColumn neighbour = compiled.field().column(column.x() + offset[0], column.z() + offset[1]);
                if (neighbour == null) {
                    assertEquals(SEA_LEVEL, column.headY());
                    continue;
                }
                if (neighbour.role() == SurfaceRole.CHANNEL) {
                    continue;
                }
                assertTrue(neighbour.height() >= column.headY());
            }
        }
    }

    private static Compiled compile(int stations, IntBinaryOperator height, SurfaceTerminal terminal, int terminalHead) {
        HydrologyTerrainSampler sampler = (int x, int z) -> HydrologyTerrainSample.openLand(height.applyAsInt(x, z), 0D, "land");
        return compile(sampler, stations, terminal, terminalHead);
    }

    private static Compiled compile(HydrologyTerrainSampler sampler, int stations, SurfaceTerminal terminal, int terminalHead) {
        return compile(zeroRoughnessSurface(), sampler, stations, terminal, terminalHead);
    }

    private static Compiled compile(
            HydrologyPlannerSettings.Surface surface,
            HydrologyTerrainSampler sampler,
            int stations,
            SurfaceTerminal terminal,
            int terminalHead
    ) {
        List<HydrologyPoint> path = List.of(new HydrologyPoint(0, 0, 0), new HydrologyPoint(stations - 1, 0, 0));
        SurfaceCenterline centerline = SurfaceCenterline.densify(path);
        ChannelProfile channel = new ChannelProfileBuilder(surface, sampler, CONSTANT_GEOMETRY)
                .build(centerline, "water", terminal == SurfaceTerminal.OCEAN_MOUTH);
        ValleyProfile valley = new ValleyProfileSolver(surface, sampler, SEA_LEVEL, 64)
                .solve(centerline, channel, terminal, terminalHead);
        assertNull(valley.rejection());
        ErosionField field = new ErosionFieldCompiler(rasterSettings(surface), sampler)
                .compile(1234L, centerline, channel, valley, terminal, 8);
        return new Compiled(field, valley);
    }

    private static HydrologyPlannerSettings.Surface zeroRoughnessSurface() {
        return zeroRoughnessSurface(0, HydrologyPlannerSettings.Erosion.defaults());
    }

    private static HydrologyPlannerSettings.Surface zeroRoughnessSurface(int sink, HydrologyPlannerSettings.Erosion erosion) {
        return zeroRoughnessSurface(sink, erosion, new HydrologyPlannerSettings.Ponds(
                new HydrologyPlannerSettings.Pond(false, 5, 9, 3),
                new HydrologyPlannerSettings.Pond(false, 4, 7, 3)));
    }

    private static HydrologyPlannerSettings.Surface zeroRoughnessSurface(
            int sink,
            HydrologyPlannerSettings.Erosion erosion,
            HydrologyPlannerSettings.Ponds ponds
    ) {
        HydrologyPlannerSettings.Surface defaults = HydrologyPlannerSettings.defaults().surface();
        return new HydrologyPlannerSettings.Surface(
                true,
                defaults.sources(),
                4, 8, 2, 4, 10, 1.5D,
                HydrologyPlannerSettings.Banks.of(sink, 3D, 4, 32, 0D, 16, 2, 6, 1.6D, HydrologyPlannerSettings.Inlet.none(), 2.5D, 24, true, erosion, ponds)
        );
    }

    /** The test bank settings with an explicit roughness, shore width, channel and flow. */
    private static HydrologyPlannerSettings.Surface surfaceWith(
            double roughness,
            int sink,
            double shoreWidth,
            HydrologyPlannerSettings.Erosion erosion,
            HydrologyPlannerSettings.Ponds ponds,
            HydrologyPlannerSettings.Channel channel,
            HydrologyPlannerSettings.Flow flow
    ) {
        HydrologyPlannerSettings.Surface defaults = HydrologyPlannerSettings.defaults().surface();
        return new HydrologyPlannerSettings.Surface(
                true,
                defaults.sources(),
                4, 8, 2, 4, 10, shoreWidth,
                new HydrologyPlannerSettings.Banks(sink, 3D, 4, 32, roughness, 16, 2, 6, 1.6D,
                        HydrologyPlannerSettings.Inlet.none(), 2.5D, 24, true, erosion, ponds, channel, flow)
        );
    }

    private static HydrologyPlannerSettings.Ponds noPonds() {
        return new HydrologyPlannerSettings.Ponds(
                new HydrologyPlannerSettings.Pond(false, 5, 9, 3),
                new HydrologyPlannerSettings.Pond(false, 4, 7, 3));
    }

    private static HydrologyPlannerSettings.Erosion shapedErosion(
            IrisRiverBlendStyle style,
            int terraceSteps,
            double cliffFraction,
            IrisRiverBedProfile bedProfile,
            double shoreRise,
            double blendBaseWidth
    ) {
        return new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D, style, terraceSteps, cliffFraction,
                bedProfile, shoreRise, blendBaseWidth, HydrologyPlannerSettings.Excavation.defaults());
    }

    private static void assertSameField(Compiled expected, Compiled actual) {
        assertEquals(expected.field().columns().size(), actual.field().columns().size());
        for (SurfaceColumn column : expected.field().columns().values()) {
            SurfaceColumn same = actual.field().column(column.x(), column.z());
            assertNotNull(same);
            assertEquals(column.height(), same.height());
            assertEquals(column.headY(), same.headY());
            assertEquals(column.role(), same.role());
        }
    }

    private static boolean differsOn(Compiled base, Compiled other, SurfaceRole role) {
        for (SurfaceColumn column : base.field().columns().values()) {
            if (column.role() != role) {
                continue;
            }
            SurfaceColumn same = other.field().column(column.x(), column.z());
            if (same == null || same.height() != column.height()) {
                return true;
            }
        }
        return false;
    }

    private static void assertHolds(Compiled compiled) {
        assertEquals(0, compiled.field().uncontainedWetCells());
        assertChannelContained(compiled);
        assertNoWriteAboveNatural(compiled);
    }

    private static Set<Integer> bankHeights(Compiled compiled, int x, int fromZ, int toZ) {
        Set<Integer> heights = new HashSet<>();
        for (int z = fromZ; z <= toZ; z++) {
            SurfaceColumn column = compiled.field().column(x, z);
            if (column != null && column.role() == SurfaceRole.BANK) {
                heights.add(column.height());
            }
        }
        return heights;
    }

    private static int outermostBank(Compiled compiled, int x) {
        int outermost = -1;
        for (int z = 1; z < 80; z++) {
            SurfaceColumn column = compiled.field().column(x, z);
            if (column != null && column.role() == SurfaceRole.BANK) {
                outermost = z;
            }
        }
        return outermost;
    }

    private static double widestWetOffset(Compiled compiled) {
        double widest = 0D;
        for (SurfaceColumn column : compiled.field().columns().values()) {
            if (column.role() != SurfaceRole.CHANNEL || column.apron() || column.x() < 40 || column.x() > 280) {
                continue;
            }
            widest = Math.max(widest, Math.abs(column.z()));
        }
        return widest;
    }

    private record Compiled(ErosionField field, ValleyProfile valley) {
    }

    static long key(int x, int z) {
        return RiverFootprint.pack(x, z);
    }

    @Test
    public void blendWidthSmoothingSpreadsASpikeAcrossItsNeighbours() {
        double[][] widths = new double[9][2];
        for (int station = 0; station < widths.length; station++) {
            widths[station][0] = 4D;
            widths[station][1] = 4D;
        }
        widths[4][1] = 34D;

        double[][] smoothed = ErosionFieldCompiler.smoothWidths(widths, 2);

        for (int station = 0; station < widths.length; station++) {
            assertEquals(4D, smoothed[station][0], 1e-9);
        }
        assertEquals(4D, smoothed[0][1], 1e-9);
        assertEquals(10D, smoothed[2][1], 1e-9);
        assertEquals(10D, smoothed[4][1], 1e-9);
        assertEquals(10D, smoothed[6][1], 1e-9);
        assertEquals(4D, smoothed[8][1], 1e-9);
        for (int station = 1; station < widths.length; station++) {
            assertTrue(Math.abs(smoothed[station][1] - smoothed[station - 1][1]) <= 6D + 1e-9);
        }
    }

    @Test
    public void bedBowlIsLevelAcrossTheThalwegAndOneBlockAtTheEdge() {
        assertEquals(1D, ErosionFieldCompiler.bowl(0D, 0.45D), 1e-9);
        assertEquals(1D, ErosionFieldCompiler.bowl(0.45D, 0.45D), 1e-9);
        assertTrue(ErosionFieldCompiler.bowl(0.6D, 0.45D) > 0.8D);
        assertTrue(ErosionFieldCompiler.bowl(0.8D, 0.45D) > 0.3D && ErosionFieldCompiler.bowl(0.8D, 0.45D) < 0.7D);
        assertEquals(0D, ErosionFieldCompiler.bowl(1D, 0.45D), 1e-9);
        double previous = 1D;
        for (double normalized = 0D; normalized <= 1D; normalized += 0.05D) {
            double value = ErosionFieldCompiler.bowl(normalized, 0.45D);
            assertTrue(value <= previous + 1e-9);
            previous = value;
        }
    }

    @Test
    public void everyCellTouchingWaterKeepsTheLipAcrossHeadSteps() {
        Compiled compiled = compile(400, (x, z) -> 120 - x / 8, SurfaceTerminal.SINKHOLE, 40);
        int sink = zeroRoughnessSurface().banks().sink();
        int checked = 0;

        for (SurfaceColumn column : compiled.field().columns().values()) {
            if (column.role() != SurfaceRole.CHANNEL) {
                continue;
            }
            for (int deltaX = -1; deltaX <= 1; deltaX++) {
                for (int deltaZ = -1; deltaZ <= 1; deltaZ++) {
                    SurfaceColumn neighbour = compiled.field().column(column.x() + deltaX, column.z() + deltaZ);
                    if (neighbour == null || neighbour.role() == SurfaceRole.CHANNEL) {
                        continue;
                    }
                    int required = Math.min(neighbour.terrain().naturalHeight(), column.headY() + sink);
                    assertTrue(neighbour.x() + "," + neighbour.z() + " height " + neighbour.height() + " < " + required,
                            neighbour.height() >= required);
                    checked++;
                }
            }
        }
        assertTrue(checked > 100);
    }

    @Test
    public void aDipOneBlockBelowTheWaterAnywhereBesideTheChannelIsHeldByALip() {
        // Whatever the solver sampled, no water cell may border a lower dry cell: a one-block dip
        // beside the channel is either answered by a lower head or by a one-block lip on the dip.
        for (int dipZ = 2; dipZ <= 6; dipZ++) {
            for (int dipX = 148; dipX <= 152; dipX++) {
                int finalZ = dipZ;
                int finalX = dipX;
                Compiled compiled = compile(300, (x, z) -> x == finalX && z == finalZ ? 79 : 80, SurfaceTerminal.SINKHOLE, 40);
                assertEquals("dip at " + finalX + "," + finalZ, 0, compiled.field().uncontainedWetCells());
                SurfaceColumn dip = compiled.field().column(finalX, finalZ);
                if (dip != null) {
                    assertTrue("lip taller than one block at " + finalX + "," + finalZ, dip.height() <= 80);
                }
            }
        }
    }

    @Test
    public void aLowCellBesideTheChannelLowersTheHeadSoTheBankStillHoldsIt() {
        Compiled compiled = compile(300, (x, z) -> x == 150 && z == 4 ? 79 : 80, SurfaceTerminal.SINKHOLE, 40);

        SurfaceColumn center = compiled.field().column(150, 0);
        assertNotNull(center);
        assertTrue("head " + center.headY(), center.headY() <= 79);
        SurfaceColumn low = compiled.field().column(150, 4);
        assertEquals(SurfaceRole.SHORE, low.role());
        assertNotNull(low);
        assertTrue(low.height() >= center.headY());
        assertEquals(0, compiled.field().uncontainedWetCells());
    }

    @Test
    public void oceanApronStartsAtTheFirstOceanStationAndStopsAtTheLimit() {
        HydrologyTerrainSampler sampler = (int x, int z) -> x >= 200
                ? HydrologyTerrainSample.ocean(SEA_LEVEL - 4, "sea")
                : HydrologyTerrainSample.openLand(SEA_LEVEL + 3, 0D, "land");
        HydrologyPlannerSettings.Surface surface = zeroRoughnessSurface();
        List<HydrologyPoint> path = List.of(new HydrologyPoint(0, 0, 0), new HydrologyPoint(259, 0, 0));
        SurfaceCenterline centerline = SurfaceCenterline.densify(path);
        ChannelProfile channel = new ChannelProfileBuilder(surface, sampler, CONSTANT_GEOMETRY)
                .build(centerline, "water", true);
        ValleyProfile valley = new ValleyProfileSolver(surface, sampler, SEA_LEVEL, 64)
                .solve(centerline, channel, SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);
        assertNull(valley.rejection());

        ErosionField field = new ErosionFieldCompiler(rasterSettings(surface), sampler)
                .compile(99L, centerline, channel, valley, SurfaceTerminal.OCEAN_MOUTH, 3);

        int firstApron = Integer.MAX_VALUE;
        int lastApron = Integer.MIN_VALUE;
        for (SurfaceColumn column : field.columns().values()) {
            if (column.apron()) {
                firstApron = Math.min(firstApron, column.x());
                lastApron = Math.max(lastApron, column.x());
                assertEquals(column.terrain().naturalHeight(), column.height());
            }
        }
        assertEquals(200, firstApron);
        assertEquals(202, lastApron);
    }
    @Test
    public void policyShoreBiomeWidthWidensTheShoreRoleOverUntouchedGround() {
        Compiled compiled = compile((int x, int z) -> land(80, 12D), 300, SurfaceTerminal.SINKHOLE, 40);

        SurfaceColumn geometricShore = compiled.field().column(150, 4);
        assertNotNull(geometricShore);
        assertEquals(SurfaceRole.SHORE, geometricShore.role());
        SurfaceColumn band = compiled.field().column(150, 12);
        assertNotNull(band);
        assertEquals(SurfaceRole.SHORE, band.role());
        assertEquals(80, band.height());
        assertNull(compiled.field().column(150, 17));
        assertNoWriteAboveNatural(compiled);
        assertChannelContained(compiled);
    }

    @Test
    public void zeroShoreBiomeWidthLeavesTheGeometricShoreWithTheBankRole() {
        Compiled compiled = compile((int x, int z) -> land(80, 0D), 300, SurfaceTerminal.SINKHOLE, 40);

        SurfaceColumn geometricShore = compiled.field().column(150, 4);
        assertNotNull(geometricShore);
        assertEquals(SurfaceRole.BANK, geometricShore.role());
        assertEquals(80, geometricShore.height());
        assertNull(compiled.field().column(150, 6));
        assertChannelContained(compiled);
    }

    private static HydrologyTerrainSample land(int height, double shoreBiomeWidth) {
        HydrologyTerrainSample open = HydrologyTerrainSample.openLand(height, 0D, "land");
        return new HydrologyTerrainSample(
                open.naturalHeight(), open.slope(), open.ocean(), open.caveAvailable(), open.caveFloorY(), open.caveFluidY(),
                open.transitAllowed(), open.outletAllowed(), open.surfaceSourceAllowed(), open.surfaceSourceRequired(),
                open.undergroundSourceAllowed(), open.undergroundSourceRequired(), open.routingCost(),
                open.surfaceSourceWeight(), open.undergroundSourceWeight(), open.widthMultiplier(), open.depthMultiplier(),
                open.incisionMultiplier(), open.routingMultiplier(), open.bankMultiplier(), open.parentBiomeKey(),
                open.surfaceBiomeKey(), open.mouthBiomeKey(), open.shoreBiomeKey(), open.bankBiomeKey(),
                open.floodedCaveBiomeKey(), open.preferredProfileKeys(), open.surfacePoolKeys(), shoreBiomeWidth, null, Double.NaN, true,
                SurfaceRiverPolicy.INHERIT
        );
    }
    @Test
    public void aSourcePondOpensAroundTheHeadwaterAtTheSourceHead() {
        HydrologyTerrainSampler flat = (int x, int z) -> HydrologyTerrainSample.openLand(80, 0D, "land");
        HydrologyPlannerSettings.Ponds ponds = new HydrologyPlannerSettings.Ponds(
                new HydrologyPlannerSettings.Pond(true, 10, 10, 3),
                new HydrologyPlannerSettings.Pond(false, 4, 7, 3));
        Compiled compiled = compile(zeroRoughnessSurface(0, HydrologyPlannerSettings.Erosion.defaults(), ponds), flat, 300, SurfaceTerminal.SINKHOLE, 40);
        Compiled plain = compile(300, (x, z) -> 80, SurfaceTerminal.SINKHOLE, 40);

        assertEquals(0, compiled.field().uncontainedWetCells());
        assertChannelContained(compiled);
        assertNoWriteAboveNatural(compiled);
        SurfaceColumn centre = compiled.field().column(0, 0);
        assertEquals(SurfaceRole.CHANNEL, centre.role());
        assertEquals(80, centre.headY());
        assertTrue(centre.height() <= 77);
        SurfaceColumn behind = compiled.field().column(-9, 0);
        assertNotNull(behind);
        assertEquals(SurfaceRole.CHANNEL, behind.role());
        assertEquals(80, behind.headY());
        SurfaceColumn beside = compiled.field().column(0, 9);
        assertEquals(SurfaceRole.CHANNEL, beside.role());
        assertEquals(0, behind.station());
        SurfaceColumn plainBehind = plain.field().column(-9, 0);
        assertTrue(plainBehind == null || plainBehind.role() != SurfaceRole.CHANNEL);
        assertEquals(SurfaceRole.SHORE, compiled.field().column(-11, 0).role());
        assertNotNull(compiled.field().column(150, 0));
        assertEquals(SurfaceRole.CHANNEL, compiled.field().column(150, 0).role());
        assertTrue(compiled.field().column(150, 4).role() == SurfaceRole.SHORE);
    }

    @Test
    public void aTerminalPondOpensAtAnInlandEndButNotAtAnOceanMouth() {
        HydrologyTerrainSampler flat = (int x, int z) -> HydrologyTerrainSample.openLand(80, 0D, "land");
        HydrologyPlannerSettings.Ponds ponds = new HydrologyPlannerSettings.Ponds(
                new HydrologyPlannerSettings.Pond(false, 5, 9, 3),
                new HydrologyPlannerSettings.Pond(true, 5, 5, 2));
        Compiled inland = compile(zeroRoughnessSurface(0, HydrologyPlannerSettings.Erosion.defaults(), ponds), flat, 300, SurfaceTerminal.SINKHOLE, 40);

        assertEquals(0, inland.field().uncontainedWetCells());
        assertChannelContained(inland);
        SurfaceColumn end = inland.field().column(299, 0);
        assertEquals(SurfaceRole.CHANNEL, end.role());
        assertTrue(end.height() <= 78);
        SurfaceColumn beyond = inland.field().column(303, 0);
        assertNotNull(beyond);
        assertEquals(SurfaceRole.CHANNEL, beyond.role());
        assertEquals(299, beyond.station());
        SurfaceColumn plainBeyond = compile(300, (x, z) -> 80, SurfaceTerminal.SINKHOLE, 40).field().column(303, 0);
        assertTrue(plainBeyond == null || plainBeyond.role() != SurfaceRole.CHANNEL);

        HydrologyTerrainSampler coast = (int x, int z) -> x >= 240
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(100 - x / 6, 0D, "land");
        Compiled mouth = compile(zeroRoughnessSurface(0, HydrologyPlannerSettings.Erosion.defaults(), ponds), coast, 300, SurfaceTerminal.OCEAN_MOUTH, SEA_LEVEL);
        int wetBeyondTheShore = 0;
        for (SurfaceColumn column : mouth.field().columns().values()) {
            if (column.role() == SurfaceRole.CHANNEL && !column.apron() && column.x() >= 236 && Math.abs(column.z()) > 4) {
                wetBeyondTheShore++;
            }
        }
        assertEquals(0, wetBeyondTheShore);
    }

    @Test
    public void aPondShrinksWhereTheGroundFallsAwayAroundItsRim() {
        HydrologyTerrainSampler ridge = (int x, int z) -> HydrologyTerrainSample.openLand(z > 7 || z < -7 ? 70 : 80, 0D, "land");
        HydrologyPlannerSettings.Ponds ponds = new HydrologyPlannerSettings.Ponds(
                new HydrologyPlannerSettings.Pond(true, 4, 12, 3),
                new HydrologyPlannerSettings.Pond(false, 4, 7, 3));
        Compiled compiled = compile(zeroRoughnessSurface(0, HydrologyPlannerSettings.Erosion.defaults(), ponds), ridge, 300, SurfaceTerminal.SINKHOLE, 40);

        assertEquals(0, compiled.field().uncontainedWetCells());
        assertChannelContained(compiled);
        assertBoundedBankFill(compiled, 8);
        assertEquals(80, compiled.field().column(0, 0).headY());
        SurfaceColumn nearRim = compiled.field().column(0, 4);
        assertEquals(SurfaceRole.CHANNEL, nearRim.role());
        assertTrue(compiled.field().columns().values().stream()
                .anyMatch(column -> column.height() > column.terrain().naturalHeight()));
        for (SurfaceColumn column : compiled.field().columns().values()) {
            if (column.role() == SurfaceRole.CHANNEL) {
                assertTrue(Math.abs(column.z()) <= 7);
            }
        }
    }

    @Test
    public void pondsAreDeterministicForACourseSeed() {
        HydrologyTerrainSampler flat = (int x, int z) -> HydrologyTerrainSample.openLand(80, 0D, "land");
        HydrologyPlannerSettings.Surface surface = zeroRoughnessSurface(0, HydrologyPlannerSettings.Erosion.defaults(), HydrologyPlannerSettings.Ponds.defaults());
        Compiled first = compile(surface, flat, 300, SurfaceTerminal.SINKHOLE, 40);
        Compiled second = compile(surface, flat, 300, SurfaceTerminal.SINKHOLE, 40);

        assertEquals(first.field().columns().size(), second.field().columns().size());
        for (SurfaceColumn column : first.field().columns().values()) {
            SurfaceColumn same = second.field().column(column.x(), column.z());
            assertNotNull(same);
            assertEquals(column.height(), same.height());
            assertEquals(column.role(), same.role());
        }
        assertEquals(0, first.field().uncontainedWetCells());
    }

    @Test
    public void blendStyleDefaultsReproduceTheValleyAndEachStyleReshapesIt() {
        HydrologyTerrainSampler hillside = (int x, int z) -> HydrologyTerrainSample.openLand(90 + Math.max(0, z) / 2, 0D, "land");
        Compiled defaults = compile(zeroRoughnessSurface(0, HydrologyPlannerSettings.Erosion.defaults()), hillside, 300, SurfaceTerminal.SINKHOLE, 40);
        Compiled smooth = compile(
                zeroRoughnessSurface(0, shapedErosion(IrisRiverBlendStyle.SMOOTH, 4, 0.5D, IrisRiverBedProfile.BOWL, 0D, 0D)),
                hillside, 300, SurfaceTerminal.SINKHOLE, 40);

        assertSameField(defaults, smooth);
        for (IrisRiverBlendStyle style : new IrisRiverBlendStyle[]{
                IrisRiverBlendStyle.LINEAR, IrisRiverBlendStyle.CONCAVE, IrisRiverBlendStyle.TERRACED, IrisRiverBlendStyle.CLIFF}) {
            Compiled shaped = compile(
                    zeroRoughnessSurface(0, shapedErosion(style, 4, 0.5D, IrisRiverBedProfile.BOWL, 0D, 0D)),
                    hillside, 300, SurfaceTerminal.SINKHOLE, 40);
            assertTrue(style.name(), differsOn(defaults, shaped, SurfaceRole.BANK));
            assertHolds(shaped);
        }

        // A shelf whose ground is level from z = 8 outward, so one cross-section shows the step pattern
        // of a style directly: every bank column out there is cut from the same 16-block rise.
        HydrologyTerrainSampler shelf = (int x, int z) ->
                HydrologyTerrainSample.openLand(80 + Math.min(16, Math.max(0, Math.abs(z) - 4) * 4), 0D, "land");
        Compiled terraced = compile(
                withMaximumIncision(zeroRoughnessSurface(0, new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D,
                        IrisRiverBlendStyle.TERRACED, 4, 0.5D, IrisRiverBedProfile.BOWL, 0D, 0D,
                        new HydrologyPlannerSettings.Excavation(64, 64, 8192))), 32),
                shelf, 300, SurfaceTerminal.SINKHOLE, 40);
        assertNull(terraced.field().rejection());
        Set<Integer> steps = bankHeights(terraced, 150, 9, 40);
        assertTrue(steps.toString(), steps.size() >= 2 && steps.size() <= 4);
        assertHolds(terraced);

        Compiled cliff = compile(
                withMaximumIncision(zeroRoughnessSurface(0, new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D,
                        IrisRiverBlendStyle.CLIFF, 4, 0.5D, IrisRiverBedProfile.BOWL, 0D, 0D,
                        new HydrologyPlannerSettings.Excavation(64, 64, 8192))), 32),
                shelf, 300, SurfaceTerminal.SINKHOLE, 40);
        assertNull(cliff.field().rejection());
        Set<Integer> bench = bankHeights(cliff, 150, 9, 40);
        assertEquals(bench.toString(), 2, bench.size());
        assertTrue(bench.contains(80));
        assertTrue(bench.contains(96));
        assertHolds(cliff);
    }

    @Test
    public void bedProfileDefaultsReproduceTheBedAndFlatVAndUDiffer() {
        HydrologyTerrainSampler flat = (int x, int z) -> HydrologyTerrainSample.openLand(80, 0D, "land");
        Compiled defaults = compile(zeroRoughnessSurface(0, HydrologyPlannerSettings.Erosion.defaults()), flat, 300, SurfaceTerminal.SINKHOLE, 40);
        Compiled bowl = compile(
                zeroRoughnessSurface(0, shapedErosion(IrisRiverBlendStyle.SMOOTH, 4, 0.5D, IrisRiverBedProfile.BOWL, 0D, 0D)),
                flat, 300, SurfaceTerminal.SINKHOLE, 40);

        assertSameField(defaults, bowl);
        for (IrisRiverBedProfile profile : new IrisRiverBedProfile[]{
                IrisRiverBedProfile.FLAT, IrisRiverBedProfile.V, IrisRiverBedProfile.U}) {
            Compiled shaped = compile(
                    zeroRoughnessSurface(0, shapedErosion(IrisRiverBlendStyle.SMOOTH, 4, 0.5D, profile, 0D, 0D)),
                    flat, 300, SurfaceTerminal.SINKHOLE, 40);
            assertTrue(profile.name(), differsOn(defaults, shaped, SurfaceRole.CHANNEL));
            assertHolds(shaped);
        }
    }

    @Test
    public void shoreRiseLiftsTheBenchTowardTheLandAndZeroKeepsItFlat() {
        HydrologyTerrainSampler hillside = (int x, int z) -> HydrologyTerrainSample.openLand(90 + Math.max(0, z) / 2, 0D, "land");
        Compiled level = compile(
                surfaceWith(0D, 0, 4D, HydrologyPlannerSettings.Erosion.defaults(), noPonds(),
                        HydrologyPlannerSettings.Channel.defaults(), HydrologyPlannerSettings.Flow.defaults()),
                hillside, 300, SurfaceTerminal.SINKHOLE, 40);
        Compiled explicit = compile(
                surfaceWith(0D, 0, 4D, shapedErosion(IrisRiverBlendStyle.SMOOTH, 4, 0.5D, IrisRiverBedProfile.BOWL, 0D, 0D), noPonds(),
                        HydrologyPlannerSettings.Channel.defaults(), HydrologyPlannerSettings.Flow.defaults()),
                hillside, 300, SurfaceTerminal.SINKHOLE, 40);
        Compiled rising = compile(
                surfaceWith(0D, 0, 4D, shapedErosion(IrisRiverBlendStyle.SMOOTH, 4, 0.5D, IrisRiverBedProfile.BOWL, 2D, 0D), noPonds(),
                        HydrologyPlannerSettings.Channel.defaults(), HydrologyPlannerSettings.Flow.defaults()),
                hillside, 300, SurfaceTerminal.SINKHOLE, 40);

        assertSameField(level, explicit);
        int water = level.field().column(150, 0).headY();
        assertEquals(water, level.field().column(150, 7).height());
        assertEquals(water + 2, rising.field().column(150, 7).height());
        int previous = water;
        for (int z = 4; z <= 7; z++) {
            SurfaceColumn column = rising.field().column(150, z);
            assertNotNull("bench column at z " + z, column);
            assertTrue(column.height() >= previous);
            assertTrue(column.height() <= water + 2);
            assertTrue(column.height() <= hillside.sample(150, z).naturalHeight());
            previous = column.height();
        }
        assertEquals(0, rising.field().uncontainedWetCells());
        assertChannelContained(rising);
        assertBoundedBankFill(rising, 8);
        assertEquals(92, rising.field().column(150, -7).height());
        assertEquals(90, rising.field().column(150, -7).terrain().naturalHeight());
    }

    @Test
    public void zeroBankDepthBudgetLeavesLowBanksUnfilledAndUncontained() {
        HydrologyPlannerSettings.Erosion erosion = new HydrologyPlannerSettings.Erosion(
                true, 12, 0.45D, 1D, 0.5D, IrisRiverBlendStyle.SMOOTH, 4, 0.5D,
                IrisRiverBedProfile.BOWL, 0D, 0D, new HydrologyPlannerSettings.Excavation(0, 16, 256));
        HydrologyPlannerSettings.Surface surface = zeroRoughnessSurface(0, erosion);
        HydrologyTerrainSampler lowGround = (x, z) -> HydrologyTerrainSample.openLand(77, 0D, "land");
        SurfaceCenterline centerline = SurfaceCenterline.densify(
                List.of(new HydrologyPoint(0, 80, 0), new HydrologyPoint(16, 80, 0)));
        int count = centerline.size();
        double[] widths = new double[count];
        double[] depths = new double[count];
        double[] multipliers = new double[count];
        int[] heads = new int[count];
        Arrays.fill(widths, 3D);
        Arrays.fill(depths, 2D);
        Arrays.fill(multipliers, 1D);
        Arrays.fill(heads, 80);
        ErosionField field = new ErosionFieldCompiler(rasterSettings(surface), lowGround).compile(42L,
                centerline, new ChannelProfile(widths, depths, multipliers), ValleyProfile.fromHeads(heads, count),
                SurfaceTerminal.SINKHOLE, 0);

        assertEquals(HydrologyCandidateRejection.SURFACE_WATER_CONTAINMENT, field.rejection());
        assertTrue(field.uncontainedWetCells() > 0);
        assertEquals(77, field.column(8, 4).height());
        for (SurfaceColumn column : field.columns().values()) {
            if (column.role() != SurfaceRole.CHANNEL) {
                assertEquals(column.terrain().naturalHeight(), column.height());
            }
        }
    }

    @Test
    public void blendBaseWidthWidensEveryValley() {
        HydrologyTerrainSampler hillside = (int x, int z) -> HydrologyTerrainSample.openLand(90 + Math.max(0, z) / 2, 0D, "land");
        Compiled defaults = compile(zeroRoughnessSurface(0, HydrologyPlannerSettings.Erosion.defaults()), hillside, 300, SurfaceTerminal.SINKHOLE, 40);
        Compiled explicit = compile(
                zeroRoughnessSurface(0, shapedErosion(IrisRiverBlendStyle.SMOOTH, 4, 0.5D, IrisRiverBedProfile.BOWL, 0D, 0D)),
                hillside, 300, SurfaceTerminal.SINKHOLE, 40);
        Compiled wide = compile(
                zeroRoughnessSurface(0, shapedErosion(IrisRiverBlendStyle.SMOOTH, 4, 0.5D, IrisRiverBedProfile.BOWL, 0D, 12D)),
                hillside, 300, SurfaceTerminal.SINKHOLE, 40);

        assertSameField(defaults, explicit);
        assertTrue(differsOn(defaults, wide, SurfaceRole.BANK));
        assertTrue(outermostBank(wide, 150) > outermostBank(defaults, 150));
        assertHolds(wide);
    }

    @Test
    public void outlineRatiosBoundTheWaterlineWobble() {
        HydrologyTerrainSampler flat = (int x, int z) -> HydrologyTerrainSample.openLand(80, 0D, "land");
        Compiled wobbly = compile(
                surfaceWith(1D, 0, 1.5D, HydrologyPlannerSettings.Erosion.defaults(), noPonds(),
                        HydrologyPlannerSettings.Channel.defaults(), HydrologyPlannerSettings.Flow.defaults()),
                flat, 300, SurfaceTerminal.SINKHOLE, 40);
        Compiled explicit = compile(
                surfaceWith(1D, 0, 1.5D, HydrologyPlannerSettings.Erosion.defaults(), noPonds(),
                        new HydrologyPlannerSettings.Channel(16, 0.6D, 1.4D, 1D), HydrologyPlannerSettings.Flow.defaults()),
                flat, 300, SurfaceTerminal.SINKHOLE, 40);
        Compiled bounded = compile(
                surfaceWith(1D, 0, 1.5D, HydrologyPlannerSettings.Erosion.defaults(), noPonds(),
                        new HydrologyPlannerSettings.Channel(16, 1D, 1D, 1D), HydrologyPlannerSettings.Flow.defaults()),
                flat, 300, SurfaceTerminal.SINKHOLE, 40);

        assertSameField(wobbly, explicit);
        assertTrue("wobble " + widestWetOffset(wobbly), widestWetOffset(wobbly) > 3.25D);
        assertTrue("bounded " + widestWetOffset(bounded), widestWetOffset(bounded) <= 3.25D);
        assertHolds(bounded);
    }

    @Test
    public void plungeBasinKnobsControlTheScourAfterADrop() {
        // Level ground broken by a two-block step every eight stations: every step is a drop the
        // default plunge basin scours after.
        HydrologyTerrainSampler stair = (int x, int z) ->
                HydrologyTerrainSample.openLand(240 - 2 * (Math.max(0, x) / 8), 0D, "land");
        Compiled defaults = compile(
                surfaceWith(0D, 0, 1.5D, HydrologyPlannerSettings.Erosion.defaults(), noPonds(),
                        HydrologyPlannerSettings.Channel.defaults(), HydrologyPlannerSettings.Flow.defaults()),
                stair, 70, SurfaceTerminal.SINKHOLE, 40);
        Compiled explicit = compile(
                surfaceWith(0D, 0, 1.5D, HydrologyPlannerSettings.Erosion.defaults(), noPonds(),
                        HydrologyPlannerSettings.Channel.defaults(), new HydrologyPlannerSettings.Flow(0.65D, 2, 2D, 1)),
                stair, 70, SurfaceTerminal.SINKHOLE, 40);
        Compiled none = compile(
                surfaceWith(0D, 0, 1.5D, HydrologyPlannerSettings.Erosion.defaults(), noPonds(),
                        HydrologyPlannerSettings.Channel.defaults(), new HydrologyPlannerSettings.Flow(0.65D, 99, 2D, 1)),
                stair, 70, SurfaceTerminal.SINKHOLE, 40);
        Compiled deep = compile(
                surfaceWith(0D, 0, 1.5D, HydrologyPlannerSettings.Erosion.defaults(), noPonds(),
                        HydrologyPlannerSettings.Channel.defaults(), new HydrologyPlannerSettings.Flow(0.65D, 2, 2D, 3)),
                stair, 70, SurfaceTerminal.SINKHOLE, 40);

        assertSameField(defaults, explicit);
        assertTrue(none.field().column(35, 0).height() > defaults.field().column(35, 0).height());
        assertTrue(deep.field().column(35, 0).height() < defaults.field().column(35, 0).height());
        assertHolds(defaults);
        assertHolds(none);
        assertHolds(deep);
    }

    @Test
    public void policyShoreWidthReplacesTheBenchPerColumn() {
        HydrologyTerrainSampler split = (int x, int z) -> {
            HydrologyTerrainSample open = HydrologyTerrainSample.openLand(80, 0D, "land");
            return z > 0 ? open.withShoreWidth(4D) : open;
        };
        Compiled compiled = compile(zeroRoughnessSurface(), split, 300, SurfaceTerminal.SINKHOLE, 40);

        for (int z = 4; z <= 7; z++) {
            SurfaceColumn bench = compiled.field().column(150, z);
            assertNotNull("wide bench column at z " + z, bench);
            assertTrue(bench.role() != SurfaceRole.CHANNEL);
            assertEquals(80, bench.height());
        }
        assertNull(compiled.field().column(150, 8));
        assertNotNull(compiled.field().column(150, -4));
        assertNull(compiled.field().column(150, -5));
        assertHolds(compiled);
    }

    @Test
    public void policyErosionFalseKeepsOnlyTheChannelAndBench() {
        HydrologyTerrainSampler hillside = (int x, int z) -> HydrologyTerrainSample.openLand(90 + Math.max(0, z) / 2, 0D, "land");
        HydrologyTerrainSampler quiet = (int x, int z) -> hillside.sample(x, z).withErosion(false);
        Compiled eroded = compile(zeroRoughnessSurface(), hillside, 300, SurfaceTerminal.SINKHOLE, 40);
        Compiled bare = compile(zeroRoughnessSurface(), quiet, 300, SurfaceTerminal.SINKHOLE, 40);

        boolean anyBank = false;
        for (SurfaceColumn column : eroded.field().columns().values()) {
            if (column.role() == SurfaceRole.BANK) {
                anyBank = true;
                break;
            }
        }
        assertTrue(anyBank);
        for (SurfaceColumn column : bare.field().columns().values()) {
            assertTrue(column.x() + "," + column.z(), column.role() != SurfaceRole.BANK);
        }
        assertEquals(SurfaceRole.SHORE, bare.field().column(150, 4).role());
        assertHolds(bare);
    }
    private static HydrologyPlannerSettings rasterSettings(HydrologyPlannerSettings.Surface surface) {
        HydrologyPlannerSettings defaults = HydrologyPlannerSettings.defaults();
        return new HydrologyPlannerSettings(60, defaults.routing(), surface, defaults.hydraulics(),
                defaults.underground(), defaults.outlets(), defaults.geometry(), defaults.deepFluids(),
                defaults.surfacePools(), 12D, defaults.seaCaves(), defaults.surfacePolicyBounds());
    }

}
