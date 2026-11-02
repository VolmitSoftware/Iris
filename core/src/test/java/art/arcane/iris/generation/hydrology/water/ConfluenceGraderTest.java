package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyTier;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ConfluenceGraderTest {
    private static final long SEED = 1337L;
    private static final WindowKey WINDOW = new WindowKey(5, -3);
    private static final int SEA_LEVEL = 40;
    private static final int JUNCTION = 100;
    private static final int POOL_EDGE_Z = -12;
    private static final int FEEDER_Z = -48;
    private static final int STUB_Z = -10;
    private static final int BANK_Z = -8;
    private static final int MARGIN_Z = -12;

    @Test
    public void spreadFeederMeetsALowerRiverWithoutAStepAtThePoolEdge() {
        int[] cascading = graded(tier(true), false);
        assertEquals("cascading feeder keeps its junction step " + Arrays.toString(cascading), 3,
                cascading[poolEdge() - 1] - cascading[poolEdge()]);

        int[] spread = graded(tier(false), false);
        for (int point = poolEdge(); point < spread.length; point++) {
            assertEquals("pooled point " + point + " " + Arrays.toString(spread), JUNCTION, spread[point]);
        }
        for (int point = 1; point < spread.length; point++) {
            int drop = spread[point - 1] - spread[point];
            assertTrue("drop " + drop + " at point " + point + " " + Arrays.toString(spread), drop >= 0 && drop <= 1);
        }
        assertEquals("spreading stays near the junction " + Arrays.toString(spread), feederGrade(FEEDER_Z), spread[0]);

        int[] blocked = graded(tier(false), true);
        assertEquals("fixed points keep the junction step " + Arrays.toString(blocked), 3, blocked[poolEdge() - 1] - blocked[poolEdge()]);
    }

    @Test
    public void downstreamStartAboveTheFeederEndFailsClosed() {
        HydrologyTier tier = tier(true);
        ReachDraft feeder = line(tier, 11L, 0f, FEEDER_Z, 0f, 0f, 2f, z -> JUNCTION);
        ReachDraft river = line(tier, 12L, 0f, 0f, 64f, 0f, 8f, z -> JUNCTION + 1);
        feeder.downstream = 1;
        river.incoming = new int[]{0};
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ConfluenceGrader.level(WINDOW, List.of(feeder, river), new int[]{0, 1}));
        assertTrue(failure.getMessage(), failure.getMessage().contains("11") && failure.getMessage().contains("12")
                && failure.getMessage().contains(WINDOW.toString()));
    }

    @Test
    public void aFeederWithItsOwnFeedersKeepsItsStartAtTheirJunction() {
        List<ReachDraft> drafts = stubbed(JUNCTION + 3);
        ReachDraft upstream = drafts.get(0);
        ReachDraft stub = drafts.get(1);
        assertTrue("the stub starts beside the river, outside its channel",
                ConfluenceGrader.edgeDistance(footprint(drafts.get(2)), stub.xs[0], stub.zs[0]) > 0D);
        assertEquals("the stub keeps its start for the reach that feeds it " + Arrays.toString(stub.grade), JUNCTION + 3, stub.grade[0]);
        for (int point = 1; point < stub.grade.length; point++) {
            assertEquals("stub point " + point + " pooled " + Arrays.toString(stub.grade), JUNCTION, stub.grade[point]);
        }
        for (int point = 0; point < upstream.grade.length; point++) {
            assertEquals("upstream point " + point + " " + Arrays.toString(upstream.grade), JUNCTION + 3, upstream.grade[point]);
        }
    }

    @Test
    public void aFeederStartingOneBlockAboveItsJunctionJoinsIt() {
        List<ReachDraft> drafts = stubbed(JUNCTION + ConfluenceGrader.MAX_START_LOWERING);
        ReachDraft upstream = drafts.get(0);
        ReachDraft stub = drafts.get(1);
        for (int point = 0; point < stub.grade.length; point++) {
            assertEquals("stub point " + point + " " + Arrays.toString(stub.grade), JUNCTION, stub.grade[point]);
        }
        assertEquals("the upstream reach meets the lowered start " + Arrays.toString(upstream.grade), JUNCTION, upstream.grade[upstream.grade.length - 1]);
    }

    private static List<ReachDraft> stubbed(int start) {
        HydrologyTier tier = tier(true);
        ReachDraft upstream = line(tier, 31L, 0f, FEEDER_Z, 0f, STUB_Z, 2f, z -> start);
        ReachDraft stub = line(tier, 32L, 0f, STUB_Z, 0f, 0f, 2f, z -> z == STUB_Z ? start : z == 0 ? JUNCTION : Math.min(start, JUNCTION + 2));
        ReachDraft river = line(tier, 33L, 0f, 0f, 64f, 0f, 8f, z -> JUNCTION);
        link(upstream, stub, river);
        return List.of(upstream, stub, river);
    }

    private static void link(ReachDraft upstream, ReachDraft stub, ReachDraft river) {
        upstream.downstream = 1;
        stub.incoming = new int[]{0};
        stub.downstream = 2;
        river.incoming = new int[]{1};
        ConfluenceGrader.level(WINDOW, List.of(upstream, stub, river), new int[]{0, 1, 2});
    }

    private static ConfluenceGrader.Footprint footprint(ReachDraft draft) {
        return new ConfluenceGrader.Footprint(draft.xs, draft.zs, draft.halfWidth);
    }

    @Test
    public void feederBesideTheReceivingBankPoolsOnlyWhenItIsNearTheJunctionLevel() {
        int[] near = besideTheBank(ConfluenceGrader.MARGIN_LOWERING);
        for (int point = 0; point < near.length; point++) {
            int z = FEEDER_Z + point;
            if (z >= MARGIN_Z) {
                assertEquals("point " + point + " at z " + z + " " + Arrays.toString(near), JUNCTION, near[point]);
            }
        }
        int[] high = besideTheBank(ConfluenceGrader.MARGIN_LOWERING + 1);
        for (int point = 0; point < high.length; point++) {
            int z = FEEDER_Z + point;
            if (z >= BANK_Z) {
                assertEquals("point " + point + " inside the receiving channel at z " + z + " " + Arrays.toString(high), JUNCTION, high[point]);
            } else if (z >= MARGIN_Z) {
                assertEquals("point " + point + " beside the bank at z " + z + " " + Arrays.toString(high), JUNCTION + ConfluenceGrader.MARGIN_LOWERING + 1,
                        high[point]);
            }
        }
    }

    private static int[] besideTheBank(int marginHeight) {
        HydrologyTier tier = tier(true);
        ReachDraft feeder = line(tier, 41L, 0f, FEEDER_Z, 0f, 0f, 2f, z -> {
            if (z == 0) {
                return JUNCTION;
            }
            if (z >= BANK_Z) {
                return JUNCTION + 1;
            }
            return JUNCTION + marginHeight + Math.max(0, Math.floorDiv(MARGIN_Z - z, 2));
        });
        ReachDraft river = line(tier, 42L, 0f, 0f, 64f, 0f, 8f, z -> JUNCTION);
        feeder.downstream = 1;
        river.incoming = new int[]{0};
        ConfluenceGrader.level(WINDOW, List.of(feeder, river), new int[]{0, 1});
        return feeder.grade;
    }

    private static int[] graded(HydrologyTier tier, boolean fixedApproach) {
        ReachDraft feeder = line(tier, 21L, 0f, FEEDER_Z, 0f, 0f, 2f, ConfluenceGraderTest::feederGrade);
        ReachDraft river = line(tier, 22L, 0f, 0f, 64f, 0f, 8f, z -> JUNCTION);
        if (fixedApproach) {
            Arrays.fill(feeder.outletZone, 0, poolEdge(), true);
        }
        feeder.downstream = 1;
        river.incoming = new int[]{0};
        ConfluenceGrader.level(WINDOW, List.of(feeder, river), new int[]{0, 1});
        return feeder.grade;
    }

    private static int feederGrade(int z) {
        if (z == 0) {
            return JUNCTION;
        }
        if (z >= POOL_EDGE_Z) {
            return JUNCTION + 2;
        }
        return JUNCTION + 3 + Math.floorDiv(POOL_EDGE_Z - 1 - z, 2);
    }

    private static int poolEdge() {
        return POOL_EDGE_Z - FEEDER_Z;
    }

    private static ReachDraft line(HydrologyTier tier, long id, float fromX, float fromZ, float toX, float toZ, float halfWidth,
                                   IntUnaryOperator grade) {
        int points = Math.round((float) Math.hypot(toX - fromX, toZ - fromZ)) + 1;
        float[] xs = new float[points];
        float[] zs = new float[points];
        for (int point = 0; point < points; point++) {
            float t = point / (points - 1f);
            xs[point] = fromX + t * (toX - fromX);
            zs[point] = fromZ + t * (toZ - fromZ);
        }
        ReachFitter.Fitted fitted = new ReachFitter.Fitted(xs, zs, new float[points], new float[points], new boolean[points], new float[points]);
        ReachDraft draft = new ReachDraft(new ReachTracer.Chain(new int[]{0, 1}, ReachTracer.End.EDGE, -1), id, tier, 0f, Integer.MIN_VALUE, fitted);
        draft.grade = new int[points];
        draft.halfWidth = new float[points];
        draft.outletZone = new boolean[points];
        for (int point = 0; point < points; point++) {
            draft.grade[point] = grade.applyAsInt(Math.round(zs[point]));
            draft.halfWidth[point] = halfWidth;
        }
        return draft;
    }

    private static HydrologyTier tier(boolean cascades) {
        IrisHydrology config = new IrisHydrology();
        for (IrisHydrologyTier tier : config.getTiers()) {
            tier.setCascades(cascades);
        }
        return HydrologySettings.compile(config, SEED, SEA_LEVEL, -64, 320).tiers().get(0);
    }
}
