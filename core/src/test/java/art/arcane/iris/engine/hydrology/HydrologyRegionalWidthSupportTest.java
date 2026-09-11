package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.surface.SurfaceBankSupport;
import art.arcane.iris.engine.hydrology.surface.SurfaceCourseBuilder;
import art.arcane.iris.engine.hydrology.surface.SurfaceCourseResult;
import art.arcane.iris.engine.hydrology.surface.SurfaceFootprint;
import art.arcane.iris.engine.hydrology.surface.SurfaceFootprintCompiler;
import art.arcane.iris.engine.hydrology.surface.SurfaceLayerColumn;
import art.arcane.iris.engine.hydrology.surface.SurfaceTerminal;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalWidthSupportTest {
    private static final HydrologyPlannerSettings SETTINGS = HydrologyRegionalPlannerTest.settings(false, 1);
    private static final List<HydrologyPoint> PATH = List.of(new HydrologyPoint(0, 80, 0), new HydrologyPoint(512, 80, 0));

    @Test
    public void aWiderChannelContainsTheCenterDepressionWithoutRaisingLand() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(
                x >= 128 && x <= 136 && Math.abs(z) <= 1 ? 65 : x >= 256 && x <= 288 ? 86 : 80, 0D, "land");
        HydrologyRegionalHydraulics hydraulics = new HydrologyRegionalHydraulics(SETTINGS);
        assertEquals(80, hydraulics.supportedHead(station(terrain, 80), terrain));
        HydrologyPlanner planner = new HydrologyPlanner(71L, SETTINGS, terrain);
        Method validate = HydrologyRegionalRoute.class.getDeclaredMethod("validateHydraulics",
                HydrologyRegionalRoute.Refinement.class, String.class, boolean.class, HydrologyTerrainSampler.class);
        validate.setAccessible(true);
        HydrologyRegionalRoute.Refinement refined = (HydrologyRegionalRoute.Refinement) validate.invoke(new HydrologyRegionalRoute(planner),
                new HydrologyRegionalRoute.Refinement(PATH, null, null, 0, null), "default", false, terrain);
        assertNull(refined.toString(), refined.rejection());

        HydrologyGeometrySampler geometry = geometry(8);
        SurfaceCourseResult built = build(terrain, geometry);
        assertTrue(built.accepted());
        RiverCourse course = new RiverCourse(91L, RiverCourseType.SURFACE, OptionalLong.of(1L), OptionalLong.of(2L),
                "default", 1, List.of(), built.segments());
        SurfaceFootprint footprint = new SurfaceFootprintCompiler(SETTINGS, terrain, geometry).compile(course);
        assertTrue(footprint.accepted());
        assertEquals(0, footprint.uncontainedWetCells());
        assertTrue(course.hydraulicallyNonRising());
        SurfaceLayerColumn center = null;
        for (SurfaceLayerColumn column : footprint.columns()) {
            assertTrue(column.layer().bedY() <= column.terrain().naturalHeight());
            assertTrue(column.terrain().naturalHeight() - column.layer().bedY() <= SETTINGS.surface().maximumIncision());
            if (column.x() == 132 && column.z() == 0) {
                center = column;
            }
        }
        assertTrue(center != null);
        assertEquals(65, center.layer().bedY());
        assertEquals(80, center.layer().fluidHeadY());

        SurfaceCourseResult narrow = build(terrain, geometry(4));
        assertFalse(narrow.accepted());
        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, narrow.rejection());
        assertEquals(23, narrow.rejectionDetail());
    }

    @Test
    public void anInteriorWidthCanHoldHeadWhenBothAuthoredExtremaFail() {
        HydrologyTerrainSampler terrain = narrowBanks();
        assertEquals(65, bankHead(terrain, 4));
        assertEquals(80, bankHead(terrain, 6));
        assertEquals(65, bankHead(terrain, 8));
        AtomicInteger samples = new AtomicInteger();
        HydrologyTerrainSampler bounded = (x, z) -> {
            assertTrue("A supported interior width must stop wider probes", Math.abs(z) <= 7);
            samples.incrementAndGet();
            return terrain.sample(x, z);
        };

        assertEquals(80, new HydrologyRegionalHydraulics(SETTINGS).supportedHead(station(terrain, 80), bounded));
        assertTrue(samples.get() > 0);
    }

    @Test
    public void fractionalLocalWidthIsCheckedBeforeUnneededWiderCandidates() {
        HydrologyTerrainSampler source = narrowBanks();
        HydrologyTerrainSampler terrain = (x, z) -> withWidthMultiplier(source.sample(x, z), 0.8125D);
        assertEquals(80, bankHead(terrain, 6.5D));
        HydrologyTerrainSampler bounded = (x, z) -> {
            assertTrue("The local width6.5 already supports the incoming head", Math.abs(z) <= 6);
            return terrain.sample(x, z);
        };

        assertEquals(80, new HydrologyRegionalHydraulics(SETTINGS).supportedHead(station(terrain, 80), bounded));
    }

    @Test
    public void minimumWidthSupportStaysLazyAndCannotRaiseIncomingHead() {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(
                x >= 128 && x <= 136 && z == 0 ? 65 : 80, 0D, "land");
        AtomicInteger expectedSamples = new AtomicInteger();
        bankHead((x, z) -> {
            expectedSamples.incrementAndGet();
            return terrain.sample(x, z);
        }, 4);
        AtomicInteger actualSamples = new AtomicInteger();
        HydrologyTerrainSampler counted = (x, z) -> {
            actualSamples.incrementAndGet();
            return terrain.sample(x, z);
        };

        assertEquals(78, new HydrologyRegionalHydraulics(SETTINGS).supportedHead(station(terrain, 78), counted));
        assertEquals(expectedSamples.get(), actualSamples.get());
    }

    @Test
    public void incompleteWiderPerimetersCannotCertifyAHighHead() {
        HydrologyTerrainSampler terrain = narrowBanks();
        HydrologyTerrainSampler incomplete = (x, z) -> x == 133 ? null : terrain.sample(x, z);

        assertEquals(65, new HydrologyRegionalHydraulics(SETTINGS).supportedHead(station(terrain, 80), incomplete));
    }

    @Test
    public void additionalSamplingAndRadiusStayBoundedWhenWiderPerimetersAreIncomplete() {
        HydrologyPlannerSettings settings = wideSettings();
        HydrologyTerrainSample ground = withWidthMultiplier(HydrologyTerrainSample.openLand(65, 0D, "land"), 0.75D);
        HydrologyTerrainSample bank = HydrologyTerrainSample.openLand(80, 0D, "land");
        HydrologyTerrainSampler incomplete = (x, z) -> x == 133 ? null : x == 132 && z == 0 ? ground : bank;
        AtomicInteger minimumSamples = new AtomicInteger();
        HydrologyTerrainSampler minimum = (x, z) -> {
            minimumSamples.incrementAndGet();
            return incomplete.sample(x, z);
        };
        SurfaceBankSupport support = new SurfaceBankSupport(settings.surface(), settings.seaLevel());
        SurfaceBankSupport.Station station = new SurfaceBankSupport.Station(132, 0, 1D, 0D, 2D);
        SurfaceBankSupport.CrossSection cross = support.crossSection(minimum, station, true);
        assertFalse(support.perimeter(minimum, station, cross.minimum()).complete());
        AtomicInteger samples = new AtomicInteger();
        HydrologyTerrainSampler terrain = (x, z) -> {
            assertTrue(Math.abs(x - 132) <= 96 && Math.abs(z) <= 96);
            samples.incrementAndGet();
            return incomplete.sample(x, z);
        };

        assertEquals(65, new HydrologyRegionalHydraulics(settings).supportedHead(
                new HydrologyRegionalHydraulics.HeadStation(132, 0, 1D, 0D, ground, 80), terrain));
        assertEquals(minimumSamples.get() + 2048, samples.get());
    }

    private static HydrologyTerrainSampler narrowBanks() {
        return (x, z) -> HydrologyTerrainSample.openLand(
                x >= 128 && x <= 136 && Math.abs(z) <= 1 || Math.abs(z) >= 7 ? 65 : 80, 0D, "land");
    }

    private static int bankHead(HydrologyTerrainSampler terrain, double width) {
        SurfaceBankSupport support = new SurfaceBankSupport(SETTINGS.surface(), SETTINGS.seaLevel());
        SurfaceBankSupport.Station station = new SurfaceBankSupport.Station(132, 0, 1D, 0D, width / 2D);
        SurfaceBankSupport.CrossSection cross = support.crossSection(terrain, station, true);
        assertFalse(cross.blocked());
        SurfaceBankSupport.Perimeter perimeter = support.perimeter(terrain, station, cross.minimum());
        assertTrue(perimeter.complete());
        return perimeter.minimum();
    }

    private static HydrologyRegionalHydraulics.HeadStation station(HydrologyTerrainSampler terrain, int incoming) {
        return new HydrologyRegionalHydraulics.HeadStation(132, 0, 1D, 0D, terrain.sample(132, 0), incoming);
    }

    private static HydrologyGeometrySampler geometry(int width) {
        return request -> switch (request.field()) {
            case SURFACE_WIDTH -> width;
            case SURFACE_DEPTH -> 2;
            default -> request.minimum();
        };
    }

    private static SurfaceCourseResult build(HydrologyTerrainSampler terrain, HydrologyGeometrySampler geometry) {
        return new SurfaceCourseBuilder(SETTINGS.surface(), terrain, geometry, SETTINGS.seaLevel())
                .build(71L, 91L, "default", PATH, SurfaceTerminal.SINKHOLE, 40, 256);
    }

    private static HydrologyTerrainSample withWidthMultiplier(HydrologyTerrainSample source, double multiplier) {
        return new HydrologyTerrainSample(source.naturalHeight(), source.slope(), source.ocean(), source.caveAvailable(),
                source.caveFloorY(), source.caveFluidY(), source.transitAllowed(), source.outletAllowed(), source.surfaceSourceAllowed(),
                source.surfaceSourceRequired(), source.undergroundSourceAllowed(), source.undergroundSourceRequired(), source.routingCost(),
                source.surfaceSourceWeight(), source.undergroundSourceWeight(), multiplier, source.depthMultiplier(), source.incisionMultiplier(),
                source.routingMultiplier(), source.bankMultiplier(), source.parentBiomeKey(), source.surfaceBiomeKey(), source.mouthBiomeKey(),
                source.shoreBiomeKey(), source.bankBiomeKey(), source.floodedCaveBiomeKey(), source.preferredProfileKeys(), source.surfacePoolKeys(),
                source.shoreBiomeWidth(), source.confinesKey(), source.shoreWidth(), source.erosion(), source.surfacePolicy());
    }

    private static HydrologyPlannerSettings wideSettings() {
        HydrologyPlannerSettings.Surface surface = SETTINGS.surface();
        return new HydrologyPlannerSettings(SETTINGS.seaLevel(), SETTINGS.routing(),
                new HydrologyPlannerSettings.Surface(surface.enabled(), surface.sources(), surface.minimumWidth(), 256,
                        surface.minimumDepth(), surface.maximumDepth(), surface.maximumIncision(), surface.shoreWidth(), surface.banks()),
                SETTINGS.hydraulics(), SETTINGS.underground(), SETTINGS.outlets(), SETTINGS.geometry(), SETTINGS.deepFluids(), SETTINGS.surfacePools(),
                SETTINGS.widestShoreBiomeWidth(), SETTINGS.seaCaves(), SETTINGS.surfacePolicyBounds());
    }
}
