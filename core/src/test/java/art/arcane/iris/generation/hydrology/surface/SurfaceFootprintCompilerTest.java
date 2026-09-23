package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydraulicChannelProfile;
import art.arcane.iris.generation.hydrology.HydraulicSegment;
import art.arcane.iris.generation.hydrology.HydrologyCandidateRejection;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.HydrologyGeometrySampler;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyPoint;
import art.arcane.iris.generation.hydrology.HydrologySurfaceDropRaster;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSampler;
import art.arcane.iris.generation.hydrology.RiverCourse;
import art.arcane.iris.generation.hydrology.RiverCourseType;
import art.arcane.iris.generation.hydrology.RiverFootprint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SurfaceFootprintCompilerTest {
    private static final int SEA_LEVEL = 60;
    private static final HydrologyGeometrySampler CONSTANT_GEOMETRY = request -> switch (request.field()) {
        case SURFACE_WIDTH -> 6;
        case SURFACE_DEPTH -> 3;
        default -> request.minimum();
    };

    @Test
    public void impossibleWetIncisionRejectsBeforeSamplingWideBanks() {
        AtomicInteger bankSamples = new AtomicInteger();
        HydrologyTerrainSampler sampler = (x, z) -> {
            if (Math.abs(z) > 8) {
                bankSamples.incrementAndGet();
            }
            return HydrologyTerrainSample.openLand(x >= 60 && x <= 68 ? 170 : 90, 0D, "land");
        };
        RiverCourse course = course(List.of(segment(1L, HydrologyFeatureType.SURFACE_POOL,
                89, 89, points(0, 200, 89))));

        SurfaceFootprint footprint = compiler(sampler).compileForPublication(course);

        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, footprint.rejection());
        assertTrue("Wide-bank terrain samples: " + bankSamples.get(), bankSamples.get() < 1_000);
        SurfaceFootprint complete = compiler(sampler).compile(course);
        assertEquals(complete.rejection(), footprint.rejection());
        assertTrue(complete.columns().size() > footprint.columns().size());
    }

    @Test
    public void mouthUsesConnectedFloodedLandWithoutChangingTerrainMetadataOrOwningWater() {
        int seaLevel = HydrologyPlannerSettings.defaults().seaLevel();
        HydrologyTerrainSampler sampler = (x, z) -> x >= 132
                ? HydrologyTerrainSample.ocean(seaLevel - 5, "ocean")
                : HydrologyTerrainSample.openLand(x >= 100 ? seaLevel - 5 : seaLevel, 0D, "land");
        RiverCourse course = course(List.of(
                segment(1L, HydrologyFeatureType.MOUTH, seaLevel, seaLevel, points(0, 140, seaLevel))));

        SurfaceFootprint footprint = compiler(sampler).compile(course);

        assertNull(footprint.rejection());
        assertEquals(0, footprint.uncontainedWetCells());
        SurfaceLayerColumn receiver = column(footprint, 100, 0);
        assertNotNull(receiver);
        assertEquals(sampler.sample(100, 0), receiver.terrain());
        assertFalse(receiver.terrain().ocean());
        assertTrue(receiver.layer().oceanApron());
        assertFalse(receiver.layer().terrainOwned());
        assertFalse(receiver.layer().fluidOwned());
        SurfaceBounds bounds = new SurfaceBounds(90, -8, 110, 8);
        SurfaceFootprint bounded = compiler(sampler).compile(course, bounds);
        assertEquals(footprint.columns().stream().filter(value -> bounds.contains(value.x(), value.z())).toList(),
                bounded.columns());
    }

    @Test
    public void mouthCannotUseAnInlandPoolSeparatedFromTheOceanByDryGround() {
        int seaLevel = HydrologyPlannerSettings.defaults().seaLevel();
        HydrologyTerrainSampler sampler = (x, z) -> x >= 132
                ? HydrologyTerrainSample.ocean(seaLevel - 5, "ocean")
                : HydrologyTerrainSample.openLand(x >= 100 && x != 120 ? seaLevel - 5 : seaLevel, 0D, "land");
        RiverCourse course = course(List.of(
                segment(1L, HydrologyFeatureType.MOUTH, seaLevel, seaLevel, points(0, 140, seaLevel))));

        SurfaceFootprint footprint = compiler(sampler).compile(course);

        assertNotNull(footprint.rejection());
        assertNull(column(footprint, 100, 0));
    }

    @Test
    public void compiledLayersDescribeAContainedCarveOnlyChannelWithShoreAndBank() {
        HydrologyTerrainSampler sampler = (int x, int z) -> x >= 150 && z > 8
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(80 + Math.max(0, -z) / 2, 0D, "land");
        RiverCourse course = course(List.of(
                segment(1L, HydrologyFeatureType.SURFACE_POOL, 79, 79, points(0, 60, 79)),
                segment(2L, HydrologyFeatureType.RIFFLE, 79, 78, List.of(new HydrologyPoint(60, 79, 0), new HydrologyPoint(61, 78, 0))),
                segment(3L, HydrologyFeatureType.SURFACE_POOL, 78, 78, points(61, 140, 78))
        ));
        SurfaceFootprint footprint = compiler(sampler).compile(course);

        assertEquals(0, footprint.uncontainedWetCells());
        assertFalse(footprint.columns().isEmpty());
        HashSet<Long> keys = new HashSet<>();
        for (SurfaceLayerColumn column : footprint.columns()) {
            assertTrue(keys.add(RiverFootprint.pack(column.x(), column.z())));
            HydrologyColumnLayer layer = column.layer();
            assertTrue(layer.bedY() <= column.terrain().naturalHeight());
            assertEquals(layer.fluidHeadY(), layer.ceilingY());
            assertEquals(1L, layer.feature().courseId());
            assertTrue(layer.feature().type().isSurface());
            if (layer.channel()) {
                assertTrue(layer.fluidOwned());
                assertTrue(layer.connectedFluid());
                assertTrue(layer.terrainOwned());
                assertTrue(layer.fluidHeadY() < column.terrain().naturalHeight());
                assertTrue(layer.bedY() < layer.fluidHeadY());
            } else {
                assertTrue(layer.grading());
                assertEquals(layer.bedY() != column.terrain().naturalHeight(), layer.terrainOwned());
                assertFalse(layer.fluidOwned());
                assertEquals(layer.bedY(), layer.fluidHeadY());
            }
        }
        SurfaceLayerColumn center = column(footprint, 30, 0);
        assertNotNull(center);
        assertTrue(center.layer().channel());
        assertEquals(79, center.layer().fluidHeadY());
        assertTrue(center.layer().feature().source() == false);
        SurfaceLayerColumn source = column(footprint, 0, 0);
        assertNotNull(source);
        assertTrue(source.layer().feature().source());
        SurfaceLayerColumn shore = column(footprint, 30, 4);
        assertNotNull(shore);
        assertTrue(shore.layer().shore());
        assertEquals("land", shore.layer().shoreBiomeKey());
        SurfaceLayerColumn bank = column(footprint, 30, -8);
        assertNotNull(bank);
        assertTrue(bank.layer().grading());
        assertFalse(bank.layer().shore());
        assertNull(column(footprint, 160, 12));
    }

    @Test
    public void plannedCoastalWaterfallRetainsAConnectedOceanReceiverWithoutCuttingTheCliffToSeaLevel() {
        int seaLevel = HydrologyPlannerSettings.defaults().seaLevel();
        HydrologyTerrainSampler sampler = (x, z) -> x >= 100
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(100, 0D, "land");
        SurfaceCourseResult result = new SurfaceCourseBuilder(HydrologyPlannerSettings.defaults().surface(),
                sampler, CONSTANT_GEOMETRY, seaLevel).build(7L, 1L, "water",
                List.of(new HydrologyPoint(0, 97, 0), new HydrologyPoint(99, 97, 0),
                        new HydrologyPoint(100, seaLevel, 0)), SurfaceTerminal.OCEAN_MOUTH, seaLevel, 64);
        assertNull(result.rejection());
        ArrayList<HydraulicSegment> segments = new ArrayList<>(result.segments());
        segments.add(segment(77L, HydrologyFeatureType.MOUTH, seaLevel, seaLevel,
                List.of(result.pathEnd())));
        SurfaceFootprint footprint = compiler(sampler).compile(course(segments));

        assertNull(footprint.rejection());
        assertEquals(0, footprint.uncontainedWetCells());
        assertTrue(footprint.columns().stream().anyMatch(column -> column.layer().channel()
                && column.layer().fluidOwned() && column.layer().fluidHeadY() == 97));
        for (SurfaceLayerColumn column : footprint.columns()) {
            if (!column.apron() && column.role() == SurfaceRole.CHANNEL) {
                assertTrue(column.layer().bedY() >= 90);
            }
        }
    }

    @Test
    public void mouthOverTheOceanPublishesOnlyANonOwningApron() {
        HydrologyTerrainSampler sampler = (int x, int z) -> x >= 100
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(70, 0D, "land");
        RiverCourse course = course(List.of(
                segment(1L, HydrologyFeatureType.SURFACE_POOL, 69, 69, points(0, 96, 69)),
                segment(2L, HydrologyFeatureType.WATERFALL, 69, SEA_LEVEL, List.of(new HydrologyPoint(96, 69, 0), new HydrologyPoint(97, SEA_LEVEL, 0))),
                segment(3L, HydrologyFeatureType.MOUTH, SEA_LEVEL, SEA_LEVEL, points(97, 112, SEA_LEVEL))
        ));
        SurfaceFootprint footprint = compiler(sampler).compile(course);

        int aprons = 0;
        for (SurfaceLayerColumn column : footprint.columns()) {
            if (column.terrain().ocean()) {
                assertTrue(column.layer().oceanApron());
                assertFalse(column.layer().terrainOwned());
                assertFalse(column.layer().fluidOwned());
                assertEquals(HydrologyFeatureType.MOUTH, column.layer().feature().type());
                aprons++;
            } else {
                assertFalse(column.layer().oceanApron());
            }
        }
        assertTrue(aprons > 0);
        assertNull(column(footprint, 111, 0));
    }

    @Test
    public void inletColumnsPublishSeaLevelFluidOverALoweredBed() {
        HydrologyTerrainSampler sampler = (int x, int z) -> x >= 100
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(70, 0D, "land");
        HydrologyPlannerSettings.Inlet inlet = HydrologyPlannerSettings.defaults().surface().banks().inlet();
        ArrayList<HydrologyPoint> ramp = new ArrayList<>();
        for (int x = 30; x <= 39; x++) {
            ramp.add(new HydrologyPoint(x, 69 - (x - 30), 0));
        }
        RiverCourse course = course(List.of(
                segment(1L, HydrologyFeatureType.SURFACE_POOL, 69, 69, points(0, 30, 69)),
                segment(2L, HydrologyFeatureType.CASCADE, 69, SEA_LEVEL, List.copyOf(ramp)),
                segment(3L, HydrologyFeatureType.SURFACE_POOL, SEA_LEVEL, SEA_LEVEL, points(39, 99, SEA_LEVEL)),
                segment(4L, HydrologyFeatureType.MOUTH, SEA_LEVEL, SEA_LEVEL, points(99, 101, SEA_LEVEL))
        ));
        SurfaceFootprint footprint = compiler(sampler).compile(course);

        assertEquals(0, footprint.uncontainedWetCells());
        assertEquals(3, inlet.depth());
        for (int x = 40; x < 100; x++) {
            SurfaceLayerColumn center = column(footprint, x, 0);
            assertNotNull("x=" + x, center);
            HydrologyColumnLayer layer = center.layer();
            assertTrue(layer.channel());
            assertTrue(layer.fluidOwned());
            assertEquals(SEA_LEVEL, layer.fluidHeadY());
            assertTrue("x=" + x + " bed " + layer.bedY(), layer.bedY() <= SEA_LEVEL - inlet.depth());
            assertEquals(sampler.sample(x, 0).mouthBiomeKey(), layer.mouthBiomeKey());
        }
        int shoreZ = -1;
        for (int z = 1; z < 16; z++) {
            SurfaceLayerColumn column = column(footprint, 90, z);
            assertNotNull(column);
            if (!column.layer().channel()) {
                shoreZ = z;
                break;
            }
        }
        assertTrue(shoreZ > 3);
        SurfaceLayerColumn shore = column(footprint, 90, shoreZ);
        assertTrue(shore.layer().shore());
        assertEquals(shore.terrain().naturalHeight() - HydrologyPlannerSettings.Excavation.defaults().maximumDepth(),
                shore.layer().bedY());
        assertEquals(shore.layer().bedY(), shore.layer().fluidHeadY());
        for (SurfaceLayerColumn column : footprint.columns()) {
            if (column.terrain().ocean()) {
                assertTrue(column.layer().oceanApron());
                assertFalse(column.layer().terrainOwned());
            }
        }
    }

    @Test
    public void inletAllowanceSurvivesAFallWithoutReachingOrdinaryUpstreamTerrain() {
        HydrologyPlannerSettings defaults = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Banks banks = defaults.surface().banks();
        HydrologyPlannerSettings.Erosion erosion = banks.erosion();
        HydrologyPlannerSettings.Erosion expandedVolume = new HydrologyPlannerSettings.Erosion(
                erosion.enabled(), erosion.smoothingRadius(), erosion.thalwegFraction(), erosion.blendCurve(),
                erosion.bedNoise(), erosion.style(), erosion.terraceSteps(), erosion.cliffFraction(), erosion.bedProfile(),
                erosion.shoreRise(), erosion.blendBaseWidth(), new HydrologyPlannerSettings.Excavation(8, 16, 512));
        HydrologyPlannerSettings.Banks expandedBanks = new HydrologyPlannerSettings.Banks(
                banks.sink(), banks.blendSlope(), banks.minimumBlendWidth(), banks.maximumBlendWidth(), banks.roughness(),
                banks.roughnessWavelength(), banks.cascadeRun(), banks.waterfallMinimumDrop(), banks.mouthFlareRatio(),
                banks.inlet(), banks.springWidthRatio(), banks.springLength(), banks.exposeCutStrata(), expandedVolume,
                banks.ponds(), banks.channel(), banks.flow());
        HydrologyPlannerSettings.Surface originalSurface = defaults.surface();
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(
                originalSurface.enabled(), originalSurface.sources(), originalSurface.minimumWidth(), originalSurface.maximumWidth(),
                originalSurface.minimumDepth(), originalSurface.maximumDepth(), originalSurface.maximumIncision(),
                originalSurface.shoreWidth(), expandedBanks);
        HydrologyPlannerSettings settings = new HydrologyPlannerSettings(defaults.seaLevel(), defaults.routing(), surface,
                defaults.hydraulics(), defaults.underground(), defaults.outlets(), defaults.geometry(), defaults.deepFluids(),
                defaults.surfacePools(), defaults.widestShoreBiomeWidth(), defaults.seaCaves(), defaults.surfacePolicyBounds());
        int seaLevel = settings.seaLevel();
        int upstreamHead = seaLevel + 22;
        int downstreamHead = seaLevel + 12;
        HydrologyTerrainSampler sampler = (x, z) -> x >= 200
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(x >= 120 && x <= 135 ? 100 : x < 151 ? 90 : 80, 0D, "land");
        HydraulicSegment fall = new HydraulicSegment(2L, 1L, HydrologyFeatureType.WATERFALL,
                upstreamHead, downstreamHead, 6, 3, true, true,
                List.of(new HydrologyPoint(150, upstreamHead, 0), new HydrologyPoint(151, downstreamHead, 0)),
                HydraulicChannelProfile.uniform(6, 3));
        ArrayList<HydrologyPoint> ramp = new ArrayList<>();
        for (int x = 180; x <= 192; x++) {
            ramp.add(new HydrologyPoint(x, downstreamHead - (x - 180), 0));
        }
        RiverCourse course = course(List.of(
                segment(1L, HydrologyFeatureType.SURFACE_POOL, upstreamHead, upstreamHead, points(0, 149, upstreamHead)),
                fall,
                segment(3L, HydrologyFeatureType.SURFACE_POOL, downstreamHead, downstreamHead, points(151, 180, downstreamHead)),
                segment(4L, HydrologyFeatureType.CASCADE, downstreamHead, seaLevel, ramp),
                segment(5L, HydrologyFeatureType.SURFACE_POOL, seaLevel, seaLevel, points(192, 199, seaLevel)),
                segment(6L, HydrologyFeatureType.MOUTH, seaLevel, seaLevel, points(199, 201, seaLevel))));
        SurfaceFootprintCompiler compiler = new SurfaceFootprintCompiler(settings, sampler, CONSTANT_GEOMETRY);
        SurfaceFootprint full = compiler.compile(course);

        assertNull("detail=" + full.rejectionDetail(), full.rejection());
        assertEquals(0, full.uncontainedWetCells());
        for (int x : new int[]{130, 195}) {
            SurfaceLayerColumn channel = column(full, x, 0);
            assertNotNull(channel);
            assertTrue(channel.layer().channel());
            assertEquals(x == 130 ? upstreamHead : seaLevel, channel.layer().fluidHeadY());
            int cut = channel.terrain().naturalHeight() - channel.layer().bedY();
            assertTrue(cut > settings.surface().maximumIncision());
            assertTrue(cut <= settings.surface().banks().inlet().maximumIncision());
        }
        for (SurfaceBounds bounds : List.of(new SurfaceBounds(112, -8, 159, 8), new SurfaceBounds(144, -8, 199, 8))) {
            SurfaceFootprint bounded = compiler.compile(course, bounds);
            assertNull(bounded.rejection());
            assertEquals(full.columns().stream().filter(value -> bounds.contains(value.x(), value.z())).toList(),
                    bounded.columns());
        }
        HydrologyTerrainSampler upstreamRidge = (x, z) -> x == 60
                ? HydrologyTerrainSample.openLand(100, 0D, "land") : sampler.sample(x, z);
        SurfaceFootprint rejected = new SurfaceFootprintCompiler(settings, upstreamRidge, CONSTANT_GEOMETRY).compile(course);
        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, rejected.rejection());
    }

    @Test
    public void standaloneMouthKeepsItsInletBudgetWithinTheDryCourseFraction() {
        int seaLevel = HydrologyPlannerSettings.defaults().seaLevel();
        for (int coast : new int[]{8, 100}) {
            int ridgeX = Math.max(1, coast / 10);
            HydrologyTerrainSampler sampler = (x, z) -> x >= coast
                    ? HydrologyTerrainSample.ocean(50, "ocean")
                    : HydrologyTerrainSample.openLand(x == ridgeX ? 85 : 70, 0D, "land");
            RiverCourse mouth = course(List.of(segment(1L, HydrologyFeatureType.MOUTH,
                    seaLevel, seaLevel, points(0, coast + 1, seaLevel))));

            SurfaceFootprint rejected = compiler(sampler).compile(mouth);

            assertEquals("coast=" + coast, HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED,
                    rejected.rejection());
        }
    }

    @Test
    public void aContainedFallRetainsBothExposedRunsAndWholeCourseStationIndices() {
        HydrologyTerrainSampler sampler = (int x, int z) -> HydrologyTerrainSample.openLand(x < 100 ? 100 : 90, 0D, "land");
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        SurfaceCourseResult built = new SurfaceCourseBuilder(settings.surface(), sampler, CONSTANT_GEOMETRY, SEA_LEVEL)
                .build(7L, 1L, "water", List.of(new HydrologyPoint(0, 100, 0), new HydrologyPoint(99, 100, 0),
                                new HydrologyPoint(100, 90, 0), new HydrologyPoint(200, 90, 0)),
                        SurfaceTerminal.TRIBUTARY, 90, 64);
        assertNull(built.rejection());
        RiverCourse course = course(built.segments());
        HydraulicSegment fall = course.segments().stream().filter(HydraulicSegment::fallingFluid).findFirst().orElseThrow();
        assertEquals(100, fall.start().x());
        SurfaceFootprintCompiler compiler = compiler(sampler);
        SurfaceFootprint full = compiler.compile(course);
        assertNull(full.rejection());
        assertEquals(0, full.uncontainedWetCells());
        SurfaceLayerColumn upstream = column(full, 99, 0);
        SurfaceLayerColumn downstream = column(full, 130, 0);
        assertNotNull(upstream);
        assertNotNull(downstream);
        assertTrue(upstream.layer().channel());
        assertTrue(downstream.layer().channel());
        assertEquals(100, upstream.layer().fluidHeadY());
        assertEquals(90, downstream.layer().fluidHeadY());
        assertEquals(130, downstream.station());
        HydrologySurfaceDropRaster drops = HydrologySurfaceDropRaster.compile(settings, sampler, CONSTANT_GEOMETRY, course);
        assertTrue(drops.connects(100, 0, upstream.layer().fluidHeadY()));
        assertTrue(drops.connects(100, 0, downstream.layer().fluidHeadY()));
        assertTrue(drops.connects(101, 0, downstream.layer().fluidHeadY()));
        SurfaceBounds bounds = new SurfaceBounds(80, -6, 140, 6);
        SurfaceFootprint bounded = compiler.compile(course, bounds);
        assertNull(bounded.rejection());
        assertEquals(full.columns().stream().filter(value -> bounds.contains(value.x(), value.z())).toList(), bounded.columns());
        for (SurfaceLayerColumn value : full.columns()) {
            if (value.layer().terrainOwned()) {
                int fill = value.layer().bedY() - value.terrain().naturalHeight();
                if (value.layer().channel()) {
                    assertTrue(fill <= 0);
                } else {
                    assertTrue(fill <= settings.surface().maximumIncision());
                    assertTrue(fill <= settings.surface().banks().erosion().excavation().maximumDepth());
                }
            }
        }
    }

    @Test
    public void aSteepContinuousSlopeKeepsItsRoundedBanksWithinTheChannelCutLimit() {
        HydrologyTerrainSampler sampler = (int x, int z) -> HydrologyTerrainSample.openLand(300 - x, 0D, "land");
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        SurfaceCourseResult built = new SurfaceCourseBuilder(settings.surface(), sampler, CONSTANT_GEOMETRY, SEA_LEVEL)
                .build(7L, 1L, "water", List.of(new HydrologyPoint(0, 300, 0), new HydrologyPoint(200, 100, 0)),
                        SurfaceTerminal.TRIBUTARY, 100, 64);
        assertNull("detail=" + built.rejectionDetail(), built.rejection());
        SurfaceFootprint footprint = compiler(sampler).compile(course(built.segments()));
        assertNull("detail=" + footprint.rejectionDetail(), footprint.rejection());
        assertEquals(0, footprint.uncontainedWetCells());
        for (SurfaceLayerColumn column : footprint.columns()) {
            if (column.layer().channel()) {
                assertTrue(column.terrain().naturalHeight() - column.layer().bedY()
                        <= settings.surface().maximumIncision());
            }
        }
    }

    @Test
    public void aDiagonalFourteenBlockFallPreservesTheUpstreamBedIncisionLimit() {
        HydrologyTerrainSampler sampler = (int x, int z) -> HydrologyTerrainSample.openLand(
                x + z < 200 ? 100 : 86, 0D, "land");
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        SurfaceCourseResult built = new SurfaceCourseBuilder(settings.surface(), sampler, CONSTANT_GEOMETRY, SEA_LEVEL)
                .build(7L, 1L, "water", List.of(new HydrologyPoint(0, 100, 0), new HydrologyPoint(99, 100, 99),
                                new HydrologyPoint(100, 86, 100), new HydrologyPoint(200, 86, 200)),
                        SurfaceTerminal.TRIBUTARY, 86, 64);
        assertNull(built.rejection());
        RiverCourse course = course(built.segments());
        assertTrue(course.segments().stream().anyMatch(segment -> segment.fallingFluid() && segment.drop() == 14));
        SurfaceFootprint footprint = compiler(sampler).compile(course);
        assertNull(footprint.rejection());
        assertEquals(0, footprint.uncontainedWetCells());
        HydrologySurfaceDropRaster drops = HydrologySurfaceDropRaster.compile(settings, sampler, CONSTANT_GEOMETRY, course);
        assertTrue(drops.connects(100, 100, 100));
        assertFalse(drops.connects(100, 99, 100));
        for (HydrologyColumnSample column : drops.columns()) {
            for (HydrologyColumnLayer layer : column.layers()) {
                if (layer.channel() && layer.fluidOwned()) {
                    int cut = sampler.sample(column.x(), column.z()).naturalHeight() - layer.bedY();
                    assertTrue("cut=" + cut + " at " + column.x() + "," + column.z(),
                            cut <= settings.surface().maximumIncision());
                }
            }
        }
        for (SurfaceLayerColumn column : footprint.columns()) {
            if (column.layer().channel()) {
                assertTrue(column.terrain().naturalHeight() - column.layer().bedY()
                        <= settings.surface().maximumIncision());
            }
        }
    }

    @Test
    public void preparedWindowsRetainColumnsAdmissionAndExcavationAcrossFallsAndMouths() {
        HydrologyTerrainSampler sampler = (int x, int z) -> x >= 190
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand((x < 100 ? 100 : 90) + Math.abs(z) / 3, 0D, "land");
        SurfaceCourseResult built = new SurfaceCourseBuilder(HydrologyPlannerSettings.defaults().surface(), sampler,
                CONSTANT_GEOMETRY, SEA_LEVEL).build(7L, 1L, "water",
                List.of(new HydrologyPoint(0, 100, 0), new HydrologyPoint(99, 100, 0),
                        new HydrologyPoint(100, 90, 0), new HydrologyPoint(180, 90, 0)),
                SurfaceTerminal.TRIBUTARY, 90, 64);
        assertNull(built.rejection());
        RiverCourse falling = course(built.segments());
        RiverCourse mouth = course(List.of(segment(1L, HydrologyFeatureType.MOUTH, SEA_LEVEL, SEA_LEVEL,
                points(180, 200, SEA_LEVEL))));
        RiverCourse curved = course(List.of(segment(1L, HydrologyFeatureType.SURFACE_POOL, 79, 79,
                List.of(new HydrologyPoint(-100, 79, -50), new HydrologyPoint(0, 79, 30),
                        new HydrologyPoint(100, 79, -50)))));
        for (RiverCourse course : List.of(falling, mouth, curved)) {
            SurfaceFootprintCompiler compiler = compiler(sampler);
            SurfaceFootprintCompiler.PreparedCourse prepared = compiler.prepare(course);
            for (int x = -128; x < 256; x += 32) {
                SurfaceBounds bounds = new SurfaceBounds(x, -80, x + 31, 80);
                assertEquals(compiler.compile(course, bounds), compiler.compile(prepared, bounds));
            }
            assertEquals(compiler.compile(course), compiler.compile(prepared, null));
        }
    }

    @Test
    public void subsequentPreparedWindowsDoNotResampleDistantCourseGeometry() {
        AtomicInteger distantSamples = new AtomicInteger();
        HydrologyTerrainSampler sampler = (int x, int z) -> {
            if (x >= 200) {
                distantSamples.incrementAndGet();
            }
            return HydrologyTerrainSample.openLand(90 + Math.abs(z) / 2, 0D, "land");
        };
        RiverCourse course = course(List.of(segment(1L, HydrologyFeatureType.SURFACE_POOL, 79, 79,
                points(0, 400, 79))));
        SurfaceFootprintCompiler compiler = compiler(sampler);
        SurfaceFootprintCompiler.PreparedCourse prepared = compiler.prepare(course);
        assertEquals(0, distantSamples.get());
        compiler.compile(prepared, new SurfaceBounds(0, -16, 31, 16));
        assertTrue(distantSamples.get() > 0);
        distantSamples.set(0);
        SurfaceBounds bounds = new SurfaceBounds(32, -16, 63, 16);
        SurfaceFootprint reused = compiler.compile(prepared, bounds);
        assertEquals(0, distantSamples.get());
        assertEquals(compiler.compile(course, bounds), reused);
        assertTrue(distantSamples.get() > 0);
    }

    private static SurfaceLayerColumn column(SurfaceFootprint footprint, int x, int z) {
        for (SurfaceLayerColumn column : footprint.columns()) {
            if (column.x() == x && column.z() == z) {
                return column;
            }
        }
        return null;
    }

    private static SurfaceFootprintCompiler compiler(HydrologyTerrainSampler sampler) {
        return new SurfaceFootprintCompiler(HydrologyPlannerSettings.defaults(), sampler, CONSTANT_GEOMETRY);
    }

    private static RiverCourse course(List<HydraulicSegment> segments) {
        return new RiverCourse(1L, RiverCourseType.SURFACE, OptionalLong.of(9L), OptionalLong.of(8L), "water", 1, List.of(), segments);
    }

    private static HydraulicSegment segment(long id, HydrologyFeatureType type, int upstream, int downstream, List<HydrologyPoint> centerline) {
        return new HydraulicSegment(id, 1L, type, upstream, downstream, 6, 3, false, false, centerline, HydraulicChannelProfile.uniform(6, 3));
    }

    private static List<HydrologyPoint> points(int fromX, int toX, int y) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (int x = fromX; x <= toX; x++) {
            points.add(new HydrologyPoint(x, y, 0));
        }
        return points;
    }
}
