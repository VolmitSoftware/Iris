package art.arcane.iris.generation.hydrology.grid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntPredicate;

public final class BasinExtractor {
    private BasinExtractor() {
    }

    public static List<Basin> basins(DemGrid raw, float[] filled, float minDepth, float seaLevel) {
        float[] heights = raw.values();
        int count = heights.length;
        if (filled.length != count) {
            throw new IllegalArgumentException("Filled surface has " + filled.length + " cells, grid has " + count);
        }
        int width = raw.width();
        int depth = raw.depth();
        boolean[] visited = new boolean[count];
        int[] queue = new int[count];
        List<Basin> basins = new ArrayList<>();
        for (int start = 0; start < count; start++) {
            if (visited[start] || !flooded(heights, filled, start, minDepth, seaLevel)) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            visited[start] = true;
            int floor = start;
            while (head < tail) {
                int cell = queue[head++];
                if (heights[cell] < heights[floor] || (heights[cell] == heights[floor] && cell < floor)) {
                    floor = cell;
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
                        if (visited[neighbour] || !flooded(heights, filled, neighbour, minDepth, seaLevel)) {
                            continue;
                        }
                        visited[neighbour] = true;
                        queue[tail++] = neighbour;
                    }
                }
            }
            int[] cells = Arrays.copyOf(queue, tail);
            Arrays.sort(cells);
            float lowestFilled = Float.POSITIVE_INFINITY;
            for (int cell : cells) {
                lowestFilled = Math.min(lowestFilled, filled[cell]);
            }
            int spill = spill(raw, filled, floor, lowestFilled);
            float spillHeight = heights[spill];
            basins.add(new Basin(basins.size(), floor, spill, spillHeight, spillHeight - heights[floor], cells, false));
        }
        return basins;
    }

    public static BitSet inland(DemGrid natural, boolean[] ocean, int seaLevel) {
        float[] heights = natural.values();
        int count = heights.length;
        if (ocean.length < count) {
            throw new IllegalArgumentException("Ocean flags cover " + ocean.length + " cells, grid has " + count);
        }
        BitSet inland = new BitSet(count);
        boolean[] visited = new boolean[count];
        int[] queue = new int[count];
        for (int start = 0; start < count; start++) {
            if (visited[start] || !belowSea(heights, ocean, start, seaLevel)) {
                continue;
            }
            int tail = component(natural, cell -> belowSea(heights, ocean, cell, seaLevel), visited, queue, start);
            if (enclosed(natural, ocean, queue, tail)) {
                for (int index = 0; index < tail; index++) {
                    inland.set(queue[index]);
                }
            }
        }
        return inland;
    }

    public static BitSet hollow(DemGrid natural, BitSet inland, int cell) {
        BitSet hollow = new BitSet(natural.values().length);
        if (!inland.get(cell)) {
            return hollow;
        }
        int[] queue = new int[natural.values().length];
        int tail = component(natural, inland::get, new boolean[queue.length], queue, cell);
        for (int index = 0; index < tail; index++) {
            hollow.set(queue[index]);
        }
        return hollow;
    }

    public static List<Basin> hollows(DemGrid natural, BitSet inland, float[] filled, int seaLevel, int firstId) {
        float[] heights = natural.values();
        boolean[] visited = new boolean[heights.length];
        int[] queue = new int[heights.length];
        List<Basin> hollows = new ArrayList<>();
        for (int start = inland.nextSetBit(0); start >= 0; start = inland.nextSetBit(start + 1)) {
            if (visited[start]) {
                continue;
            }
            int tail = component(natural, inland::get, visited, queue, start);
            if (!drained(filled, queue, tail, seaLevel)) {
                continue;
            }
            int[] cells = Arrays.copyOf(queue, tail);
            Arrays.sort(cells);
            int floor = cells[0];
            for (int cell : cells) {
                if (heights[cell] < heights[floor]) {
                    floor = cell;
                }
            }
            hollows.add(new Basin(firstId + hollows.size(), floor, floor, seaLevel, seaLevel - heights[floor], cells, true));
        }
        return hollows;
    }

    private static boolean drained(float[] filled, int[] cells, int count, int seaLevel) {
        for (int index = 0; index < count; index++) {
            if (filled[cells[index]] > seaLevel) {
                return false;
            }
        }
        return true;
    }

    private static boolean belowSea(float[] heights, boolean[] ocean, int cell, int seaLevel) {
        return !ocean[cell] && Math.round(heights[cell]) < seaLevel;
    }

    private static boolean enclosed(DemGrid grid, boolean[] ocean, int[] cells, int count) {
        int width = grid.width();
        int depth = grid.depth();
        for (int index = 0; index < count; index++) {
            int cell = cells[index];
            int i = cell % width;
            int j = cell / width;
            if (i == 0 || j == 0 || i == width - 1 || j == depth - 1) {
                return false;
            }
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (ocean[(j + dz) * width + i + dx]) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static int component(DemGrid grid, IntPredicate member, boolean[] visited, int[] queue, int start) {
        int width = grid.width();
        int depth = grid.depth();
        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        visited[start] = true;
        while (head < tail) {
            int cell = queue[head++];
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
                    if (!visited[neighbour] && member.test(neighbour)) {
                        visited[neighbour] = true;
                        queue[tail++] = neighbour;
                    }
                }
            }
        }
        return tail;
    }

    private static boolean flooded(float[] heights, float[] filled, int cell, float minDepth, float seaLevel) {
        return filled[cell] - heights[cell] > minDepth && filled[cell] > seaLevel;
    }

    private static int spill(DemGrid raw, float[] filled, int floor, float lowestFilled) {
        float[] heights = raw.values();
        int current = floor;
        while (true) {
            int next = D8Router.receiver(raw, filled, current);
            if (next < 0) {
                return current;
            }
            current = next;
            if (!(filled[current] > heights[current]) && filled[current] < lowestFilled) {
                return current;
            }
        }
    }
}
