package art.arcane.iris.generation.hydrology.water;

import java.util.Arrays;
import java.util.List;

final class ReachGrader {
    private static final int LOOKAHEAD = 32;
    private static final double POOL_QUANTILE = 0.25D;
    private static final int MAX_POOL = 4;

    private ReachGrader() {
    }

    record PondChain(int[] levels, boolean[] fixed, int[] inflowPond, int[] outletDrafts) {
        int outletOf(int pond) {
            return pond < 0 || fixed[pond] ? -1 : outletDrafts[pond];
        }
    }

    static int[] grade(List<ReachDraft> drafts, int[] upstreamFirst, int seaLevel, int maxFill, PondChain ponds) {
        boolean[] lakeFloor = new boolean[drafts.size()];
        for (int position = upstreamFirst.length - 1; position >= 0; position--) {
            int index = upstreamFirst[position];
            ReachDraft draft = drafts.get(index);
            int endPond = ponds.inflowPond()[index];
            int downstreamFloor = draft.downstream < 0 ? Integer.MIN_VALUE : drafts.get(draft.downstream).floor;
            draft.floor = Math.max(seaLevel, Math.max(draft.endLevel, downstreamFloor));
            lakeFloor[index] = endPond >= 0 && ponds.fixed()[endPond] && draft.endLevel >= downstreamFloor
                    || draft.downstream >= 0 && lakeFloor[draft.downstream] && downstreamFloor >= draft.endLevel;
        }
        int[] levels = ponds.levels().clone();
        int[] outletPond = new int[drafts.size()];
        Arrays.fill(outletPond, -1);
        for (int pond = 0; pond < levels.length; pond++) {
            if (ponds.outletDrafts()[pond] >= 0) {
                outletPond[ponds.outletDrafts()[pond]] = pond;
            }
        }
        int[][] inflows = inflows(ponds, levels.length);
        int[] fed = new int[drafts.size()];
        Arrays.fill(fed, Integer.MAX_VALUE);
        boolean[] graded = new boolean[drafts.size()];
        boolean[] settled = new boolean[levels.length];
        for (int index : upstreamFirst) {
            ReachDraft draft = drafts.get(index);
            int source = outletPond[index];
            if (source >= 0) {
                settle(drafts, ponds, inflows[source], levels, fed, graded, settled, lakeFloor, source, seaLevel);
                draft.carry = Math.min(draft.carry, levels[source]);
                fed[index] = levels[source];
            }
            for (int upstream : draft.incoming) {
                fed[index] = Math.min(fed[index], fed[upstream]);
            }
            int level = Math.max(seaLevel, draft.carry);
            for (int upstream : draft.incoming) {
                level = Math.min(level, drafts.get(upstream).last());
            }
            int[] grade = profile(drafts, draft);
            for (int point = 0; point < grade.length; point++) {
                int cap = draft.ocean[point] ? seaLevel : draft.target[point] + MAX_POOL;
                int floor = lakeFloor[index] ? draft.floor : Math.max(seaLevel, Math.min(draft.floor, (int) Math.floor(draft.bank[point]) + maxFill));
                level = Math.min(level, Math.max(floor, Math.min(grade[point], cap)));
                grade[point] = level;
            }
            draft.grade = grade;
            graded[index] = true;
            lowerIncomingEnds(drafts, draft);
        }
        for (int pond = 0; pond < levels.length; pond++) {
            settle(drafts, ponds, inflows[pond], levels, fed, graded, settled, lakeFloor, pond, seaLevel);
        }
        for (int position = upstreamFirst.length - 1; position >= 0; position--) {
            ReachDraft draft = drafts.get(upstreamFirst[position]);
            if (!draft.tier.cascades()) {
                spread(draft.grade, draft.tier.waterfallMinimumDrop(), startLimit(drafts, draft));
            }
            lowerIncomingEnds(drafts, draft);
        }
        return levels;
    }

    private static int[][] inflows(PondChain ponds, int count) {
        int[] sizes = new int[count];
        for (int pond : ponds.inflowPond()) {
            if (pond >= 0) {
                sizes[pond]++;
            }
        }
        int[][] inflows = new int[count][];
        for (int pond = 0; pond < count; pond++) {
            inflows[pond] = new int[sizes[pond]];
        }
        Arrays.fill(sizes, 0);
        for (int index = 0; index < ponds.inflowPond().length; index++) {
            int pond = ponds.inflowPond()[index];
            if (pond >= 0) {
                inflows[pond][sizes[pond]++] = index;
            }
        }
        return inflows;
    }

    private static void settle(List<ReachDraft> drafts, PondChain ponds, int[] inflows, int[] levels, int[] fed, boolean[] graded,
                               boolean[] settled, boolean[] lakeFloor, int pond, int seaLevel) {
        if (settled[pond]) {
            return;
        }
        settled[pond] = true;
        if (ponds.fixed()[pond]) {
            return;
        }
        int level = levels[pond];
        for (int index : inflows) {
            if (!graded[index]) {
                throw new IllegalStateException("Pond " + pond + " settled before its inflow reach " + drafts.get(index).id + " was graded");
            }
            level = Math.min(level, drafts.get(index).last());
            if (fed[index] != Integer.MAX_VALUE) {
                level = Math.min(level, Math.max(seaLevel, fed[index] - 1));
            }
        }
        int outlet = ponds.outletDrafts()[pond];
        if (outlet >= 0 && lakeFloor[outlet] && level < drafts.get(outlet).floor) {
            level = Integer.MIN_VALUE;
        }
        levels[pond] = level;
    }

    private static int startLimit(List<ReachDraft> drafts, ReachDraft draft) {
        int limit = Integer.MIN_VALUE;
        for (int upstream : draft.incoming) {
            ReachDraft feeder = drafts.get(upstream);
            limit = Math.max(limit, carryLimit(feeder.grade, feeder.grade.length - 1, feeder.tier.waterfallMinimumDrop()));
        }
        return limit;
    }

    private static int carryLimit(int[] grade, int point, int waterfallMinimumDrop) {
        if (point == 0 || grade[point - 1] - grade[point] >= waterfallMinimumDrop) {
            return Integer.MIN_VALUE;
        }
        return grade[point - 1] - (waterfallMinimumDrop - 1);
    }

    private static int[] profile(List<ReachDraft> drafts, ReachDraft draft) {
        int own = draft.target.length;
        int[] values = Arrays.copyOf(draft.target, own + LOOKAHEAD);
        int count = own;
        for (int next = draft.downstream; next >= 0 && count < values.length; next = drafts.get(next).downstream) {
            int[] downstream = drafts.get(next).target;
            int taken = Math.min(downstream.length - 1, values.length - count);
            System.arraycopy(downstream, 1, values, count, taken);
            count += taken;
        }
        return Arrays.copyOf(isotonic(values, count), own);
    }

    private static int[] isotonic(int[] values, int count) {
        int[] starts = new int[count];
        int[] levels = new int[count];
        int blocks = 0;
        for (int point = 0; point < count; point++) {
            starts[blocks] = point;
            levels[blocks] = values[point];
            blocks++;
            while (blocks > 1 && levels[blocks - 2] < levels[blocks - 1]) {
                blocks--;
                levels[blocks - 1] = quantile(values, starts[blocks - 1], point);
            }
        }
        int[] fitted = new int[count];
        for (int block = 0; block < blocks; block++) {
            int end = block + 1 < blocks ? starts[block + 1] : count;
            Arrays.fill(fitted, starts[block], end, levels[block]);
        }
        return fitted;
    }

    private static int quantile(int[] values, int from, int to) {
        int[] sorted = Arrays.copyOfRange(values, from, to + 1);
        Arrays.sort(sorted);
        return sorted[Math.max(0, (int) Math.ceil(POOL_QUANTILE * sorted.length) - 1)];
    }

    private static void spread(int[] grade, int waterfallMinimumDrop, int startLimit) {
        for (int point = grade.length - 2; point >= 0; point--) {
            int drop = grade[point] - grade[point + 1];
            if (drop >= 2 && drop < waterfallMinimumDrop) {
                int limit = point == 0 ? startLimit : carryLimit(grade, point, waterfallMinimumDrop);
                grade[point] = Math.max(grade[point + 1] + 1, limit);
            }
        }
    }

    private static void lowerIncomingEnds(List<ReachDraft> drafts, ReachDraft draft) {
        for (int upstream : draft.incoming) {
            int[] grade = drafts.get(upstream).grade;
            grade[grade.length - 1] = Math.min(grade[grade.length - 1], draft.grade[0]);
        }
    }
}
