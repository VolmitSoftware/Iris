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
    private static final int SEA_LEVEL = 40;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;
    private static final int CELL = 8;
    private static final int FINE = 4;
    private static final int VALLEY_Z = 256;
    private static final float VALLEY_THRESHOLD = 5000f;
    private static final int STEP_X = 196;
    private static final int WINDOW_MIN_X = 140;
    private static final int WINDOW_MAX_X = 250;
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
                assertTrue("trial " + trial + " " + reach + " gained a waterfall", count(reach, ReachStyle.WATERFALL) <= count(twin, ReachStyle.WATERFALL));
                spreadSteps += count(reach, ReachStyle.CASCADE);
                cascadingSteps += count(twin, ReachStyle.CASCADE);
            }
        }
        assertTrue("reaches " + totals[0], totals[0] > 4000);
        assertTrue("drops " + totals[1], totals[1] > 4000);
        assertTrue("links " + totals[2], totals[2] > 1000);
        assertTrue("mouth points " + totals[3], totals[3] > 100);
        assertTrue("pond outlets " + totals[4], totals[4] > 40);
        assertTrue("cascade steps spread " + spreadSteps + " kept " + cascadingSteps, 2 * spreadSteps < cascadingSteps);
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
        ReachGrader.grade(List.of(left, right, trunk), new int[]{0, 1, 2}, 0, 6, noPonds(3));
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
                    new ReachGrader.PondChain(new int[]{upper, upper}, new boolean[2], new int[]{1}, new int[]{0, -1}));
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
        ReachGrader.grade(List.of(steep), new int[]{0}, 0, 6, noPonds(1));
        int[] grade = steep.grade;
        for (int index = 1; index < grade.length; index++) {
            int drop = grade[index - 1] - grade[index];
            assertTrue(Arrays.toString(grade), drop >= 0 && drop < tier.waterfallMinimumDrop());
        }
        assertTrue(Arrays.toString(grade), grade[0] > (int) ground[0] - tier.inset() - tier.waterfallMinimumDrop());
        assertEquals((int) ground[ground.length - 1] - tier.inset(), grade[grade.length - 1]);
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
        ShapedNetwork wider = new ReachShaper(settings).shape(network.grid(), network.receivers(), quadrupled, network.channels(),
                network.ponds(), network.fine(), SEED);
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
            if (head && firstBeyond(reach, 24f) < 0) {
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
        assertTrue("outlet starts " + outlet.xs()[0] + "," + outlet.zs()[0] + " spill " + upper.spillX() + "," + upper.spillZ(),
                Math.hypot(outlet.xs()[0] - upper.spillX(), outlet.zs()[0] - upper.spillZ()) <= 1.5D * CELL);
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
        ShapedNetwork shaped = shape(settings, network(settings, shallow, 64, 64, VALLEY_THRESHOLD, false));
        int checked = 0;
        int pooled = 0;
        for (Reach reach : shaped.reaches()) {
            for (int index = 0; index < reach.xs().length; index++) {
                float x = reach.xs()[index];
                float z = reach.zs()[index];
                int natural = (int) Math.floor(shallow.applyAsDouble(x, z)) - reach.tier().inset();
                if (x >= pitX + 48 && x <= pitX + 200 && Math.abs(z - VALLEY_Z) <= 12f) {
                    checked++;
                    assertTrue(reach + " at " + x + " grade " + reach.grade()[index] + " natural " + natural, reach.grade()[index] >= natural - 1);
                }
                if (overPit(x, z, pitX)) {
                    pooled += reach.grade()[index] > (int) Math.floor(shallow.applyAsDouble(pitX, VALLEY_Z)) ? 1 : 0;
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
        ShapedNetwork shaped = new ReachShaper(settings).shape(flat, receivers, accumulation, channels, List.of(pond), fine, SEED);
        Reach outlet = byId(shaped.reaches()).get(shaped.ponds().get(0).outletReachId());
        assertNotNull(outlet);
        assertEquals(120f, outlet.xs()[0], 0f);
        assertEquals(64f, outlet.zs()[0], 0f);
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
        ShapedNetwork shaped = new ReachShaper(settings).shape(surface, receivers, accumulation, channels, List.of(), fine, SEED);
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
        ShapedNetwork shaped = new ReachShaper(settings).shape(surface, receivers, accumulation, channels, List.of(sunken), fine, SEED);
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
    public void oceanPointsHoldSeaLevel() {
        HydrologySettings settings = settings(config -> {
        });
        int inletX = 204;
        DoubleBinaryOperator terrain = (x, z) -> 60D - 0.05D * x + 0.1D * Math.abs(z - VALLEY_Z)
                - (Math.abs(x - inletX) <= 2D && Math.abs(z - VALLEY_Z) <= 20D ? 10.3D : 0D);
        ShapedNetwork shaped = shape(settings, network(settings, terrain, 64, 64, VALLEY_THRESHOLD, false));
        int overInlet = 0;
        for (Reach reach : shaped.reaches()) {
            for (int index = 0; index < reach.xs().length; index++) {
                if (reach.style()[index] == ReachStyle.MOUTH) {
                    assertEquals(reach + " at " + index, SEA_LEVEL, reach.grade()[index]);
                    overInlet += Math.abs(reach.xs()[index] - inletX) <= 2f ? 1 : 0;
                }
            }
        }
        assertTrue("mouth points over the inlet " + overInlet, overInlet > 0);
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
        ShapedNetwork shaped = new ReachShaper(settings).shape(flat, receivers, accumulation, channels, List.of(looped, fed), fine, SEED);
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
        assertEquals(344f, outlet.xs()[0], 0f);
        assertEquals((float) VALLEY_Z, outlet.zs()[0], 0f);
        assertEquals(ReachStyle.POND_OUTLET, outlet.style()[0]);
        assertFalse(endsIn(outlet, drained));
        for (Reach reach : shaped.reaches()) {
            assertFalse(reach + " starts at the spill point beside the inflow", reach.xs()[0] == 288f && reach.zs()[0] == VALLEY_Z);
        }
        Reach fallback = byId.get(shaped.ponds().get(1).outletReachId());
        assertNotNull(fallback);
        assertEquals(424f, fallback.xs()[0], 0f);
        assertEquals((float) VALLEY_Z, fallback.zs()[0], 0f);
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
        ShapedNetwork shaped = new ReachShaper(settings).shape(flat, receivers, accumulation, channels, List.of(), fine, SEED);
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
        ReachStyle[] styles = ReachShaper.styles(grade, ocean, new boolean[grade.length], 6);
        assertEquals(List.of(ReachStyle.RUN, ReachStyle.RUN, ReachStyle.WATERFALL, ReachStyle.MOUTH, ReachStyle.MOUTH), List.of(styles));
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
        List<Reach> paths = new ReachShaper(settings).coarsePaths(network.grid(), network.receivers(), network.accumulation(), channels, SEED);
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

    private static int count(Reach reach, ReachStyle style) {
        int count = 0;
        for (ReachStyle point : reach.style()) {
            count += point == style ? 1 : 0;
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

    private static PondMask rectangle(int originX, int originZ, int width, int depth) {
        long[] bits = new long[(width * depth + 63) >>> 6];
        for (int index = 0; index < width * depth; index++) {
            bits[index >>> 6] |= 1L << index;
        }
        return new PondMask(originX, originZ, FINE, width, depth, bits);
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
        ReachFitter.Fitted fitted = new ReachFitter.Fitted(new float[points], new float[points], new float[points], ground.clone(),
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

    private static void noCascades(IrisHydrology config) {
        for (IrisHydrologyTier tier : config.getTiers()) {
            tier.setCascades(false);
        }
    }

    private static HydrologySettings settings(Consumer<IrisHydrology> edit) {
        IrisHydrology config = new IrisHydrology();
        edit.accept(config);
        return HydrologySettings.compile(config, SEED, SEA_LEVEL, MIN_Y, MAX_Y);
    }

    private static ShapedNetwork shape(HydrologySettings settings, Network network) {
        return new ReachShaper(settings).shape(network.grid(), network.receivers(), network.accumulation(), network.channels(),
                network.ponds(), network.fine(), SEED);
    }

    private static Network network(HydrologySettings settings, DoubleBinaryOperator height, int width, int depth, float threshold,
                                   boolean withPonds) {
        return network(settings, height, 0, width, depth, CELL, threshold, withPonds);
    }

    private static Network network(HydrologySettings settings, DoubleBinaryOperator height, int originX, int width, int depth,
                                   int cell, float threshold, boolean withPonds) {
        Terrain terrain = new Terrain(height);
        int count = width * depth;
        HydrologySampleBuffer samples = new HydrologySampleBuffer(count);
        new HeightLattice(terrain, cell, false, 1 << 20).fill(Math.floorDiv(originX, cell), 0, width, depth, samples);
        DemGrid raw = new DemGrid(originX, 0, cell, width, depth);
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
        DemGrid surface = new DemGrid(originX, 0, cell, width, depth);
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
