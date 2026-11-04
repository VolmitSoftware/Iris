package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.grid.CellHash;

import java.util.Arrays;

final class StepPools {
    static final float POOL_MIN_WIDTHS = 2.5f;
    static final float POOL_MAX_WIDTHS = 5f;
    static final float POOL_MIN = 6f;
    static final float POOL_MAX = 32f;
    static final float PLUNGE_PER_DROP = 0.75f;
    static final float PLUNGE_MIN = 6f;
    static final float PLUNGE_MAX = 16f;
    static final int ROLLOVER_DROP = 2;
    static final float ROLLOVER_WIDTHS = 1.5f;
    static final float ROLLOVER_MIN = 4f;
    static final float ROLLOVER_MAX = 12f;
    private static final int STEP_EXTRA = 2;
    private static final long SALT_POOL = 0x535445504f4f4cL;

    private StepPools() {
    }

    static int stepMaxDrop(HydrologyTier tier) {
        return tier.waterfallMinDrop() + STEP_EXTRA;
    }

    static float plungeLength(int drop) {
        return Math.clamp(drop * PLUNGE_PER_DROP, PLUNGE_MIN, PLUNGE_MAX);
    }

    static void group(int[] grade, int[] floors, float[] xs, float[] zs, float[] catchments, HydrologyTier tier, float protectedLength, long seed) {
        int count = grade.length;
        float[] along = along(xs, zs);
        int start = 1;
        while (start < count && along[start] <= protectedLength) {
            start++;
        }
        int point = start;
        while (point < count - 1) {
            if (grade[point] >= grade[point - 1]) {
                point++;
                continue;
            }
            int top = grade[point - 1];
            int first = top - grade[point];
            boolean cliff = first >= tier.waterfallMinDrop();
            float width = 2f * (tier.halfWidth(catchments[point]) + 1f);
            float span = cliff ? plungeLength(first)
                    : Math.clamp(width * poolWidths(seed, xs[point], zs[point]), POOL_MIN, POOL_MAX);
            int end = point;
            int highestFloor = Integer.MIN_VALUE;
            for (int next = point + 1; next < count - 1 && along[next] - along[point] < span; next++) {
                highestFloor = Math.max(highestFloor, floors[next - 1]);
                if (grade[next] >= grade[end]) {
                    continue;
                }
                int limit = cliff ? grade[point] - tier.waterfallMinDrop() : top - stepMaxDrop(tier) - 1;
                if (grade[next] <= limit || highestFloor > grade[next]) {
                    break;
                }
                end = next;
            }
            Arrays.fill(grade, point, end, grade[end]);
            if (cliff && first >= 2 * tier.waterfallMinDrop()) {
                rollover(grade, floors, along, point, width, start);
            }
            point = end + 1;
        }
    }

    private static float poolWidths(long seed, float x, float z) {
        double share = CellHash.unit(CellHash.mix(seed, SALT_POOL, (int) Math.floor(x), (int) Math.floor(z)));
        return (float) (POOL_MIN_WIDTHS + (POOL_MAX_WIDTHS - POOL_MIN_WIDTHS) * share);
    }

    private static void rollover(int[] grade, int[] floors, float[] along, int foot, float width, int start) {
        float length = Math.clamp(ROLLOVER_WIDTHS * width, ROLLOVER_MIN, ROLLOVER_MAX);
        int brink = foot - 1;
        for (int point = brink; point >= start && along[brink] - along[point] < length; point--) {
            grade[point] = Math.min(grade[point], Math.max(grade[point] - ROLLOVER_DROP, Math.max(floors[point], grade[point + 1])));
        }
    }

    private static float[] along(float[] xs, float[] zs) {
        float[] along = new float[xs.length];
        for (int point = 1; point < xs.length; point++) {
            along[point] = along[point - 1] + (float) Math.hypot(xs[point] - xs[point - 1], zs[point] - zs[point - 1]);
        }
        return along;
    }
}
