package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.LakeCandidate;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.PondPlanner;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachShaper;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.Test;

import java.util.ArrayList;
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

public class RegionPlannerTest {
    private static final long SEED = 1337L;
    private static final int SEA_LEVEL = SyntheticTerrain.SEA_LEVEL;
    private static final RegionKey ORIGIN = new RegionKey(0, 0);
    private static final RegionKey EAST = new RegionKey(1, 0);

    @Test
    public void zeroDensityRemovesMajorChannelsAndLakes() {
        HydrologySettings settings = HydrologySettings.compile(new IrisHydrology().setDensity(0D), SEED, SEA_LEVEL,
                SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
        assertTrue(planner(settings, SyntheticTerrain.coneIsland(2048D, 2048D, 1700D, 240D, 6)).plan(ORIGIN).majorReaches().isEmpty());
        assertTrue(planner(settings, SyntheticTerrain.crater(2048D, 2048D, 150, 700D)).plan(ORIGIN).lakes().isEmpty());
    }

    @Test
    public void coneIslandMajorReachesEndInOcean() {
        HydrologySettings settings = settings();
        SyntheticTerrain terrain = SyntheticTerrain.coneIsland(2048D, 2048D, 1700D, 240D, 6);
        RegionComputation computation = planner(settings, terrain).compute(ORIGIN);
        List<Reach> owned = computation.owned().majorReaches();
        assertFalse(owned.isEmpty());
        assertEquals(computation.allReaches(), owned);
        HydrologyTier major = settings.majorTier();
        Map<Long, Reach> byId = byId(computation.allReaches());
        Set<Long> mouths = new HashSet<>();
        for (Reach reach : owned) {
            assertTrue(reach + " catchment", reach.catchment() >= major.minCatchment());
            assertEquals(major, reach.tier());
            Reach terminal = reach;
            for (int hops = 0; terminal.downstreamId() != 0L; hops++) {
                assertTrue("downstream loop from " + reach, hops < owned.size());
                terminal = byId.get(terminal.downstreamId());
                assertNotNull(terminal);
            }
            int last = terminal.xs().length - 1;
            assertEquals(terminal.toString(), ReachStyle.MOUTH, terminal.style()[last]);
            assertEquals(SEA_LEVEL, terminal.grade()[last]);
            assertTrue(terrain.base(terminal.xs()[last], terminal.zs()[last]) <= SEA_LEVEL);
            mouths.add(terminal.id());
        }
        assertEquals("one major mouth per valley", 6, mouths.size());
    }

    @Test
    public void craterBecomesOneLakeBelowSpill() {
        HydrologySettings settings = settings();
        int spill = 150;
        RegionPlan plan = planner(settings, SyntheticTerrain.crater(2048D, 2048D, spill, 700D)).plan(ORIGIN);
        assertEquals("lakes " + plan.lakes(), 1, plan.lakes().size());
        Pond lake = plan.lakes().get(0);
        assertEquals(PondKind.LAKE, lake.kind());
        assertTrue(lake.mask().contains(2048, 2048));
        assertEquals(spill, lake.spillHeight(), 0f);
        assertTrue("level " + lake.level(), lake.level() >= spill - 3 && lake.level() <= spill - 1);
        assertTrue(lake.floor() < lake.level());
    }

    @Test
    public void steepOutletLakeSurvives() {
        HydrologySettings settings = settings();
        for (double cliffRun : new double[]{4D, 8D, 16D, 64D}) {
            RegionPlan plan = planner(settings, SyntheticTerrain.steepOutlet(2048D, 2048D, cliffRun, 30D)).plan(ORIGIN);
            assertEquals("cliff run " + cliffRun + " lakes " + plan.lakes(), 1, plan.lakes().size());
            Pond lake = plan.lakes().get(0);
            assertTrue(lake.mask().contains(2048, 2048));
            float lip = 118f - 0.002f * 594f;
            assertTrue("cliff run " + cliffRun + " spill " + lake.spillHeight(), lake.spillHeight() > lip - 1f && lake.spillHeight() <= lip + 1e-3f);
            int spill = (int) Math.floor(lake.spillHeight());
            assertTrue("cliff run " + cliffRun + " level " + lake.level(), lake.level() >= spill - 3 && lake.level() <= spill - 1);
        }
    }

    @Test
    public void subLatticeNotchLowersSpill() {
        HydrologySettings settings = settings();
        for (double depth : new double[]{0D, 3D, 7D}) {
            RegionPlan plan = planner(settings, SyntheticTerrain.notchedBowl(2048D, 2048D, depth)).plan(ORIGIN);
            assertEquals("notch " + depth + " lakes " + plan.lakes(), 1, plan.lakes().size());
            Pond lake = plan.lakes().get(0);
            if (depth == 0D) {
                assertTrue("spill " + lake.spillHeight(), lake.spillHeight() >= 125f && lake.spillHeight() < 126f);
            } else {
                assertEquals("notch " + depth, 125f - (float) depth, lake.spillHeight(), 0f);
            }
            int spill = (int) Math.floor(lake.spillHeight());
            assertTrue("notch " + depth + " level " + lake.level(), lake.level() >= spill - 3 && lake.level() <= spill - 1);
        }
    }

    @Test
    public void seamLakesResolveToOneOwner() {
        HydrologySettings settings = settings();
        RegionComputation west = planner(settings, SyntheticTerrain.seamLakes()).compute(ORIGIN);
        RegionComputation east = planner(settings, SyntheticTerrain.seamLakes()).compute(EAST);
        List<LakeCandidate> westCandidates = candidatesAnchoredIn(ORIGIN, west.allLakes());
        List<LakeCandidate> eastCandidates = candidatesAnchoredIn(EAST, west.allLakes());
        assertEquals("candidates " + westCandidates, 1, westCandidates.size());
        assertEquals("candidates " + eastCandidates, 1, eastCandidates.size());
        assertTrue(westCandidates.get(0).near(eastCandidates.get(0)));
        List<Pond> lakes = List.of(west.owned().lakes(), east.owned().lakes()).stream().flatMap(List::stream).toList();
        for (int[] centre : new int[][]{{3520, 2048}, {4380, 2048}}) {
            assertEquals("lakes over " + centre[0] + " " + lakes, 1, lakes.stream().filter(lake -> lake.mask().contains(centre[0], centre[1])).count());
        }
        for (int a = 0; a < lakes.size(); a++) {
            for (int b = a + 1; b < lakes.size(); b++) {
                assertFalse(lakes.get(a) + " overlaps " + lakes.get(b), lakes.get(a).mask().overlaps(lakes.get(b).mask()));
            }
        }
        for (Pond lake : lakes) {
            assertTrue(lake + " stays below the gap crest", lake.level() < 118);
        }
    }

    @Test
    public void enclosedSeaIsNotALake() {
        SyntheticTerrain ring = SyntheticTerrain.boxedFractal(SEED, 2046D, 2046D, 1024D);
        List<LakeCandidate> candidates = planner(settings(), ring).compute(ORIGIN).allLakes();
        List<Pond> lakes = new PondPlanner(settings()).lakes(candidates, new HeightLattice(ring, RegionPlanner.FINE_CELL, false, 1 << 20));
        assertFalse("ring candidates", candidates.isEmpty());
        assertTrue("the ring encloses sea", ring.natural(2353D, 2814D) < SEA_LEVEL);
        for (Pond lake : lakes) {
            PondMask mask = lake.mask();
            for (int j = 0; j < mask.depth(); j++) {
                for (int i = 0; i < mask.width(); i++) {
                    int x = mask.originX() + i * mask.step();
                    int z = mask.originZ() + j * mask.step();
                    if (mask.contains(x, z)) {
                        assertTrue(lake + " floods sea at " + x + "," + z, ring.natural(x, z) > SEA_LEVEL);
                    }
                }
            }
        }
    }

    @Test
    public void measuredLakeStaysWithinClampedBound() {
        long[][] spreading = {{1L, 2L, 4L}, {2L, -3L, -2L}, {3L, 2L, 1L}, {1337L, -2L, 1L}};
        int lakes = 0;
        for (long[] sample : spreading) {
            HydrologySettings settings = HydrologySettings.compile(new IrisHydrology(), sample[0], SEA_LEVEL, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
            RegionComputation computation = planner(settings, SyntheticTerrain.fractal(sample[0])).compute(new RegionKey((int) sample[1], (int) sample[2]));
            for (Pond lake : computation.owned().lakes()) {
                LakeCandidate candidate = candidateAt(computation.allLakes(), lake.floorX(), lake.floorZ());
                PondMask mask = lake.mask();
                for (int j = 0; j < mask.depth(); j++) {
                    for (int i = 0; i < mask.width(); i++) {
                        int x = mask.originX() + i * mask.step();
                        int z = mask.originZ() + j * mask.step();
                        if (mask.contains(x, z)) {
                            assertTrue("seed " + sample[0] + " lake " + lake.floorX() + "," + lake.floorZ() + " node " + x + "," + z,
                                    withinCells(candidate, x, z, LakeCandidate.BOUND_CELLS * RegionPlanner.CELL));
                        }
                    }
                }
                lakes++;
            }
        }
        assertTrue("lakes checked " + lakes, lakes >= 8);
    }

    @Test
    public void seamOverlappingLakesResolveToOne() {
        HydrologySettings settings = HydrologySettings.compile(new IrisHydrology(), 2L, SEA_LEVEL, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
        RegionPlanner planner = planner(settings, SyntheticTerrain.fractal(2L));
        List<Pond> lakes = new ArrayList<>();
        lakes.addAll(planner.plan(new RegionKey(1, -1)).lakes());
        lakes.addAll(planner.plan(new RegionKey(1, -2)).lakes());
        assertTrue("lakes " + lakes.size(), lakes.size() >= 4);
        for (int a = 0; a < lakes.size(); a++) {
            for (int b = a + 1; b < lakes.size(); b++) {
                assertFalse(lakes.get(a) + " overlaps " + lakes.get(b), lakes.get(a).mask().overlaps(lakes.get(b).mask()));
            }
        }
        int touching = 2 * LakeCandidate.BOUND_CELLS + 1;
        assertTrue(cellCandidate(0, 0).near(cellCandidate(touching, 0)));
        assertFalse(cellCandidate(0, 0).near(cellCandidate(touching + 1, 0)));
    }

    @Test
    public void oversizeLakeCandidateIsBreached() {
        RegionComputation fits = planner(settings(), SyntheticTerrain.valleyLake(5600D)).compute(ORIGIN);
        assertTrue(fits.owned().lakes().stream().anyMatch(lake -> lake.mask().contains(4500, 2048)));
        RegionComputation oversize = planner(settings(), SyntheticTerrain.valleyLake(6400D)).compute(ORIGIN);
        for (LakeCandidate candidate : oversize.allLakes()) {
            int[] box = candidate.cellBox();
            assertTrue(candidate + " span", Math.max(box[2] - box[0] + 1, box[3] - box[1] + 1) * RegionPlanner.CELL <= LakeCandidate.MAX_EXTENT);
        }
        assertFalse("the long valley is breached", oversize.owned().lakes().stream().anyMatch(lake -> lake.mask().contains(4500, 2048)));
    }

    @Test
    public void basinAboveLakeMaxAreaIsBreached() {
        HydrologySettings settings = settings();
        SyntheticTerrain caldera = SyntheticTerrain.crater(2048D, 2048D, 150, 1600D);
        RegionComputation breached = planner(settings, caldera).compute(ORIGIN);
        assertTrue("lakes " + breached.allLakes(), breached.allLakes().isEmpty());
        Map<Long, Reach> byId = byId(breached.allReaches());
        boolean drained = false;
        for (Reach reach : breached.allReaches()) {
            if (passesWithin(reach, 2048D, 2048D, 256D)) {
                Reach terminal = reach;
                for (int hops = 0; terminal != null && terminal.downstreamId() != 0L && hops < byId.size(); hops++) {
                    terminal = byId.get(terminal.downstreamId());
                }
                assertNotNull(terminal);
                drained |= terminal.style()[terminal.xs().length - 1] == ReachStyle.MOUTH;
            }
        }
        assertTrue("major reach from the breached floor to the sea", drained);
        SyntheticTerrain bowl = SyntheticTerrain.crater(2048D, 2048D, 150, 900D);
        RegionPlan flooded = planner(settings, bowl).plan(ORIGIN);
        assertEquals("lakes " + flooded.lakes(), 1, flooded.lakes().size());
        assertTrue(flooded.lakes().get(0).mask().contains(2048, 2048));
        IrisHydrology tight = new IrisHydrology();
        tight.getLakes().setMaxArea(1 << 20);
        HydrologySettings capped = HydrologySettings.compile(tight, SEED, SEA_LEVEL, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
        assertTrue("a bowl above the area cap is breached", planner(capped, bowl).plan(ORIGIN).lakes().isEmpty());
    }

    @Test
    public void neighbouringRegionsAgreeOnOwnedFeatures() {
        HydrologySettings settings = settings();
        RegionComputation west = planner(settings, SyntheticTerrain.ridgeValley()).compute(ORIGIN);
        RegionComputation east = planner(settings, SyntheticTerrain.ridgeValley()).compute(EAST);
        for (RegionComputation computation : List.of(west, east)) {
            RegionPlan owned = computation.owned();
            assertFalse(owned.key() + " reaches", owned.majorReaches().isEmpty());
            assertFalse(owned.key() + " lakes", owned.lakes().isEmpty());
            assertEquals(owned.majorReaches().size(), new HashSet<>(owned.majorReaches()).size());
            assertEquals(new HashSet<>(owned.majorReaches()), reachesAnchoredIn(owned.key(), computation.allReaches()));
            assertEquals(owned.lakes(), measure(settings, SyntheticTerrain.ridgeValley(), owned.key(), computation.allLakes()));
        }
        assertEquals(new HashSet<>(west.owned().majorReaches()), reachesAnchoredIn(ORIGIN, east.allReaches()));
        assertEquals(new HashSet<>(east.owned().majorReaches()), reachesAnchoredIn(EAST, west.allReaches()));
        assertEquals(new HashSet<>(candidatesAnchoredIn(ORIGIN, west.allLakes())), new HashSet<>(candidatesAnchoredIn(ORIGIN, east.allLakes())));
        assertEquals(new HashSet<>(candidatesAnchoredIn(EAST, east.allLakes())), new HashSet<>(candidatesAnchoredIn(EAST, west.allLakes())));
        assertEquals(west.owned().lakes(), measure(settings, SyntheticTerrain.ridgeValley(), ORIGIN, east.allLakes()));
        assertEquals(east.owned().lakes(), measure(settings, SyntheticTerrain.ridgeValley(), EAST, west.allLakes()));
        Set<Long> westIds = new HashSet<>();
        for (Reach reach : west.owned().majorReaches()) {
            westIds.add(reach.id());
        }
        int crossings = 0;
        for (Reach reach : east.owned().majorReaches()) {
            crossings += westIds.contains(reach.downstreamId()) ? 1 : 0;
        }
        assertTrue("reaches linking across the seam " + crossings, crossings > 0);
    }

    @Test
    public void regionComputeSampleBudget() {
        SyntheticTerrain terrain = SyntheticTerrain.fractal(SEED);
        RegionComputation computation = planner(settings(), terrain).compute(ORIGIN);
        List<LakeCandidate> owned = candidatesAnchoredIn(ORIGIN, computation.allLakes());
        List<LakeCandidate> measured = RegionPlanner.measuredCandidates(ORIGIN, computation.allLakes());
        assertFalse(owned.isEmpty());
        assertTrue(measured.containsAll(owned));
        assertTrue("measured " + measured.size() + " of " + computation.allLakes().size(), computation.allLakes().size() > measured.size());
        long budget = 0L;
        for (LakeCandidate candidate : measured) {
            int[] box = candidate.cellBox();
            long width = 4L * (box[2] - box[0]) + 21L;
            long depth = 4L * (box[3] - box[1]) + 21L;
            budget += width * depth + 64L * 64L + 1L;
        }
        long span = 3L * RegionKey.SIZE / RegionPlanner.CELL;
        assertEquals(span * span, terrain.baseSamples());
        assertTrue("natural samples " + terrain.naturalSamples() + " budget " + budget,
                terrain.naturalSamples() > 0L && terrain.naturalSamples() <= budget);
    }

    @Test
    public void planIsDeterministic() throws Exception {
        HydrologySettings settings = settings();
        RegionPlanner planner = planner(settings, SyntheticTerrain.fractal(SEED));
        RegionPlan first = planner.plan(ORIGIN);
        assertFalse(first.majorReaches().isEmpty());
        assertFalse(first.lakes().isEmpty());
        planner.compute(EAST);
        assertEquals(first, planner.plan(ORIGIN));
        RegionPlanner fresh = planner(settings, SyntheticTerrain.fractal(SEED));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<RegionPlan> left = pool.submit(() -> fresh.plan(ORIGIN));
            Future<RegionPlan> right = pool.submit(() -> fresh.plan(ORIGIN));
            assertEquals(first, left.get());
            assertEquals(first, right.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private static HydrologySettings settings() {
        return HydrologySettings.compile(new IrisHydrology(), SEED, SEA_LEVEL, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
    }

    private static RegionPlanner planner(HydrologySettings settings, SyntheticTerrain terrain) {
        return new RegionPlanner(settings, new HeightLattice(terrain, RegionPlanner.CELL, true, 1 << 18), new HeightLattice(terrain, RegionPlanner.FINE_CELL, false, 1 << 20),
                new PondPlanner(settings), new ReachShaper(settings));
    }

    private static Map<Long, Reach> byId(List<Reach> reaches) {
        Map<Long, Reach> byId = new HashMap<>();
        for (Reach reach : reaches) {
            byId.put(reach.id(), reach);
        }
        return byId;
    }

    private static boolean passesWithin(Reach reach, double x, double z, double distance) {
        for (int point = 0; point < reach.xs().length; point++) {
            if (Math.hypot(reach.xs()[point] - x, reach.zs()[point] - z) <= distance) {
                return true;
            }
        }
        return false;
    }

    private static Set<Reach> reachesAnchoredIn(RegionKey key, List<Reach> reaches) {
        Set<Reach> anchored = new HashSet<>();
        for (Reach reach : reaches) {
            int last = reach.xs().length - 1;
            if (key.equals(RegionKey.containing((int) Math.floor(reach.xs()[last]), (int) Math.floor(reach.zs()[last])))) {
                anchored.add(reach);
            }
        }
        return anchored;
    }

    private static List<LakeCandidate> candidatesAnchoredIn(RegionKey key, List<LakeCandidate> candidates) {
        List<LakeCandidate> anchored = new ArrayList<>();
        for (LakeCandidate candidate : candidates) {
            if (key.equals(RegionKey.containing(candidate.floorX(), candidate.floorZ()))) {
                anchored.add(candidate);
            }
        }
        return anchored;
    }

    private static LakeCandidate candidateAt(List<LakeCandidate> candidates, int floorX, int floorZ) {
        for (LakeCandidate candidate : candidates) {
            if (candidate.floorX() == floorX && candidate.floorZ() == floorZ) {
                return candidate;
            }
        }
        throw new AssertionError("no candidate floored at " + floorX + "," + floorZ);
    }

    private static boolean withinCells(LakeCandidate candidate, int x, int z, int margin) {
        int reach = margin + candidate.cell() / 2;
        for (long packed : candidate.cells()) {
            int cellX = (int) (packed >> 32) * candidate.cell();
            int cellZ = (int) packed * candidate.cell();
            if (Math.abs(x - cellX) <= reach && Math.abs(z - cellZ) <= reach) {
                return true;
            }
        }
        return false;
    }

    private static LakeCandidate cellCandidate(int cellX, int cellZ) {
        int cell = RegionPlanner.CELL;
        return new LakeCandidate(cellX * cell, cellZ * cell, 100f, cellX * cell, cellZ * cell, 110f, cell, cell,
                new long[]{LakeCandidate.pack(cellX, cellZ)});
    }

    private static List<Pond> measure(HydrologySettings settings, SyntheticTerrain terrain, RegionKey key, List<LakeCandidate> candidates) {
        List<Pond> lakes = new PondPlanner(settings).lakes(RegionPlanner.measuredCandidates(key, candidates),
                new HeightLattice(terrain, RegionPlanner.FINE_CELL, false, 1 << 20));
        List<Pond> owned = new ArrayList<>();
        for (Pond lake : lakes) {
            if (key.equals(RegionKey.containing(lake.floorX(), lake.floorZ()))) {
                owned.add(lake);
            }
        }
        return owned;
    }
}
