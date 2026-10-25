package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.grid.CellHeap;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class TrunkRouter {
    static final int CORRIDOR = 32;
    private static final float STEP_COST = 0.1f;

    private TrunkRouter() {
    }

    static List<int[]> route(DemGrid raw, Reach reach, Reach downstream) {
        int count = reach.xs().length;
        int last = count - 1;
        int[] is = new int[count];
        int[] js = new int[count];
        for (int point = 0; point < count; point++) {
            is[point] = node(reach.xs()[point], raw.originX(), raw.cell());
            js[point] = node(reach.zs()[point], raw.originZ(), raw.cell());
        }
        int start = anchor(raw, is[0], js[0], is[1] - is[0], js[1] - js[0]);
        int[] exit = exitDirection(raw, reach, downstream, is, js);
        int end = anchor(raw, is[last], js[last], exit[0], exit[1]);
        if (start >= 0 && end >= 0) {
            int[] path = corridorPath(raw, is, js, start, end);
            if (path != null) {
                return List.of(path);
            }
        }
        if (start >= 0) {
            is[0] = start % raw.width();
            js[0] = start / raw.width();
        }
        if (end >= 0) {
            is[last] = end % raw.width();
            js[last] = end / raw.width();
        }
        return rasterize(raw, is, js);
    }

    private static int node(float block, int origin, int cell) {
        return Math.floorDiv(Math.round(block) - origin + (cell >> 1), cell);
    }

    private static int[] exitDirection(DemGrid raw, Reach reach, Reach downstream, int[] is, int[] js) {
        int last = is.length - 1;
        if (downstream != null && downstream.xs().length >= 2 && downstream.xs()[0] == reach.xs()[last] && downstream.zs()[0] == reach.zs()[last]) {
            int nextI = node(downstream.xs()[1], raw.originX(), raw.cell());
            int nextJ = node(downstream.zs()[1], raw.originZ(), raw.cell());
            return new int[]{nextI - is[last], nextJ - js[last]};
        }
        return new int[]{is[last] - is[last - 1], js[last] - js[last - 1]};
    }

    private static int anchor(DemGrid raw, int i, int j, int di, int dj) {
        if (!inside(raw, i, j)) {
            return -1;
        }
        int own = raw.index(i, j);
        int stepI = -Integer.signum(dj);
        int stepJ = Integer.signum(di);
        if (stepI == 0 && stepJ == 0) {
            return own;
        }
        double stride = raw.cell() * Math.hypot(stepI, stepJ);
        int reachSteps = (int) Math.floor(CORRIDOR / stride);
        if (!inside(raw, i - reachSteps * stepI, j - reachSteps * stepJ) || !inside(raw, i + reachSteps * stepI, j + reachSteps * stepJ)) {
            return own;
        }
        float[] heights = raw.values();
        int best = own;
        for (int distance = 1; distance <= reachSteps; distance++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                int cell = raw.index(i + sign * distance * stepI, j + sign * distance * stepJ);
                if (heights[cell] < heights[best]) {
                    best = cell;
                }
            }
        }
        return best;
    }

    private static int[] corridorPath(DemGrid raw, int[] is, int[] js, int start, int end) {
        if (start == end) {
            return new int[]{start};
        }
        int margin = CORRIDOR / raw.cell();
        int startI = start % raw.width();
        int startJ = start / raw.width();
        int endI = end % raw.width();
        int endJ = end / raw.width();
        int minI = Math.clamp(Math.min(min(is) - margin, Math.min(startI, endI)), 0, raw.width() - 1);
        int minJ = Math.clamp(Math.min(min(js) - margin, Math.min(startJ, endJ)), 0, raw.depth() - 1);
        int maxI = Math.clamp(Math.max(max(is) + margin, Math.max(startI, endI)), 0, raw.width() - 1);
        int maxJ = Math.clamp(Math.max(max(js) + margin, Math.max(startJ, endJ)), 0, raw.depth() - 1);
        int width = maxI - minI + 1;
        int depth = maxJ - minJ + 1;
        boolean[] allowed = new boolean[width * depth];
        double limit = (double) margin * margin;
        for (int lj = 0; lj < depth; lj++) {
            for (int li = 0; li < width; li++) {
                allowed[lj * width + li] = distanceSquared(minI + li, minJ + lj, is, js) <= limit;
            }
        }
        int from = local(start, raw, minI, minJ, width);
        int target = local(end, raw, minI, minJ, width);
        allowed[from] = true;
        allowed[target] = true;
        int[] previous = search(raw, allowed, width, depth, minI, minJ, from, target, raw.values()[end]);
        if (previous == null) {
            return null;
        }
        int length = 0;
        for (int cell = target; cell >= 0; cell = previous[cell]) {
            length++;
        }
        int[] path = new int[length];
        int index = length;
        for (int cell = target; cell >= 0; cell = previous[cell]) {
            path[--index] = raw.index(minI + cell % width, minJ + cell / width);
        }
        return path;
    }

    private static int[] search(DemGrid raw, boolean[] allowed, int width, int depth, int minI, int minJ, int from, int target, float endHeight) {
        float[] heights = raw.values();
        float[] cost = new float[allowed.length];
        int[] previous = new int[allowed.length];
        boolean[] done = new boolean[allowed.length];
        Arrays.fill(cost, Float.POSITIVE_INFINITY);
        cost[from] = 0f;
        previous[from] = -1;
        CellHeap open = new CellHeap(64);
        open.push(0f, from);
        while (!open.isEmpty()) {
            int cell = open.pop();
            if (done[cell]) {
                continue;
            }
            done[cell] = true;
            if (cell == target) {
                return previous;
            }
            int li = cell % width;
            int lj = cell / width;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int ni = li + dx;
                    int nj = lj + dz;
                    if ((dx == 0 && dz == 0) || ni < 0 || nj < 0 || ni >= width || nj >= depth) {
                        continue;
                    }
                    int neighbour = nj * width + ni;
                    if (done[neighbour] || !allowed[neighbour]) {
                        continue;
                    }
                    float height = heights[raw.index(minI + ni, minJ + nj)];
                    float candidate = cost[cell] + Math.max(0f, height - endHeight) + STEP_COST;
                    if (candidate < cost[neighbour]) {
                        cost[neighbour] = candidate;
                        previous[neighbour] = cell;
                        open.push(candidate, neighbour);
                    }
                }
            }
        }
        return null;
    }

    private static List<int[]> rasterize(DemGrid raw, int[] is, int[] js) {
        List<int[]> pieces = new ArrayList<>();
        int[] piece = new int[16];
        int length = 0;
        for (int point = 0; point < is.length; point++) {
            int steps = point == 0 ? 0 : Math.max(Math.abs(is[point] - is[point - 1]), Math.abs(js[point] - js[point - 1]));
            for (int step = point == 0 ? 0 : 1; step <= steps; step++) {
                int ci = steps == 0 ? is[point] : is[point - 1] + Math.round((float) (is[point] - is[point - 1]) * step / steps);
                int cj = steps == 0 ? js[point] : js[point - 1] + Math.round((float) (js[point] - js[point - 1]) * step / steps);
                if (!inside(raw, ci, cj)) {
                    length = flush(piece, length, pieces);
                    continue;
                }
                int cell = raw.index(ci, cj);
                if (length > 0 && piece[length - 1] == cell) {
                    continue;
                }
                if (length == piece.length) {
                    piece = Arrays.copyOf(piece, 2 * length);
                }
                piece[length++] = cell;
            }
        }
        flush(piece, length, pieces);
        return pieces;
    }

    private static int flush(int[] piece, int length, List<int[]> pieces) {
        if (length > 0) {
            pieces.add(Arrays.copyOf(piece, length));
        }
        return 0;
    }

    private static double distanceSquared(int i, int j, int[] is, int[] js) {
        double best = Double.POSITIVE_INFINITY;
        for (int point = 0; point + 1 < is.length; point++) {
            double ax = is[point];
            double az = js[point];
            double bx = is[point + 1] - ax;
            double bz = js[point + 1] - az;
            double span = bx * bx + bz * bz;
            double t = span == 0D ? 0D : Math.clamp(((i - ax) * bx + (j - az) * bz) / span, 0D, 1D);
            double dx = i - (ax + t * bx);
            double dz = j - (az + t * bz);
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    private static int local(int cell, DemGrid raw, int minI, int minJ, int width) {
        return (cell / raw.width() - minJ) * width + (cell % raw.width() - minI);
    }

    private static boolean inside(DemGrid raw, int i, int j) {
        return i >= 0 && j >= 0 && i < raw.width() && j < raw.depth();
    }

    private static int min(int[] values) {
        int min = Integer.MAX_VALUE;
        for (int value : values) {
            min = Math.min(min, value);
        }
        return min;
    }

    private static int max(int[] values) {
        int max = Integer.MIN_VALUE;
        for (int value : values) {
            max = Math.max(max, value);
        }
        return max;
    }
}
