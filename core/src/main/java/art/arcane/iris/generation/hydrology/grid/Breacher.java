package art.arcane.iris.generation.hydrology.grid;

import java.util.Arrays;
import java.util.BitSet;
import java.util.function.IntPredicate;

public final class Breacher {
    private static final float DROP = 0.01f;
    private static final float STEP_COST = 0.01f;
    private static final float DIAGONAL = (float) Math.sqrt(2.0D);

    private Breacher() {
    }

    public static void breach(DemGrid raw, float[] filled, Basin basin) {
        float[] heights = raw.values();
        if (filled.length != heights.length) {
            throw new IllegalArgumentException("Filled surface has " + filled.length + " cells, grid has " + heights.length);
        }
        int floor = basin.floorIndex();
        int spill = basin.spillIndex();
        float floorHeight = heights[floor];
        BitSet inside = new BitSet(heights.length);
        for (int cell : basin.cells()) {
            inside.set(cell);
        }
        int[] toSpill = route(raw, floor, floorHeight, new BitSet(), cell -> cell == spill);
        int[] onward = route(raw, spill, floorHeight, inside,
                cell -> filled[cell] < floorHeight || D8Router.receiver(raw, filled, cell) < 0);
        int[] path = Arrays.copyOf(toSpill, toSpill.length + onward.length - 1);
        System.arraycopy(onward, 1, path, toSpill.length, onward.length - 1);
        int steps = path.length - 1;
        float outlet = filled[path[steps]];
        float targetLevel = outlet < floorHeight ? outlet : floorHeight - DROP * steps;
        carve(heights, path, floorHeight, targetLevel, Math.min(DROP, (floorHeight - targetLevel) / steps));
    }

    private static void carve(float[] heights, int[] path, float floorHeight, float targetLevel, float step) {
        float level = floorHeight;
        for (int index = 1; index < path.length; index++) {
            int cell = path[index];
            float next = Math.max(targetLevel, floorHeight - step * index);
            if (!(next < level)) {
                next = Math.nextDown(level);
            }
            heights[cell] = Math.min(heights[cell], next);
            level = next;
        }
    }

    private static int[] route(DemGrid raw, int start, float floorHeight, BitSet blocked, IntPredicate target) {
        float[] heights = raw.values();
        int count = heights.length;
        int width = raw.width();
        int depth = raw.depth();
        float[] cost = new float[count];
        int[] previous = new int[count];
        boolean[] done = new boolean[count];
        Arrays.fill(cost, Float.POSITIVE_INFINITY);
        cost[start] = 0f;
        previous[start] = -1;
        CellHeap open = new CellHeap(64);
        open.push(0f, start);
        while (!open.isEmpty()) {
            int cell = open.pop();
            if (done[cell]) {
                continue;
            }
            done[cell] = true;
            if (target.test(cell)) {
                return path(previous, cell);
            }
            int i = cell % width;
            int j = cell / width;
            for (int dz = -1; dz <= 1; dz++) {
                int nj = j + dz;
                if (nj < 0 || nj >= depth) {
                    continue;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    int ni = i + dx;
                    if (ni < 0 || ni >= width) {
                        continue;
                    }
                    int neighbour = nj * width + ni;
                    if (done[neighbour] || blocked.get(neighbour)) {
                        continue;
                    }
                    float weight = Math.max(0f, heights[neighbour] - floorHeight) + STEP_COST;
                    float candidate = cost[cell] + (dx != 0 && dz != 0 ? weight * DIAGONAL : weight);
                    if (candidate < cost[neighbour]) {
                        cost[neighbour] = candidate;
                        previous[neighbour] = cell;
                        open.push(candidate, neighbour);
                    }
                }
            }
        }
        throw new IllegalStateException("No breach route from cell " + start);
    }

    private static int[] path(int[] previous, int end) {
        int length = 0;
        for (int cell = end; cell >= 0; cell = previous[cell]) {
            length++;
        }
        int[] path = new int[length];
        int index = length;
        for (int cell = end; cell >= 0; cell = previous[cell]) {
            path[--index] = cell;
        }
        return path;
    }
}
