package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

public class WindowPlannerTest {
    private static final long SEED = 1337L;
    private static final int SEA_LEVEL = SyntheticTerrain.SEA_LEVEL;
    private static final WindowKey ORIGIN = new WindowKey(0, 0);
    private static final WindowKey LAND = new WindowKey(4, 4);
    private static final int SEAM_DISTANCE = 256;

    @Test
    public void zeroDensityRemovesLocalChannelsAndPonds() {
        HydrologySettings settings = settings(new IrisHydrology().setDensity(0D));
        WindowPlan plan = planner(settings, SyntheticTerrain.trunkValley()).plan(LAND, noRegions(LAND));
        assertTrue(plan.reaches().isEmpty());
        assertTrue(plan.ponds().isEmpty());
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
                    assertEquals("tributary trunk at z=" + z, floor, x, WindowPlanner.CELL);
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
        SyntheticTerrain terrain = SyntheticTerrain.fractal(SEED).withRainfall((x, z) -> 0.3D);
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
        RegionPlanner fractalPlanner = regionPlanner(fractal, SyntheticTerrain.fractal(2L));
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
        List<RegionPlan> regions = regionPlans(settings, SyntheticTerrain.fractal(SEED), LAND, new HashMap<>());
        assertTrue("chained cups", count(planner(settings, SyntheticTerrain.fractal(SEED)).compute(LAND, regions).allPonds(), PondKind.CHAINED) > 0);
        IrisHydrology unchained = new IrisHydrology();
        unchained.getPonds().getSeeded().setAlongChannels(false);
        HydrologySettings plain = settings(unchained);
        assertEquals(0, count(planner(plain, SyntheticTerrain.fractal(SEED)).compute(LAND, regions).allPonds(), PondKind.CHAINED));
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
                        assertTrue(pond + " outlet starts off its shore at " + outlet.xs()[0] + "," + outlet.zs()[0],
                                nearMask(pond.mask(), outlet.xs()[0], outlet.zs()[0], WindowPlanner.CELL));
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
            List<Pond> planned = call.getArgument(4);
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
        }).when(shaper).shape(any(), any(), any(), any(), any(), any(), anyLong());
        return new WindowPlanner(settings, new HeightLattice(terrain, WindowPlanner.CELL, false, 1 << 18),
                new HeightLattice(terrain, WindowPlanner.FINE_CELL, false, 1 << 21), new PondPlanner(settings), shaper);
    }

    @Test
    public void planIsDeterministic() throws Exception {
        HydrologySettings settings = settings(new IrisHydrology());
        List<RegionPlan> regions = regionPlans(settings, SyntheticTerrain.fractal(SEED), LAND, new HashMap<>());
        WindowPlanner planner = planner(settings, SyntheticTerrain.fractal(SEED));
        WindowPlan first = planner.plan(LAND, regions);
        assertFalse(first.reaches().isEmpty());
        assertFalse(first.ponds().isEmpty());
        WindowKey east = new WindowKey(LAND.wx() + 1, LAND.wz());
        planner.compute(east, regionPlans(settings, SyntheticTerrain.fractal(SEED), east, new HashMap<>()));
        assertEquals(first, planner.plan(LAND, regions));
        assertEquals(first, planner.plan(LAND, reversed(regions)));
        WindowPlanner fresh = planner(settings, SyntheticTerrain.fractal(SEED));
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

    private static HydrologySettings settings(IrisHydrology config) {
        return HydrologySettings.compile(config, SEED, SEA_LEVEL, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
    }

    private static WindowPlanner planner(HydrologySettings settings, SyntheticTerrain terrain) {
        return new WindowPlanner(settings, new HeightLattice(terrain, WindowPlanner.CELL, false, 1 << 18),
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
}
