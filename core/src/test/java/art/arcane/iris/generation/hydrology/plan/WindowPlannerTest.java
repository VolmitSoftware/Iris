package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyTier;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.raster.TongueGuard;
import art.arcane.iris.generation.hydrology.raster.WaterHeads;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.CupSet;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.PondPlanner;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachShaper;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import art.arcane.iris.generation.hydrology.water.ShapedNetwork;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class WindowPlannerTest {
    private static final long SEED = 1337L;
    private static final int SEA_LEVEL = SyntheticTerrain.SEA_LEVEL;
    private static final WindowKey ORIGIN = new WindowKey(0, 0);
    private static final WindowKey LAND = new WindowKey(4, 4);
    private static final int SEAM_DISTANCE = 256;
    private static final double COAST_DROP = 20D;
    private static final double CLIFF_COAST_X = 300D;
    private static final double HOLLOW_X = 600D;
    private static final double HOLLOW_Z = 512D;
    private static final double HOLLOW_RADIUS = 100D;
    private static final double HOLLOW_DEPTH = 64D;
    private static final double PIT_X = 900D;
    private static final double PIT_Z = 512D;
    private static final double PIT_RADIUS = 50D;
    private static final double PIT_DEPTH = 30D;
    private static final int DEEP_CUT = 12;
    private static final int SLIVER_REACH = 32;
    private static final int RIM_CUT = 24;
    private static final double VALLEY_HALF_WIDTH = 200D;
    private static final double VALLEY_SEA_INSET = 320D;
    private static final double LINE_STREAM_CATCHMENT = 10000D;
    private static final double LINE_HOLLOW_RADIUS = 240D;
    private static final double LINE_HOLLOW_DEPTH = 90D;
    private static final double HOLLOW_EDGE = 8D;
    private static final double CRATER_X = 512D;
    private static final double CRATER_Z = 512D;
    private static final int CRATER_SPILL = 75;
    private static final double CRATER_RIM = 120D;
    private static final long DELTA_SEED = 5L;

    @Test
    public void zeroDensityRemovesLocalChannelsAndPonds() {
        HydrologySettings settings = settings(new IrisHydrology().setDensity(0D));
        WindowPlan plan = planner(settings, SyntheticTerrain.trunkValley()).plan(LAND, noRegions(LAND));
        assertTrue(plan.reaches().isEmpty());
        assertTrue(plan.ponds().isEmpty());
    }

    @Test
    public void shrinkingPondReplansUntilItsChangedOutletStopsLowering() {
        HydrologySettings settings = settings(new IrisHydrology());
        SyntheticTerrain terrain = SyntheticTerrain.flat(100D).withPit(64D, 64D, 20D, 20D);
        int width = 41;
        long[] bits = new long[(width * width + 63) >>> 6];
        for (int z = -20; z <= 20; z++) {
            for (int x = -20; x <= 20; x++) {
                if (x * x + z * z < 400) {
                    int cell = (z + 20) * width + x + 20;
                    bits[cell >>> 6] |= 1L << cell;
                }
            }
        }
        Pond initial = new Pond(19L, PondKind.NATURAL, 100, 80, 84, 64, 100f,
                new PondMask(44, 44, 1, width, width, bits), 0L, 0, "water", 64, 64);
        PondPlanner ponds = spy(new PondPlanner(settings));
        doReturn(CupSet.EMPTY).when(ponds).subtractCups(any(), any(), any(), any());
        doReturn(List.of(initial)).when(ponds).ponds(any(), any(), any(), any(), any(), any());
        ReachShaper shaper = mock(ReachShaper.class);
        AtomicInteger passes = new AtomicInteger();
        doAnswer(call -> {
            List<Pond> water = call.getArgument(6);
            Pond pond = single(water);
            passes.incrementAndGet();
            int head = pond.level() > 96 ? 96 : 94;
            Reach outlet = new Reach(20L, settings.tiers().get(0), 0L, new float[]{64f, 64f}, new float[]{32f, 96f},
                    new int[]{head, head}, new float[]{2f, 2f}, new float[]{2f, 2f}, new ReachStyle[]{ReachStyle.RUN, ReachStyle.RUN}, 1000f);
            return new ShapedNetwork(List.of(outlet), List.of(pond.withOutletReachId(outlet.id())));
        }).when(shaper).shape(any(), any(), any(), any(), any(), any(), any(), any(), anyLong());
        WindowPlanner planner = new WindowPlanner(settings,
                new HeightLattice(terrain, WindowPlanner.FINE_CELL, false, 1 << 18), ponds, shaper);

        WindowComputation settled = planner.compute(ORIGIN, noRegions(ORIGIN));

        Pond pond = single(settled.allPonds());
        assertEquals(3, passes.get());
        assertEquals(94, pond.level());
        assertEquals(20L, pond.outletReachId());
        assertTrue(initial.mask().contains(83, 64));
        assertFalse(pond.mask().contains(83, 64));
        assertTrue(pond.mask().contains(64, 64));
        assertEquals(94, settled.allReaches().get(0).grade()[0]);
    }

    @Test
    public void planningStopsTonguesThatWouldRaiseTheBedOverMaxFill() {
        int stopped = 0;
        SyntheticTerrain terrain = SyntheticTerrain.southSlope(300D, 5D);
        for (long seed = 1L; seed <= 6L; seed++) {
            HydrologySettings settings = HydrologySettings.compile(new IrisHydrology(), seed, SyntheticTerrain.SEA_LEVEL, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
            Reach river = new Reach(30L, settings.tiers().get(0), 0L, new float[]{64f, 64f}, new float[]{20f, 40f}, new int[]{200, 160},
                    new float[]{8f, 8f}, new float[]{2f, 2f}, new ReachStyle[]{ReachStyle.RUN, ReachStyle.WATERFALL}, 1000f);
            ReachShaper shaper = mock(ReachShaper.class);
            doAnswer(call -> new ShapedNetwork(List.of(river), List.of())).when(shaper).shape(any(), any(), any(), any(), any(), any(), any(), any(), anyLong());
            WindowPlanner planner = new WindowPlanner(settings, new HeightLattice(terrain, WindowPlanner.FINE_CELL, false, 1 << 18), new PondPlanner(settings), shaper);
            Reach planned = planner.compute(ORIGIN, noRegions(ORIGIN)).allReaches().get(0);
            ShapedNetwork expected = TongueGuard.guard(new ShapedNetwork(List.of(river), List.of()), (x, z) -> 300D - 5D * z, seed, SyntheticTerrain.SEA_LEVEL,
                    settings.deformation().maxFill());
            assertEquals("seed " + seed, expected.reaches().get(0), planned);
            stopped += planned.straightFalls().length;
        }
        assertTrue("stopped tongues " + stopped, stopped > 0);
    }

    @Test
    public void burnedTrunkCapturesLocalDrainage() {
        HydrologySettings settings = settings(new IrisHydrology());
        SyntheticTerrain terrain = SyntheticTerrain.trunkValley();
        WindowKey key = new WindowKey(6, 4);
        List<RegionPlan> regions = regionPlans(settings, terrain, key, new HashMap<>());
        List<Reach> trunks = new ArrayList<>();
        for (RegionPlan region : regions) {
            trunks.addAll(region.majorReaches());
        }
        Set<Float> trunkCatchments = new HashSet<>();
        for (Reach trunk : trunks) {
            trunkCatchments.add(trunk.catchment());
        }
        WindowPlanner planner = planner(settings, terrain);
        WindowComputation burned = planner.compute(key, regions);
        HydrologyTier major = settings.majorTier();
        List<Reach> majors = tier(burned.allReaches(), major);
        for (Reach reach : majors) {
            assertTrue(reach + " carries an L0 catchment", trunkCatchments.contains(reach.catchment()));
        }
        assertTrue("L0 trunk points in the window", trunkPointsFollowed(key, trunks, majors) >= 4);
        WindowComputation dry = planner(settings, terrain.withRainfall((x, z) -> 0D)).compute(key, regions);
        assertEquals("a rainless window keeps only the trunk", dry.allReaches(), tier(dry.allReaches(), major));
        assertTrue("rainless trunk points in the window", trunkPointsFollowed(key, trunks, dry.allReaches()) >= 4);
        Map<Long, Reach> byId = byId(burned.allReaches());
        int tributaries = 0;
        for (Reach reach : burned.allReaches()) {
            Reach downstream = byId.get(reach.downstreamId());
            if (!reach.tier().equals(major) && downstream != null && downstream.tier().equals(major)) {
                tributaries++;
            }
        }
        assertTrue("tributaries joining the trunk " + tributaries, tributaries >= 3);
        float entry = (key.wx() + 2) * WindowKey.SIZE - 96f;
        assertTrue("burned trunk is major at the upstream edge", maxX(majors) >= entry);
        assertTrue("local drainage alone is not major at the upstream edge", maxX(tier(planner.compute(key, noRegions(key)).allReaches(), major)) < entry);
    }

    @Test
    public void trunkFollowsValleyFloor() {
        HydrologySettings settings = settings(new IrisHydrology());
        SyntheticTerrain terrain = SyntheticTerrain.trunkValley();
        WindowKey key = new WindowKey(6, 4);
        WindowComputation computation = planner(settings, terrain).compute(key, regionPlans(settings, terrain, key, new HashMap<>()));
        double floor = 3292.5D;
        int checked = 0;
        for (Reach reach : tier(computation.allReaches(), settings.majorTier())) {
            for (int point = 0; point < reach.xs().length; point++) {
                float x = reach.xs()[point];
                float z = reach.zs()[point];
                if (z >= 1900f && z <= 2150f && x >= 3240f && x <= 3380f) {
                    assertEquals("tributary trunk at z=" + z, floor, x, 2 * WindowPlanner.CELL);
                    checked++;
                }
            }
        }
        assertTrue("tributary trunk points " + checked, checked >= 50);
    }

    @Test
    public void blockedStreamsInterruptTrunkAtBothLevels() {
        HydrologySettings settings = settings(new IrisHydrology());
        float bandWest = 3264f;
        float bandEast = 3392f;
        SyntheticTerrain terrain = SyntheticTerrain.trunkValley().withStreamsBlocked((x, z) -> x >= bandWest && x < bandEast);
        WindowKey key = new WindowKey(6, 4);
        List<RegionPlan> regions = regionPlans(settings, terrain, key, new HashMap<>());
        List<Reach> trunks = new ArrayList<>();
        for (RegionPlan region : regions) {
            trunks.addAll(region.majorReaches());
        }
        assertFalse(trunks.isEmpty());
        for (Reach trunk : trunks) {
            for (float x : trunk.xs()) {
                assertTrue(trunk + " stays out of the blocked band", x < bandWest || x >= bandEast);
            }
        }
        assertTrue("L0 trunk west of the band", minX(trunks) < bandWest);
        assertTrue("L0 trunk east of the band", maxX(trunks) >= bandEast);
        WindowComputation computation = planner(settings, terrain).compute(key, regions);
        float margin = TrunkRouter.CORRIDOR - WindowPlanner.CELL;
        for (Reach reach : computation.allReaches()) {
            for (float x : reach.xs()) {
                assertTrue(reach + " stays out of the blocked band", x < bandWest + margin || x > bandEast - margin);
            }
        }
        List<Reach> majors = tier(computation.allReaches(), settings.majorTier());
        assertTrue("L1 trunk west of the band", minX(majors) < bandWest);
        assertTrue("L1 trunk east of the band", maxX(majors) >= bandEast);
    }

    @Test
    public void channelsStartAtStreamThreshold() {
        for (double minCatchment : new double[]{1500D, 3000D}) {
            IrisHydrology config = new IrisHydrology();
            config.getTiers().get(0).setMinCatchment(minCatchment);
            config.getDrainage().setWetnessThinning(false);
            config.getPonds().getNatural().setEnabled(false);
            config.getPonds().getSeeded().setEnabled(false);
            HydrologySettings settings = settings(config);
            float rainfall = 0.5f;
            SyntheticTerrain terrain = SyntheticTerrain.southSlope(150D, 0.05D)
                    .withRainfall((x, z) -> x >= 256D && x < 256D + WindowPlanner.CELL ? rainfall : 0D);
            WindowComputation computation = planner(settings, terrain).compute(ORIGIN, noRegions(ORIGIN));
            List<Reach> heads = heads(computation.allReaches());
            assertEquals("heads " + heads, 1, heads.size());
            Reach head = heads.get(0);
            float perCell = rainfall * WindowPlanner.CELL * WindowPlanner.CELL;
            int headRow = (int) Math.ceil(minCatchment / perCell) - 1;
            assertTrue(perCell * (headRow + 1) >= minCatchment && perCell * headRow < minCatchment);
            assertEquals(256f, head.xs()[0], 0f);
            assertEquals("threshold " + minCatchment, -WindowKey.SIZE + headRow * WindowPlanner.CELL, head.zs()[0], 0f);
            for (Reach reach : computation.allReaches()) {
                assertTrue(reach + " starts below the head", reach.zs()[0] >= head.zs()[0]);
            }
        }
    }

    @Test
    public void gentleSlopeNeedsHeadRelief() {
        double[][] cases = {{0.02D, 1D, 1D}, {0.01D, 1D, 0D}, {0.01D, 0.5D, 1D}};
        for (double[] scenario : cases) {
            IrisHydrology config = new IrisHydrology();
            config.getDrainage().setWetnessThinning(false);
            config.getDrainage().setHeadRelief(scenario[1]);
            config.getPonds().getNatural().setEnabled(false);
            config.getPonds().getSeeded().setEnabled(false);
            SyntheticTerrain terrain = SyntheticTerrain.southSlope(150D, scenario[0])
                    .withRainfall((x, z) -> x >= 256D && x < 256D + WindowPlanner.CELL ? 1D : 0D);
            WindowComputation computation = planner(settings(config), terrain).compute(ORIGIN, noRegions(ORIGIN));
            assertEquals("gradient " + scenario[0] + " relief " + scenario[1], (int) scenario[2], heads(computation.allReaches()).size());
        }
    }

    @Test
    public void wetnessThinningDropsHeadsNotTrunkAccumulation() {
        SyntheticTerrain terrain = SyntheticTerrain.openFractal(SEED).withRainfall((x, z) -> 0.3D);
        IrisHydrology thinning = new IrisHydrology();
        thinning.getPonds().getSeeded().setAlongChannels(false);
        IrisHydrology full = new IrisHydrology();
        full.getPonds().getSeeded().setAlongChannels(false);
        full.getDrainage().setWetnessThinning(false);
        HydrologySettings thinned = settings(thinning);
        List<RegionPlan> regions = regionPlans(thinned, terrain, LAND, new HashMap<>());
        WindowComputation on = planner(thinned, terrain).compute(LAND, regions);
        WindowComputation off = planner(settings(full), terrain).compute(LAND, regions);
        int headsOn = heads(on.allReaches()).size();
        int headsOff = heads(off.allReaches()).size();
        assertTrue("heads with thinning " + headsOn + " without " + headsOff, headsOn > 0 && headsOn < headsOff);
        Map<String, Set<Float>> catchmentsByEnd = new HashMap<>();
        for (Reach reach : off.allReaches()) {
            catchmentsByEnd.computeIfAbsent(end(reach), ignored -> new HashSet<>()).add(reach.catchment());
        }
        int compared = 0;
        for (Reach reach : on.allReaches()) {
            int last = reach.xs().length - 1;
            if (onAreaBorder(LAND, reach.xs()[last], reach.zs()[last])) {
                continue;
            }
            Set<Float> unthinned = catchmentsByEnd.get(end(reach));
            assertNotNull(reach + " ends where an unthinned reach ends", unthinned);
            assertTrue(reach + " keeps its catchment " + unthinned, unthinned.contains(reach.catchment()));
            compared++;
        }
        assertTrue("compared reaches " + compared, compared > 0);
    }

    @Test
    public void oversizedBasinsAreBreached() {
        int centre = 256;
        for (boolean deep : new boolean[]{true, false}) {
            SyntheticTerrain terrain = SyntheticTerrain.slopedPit(centre, centre, 96D, deep ? 40D : 12D);
            for (boolean capped : new boolean[]{true, false}) {
                IrisHydrology config = new IrisHydrology();
                config.getPonds().getSeeded().setEnabled(false);
                if (deep && !capped) {
                    config.getDrainage().setBreachDepth(64);
                }
                if (!deep && capped) {
                    config.getPonds().getNatural().setMaxArea(8192);
                }
                WindowComputation computation = planner(settings(config), terrain).compute(ORIGIN, noRegions(ORIGIN));
                assertEquals((deep ? "deep" : "wide") + " basin capped=" + capped + " ponds " + computation.allPonds(),
                        !capped, inPond(computation.allPonds(), centre, centre));
            }
        }
    }

    @Test
    public void regionLakesFeedReachesButStayInRegionPlans() {
        HydrologySettings settings = settings(new IrisHydrology());
        SyntheticTerrain terrain = SyntheticTerrain.crater(2304D, 2304D, 150, 700D);
        WindowKey key = WindowKey.containing(2304, 2304);
        List<RegionPlan> regions = regionPlans(settings, terrain, key, new HashMap<>());
        List<Pond> lakes = new ArrayList<>();
        List<Reach> trunks = new ArrayList<>();
        for (RegionPlan region : regions) {
            lakes.addAll(region.lakes());
            trunks.addAll(region.majorReaches());
        }
        assertEquals("region lakes " + lakes, 1, lakes.size());
        Pond lake = lakes.get(0);
        int leaving = 0;
        for (Reach trunk : trunks) {
            leaving += nearLake(lake, trunk.xs()[0], trunk.zs()[0]) ? 1 : 0;
        }
        assertTrue("L0 major rivers rising in the lake " + leaving, leaving >= 1);
        WindowComputation computation = planner(settings, terrain).compute(key, regions);
        for (Pond pond : computation.allPonds()) {
            assertTrue(pond + " is a window pond", pond.kind() != PondKind.LAKE);
            assertFalse(pond + " overlaps the lake", pond.mask().overlaps(lake.mask()));
        }
        assertNoInflowEndsBelowItsLake(computation.allReaches(), lakes);
        int inflows = 0;
        int outlets = 0;
        for (Reach reach : computation.allReaches()) {
            int last = reach.xs().length - 1;
            inflows += reach.downstreamId() == 0L && inPond(List.of(lake), reach.xs()[last], reach.zs()[last]) ? 1 : 0;
            boolean outlet = reach.style()[0] == ReachStyle.POND_OUTLET && reach.tier().equals(settings.majorTier());
            outlets += outlet && nearLake(lake, reach.xs()[0], reach.zs()[0]) ? 1 : 0;
        }
        assertTrue("reaches ending in the lake " + inflows, inflows >= 3);
        assertTrue("major lake outlet reaches " + outlets, outlets >= 1);
    }

    @Test
    public void windowSeesEveryLakeInItsComputeArea() {
        Map<Pond, RegionKey> owners = new HashMap<>();
        HydrologySettings fractal = HydrologySettings.compile(new IrisHydrology(), 2L, SEA_LEVEL, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
        RegionPlanner fractalPlanner = regionPlanner(fractal, SyntheticTerrain.openFractal(2L));
        for (int rz = -2; rz <= 0; rz++) {
            for (int rx = 0; rx <= 2; rx++) {
                RegionKey key = new RegionKey(rx, rz);
                for (Pond lake : fractalPlanner.plan(key).lakes()) {
                    owners.put(lake, key);
                }
            }
        }
        HydrologySettings settings = settings(withoutMajorRivers());
        SyntheticTerrain valley = SyntheticTerrain.valleyLake().withPit(5300D, 2248D, 70D, 10D);
        RegionKey owner = new RegionKey(0, 0);
        Pond lake = single(regionPlanner(settings, valley).plan(owner).lakes());
        owners.put(lake, owner);
        int checked = 0;
        int beyondReaches = 0;
        for (Map.Entry<Pond, RegionKey> entry : owners.entrySet()) {
            PondMask mask = entry.getKey().mask();
            int minWx = Math.floorDiv(mask.originX(), WindowKey.SIZE) - 2;
            int maxWx = Math.floorDiv(mask.originX() + mask.width() * mask.step(), WindowKey.SIZE) + 2;
            int minWz = Math.floorDiv(mask.originZ(), WindowKey.SIZE) - 2;
            int maxWz = Math.floorDiv(mask.originZ() + mask.depth() * mask.step(), WindowKey.SIZE) + 2;
            for (int wz = minWz; wz <= maxWz; wz++) {
                for (int wx = minWx; wx <= maxWx; wx++) {
                    WindowKey window = new WindowKey(wx, wz);
                    if (maskTouchesComputeArea(mask, window)) {
                        assertTrue(entry.getKey() + " owned by " + entry.getValue() + " reaches " + window,
                                WindowPlanner.lakeRegions(window).contains(entry.getValue()));
                        beyondReaches += WindowPlanner.regions(window).contains(entry.getValue()) ? 0 : 1;
                        checked++;
                    }
                }
            }
        }
        assertTrue("window and lake pairs " + checked, checked >= 50);
        assertTrue("pairs outside the reach regions " + beyondReaches, beyondReaches >= 1);
        WindowKey key = new WindowKey(11, 4);
        assertFalse(WindowPlanner.regions(key).contains(owner));
        for (Pond pond : planner(settings, valley).compute(key, regionPlans(settings, valley, key, new HashMap<>())).allPonds()) {
            assertFalse(pond + " lies in the lake", pond.mask().overlaps(lake.mask()));
        }
        assertTrue("without the lake the pit holds a pond", inPond(planner(settings, valley).compute(key, noRegions(key)).allPonds(), 5300f, 2248f));
    }

    @Test
    public void lakeExemptsBasinFromBreaching() {
        IrisHydrology config = withoutMajorRivers();
        config.getPonds().getSeeded().setEnabled(false);
        HydrologySettings settings = settings(config);
        float pitX = 2960f;
        float pitZ = 2304f;
        SyntheticTerrain terrain = SyntheticTerrain.crater(2304D, 2304D, 150, 700D).withPit(pitX, pitZ, 32D, 6D);
        WindowKey key = WindowKey.containing(2304, 2304);
        List<RegionPlan> regions = regionPlans(settings, terrain, key, new HashMap<>());
        Pond lake = single(regions.stream().flatMap(region -> region.lakes().stream()).toList());
        assertFalse("the notch pit lies outside the lake", lake.mask().grownContains((int) pitX, (int) pitZ));
        assertTrue("the unbreached crater leaves the notch pit standing", inPond(planner(settings, terrain).compute(key, regions).allPonds(), pitX, pitZ));
        assertFalse("breaching the crater drains the notch pit", inPond(planner(settings, terrain).compute(key, noRegions(key)).allPonds(), pitX, pitZ));
    }

    @Test
    public void chainedCupsFollowPreliminaryChannels() {
        HydrologySettings settings = settings(new IrisHydrology());
        List<RegionPlan> regions = regionPlans(settings, SyntheticTerrain.openFractal(SEED), LAND, new HashMap<>());
        assertTrue("chained cups", count(planner(settings, SyntheticTerrain.openFractal(SEED)).compute(LAND, regions).allPonds(), PondKind.CHAINED) > 0);
        IrisHydrology unchained = new IrisHydrology();
        unchained.getPonds().getSeeded().setAlongChannels(false);
        HydrologySettings plain = settings(unchained);
        assertEquals(0, count(planner(plain, SyntheticTerrain.openFractal(SEED)).compute(LAND, regions).allPonds(), PondKind.CHAINED));
    }

    @Test
    public void allOceanWindowPlansEmpty() {
        HydrologySettings settings = settings(new IrisHydrology());
        SyntheticTerrain terrain = SyntheticTerrain.flat(SEA_LEVEL - 20D);
        List<RegionPlan> regions = regionPlans(settings, terrain, ORIGIN, new HashMap<>());
        for (RegionPlan region : regions) {
            assertTrue(region.majorReaches().isEmpty() && region.lakes().isEmpty());
        }
        WindowComputation computation = planner(settings, terrain).compute(ORIGIN, regions);
        assertTrue(computation.owned().reaches().isEmpty());
        assertTrue(computation.owned().ponds().isEmpty());
        assertTrue(computation.allReaches().isEmpty());
        assertTrue(computation.allPonds().isEmpty());
    }

    @Test
    public void flatPlateauSproutsNoParallelChannels() {
        HydrologySettings settings = settings(new IrisHydrology());
        SyntheticTerrain terrain = SyntheticTerrain.flat(100D);
        assertTrue(planner(settings, terrain).compute(ORIGIN, noRegions(ORIGIN)).allReaches().isEmpty());
        WindowComputation computation = planner(settings, terrain).compute(ORIGIN, regionPlans(settings, terrain, ORIGIN, new HashMap<>()));
        assertEquals("only burned trunks remain", computation.allReaches(), tier(computation.allReaches(), settings.majorTier()));
    }

    @Test
    public void flatPlateauDrainsWithoutStrandedCells() {
        IrisHydrology config = new IrisHydrology();
        config.getDrainage().setHeadRelief(0D);
        HydrologySettings settings = settings(config);
        SyntheticTerrain terrain = SyntheticTerrain.flat(100D);
        WindowComputation computation = planner(settings, terrain).compute(ORIGIN, regionPlans(settings, terrain, ORIGIN, new HashMap<>()));
        Map<Long, Reach> byId = byId(computation.allReaches());
        for (Reach reach : computation.allReaches()) {
            int last = reach.xs().length - 1;
            float x = reach.xs()[last];
            float z = reach.zs()[last];
            boolean linked = byId.containsKey(reach.downstreamId());
            boolean border = onAreaBorder(ORIGIN, x, z);
            boolean pooled = inPond(computation.allPonds(), x, z);
            assertTrue(reach + " drains to an outlet", linked || border || pooled || reach.style()[last] == ReachStyle.MOUTH);
        }
        for (Pond pond : computation.allPonds()) {
            assertTrue(pond + " is a cup on level ground", pond.kind() == PondKind.SEEDED || pond.kind() == PondKind.CHAINED);
        }
    }

    @Test
    public void seamAgreementOnFractalTerrain() {
        HydrologySettings settings = settings(new IrisHydrology());
        int wx0 = LAND.wx();
        int wz0 = LAND.wz();
        double boxSize = 2 * WindowKey.SIZE;
        Map<RegionKey, RegionPlan> regionCache = new HashMap<>();
        Map<WindowKey, WindowComputation> computations = new HashMap<>();
        for (int wz = wz0; wz <= wz0 + 1; wz++) {
            for (int wx = wx0; wx <= wx0 + 1; wx++) {
                WindowKey key = new WindowKey(wx, wz);
                SyntheticTerrain terrain = SyntheticTerrain.boxedFractal(SEED, wx0 * WindowKey.SIZE - 2D, wz0 * WindowKey.SIZE - 2D, boxSize);
                computations.put(key, planner(settings, terrain).compute(key, regionPlans(settings, terrain, key, regionCache)));
            }
        }
        int reaches = 0;
        int ponds = 0;
        int trunks = 0;
        WindowKey[][] pairs = {
                {new WindowKey(wx0, wz0), new WindowKey(wx0 + 1, wz0)},
                {new WindowKey(wx0, wz0 + 1), new WindowKey(wx0 + 1, wz0 + 1)},
                {new WindowKey(wx0, wz0), new WindowKey(wx0, wz0 + 1)},
                {new WindowKey(wx0 + 1, wz0), new WindowKey(wx0 + 1, wz0 + 1)}
        };
        for (WindowKey[] pair : pairs) {
            for (int side = 0; side < 2; side++) {
                WindowKey owner = pair[side];
                WindowKey other = pair[1 - side];
                Set<Reach> ownedReaches = nearSeamReaches(owner, other, computations.get(owner).owned().reaches());
                Set<Pond> ownedPonds = nearSeamPonds(owner, other, computations.get(owner).owned().ponds());
                assertEquals(owner + " reaches seen from " + other, ownedReaches, nearSeamReaches(owner, other, computations.get(other).allReaches()));
                assertEquals(owner + " ponds seen from " + other, ownedPonds, nearSeamPonds(owner, other, computations.get(other).allPonds()));
                reaches += ownedReaches.size();
                ponds += ownedPonds.size();
                trunks += tier(new ArrayList<>(ownedReaches), settings.majorTier()).size();
            }
        }
        assertTrue("near-seam reaches " + reaches, reaches >= 8);
        assertTrue("near-seam trunk reaches " + trunks, trunks >= 1);
        assertTrue("near-seam ponds " + ponds, ponds >= 1);
    }

    @Test
    public void noInflowEndsBelowTheLakeItEnters() {
        HydrologySettings settings = settings(new IrisHydrology());
        int lakeInflows = 0;
        for (long seed : new long[]{3L, 11L, 17L, 23L}) {
            SyntheticTerrain terrain = SyntheticTerrain.boxedFractal(seed, LAND.wx() * WindowKey.SIZE - 2D, LAND.wz() * WindowKey.SIZE - 2D,
                    2 * WindowKey.SIZE);
            Map<RegionKey, RegionPlan> cache = new HashMap<>();
            for (int wz = LAND.wz(); wz <= LAND.wz() + 1; wz++) {
                for (int wx = LAND.wx(); wx <= LAND.wx() + 1; wx++) {
                    WindowKey key = new WindowKey(wx, wz);
                    List<RegionPlan> regions = regionPlans(settings, terrain, key, cache);
                    List<Pond> lakes = new ArrayList<>();
                    for (RegionPlan region : regions) {
                        lakes.addAll(region.lakes());
                    }
                    lakeInflows += assertNoInflowEndsBelowItsLake(planner(settings, terrain).compute(key, regions).allReaches(), lakes);
                }
            }
        }
        assertTrue("reaches ending in a lake " + lakeInflows, lakeInflows > 0);
    }

    @Test
    public void settledPondsKeepTheirInflowsAndOutletsOnTheirShore() {
        HydrologySettings settings = settings(new IrisHydrology());
        int outlets = 0;
        int inflows = 0;
        int loweredInSample = 0;
        for (long seed : new long[]{1L, 2L, 5L, 8L}) {
            SyntheticTerrain terrain = SyntheticTerrain.boxedFractal(seed, LAND.wx() * WindowKey.SIZE - 2D, LAND.wz() * WindowKey.SIZE - 2D,
                    2 * WindowKey.SIZE);
            Map<RegionKey, RegionPlan> cache = new HashMap<>();
            for (int wz = LAND.wz(); wz <= LAND.wz() + 1; wz++) {
                for (int wx = LAND.wx(); wx <= LAND.wx() + 1; wx++) {
                    WindowKey key = new WindowKey(wx, wz);
                    List<RegionPlan> regions = regionPlans(settings, terrain, key, cache);
                    Set<Long> lowered = new HashSet<>();
                    WindowComputation computation = loweringWatched(settings, terrain, lowered).compute(key, regions);
                    for (Pond pond : computation.allPonds()) {
                        loweredInSample += lowered.contains(pond.id()) ? 1 : 0;
                    }
                    List<Pond> water = new ArrayList<>(computation.allPonds());
                    for (RegionPlan region : regions) {
                        water.addAll(region.lakes());
                    }
                    for (Reach reach : computation.allReaches()) {
                        int last = reach.xs().length - 1;
                        if (reach.downstreamId() != 0L || reach.grade()[last] <= settings.seaLevel()
                                || nearWindowEdge(reach.xs()[last]) || nearWindowEdge(reach.zs()[last])) {
                            continue;
                        }
                        Pond entered = null;
                        for (Pond pond : water) {
                            if (nearMask(pond.mask(), reach.xs()[last], reach.zs()[last], WindowPlanner.CELL)) {
                                entered = pond;
                                break;
                            }
                        }
                        assertNotNull(reach + " ends inland at " + reach.xs()[last] + "," + reach.zs()[last] + " off every pond shore", entered);
                        inflows++;
                        assertTrue(reach + " ends at " + reach.grade()[last] + " below the pond it enters at " + entered.level(),
                                reach.grade()[last] >= entered.level());
                    }
                    Map<Long, Reach> byId = byId(computation.allReaches());
                    for (Pond pond : computation.allPonds()) {
                        Reach outlet = byId.get(pond.outletReachId());
                        if (outlet == null) {
                            continue;
                        }
                        outlets++;
                        assertOutletLeavesAtThePondHead(pond, outlet);
                        assertTrue(pond + " outlet " + outlet + " starts above it", outlet.grade()[0] <= pond.level());
                    }
                }
            }
        }
        assertTrue("pond outlets " + outlets, outlets > 0);
        assertTrue("pond inflows " + inflows, inflows > 0);
        assertTrue("ponds the shaper lowered and the planner settled " + loweredInSample, loweredInSample > 0);
    }

    private static WindowPlanner loweringWatched(HydrologySettings settings, SyntheticTerrain terrain, Set<Long> lowered) {
        ReachShaper shaper = spy(new ReachShaper(settings));
        doAnswer(call -> {
            List<Pond> planned = call.getArgument(6);
            ShapedNetwork network = (ShapedNetwork) call.callRealMethod();
            Map<Long, Integer> levels = new HashMap<>();
            for (Pond pond : planned) {
                levels.put(pond.id(), pond.level());
            }
            for (Pond pond : network.ponds()) {
                Integer level = levels.get(pond.id());
                if (level != null && pond.level() < level) {
                    lowered.add(pond.id());
                }
            }
            return network;
        }).when(shaper).shape(any(), any(), any(), any(), any(), any(), any(), any(), anyLong());
        return new WindowPlanner(settings,
                new HeightLattice(terrain, WindowPlanner.FINE_CELL, false, 1 << 21), new PondPlanner(settings), shaper);
    }

    @Test
    public void planIsDeterministic() throws Exception {
        HydrologySettings settings = settings(new IrisHydrology());
        List<RegionPlan> regions = regionPlans(settings, SyntheticTerrain.openFractal(SEED), LAND, new HashMap<>());
        WindowPlanner planner = planner(settings, SyntheticTerrain.openFractal(SEED));
        WindowPlan first = planner.plan(LAND, regions);
        assertFalse(first.reaches().isEmpty());
        assertFalse(first.ponds().isEmpty());
        WindowKey east = new WindowKey(LAND.wx() + 1, LAND.wz());
        planner.compute(east, regionPlans(settings, SyntheticTerrain.openFractal(SEED), east, new HashMap<>()));
        assertEquals(first, planner.plan(LAND, regions));
        assertEquals(first, planner.plan(LAND, reversed(regions)));
        WindowPlanner fresh = planner(settings, SyntheticTerrain.openFractal(SEED));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<WindowPlan> left = pool.submit(() -> fresh.plan(LAND, regions));
            Future<WindowPlan> right = pool.submit(() -> fresh.plan(LAND, regions));
            assertEquals(first, left.get());
            assertEquals(first, right.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private static boolean nearWindowEdge(float coordinate) {
        int offset = Math.floorMod((int) Math.floor(coordinate), WindowKey.SIZE);
        return offset < WindowPlanner.CELL || offset >= WindowKey.SIZE - WindowPlanner.CELL;
    }

    @Test
    public void pondOutletsAndConfluencesMeetConnectedWater() {
        int outlets = 0;
        int feeders = 0;
        for (long seed = 1L; seed <= 7L; seed++) {
            PlannedScene scene = PlannedScene.plan(SyntheticTerrain.openFractal(seed), PlannedScene.packSettings(SEED), 1, 1);
            assertEquals("detached outlets on openFractal(" + seed + ")", List.of(), WaterInvariants.detachedOutlets(scene));
            assertEquals("confluence tongues on openFractal(" + seed + ")", List.of(), WaterInvariants.confluenceTongues(scene));
            outlets += WaterInvariants.checkedPonds(scene).size();
            feeders += WaterInvariants.checkedFeeders(scene).size();
        }
        assertTrue("pond outlets checked " + outlets, outlets >= 50);
        assertTrue("confluence feeders checked " + feeders, feeders >= 300);
    }

    private static void assertOutletLeavesAtThePondHead(Pond pond, Reach outlet) {
        PondMask mask = pond.mask();
        int leading = 0;
        while (leading + 1 < outlet.xs().length && touchesShore(mask, outlet, leading)) {
            leading++;
        }
        assertTrue(pond + " outlet " + outlet.id() + " starts off its shore at " + outlet.xs()[0] + "," + outlet.zs()[0], leading > 0);
        int head = WaterHeads.standing(pond.level(), SEA_LEVEL);
        for (int segment = 0; segment < leading; segment++) {
            int[] box = segmentBox(outlet, segment);
            for (int z = box[1]; z <= box[3]; z++) {
                for (int x = box[0]; x <= box[2]; x++) {
                    int[] covering = nearestCovering(outlet, x, z);
                    if (covering[0] == segment && onShore(mask, x, z)) {
                        assertEquals(pond + " outlet " + outlet.id() + " water at " + x + "," + z, head,
                                WaterHeads.snap(outlet.grade()[covering[1]], SEA_LEVEL, outlet.tier().stepHeight()));
                    }
                }
            }
        }
    }

    private static boolean touchesShore(PondMask mask, Reach outlet, int segment) {
        int[] box = segmentBox(outlet, segment);
        for (int z = box[1]; z <= box[3]; z++) {
            for (int x = box[0]; x <= box[2]; x++) {
                if (onShore(mask, x, z) && covers(outlet, segment, x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean onShore(PondMask mask, int x, int z) {
        return mask.contains(x, z) || mask.contains(x + 1, z) || mask.contains(x - 1, z) || mask.contains(x, z + 1) || mask.contains(x, z - 1);
    }

    private static int[] segmentBox(Reach reach, int segment) {
        float radius = Math.max(reach.halfWidth()[segment], reach.halfWidth()[segment + 1]);
        return new int[]{(int) Math.floor(Math.min(reach.xs()[segment], reach.xs()[segment + 1]) - radius),
                (int) Math.floor(Math.min(reach.zs()[segment], reach.zs()[segment + 1]) - radius),
                (int) Math.ceil(Math.max(reach.xs()[segment], reach.xs()[segment + 1]) + radius),
                (int) Math.ceil(Math.max(reach.zs()[segment], reach.zs()[segment + 1]) + radius)};
    }

    private static int[] nearestCovering(Reach reach, int x, int z) {
        double nearest = Double.POSITIVE_INFINITY;
        int[] found = {-1, -1};
        for (int segment = 0; segment + 1 < reach.xs().length; segment++) {
            double t = WaterInvariants.along(reach, segment, x, z);
            double distance = WaterInvariants.offset(reach, segment, t, x, z);
            if (distance <= WaterInvariants.width(reach, segment, t) && distance < nearest) {
                nearest = distance;
                found[0] = segment;
                found[1] = t < 0.5D ? segment : segment + 1;
            }
        }
        return found;
    }

    private static boolean covers(Reach reach, int segment, int x, int z) {
        double t = WaterInvariants.along(reach, segment, x, z);
        return WaterInvariants.offset(reach, segment, t, x, z) <= WaterInvariants.width(reach, segment, t);
    }

    private static boolean nearMask(PondMask mask, float x, float z, int distance) {
        for (int dz = -distance; dz <= distance; dz++) {
            for (int dx = -distance; dx <= distance; dx++) {
                if (mask.contains((int) Math.floor(x) + dx, (int) Math.floor(z) + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int assertNoInflowEndsBelowItsLake(List<Reach> reaches, List<Pond> lakes) {
        int checked = 0;
        for (Reach reach : reaches) {
            int last = reach.xs().length - 1;
            if (reach.downstreamId() != 0L) {
                continue;
            }
            for (Pond lake : lakes) {
                if (lake.mask().contains((int) Math.floor(reach.xs()[last]), (int) Math.floor(reach.zs()[last]))) {
                    checked++;
                    assertTrue(reach + " ends at " + reach.grade()[last] + " below lake " + lake.id() + " at " + lake.level(),
                            reach.grade()[last] >= lake.level());
                }
            }
        }
        return checked;
    }

    private static IrisHydrology withoutMajorRivers() {
        IrisHydrology config = new IrisHydrology();
        config.getTiers().get(2).setMinCatchment(1.0e7D);
        return config;
    }

    private static Pond single(List<Pond> ponds) {
        assertEquals("ponds " + ponds, 1, ponds.size());
        return ponds.get(0);
    }

    private static int count(List<Pond> ponds, PondKind kind) {
        int matching = 0;
        for (Pond pond : ponds) {
            matching += pond.kind() == kind ? 1 : 0;
        }
        return matching;
    }

    @Test
    public void seaInletsBelongToTheWindowOfTheirFirstLandStationInAnyPlanningOrder() {
        HydrologySettings settings = settings(new IrisHydrology());
        SyntheticTerrain terrain = SyntheticTerrain.cliffCoast(1400D);
        List<WindowKey> keys = List.of(new WindowKey(2, 0), new WindowKey(3, 0), new WindowKey(2, 1), new WindowKey(3, 1));
        Map<WindowKey, WindowPlan> forward = new HashMap<>();
        WindowPlanner first = planner(settings, terrain);
        for (WindowKey key : keys) {
            forward.put(key, first.plan(key, noRegions(key)));
        }
        Map<WindowKey, WindowPlan> backward = new HashMap<>();
        WindowPlanner second = planner(settings, terrain);
        for (int index = keys.size() - 1; index >= 0; index--) {
            backward.put(keys.get(index), second.plan(keys.get(index), noRegions(keys.get(index))));
        }
        assertEquals(forward, backward);
        int spacing = settings.underground().seaInlets().spacing();
        Set<Long> sites = new HashSet<>();
        int inlets = 0;
        int crossing = 0;
        for (Map.Entry<WindowKey, WindowPlan> entry : forward.entrySet()) {
            for (SeaInlet inlet : entry.getValue().inlets()) {
                inlets++;
                int landX = (int) Math.floor(inlet.xs()[1]);
                int landZ = (int) Math.floor(inlet.zs()[1]);
                assertEquals(inlet + " belongs to the window of its first land station", WindowKey.containing(landX, landZ), entry.getKey());
                assertTrue(inlet + " is the only inlet of its site", sites.add(((long) Math.floorDiv(landX, spacing) << 32)
                        | (Math.floorDiv(landZ, spacing) & 0xFFFFFFFFL)));
                boolean leaves = false;
                for (int station = 0; station < inlet.stations(); station++) {
                    leaves |= !WindowKey.containing((int) Math.floor(inlet.xs()[station]), (int) Math.floor(inlet.zs()[station])).equals(entry.getKey());
                }
                crossing += leaves ? 1 : 0;
            }
        }
        assertTrue("sea inlets on the cliff coast " + inlets, inlets >= 2);
        assertTrue("inlets running into the next window " + crossing, crossing > 0);
        for (WindowKey key : List.of(new WindowKey(3, 0), new WindowKey(3, 1))) {
            assertTrue("the inland window owns none of the coast's inlets", forward.get(key).inlets().isEmpty());
        }
    }

    @Test
    public void seaConnectedHeadsSitAtSeaLevel() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        PlannedScene fractal = PlannedScene.plan(SyntheticTerrain.openFractal(1L).lowered(COAST_DROP), settings, 2, 2);
        PlannedScene cliff = PlannedScene.plan(SyntheticTerrain.cliffCoast(CLIFF_COAST_X), settings, 2, 2);
        SeaColumns fractalColumns = assertSeaConnectedHeads("openFractal(1) lowered " + COAST_DROP, fractal);
        SeaColumns cliffColumns = assertSeaConnectedHeads("cliffCoast(" + CLIFF_COAST_X + ")", cliff);
        assertTrue("mouth vertices on the coastal fractal " + fractalColumns.vertices(), fractalColumns.vertices() >= 100);
        assertTrue("mouth columns on the coastal fractal " + fractalColumns.mouths(), fractalColumns.mouths() >= 1000);
        assertTrue("mouth columns on the cliff coast " + cliffColumns.mouths(), cliffColumns.mouths() >= 1000);
        assertTrue("sea inlets on the cliff coast " + cliff.inlets().size(), cliff.inlets().size() >= 2);
        assertTrue("inlet columns on the cliff coast " + cliffColumns.inlets(), cliffColumns.inlets() >= 500);
    }

    @Test
    public void coastalBanksNeverRaiseTheSeaFloor() {
        PlannedScene scene = PlannedScene.plan(SyntheticTerrain.openFractal(1L).lowered(COAST_DROP), PlannedScene.packSettings(SEED), 2, 2);
        int banksInTheSea = 0;
        int raised = 0;
        int emergent = 0;
        int holding = 0;
        String first = null;
        for (int z = scene.minZ() + WaterInvariants.EDGE_MARGIN; z <= scene.maxZ() - WaterInvariants.EDGE_MARGIN; z++) {
            for (int x = scene.minX() + WaterInvariants.EDGE_MARGIN; x <= scene.maxX() - WaterInvariants.EDGE_MARGIN; x++) {
                int natural = scene.natural(x, z);
                if (natural >= SEA_LEVEL || scene.head(x, z) != Integer.MIN_VALUE) {
                    continue;
                }
                banksInTheSea += scene.role(x, z) == WaterRole.BANK ? 1 : 0;
                int ground = scene.terrain(x, z);
                if (ground <= natural) {
                    continue;
                }
                if (holdsWaterAboveTheSea(scene, x, z)) {
                    holding++;
                    continue;
                }
                raised++;
                emergent += ground >= SEA_LEVEL ? 1 : 0;
                if (first == null) {
                    first = scene.role(x, z) + " at " + x + "," + z + " raised from " + natural + " to " + ground;
                }
            }
        }
        assertTrue("bank columns in the sea " + banksInTheSea, banksInTheSea >= 1000);
        assertEquals("sea columns raised " + raised + " (" + emergent + " out of the sea, " + holding + " holding water above the sea), first "
                + first, 0, raised);
    }

    private static boolean holdsWaterAboveTheSea(PlannedScene scene, int x, int z) {
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] side : sides) {
            if (scene.head(x + side[0], z + side[1]) > SEA_LEVEL) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void coastalDeltasBelongToOneWindowAndPersist() {
        IrisHydrology config = PlannedScene.packConfig();
        for (IrisHydrologyTier tier : config.getTiers()) {
            tier.getDelta().setChannels(2);
        }
        HydrologySettings settings = HydrologySettings.compile(config, SEED, SEA_LEVEL, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
        SyntheticTerrain coast = SyntheticTerrain.boxedFractal(DELTA_SEED, LAND.wx() * WindowKey.SIZE - 2D, LAND.wz() * WindowKey.SIZE - 2D,
                2 * WindowKey.SIZE);
        Map<WindowKey, WindowComputation> fanned = coastalWindows(settings, coast);
        Set<Long> plain = new HashSet<>();
        for (WindowComputation computation : coastalWindows(PlannedScene.packSettings(SEED), coast).values()) {
            for (Reach reach : computation.allReaches()) {
                plain.add(reach.id());
            }
        }
        Map<Long, WindowKey> owners = new HashMap<>();
        int shared = 0;
        for (Map.Entry<WindowKey, WindowComputation> entry : fanned.entrySet()) {
            WindowPlan owned = entry.getValue().owned();
            for (Reach reach : owned.reaches()) {
                if (plain.contains(reach.id())) {
                    continue;
                }
                assertNull("delta channel " + reach.id() + " has one owner", owners.put(reach.id(), entry.getKey()));
                int last = reach.xs().length - 1;
                assertEquals(reach + " belongs to the window of its sea end", entry.getKey(),
                        WindowKey.containing((int) Math.floor(reach.xs()[last]), (int) Math.floor(reach.zs()[last])));
                assertEquals(reach + " drains to the sea", 0L, reach.downstreamId());
                for (int point = 0; point < reach.grade().length; point++) {
                    assertEquals(reach + " point " + point, ReachStyle.MOUTH, reach.style()[point]);
                    assertEquals(reach + " point " + point, SEA_LEVEL, reach.grade()[point]);
                }
                for (Map.Entry<WindowKey, WindowComputation> other : fanned.entrySet()) {
                    if (other.getKey().equals(entry.getKey())) {
                        continue;
                    }
                    for (Reach seen : other.getValue().allReaches()) {
                        if (seen.id() == reach.id()) {
                            shared++;
                            assertEquals(reach + " seen from " + other.getKey(), reach, seen);
                        }
                    }
                }
            }
            assertEquals(entry.getKey() + " plan round-trips its delta", owned,
                    HydrologyPlanCodec.decodeWindow(settings, HydrologyPlanCodec.encode(settings, owned)).orElseThrow());
        }
        assertTrue("delta channels on the bounded coast " + owners.size(), owners.size() >= 8);
        assertTrue("delta channels also planned by a neighbouring window " + shared, shared >= 8);
    }

    private static Map<WindowKey, WindowComputation> coastalWindows(HydrologySettings settings, SyntheticTerrain terrain) {
        Map<RegionKey, RegionPlan> regions = new HashMap<>();
        Map<WindowKey, WindowComputation> computations = new HashMap<>();
        for (int wz = LAND.wz(); wz <= LAND.wz() + 1; wz++) {
            for (int wx = LAND.wx(); wx <= LAND.wx() + 1; wx++) {
                WindowKey key = new WindowKey(wx, wz);
                computations.put(key, planner(settings, terrain).compute(key, regionPlans(settings, terrain, key, regions)));
            }
        }
        return computations;
    }

    @Test
    public void planWithAMouthBelowSeaFailsClosed() {
        HydrologySettings settings = settings(new IrisHydrology());
        Reach mouth = coastal(4711L, settings.tiers().get(0), SEA_LEVEL - 1, ReachStyle.MOUTH);
        assertFailsClosed(settings, List.of(mouth), List.of(), "reach 4711");
    }

    @Test
    public void seaSliversInsideAReachLeaveNoMouthsTrenchesOrLostPonds() {
        HydrologySettings settings = settings(new IrisHydrology());
        PlannedScene dry = PlannedScene.plan(SyntheticTerrain.valleySlope().withPit(PIT_X, PIT_Z, PIT_RADIUS, PIT_DEPTH), settings, 2, 2);
        int dryCuts = deepCuts(dry);
        for (double stripX : new double[]{817D, 841D}) {
            PlannedScene scene = PlannedScene.plan(SyntheticTerrain.seaStrip(stripX, 4D).withPit(PIT_X, PIT_Z, PIT_RADIUS, PIT_DEPTH), settings, 2, 2);
            int crossing = 0;
            for (Reach reach : scene.reaches()) {
                for (int point = 0; point < reach.style().length; point++) {
                    assertNotEquals(reach + " point " + point + " over the sliver at x " + stripX + " is a mouth", ReachStyle.MOUTH, reach.style()[point]);
                    crossing += reach.xs()[point] >= stripX - 2D && reach.xs()[point] < stripX + 6D ? 1 : 0;
                }
            }
            assertTrue("reach points crossing the sea sliver at x " + stripX + ": " + crossing, crossing > 0);
            int away = assertPondsOffTheSliverChainAreKept(dry, scene, stripX);
            assertTrue("ponds away from the sea sliver at x " + stripX + ": " + away, away > 10);
            int cuts = deepCuts(scene);
            assertTrue("reach points cut more than " + DEEP_CUT + " below the ground with the sliver at x " + stripX + ": " + cuts + ", without it "
                    + dryCuts, cuts <= dryCuts + dryCuts / 50);
        }
    }

    private static int assertPondsOffTheSliverChainAreKept(PlannedScene dry, PlannedScene scene, double stripX) {
        Map<Long, Pond> ponds = new HashMap<>();
        for (Pond pond : scene.ponds()) {
            ponds.put(pond.id(), pond);
        }
        Set<Long> chain = pondsDownstreamOfTheSliver(dry, stripX);
        int away = 0;
        for (Pond pond : dry.ponds()) {
            if (Math.abs(pond.floorX() - stripX) <= SLIVER_REACH) {
                continue;
            }
            Pond kept = ponds.get(pond.id());
            assertNotNull(pond + " beyond the sea sliver at x " + stripX + " is kept", kept);
            if (!chain.contains(pond.id())) {
                away++;
                assertEquals(pond + " level with the sea sliver at x " + stripX, pond.level(), kept.level());
            }
        }
        return away;
    }

    private static Set<Long> pondsDownstreamOfTheSliver(PlannedScene dry, double stripX) {
        Map<Long, Reach> reaches = new HashMap<>();
        for (Reach reach : dry.reaches()) {
            reaches.put(reach.id(), reach);
        }
        Set<Long> chain = new HashSet<>();
        List<Reach> frontier = new ArrayList<>();
        for (Reach reach : dry.reaches()) {
            for (float x : reach.xs()) {
                if (Math.abs(x - stripX) <= SLIVER_REACH) {
                    chain.add(reach.id());
                    frontier.add(reach);
                    break;
                }
            }
        }
        Set<Long> exempt = new HashSet<>();
        for (Pond pond : dry.ponds()) {
            if (Math.abs(pond.floorX() - stripX) <= SLIVER_REACH) {
                exempt.add(pond.id());
                follow(reaches.get(pond.outletReachId()), chain, frontier);
            }
        }
        for (int index = 0; index < frontier.size(); index++) {
            Reach reach = frontier.get(index);
            follow(reaches.get(reach.downstreamId()), chain, frontier);
            int last = reach.xs().length - 1;
            for (Pond pond : dry.ponds()) {
                if (pond.mask().nearest(reach.xs()[last], reach.zs()[last], reach.halfWidth()[last] + 2f) != null) {
                    exempt.add(pond.id());
                    follow(reaches.get(pond.outletReachId()), chain, frontier);
                }
            }
        }
        return exempt;
    }

    private static void follow(Reach reach, Set<Long> chain, List<Reach> frontier) {
        if (reach != null && chain.add(reach.id())) {
            frontier.add(reach);
        }
    }

    private static int deepCuts(PlannedScene scene) {
        int deep = 0;
        for (Reach reach : scene.reaches()) {
            for (int point = 0; point < reach.grade().length; point++) {
                int x = (int) Math.floor(reach.xs()[point]);
                int z = (int) Math.floor(reach.zs()[point]);
                if (pondAt(scene.ponds(), x, z) == null && scene.natural(x, z) - reach.grade()[point] > DEEP_CUT) {
                    deep++;
                }
            }
        }
        return deep;
    }

    @Test
    public void inlandBelowSeaHollowsHoldPondsNotMouths() {
        HydrologySettings settings = settings(new IrisHydrology());
        SyntheticTerrain hollow = SyntheticTerrain.valleySlope().withPit(HOLLOW_X, HOLLOW_Z, HOLLOW_RADIUS, HOLLOW_DEPTH);
        assertTrue("the hollow floor lies far below the sea", hollow.natural(HOLLOW_X, HOLLOW_Z) < SEA_LEVEL - 20D);
        PlannedScene inland = PlannedScene.plan(hollow.withLand((x, z) -> true), settings, 2, 2);
        Pond pond = pondAt(inland.ponds(), HOLLOW_X, HOLLOW_Z);
        assertNotNull("a pond holds the inland hollow, ponds " + inland.ponds(), pond);
        assertEquals("the inland hollow holds water at the sea surface " + pond, SEA_LEVEL, pond.level());
        int inflows = 0;
        for (Reach reach : inland.reaches()) {
            for (int point = 0; point < reach.style().length; point++) {
                assertNotEquals(reach + " point " + point + " is a sea mouth inland", ReachStyle.MOUTH, reach.style()[point]);
            }
            int last = reach.xs().length - 1;
            if (pond.mask().contains((int) Math.floor(reach.xs()[last]), (int) Math.floor(reach.zs()[last]))) {
                inflows++;
                assertEquals(reach + " ends in the hollow pond", 0L, reach.downstreamId());
                assertTrue(reach + " meets the hollow pond at or above its level", reach.grade()[last] >= pond.level());
            }
        }
        assertTrue("streams draining into the hollow end in its pond: " + inflows, inflows > 0);
        assertEquals("the inland hollow pond has no outlet", 0L, pond.outletReachId());
        assertNoSeaLevelCanyon(inland, hollow);
        PlannedScene valley = PlannedScene.plan(SyntheticTerrain.valleySlope().withLand((x, z) -> true), settings, 2, 2);
        int beyond = deepCutsBeyond(inland, HOLLOW_X + 1.5D * HOLLOW_RADIUS);
        int open = deepCutsBeyond(valley, HOLLOW_X + 1.5D * HOLLOW_RADIUS);
        assertTrue("reach points cut more than " + DEEP_CUT + " deep past the hollow rim: " + beyond + ", in the same valley without the hollow "
                + open, beyond <= open + open / 50);
        assertEquals("the hollow centre is pond water", WaterRole.POND, inland.role((int) HOLLOW_X, (int) HOLLOW_Z));
        assertEquals("the hollow pond surface", SEA_LEVEL, inland.head((int) HOLLOW_X, (int) HOLLOW_Z));
        for (int z = inland.minZ() + WaterInvariants.EDGE_MARGIN; z <= inland.maxZ() - WaterInvariants.EDGE_MARGIN; z++) {
            for (int x = inland.minX() + WaterInvariants.EDGE_MARGIN; x <= inland.maxX() - WaterInvariants.EDGE_MARGIN; x++) {
                assertNotEquals("no sea mouth water inland at " + x + "," + z, WaterRole.MOUTH, inland.role(x, z));
            }
        }
        PlannedScene sea = PlannedScene.plan(hollow, settings, 2, 2);
        assertNull("the same hollow with sea intent is the sea, not a pond", pondAt(sea.ponds(), HOLLOW_X, HOLLOW_Z));
        int mouths = 0;
        for (Reach reach : sea.reaches()) {
            int last = reach.xs().length - 1;
            if (reach.style()[last] == ReachStyle.MOUTH && Math.hypot(reach.xs()[last] - HOLLOW_X, reach.zs()[last] - HOLLOW_Z) < HOLLOW_RADIUS) {
                mouths++;
            }
        }
        assertTrue("streams entering the sea-intent hollow keep their mouths: " + mouths, mouths > 0);
    }

    @Test
    public void pondlessInlandHollowsStopStreamsAtTheirEdge() {
        HydrologySettings settings = settings(new IrisHydrology());
        SyntheticTerrain hollow = SyntheticTerrain.valleySlope().withPit(HOLLOW_X, HOLLOW_Z, HOLLOW_RADIUS, HOLLOW_DEPTH).withLand((x, z) -> true)
                .withPondsBlocked((x, z) -> Math.hypot(x - HOLLOW_X, z - HOLLOW_Z) < HOLLOW_RADIUS);
        PlannedScene scene = PlannedScene.plan(hollow, settings, 2, 2);
        assertNull("no pond fills a hollow whose policy blocks ponds", pondAt(scene.ponds(), HOLLOW_X, HOLLOW_Z));
        int edges = 0;
        for (Reach reach : scene.reaches()) {
            for (int point = 0; point < reach.grade().length; point++) {
                double ground = hollow.natural(reach.xs()[point], reach.zs()[point]);
                assertNotEquals(reach + " point " + point + " is a sea mouth inland", ReachStyle.MOUTH, reach.style()[point]);
                assertTrue(reach + " point " + point + " runs into the hollow over ground at " + ground, ground >= SEA_LEVEL - HOLLOW_EDGE);
            }
            int last = reach.xs().length - 1;
            if (reach.downstreamId() == 0L && Math.hypot(reach.xs()[last] - HOLLOW_X, reach.zs()[last] - HOLLOW_Z) < HOLLOW_RADIUS) {
                edges++;
                assertTrue(reach + " ends at the hollow edge", hollow.natural(reach.xs()[last], reach.zs()[last]) < SEA_LEVEL + HOLLOW_EDGE);
            }
        }
        assertTrue("streams draining into the hollow end at its edge: " + edges, edges > 0);
        assertNoSeaLevelCanyon(scene, hollow);
    }

    private static boolean inSquare(WindowKey key, float[] point) {
        return inSquare(key, point[0], point[1]);
    }

    private static float[] lastPoint(Reach reach) {
        int last = reach.xs().length - 1;
        return new float[]{reach.xs()[last], reach.zs()[last]};
    }

    private static void assertNoSeaLevelCanyon(PlannedScene scene, SyntheticTerrain terrain) {
        assertNoSeaLevelCanyon(scene.reaches(), scene.ponds(), terrain);
    }

    private static void assertNoSeaLevelCanyon(List<Reach> reaches, List<Pond> ponds, SyntheticTerrain terrain) {
        for (Reach reach : reaches) {
            for (int point = 0; point < reach.grade().length; point++) {
                double ground = terrain.natural(reach.xs()[point], reach.zs()[point]);
                if (pondAt(ponds, reach.xs()[point], reach.zs()[point]) == null) {
                    assertTrue(reach + " point " + point + " is held at the sea under ground at " + ground,
                            reach.grade()[point] > SEA_LEVEL + 2 || ground < SEA_LEVEL + RIM_CUT);
                }
            }
        }
    }

    private static int deepCutsBeyond(PlannedScene scene, double minX) {
        int deep = 0;
        for (Reach reach : scene.reaches()) {
            for (int point = 0; point < reach.grade().length; point++) {
                int x = (int) Math.floor(reach.xs()[point]);
                int z = (int) Math.floor(reach.zs()[point]);
                if (x > minX && pondAt(scene.ponds(), x, z) == null && scene.natural(x, z) - reach.grade()[point] > DEEP_CUT) {
                    deep++;
                }
            }
        }
        return deep;
    }

    @Test
    public void hollowOnAWindowLineIsPlannedAlikeByBothNeighbours() {
        IrisHydrology config = new IrisHydrology();
        config.getLakes().setEnabled(false);
        config.getPonds().getSeeded().setAlongChannels(false);
        config.getTiers().get(0).setMinCatchment(LINE_STREAM_CATCHMENT);
        HydrologySettings settings = settings(config);
        int line = LAND.wx() * WindowKey.SIZE;
        double axis = LAND.wz() * WindowKey.SIZE + SEAM_DISTANCE;
        SyntheticTerrain terrain = SyntheticTerrain.walledValley(axis, VALLEY_HALF_WIDTH, line + 2 * WindowKey.SIZE - VALLEY_SEA_INSET)
                .withPit(line, axis, LINE_HOLLOW_RADIUS, LINE_HOLLOW_DEPTH)
                .withLand((x, z) -> Math.hypot(x - line, z - axis) < LINE_HOLLOW_RADIUS + 2D * WindowPlanner.CELL);
        assertTrue("the hollow floor lies far below the sea", terrain.natural(line, axis) < SEA_LEVEL - 20D);
        WindowKey west = LAND;
        WindowKey east = new WindowKey(LAND.wx() + 1, LAND.wz());
        assertEquals("the east window's planning area starts on the hollow's centre line", line, (east.wx() - 1) * WindowKey.SIZE);
        Map<RegionKey, RegionPlan> cache = new HashMap<>();
        WindowComputation westView = planner(settings, terrain).compute(west, regionPlans(settings, terrain, west, cache));
        WindowComputation eastView = planner(settings, terrain).compute(east, regionPlans(settings, terrain, east, cache));
        Set<Reach> westOwned = nearSeamReaches(west, east, westView.owned().reaches());
        Set<Reach> eastOwned = nearSeamReaches(east, west, eastView.owned().reaches());
        assertEquals(west + " reaches seen from " + east, westOwned, nearSeamReaches(west, east, eastView.allReaches()));
        assertEquals(east + " reaches seen from " + west, eastOwned, nearSeamReaches(east, west, westView.allReaches()));
        List<Reach> owned = new ArrayList<>(westView.owned().reaches());
        owned.addAll(eastView.owned().reaches());
        List<Pond> water = new ArrayList<>(westView.allPonds());
        water.addAll(eastView.allPonds());
        for (RegionPlan region : cache.values()) {
            water.addAll(region.lakes());
        }
        assertNoSeaLevelCanyon(owned, water, terrain);
        Map<Long, Reach> byId = byId(owned);
        int links = 0;
        for (Reach feeder : owned) {
            Reach downstream = byId.get(feeder.downstreamId());
            if (downstream == null || inSquare(west, lastPoint(feeder)) == inSquare(west, lastPoint(downstream))) {
                continue;
            }
            links++;
            int last = feeder.xs().length - 1;
            int drop = feeder.grade()[last] - downstream.grade()[0];
            assertTrue(feeder + " meets " + downstream + " across the window line with a drop of " + drop,
                    drop >= 0 && drop < downstream.tier().waterfallMinimumDrop());
        }
        assertTrue("reaches near the window line " + westOwned.size() + " + " + eastOwned.size(), westOwned.size() + eastOwned.size() >= 3);
        assertTrue("reach links between the two windows' plans " + links, links >= 1);
        assertTrue("a major river crosses the window line", tier(new ArrayList<>(eastOwned), settings.majorTier()).size()
                + tier(new ArrayList<>(westOwned), settings.majorTier()).size() >= 1);
    }

    @Test
    public void deepInlandHollowsKeepTheirRim() {
        HydrologySettings settings = settings(new IrisHydrology());
        SyntheticTerrain crater = SyntheticTerrain.crater(CRATER_X, CRATER_Z, CRATER_SPILL, CRATER_RIM)
                .withLand((x, z) -> Math.hypot(x - CRATER_X, z - CRATER_Z) < CRATER_RIM + 50D);
        double floor = crater.natural(CRATER_X, CRATER_Z);
        assertTrue("the crater floor lies below the sea and deeper than the breach depth under its spill " + floor,
                floor < SEA_LEVEL && CRATER_SPILL - floor > settings.drainage().breachDepth());
        PlannedScene scene = PlannedScene.plan(crater, settings, 2, 2);
        Pond pond = pondAt(scene.ponds(), CRATER_X, CRATER_Z);
        assertNotNull("a pond holds the crater floor, ponds " + scene.ponds(), pond);
        assertEquals(pond.toString(), SEA_LEVEL, pond.level());
        assertEquals("the crater pond has no outlet " + pond, 0L, pond.outletReachId());
        int rim = 0;
        for (Reach reach : scene.reaches()) {
            for (int point = 0; point < reach.grade().length; point++) {
                double distance = Math.hypot(reach.xs()[point] - CRATER_X, reach.zs()[point] - CRATER_Z);
                if (distance < 0.5D * CRATER_RIM || distance > CRATER_RIM + 100D) {
                    continue;
                }
                rim++;
                double ground = crater.natural(reach.xs()[point], reach.zs()[point]);
                assertNotEquals(reach + " point " + point + " is a sea mouth in the crater", ReachStyle.MOUTH, reach.style()[point]);
                assertTrue(reach + " point " + point + " cuts through the crater rim, graded " + reach.grade()[point] + " under ground " + ground,
                        ground - reach.grade()[point] <= RIM_CUT);
            }
        }
        assertTrue("reach points on the crater walls " + rim, rim > 100);
    }

    private static Pond pondAt(List<Pond> ponds, double x, double z) {
        for (Pond pond : ponds) {
            if (pond.mask().contains((int) Math.floor(x), (int) Math.floor(z))) {
                return pond;
            }
        }
        return null;
    }

    @Test
    public void planWithWaterOffTheSeaSurfaceFailsClosed() {
        HydrologySettings settings = settings(new IrisHydrology());
        HydrologyTier stream = settings.tiers().get(0);
        assertFailsClosed(settings, List.of(coastal(4712L, stream, SEA_LEVEL + 1, ReachStyle.MOUTH)), List.of(), "reach 4712");
        assertFailsClosed(settings, List.of(coastal(4713L, stream, SEA_LEVEL - 1, ReachStyle.RUN)), List.of(), "reach 4713");
        Reach crossing = new Reach(4715L, stream, 0L, new float[]{64f, 80f, 96f}, new float[]{64f, 64f, 64f},
                new int[]{SEA_LEVEL + 6, SEA_LEVEL + 4, SEA_LEVEL + 2}, new float[]{2f, 2f, 2f}, new float[]{1f, 1f, 1f},
                new ReachStyle[]{ReachStyle.RUN, ReachStyle.MOUTH, ReachStyle.RUN}, 1000f);
        assertFailsClosed(settings, List.of(crossing), List.of(), "reach 4715");
        Reach linked = new Reach(4716L, stream, 4717L, new float[]{64f, 80f, 96f}, new float[]{64f, 64f, 64f},
                new int[]{SEA_LEVEL + 2, SEA_LEVEL, SEA_LEVEL}, new float[]{2f, 2f, 2f}, new float[]{1f, 1f, 1f},
                new ReachStyle[]{ReachStyle.RUN, ReachStyle.MOUTH, ReachStyle.MOUTH}, 1000f);
        assertFailsClosed(settings, List.of(linked), List.of(), "reach 4716");
        int width = 9;
        long[] bits = new long[(width * width + 63) >>> 6];
        for (int cell = 0; cell < width * width; cell++) {
            bits[cell >>> 6] |= 1L << cell;
        }
        Pond sunken = new Pond(4714L, PondKind.NATURAL, SEA_LEVEL - 1, SEA_LEVEL - 4, 132, 128, SEA_LEVEL,
                new PondMask(124, 124, 1, width, width, bits), 0L, 0, "water", 128, 128);
        assertFailsClosed(settings, List.of(), List.of(sunken), "pond 4714");
    }

    private static Reach coastal(long id, HydrologyTier tier, int mouthGrade, ReachStyle mouthStyle) {
        return new Reach(id, tier, 0L, new float[]{64f, 80f, 96f}, new float[]{64f, 64f, 64f},
                new int[]{mouthGrade + 2, mouthGrade + 2, mouthGrade}, new float[]{2f, 2f, 2f}, new float[]{1f, 1f, 1f},
                new ReachStyle[]{ReachStyle.RUN, ReachStyle.RUN, mouthStyle}, 1000f);
    }

    private static void assertFailsClosed(HydrologySettings settings, List<Reach> reaches, List<Pond> water, String feature) {
        WindowPlanner planner = plannerReturning(settings, reaches, water);
        IllegalStateException failure = assertThrows(feature + " off the sea surface", IllegalStateException.class,
                () -> planner.compute(ORIGIN, noRegions(ORIGIN)));
        assertTrue(failure.getMessage(), failure.getMessage().contains(feature) && failure.getMessage().contains("Window " + ORIGIN));
    }

    private static WindowPlanner plannerReturning(HydrologySettings settings, List<Reach> reaches, List<Pond> water) {
        PondPlanner ponds = spy(new PondPlanner(settings));
        doReturn(CupSet.EMPTY).when(ponds).subtractCups(any(), any(), any(), any());
        doReturn(water).when(ponds).ponds(any(), any(), any(), any(), any(), any());
        ReachShaper shaper = mock(ReachShaper.class);
        doReturn(new ShapedNetwork(reaches, water)).when(shaper).shape(any(), any(), any(), any(), any(), any(), any(), any(), anyLong());
        return new WindowPlanner(settings, new HeightLattice(SyntheticTerrain.flat(100D), WindowPlanner.FINE_CELL, false, 1 << 18), ponds, shaper);
    }

    private static SeaColumns assertSeaConnectedHeads(String name, PlannedScene scene) {
        for (SeaInlet inlet : scene.inlets()) {
            assertEquals(name + " " + inlet, SEA_LEVEL, inlet.waterLevel());
        }
        int vertices = 0;
        for (Reach reach : scene.reaches()) {
            for (int point = 0; point < reach.grade().length; point++) {
                assertTrue(name + " " + reach + " point " + point + " grade " + reach.grade()[point], reach.grade()[point] >= SEA_LEVEL);
                if (reach.style()[point] == ReachStyle.MOUTH) {
                    vertices++;
                    assertEquals(name + " " + reach + " mouth point " + point, SEA_LEVEL, reach.grade()[point]);
                }
            }
        }
        for (Pond pond : scene.ponds()) {
            assertTrue(name + " " + pond, pond.level() >= SEA_LEVEL);
        }
        int mouths = 0;
        int inlets = 0;
        for (int z = scene.minZ() + WaterInvariants.EDGE_MARGIN; z <= scene.maxZ() - WaterInvariants.EDGE_MARGIN; z++) {
            for (int x = scene.minX() + WaterInvariants.EDGE_MARGIN; x <= scene.maxX() - WaterInvariants.EDGE_MARGIN; x++) {
                WaterRole role = scene.role(x, z);
                if (role == WaterRole.MOUTH || role == WaterRole.INLET) {
                    assertEquals(name + " " + role + " head at " + x + "," + z, SEA_LEVEL, scene.head(x, z));
                    mouths += role == WaterRole.MOUTH ? 1 : 0;
                    inlets += role == WaterRole.INLET ? 1 : 0;
                }
            }
        }
        assertEquals(name + " water below the sea surface", List.of(), WaterInvariants.subSeaAir(scene, SEA_LEVEL));
        return new SeaColumns(vertices, mouths, inlets);
    }

    private static HydrologySettings settings(IrisHydrology config) {
        return HydrologySettings.compile(config, SEED, SEA_LEVEL, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
    }

    private static WindowPlanner planner(HydrologySettings settings, SyntheticTerrain terrain) {
        return new WindowPlanner(settings,
                new HeightLattice(terrain, WindowPlanner.FINE_CELL, false, 1 << 21), new PondPlanner(settings), new ReachShaper(settings));
    }

    private static RegionPlanner regionPlanner(HydrologySettings settings, SyntheticTerrain terrain) {
        return new RegionPlanner(settings, new HeightLattice(terrain, RegionPlanner.CELL, true, 1 << 18),
                new HeightLattice(terrain, RegionPlanner.FINE_CELL, false, 1 << 20), new PondPlanner(settings), new ReachShaper(settings));
    }

    private static boolean maskTouchesComputeArea(PondMask mask, WindowKey window) {
        int minX = (window.wx() - 1) * WindowKey.SIZE;
        int minZ = (window.wz() - 1) * WindowKey.SIZE;
        for (int j = 0; j < mask.depth(); j++) {
            for (int i = 0; i < mask.width(); i++) {
                int x = mask.originX() + i * mask.step();
                int z = mask.originZ() + j * mask.step();
                if (x >= minX && x < minX + 3 * WindowKey.SIZE && z >= minZ && z < minZ + 3 * WindowKey.SIZE && mask.contains(x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<RegionPlan> regionPlans(HydrologySettings settings, SyntheticTerrain terrain, WindowKey key, Map<RegionKey, RegionPlan> cache) {
        RegionPlanner planner = regionPlanner(settings, terrain);
        List<RegionPlan> plans = new ArrayList<>();
        for (RegionKey region : WindowPlanner.lakeRegions(key)) {
            plans.add(cache.computeIfAbsent(region, planner::plan));
        }
        return plans;
    }

    private static List<RegionPlan> noRegions(WindowKey key) {
        List<RegionPlan> plans = new ArrayList<>();
        for (RegionKey region : WindowPlanner.lakeRegions(key)) {
            plans.add(new RegionPlan(region, List.of(), List.of()));
        }
        return plans;
    }

    private static List<RegionPlan> reversed(List<RegionPlan> plans) {
        List<RegionPlan> reversed = new ArrayList<>(plans);
        Collections.reverse(reversed);
        return reversed;
    }

    private static List<Reach> heads(List<Reach> reaches) {
        Set<Long> fed = new HashSet<>();
        for (Reach reach : reaches) {
            fed.add(reach.downstreamId());
        }
        List<Reach> heads = new ArrayList<>();
        for (Reach reach : reaches) {
            if (!fed.contains(reach.id()) && reach.style()[0] != ReachStyle.POND_OUTLET) {
                heads.add(reach);
            }
        }
        return heads;
    }

    private static String end(Reach reach) {
        int last = reach.xs().length - 1;
        return reach.xs()[last] + "," + reach.zs()[last];
    }

    private static Map<Long, Reach> byId(List<Reach> reaches) {
        Map<Long, Reach> byId = new HashMap<>();
        for (Reach reach : reaches) {
            byId.put(reach.id(), reach);
        }
        return byId;
    }

    private static List<Reach> tier(List<Reach> reaches, HydrologyTier tier) {
        List<Reach> matching = new ArrayList<>();
        for (Reach reach : reaches) {
            if (reach.tier().equals(tier)) {
                matching.add(reach);
            }
        }
        return matching;
    }

    private static int trunkPointsFollowed(WindowKey key, List<Reach> trunks, List<Reach> reaches) {
        int followed = 0;
        for (Reach trunk : trunks) {
            for (int point = 0; point < trunk.xs().length; point++) {
                if (inSquare(key, trunk.xs()[point], trunk.zs()[point])) {
                    assertTrue("trunk point " + trunk.xs()[point] + "," + trunk.zs()[point],
                            near(reaches, trunk.xs()[point], trunk.zs()[point], TrunkRouter.CORRIDOR + WindowPlanner.CELL));
                    followed++;
                }
            }
        }
        return followed;
    }

    private static boolean inSquare(WindowKey key, float x, float z) {
        return key.equals(WindowKey.containing((int) Math.floor(x), (int) Math.floor(z)));
    }

    private static boolean near(List<Reach> reaches, float x, float z, double distance) {
        for (Reach reach : reaches) {
            for (int point = 0; point < reach.xs().length; point++) {
                if (Math.hypot(reach.xs()[point] - x, reach.zs()[point] - z) <= distance) {
                    return true;
                }
            }
        }
        return false;
    }

    private static float minX(List<Reach> reaches) {
        float min = Float.POSITIVE_INFINITY;
        for (Reach reach : reaches) {
            for (float x : reach.xs()) {
                min = Math.min(min, x);
            }
        }
        return min;
    }

    private static float maxX(List<Reach> reaches) {
        float max = Float.NEGATIVE_INFINITY;
        for (Reach reach : reaches) {
            for (float x : reach.xs()) {
                max = Math.max(max, x);
            }
        }
        return max;
    }

    private static boolean nearLake(Pond lake, float x, float z) {
        int step = lake.mask().step();
        for (int dz = -2 * step; dz <= 2 * step; dz += step) {
            for (int dx = -2 * step; dx <= 2 * step; dx += step) {
                if (lake.mask().contains((int) Math.floor(x) + dx, (int) Math.floor(z) + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean onAreaBorder(WindowKey key, float x, float z) {
        float minX = (key.wx() - 1) * WindowKey.SIZE + WindowPlanner.CELL;
        float minZ = (key.wz() - 1) * WindowKey.SIZE + WindowPlanner.CELL;
        float maxX = (key.wx() + 2) * WindowKey.SIZE - 2 * WindowPlanner.CELL;
        float maxZ = (key.wz() + 2) * WindowKey.SIZE - 2 * WindowPlanner.CELL;
        return x <= minX || z <= minZ || x >= maxX || z >= maxZ;
    }

    private static boolean inPond(List<Pond> ponds, float x, float z) {
        for (Pond pond : ponds) {
            if (pond.mask().contains((int) Math.floor(x), (int) Math.floor(z))) {
                return true;
            }
        }
        return false;
    }

    private static Set<Reach> nearSeamReaches(WindowKey owner, WindowKey other, List<Reach> reaches) {
        Set<Reach> near = new HashSet<>();
        for (Reach reach : reaches) {
            int last = reach.xs().length - 1;
            if (nearSeam(owner, other, reach.xs()[last], reach.zs()[last])) {
                near.add(reach);
            }
        }
        return near;
    }

    private static Set<Pond> nearSeamPonds(WindowKey owner, WindowKey other, List<Pond> ponds) {
        Set<Pond> near = new HashSet<>();
        for (Pond pond : ponds) {
            if (nearSeam(owner, other, pond.floorX(), pond.floorZ())) {
                near.add(pond);
            }
        }
        return near;
    }

    private static boolean nearSeam(WindowKey owner, WindowKey other, float x, float z) {
        if (!inSquare(owner, x, z)) {
            return false;
        }
        if (owner.wx() != other.wx()) {
            float seam = Math.max(owner.wx(), other.wx()) * WindowKey.SIZE;
            return Math.abs(x - seam) <= SEAM_DISTANCE;
        }
        float seam = Math.max(owner.wz(), other.wz()) * WindowKey.SIZE;
        return Math.abs(z - seam) <= SEAM_DISTANCE;
    }

    private record SeaColumns(int vertices, int mouths, int inlets) {
    }
}
