package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyTier;
import art.arcane.iris.generation.hydrology.grid.Basin;
import art.arcane.iris.generation.hydrology.grid.BasinExtractor;
import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.grid.ChannelHeads;
import art.arcane.iris.generation.hydrology.grid.D8Router;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.FlowAccumulator;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.grid.HydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.grid.PriorityFlood;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.raster.PondChannelSettlement;
import art.arcane.iris.testsupport.DeltaCoast;
import art.arcane.iris.testsupport.SeaCells;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ReachShaperTest {
    private static final long SEED = 1337L;
    private static final WindowKey WINDOW = new WindowKey(0, 0);
    private static final int SEA_LEVEL = 40;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;
    private static final int CELL = 8;
    private static final int FINE = 4;
    private static final int STATION = 4;
    private static final int VALLEY_Z = 256;
    private static final int HANGING_X = 256;
    private static final float VALLEY_THRESHOLD = 5000f;
    private static final int STEP_X = 196;
    private static final int WINDOW_MIN_X = 140;
    private static final int WINDOW_MAX_X = 250;
    private static final int OUTLET_POND_LEVEL = SEA_LEVEL + 3;
    private static final double SLIVER_X = 134D;
    private static final byte ALLOW_ALL = HydrologySampleBuffer.ALLOW_STREAMS | HydrologySampleBuffer.ALLOW_PONDS;

    @Test
    public void gradeNeverRisesDownstream() {
        Random random = new Random(SEED);
        HydrologySettings cascading = settings(config -> {
        });
        HydrologySettings spreading = settings(ReachShaperTest::noCascades);
        int[] totals = new int[6];
        int cascadingSteps = 0;
        int spreadSteps = 0;
        for (int trial = 0; trial < 200; trial++) {
            Network network = network(cascading, randomTerrain(random), 40, 40, 2000f, trial % 4 == 0);
            ShapedNetwork stepped = shape(cascading, network);
            ShapedNetwork spread = shape(spreading, network);
            assertNetworkInvariants("trial " + trial, stepped, totals);
            assertNetworkInvariants("trial " + trial + " spread", spread, totals);
            Map<Long, Reach> steppedById = byId(stepped.reaches());
            for (Reach reach : spread.reaches()) {
                Reach twin = steppedById.get(reach.id());
                assertNotNull(twin);
                int fall = reach.tier().waterfallMinimumDrop();
                int junction = twin.downstreamId() == 0L ? Integer.MIN_VALUE : twin.grade()[twin.grade().length - 1];
                assertTrue("trial " + trial + " " + reach + " gained a waterfall against " + Arrays.toString(twin.grade()),
                        dropsAbove(reach, junction, fall, Integer.MAX_VALUE) <= dropsAbove(twin, Integer.MIN_VALUE, fall, Integer.MAX_VALUE));
                spreadSteps += dropsAbove(reach, junction, 2, Integer.MAX_VALUE);
                cascadingSteps += dropsAbove(twin, junction, 2, Integer.MAX_VALUE);
            }
        }
        assertTrue("reaches " + totals[0], totals[0] > 4000);
        assertTrue("drops " + totals[1], totals[1] > 4000);
        assertTrue("links " + totals[2], totals[2] > 1000);
        assertTrue("mouth points " + totals[3], totals[3] > 100);
        assertTrue("pond outlets " + totals[4], totals[4] > 40);
        assertTrue("visible drops spread " + spreadSteps + " kept " + cascadingSteps, 11 * spreadSteps < 10 * cascadingSteps);
    }

    @Test
    public void confluenceEndpointsAreExact() {
        HydrologySettings settings = settings(config -> {
        });
        Network network = network(settings, fractal(SEED), 64, 64, 2000f, false);
        ShapedNetwork shaped = shape(settings, network);
        Map<Long, Reach> byId = byId(shaped.reaches());
        Map<Long, Integer> incoming = new HashMap<>();
        for (Reach reach : shaped.reaches()) {
            if (reach.downstreamId() == 0L) {
                continue;
            }
            Reach downstream = byId.get(reach.downstreamId());
            assertNotNull(downstream);
            int last = reach.xs().length - 1;
            assertEquals(Float.floatToIntBits(downstream.xs()[0]), Float.floatToIntBits(reach.xs()[last]));
            assertEquals(Float.floatToIntBits(downstream.zs()[0]), Float.floatToIntBits(reach.zs()[last]));
            assertEquals(reach + " into " + downstream, downstream.grade()[0], reach.grade()[last]);
            assertEquals(downstream.xs()[0], (float) Math.round(downstream.xs()[0]), 0f);
            assertEquals(0, Math.floorMod(Math.round(downstream.xs()[0]), CELL));
            assertEquals(0, Math.floorMod(Math.round(downstream.zs()[0]), CELL));
            incoming.merge(downstream.id(), 1, Integer::sum);
        }
        int confluences = 0;
        for (int count : incoming.values()) {
            confluences += count >= 2 ? 1 : 0;
        }
        assertTrue("links " + incoming.size(), incoming.size() >= 10);
        assertTrue("confluences " + confluences, confluences >= 3);
    }

    @Test
    public void feedersMeetTheConfluenceLevelWhereverTheyTouchTheReceivingWater() {
        HydrologySettings settings = settings(config -> {
        });
        int confluences = 0;
        int pooled = 0;
        int inside = 0;
        int falls = 0;
        for (long seed : new long[]{SEED, SEED + 5L, SEED + 9L}) {
            ShapedNetwork shaped = shape(settings, network(settings, fractal(seed), 64, 64, 2000f, false));
            Map<Long, Reach> byId = byId(shaped.reaches());
            Map<Long, List<Reach>> feeders = feeders(shaped.reaches());
            int joins = 0;
            for (Map.Entry<Long, List<Reach>> entry : feeders.entrySet()) {
                Reach downstream = byId.get(entry.getKey());
                List<Reach> siblings = entry.getValue();
                joins += siblings.size() >= 2 ? 1 : 0;
                for (Reach feeder : siblings) {
                    List<ConfluenceGrader.Footprint> receiving = receiving(downstream, siblings, feeder);
                    int level = downstream.grade()[0];
                    for (int point = feeder.xs().length - 1; point >= 0; point--) {
                        double clearance = clearance(receiving, feeder.xs()[point], feeder.zs()[point]);
                        if (clearance > feeder.halfWidth()[point] + 1f) {
                            break;
                        }
                        String at = "seed " + seed + " " + feeder + " into " + downstream + " at point " + point + " with clearance " + clearance;
                        int grade = feeder.grade()[point];
                        boolean heldStart = point == 0 && feeders.containsKey(feeder.id());
                        if (grade != level && (heldStart || clearance > 0D && grade - level > ConfluenceGrader.MARGIN_LOWERING)) {
                            ReachStyle style = feeder.style()[point + 1];
                            assertTrue(at + " stands " + (grade - level) + " above the junction and drops into it as " + style,
                                    style == ReachStyle.CASCADE || style == ReachStyle.WATERFALL);
                            falls++;
                            break;
                        }
                        assertEquals(at, level, grade);
                        pooled++;
                        inside += clearance <= 0D ? 1 : 0;
                    }
                }
            }
            assertTrue("seed " + seed + " confluences " + joins, joins >= 3);
            confluences += joins;
        }
        assertTrue("pooled points " + pooled + " at " + confluences + " confluences", pooled > 3 * confluences);
        assertTrue("pooled points inside the receiving channel " + inside + " at " + confluences + " confluences", inside > 3 * confluences);
        assertTrue("falls into the junction from beside the bank or a held start " + falls, falls > 0);
    }

    @Test
    public void hangingTributaryFallsAtTheReceivingBank() {
        HydrologySettings settings = settings(config -> {
        });
        ShapedNetwork shaped = shape(settings, network(settings, hangingValley(), 64, 64, 2000f, false));
        Map<Long, Reach> byId = byId(shaped.reaches());
        List<Reach> hanging = new ArrayList<>();
        for (Reach reach : shaped.reaches()) {
            if (reach.downstreamId() != 0L && Math.abs(reach.xs()[0] - HANGING_X) <= 32f && reach.zs()[0] < VALLEY_Z - 64f) {
                hanging.add(reach);
            }
        }
        assertEquals("tributaries draining the plateau " + hanging, 1, hanging.size());
        Reach tributary = hanging.get(0);
        Reach downstream = byId.get(tributary.downstreamId());
        assertEquals(tributary.toString(), settings.tiers().get(0), tributary.tier());
        assertEquals(downstream.toString(), settings.tiers().get(1), downstream.tier());
        ConfluenceGrader.Footprint receiving = footprint(downstream);
        int waterfalls = 0;
        for (int point = 0; point < tributary.xs().length; point++) {
            if (tributary.style()[point] == ReachStyle.WATERFALL) {
                waterfalls++;
                double edge = ConfluenceGrader.edgeDistance(receiving, tributary.xs()[point - 1], tributary.zs()[point - 1]);
                assertTrue(tributary + " brink before point " + point + " is inside the receiving channel, " + edge + " from its edge", edge > 0D);
            }
        }
        assertEquals(tributary + " waterfalls", 1, waterfalls);
        int inside = 0;
        for (int point = 0; point < tributary.xs().length; point++) {
            if (ConfluenceGrader.edgeDistance(receiving, tributary.xs()[point], tributary.zs()[point]) <= 0D) {
                inside++;
                assertEquals(tributary + " water inside the receiving channel at point " + point, downstream.grade()[0], tributary.grade()[point]);
            }
        }
        assertTrue(tributary + " points inside the receiving channel " + inside, inside > 0);
    }

    @Test
    public void cascadeStepsSurviveWhenTierCascades() {
        List<Drop> cascading = drops(settings(config -> {
        }), steppedValley(3));
        assertEquals("drops " + cascading, 1, cascading.size());
        assertEquals(3, cascading.get(0).size());
        assertEquals(ReachStyle.CASCADE, cascading.get(0).style());

        List<Drop> spread = drops(settings(ReachShaperTest::noCascades), steppedValley(3));
        assertTrue("drops " + spread, spread.size() >= 3);
        int total = 0;
        for (Drop drop : spread) {
            assertEquals("drops " + spread, 1, drop.size());
            assertEquals(ReachStyle.RIFFLE, drop.style());
            total += drop.size();
        }
        assertEquals(3, total);
    }

    @Test
    public void dropAtWaterfallMinimumBecomesWaterfall() {
        HydrologySettings cascading = settings(config -> {
        });
        int minimum = cascading.tiers().get(1).waterfallMinimumDrop();
        List<Drop> waterfall = drops(cascading, steppedValley(minimum));
        assertEquals("drops " + waterfall, 1, waterfall.size());
        assertEquals(minimum, waterfall.get(0).size());
        assertEquals(ReachStyle.WATERFALL, waterfall.get(0).style());

        List<Drop> below = drops(cascading, steppedValley(minimum - 1));
        assertEquals("drops " + below, 1, below.size());
        assertEquals(minimum - 1, below.get(0).size());
        assertEquals(ReachStyle.CASCADE, below.get(0).style());

        List<Drop> unspread = drops(settings(ReachShaperTest::noCascades), steppedValley(minimum));
        assertEquals("drops " + unspread, 1, unspread.size());
        assertEquals(minimum, unspread.get(0).size());
        assertEquals(ReachStyle.WATERFALL, unspread.get(0).style());
    }

    @Test
    public void spreadLowersJunctionStarts() {
        HydrologyTier tier = settings(ReachShaperTest::noCascades).tiers().get(0);
        ReachDraft left = draft(tier, 11f, 11f, 11f);
        ReachDraft right = draft(tier, 11f, 11f, 11f);
        ReachDraft trunk = draft(tier, 11f, 8f, 8f, 8f, 8f);
        left.downstream = 2;
        right.downstream = 2;
        trunk.incoming = new int[]{0, 1};
        ReachGrader.grade(List.of(left, right, trunk), new int[]{0, 1, 2}, 0, 6, noPonds(3), SEED);
        for (ReachDraft draft : List.of(left, right, trunk)) {
            for (int index = 1; index < draft.grade.length; index++) {
                int drop = draft.grade[index - 1] - draft.grade[index];
                assertTrue(Arrays.toString(draft.grade), drop == 0 || drop == 1);
            }
        }
        assertEquals(trunk.grade[0], left.last());
        assertEquals(trunk.grade[0], right.last());
        assertEquals(7, trunk.grade[1]);
    }

    @Test
    public void chainedPondsFallStrictlyExceptWhereBothSitAtTheSea() {
        HydrologyTier tier = settings(config -> {
        }).tiers().get(0);
        for (int upper : new int[]{SEA_LEVEL, SEA_LEVEL + 10}) {
            ReachDraft link = draft(tier, upper + 3f, upper + 3f, upper + 3f);
            int[] levels = ReachGrader.grade(List.of(link), new int[]{0}, SEA_LEVEL, 6,
                    new ReachGrader.PondChain(new int[]{upper, upper}, new boolean[2], new int[]{1}, new int[]{0, -1}), SEED);
            assertEquals(upper, levels[0]);
            assertEquals("pond fed by a pond at " + upper, upper == SEA_LEVEL ? SEA_LEVEL : upper - 1, levels[1]);
        }
    }

    @Test
    public void spreadNeverCreatesWaterfalls() {
        HydrologyTier tier = settings(ReachShaperTest::noCascades).tiers().get(0);
        float[] ground = new float[24];
        for (int index = 0; index < ground.length; index++) {
            ground[index] = 100f - 2f * index;
        }
        ReachDraft steep = draft(tier, ground);
        ReachGrader.grade(List.of(steep), new int[]{0}, 0, 6, noPonds(1), SEED);
        int[] grade = steep.grade;
        for (int index = 1; index < grade.length; index++) {
            int drop = grade[index - 1] - grade[index];
            assertTrue(Arrays.toString(grade), drop >= 0 && drop < tier.waterfallMinimumDrop());
        }
        assertTrue(Arrays.toString(grade), grade[0] > (int) ground[0] - tier.inset() - tier.waterfallMinimumDrop());
        assertEquals((int) ground[ground.length - 1] - tier.inset(), grade[grade.length - 1]);
    }

    @Test
    public void steepRunsGroupIntoStepPools() {
        HydrologyTier tier = settings(config -> {
        }).tiers().get(0);
        float[] ground = new float[32];
        for (int index = 0; index < ground.length; index++) {
            ground[index] = 200f - 4f * index;
        }
        int[] baseline = graded(withSteps(tier, false, 2), ground);
        for (int index = 1; index < baseline.length; index++) {
            assertEquals("ungrouped " + Arrays.toString(baseline), 4, baseline[index - 1] - baseline[index]);
        }
        int[] grade = graded(tier, ground);
        String label = Arrays.toString(grade);
        int last = grade.length - 1;
        assertEquals(label, baseline[0], grade[0]);
        assertEquals(label, baseline[last], grade[last]);
        int drops = 0;
        int pools = 0;
        int level = 1;
        for (int index = 1; index < grade.length; index++) {
            int drop = grade[index - 1] - grade[index];
            assertTrue(label + " at " + index, drop >= 0 && drop <= StepPools.stepMaxDrop(tier));
            assertTrue(label + " above the ungrouped grade at " + index, grade[index] <= baseline[index]);
            if (drop > 0) {
                drops++;
                pools += STATION * level >= 8 ? 1 : 0;
                level = 1;
            } else {
                level++;
            }
        }
        pools += STATION * level >= 8 ? 1 : 0;
        assertTrue(label + " pools " + pools, pools >= 3);
        assertTrue(label + " drops " + drops, drops <= Math.ceil(STATION * last / StepPools.POOL_MIN));

        int[] spread = graded(withSteps(tier, false, tier.waterfallMinimumDrop()), ground);
        for (int index = 1; index < spread.length; index++) {
            assertTrue("spread " + Arrays.toString(spread), spread[index - 1] - spread[index] < tier.waterfallMinimumDrop());
        }
    }

    @Test
    public void cliffKeepsOneFallWithAFlatPlungeReach() {
        HydrologyTier tier = settings(config -> {
        }).tiers().get(0);
        float[] ground = new float[32];
        for (int index = 0; index < ground.length; index++) {
            ground[index] = index < 12 ? 160f : 140f - (index - 12);
        }
        int[] grade = graded(tier, ground);
        String label = Arrays.toString(grade);
        int falls = 0;
        int foot = -1;
        for (int index = 1; index < grade.length; index++) {
            int drop = grade[index - 1] - grade[index];
            assertTrue(label, drop >= 0);
            if (drop >= tier.waterfallMinimumDrop()) {
                falls++;
                foot = index;
            }
        }
        assertEquals(label, 1, falls);
        int end = foot;
        while (end + 1 < grade.length && grade[end + 1] == grade[foot]) {
            end++;
        }
        assertTrue(label + " level below the fall from " + foot + " to " + end, STATION * (end - foot) >= StepPools.PLUNGE_MIN);

        HydrologySettings settings = settings(config -> {
        });
        DoubleBinaryOperator cliff = steppedValley(12);
        float brink = brinkMidpoint(shape(settings, network(settings, cliff, 64, 64, VALLEY_THRESHOLD, false)));
        assertTrue("brink midpoint " + brink, brink >= STEP_X - 3);
        assertTrue("brink midpoint " + brink, brink <= STEP_X);
    }

    @Test
    public void cliffBetweenLatticeNodesKeepsWaterBelowTheGround() {
        HydrologySettings settings = settings(config -> {
        });
        int[] brinks = {190, 190, 190, 194, 194, 194, 194};
        for (int edge = 0; edge < brinks.length; edge++) {
            int cliffX = STEP_X - 3 + edge;
            DoubleBinaryOperator cliff = (x, z) -> (x < 2 * CELL ? 140D : x < cliffX ? 120D : 108D) + 0.1D * Math.abs(z - VALLEY_Z);
            ShapedNetwork shaped = shape(settings, network(settings, cliff, 64, 64, VALLEY_THRESHOLD, false));
            for (Reach reach : shaped.reaches()) {
                for (int index = 0; index < reach.xs().length; index++) {
                    double natural = cliff.applyAsDouble(reach.xs()[index], reach.zs()[index]);
                    assertTrue("cliff " + cliffX + " " + reach + " at " + index + " x " + reach.xs()[index] + " grade " + reach.grade()[index]
                            + " natural " + natural, reach.grade()[index] <= natural);
                }
            }
            assertEquals("cliff " + cliffX + " brink midpoint", brinks[edge], brinkMidpoint(shaped), 0.5f);
        }
    }

    @Test
    public void cliffAtAnyStationPhaseKeepsWaterBelowTheGround() {
        HydrologySettings settings = settings(config -> {
        });
        for (int origin = 0; origin < FINE; origin++) {
            for (double edge = STEP_X - 2D; edge <= STEP_X + 2D; edge += 0.5D) {
                double cliffX = edge;
                DoubleBinaryOperator cliff = (x, z) -> (x < 2 * CELL ? 140D : x < cliffX ? 120D : 108D) + 0.1D * Math.abs(z - VALLEY_Z);
                ShapedNetwork shaped = shape(settings, network(settings, cliff, origin, 64, 64, CELL, VALLEY_THRESHOLD, false));
                for (Reach reach : shaped.reaches()) {
                    for (int index = 0; index < reach.xs().length; index++) {
                        double natural = cliff.applyAsDouble(reach.xs()[index], reach.zs()[index]);
                        assertTrue("origin " + origin + " cliff " + cliffX + " " + reach + " at " + index + " x " + reach.xs()[index]
                                + " grade " + reach.grade()[index] + " natural " + natural, reach.grade()[index] <= natural);
                    }
                }
                float brink = brinkMidpoint(shaped);
                assertTrue("origin " + origin + " cliff " + cliffX + " brink midpoint " + brink, brink >= cliffX - 5D && brink <= cliffX);
            }
        }
    }

    @Test
    public void stepsBelowTheWaterfallMinimumKeepTheFullGroundRead() {
        HydrologySettings settings = settings(config -> {
        });
        DoubleBinaryOperator forward = steppedValley(5);
        DoubleBinaryOperator mirrored = (x, z) -> forward.applyAsDouble(504D - x, z);
        float before = STEP_X - firstAtOrBelow(shape(settings, network(settings, forward, 64, 64, VALLEY_THRESHOLD, false)), 113, true);
        float mirroredBefore = firstAtOrBelow(shape(settings, network(settings, mirrored, 64, 64, VALLEY_THRESHOLD, false)), 113, false) - (504 - STEP_X);
        assertTrue("the full read starts the lower level 5.5 before the step, the trimmed read 1.5: " + before, before >= 4f);
        assertEquals("a step below the waterfall minimum reads the same both ways", before, mirroredBefore, 1e-3f);
    }

    @Test
    public void steepPlanarChannelsCurveWithoutLongStraightRunsOrKinks() {
        HydrologySettings settings = settings(config -> {
        });
        double east = Math.cos(Math.toRadians(30D));
        double south = Math.sin(Math.toRadians(30D));
        DoubleBinaryOperator terrain = (x, z) -> 300D - 0.6D * (x * east + z * south) + 1.5D * valueNoise(SEED, x / 24D, z / 24D);
        ShapedNetwork shaped = shape(settings, network(settings, terrain, 64, 64, 2000f, false));
        int measured = 0;
        double longestRun = 0D;
        double sharpest = 0D;
        for (Reach reach : shaped.reaches()) {
            assertNoFold("steep", reach);
            for (int index = 0; index < reach.xs().length; index++) {
                double natural = Math.max(SEA_LEVEL, terrain.applyAsDouble(reach.xs()[index], reach.zs()[index]));
                assertTrue(reach + " at " + index + " grade " + reach.grade()[index] + " natural " + natural, reach.grade()[index] <= natural);
            }
            float[] distance = ReachShaper.distances(reach.xs(), reach.zs());
            if (distance[distance.length - 1] < 64f) {
                continue;
            }
            measured++;
            double[] turns = turnsPerStation(reach, distance, 2 * STATION);
            double run = 0D;
            for (double turn : turns) {
                sharpest = Math.max(sharpest, turn);
                run = turn < 1.5D ? run + STATION : 0D;
                longestRun = Math.max(longestRun, run);
            }
        }
        String metric = "reaches " + measured + " longest straight run " + longestRun + " sharpest turn " + sharpest;
        assertTrue(metric, measured >= 5);
        assertTrue(metric, longestRun <= 40D);
        assertTrue(metric, sharpest <= 30D);
    }

    private static double[] turnsPerStation(Reach reach, float[] distance, float excluded) {
        double length = distance[distance.length - 1] - 2D * excluded;
        int stations = (int) Math.floor(length / STATION) + 1;
        double[] x = new double[stations];
        double[] z = new double[stations];
        int segment = 0;
        for (int station = 0; station < stations; station++) {
            double along = excluded + station * (double) STATION;
            while (segment < distance.length - 2 && distance[segment + 1] < along) {
                segment++;
            }
            double t = (along - distance[segment]) / Math.max(1e-6D, distance[segment + 1] - distance[segment]);
            x[station] = lerp(reach.xs()[segment], reach.xs()[segment + 1], t);
            z[station] = lerp(reach.zs()[segment], reach.zs()[segment + 1], t);
        }
        double[] turns = new double[Math.max(0, stations - 2)];
        for (int station = 0; station < turns.length; station++) {
            double first = Math.atan2(z[station + 1] - z[station], x[station + 1] - x[station]);
            double second = Math.atan2(z[station + 2] - z[station + 1], x[station + 2] - x[station + 1]);
            turns[station] = Math.abs(Math.toDegrees(Math.IEEEremainder(second - first, 2D * Math.PI)));
        }
        return turns;
    }

    @Test
    public void shapeIsTranslationInvariant() {
        HydrologySettings settings = settings(config -> {
        });
        int shift = 512;
        DoubleBinaryOperator eastward = (x, z) -> 240D - 0.3D * z + 16D * Math.abs(Math.sin(Math.PI * (x + 12D * Math.sin(z / 40D)) / 64D));
        DoubleBinaryOperator southward = (x, z) -> eastward.applyAsDouble(z, x);
        assertInteriorReachesIgnoreTheGridOrigin(shape(settings, network(settings, eastward, 0, 0, 128, 64, CELL, 1200f, false)),
                shape(settings, network(settings, eastward, shift, 0, 128, 64, CELL, 1200f, false)), true);
        assertInteriorReachesIgnoreTheGridOrigin(shape(settings, network(settings, southward, 0, 0, 64, 128, CELL, 1200f, false)),
                shape(settings, network(settings, southward, 0, shift, 64, 128, CELL, 1200f, false)), false);

        DoubleBinaryOperator moved = (x, z) -> eastward.applyAsDouble(x - shift, z - shift);
        List<Reach> here = shape(settings, network(settings, eastward, 0, 0, 64, 64, CELL, 1200f, false)).reaches();
        List<Reach> there = shape(settings, network(settings, moved, shift, shift, 64, 64, CELL, 1200f, false)).reaches();
        assertEquals(here.size(), there.size());
        assertEquals(reachEnds(here, 0), reachEnds(there, shift));
        int bends = 0;
        for (int index = 0; index < here.size(); index++) {
            bends += Arrays.equals(here.get(index).zs(), offset(there.get(index).zs(), -shift)) ? 0 : 1;
        }
        assertTrue("meander phases are keyed on world position, reaches that moved " + bends + " of " + here.size(), 2 * bends > here.size());
    }

    private static void assertInteriorReachesIgnoreTheGridOrigin(ShapedNetwork first, ShapedNetwork second, boolean acrossX) {
        Map<Long, Reach> others = byId(second.reaches());
        int compared = 0;
        for (Reach reach : first.reaches()) {
            float[] across = acrossX ? reach.xs() : reach.zs();
            if (minimum(across) >= 576f && maximum(across) <= 960f) {
                assertEquals(reach, others.get(reach.id()));
                compared++;
            }
        }
        assertTrue("reaches compared " + compared, compared >= 5);
    }

    private static List<String> reachEnds(List<Reach> reaches, int shift) {
        List<String> ends = new ArrayList<>();
        for (Reach reach : reaches) {
            int last = reach.xs().length - 1;
            ends.add((reach.xs()[0] - shift) + "," + (reach.zs()[0] - shift) + " to " + (reach.xs()[last] - shift) + "," + (reach.zs()[last] - shift));
        }
        return ends;
    }

    private static float[] offset(float[] values, float by) {
        float[] moved = values.clone();
        for (int index = 0; index < moved.length; index++) {
            moved[index] += by;
        }
        return moved;
    }

    @Test
    public void cliffBrinkStaysAtTheEdgeOnEverySeed() {
        for (long seed = 1L; seed <= 24L; seed++) {
            HydrologySettings settings = HydrologySettings.compile(new IrisHydrology(), seed, SEA_LEVEL, MIN_Y, MAX_Y);
            for (int edge = STEP_X - 3; edge <= STEP_X + 3; edge++) {
                int cliffX = edge;
                DoubleBinaryOperator cliff = (x, z) -> (x < 2 * CELL ? 140D : x < cliffX ? 120D : 108D) + 0.1D * Math.abs(z - VALLEY_Z);
                ShapedNetwork shaped = shape(settings, network(settings, cliff, 64, 64, VALLEY_THRESHOLD, false), seed);
                String label = "seed " + seed + " cliff " + cliffX;
                for (Reach reach : shaped.reaches()) {
                    for (int index = 0; index < reach.xs().length; index++) {
                        double natural = cliff.applyAsDouble(reach.xs()[index], reach.zs()[index]);
                        assertTrue(label + " " + reach + " at " + index + " grade " + reach.grade()[index] + " natural " + natural,
                                reach.grade()[index] <= natural);
                    }
                }
                float brink = brinkMidpoint(shaped);
                assertTrue(label + " brink midpoint " + brink, brink >= cliffX - 5 && brink <= cliffX);
                assertTrue(label + " brink midpoint " + brink, cliffX != STEP_X || brink >= STEP_X - 3);
                double tilt = brinkTilt(shaped);
                assertTrue(label + " chord tilt at the fall " + tilt, tilt <= 25D);
            }
        }
    }

    private static double brinkTilt(ShapedNetwork shaped) {
        for (Reach reach : shaped.reaches()) {
            for (int index = 1; index < reach.xs().length; index++) {
                if (reach.xs()[index] >= WINDOW_MIN_X && reach.xs()[index] <= WINDOW_MAX_X
                        && reach.grade()[index - 1] - reach.grade()[index] >= reach.tier().waterfallMinimumDrop()) {
                    return Math.toDegrees(Math.atan2(Math.abs(reach.zs()[index] - reach.zs()[index - 1]), Math.abs(reach.xs()[index] - reach.xs()[index - 1])));
                }
            }
        }
        throw new AssertionError("no fall in the step window");
    }

    private static float brinkMidpoint(ShapedNetwork shaped) {
        int falls = 0;
        float midpoint = Float.NaN;
        for (Reach reach : shaped.reaches()) {
            for (int index = 1; index < reach.xs().length; index++) {
                if (reach.xs()[index] < WINDOW_MIN_X || reach.xs()[index] > WINDOW_MAX_X) {
                    continue;
                }
                if (reach.grade()[index - 1] - reach.grade()[index] >= reach.tier().waterfallMinimumDrop()) {
                    falls++;
                    midpoint = (reach.xs()[index - 1] + reach.xs()[index]) / 2f;
                }
            }
        }
        assertEquals("falls in the step window", 1, falls);
        return midpoint;
    }

    @Test
    public void gentlerRunsHoldPoolsMeasuredAlongTheChannel() {
        HydrologyTier tier = settings(config -> {
        }).tiers().get(2);
        float[] ground = new float[48];
        for (int index = 0; index < ground.length; index++) {
            ground[index] = 200f - 2f * index;
        }
        int[] grade = graded(tier, ground);
        int pools = 0;
        int first = 0;
        for (int index = 1; index <= grade.length; index++) {
            if (index == grade.length || grade[index] < grade[first]) {
                pools += STATION * (index - 1 - first) >= 8 ? 1 : 0;
                first = index;
            }
        }
        assertTrue(Arrays.toString(grade) + " pools " + pools, pools >= 3);
    }

    @Test
    public void closeFallsStaySeparateWhenTheLowerLandsOnTheEndLevel() {
        HydrologyTier tier = settings(config -> {
        }).tiers().get(0);
        float[] ground = new float[24];
        for (int index = 0; index < ground.length; index++) {
            ground[index] = index < 10 ? 160f : index < 12 ? 140f : 130f;
        }
        int[] baseline = graded(withSteps(tier, false, 2), ground);
        int[] grade = graded(tier, ground);
        String label = Arrays.toString(baseline) + " grouped " + Arrays.toString(grade);
        assertEquals(label, baseline[10], grade[10]);
        assertEquals(label, baseline[11], grade[11]);
        assertEquals(label, baseline[12], grade[12]);
        assertEquals(label, baseline[23], grade[12]);
    }

    @Test
    public void tallFallsRollOverAtTheBrinkAndKeepEveryFall() {
        HydrologyTier tier = settings(config -> {
        }).tiers().get(0);
        float[] ground = new float[32];
        for (int index = 0; index < ground.length; index++) {
            ground[index] = index < 10 ? 160f : index < 20 ? 140f : 133f;
        }
        int[] baseline = graded(withSteps(tier, false, 2), ground);
        int[] grade = graded(tier, ground);
        String label = Arrays.toString(baseline) + " grouped " + Arrays.toString(grade);
        assertEquals(label, baseline[9] - StepPools.ROLLOVER_DROP, grade[9]);
        assertEquals(label, baseline[0], grade[0]);
        assertEquals(label, baseline[10], grade[10]);
        assertEquals(label, baseline[20], grade[20]);
        assertEquals(label, baseline[19], grade[19]);
    }

    @Test
    public void stepPoolsNeverMoveDropsIntoPondOutletZones() {
        DoubleBinaryOperator terrain = (x, z) -> (x <= 344D ? 150D - 0.05D * x : 150D - 0.05D * 344D - 0.5D * (x - 344D))
                + 0.1D * Math.abs(z - VALLEY_Z);
        HydrologySettings grouping = settings(config -> {
        });
        HydrologySettings ungrouped = settings(ReachShaperTest::ungrouped);
        Network open = network(grouping, terrain, 64, 64, VALLEY_THRESHOLD, false);
        int level = (int) Math.floor(terrain.applyAsDouble(340, VALLEY_Z)) + 1;
        Pond drained = new Pond(11L, PondKind.NATURAL, level, level - 3, 288, VALLEY_Z, level + 1f, rectangle(300, VALLEY_Z - 12, 11, 7),
                0L, 0, "water", 320, VALLEY_Z);
        Network network = new Network(open.grid(), open.receivers(), open.accumulation(), open.channels(), List.of(drained), open.fine());
        ShapedNetwork grouped = shape(grouping, network);
        ShapedNetwork plain = shape(ungrouped, network);
        Reach outlet = byId(grouped.reaches()).get(grouped.ponds().get(0).outletReachId());
        Reach baseline = byId(plain.reaches()).get(plain.ponds().get(0).outletReachId());
        assertNotNull(outlet);
        assertNotNull(baseline);
        assertTrue(Arrays.equals(baseline.xs(), outlet.xs()) && Arrays.equals(baseline.zs(), outlet.zs()));
        String label = Arrays.toString(baseline.grade()) + " grouped " + Arrays.toString(outlet.grade());
        float protectedStart = Math.max(ReachShaper.OUTLET_LENGTH, 2f * (outlet.halfWidth()[0] + 1f));
        float along = 0f;
        boolean zone = true;
        int lowered = 0;
        for (int point = 0; point < outlet.grade().length; point++) {
            if (point > 0) {
                along += (float) Math.hypot(outlet.xs()[point] - outlet.xs()[point - 1], outlet.zs()[point] - outlet.zs()[point - 1]);
            }
            zone = zone && (along <= protectedStart || baseline.style()[point] == ReachStyle.POND_OUTLET);
            if (zone) {
                assertEquals(label + " inside the outlet zone at " + point, baseline.grade()[point], outlet.grade()[point]);
            } else {
                assertTrue(label + " above the ungrouped grade at " + point, outlet.grade()[point] <= baseline.grade()[point]);
                lowered += outlet.grade()[point] < baseline.grade()[point] ? 1 : 0;
            }
        }
        assertTrue(label + " grouped below the outlet zone", lowered > 0);
        List<Pond> groupedPonds = PondChannelSettlement.settle(WINDOW, grouped, SEA_LEVEL).ponds();
        List<Pond> plainPonds = PondChannelSettlement.settle(WINDOW, plain, SEA_LEVEL).ponds();
        assertEquals(plainPonds.size(), groupedPonds.size());
        for (int pond = 0; pond < plainPonds.size(); pond++) {
            assertEquals("pond " + pond, plainPonds.get(pond).level(), groupedPonds.get(pond).level());
        }
        assertEquals(level, groupedPonds.get(0).level());
    }

    @Test
    public void widthFollowsSqrtCatchment() {
        HydrologySettings settings = settings(config -> config.setTiers(new KList<>(new IrisHydrologyTier()
                .setHalfWidth(new KList<>(0.5D, 64D)))));
        HydrologyTier tier = settings.tiers().get(0);
        Network network = network(settings, fractal(SEED + 1L), 64, 64, 2000f, false);
        ShapedNetwork shaped = shape(settings, network);
        float[] quadrupled = network.accumulation().clone();
        for (int cell = 0; cell < quadrupled.length; cell++) {
            quadrupled[cell] *= 4f;
        }
        ShapedNetwork wider = new ReachShaper(settings).shape(WINDOW, network.grid(), network.receivers(), quadrupled, network.channels(),
                SeaCells.atOrBelow(network.grid(), SEA_LEVEL), network.ponds(), network.fine(), SEED);
        Map<Long, Reach> widerById = byId(wider.reaches());
        Map<Long, Integer> incoming = incoming(shaped.reaches());
        int compared = 0;
        int tapered = 0;
        for (Reach reach : shaped.reaches()) {
            int last = reach.xs().length - 1;
            for (int index = 0; index <= last; index++) {
                assertEquals(tier.depth(reach.halfWidth()[index]), reach.depth()[index], 0f);
            }
            boolean head = !incoming.containsKey(reach.id());
            if (firstBeyond(reach, head ? 24f : 16f) < 0) {
                continue;
            }
            float expected = tier.minHalfWidth() + tier.widthFactor() * (float) Math.sqrt(reach.catchment() / 2500f);
            assertEquals(reach.toString(), expected, reach.halfWidth()[last], 1e-3f);
            Reach scaled = widerById.get(reach.id());
            assertNotNull(scaled);
            assertEquals(4f * reach.catchment(), scaled.catchment(), 1e-2f * reach.catchment());
            float growth = (scaled.halfWidth()[scaled.xs().length - 1] - tier.minHalfWidth()) / (reach.halfWidth()[last] - tier.minHalfWidth());
            assertEquals(reach.toString(), 2f, growth, 1e-3f);
            compared++;
            if (head) {
                assertTrue(reach.toString(), reach.halfWidth()[0] < 0.5f * reach.halfWidth()[firstBeyond(reach, 24f)]);
                tapered++;
            }
        }
        assertTrue("compared " + compared, compared >= 20);
        assertTrue("tapered heads " + tapered, tapered >= 5);
    }

    @Test
    public void pondLevelIsFloorForUpstreamGrade() {
        HydrologySettings settings = settings(config -> {
            config.getPonds().getSeeded().setEnabled(false);
            config.getPonds().setFreeboard(new KList<>(1, 1));
        });
        DoubleBinaryOperator terrain = bowlValley(new int[]{250});
        Network network = network(settings, terrain, 64, 64, VALLEY_THRESHOLD, true);
        assertEquals(1, network.ponds().size());
        Pond pond = network.ponds().get(0);
        ShapedNetwork shaped = shape(settings, network);
        Map<Long, Reach> byId = byId(shaped.reaches());
        List<Reach> inflows = new ArrayList<>();
        for (Reach reach : shaped.reaches()) {
            if (endsIn(reach, pond)) {
                inflows.add(reach);
            }
        }
        assertFalse(inflows.isEmpty());
        int binding = 0;
        for (Reach inflow : inflows) {
            assertEquals(0L, inflow.downstreamId());
            assertEquals(pond.level(), inflow.grade()[inflow.grade().length - 1]);
            for (Reach reach : upstreamOf(inflow, shaped.reaches(), byId)) {
                for (int index = 0; index < reach.grade().length; index++) {
                    assertTrue(reach + " at " + index, reach.grade()[index] >= pond.level());
                    int natural = (int) Math.floor(terrain.applyAsDouble(reach.xs()[index], reach.zs()[index])) - reach.tier().inset();
                    binding += natural < pond.level() && !pond.mask().contains(Math.round(reach.xs()[index]), Math.round(reach.zs()[index])) ? 1 : 0;
                }
            }
        }
        assertTrue("points where the pond level lifted the grade " + binding, binding > 0);
    }

    @Test
    public void pondOutletLinksToNextPond() {
        HydrologySettings settings = settings(config -> config.getPonds().getSeeded().setEnabled(false));
        Network network = network(settings, bowlValley(new int[]{150, 350}), 64, 64, VALLEY_THRESHOLD, true);
        assertEquals(2, network.ponds().size());
        ShapedNetwork shaped = shape(settings, network);
        assertEquals(2, shaped.ponds().size());
        Pond upper = shaped.ponds().get(0).floorX() < shaped.ponds().get(1).floorX() ? shaped.ponds().get(0) : shaped.ponds().get(1);
        Pond lower = upper == shaped.ponds().get(0) ? shaped.ponds().get(1) : shaped.ponds().get(0);
        assertTrue(lower.level() < upper.level());
        assertNotEquals(0L, upper.outletReachId());
        Map<Long, Reach> byId = byId(shaped.reaches());
        Reach outlet = byId.get(upper.outletReachId());
        assertNotNull(outlet);
        assertTrue(outlet.grade()[0] <= upper.level());
        assertEquals(ReachStyle.POND_OUTLET, outlet.style()[0]);
        assertTrue("outlet leads in from " + outlet.xs()[0] + "," + outlet.zs()[0],
                upper.mask().contains(Math.round(outlet.xs()[0]), Math.round(outlet.zs()[0])));
        assertTrue("outlet exits " + outlet.xs()[1] + "," + outlet.zs()[1] + " spill " + upper.spillX() + "," + upper.spillZ(),
                Math.hypot(outlet.xs()[1] - upper.spillX(), outlet.zs()[1] - upper.spillZ()) <= 1.5D * CELL);
        Reach reach = outlet;
        for (int hops = 0; reach.downstreamId() != 0L && hops < 64; hops++) {
            reach = byId.get(reach.downstreamId());
            assertNotNull(reach);
        }
        assertTrue(reach + " ends outside the lower pond", endsIn(reach, lower));
        assertEquals(lower.level(), reach.grade()[reach.grade().length - 1]);
        Pond original = network.ponds().get(0).id() == upper.id() ? network.ponds().get(0) : network.ponds().get(1);
        assertEquals(original.withOutletReachId(upper.outletReachId()), upper);
    }

    @Test
    public void pondOutletWaterReachesItsPondAtThePondLevel() {
        HydrologySettings settings = settings(config -> {
        });
        DemGrid flat = outletGrid();
        int[] receivers = new int[flat.values().length];
        float[] accumulation = new float[receivers.length];
        BitSet channels = new BitSet(receivers.length);
        outletRow(flat, receivers, accumulation, channels);
        Pond pond = new Pond(31L, PondKind.NATURAL, 99, 94, 104, 64, 100f, disk(96, 64, 5), 0L, 0, "water", 96, 64);
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> Math.hypot(x - 96D, z - 64D) <= 5D ? 94D
                : x <= 104D ? 100D : 100D - 0.5D * (x - 104D)), FINE, false, 1 << 16);
        ShapedNetwork shaped = new ReachShaper(settings).shape(WINDOW, flat, receivers, accumulation, channels,
                SeaCells.atOrBelow(flat, SEA_LEVEL), List.of(pond), fine, SEED);
        Pond shapedPond = shaped.ponds().get(0);
        assertEquals(99, shapedPond.level());
        assertOutletLeavesAtThePondLevel("lip outlet", shapedPond, byId(shaped.reaches()).get(shapedPond.outletReachId()));
        assertEquals(99, PondChannelSettlement.settle(WINDOW, shaped, SEA_LEVEL).ponds().get(0).level());
    }

    @Test
    public void pondOutletAtAJunctionLowersOnceAndStaysConnected() {
        HydrologySettings settings = settings(config -> {
        });
        DemGrid flat = outletGrid();
        int[] receivers = new int[flat.values().length];
        float[] accumulation = new float[receivers.length];
        BitSet channels = new BitSet(receivers.length);
        outletRow(flat, receivers, accumulation, channels);
        for (int j = flat.depth() - 1; j > 8; j--) {
            flow(flat, receivers, accumulation, channels, 13, j, 13, j - 1, 1500f + 10f * (flat.depth() - j));
        }
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> {
            double radius = Math.hypot(x - 96D, z - 64D);
            if (radius <= 8D) {
                return 93D + radius;
            }
            if (Math.abs(x - 104D) <= 3D && z >= 68D) {
                return 95D;
            }
            return x <= 104D ? 100D : 100D - 0.5D * (x - 104D);
        }), FINE, false, 1 << 16);
        PondPlanner planner = new PondPlanner(settings);
        ReachShaper shaper = new ReachShaper(settings);
        List<Pond> water = List.of(new Pond(41L, PondKind.NATURAL, 99, 93, 104, 64, 100f, disk(96, 64, 5), 0L, 0, "water", 96, 64));
        ShapedNetwork network = PondChannelSettlement.settle(WINDOW, shaper.shape(WINDOW, flat, receivers, accumulation, channels,
                SeaCells.atOrBelow(flat, SEA_LEVEL), water, fine, SEED), SEA_LEVEL);
        int lowerings = 0;
        while (network.ponds().get(0).level() < water.get(0).level()) {
            lowerings++;
            assertTrue("pond still lowering after " + lowerings + " passes", lowerings <= 2);
            Pond reflooded = planner.reflood(flat, fine, CupSet.EMPTY, network.ponds().get(0));
            assertNotNull("reflooded pond at " + network.ponds().get(0).level(), reflooded);
            water = List.of(reflooded.withOutletReachId(0L));
            network = PondChannelSettlement.settle(WINDOW, shaper.shape(WINDOW, flat, receivers, accumulation, channels,
                    SeaCells.atOrBelow(flat, SEA_LEVEL), water, fine, SEED), SEA_LEVEL);
        }
        Pond settled = network.ponds().get(0);
        Map<Long, Reach> byId = byId(network.reaches());
        Reach outlet = byId.get(settled.outletReachId());
        assertOutletLeavesAtThePondLevel("junction outlet", settled, outlet);
        Reach tributary = null;
        for (Reach reach : network.reaches()) {
            if (reach.downstreamId() == outlet.id()) {
                tributary = reach;
            }
        }
        assertNotNull("tributary joining the outlet", tributary);
        assertEquals("settlements down to the tributary", 1, lowerings);
        assertEquals(tributary.grade()[tributary.grade().length - 1], settled.level());
    }

    @Test
    public void fallbackOutletRunningTowardItsPondKeepsItsStartAndHoldsThePondLevel() {
        HydrologySettings settings = settings(config -> {
        });
        DemGrid flat = outletGrid();
        int[] receivers = new int[flat.values().length];
        Arrays.fill(receivers, -1);
        float[] accumulation = new float[receivers.length];
        BitSet channels = new BitSet(receivers.length);
        flow(flat, receivers, accumulation, channels, 4, 4, 4, 5, 1600f);
        for (int i = 0; i < 4; i++) {
            flow(flat, receivers, accumulation, channels, i, 5, i + 1, 5, 1500f + 10f * i);
        }
        for (int i = 4; i < flat.width(); i++) {
            flow(flat, receivers, accumulation, channels, i, 5, i + 1 < flat.width() ? i + 1 : -1, 5, 3200f + 10f * i);
        }
        PondMask mask = disk(32, 43, 2);
        assertFalse("no DEM node inside the pond", mask.contains(32, 40) || mask.contains(32, 48));
        Pond pond = new Pond(51L, PondKind.NATURAL, 99, 94, 32, 34, 100f, mask, 0L, 0, "water", 32, 43);
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> 100D), FINE, false, 1 << 16);
        ShapedNetwork shaped = new ReachShaper(settings).shape(WINDOW, flat, receivers, accumulation, channels,
                SeaCells.atOrBelow(flat, SEA_LEVEL), List.of(pond), fine, SEED);
        Pond shapedPond = shaped.ponds().get(0);
        Reach outlet = byId(shaped.reaches()).get(shapedPond.outletReachId());
        assertNotNull(outlet);
        assertNoFold("fallback outlet", outlet);
        assertEquals("the outlet keeps the fallback exit as its start", 32f, outlet.xs()[0], 0f);
        assertEquals(32f, outlet.zs()[0], 0f);
        for (int point = 0; point < outlet.grade().length; point++) {
            assertEquals("held grade at " + point, shapedPond.level(), outlet.grade()[point]);
        }
        assertOutletTouchesAtThePondLevel("fallback outlet", shapedPond, outlet);
        assertEquals(99, PondChannelSettlement.settle(WINDOW, shaped, SEA_LEVEL).ponds().get(0).level());
    }

    @Test
    public void outletHeldAlongItsPondsShoreIsStyledAsThePondOutlet() {
        HydrologySettings settings = settings(config -> {
        });
        DemGrid flat = outletGrid();
        int[] receivers = new int[flat.values().length];
        Arrays.fill(receivers, -1);
        float[] accumulation = new float[receivers.length];
        BitSet channels = new BitSet(receivers.length);
        for (int i = 9; i < flat.width(); i++) {
            flow(flat, receivers, accumulation, channels, i, 8, i + 1 < flat.width() ? i + 1 : -1, 8, 1600f + 10f * i);
        }
        PondMask shore = rectangle(72, 52, 18, 3);
        Pond pond = new Pond(81L, PondKind.NATURAL, 99, 94, 72, 64, 100f, shore, 0L, 0, "water", 104, 56);
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> shore.contains((int) x, (int) z) ? 94D : 100D), FINE, false, 1 << 16);
        ShapedNetwork shaped = new ReachShaper(settings).shape(WINDOW, flat, receivers, accumulation, channels,
                SeaCells.atOrBelow(flat, SEA_LEVEL), List.of(pond), fine, SEED);
        Reach outlet = byId(shaped.reaches()).get(shaped.ponds().get(0).outletReachId());
        assertNotNull("the pond drains through an outlet along its shore", outlet);
        float farthestHeld = 0f;
        for (int point = 0; point < outlet.xs().length; point++) {
            float along = outlet.xs()[point] - outlet.xs()[0];
            if (outlet.xs()[point] <= 136f) {
                assertEquals("outlet point " + point + " beside the shore at x=" + outlet.xs()[point], ReachStyle.POND_OUTLET, outlet.style()[point]);
                farthestHeld = Math.max(farthestHeld, along);
            } else if (outlet.xs()[point] >= 160f) {
                assertEquals("outlet point " + point + " past the shore at x=" + outlet.xs()[point], ReachStyle.RUN, outlet.style()[point]);
            }
        }
        assertTrue("the held shore runs " + farthestHeld + " blocks, past the 8-block outlet zone", farthestHeld > 40f);
    }

    @Test
    public void pondsSharingAnExitLeadInOnceFromTheirGradingPond() {
        HydrologySettings settings = settings(config -> {
        });
        DemGrid flat = outletGrid();
        int[] receivers = new int[flat.values().length];
        float[] accumulation = new float[receivers.length];
        BitSet channels = new BitSet(receivers.length);
        outletRow(flat, receivers, accumulation, channels);
        receivers[flat.index(13, 7)] = flat.index(13, 8);
        accumulation[flat.index(13, 7)] = 100f;
        Pond fed = new Pond(61L, PondKind.NATURAL, 99, 94, 104, 64, 100f, disk(96, 64, 5), 0L, 0, "water", 96, 64);
        Pond side = new Pond(62L, PondKind.NATURAL, 99, 94, 104, 60, 100f, disk(104, 56, 3), 0L, 0, "water", 104, 56);
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> fed.mask().contains((int) x, (int) z)
                || side.mask().contains((int) x, (int) z) ? 94D : 100D), FINE, false, 1 << 16);
        ShapedNetwork shaped = new ReachShaper(settings).shape(WINDOW, flat, receivers, accumulation, channels,
                SeaCells.atOrBelow(flat, SEA_LEVEL), List.of(fed, side), fine, SEED);
        assertEquals("both ponds drain through the same exit", shaped.ponds().get(0).outletReachId(), shaped.ponds().get(1).outletReachId());
        Reach outlet = byId(shaped.reaches()).get(shaped.ponds().get(1).outletReachId());
        assertNotNull(outlet);
        assertEquals("the exit stays the junction vertex", 104f, outlet.xs()[1], 0f);
        assertEquals(64f, outlet.zs()[1], 0f);
        assertTrue("one lead-in from the grading pond " + outlet.xs()[0] + "," + outlet.zs()[0],
                side.mask().contains(Math.round(outlet.xs()[0]), Math.round(outlet.zs()[0])));
        assertEquals(99, outlet.grade()[0]);
    }

    @Test
    public void heldOutletPointsOverTheSeaStayAtTheSeaSurface() {
        HydrologySettings settings = settings(config -> {
        });
        int width = 24;
        DemGrid surface = new DemGrid(0, 0, CELL, width, 3);
        Arrays.fill(surface.values(), 60f);
        int[] receivers = new int[width * 3];
        Arrays.fill(receivers, -1);
        float[] accumulation = new float[width * 3];
        BitSet channels = new BitSet(width * 3);
        for (int i = 0; i < 19; i++) {
            flow(surface, receivers, accumulation, channels, i, 1, i + 1, 1, 1600f + 10f * i);
        }
        surface.set(19, 1, 30f);
        Pond pond = new Pond(71L, PondKind.NATURAL, 59, 54, 136, 8, 60f, disk(128, 8, 7), 0L, 0, "water", 128, 8);
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> x < 138D ? 60D : 35D), FINE, false, 1 << 16);
        ShapedNetwork shaped = new ReachShaper(settings).shape(WINDOW, surface, receivers, accumulation, channels,
                SeaCells.atOrBelow(surface, SEA_LEVEL), List.of(pond), fine, SEED);
        Pond shapedPond = shaped.ponds().get(0);
        Reach outlet = byId(shaped.reaches()).get(shapedPond.outletReachId());
        assertNotNull(outlet);
        assertTrue(shapedPond.mask().contains(Math.round(outlet.xs()[0]), Math.round(outlet.zs()[0])));
        assertEquals("the lip holds the pond level", shapedPond.level(), outlet.grade()[0]);
        int mouths = 0;
        for (int point = 0; point < outlet.grade().length; point++) {
            if (outlet.style()[point] == ReachStyle.MOUTH) {
                mouths++;
                assertEquals("ocean point " + point + " at " + outlet.xs()[point], SEA_LEVEL, outlet.grade()[point]);
            }
        }
        assertTrue("ocean points " + mouths, mouths > 1);
    }

    @Test
    public void inlandPondOutletOverASeaSliverHoldsThePondLevel() {
        HydrologySettings settings = settings(config -> {
        });
        int width = 24;
        DemGrid surface = new DemGrid(0, 0, CELL, width, 3);
        Arrays.fill(surface.values(), 60f);
        int[] receivers = new int[width * 3];
        Arrays.fill(receivers, -1);
        float[] accumulation = new float[width * 3];
        BitSet channels = new BitSet(width * 3);
        for (int i = 0; i < 19; i++) {
            flow(surface, receivers, accumulation, channels, i, 1, i + 1, 1, 1600f + 10f * i);
        }
        Pond pond = new Pond(73L, PondKind.NATURAL, 59, 54, 136, 8, 60f, disk(128, 8, 7), 0L, 0, "water", 128, 8);
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> x >= SLIVER_X && x < SLIVER_X + 8D ? 35D : 60D), FINE, false, 1 << 16);
        ShapedNetwork shaped = new ReachShaper(settings).shape(WINDOW, surface, receivers, accumulation, channels,
                SeaCells.atOrBelow(surface, SEA_LEVEL), List.of(pond), fine,
                SEED);
        Pond shapedPond = shaped.ponds().get(0);
        Reach outlet = byId(shaped.reaches()).get(shapedPond.outletReachId());
        assertNotNull(outlet);
        assertEquals("the inland pond keeps its level", 59, shapedPond.level());
        assertTrue("the outlet leads in from the pond", shapedPond.mask().contains(Math.round(outlet.xs()[0]), Math.round(outlet.zs()[0])));
        assertTrue("the outlet starts over the sea sliver", outlet.xs()[1] >= SLIVER_X && outlet.xs()[1] < SLIVER_X + 8D);
        assertEquals("the lead-in holds the pond level", shapedPond.level(), outlet.grade()[0]);
        assertEquals("the lip holds the pond level", shapedPond.level(), outlet.grade()[1]);
        for (int point = 0; point < outlet.grade().length; point++) {
            assertNotEquals("outlet point " + point + " at " + outlet.xs()[point] + " is a mouth", ReachStyle.MOUTH, outlet.style()[point]);
            assertTrue("outlet point " + point + " at " + outlet.xs()[point] + " drops to the sea surface, graded " + outlet.grade()[point],
                    outlet.grade()[point] > SEA_LEVEL + 10);
        }
    }

    @Test
    public void gorgeCutWhenRidgeMissedByGrid() {
        HydrologySettings settings = settings(config -> {
        });
        int ridgeX = 196;
        DoubleBinaryOperator terrain = (x, z) -> 150D - 0.05D * x + 0.1D * Math.abs(z - VALLEY_Z) + (x >= ridgeX - 3 && x <= ridgeX + 3 ? 15D : 0D);
        Network network = network(settings, terrain, 64, 64, VALLEY_THRESHOLD, false);
        for (int i = ridgeX / CELL; i <= ridgeX / CELL + 1; i++) {
            assertTrue(network.grid().get(i, VALLEY_Z / CELL) < terrain.applyAsDouble(ridgeX, VALLEY_Z) - 10D);
        }
        ShapedNetwork shaped = shape(settings, network);
        int crossings = 0;
        for (Reach reach : shaped.reaches()) {
            int last = reach.xs().length - 1;
            assertFalse(reach + " ends at the ridge", Math.abs(reach.xs()[last] - ridgeX) < 2 * CELL && Math.abs(reach.zs()[last] - VALLEY_Z) < 2 * CELL);
            for (int index = 1; index <= last; index++) {
                if (reach.xs()[index - 1] < ridgeX && reach.xs()[index] >= ridgeX) {
                    crossings++;
                    float gap = (float) Math.hypot(reach.xs()[index] - reach.xs()[index - 1], reach.zs()[index] - reach.zs()[index - 1]);
                    assertTrue(reach + " gap " + gap, gap <= 2f);
                    double ridge = terrain.applyAsDouble(ridgeX, reach.zs()[index]);
                    assertTrue(reach + " grade " + reach.grade()[index] + " ridge " + ridge, reach.grade()[index] < ridge - 10D);
                    double upstream = terrain.applyAsDouble(ridgeX - 2 * CELL, VALLEY_Z);
                    assertTrue(reach + " grade " + reach.grade()[index] + " upstream ground " + upstream,
                            reach.grade()[index] >= upstream - reach.tier().inset() - 2D);
                }
            }
            for (int index = 1; index <= last; index++) {
                float gap = (float) Math.hypot(reach.xs()[index] - reach.xs()[index - 1], reach.zs()[index] - reach.zs()[index - 1]);
                assertTrue(reach + " gap " + gap + " at " + index, gap <= 3f);
            }
        }
        assertEquals(1, crossings);
    }

    @Test
    public void stationsDoNotDependOnWhereTheWalkStarts() {
        HydrologySettings settings = settings(config -> {
        });
        DoubleBinaryOperator terrain = (x, z) -> 150D - 0.04D * x + 0.1D * Math.abs(z - 128D);
        ShapedNetwork whole = shape(settings, network(settings, terrain, 0, 160, 32, CELL, VALLEY_THRESHOLD, false));
        ShapedNetwork clipped = shape(settings, network(settings, terrain, 296, 123, 32, CELL, VALLEY_THRESHOLD, false));
        List<Long> wholeIds = splitsBeyond(whole, 600f);
        List<Long> clippedIds = splitsBeyond(clipped, 600f);
        assertTrue("reaches beyond x=600 " + wholeIds.size(), wholeIds.size() >= 2);
        assertEquals(wholeIds, clippedIds);
    }

    @Test
    public void shortPitIsPooledInsteadOfLockingTheGrade() {
        HydrologySettings settings = settings(config -> {
        });
        int pitX = 204;
        DoubleBinaryOperator shallow = pittedValley(pitX, 4D);
        HydrologySettings plain = settings(ReachShaperTest::ungrouped);
        Network shallowNetwork = network(settings, shallow, 64, 64, VALLEY_THRESHOLD, false);
        ShapedNetwork shaped = shape(settings, shallowNetwork);
        for (Reach reach : shape(plain, shallowNetwork).reaches()) {
            for (int index = 0; index < reach.xs().length; index++) {
                float x = reach.xs()[index];
                if (x >= pitX + 48 && x <= pitX + 200 && Math.abs(reach.zs()[index] - VALLEY_Z) <= 12f) {
                    int natural = (int) Math.floor(shallow.applyAsDouble(x, reach.zs()[index])) - reach.tier().inset();
                    assertTrue("ungrouped " + reach + " at " + x + " grade " + reach.grade()[index] + " natural " + natural, reach.grade()[index] >= natural - 1);
                }
            }
        }
        int checked = 0;
        int pooled = 0;
        for (Reach reach : shaped.reaches()) {
            for (int index = 0; index < reach.xs().length; index++) {
                float x = reach.xs()[index];
                float z = reach.zs()[index];
                int poolEnd = (int) Math.floor(shallow.applyAsDouble(x + StepPools.POOL_MAX, z)) - reach.tier().inset();
                if (x >= pitX + 48 && x <= pitX + 200 && Math.abs(z - VALLEY_Z) <= 12f) {
                    checked++;
                    assertTrue(reach + " at " + x + " grade " + reach.grade()[index] + " pool end " + poolEnd, reach.grade()[index] >= poolEnd - 1);
                }
                if (overPit(x, z, pitX)) {
                    pooled += reach.grade()[index] > (int) Math.floor(shallow.applyAsDouble(pitX, VALLEY_Z)) - reach.tier().inset() ? 1 : 0;
                }
            }
        }
        assertTrue("points downstream of the pit " + checked, checked > 100);
        assertTrue("pooled points over the pit " + pooled, pooled > 0);

        DoubleBinaryOperator deep = pittedValley(pitX, 30D);
        int bottom = (int) Math.floor(deep.applyAsDouble(pitX, VALLEY_Z));
        int over = 0;
        for (Reach reach : shape(settings, network(settings, deep, 64, 64, VALLEY_THRESHOLD, false)).reaches()) {
            for (int index = 0; index < reach.xs().length; index++) {
                if (overPit(reach.xs()[index], reach.zs()[index], pitX)) {
                    over++;
                    assertTrue(reach + " grade " + reach.grade()[index] + " pit bottom " + bottom,
                            reach.grade()[index] <= bottom - reach.tier().inset() + 4);
                }
            }
        }
        assertTrue("points over the deep pit " + over, over > 0);
    }

    @Test
    public void inflowMeetsPondAtItsLevelDespiteDeepPit() {
        HydrologySettings settings = settings(config -> {
            config.getPonds().getSeeded().setEnabled(false);
            config.getPonds().setFreeboard(new KList<>(1, 1));
        });
        int pitX = 212;
        DoubleBinaryOperator bowl = bowlValley(new int[]{250});
        DoubleBinaryOperator terrain = (x, z) -> bowl.applyAsDouble(x, z) - (Math.hypot(x - pitX, z - VALLEY_Z) <= 3D ? 12D : 0D);
        Network network = network(settings, terrain, 64, 64, VALLEY_THRESHOLD, true);
        assertEquals(1, network.ponds().size());
        Pond pond = network.ponds().get(0);
        ShapedNetwork shaped = shape(settings, network);
        Map<Long, Reach> byId = byId(shaped.reaches());
        int inflows = 0;
        int overPit = 0;
        for (Reach inflow : shaped.reaches()) {
            if (!endsIn(inflow, pond)) {
                continue;
            }
            inflows++;
            assertEquals(inflow.toString(), pond.level(), inflow.grade()[inflow.grade().length - 1]);
            for (Reach reach : upstreamOf(inflow, shaped.reaches(), byId)) {
                assertTrue("pit sits deep enough to bind: " + reach, (int) Math.floor(terrain.applyAsDouble(pitX, VALLEY_Z)) - reach.tier().inset() + 4 < pond.level());
                for (int index = 0; index < reach.grade().length; index++) {
                    assertTrue(reach + " at " + index + " grade " + reach.grade()[index], reach.grade()[index] >= pond.level());
                    overPit += overPit(reach.xs()[index], reach.zs()[index], pitX) ? 1 : 0;
                }
            }
        }
        assertTrue("inflows " + inflows, inflows > 0);
        assertTrue("inflow points over the pit " + overPit, overPit > 0);
    }

    @Test
    public void backwaterNeverSitsMoreThanTheFillLimitAboveTheBanks() {
        HydrologySettings settings = settings(config -> {
            config.getPonds().getSeeded().setEnabled(false);
            config.getPonds().getNatural().setMinArea(1024);
            config.getPonds().setFreeboard(new KList<>(1, 1));
        });
        int maxFill = settings.deformation().maxFill();
        int pitX = 176;
        DoubleBinaryOperator bowl = bowlValley(new int[]{250});
        DoubleBinaryOperator terrain = (x, z) -> bowl.applyAsDouble(x, z) - (Math.hypot(x - pitX, z - VALLEY_Z) <= 14D ? 20D : 0D);
        Network network = network(settings, terrain, 64, 64, VALLEY_THRESHOLD, true);
        assertEquals(1, network.ponds().size());
        Pond pond = network.ponds().get(0);
        ShapedNetwork shaped = shape(settings, network);
        Map<Long, Reach> byId = byId(shaped.reaches());
        int checked = 0;
        for (Reach inflow : shaped.reaches()) {
            if (!endsIn(inflow, pond)) {
                continue;
            }
            for (Reach reach : upstreamOf(inflow, shaped.reaches(), byId)) {
                for (int index = 0; index < reach.grade().length; index++) {
                    float x = reach.xs()[index];
                    float z = reach.zs()[index];
                    if (Math.abs(x - pitX) > 3f || Math.abs(z - VALLEY_Z) > 3f || pond.mask().contains(Math.round(x), Math.round(z))) {
                        continue;
                    }
                    double edge = reach.halfWidth()[index] + 1D;
                    double bank = Math.min(Math.min(terrain.applyAsDouble(x, z - edge), terrain.applyAsDouble(x, z + edge)),
                            Math.min(terrain.applyAsDouble(x - edge, z), terrain.applyAsDouble(x + edge, z)));
                    assertTrue(reach + " at " + index + " grade " + reach.grade()[index] + " over banks at " + bank + ", pond " + pond.level(),
                            reach.grade()[index] <= Math.floor(bank) + maxFill + 1 && reach.grade()[index] < pond.level());
                    checked++;
                }
            }
        }
        assertTrue("points over the wide pit " + checked, checked > 0);
        assertWaterNeverFlowsUphillIntoAPond("wide pit", shaped);
    }

    @Test
    public void backwaterNeverFloatsAboveGround() {
        HydrologySettings settings = settings(config -> config.getPonds().getSeeded().setEnabled(false));
        List<DoubleBinaryOperator> terrains = new ArrayList<>();
        DoubleBinaryOperator bowl = bowlValley(new int[]{250});
        terrains.add((x, z) -> bowl.applyAsDouble(x, z) - (Math.hypot(x - 212D, z - VALLEY_Z) <= 3D ? 12D : 0D));
        terrains.add(bowlValley(new int[]{150, 350}));
        for (int tile = 0; tile < 6; tile++) {
            terrains.add(rolling(9000L + tile, tile < 3 ? 24D : 40D));
        }
        int lifted = 0;
        int pooled = 0;
        for (DoubleBinaryOperator terrain : terrains) {
            Network network = network(settings, terrain, 64, 64, 2000f, true);
            ShapedNetwork shaped = shape(settings, network);
            Map<Long, Reach> byId = byId(shaped.reaches());
            for (Pond pond : shaped.ponds()) {
                for (Reach inflow : shaped.reaches()) {
                    if (!endsIn(inflow, pond)) {
                        continue;
                    }
                    for (Reach reach : upstreamOf(inflow, shaped.reaches(), byId)) {
                        for (int index = 0; index < reach.grade().length; index++) {
                            float x = reach.xs()[index];
                            float z = reach.zs()[index];
                            if (reach.grade()[index] != pond.level() || pond.mask().contains(Math.round(x), Math.round(z))) {
                                continue;
                            }
                            lifted++;
                            int natural = (int) Math.floor(terrain.applyAsDouble(x, z)) - reach.tier().inset();
                            if (reach.grade()[index] > natural) {
                                pooled++;
                                float fill = filledAt(network.grid(), x, z);
                                assertTrue(reach + " at " + index + " floats: grade " + reach.grade()[index] + " natural " + natural + " fill " + fill,
                                        fill >= pond.level() - 1f);
                            }
                        }
                    }
                }
            }
        }
        assertTrue("backwater points " + lifted, lifted > 100);
        assertTrue("pooled backwater points " + pooled, pooled > 10);
    }

    @Test
    public void outletRankedByThePondsOwnOutflow() {
        HydrologySettings settings = settings(config -> {
        });
        int width = 32;
        int depth = 16;
        DemGrid flat = new DemGrid(0, 0, CELL, width, depth);
        Arrays.fill(flat.values(), 100f);
        int[] receivers = new int[width * depth];
        Arrays.fill(receivers, -1);
        float[] accumulation = new float[width * depth];
        Arrays.fill(accumulation, 64f);
        BitSet channels = new BitSet(width * depth);
        for (int i = 0; i < width; i++) {
            int main = flat.index(i, 8);
            receivers[main] = i + 1 < width ? flat.index(i + 1, 8) : -1;
            accumulation[main] = (i + 1) * 1000f;
            channels.set(main);
            int side = flat.index(i, 11);
            receivers[side] = i + 1 < width ? flat.index(i + 1, 11) : -1;
            accumulation[side] = (i + 1) * 20000f;
            channels.set(side);
        }
        for (int i = 10; i <= 14; i++) {
            for (int j = 6; j <= 10; j++) {
                if (j != 8) {
                    receivers[flat.index(i, j)] = flat.index(i, j < 8 ? j + 1 : j - 1);
                }
            }
        }
        receivers[flat.index(12, 10)] = flat.index(12, 11);
        accumulation[flat.index(12, 10)] = 300f;
        Pond pond = new Pond(21L, PondKind.NATURAL, 101, 98, 0, 0, 102f, rectangle(80, 48, 9, 9), 0L, 0, "water", 96, 64);
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> 100D), FINE, false, 1 << 16);
        ShapedNetwork shaped = new ReachShaper(settings).shape(WINDOW, flat, receivers, accumulation, channels,
                SeaCells.atOrBelow(flat, SEA_LEVEL), List.of(pond), fine, SEED);
        Reach outlet = byId(shaped.reaches()).get(shaped.ponds().get(0).outletReachId());
        assertNotNull(outlet);
        assertTrue(pond.mask().contains(Math.round(outlet.xs()[0]), Math.round(outlet.zs()[0])));
        assertEquals(120f, outlet.xs()[1], 0f);
        assertEquals(64f, outlet.zs()[1], 0f);
    }

    @Test
    public void oceanEndIsMouthAtSeaLevelWhenFineGroundIsDry() {
        HydrologySettings settings = settings(config -> {
        });
        int width = 24;
        DemGrid surface = new DemGrid(0, 0, CELL, width, 3);
        Arrays.fill(surface.values(), 60f);
        int[] receivers = new int[width * 3];
        Arrays.fill(receivers, -1);
        float[] accumulation = new float[width * 3];
        BitSet channels = new BitSet(width * 3);
        for (int i = 0; i < width; i++) {
            int cell = surface.index(i, 1);
            receivers[cell] = i + 1 < width ? surface.index(i + 1, 1) : -1;
            accumulation[cell] = (i + 1) * 1000f;
            if (i + 1 < width) {
                channels.set(cell);
            }
        }
        surface.set(width - 1, 1, 30f);
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> 45D), FINE, false, 1 << 16);
        ShapedNetwork shaped = new ReachShaper(settings).shape(WINDOW, surface, receivers, accumulation, channels,
                SeaCells.atOrBelow(surface, SEA_LEVEL), List.of(), fine, SEED);
        int mouths = 0;
        for (Reach reach : shaped.reaches()) {
            int last = reach.xs().length - 1;
            if (reach.xs()[last] == (width - 1) * CELL) {
                mouths++;
                assertEquals(reach.toString(), ReachStyle.MOUTH, reach.style()[last]);
                assertEquals(reach.toString(), SEA_LEVEL, reach.grade()[last]);
                assertEquals(0L, reach.downstreamId());
            }
        }
        assertEquals(1, mouths);
    }

    @Test
    public void mouthHoldsTheSeaSurfaceBelowAPondUnderSeaLevel() {
        HydrologySettings settings = settings(config -> {
        });
        int width = 24;
        DemGrid surface = new DemGrid(0, 0, CELL, width, 3);
        Arrays.fill(surface.values(), 60f);
        int[] receivers = new int[width * 3];
        Arrays.fill(receivers, -1);
        float[] accumulation = new float[width * 3];
        BitSet channels = new BitSet(width * 3);
        for (int i = 0; i < width; i++) {
            int cell = surface.index(i, 1);
            receivers[cell] = i + 1 < width ? surface.index(i + 1, 1) : -1;
            accumulation[cell] = (i + 1) * 1000f;
            if (i + 1 < width) {
                channels.set(cell);
            }
        }
        surface.set(width - 1, 1, 30f);
        Pond sunken = new Pond(77L, PondKind.NATURAL, SEA_LEVEL - 1, SEA_LEVEL - 6, CELL, CELL, SEA_LEVEL + 0.5f,
                new PondMask(0, CELL, CELL, 2, 1, new long[]{0b11L}), 0L, 0, "water", 0, CELL);
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> 45D), FINE, false, 1 << 16);
        ShapedNetwork shaped = new ReachShaper(settings).shape(WINDOW, surface, receivers, accumulation, channels,
                SeaCells.atOrBelow(surface, SEA_LEVEL), List.of(sunken), fine, SEED);
        int mouths = 0;
        for (Reach reach : shaped.reaches()) {
            for (int grade : reach.grade()) {
                assertTrue(reach + " grades below the sea surface", grade >= SEA_LEVEL);
            }
            int last = reach.xs().length - 1;
            if (reach.style()[last] == ReachStyle.MOUTH) {
                mouths++;
                assertEquals(reach.toString(), SEA_LEVEL, reach.grade()[last]);
            }
        }
        assertEquals(1, mouths);
    }

    @Test
    public void oceanReachesFlareOverTheEstuary() {
        HydrologySettings settings = settings(config -> {
        });
        HydrologySettings plain = settings(config -> config.getTiers().forEach(tier -> tier.getEstuary().setFlare(1D)));
        HydrologySettings plainMouth = settings(config -> config.getTiers().forEach(tier -> tier.getEstuary().setLength(0)));
        Reach estuary = coastalRun(settings, true);
        Reach unflared = coastalRun(plain, true);
        Reach mouth = coastalRun(plainMouth, true);
        HydrologyTier.Estuary shape = estuary.tier().estuary();
        assertEquals(48, shape.length());
        assertEquals(2.5f, shape.flare(), 0f);
        assertTrue("the same channel line", Arrays.equals(estuary.xs(), unflared.xs()) && Arrays.equals(estuary.xs(), mouth.xs()));

        int last = estuary.xs().length - 1;
        int start = trailingMouth(estuary);
        float span = estuary.xs()[last] - estuary.xs()[start];
        assertTrue("estuary reaches " + span + " blocks up from the mouth", span >= 40f && span <= 52f);
        for (int point = 0; point <= last; point++) {
            boolean inside = point >= start;
            assertEquals(estuary + " is a mouth at " + point, inside, estuary.style()[point] == ReachStyle.MOUTH);
            assertTrue(estuary + " grade at " + point, inside ? estuary.grade()[point] == SEA_LEVEL : estuary.grade()[point] > SEA_LEVEL);
            assertEquals("the flare leaves the depth alone at " + point, unflared.depth()[point], estuary.depth()[point], 0f);
            if (point <= start) {
                assertEquals("full channel depth above the estuary at " + point, mouth.depth()[point], estuary.depth()[point], 0f);
                assertEquals("no flare above the estuary at " + point, unflared.halfWidth()[point], estuary.halfWidth()[point], 0f);
            } else {
                assertTrue("the estuary shallows toward the mouth at " + point, estuary.depth()[point] <= mouth.depth()[point]);
                float ratio = estuary.halfWidth()[point] / unflared.halfWidth()[point];
                float previous = estuary.halfWidth()[point - 1] / unflared.halfWidth()[point - 1];
                assertTrue("flare grows toward the mouth at " + point + ": " + previous + " then " + ratio, ratio >= previous - 1e-6f);
            }
        }
        float mouthFlare = estuary.halfWidth()[last] / unflared.halfWidth()[last];
        assertTrue("a full-length estuary opens to about flare times the channel: " + mouthFlare, mouthFlare >= 2.45f && mouthFlare <= 2.5f + 1e-4f);
        float half = estuary.xs()[start] + 0.5f * span;
        int middle = start;
        while (estuary.xs()[middle] < half) {
            middle++;
        }
        assertTrue("flare grows slowly at first", estuary.halfWidth()[middle] / unflared.halfWidth()[middle] < 1.75f);
        assertTrue("the estuary shallows gradually", estuary.depth()[middle] > HydrologyTier.MINIMUM_DEPTH);
        assertEquals("the estuary bed meets the seabed at the mouth", HydrologyTier.MINIMUM_DEPTH, estuary.depth()[last], 0f);
        assertTrue("a plain mouth keeps the channel depth", mouth.depth()[last] > 2f * HydrologyTier.MINIMUM_DEPTH);

        assertTrue("length 0 ends in a plain mouth", mouth.xs()[last] - mouth.xs()[trailingMouth(mouth)] < 8f);
        assertTrue("length 0 keeps the channel width", Arrays.equals(unflared.halfWidth(), mouth.halfWidth()));

        Reach inland = coastalRun(settings, false);
        Reach inlandPlain = coastalRun(plain, false);
        assertEquals(0, inland.downstreamId());
        assertNotEquals(ReachStyle.MOUTH, inland.style()[inland.xs().length - 1]);
        assertTrue("a channel that does not reach the sea keeps its width", Arrays.equals(inlandPlain.halfWidth(), inland.halfWidth()));
    }

    @Test
    public void estuaryStopsWhereTheBanksRise() {
        HydrologySettings settings = settings(config -> {
        });
        HydrologySettings plainMouth = settings(config -> config.getTiers().forEach(tier -> tier.getEstuary().setLength(0)));
        double rise = 0.2D;
        Reach estuary = coastalRun(settings, true, rise);
        Reach mouth = coastalRun(plainMouth, true, rise);
        int last = estuary.xs().length - 1;
        int start = trailingMouth(estuary);
        float span = estuary.xs()[last] - estuary.xs()[start];
        int highestBank = SEA_LEVEL + settings.deformation().maxFill();
        assertTrue("banks above " + highestBank + " stop the estuary " + span + " blocks up, short of its length", span >= 16f && span <= 30f);
        for (int point = start; point <= last; point++) {
            double bank = SEA_LEVEL + 1D + rise * (estuary.xs()[last] - estuary.xs()[point]);
            assertTrue("estuary point " + point + " has a bank at " + bank, bank <= highestBank + 1D);
            assertEquals(SEA_LEVEL, estuary.grade()[point]);
        }
        assertEquals("no flare where the estuary starts", mouth.halfWidth()[start], estuary.halfWidth()[start], 0f);
        for (int point = start + 1; point <= last; point++) {
            float ratio = estuary.halfWidth()[point] / mouth.halfWidth()[point];
            float previous = estuary.halfWidth()[point - 1] / mouth.halfWidth()[point - 1];
            assertTrue("flare grows toward the mouth at " + point + ": " + previous + " then " + ratio, ratio >= previous - 1e-6f);
        }
        float mouthFlare = estuary.halfWidth()[last] / mouth.halfWidth()[last];
        assertTrue("a short estuary opens less than flare: " + mouthFlare, mouthFlare > 1.5f && mouthFlare < 2.4f);
        assertEquals("a short estuary still meets the seabed", HydrologyTier.MINIMUM_DEPTH, estuary.depth()[last], 0f);
    }

    @Test
    public void estuaryStopsAtAHeldPondLip() {
        Reach outlet = coastalOutlet(PondKind.NATURAL);
        PondMask pond = disk(128, 8, 7);
        int lip = 0;
        while (lip < outlet.xs().length && ReachShaper.nearLip(pond, outlet.xs()[lip], outlet.zs()[lip], outlet.halfWidth()[lip])) {
            assertEquals("lip point " + lip + " holds the pond level", OUTLET_POND_LEVEL, outlet.grade()[lip]);
            assertNotEquals("lip point " + lip, ReachStyle.MOUTH, outlet.style()[lip]);
            lip++;
        }
        assertTrue("points along the lip " + lip, lip >= 4);
        assertEstuaryInland(outlet);
    }

    @Test
    public void lakeOutletLeavesTheLakeAtItsLevelBesideAnEstuary() {
        Reach outlet = coastalOutlet(PondKind.LAKE);
        assertEquals("the outlet leaves the lake at its level", OUTLET_POND_LEVEL, outlet.grade()[0]);
        assertNotEquals(ReachStyle.MOUTH, outlet.style()[0]);
        assertEstuaryInland(outlet);
    }

    @Test
    public void estuaryOpeningStaysFiniteWithoutLengthOrDistance() {
        boolean[] ocean = {false, true, true};
        float[] stacked = {0f, 10f, 10f};
        for (ReachShaper.Opening opening : List.of(ReachShaper.estuaryOpening(new HydrologyTier.Estuary(0, 2.5f), ocean, stacked),
                ReachShaper.estuaryOpening(new HydrologyTier.Estuary(48, 2.5f), ocean, stacked))) {
            assertEquals(0f, opening.share(), 0f);
            for (float progress : opening.progress()) {
                assertEquals(0f, progress, 0f);
            }
        }
        ReachShaper.Opening half = ReachShaper.estuaryOpening(new HydrologyTier.Estuary(48, 2.5f), ocean, new float[]{0f, 24f, 48f});
        assertEquals(0.75f, half.share(), 1e-6f);
        assertEquals(0f, half.progress()[1], 0f);
        assertEquals(1f, half.progress()[2], 1e-6f);
    }

    @Test
    public void majorMouthSplitsIntoSeededDistributaries() {
        for (int channels = 2; channels <= 4; channels++) {
            HydrologySettings settings = DeltaCoast.settings(channels);
            ShapedNetwork shaped = cleanDelta(settings, SEED);
            Reach parent = DeltaCoast.river(shaped, DeltaCoast.ROW);
            List<Reach> fan = distributaries(shaped, parent);
            assertEquals(channels + " delta channels", channels, fan.size());
            assertEquals("only the river and its delta", channels + 1, shaped.reaches().size());
            int estuary = trailingMouth(parent);
            float halfWidth = parent.halfWidth()[estuary] / (float) Math.sqrt(channels);
            for (Reach channel : fan) {
                int last = channel.xs().length - 1;
                assertEquals(channel.toString(), 0L, channel.downstreamId());
                assertEquals(channel.toString(), parent.tier(), channel.tier());
                for (int point = 0; point <= last; point++) {
                    assertEquals(channel + " at sea level at " + point, SEA_LEVEL, channel.grade()[point]);
                    assertEquals(channel + " is a mouth at " + point, ReachStyle.MOUTH, channel.style()[point]);
                    assertEquals(channel + " half-width at " + point, halfWidth, channel.halfWidth()[point], 1e-6f);
                }
                assertTrue(channel + " branches below the estuary start", channel.xs()[0] > parent.xs()[estuary]);
                assertTrue(channel + " ends where the sea begins",
                        DeltaCoast.ground(fineNode(channel.xs()[last]), fineNode(channel.zs()[last])) <= SEA_LEVEL);
                assertTrue(channel + " crosses land first", DeltaCoast.ground(fineNode(channel.xs()[1]), fineNode(channel.zs()[1])) > SEA_LEVEL);
                double angle = Math.toDegrees(Math.atan2(Math.abs(channel.zs()[last] - channel.zs()[0]), channel.xs()[last] - channel.xs()[0]));
                assertTrue(channel + " fans out within the spread: " + angle, angle > 3D && angle < 37D);
                assertEquals(channel + " depth meets the seabed", HydrologyTier.MINIMUM_DEPTH, channel.depth()[last], 1e-6f);
            }
            assertDeltaClear(shaped, List.of());
            assertEquals("deterministic", byId(shaped.reaches()), byId(cleanDelta(settings, SEED).reaches()));
            assertEquals("independent of the grid origin", byId(shaped.reaches()),
                    byId(DeltaCoast.shape(settings, -64, -40, List.of(), DeltaCoast.NO_NEIGHBOUR, SEED).reaches()));
            List<Reach> reseeded = DeltaCoast.deltaChannels(cleanDelta(settings, SEED + 1L));
            assertEquals(channels, reseeded.size());
            assertNotEquals("the seed chooses the channels", ends(fan), ends(reseeded));
        }
    }

    @Test
    public void deltaChannelAcrossAPondIsLeftOut() {
        HydrologySettings settings = DeltaCoast.settings(2);
        CleanFan fan = cleanFan(settings);
        int far = fan.north().xs().length - 3;
        int pondX = Math.round(fan.north().xs()[far]);
        int pondZ = Math.round(fan.north().zs()[far]);
        Pond pond = new Pond(91L, PondKind.NATURAL, SEA_LEVEL + 1, SEA_LEVEL - 1, 0, 0, SEA_LEVEL + 1.5f, disk(pondX, pondZ, 2), 0L, 0, "water",
                pondX, pondZ);
        assertTrue("the pond lies on the northern channel", coversMask(fan.north(), pond.mask()));
        ShapedNetwork ponded = DeltaCoast.shape(settings, 0, 0, List.of(pond), DeltaCoast.NO_NEIGHBOUR, SEED);
        assertFalse("the pond lies clear of the river", coversMask(DeltaCoast.river(ponded, DeltaCoast.ROW), pond.mask()));
        assertEquals("a channel that would cross a pond is left out", List.of(fan.south()), DeltaCoast.deltaChannels(ponded));
        assertDeltaClear(ponded, List.of(pond));
    }

    @Test
    public void deltaChannelOverHighGroundIsLeftOut() {
        HydrologySettings settings = DeltaCoast.settings(2);
        CleanFan fan = cleanFan(settings);
        boolean climbs = false;
        for (int point = 0; point < fan.north().xs().length; point++) {
            climbs |= fan.north().zs()[point] > DeltaCoast.HILL_Z + 2 && fan.north().xs()[point] < DeltaCoast.COAST_X - 4D;
        }
        assertTrue("the northern channel would climb the hill", climbs);
        ShapedNetwork hill = DeltaCoast.shape(settings, 0, 0, List.of(), DeltaCoast.NO_NEIGHBOUR, SEED, DeltaCoast::northHill);
        assertEquals("a channel that would cut through ground above the fill limit is left out", List.of(fan.south()),
                DeltaCoast.deltaChannels(hill));
        assertDeltaClear(hill, List.of());
    }

    @Test
    public void deltaChannelsTowardAnotherRiverAreLeftOut() {
        HydrologySettings settings = DeltaCoast.settings(2);
        CleanFan fan = cleanFan(settings);
        int neighbourRow = DeltaCoast.ROW + 32;
        ShapedNetwork twin = DeltaCoast.shape(settings, 0, 0, List.of(), neighbourRow, SEED);
        Reach neighbour = DeltaCoast.river(twin, neighbourRow);
        assertTrue("the northern channel would cross the neighbouring river", crossesWater(fan.north(), neighbour));
        assertEquals("a channel that would cross another river is left out", List.of(fan.south()),
                distributaries(twin, DeltaCoast.river(twin, DeltaCoast.ROW)));
        List<Reach> outward = distributaries(twin, neighbour);
        assertEquals("the other river keeps its outward channel", 1, outward.size());
        assertTrue(outward.get(0) + " turns away from the first river", outward.get(0).zs()[outward.get(0).xs().length - 1] > neighbourRow);
        assertDeltaClear(twin, List.of());
    }

    @Test
    public void groundReadIsSymmetricAroundThePoint() {
        HydrologySettings settings = settings(config -> {
        });
        DoubleBinaryOperator forward = steppedValley(3);
        DoubleBinaryOperator mirrored = (x, z) -> forward.applyAsDouble(504D - x, z);
        float before = STEP_X - firstAtOrBelow(shape(settings, network(settings, forward, 64, 64, VALLEY_THRESHOLD, false)), 115, true);
        float mirroredBefore = firstAtOrBelow(shape(settings, network(settings, mirrored, 64, 64, VALLEY_THRESHOLD, false)), 115, false) - (504 - STEP_X);
        assertEquals(before, mirroredBefore, 1e-3f);
    }

    @Test
    public void seaSliverInsideAReachIsNeitherAMouthNorItsBed() {
        HydrologySettings settings = settings(config -> {
        });
        int inletX = 204;
        DoubleBinaryOperator terrain = (x, z) -> 60D - 0.05D * x + 0.1D * Math.abs(z - VALLEY_Z)
                - (Math.abs(x - inletX) <= 2D && Math.abs(z - VALLEY_Z) <= 20D ? 12.3D : 0D);
        ShapedNetwork shaped = shape(settings, network(settings, terrain, 64, 64, VALLEY_THRESHOLD, false));
        int crossing = 0;
        int below = 0;
        int mouths = 0;
        for (Reach reach : shaped.reaches()) {
            int sea = reach.xs().length;
            while (sea > 0 && reach.style()[sea - 1] == ReachStyle.MOUTH) {
                sea--;
            }
            for (int index = 0; index < reach.xs().length; index++) {
                float x = reach.xs()[index];
                if (index >= sea) {
                    mouths++;
                    assertEquals(reach + " mouth point " + index, SEA_LEVEL, reach.grade()[index]);
                    assertTrue(reach + " mouth point " + index + " at " + x + " lies at the sea",
                            terrain.applyAsDouble(x, reach.zs()[index]) < SEA_LEVEL + 8D);
                    continue;
                }
                assertNotEquals(reach + " point " + index + " at " + x + " is a mouth inside the reach", ReachStyle.MOUTH, reach.style()[index]);
                crossing += Math.abs(x - inletX) <= 2f ? 1 : 0;
                if (x >= inletX + 16f && x <= inletX + 96f) {
                    below++;
                    assertTrue(reach + " point " + index + " at " + x + " keeps to the valley below the sliver, graded " + reach.grade()[index],
                            reach.grade()[index] >= Math.floor(terrain.applyAsDouble(x, reach.zs()[index])) - reach.tier().inset() - 2);
                }
            }
        }
        assertTrue("points crossing the sea sliver " + crossing, crossing > 0);
        assertTrue("points below the sea sliver " + below, below > 0);
        assertTrue("mouth points where the valley meets the sea " + mouths, mouths > 0);
    }

    @Test
    public void outletRunningBackIntoItsPondLeavesEveryPondAtOrBelowItsInflows() {
        HydrologySettings settings = settings(config -> {
        });
        int width = 32;
        int depth = 24;
        DemGrid flat = new DemGrid(0, 0, CELL, width, depth);
        Arrays.fill(flat.values(), 100f);
        int[] receivers = new int[width * depth];
        Arrays.fill(receivers, -1);
        float[] accumulation = new float[width * depth];
        Arrays.fill(accumulation, 64f);
        BitSet channels = new BitSet(width * depth);
        for (int i = 0; i <= 8; i++) {
            flow(flat, receivers, accumulation, channels, i, 6, i + 1, 6, i == 8 ? 50000f : 40000f + i * 1000f);
        }
        flow(flat, receivers, accumulation, channels, 9, 6, 8, 7, 50000f);
        flow(flat, receivers, accumulation, channels, 8, 7, 8, 8, 50000f);
        flow(flat, receivers, accumulation, channels, 8, 8, 8, 9, 50000f);
        flow(flat, receivers, accumulation, channels, 8, 9, 8, 10, 50000f);
        for (int i = 8; i < width; i++) {
            flow(flat, receivers, accumulation, channels, i, 10, i + 1 < width ? i + 1 : -1, 10, 50000f);
        }
        for (int j = depth - 1; j > 16; j--) {
            flow(flat, receivers, accumulation, channels, 22, j, 22, j - 1, 2000f + (depth - j) * 1000f);
        }
        flow(flat, receivers, accumulation, channels, 22, 16, 23, 16, 9000f);
        flow(flat, receivers, accumulation, channels, 23, 16, 24, 16, 9000f);
        for (int i = 24; i < width; i++) {
            flow(flat, receivers, accumulation, channels, i, 16, i + 1 < width ? i + 1 : -1, 16, 9000f);
        }
        int maxFill = settings.deformation().maxFill();
        Pond looped = new Pond(31L, PondKind.NATURAL, 101, 98, 72, 48, 102f, rectangle(32, 32, 9, 9), 0L, 0, "water", 48, 48);
        Pond fed = new Pond(32L, PondKind.NATURAL, 100 + maxFill + 4, 98, 192, 128, 100 + maxFill + 5f, rectangle(160, 112, 9, 9), 0L, 0,
                "water", 176, 128);
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> 100D), FINE, false, 1 << 16);
        ShapedNetwork shaped = new ReachShaper(settings).shape(WINDOW, flat, receivers, accumulation, channels,
                SeaCells.atOrBelow(flat, SEA_LEVEL), List.of(looped, fed), fine, SEED);
        int inflows = 0;
        for (Reach reach : shaped.reaches()) {
            if (reach.downstreamId() == 0L && endsIn(reach, fed)) {
                inflows++;
            }
        }
        assertEquals(1, inflows);
        assertTrue("fed pond lowered below " + fed.level(), shaped.ponds().get(1).level() < fed.level());
        assertWaterNeverFlowsUphillIntoAPond("outlet loop", shaped);
        Reach outlet = byId(shaped.reaches()).get(shaped.ponds().get(0).outletReachId());
        assertTrue("outlet " + outlet + " runs back into its own pond", outlet == null || !endsIn(outlet, looped));
    }

    @Test
    public void pondOutletIsTheChannelThatDrainsIt() {
        HydrologySettings settings = settings(config -> {
        });
        DoubleBinaryOperator terrain = (x, z) -> 150D - 0.05D * x + 0.1D * Math.abs(z - VALLEY_Z);
        Network open = network(settings, terrain, 64, 64, VALLEY_THRESHOLD, false);
        int level = (int) Math.floor(terrain.applyAsDouble(340, VALLEY_Z)) + 1;
        Pond drained = new Pond(11L, PondKind.NATURAL, level, level - 3, 288, VALLEY_Z, level + 1f, rectangle(300, VALLEY_Z - 12, 11, 7),
                0L, 0, "water", 320, VALLEY_Z);
        Pond beside = new Pond(12L, PondKind.NATURAL, level - 5, level - 7, 424, VALLEY_Z, level - 4f, rectangle(420, VALLEY_Z - 4, 1, 1),
                0L, 0, "water", 420, VALLEY_Z - 4);
        Network network = new Network(open.grid(), open.receivers(), open.accumulation(), open.channels(), List.of(drained, beside), open.fine());
        ShapedNetwork shaped = shape(settings, network);
        Map<Long, Reach> byId = byId(shaped.reaches());
        Reach outlet = byId.get(shaped.ponds().get(0).outletReachId());
        assertNotNull(outlet);
        assertTrue(drained.mask().contains(Math.round(outlet.xs()[0]), Math.round(outlet.zs()[0])));
        assertEquals(344f, outlet.xs()[1], 0f);
        assertEquals((float) VALLEY_Z, outlet.zs()[1], 0f);
        assertEquals(ReachStyle.POND_OUTLET, outlet.style()[0]);
        assertFalse(endsIn(outlet, drained));
        for (Reach reach : shaped.reaches()) {
            assertFalse(reach + " starts at the spill point beside the inflow", reach.xs()[0] == 288f && reach.zs()[0] == VALLEY_Z);
        }
        Reach fallback = byId.get(shaped.ponds().get(1).outletReachId());
        assertNotNull(fallback);
        assertTrue(beside.mask().contains(Math.round(fallback.xs()[0]), Math.round(fallback.zs()[0])));
        assertEquals(424f, fallback.xs()[1], 0f);
        assertEquals((float) VALLEY_Z, fallback.zs()[1], 0f);
    }

    @Test
    public void gradingDoesNotNeedDescendingHeights() {
        HydrologySettings settings = settings(config -> {
        });
        int width = 64;
        int depth = 8;
        int row = 4;
        int junction = 40;
        DemGrid flat = new DemGrid(0, 0, CELL, width, depth);
        Arrays.fill(flat.values(), 100f);
        int[] receivers = new int[width * depth];
        float[] accumulation = new float[width * depth];
        Arrays.fill(receivers, -1);
        BitSet channels = new BitSet(width * depth);
        for (int i = 0; i < width; i++) {
            int cell = flat.index(i, row);
            receivers[cell] = i == 0 ? -1 : flat.index(i - 1, row);
            accumulation[cell] = (width - i) * 1000f + (i <= junction ? 4000f : 0f);
            channels.set(cell);
        }
        for (int j = 0; j < row; j++) {
            int cell = flat.index(junction, j);
            receivers[cell] = flat.index(junction, j + 1);
            accumulation[cell] = (j + 1) * 1000f;
            channels.set(cell);
        }
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> 100D), FINE, false, 1 << 16);
        ShapedNetwork shaped = new ReachShaper(settings).shape(WINDOW, flat, receivers, accumulation, channels,
                SeaCells.atOrBelow(flat, SEA_LEVEL), List.of(), fine, SEED);
        Map<Long, Reach> byId = byId(shaped.reaches());
        int links = 0;
        for (Reach reach : shaped.reaches()) {
            if (reach.downstreamId() != 0L) {
                links++;
                Reach downstream = byId.get(reach.downstreamId());
                assertEquals(downstream.grade()[0], reach.grade()[reach.grade().length - 1]);
            }
        }
        assertTrue("links " + links, links >= 2);
        assertEquals(1, incoming(shaped.reaches()).values().stream().filter(count -> count == 2).count());
    }

    @Test
    public void pondLookupMatchesLinearScan() {
        Random random = new Random(SEED);
        List<Pond> ponds = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            int width = 1 + random.nextInt(12);
            int depth = 1 + random.nextInt(12);
            long[] bits = new long[(width * depth + 63) >>> 6];
            for (int bit = 0; bit < width * depth; bit++) {
                if (random.nextInt(3) > 0) {
                    bits[bit >>> 6] |= 1L << bit;
                }
            }
            int step = random.nextBoolean() ? 4 : 16;
            PondMask mask = new PondMask(random.nextInt(600) - 300, random.nextInt(600) - 300, step, width, depth, bits);
            ponds.add(new Pond(index, PondKind.NATURAL, 100, 90, 0, 0, 101f, mask, 0L, 0, "water", 0, 0));
        }
        PondLookup lookup = new PondLookup(ponds);
        int hits = 0;
        for (int sample = 0; sample < 20000; sample++) {
            int x = random.nextInt(800) - 400;
            int z = random.nextInt(800) - 400;
            int expected = -1;
            for (int index = 0; index < ponds.size() && expected < 0; index++) {
                expected = ponds.get(index).mask().contains(x, z) ? index : -1;
            }
            assertEquals(x + "," + z, expected, lookup.at(x, z));
            hits += expected >= 0 ? 1 : 0;
        }
        assertTrue("hits " + hits, hits > 1000);
        assertEquals(-1, new PondLookup(List.of()).at(0, 0));
    }

    @Test
    public void shapeIsDeterministic() {
        HydrologySettings settings = settings(config -> {
        });
        ShapedNetwork first = shape(settings, network(settings, fractal(SEED + 5L), 64, 64, 2000f, true));
        ShapedNetwork second = shape(settings, network(settings, fractal(SEED + 5L), 64, 64, 2000f, true));
        assertTrue("reaches " + first.reaches().size(), first.reaches().size() > 20);
        assertEquals(first.reaches(), second.reaches());
        assertEquals(first.ponds(), second.ponds());
        assertEquals(first.reaches().hashCode(), second.reaches().hashCode());
    }

    @Test
    public void waistRuleNeverNarrowsBelowHalfUpstreamMax() {
        HydrologySettings settings = settings(config -> config.setTiers(new KList<>(
                new IrisHydrologyTier().setHalfWidth(new KList<>(4D, 6D)),
                new IrisHydrologyTier().setName("river").setMinCatchment(20000D).setHalfWidth(new KList<>(0.5D, 2D)))));
        ShapedNetwork shaped = shape(settings, network(settings, fractal(SEED + 2L), 64, 64, 2000f, false));
        Map<Long, Float> upstreamMax = new HashMap<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Reach reach : shaped.reaches()) {
                if (reach.downstreamId() == 0L) {
                    continue;
                }
                float carried = Math.max(upstreamMax.getOrDefault(reach.id(), 0f), maximum(reach.halfWidth()));
                if (carried > upstreamMax.getOrDefault(reach.downstreamId(), 0f)) {
                    upstreamMax.put(reach.downstreamId(), carried);
                    changed = true;
                }
            }
        }
        int widened = 0;
        for (Reach reach : shaped.reaches()) {
            float running = upstreamMax.getOrDefault(reach.id(), 0f);
            for (int index = 0; index < reach.halfWidth().length; index++) {
                assertTrue(reach + " at " + index, reach.halfWidth()[index] >= 0.5f * running - 1e-6f);
                running = Math.max(running, reach.halfWidth()[index]);
                widened += reach.halfWidth()[index] > reach.tier().maxHalfWidth() ? 1 : 0;
            }
        }
        assertTrue("points widened past their tier by the waist rule " + widened, widened > 0);
    }

    @Test
    public void junctionWidthBlendsOverSixteenBlocks() {
        HydrologySettings settings = settings(config -> config.setTiers(new KList<>(
                new IrisHydrologyTier().setHalfWidth(new KList<>(1D, 3D)),
                new IrisHydrologyTier().setName("river").setMinCatchment(6000D).setHalfWidth(new KList<>(8D, 12D)))));
        DemGrid flat = outletGrid();
        int[] receivers = new int[flat.values().length];
        Arrays.fill(receivers, -1);
        float[] accumulation = new float[receivers.length];
        BitSet channels = new BitSet(receivers.length);
        for (int i = 0; i < 12; i++) {
            flow(flat, receivers, accumulation, channels, i, 8, i + 1, 8, 2000f + 10f * i);
        }
        for (int j = 0; j < 8; j++) {
            flow(flat, receivers, accumulation, channels, 12, j, 12, j + 1, 3500f + 10f * j);
        }
        for (int i = 12; i < flat.width(); i++) {
            flow(flat, receivers, accumulation, channels, i, 8, i + 1 < flat.width() ? i + 1 : -1, 8, 6400f);
        }
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> 100D), FINE, false, 1 << 16);
        ShapedNetwork shaped = new ReachShaper(settings).shape(WINDOW, flat, receivers, accumulation, channels,
                SeaCells.atOrBelow(flat, SEA_LEVEL), List.of(), fine, SEED);
        Map<Long, List<Reach>> feeders = feeders(shaped.reaches());
        assertEquals("one confluence " + feeders.keySet(), 1, feeders.size());
        Map.Entry<Long, List<Reach>> confluence = feeders.entrySet().iterator().next();
        assertEquals("two feeders", 2, confluence.getValue().size());
        Reach downstream = byId(shaped.reaches()).get(confluence.getKey());
        float widest = 0f;
        for (Reach feeder : confluence.getValue()) {
            assertEquals("feeders are streams", "stream", feeder.tier().name());
            widest = Math.max(widest, feeder.halfWidth()[feeder.halfWidth().length - 1]);
        }
        assertEquals("the merged catchment is a river", "river", downstream.tier().name());
        float law = downstream.tier().halfWidth(downstream.catchment());
        assertTrue("streams " + widest + " join a river " + law, widest < 0.5f * law);
        float along = 0f;
        float previous = widest;
        int blended = 0;
        for (int point = 0; point < downstream.xs().length; point++) {
            if (point > 0) {
                along += (float) Math.hypot(downstream.xs()[point] - downstream.xs()[point - 1], downstream.zs()[point] - downstream.zs()[point - 1]);
            }
            float halfWidth = downstream.halfWidth()[point];
            float expected = along >= 16f ? law : widest + (law - widest) * (along / 16f);
            assertEquals("half-width " + along + " blocks below the junction", expected, halfWidth, 1e-3f);
            assertTrue("half-width narrows from " + previous + " to " + halfWidth + " at " + along, halfWidth >= previous - 1e-6f);
            assertEquals(downstream.tier().depth(halfWidth), downstream.depth()[point], 0f);
            blended += along > 0f && along < 16f ? 1 : 0;
            previous = halfWidth;
        }
        assertTrue("vertices inside the blend " + blended, blended >= 2);
    }

    @Test
    public void pondOutletTakingATributaryAtItsExitKeepsItsOwnWidth() {
        HydrologySettings settings = settings(config -> config.setTiers(new KList<>(
                new IrisHydrologyTier().setHalfWidth(new KList<>(1D, 3D)),
                new IrisHydrologyTier().setName("river").setMinCatchment(6000D).setHalfWidth(new KList<>(8D, 12D)))));
        DemGrid flat = outletGrid();
        int[] receivers = new int[flat.values().length];
        Arrays.fill(receivers, -1);
        float[] accumulation = new float[receivers.length];
        BitSet channels = new BitSet(receivers.length);
        for (int i = 0; i < flat.width(); i++) {
            flow(flat, receivers, accumulation, channels, i, 8, i + 1 < flat.width() ? i + 1 : -1, 8, i < 13 ? 1600f + 10f * i : 6400f);
        }
        for (int j = flat.depth() - 1; j > 8; j--) {
            flow(flat, receivers, accumulation, channels, 13, j, 13, j - 1, 1500f + 10f * (flat.depth() - j));
        }
        Pond pond = new Pond(71L, PondKind.NATURAL, 99, 94, 104, 64, 100f, disk(96, 64, 5), 0L, 0, "water", 96, 64);
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> Math.hypot(x - 96D, z - 64D) <= 5D ? 94D
                : x <= 104D ? 100D : 100D - 0.5D * (x - 104D)), FINE, false, 1 << 16);
        ShapedNetwork shaped = new ReachShaper(settings).shape(WINDOW, flat, receivers, accumulation, channels,
                SeaCells.atOrBelow(flat, SEA_LEVEL), List.of(pond), fine, SEED);
        Reach outlet = byId(shaped.reaches()).get(shaped.ponds().get(0).outletReachId());
        assertNotNull("the pond drains through an outlet", outlet);
        assertEquals("the outlet is a river", "river", outlet.tier().name());
        Reach tributary = null;
        for (Reach reach : shaped.reaches()) {
            if (reach.downstreamId() == outlet.id()) {
                tributary = reach;
            }
        }
        assertNotNull("a tributary joins the outlet at its exit", tributary);
        float law = outlet.tier().halfWidth(outlet.catchment());
        float tributaryEnd = tributary.halfWidth()[tributary.halfWidth().length - 1];
        assertTrue("the tributary " + tributaryEnd + " is narrower than the outlet " + law, tributaryEnd < 0.5f * law);
        for (int point = 0; point < outlet.xs().length; point++) {
            assertEquals("outlet half-width at point " + point + " (" + outlet.xs()[point] + "," + outlet.zs()[point] + ")", law,
                    outlet.halfWidth()[point], 1e-3f);
        }
    }

    @Test
    public void reachEndingInOceanOrEdgeHasNoDownstream() {
        HydrologySettings settings = settings(config -> {
        });
        DoubleBinaryOperator coast = (x, z) -> fractal(SEED + 3L).applyAsDouble(x, z) - 0.06D * x;
        Network network = network(settings, coast, 64, 64, 2000f, false);
        ShapedNetwork shaped = shape(settings, network);
        int maxX = (network.grid().width() - 1) * CELL;
        int maxZ = (network.grid().depth() - 1) * CELL;
        int mouths = 0;
        int edges = 0;
        for (Reach reach : shaped.reaches()) {
            int last = reach.xs().length - 1;
            float x = reach.xs()[last];
            float z = reach.zs()[last];
            boolean edge = x == 0f || z == 0f || x == maxX || z == maxZ;
            if (reach.style()[last] == ReachStyle.MOUTH) {
                mouths++;
                assertEquals(reach.toString(), 0L, reach.downstreamId());
            }
            if (edge) {
                edges++;
                assertEquals(reach.toString(), 0L, reach.downstreamId());
            }
        }
        assertTrue("mouth ends " + mouths, mouths >= 2);
        assertTrue("edge ends " + edges, edges >= 2);
    }

    @Test
    public void oceanEntryIsMouthWithWaterfallLipBefore() {
        HydrologySettings settings = settings(config -> {
        });
        DoubleBinaryOperator cliff = (x, z) -> x > 204D ? 60D + 0.02D * (x - 204D) + 0.1D * Math.abs(z - VALLEY_Z) : 20D;
        ShapedNetwork shaped = shape(settings, network(settings, cliff, 64, 64, VALLEY_THRESHOLD, false));
        int falls = 0;
        for (Reach reach : shaped.reaches()) {
            int entry = -1;
            for (int index = 0; index < reach.style().length && entry < 0; index++) {
                entry = reach.style()[index] == ReachStyle.MOUTH ? index : -1;
            }
            if (entry < 1) {
                continue;
            }
            assertEquals(reach.toString(), SEA_LEVEL, reach.grade()[entry]);
            int fall = -1;
            for (int index = 1; index <= entry && fall < 0; index++) {
                fall = reach.grade()[index] == SEA_LEVEL ? index : -1;
            }
            if (reach.grade()[fall - 1] - SEA_LEVEL >= reach.tier().waterfallMinimumDrop()) {
                falls++;
                int lip = fall == entry ? entry - 1 : fall;
                assertEquals(reach.toString(), ReachStyle.WATERFALL, reach.style()[lip]);
                assertEquals(reach.toString(), ReachStyle.MOUTH, reach.style()[entry]);
            }
        }
        assertTrue("coastal falls " + falls, falls >= 1);

        int[] grade = {62, 62, 62, 40, 40};
        boolean[] ocean = {false, false, false, true, true};
        ReachStyle[] styles = ReachShaper.styles(grade, ocean, new boolean[grade.length], 6, SEA_LEVEL);
        assertEquals(List.of(ReachStyle.RUN, ReachStyle.RUN, ReachStyle.WATERFALL, ReachStyle.MOUTH, ReachStyle.MOUTH), List.of(styles));
    }

    @Test
    public void heldLipStepThatShowsAsTwoBlocksIsACascade() {
        int[] grade = {76, 76, 76, 75, 75};
        boolean[] ocean = new boolean[grade.length];
        boolean[] held = {true, true, true, false, false};
        assertEquals("a one-block step off a held lip that the even head snap shows as two blocks",
                List.of(ReachStyle.POND_OUTLET, ReachStyle.POND_OUTLET, ReachStyle.POND_OUTLET, ReachStyle.CASCADE, ReachStyle.RUN),
                List.of(ReachShaper.styles(grade, ocean, held, 6, SEA_LEVEL)));
        assertEquals("the same step hidden by the head snap", ReachStyle.RIFFLE, ReachShaper.styles(grade, ocean, held, 6, SEA_LEVEL + 1)[3]);
        assertEquals("a one-block step that leaves no held lip", ReachStyle.RIFFLE,
                ReachShaper.styles(grade, ocean, new boolean[grade.length], 6, SEA_LEVEL)[3]);
        int[] zoned = {78, 78, 77, 77, 77};
        boolean[] zone = {true, true, true, true, false};
        assertEquals("a one-block step inside an outlet zone that the head snap shows as two blocks",
                List.of(ReachStyle.POND_OUTLET, ReachStyle.POND_OUTLET, ReachStyle.CASCADE, ReachStyle.POND_OUTLET, ReachStyle.RUN),
                List.of(ReachShaper.styles(zoned, ocean, zone, 6, SEA_LEVEL)));
        assertEquals("the same step inside the zone hidden by the head snap", ReachStyle.POND_OUTLET,
                ReachShaper.styles(zoned, ocean, zone, 6, SEA_LEVEL + 1)[2]);
    }

    @Test
    public void channelWaterStaysBelowNaturalGroundOnSmoothValleys() {
        HydrologySettings settings = settings(config -> {
        });
        DoubleBinaryOperator terrain = (x, z) -> 200D - 0.12D * x + 16D * Math.abs(Math.sin(Math.PI * (z + 20D * Math.sin(x / 50D)) / 64D));
        ShapedNetwork shaped = shape(settings, network(settings, terrain, 64, 64, 2000f, false));
        int points = 0;
        for (Reach reach : shaped.reaches()) {
            for (int index = 0; index < reach.xs().length; index++) {
                double natural = terrain.applyAsDouble(reach.xs()[index], reach.zs()[index]);
                assertTrue(reach + " at " + index + " grade " + reach.grade()[index] + " natural " + natural, reach.grade()[index] <= natural);
                points++;
            }
        }
        assertTrue("points " + points, points > 1000);
    }

    @Test
    public void coarsePathsFollowCellCentresWithCatchment() {
        HydrologySettings settings = settings(config -> {
        });
        int cell = 64;
        int size = 96;
        double centre = size * cell / 2D;
        DoubleBinaryOperator island = (x, z) -> 200D - 0.06D * Math.hypot(x - centre, z - centre) + 8D * valueNoise(SEED, x / 300D, z / 300D);
        Network network = network(settings, island, 0, size, size, cell, 50000f, false);
        float minCatchment = settings.majorTier().minCatchment();
        byte[] allowed = new byte[network.receivers().length];
        Arrays.fill(allowed, HydrologySampleBuffer.ALLOW_STREAMS);
        BitSet candidates = ChannelHeads.candidates(network.grid(), network.accumulation(), allowed, minCatchment, SEA_LEVEL);
        BitSet channels = ChannelHeads.keep(network.grid(), network.grid().values(), network.receivers(), candidates,
                settings.drainage().headRelief(), index -> false, index -> true);
        List<Reach> paths = new ReachShaper(settings).coarsePaths(network.grid(), network.receivers(), network.accumulation(), channels,
                SeaCells.atOrBelow(network.grid(), SEA_LEVEL), SEED);
        assertFalse(paths.isEmpty());
        Map<Long, Reach> byId = byId(paths);
        int mouths = 0;
        for (Reach path : paths) {
            assertTrue(path.catchment() >= minCatchment);
            assertEquals(settings.tierFor(path.catchment()), path.tier());
            for (int index = 0; index < path.xs().length; index++) {
                assertEquals(0, Math.floorMod((int) path.xs()[index], cell));
                assertEquals(0, Math.floorMod((int) path.zs()[index], cell));
                if (index > 0) {
                    assertTrue(path.grade()[index] <= path.grade()[index - 1]);
                    assertTrue(Math.abs(path.xs()[index] - path.xs()[index - 1]) <= cell && Math.abs(path.zs()[index] - path.zs()[index - 1]) <= cell);
                }
            }
            int last = path.xs().length - 1;
            if (path.style()[last] == ReachStyle.MOUTH) {
                mouths++;
                assertEquals(SEA_LEVEL, path.grade()[last]);
                assertEquals(0L, path.downstreamId());
            }
            if (path.downstreamId() != 0L) {
                Reach downstream = byId.get(path.downstreamId());
                assertNotNull(downstream);
                assertEquals(downstream.xs()[0], path.xs()[last], 0f);
                assertEquals(downstream.zs()[0], path.zs()[last], 0f);
            }
        }
        assertTrue("mouths " + mouths, mouths >= 4);
    }

    @Test
    public void channelsOverRoughGroundNeverFoldBackOnThemselves() {
        HydrologySettings settings = settings(config -> {
        });
        for (int trial = 0; trial < 12; trial++) {
            ShapedNetwork shaped = shape(settings, network(settings, rough(SEED + trial), 64, 64, 1200f, trial % 2 == 0));
            for (Reach reach : shaped.reaches()) {
                assertNoFold("trial " + trial, reach);
            }
        }
    }

    private static void assertNetworkInvariants(String label, ShapedNetwork shaped, int[] totals) {
        Map<Long, Reach> byId = byId(shaped.reaches());
        for (Reach reach : shaped.reaches()) {
            totals[0]++;
            assertNoFold(label, reach);
            int[] grade = reach.grade();
            for (int index = 1; index < grade.length; index++) {
                assertTrue(label + " " + reach + " rises at " + index, grade[index] <= grade[index - 1]);
                totals[1] += grade[index] < grade[index - 1] ? 1 : 0;
            }
            for (int index = 0; index < grade.length; index++) {
                if (reach.style()[index] == ReachStyle.MOUTH) {
                    totals[3]++;
                    assertEquals(label + " " + reach, SEA_LEVEL, grade[index]);
                }
            }
            if (reach.downstreamId() != 0L) {
                totals[2]++;
                Reach downstream = byId.get(reach.downstreamId());
                assertNotNull(label + " " + reach, downstream);
                assertTrue(label + " " + reach + " into " + downstream, downstream.grade()[0] <= grade[grade.length - 1]);
            }
        }
        assertWaterNeverFlowsUphillIntoAPond(label, shaped);
        for (Pond pond : shaped.ponds()) {
            if (pond.outletReachId() != 0L) {
                totals[4]++;
                Reach outlet = byId.get(pond.outletReachId());
                assertNotNull(label + " pond " + pond.id(), outlet);
                assertTrue(label + " pond " + pond.level() + " outlet " + outlet.grade()[0], outlet.grade()[0] <= pond.level());
            }
        }
    }

    private static void assertNoFold(String label, Reach reach) {
        assertNoFold(label + " " + reach, reach.xs(), reach.zs(), 2f * (reach.tier().minHalfWidth() + 1f), 2f * (maximum(reach.halfWidth()) + 1f));
    }

    private static void assertNoFold(String label, float[] xs, float[] zs, float near, float gap) {
        for (int first = 0; first < xs.length; first++) {
            float along = 0f;
            for (int second = first + 1; second < xs.length; second++) {
                along += (float) Math.hypot(xs[second] - xs[second - 1], zs[second] - zs[second - 1]);
                float apart = (float) Math.hypot(xs[second] - xs[first], zs[second] - zs[first]);
                assertTrue(label + " folds back from point " + first + " to " + second + ": " + apart + " apart after "
                        + along + " along", apart >= near || along < apart + gap);
            }
        }
    }

    private static int dropsAbove(Reach reach, int floor, int minimum, int limit) {
        int[] grade = reach.grade();
        int count = 0;
        for (int index = 1; index < grade.length; index++) {
            int drop = grade[index - 1] - grade[index];
            count += drop >= minimum && drop < limit && grade[index] > floor ? 1 : 0;
        }
        return count;
    }

    private static float firstAtOrBelow(ShapedNetwork shaped, int grade, boolean eastward) {
        float best = eastward ? Float.MAX_VALUE : -Float.MAX_VALUE;
        for (Reach reach : shaped.reaches()) {
            for (int index = 0; index < reach.xs().length; index++) {
                if (Math.abs(reach.zs()[index] - VALLEY_Z) <= 12f && reach.grade()[index] <= grade) {
                    best = eastward ? Math.min(best, reach.xs()[index]) : Math.max(best, reach.xs()[index]);
                }
            }
        }
        assertTrue("no point reaches grade " + grade, Math.abs(best) < Float.MAX_VALUE);
        return best;
    }

    private static float filledAt(DemGrid grid, float x, float z) {
        int i = Math.clamp(Math.round((x - grid.originX()) / grid.cell()), 0, grid.width() - 1);
        int j = Math.clamp(Math.round((z - grid.originZ()) / grid.cell()), 0, grid.depth() - 1);
        return grid.get(i, j);
    }

    private static DoubleBinaryOperator rolling(long seed, double amplitude) {
        return (x, z) -> {
            double height = 95D - 0.02D * x;
            double scale = amplitude;
            double wavelength = 256D;
            for (int octave = 0; octave < 5; octave++) {
                height += scale * valueNoise(seed * 31L + octave, x / wavelength, z / wavelength);
                scale *= 0.5D;
                wavelength *= 0.5D;
            }
            return height;
        };
    }

    private static void flow(DemGrid grid, int[] receivers, float[] accumulation, BitSet channels, int i, int j, int toI, int toJ,
                             float catchment) {
        int cell = grid.index(i, j);
        receivers[cell] = toI < 0 ? -1 : grid.index(toI, toJ);
        accumulation[cell] = catchment;
        channels.set(cell);
    }

    private static DemGrid outletGrid() {
        DemGrid flat = new DemGrid(0, 0, CELL, 32, 16);
        Arrays.fill(flat.values(), 100f);
        return flat;
    }

    private static void outletRow(DemGrid grid, int[] receivers, float[] accumulation, BitSet channels) {
        Arrays.fill(receivers, -1);
        for (int i = 0; i < grid.width(); i++) {
            flow(grid, receivers, accumulation, channels, i, 8, i + 1 < grid.width() ? i + 1 : -1, 8, 1600f + 10f * i);
        }
    }

    private static void assertOutletLeavesAtThePondLevel(String label, Pond pond, Reach outlet) {
        assertNotNull(label, outlet);
        assertTrue(label + " starts off the shore at " + outlet.xs()[0] + "," + outlet.zs()[0],
                pond.mask().contains(Math.round(outlet.xs()[0]), Math.round(outlet.zs()[0])));
        assertOutletTouchesAtThePondLevel(label, pond, outlet);
    }

    private static void assertOutletTouchesAtThePondLevel(String label, Pond pond, Reach outlet) {
        PondMask mask = pond.mask();
        int touched = 0;
        for (int j = 0; j < mask.depth(); j++) {
            for (int i = 0; i < mask.width(); i++) {
                if (!mask.node(i, j)) {
                    continue;
                }
                int x = mask.originX() + i * mask.step();
                int z = mask.originZ() + j * mask.step();
                int[][] cells = {{x, z}, {x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
                for (int[] cell : cells) {
                    int point = coveringPoint(outlet, cell[0], cell[1]);
                    if (point >= 0) {
                        touched++;
                        assertEquals(label + " water at " + cell[0] + "," + cell[1], pond.level(), outlet.grade()[point]);
                    }
                }
            }
        }
        assertTrue(label + " water touches the pond", touched > 0);
    }

    private static int coveringPoint(Reach reach, int x, int z) {
        double nearest = Double.POSITIVE_INFINITY;
        int point = -1;
        for (int segment = 0; segment + 1 < reach.xs().length; segment++) {
            double ax = reach.xs()[segment];
            double az = reach.zs()[segment];
            double dx = reach.xs()[segment + 1] - ax;
            double dz = reach.zs()[segment + 1] - az;
            double length = dx * dx + dz * dz;
            double t = length == 0D ? 0D : Math.clamp(((x - ax) * dx + (z - az) * dz) / length, 0D, 1D);
            double distance = Math.hypot(x - (ax + t * dx), z - (az + t * dz));
            double width = reach.halfWidth()[segment] + t * (reach.halfWidth()[segment + 1] - reach.halfWidth()[segment]);
            if (distance <= width && distance < nearest) {
                nearest = distance;
                point = t < 0.5D ? segment : segment + 1;
            }
        }
        return point;
    }

    private static PondMask disk(int centreX, int centreZ, int radius) {
        int originX = centreX - radius - 1;
        int originZ = centreZ - radius - 1;
        int size = 2 * radius + 3;
        long[] bits = new long[(size * size + 63) >>> 6];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                if (Math.hypot(originX + i - centreX, originZ + j - centreZ) <= radius) {
                    int index = j * size + i;
                    bits[index >>> 6] |= 1L << index;
                }
            }
        }
        return new PondMask(originX, originZ, 1, size, size, bits);
    }

    private static PondMask rectangle(int originX, int originZ, int width, int depth) {
        long[] bits = new long[(width * depth + 63) >>> 6];
        for (int index = 0; index < width * depth; index++) {
            bits[index >>> 6] |= 1L << index;
        }
        return new PondMask(originX, originZ, FINE, width, depth, bits);
    }

    private static float minimum(float[] values) {
        float minimum = Float.POSITIVE_INFINITY;
        for (float value : values) {
            minimum = Math.min(minimum, value);
        }
        return minimum;
    }

    private static float maximum(float[] values) {
        float maximum = 0f;
        for (float value : values) {
            maximum = Math.max(maximum, value);
        }
        return maximum;
    }

    private static List<Long> splitsBeyond(ShapedNetwork shaped, float minX) {
        List<Long> ids = new ArrayList<>();
        for (Reach reach : shaped.reaches()) {
            if (reach.xs()[0] >= minX) {
                ids.add(reach.id());
            }
        }
        ids.sort(Long::compare);
        return ids;
    }

    private static ReachDraft draft(HydrologyTier tier, float... ground) {
        int points = ground.length;
        float[] xs = new float[points];
        for (int point = 0; point < points; point++) {
            xs[point] = STATION * point;
        }
        ReachFitter.Fitted fitted = new ReachFitter.Fitted(xs, new float[points], new float[points], ground.clone(),
                new boolean[points], ground.clone());
        return new ReachDraft(new ReachTracer.Chain(new int[]{0, 1}, ReachTracer.End.EDGE, -1), 1L, tier, 0f, Integer.MIN_VALUE, fitted);
    }

    private static List<Drop> drops(HydrologySettings settings, DoubleBinaryOperator terrain) {
        Network network = network(settings, terrain, 64, 64, VALLEY_THRESHOLD, false);
        ShapedNetwork shaped = shape(settings, network);
        List<Drop> drops = new ArrayList<>();
        int covered = 0;
        for (Reach reach : shaped.reaches()) {
            for (int index = 1; index < reach.xs().length; index++) {
                if (reach.xs()[index] < WINDOW_MIN_X || reach.xs()[index] > WINDOW_MAX_X) {
                    continue;
                }
                covered++;
                int size = reach.grade()[index - 1] - reach.grade()[index];
                if (size > 0) {
                    drops.add(new Drop(size, reach.style()[index], reach.xs()[index]));
                }
            }
        }
        assertTrue("points in the step window " + covered, covered > 50);
        return drops;
    }

    private static DoubleBinaryOperator pittedValley(int pitX, double depth) {
        return (x, z) -> 150D - 0.05D * x + 0.1D * Math.abs(z - VALLEY_Z) - (Math.hypot(x - pitX, z - VALLEY_Z) <= 3D ? depth : 0D);
    }

    private static boolean overPit(float x, float z, int pitX) {
        return Math.abs(x - pitX) <= 2f && Math.abs(z - VALLEY_Z) <= 2f;
    }

    private static DoubleBinaryOperator steppedValley(int drop) {
        return (x, z) -> {
            double base = x < 2 * CELL ? 140D : x < STEP_X ? 120D : 120D - drop;
            return base + 0.1D * Math.abs(z - VALLEY_Z);
        };
    }

    private static DoubleBinaryOperator hangingValley() {
        return (x, z) -> {
            double ground = 150D - 0.05D * x + 0.1D * Math.abs(z - VALLEY_Z);
            double across = Math.abs(x - HANGING_X);
            return z < VALLEY_Z - 2 && across <= 32D ? ground + 20D + 0.25D * across : ground;
        };
    }

    private static DoubleBinaryOperator bowlValley(int[] centres) {
        return (x, z) -> {
            double height = 150D - 0.05D * x + 0.1D * Math.abs(z - VALLEY_Z);
            for (int centre : centres) {
                double ratio = Math.hypot(x - centre, z - VALLEY_Z) / 40D;
                height -= 10D * Math.max(0D, 1D - ratio * ratio);
            }
            return height;
        };
    }

    private static DoubleBinaryOperator randomTerrain(Random random) {
        double base = 50D + random.nextDouble() * 90D;
        double tiltX = (random.nextDouble() - 0.5D) * 0.4D;
        double tiltZ = (random.nextDouble() - 0.5D) * 0.4D;
        int waves = 4;
        double[] amplitude = new double[waves];
        double[] frequencyX = new double[waves];
        double[] frequencyZ = new double[waves];
        double[] phase = new double[waves];
        for (int wave = 0; wave < waves; wave++) {
            double length = 60D + random.nextDouble() * 240D;
            double angle = random.nextDouble() * 2D * Math.PI;
            amplitude[wave] = 4D + random.nextDouble() * 24D;
            frequencyX[wave] = 2D * Math.PI / length * Math.cos(angle);
            frequencyZ[wave] = 2D * Math.PI / length * Math.sin(angle);
            phase[wave] = random.nextDouble() * 2D * Math.PI;
        }
        double terrace = random.nextBoolean() ? 0D : 2D + random.nextInt(7);
        return (x, z) -> {
            double height = base + tiltX * x + tiltZ * z;
            for (int wave = 0; wave < waves; wave++) {
                height += amplitude[wave] * Math.sin(frequencyX[wave] * x + frequencyZ[wave] * z + phase[wave]);
            }
            if (terrace > 0D) {
                double step = Math.floor(height / terrace) * terrace;
                height = step + 0.2D * (height - step);
            }
            return height;
        };
    }

    private static DoubleBinaryOperator fractal(long seed) {
        return (x, z) -> {
            double height = 120D - 0.08D * x;
            double amplitude = 40D;
            double wavelength = 160D;
            for (int octave = 0; octave < 4; octave++) {
                height += amplitude * valueNoise(seed + octave, x / wavelength, z / wavelength);
                amplitude *= 0.5D;
                wavelength *= 0.5D;
            }
            return height;
        };
    }

    private static DoubleBinaryOperator rough(long seed) {
        DoubleBinaryOperator coarse = fractal(seed);
        return (x, z) -> coarse.applyAsDouble(x, z) + 3D * valueNoise(seed * 31L, x / 6D, z / 6D)
                + 1.5D * valueNoise(seed * 37L, x / 3D, z / 3D);
    }

    private static double valueNoise(long seed, double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = smooth(x - x0);
        double fz = smooth(z - z0);
        double top = lerp(corner(seed, x0, z0), corner(seed, x0 + 1, z0), fx);
        double bottom = lerp(corner(seed, x0, z0 + 1), corner(seed, x0 + 1, z0 + 1), fx);
        return lerp(top, bottom, fz);
    }

    private static double corner(long seed, int x, int z) {
        return 2D * CellHash.unit(CellHash.mix(seed, 0x4e4f495345L, x, z)) - 1D;
    }

    private static double smooth(double t) {
        return t * t * (3D - 2D * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static ReachGrader.PondChain noPonds(int drafts) {
        int[] inflowPond = new int[drafts];
        Arrays.fill(inflowPond, -1);
        return new ReachGrader.PondChain(new int[0], new boolean[0], inflowPond, new int[0]);
    }

    private static void assertWaterNeverFlowsUphillIntoAPond(String label, ShapedNetwork shaped) {
        Map<Long, Reach> byId = byId(shaped.reaches());
        for (Pond pond : shaped.ponds()) {
            for (Reach reach : shaped.reaches()) {
                int last = reach.grade().length - 1;
                if (reach.downstreamId() == 0L && endsIn(reach, pond)) {
                    assertTrue(label + " " + reach + " enters pond " + pond.id() + " at " + reach.grade()[last] + " below its level "
                            + pond.level(), reach.grade()[last] >= pond.level());
                }
            }
            Reach outlet = byId.get(pond.outletReachId());
            while (outlet != null && outlet.downstreamId() != 0L) {
                outlet = byId.get(outlet.downstreamId());
            }
            for (Pond next : shaped.ponds()) {
                if (outlet != null && next != pond && endsIn(outlet, next)) {
                    assertTrue(label + " pond " + pond.id() + " at " + pond.level() + " feeds pond " + next.id() + " at " + next.level(),
                            next.level() < pond.level() || next.level() == SEA_LEVEL && pond.level() == SEA_LEVEL);
                }
            }
        }
    }

    private static boolean endsIn(Reach reach, Pond pond) {
        int last = reach.xs().length - 1;
        return pond.mask().contains(Math.round(reach.xs()[last]), Math.round(reach.zs()[last]));
    }

    private static List<Reach> upstreamOf(Reach reach, List<Reach> reaches, Map<Long, Reach> byId) {
        List<Reach> found = new ArrayList<>();
        found.add(reach);
        for (int index = 0; index < found.size(); index++) {
            long target = found.get(index).id();
            for (Reach candidate : reaches) {
                if (candidate.downstreamId() == target && byId.containsKey(candidate.id()) && !found.contains(candidate)) {
                    found.add(candidate);
                }
            }
        }
        return found;
    }

    private static Reach coastalOutlet(PondKind kind) {
        HydrologySettings settings = settings(config -> {
        });
        int width = 26;
        DemGrid surface = new DemGrid(0, 0, CELL, width, 3);
        Arrays.fill(surface.values(), 60f);
        int[] receivers = new int[width * 3];
        Arrays.fill(receivers, -1);
        float[] accumulation = new float[width * 3];
        BitSet channels = new BitSet(width * 3);
        for (int i = 0; i < 21; i++) {
            flow(surface, receivers, accumulation, channels, i, 1, i + 1, 1, 1600f + 10f * i);
        }
        surface.set(21, 1, 30f);
        Pond pond = new Pond(73L, kind, OUTLET_POND_LEVEL, OUTLET_POND_LEVEL - 4, 136, 8, OUTLET_POND_LEVEL + 0.5f, disk(128, 8, 7), 0L, 0,
                "water", 128, 8);
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> x < 160D ? SEA_LEVEL + 4D : SEA_LEVEL - 5D), FINE, false, 1 << 16);
        ShapedNetwork shaped = new ReachShaper(settings).shape(WINDOW, surface, receivers, accumulation, channels,
                SeaCells.atOrBelow(surface, SEA_LEVEL), List.of(pond), fine, SEED);
        Pond shapedPond = shaped.ponds().get(0);
        assertEquals(kind + " level", OUTLET_POND_LEVEL, shapedPond.level());
        Reach outlet = byId(shaped.reaches()).get(shapedPond.outletReachId());
        assertNotNull(kind + " outlet", outlet);
        assertEquals(ReachStyle.MOUTH, outlet.style()[outlet.style().length - 1]);
        return outlet;
    }

    private static void assertEstuaryInland(Reach outlet) {
        int inland = 0;
        for (int point = 0; point < outlet.xs().length; point++) {
            if (outlet.style()[point] == ReachStyle.MOUTH) {
                assertEquals(outlet + " at " + point, SEA_LEVEL, outlet.grade()[point]);
                inland += outlet.xs()[point] < 156f ? 1 : 0;
            }
        }
        assertTrue("estuary points over the low land " + inland, inland > 0);
    }

    private static Reach coastalRun(HydrologySettings settings, boolean ocean) {
        return coastalRun(settings, ocean, 0.08D);
    }

    private static Reach coastalRun(HydrologySettings settings, boolean ocean, double rise) {
        int width = 24;
        DemGrid surface = new DemGrid(0, 0, CELL, width, 3);
        Arrays.fill(surface.values(), 60f);
        int[] receivers = new int[width * 3];
        Arrays.fill(receivers, -1);
        float[] accumulation = new float[width * 3];
        BitSet channels = new BitSet(width * 3);
        for (int i = 0; i < width; i++) {
            int cell = surface.index(i, 1);
            receivers[cell] = i + 1 < width ? surface.index(i + 1, 1) : -1;
            accumulation[cell] = (i + 1) * 1000f;
            if (i + 1 < width) {
                channels.set(cell);
            }
        }
        if (ocean) {
            surface.set(width - 1, 1, 30f);
        }
        float mouthX = (width - 1) * CELL;
        HeightLattice fine = new HeightLattice(new Terrain((x, z) -> SEA_LEVEL + 1D + rise * Math.max(0D, mouthX - x)), FINE, false, 1 << 16);
        ShapedNetwork shaped = new ReachShaper(settings).shape(WINDOW, surface, receivers, accumulation, channels,
                SeaCells.atOrBelow(surface, SEA_LEVEL), List.of(), fine, SEED);
        assertEquals(1, shaped.reaches().size());
        return shaped.reaches().get(0);
    }

    private static ShapedNetwork cleanDelta(HydrologySettings settings, long seed) {
        return DeltaCoast.shape(settings, 0, 0, List.of(), DeltaCoast.NO_NEIGHBOUR, seed);
    }

    private static CleanFan cleanFan(HydrologySettings settings) {
        List<Reach> clean = DeltaCoast.deltaChannels(cleanDelta(settings, SEED));
        assertEquals("two delta channels on the clean coast", 2, clean.size());
        Reach first = clean.get(0);
        boolean firstNorth = first.zs()[first.xs().length - 1] > DeltaCoast.ROW;
        return firstNorth ? new CleanFan(first, clean.get(1)) : new CleanFan(clean.get(1), first);
    }

    private static List<Reach> distributaries(ShapedNetwork shaped, Reach parent) {
        List<Reach> fan = new ArrayList<>();
        for (Reach reach : DeltaCoast.deltaChannels(shaped)) {
            if (branchesFrom(reach, parent)) {
                fan.add(reach);
            }
        }
        return fan;
    }

    private static boolean branchesFrom(Reach channel, Reach parent) {
        int point = coveringPoint(parent, Math.round(channel.xs()[0]), Math.round(channel.zs()[0]));
        return point >= 0 && parent.grade()[point] == SEA_LEVEL;
    }

    private static void assertDeltaClear(ShapedNetwork shaped, List<Pond> ponds) {
        List<Reach> fan = DeltaCoast.deltaChannels(shaped);
        for (Reach channel : fan) {
            Reach parent = null;
            for (Reach reach : shaped.reaches()) {
                if (reach.style()[0] != ReachStyle.MOUTH && branchesFrom(channel, reach)) {
                    parent = reach;
                }
            }
            assertNotNull(channel + " branches from a river's estuary", parent);
            for (Reach other : shaped.reaches()) {
                boolean sibling = other != channel && fan.contains(other) && branchesFrom(other, parent)
                        && other.xs()[0] == channel.xs()[0] && other.zs()[0] == channel.zs()[0];
                if (other == channel || other == parent || sibling) {
                    continue;
                }
                assertFalse(channel + " crosses " + other, crossesWater(channel, other));
            }
            for (Reach sibling : fan) {
                if (sibling != channel && sibling.xs()[0] == channel.xs()[0] && sibling.zs()[0] == channel.zs()[0]) {
                    assertFalse(channel + " and " + sibling + " cross", linesCross(channel, sibling));
                }
            }
            for (Pond pond : ponds) {
                assertFalse(channel + " crosses pond " + pond.id(), coversMask(channel, pond.mask()));
            }
            for (int z = (int) Math.floor(minimum(channel.zs())) - 16; z <= (int) Math.ceil(maximum(channel.zs())) + 16; z++) {
                for (int x = (int) Math.floor(minimum(channel.xs())) - 16; x <= (int) Math.ceil(maximum(channel.xs())) + 16; x++) {
                    int point = coveringPoint(parent, x, z);
                    assertTrue(channel + " meets its river above the sea at " + x + "," + z,
                            coveringPoint(channel, x, z) < 0 || point < 0 || parent.grade()[point] == SEA_LEVEL);
                }
            }
        }
    }

    private static boolean crossesWater(Reach channel, Reach other) {
        for (int z = (int) Math.floor(minimum(channel.zs())) - 16; z <= (int) Math.ceil(maximum(channel.zs())) + 16; z++) {
            for (int x = (int) Math.floor(minimum(channel.xs())) - 16; x <= (int) Math.ceil(maximum(channel.xs())) + 16; x++) {
                if (coveringPoint(channel, x, z) >= 0 && coveringPoint(other, x, z) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean coversMask(Reach reach, PondMask mask) {
        for (int j = 0; j < mask.depth(); j++) {
            for (int i = 0; i < mask.width(); i++) {
                if (mask.node(i, j) && coveringPoint(reach, mask.originX() + i * mask.step(), mask.originZ() + j * mask.step()) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean linesCross(Reach first, Reach second) {
        for (int a = 1; a + 1 < first.xs().length; a++) {
            for (int b = 1; b + 1 < second.xs().length; b++) {
                if (segmentsCross(first.xs()[a], first.zs()[a], first.xs()[a + 1], first.zs()[a + 1], second.xs()[b], second.zs()[b],
                        second.xs()[b + 1], second.zs()[b + 1])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean segmentsCross(double ax, double az, double bx, double bz, double cx, double cz, double dx, double dz) {
        double first = turn(ax, az, bx, bz, cx, cz);
        double second = turn(ax, az, bx, bz, dx, dz);
        double third = turn(cx, cz, dx, dz, ax, az);
        double fourth = turn(cx, cz, dx, dz, bx, bz);
        return first * second < 0D && third * fourth < 0D;
    }

    private static double turn(double ax, double az, double bx, double bz, double px, double pz) {
        return (bx - ax) * (pz - az) - (bz - az) * (px - ax);
    }

    private static List<String> ends(List<Reach> fan) {
        List<String> ends = new ArrayList<>();
        for (Reach channel : fan) {
            int last = channel.xs().length - 1;
            ends.add(channel.xs()[last] + "," + channel.zs()[last]);
        }
        return ends;
    }

    private static double fineNode(float coordinate) {
        return Math.floor(coordinate / FINE + 0.5D) * FINE;
    }

    private static int trailingMouth(Reach reach) {
        int start = reach.style().length;
        while (start > 0 && reach.style()[start - 1] == ReachStyle.MOUTH) {
            start--;
        }
        assertTrue(reach + " ends in a mouth", start < reach.style().length);
        return start;
    }

    private static int firstBeyond(Reach reach, float distance) {
        float travelled = 0f;
        for (int index = 1; index < reach.xs().length; index++) {
            travelled += (float) Math.hypot(reach.xs()[index] - reach.xs()[index - 1], reach.zs()[index] - reach.zs()[index - 1]);
            if (travelled >= distance) {
                return index;
            }
        }
        return -1;
    }

    private static Map<Long, Reach> byId(List<Reach> reaches) {
        Map<Long, Reach> byId = new HashMap<>();
        for (Reach reach : reaches) {
            assertTrue("duplicate id " + reach, byId.put(reach.id(), reach) == null);
        }
        return byId;
    }

    private static Map<Long, Integer> incoming(List<Reach> reaches) {
        Map<Long, Integer> incoming = new HashMap<>();
        for (Reach reach : reaches) {
            if (reach.downstreamId() != 0L) {
                incoming.merge(reach.downstreamId(), 1, Integer::sum);
            }
        }
        return incoming;
    }

    private static Map<Long, List<Reach>> feeders(List<Reach> reaches) {
        Map<Long, List<Reach>> feeders = new HashMap<>();
        for (Reach reach : reaches) {
            if (reach.downstreamId() != 0L) {
                feeders.computeIfAbsent(reach.downstreamId(), id -> new ArrayList<>()).add(reach);
            }
        }
        return feeders;
    }

    private static List<ConfluenceGrader.Footprint> receiving(Reach downstream, List<Reach> siblings, Reach feeder) {
        List<ConfluenceGrader.Footprint> receiving = new ArrayList<>(siblings.size());
        receiving.add(footprint(downstream));
        for (Reach sibling : siblings) {
            if (sibling != feeder) {
                receiving.add(footprint(sibling));
            }
        }
        return receiving;
    }

    private static ConfluenceGrader.Footprint footprint(Reach reach) {
        return new ConfluenceGrader.Footprint(reach.xs(), reach.zs(), reach.halfWidth());
    }

    private static double clearance(List<ConfluenceGrader.Footprint> receiving, float x, float z) {
        double clearance = Double.POSITIVE_INFINITY;
        for (ConfluenceGrader.Footprint channel : receiving) {
            clearance = Math.min(clearance, ConfluenceGrader.edgeDistance(channel, x, z));
        }
        return clearance;
    }

    private static void noCascades(IrisHydrology config) {
        for (IrisHydrologyTier tier : config.getTiers()) {
            tier.setCascades(false);
        }
    }

    private static void ungrouped(IrisHydrology config) {
        for (IrisHydrologyTier tier : config.getTiers()) {
            tier.setCascades(false);
            tier.setWaterfallMinimumDrop(2);
        }
    }

    private static HydrologyTier withSteps(HydrologyTier tier, boolean cascades, int waterfallMinimumDrop) {
        return new HydrologyTier(tier.name(), tier.minCatchment(), tier.minHalfWidth(), tier.maxHalfWidth(), tier.widthFactor(), tier.depthRatio(),
                tier.inset(), cascades, waterfallMinimumDrop, tier.bankBand(), tier.bankStyle(), tier.estuary(), tier.delta(), tier.materials(),
                tier.fluid(), tier.decorators());
    }

    private static int[] graded(HydrologyTier tier, float[] ground) {
        ReachDraft draft = draft(tier, ground);
        ReachGrader.grade(List.of(draft), new int[]{0}, 0, 6, noPonds(1), SEED);
        return draft.grade;
    }

    private static HydrologySettings settings(Consumer<IrisHydrology> edit) {
        IrisHydrology config = new IrisHydrology();
        edit.accept(config);
        return HydrologySettings.compile(config, SEED, SEA_LEVEL, MIN_Y, MAX_Y);
    }

    private static ShapedNetwork shape(HydrologySettings settings, Network network) {
        return shape(settings, network, SEED);
    }

    private static ShapedNetwork shape(HydrologySettings settings, Network network, long seed) {
        return new ReachShaper(settings).shape(WINDOW, network.grid(), network.receivers(), network.accumulation(), network.channels(),
                SeaCells.atOrBelow(network.grid(), SEA_LEVEL), network.ponds(), network.fine(), seed);
    }

    private static Network network(HydrologySettings settings, DoubleBinaryOperator height, int width, int depth, float threshold,
                                   boolean withPonds) {
        return network(settings, height, 0, width, depth, CELL, threshold, withPonds);
    }

    private static Network network(HydrologySettings settings, DoubleBinaryOperator height, int originX, int width, int depth,
                                   int cell, float threshold, boolean withPonds) {
        return network(settings, height, originX, 0, width, depth, cell, threshold, withPonds);
    }

    private static Network network(HydrologySettings settings, DoubleBinaryOperator height, int originX, int originZ, int width, int depth,
                                   int cell, float threshold, boolean withPonds) {
        Terrain terrain = new Terrain(height);
        int count = width * depth;
        HydrologySampleBuffer samples = new HydrologySampleBuffer(count);
        new HeightLattice(terrain, cell, false, 1 << 20).fill(Math.floorDiv(originX, cell), Math.floorDiv(originZ, cell), width, depth, samples);
        DemGrid raw = new DemGrid(originX, originZ, cell, width, depth);
        System.arraycopy(samples.height(), 0, raw.values(), 0, count);
        BitSet seeds = new BitSet(count);
        for (int j = 0; j < depth; j++) {
            for (int i = 0; i < width; i++) {
                int index = raw.index(i, j);
                if (i == 0 || j == 0 || i == width - 1 || j == depth - 1 || samples.ocean()[index]) {
                    seeds.set(index);
                }
            }
        }
        float[] filled = PriorityFlood.fill(raw, seeds, settings.drainage().fillEpsilon());
        HeightLattice fine = new HeightLattice(terrain, FINE, false, 1 << 20);
        List<Pond> ponds = List.of();
        if (withPonds) {
            List<Basin> basins = BasinExtractor.basins(raw, filled, 0.5f, SEA_LEVEL);
            ponds = new PondPlanner(settings).ponds(raw, filled, basins, List.of(), fine, CupSet.EMPTY);
        }
        DemGrid surface = new DemGrid(originX, originZ, cell, width, depth);
        System.arraycopy(filled, 0, surface.values(), 0, count);
        int[] receivers = D8Router.receivers(surface, filled);
        for (int index = 0; index < count; index++) {
            if (samples.ocean()[index]) {
                receivers[index] = -1;
            }
        }
        float[] weights = new float[count];
        Arrays.fill(weights, (float) cell * cell);
        float[] accumulation = FlowAccumulator.accumulate(receivers, filled, weights, settings.drainage().saturationCatchment());
        BitSet channels = new BitSet(count);
        for (int index = 0; index < count; index++) {
            if (accumulation[index] >= threshold && filled[index] > SEA_LEVEL) {
                channels.set(index);
            }
        }
        return new Network(surface, receivers, accumulation, channels, ponds, fine);
    }

    private record CleanFan(Reach north, Reach south) {
    }

    private record Network(DemGrid grid, int[] receivers, float[] accumulation, BitSet channels, List<Pond> ponds, HeightLattice fine) {
    }

    private record Drop(int size, ReachStyle style, float x) {
    }

    private static final class Terrain implements HydrologyTerrainSource {
        private final DoubleBinaryOperator height;

        private Terrain(DoubleBinaryOperator height) {
            this.height = height;
        }

        @Override
        public void sample(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly) {
            for (int index = 0; index < xs.length; index++) {
                float value = (float) height.applyAsDouble(xs[index], zs[index]);
                out.height()[index] = value;
                out.ocean()[index] = value <= SEA_LEVEL;
                out.rainfall()[index] = 1f;
                out.pondDensity()[index] = 1f;
                out.permission()[index] = ALLOW_ALL;
                out.fluidProfile()[index] = null;
            }
        }

        @Override
        public int seaLevel() {
            return SEA_LEVEL;
        }

        @Override
        public int minY() {
            return MIN_Y;
        }

        @Override
        public int maxY() {
            return MAX_Y;
        }
    }
}
