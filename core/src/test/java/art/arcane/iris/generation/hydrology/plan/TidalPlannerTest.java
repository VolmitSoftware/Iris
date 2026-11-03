package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import art.arcane.iris.generation.hydrology.water.ShapedNetwork;
import art.arcane.iris.generation.hydrology.water.TidalPlanner;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TidalPlannerTest {
    private static final long SEED = 1337L;
    private static final int SEA = SyntheticTerrain.SEA_LEVEL;
    private static final double COAST_X = 300D;
    private static final ShapedNetwork NOTHING = new ShapedNetwork(List.of(), List.of());
    private static final List<WindowKey> COAST_WINDOWS = List.of(new WindowKey(0, 0), new WindowKey(0, 1), new WindowKey(0, -1), new WindowKey(1, 0));

    private static HydrologySettings settings() {
        return settings(new IrisHydrology());
    }

    private static HydrologySettings settings(IrisHydrology config) {
        return HydrologySettings.compile(config, SEED, SEA, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
    }

    private static Fixture planner(HydrologySettings settings, SyntheticTerrain terrain) {
        return new Fixture(settings, new HeightLattice(terrain, WindowPlanner.FINE_CELL, false, 1 << 21));
    }

    private static List<Reach> arms(Fixture planner) {
        return arms2(planner, COAST_WINDOWS.toArray(new WindowKey[0]));
    }

    private record Fixture(TidalPlanner tidal, HeightLattice fine) {
        Fixture(HydrologySettings settings, HeightLattice fine) {
            this(new TidalPlanner(settings, fine), fine);
        }

        List<Reach> plan(WindowKey window, ShapedNetwork network) {
            int span = 3 * WindowKey.SIZE / WindowPlanner.CELL;
            int originX = (window.wx() - 1) * WindowKey.SIZE;
            int originZ = (window.wz() - 1) * WindowKey.SIZE;
            HydrologySampleBuffer samples = new HydrologySampleBuffer(span * span);
            fine.fill(originX / WindowPlanner.FINE_CELL, originZ / WindowPlanner.FINE_CELL, span, span, WindowPlanner.CELL / WindowPlanner.FINE_CELL, samples);
            DemGrid natural = new DemGrid(originX, originZ, WindowPlanner.CELL, span, span);
            System.arraycopy(samples.height(), 0, natural.values(), 0, span * span);
            return tidal.plan(window, natural, samples, network);
        }
    }

    private static float length(Reach reach) {
        float length = 0f;
        for (int point = 1; point < reach.xs().length; point++) {
            length += (float) Math.hypot(reach.xs()[point] - reach.xs()[point - 1], reach.zs()[point] - reach.zs()[point - 1]);
        }
        return length;
    }

    private static int last(Reach reach) {
        return reach.xs().length - 1;
    }

    @Test
    public void lowCoastHoldsWholeTidalReachesAtSeaLevelThatDrainToTheSea() {
        HydrologySettings settings = settings();
        List<Reach> arms = arms(planner(settings, SyntheticTerrain.lowCoast(COAST_X)));
        assertTrue("arms on the low coast " + arms.size(), arms.size() >= 3);
        for (Reach arm : arms) {
            assertEquals(settings.tidalTier(), arm.tier());
            assertEquals(0L, arm.downstreamId());
            for (int point = 0; point < arm.xs().length; point++) {
                assertEquals(arm + " point " + point, ReachStyle.TIDAL, arm.style()[point]);
                assertEquals(arm + " point " + point, SEA, arm.grade()[point]);
            }
            assertTrue(arm + " starts inland of the coast", arm.xs()[0] > COAST_X + 16f);
            assertTrue(arm + " ends in the sea", arm.xs()[last(arm)] < COAST_X);
            assertTrue(arm + " length " + length(arm), length(arm) >= settings.underground().seaInlets().length().min());
            WindowPlanner.requireSeaConnectedHeads(new WindowPlan(WindowKey.containing((int) Math.floor(arm.xs()[last(arm)]), (int) Math.floor(arm.zs()[last(arm)])),
                    List.of(arm), List.of()), SEA);
        }
    }

    @Test
    public void armsBelongToTheWindowOfTheirMouth() {
        Fixture planner = planner(settings(), SyntheticTerrain.lowCoast(COAST_X));
        for (WindowKey window : COAST_WINDOWS) {
            for (Reach arm : planner.plan(window, NOTHING)) {
                assertEquals(window, WindowKey.containing((int) Math.floor(arm.xs()[last(arm)]), (int) Math.floor(arm.zs()[last(arm)])));
            }
        }
    }

    @Test
    public void tallCoastsHoldNoArms() {
        assertTrue(arms(planner(settings(), SyntheticTerrain.cliffCoast(COAST_X).lowered(-30D))).isEmpty());
    }

    @Test
    public void disabledAndZeroDensityPlanNoArms() {
        IrisHydrology disabled = new IrisHydrology();
        disabled.getUnderground().getSeaInlets().setEnabled(false);
        assertTrue(arms(planner(settings(disabled), SyntheticTerrain.lowCoast(COAST_X))).isEmpty());
        assertTrue(arms(planner(settings(new IrisHydrology().setDensity(0D)), SyntheticTerrain.lowCoast(COAST_X))).isEmpty());
    }

    @Test
    public void densityThinsSites() {
        List<Reach> full = arms(planner(settings(), SyntheticTerrain.lowCoast(COAST_X)));
        List<Reach> half = arms(planner(settings(new IrisHydrology().setDensity(0.5D)), SyntheticTerrain.lowCoast(COAST_X)));
        assertFalse(half.isEmpty());
        assertTrue(half.size() < full.size());
    }

    @Test
    public void streamBlockedGroundHoldsNoArms() {
        SyntheticTerrain blocked = SyntheticTerrain.lowCoast(COAST_X).withStreamsBlocked((x, z) -> true);
        assertTrue(arms(planner(settings(), blocked)).isEmpty());
    }

    @Test
    public void armsWalkAwayFromTheSeaOnAPeninsulaInsteadOfAlongTheShore() {
        SyntheticTerrain island = SyntheticTerrain.lowIsland(800D, 800D, 420D, 24D);
        for (Reach arm : arms2(planner(settings(), island), new WindowKey(0, 0), new WindowKey(1, 0), new WindowKey(0, 1), new WindowKey(1, 1))) {
            double mouthRadius = Math.hypot(arm.xs()[last(arm)] - 800D, arm.zs()[last(arm)] - 800D);
            double headRadius = Math.hypot(arm.xs()[0] - 800D, arm.zs()[0] - 800D);
            assertTrue(arm + " runs inland by at least half its length: " + (mouthRadius - headRadius) + " of " + length(arm),
                    mouthRadius - headRadius >= 0.5D * length(arm));
        }
    }

    private static List<Reach> arms2(Fixture planner, WindowKey... windows) {
        List<Reach> arms = new ArrayList<>();
        for (WindowKey window : windows) {
            arms.addAll(planner.plan(window, NOTHING));
        }
        return arms;
    }

    @Test
    public void headsNarrowToAtLeastAThirdOfTheMouth() {
        for (Reach arm : arms(planner(settings(), SyntheticTerrain.lowCoast(COAST_X)))) {
            float[] widths = arm.halfWidth();
            int points = widths.length;
            float head = widths[points / 8];
            float mouth = widths[points - 1];
            assertTrue(arm + " mouth " + mouth + " head " + head, mouth / Math.max(head, 2f) >= 3f);
        }
    }

    @Test
    public void armsStopBeforeGroundAboveTheBankLimit() {
        SyntheticTerrain ramp = SyntheticTerrain.lowCoast(COAST_X);
        HydrologySettings settings = settings();
        int limit = settings.underground().seaInlets().maximumBankHeight();
        for (Reach arm : arms(planner(settings, ramp))) {
            for (int point = 0; point < arm.xs().length; point++) {
                assertTrue(arm + " runs through ground " + ramp.natural(arm.xs()[point], arm.zs()[point]),
                        ramp.natural(arm.xs()[point], arm.zs()[point]) <= SEA + limit);
            }
        }
    }

    @Test
    public void armsNeverGradeBanksThroughGroundTooTallForTheBand() {
        HydrologySettings settings = settings();
        double wallX = COAST_X + 150D;
        SyntheticTerrain hill = SyntheticTerrain.lowCoast(COAST_X).rising(wallX, 2D);
        double graded = 1.5D * settings.underground().seaInlets().maximumBankHeight();
        List<Reach> arms = arms(planner(settings, hill));
        assertFalse("a low strip still holds arms", arms.isEmpty());
        for (Reach arm : arms) {
            for (int point = 0; point < arm.xs().length; point++) {
                double side = arm.halfWidth()[point] + settings.underground().seaInlets().bankBand();
                assertTrue(arm + " grades through " + hill.natural(arm.xs()[point] + side, arm.zs()[point]),
                        hill.natural(arm.xs()[point] + side, arm.zs()[point]) <= SEA + graded + 1D);
            }
            double ahead = arm.halfWidth()[0] + settings.underground().seaInlets().bankBand();
            assertTrue(arm + " ends against a wall", hill.natural(arm.xs()[0] + ahead, arm.zs()[0]) <= SEA + graded + 1D);
        }
    }

    @Test
    public void groundBelowTheSeaBesideAnArmCutsTheArmOffAtIt() {
        SyntheticTerrain plain = SyntheticTerrain.lowCoast(COAST_X);
        List<Reach> clean = arms(planner(settings(), plain));
        assertFalse(clean.isEmpty());
        Reach longest = clean.get(0);
        for (Reach arm : clean) {
            longest = length(arm) > length(longest) ? arm : longest;
        }
        double hollowX = longest.xs()[0] - 12D;
        double hollowZ = longest.zs()[0];
        SyntheticTerrain hollow = plain.withPit(hollowX, hollowZ, 10D, 40D).withLand((x, z) -> x > COAST_X);
        for (Reach arm : arms(planner(settings(), hollow))) {
            for (int point = 0; point < arm.xs().length; point++) {
                double beside = Math.hypot(arm.xs()[point] - hollowX, arm.zs()[point] - hollowZ);
                assertTrue(arm + " passes the hollow at " + beside, beside > arm.halfWidth()[point] + 4D);
            }
        }
    }

    @Test
    public void armsKeepClearOfRivers() {
        SyntheticTerrain coast = SyntheticTerrain.lowCoast(COAST_X);
        HydrologySettings settings = settings();
        Fixture planner = planner(settings, coast);
        List<Reach> free = arms(planner);
        assertFalse(free.isEmpty());
        Reach blocked = free.get(0);
        float z = blocked.zs()[blocked.xs().length / 2];
        int points = 40;
        float[] xs = new float[points];
        float[] zs = new float[points];
        int[] grade = new int[points];
        float[] halfWidth = new float[points];
        float[] depth = new float[points];
        ReachStyle[] style = new ReachStyle[points];
        for (int point = 0; point < points; point++) {
            xs[point] = 700f - point * 10f;
            zs[point] = z;
            grade[point] = SEA + 2;
            halfWidth[point] = 3f;
            depth[point] = 1f;
            style[point] = ReachStyle.RUN;
        }
        Reach river = new Reach(9L, settings.tiers().get(1), 0L, xs, zs, grade, halfWidth, depth, style, 30000f);
        ShapedNetwork network = new ShapedNetwork(List.of(river), List.of());
        WindowKey owner = WindowKey.containing((int) Math.floor(blocked.xs()[last(blocked)]), (int) Math.floor(blocked.zs()[last(blocked)]));
        for (Reach arm : planner.plan(owner, network)) {
            for (int point = 0; point < arm.xs().length; point++) {
                for (int other = 0; other < points; other++) {
                    double apart = Math.hypot(arm.xs()[point] - xs[other], arm.zs()[point] - zs[other]);
                    assertTrue(arm + " nears the river: " + apart, apart > arm.halfWidth()[point] + 3f);
                }
            }
        }
    }

    @Test
    public void armsKeepClearOfPonds() {
        SyntheticTerrain coast = SyntheticTerrain.lowCoast(COAST_X);
        Fixture planner = planner(settings(), coast);
        Reach blocked = arms(planner).get(0);
        int pondX = (int) blocked.xs()[blocked.xs().length / 2];
        int pondZ = (int) blocked.zs()[blocked.xs().length / 2];
        long[] bits = new long[1];
        Arrays.fill(bits, -1L);
        PondMask mask = new PondMask(pondX - 16, pondZ - 16, 8, 5, 5, bits);
        Pond pond = new Pond(3L, PondKind.NATURAL, SEA + 8, SEA + 4, pondX, pondZ, SEA + 8.5f, mask, 0L, 0, null, pondX, pondZ);
        WindowKey owner = WindowKey.containing((int) Math.floor(blocked.xs()[last(blocked)]), (int) Math.floor(blocked.zs()[last(blocked)]));
        for (Reach arm : planner.plan(owner, new ShapedNetwork(List.of(), List.of(pond)))) {
            for (int point = 0; point < arm.xs().length; point++) {
                assertFalse(arm + " enters the pond", mask.grownContains((int) arm.xs()[point], (int) arm.zs()[point]));
            }
        }
    }

    @Test
    public void armsNeverCrowdEachOtherAcrossWindowLines() {
        HydrologySettings settings = settings();
        List<Reach> arms = arms(planner(settings, SyntheticTerrain.lowCoast(COAST_X)));
        for (int a = 0; a < arms.size(); a++) {
            for (int b = a + 1; b < arms.size(); b++) {
                Reach first = arms.get(a);
                Reach second = arms.get(b);
                for (int p = 0; p < first.xs().length; p += 2) {
                    for (int q = 0; q < second.xs().length; q += 2) {
                        double apart = Math.hypot(first.xs()[p] - second.xs()[q], first.zs()[p] - second.zs()[q]);
                        assertTrue(first + " and " + second + " come within " + apart,
                                apart >= first.halfWidth()[p] + second.halfWidth()[q]);
                    }
                }
            }
        }
    }

    @Test
    public void armsAreIdenticalInAnyPlanningOrderAndAcrossThreads() throws Exception {
        HydrologySettings settings = settings();
        SyntheticTerrain coast = SyntheticTerrain.lowCoast(COAST_X);
        Map<WindowKey, List<Reach>> forward = new HashMap<>();
        Fixture first = planner(settings, coast);
        for (WindowKey window : COAST_WINDOWS) {
            forward.put(window, first.plan(window, NOTHING));
        }
        Map<WindowKey, List<Reach>> backward = new HashMap<>();
        Fixture second = planner(settings, coast);
        for (int index = COAST_WINDOWS.size() - 1; index >= 0; index--) {
            backward.put(COAST_WINDOWS.get(index), second.plan(COAST_WINDOWS.get(index), NOTHING));
        }
        assertEquals(forward, backward);
        Fixture third = planner(settings, coast);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Map<WindowKey, Future<List<Reach>>> futures = new HashMap<>();
            for (WindowKey window : COAST_WINDOWS) {
                futures.put(window, pool.submit(() -> third.plan(window, NOTHING)));
            }
            for (WindowKey window : COAST_WINDOWS) {
                assertEquals(forward.get(window), futures.get(window).get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void everyArmIsPlannedOnlyByTheWindowOfItsMouth() {
        Fixture planner = planner(settings(), SyntheticTerrain.lowCoast(COAST_X));
        Map<Long, WindowKey> owners = new HashMap<>();
        for (int wz = -2; wz <= 2; wz++) {
            for (int wx = -1; wx <= 2; wx++) {
                WindowKey window = new WindowKey(wx, wz);
                for (Reach arm : planner.plan(window, NOTHING)) {
                    assertEquals(arm + " planned twice", null, owners.put(arm.id(), window));
                }
            }
        }
        assertFalse(owners.isEmpty());
    }

    @Test
    public void territoriesKeepArmsApartWithoutKnowingWhatTheirNeighboursPlanned() {
        HydrologySettings settings = settings();
        SyntheticTerrain coast = SyntheticTerrain.lowCoast(COAST_X);
        Fixture planner = planner(settings, coast);
        WindowKey first = new WindowKey(0, 0);
        WindowKey second = new WindowKey(0, 1);
        List<Reach> alone = planner.plan(second, NOTHING);
        planner.plan(first, NOTHING);
        assertEquals("planning the neighbour first changes nothing", alone, planner(settings, coast).plan(second, NOTHING));
    }

    @Test
    public void armsFollowTheFunnelKnobs() {
        IrisHydrology wide = new IrisHydrology();
        wide.getUnderground().getSeaInlets().setMouthHalfWidth(new KList<>(20D, 20D))
                .setWidthVariation(0D);
        for (Reach arm : arms(planner(settings(wide), SyntheticTerrain.lowCoast(COAST_X)))) {
            assertEquals(arm.toString(), 20f, arm.halfWidth()[last(arm)], 0.01f);
        }
    }

    @Test
    public void depthShoalsToOneBlockAtBothEnds() {
        for (Reach arm : arms(planner(settings(), SyntheticTerrain.lowCoast(COAST_X)))) {
            assertEquals(1f, arm.depth()[0], 0.01f);
            assertEquals(1f, arm.depth()[last(arm)], 0.01f);
            float deepest = 0f;
            for (float depth : arm.depth()) {
                deepest = Math.max(deepest, depth);
            }
            assertTrue(arm + " deepest " + deepest, deepest >= 2f && deepest <= 5f);
        }
    }
}
