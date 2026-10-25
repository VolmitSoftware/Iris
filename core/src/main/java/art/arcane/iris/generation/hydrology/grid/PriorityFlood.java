package art.arcane.iris.generation.hydrology.grid;

import java.util.BitSet;

public final class PriorityFlood {
    private PriorityFlood() {
    }

    public static float[] fill(DemGrid raw, BitSet seeds, float epsilon) {
        float[] heights = raw.values();
        int count = heights.length;
        if (seeds.isEmpty() || seeds.length() > count) {
            throw new IllegalArgumentException("Priority-flood seeds must be non-empty and inside the grid");
        }
        if (!(epsilon >= 0f)) {
            throw new IllegalArgumentException("Priority-flood epsilon must be non-negative: " + epsilon);
        }
        int width = raw.width();
        int depth = raw.depth();
        float[] filled = new float[count];
        boolean[] closed = new boolean[count];
        CellHeap open = new CellHeap(count);
        for (int seed = seeds.nextSetBit(0); seed >= 0; seed = seeds.nextSetBit(seed + 1)) {
            filled[seed] = heights[seed];
            closed[seed] = true;
            open.push(heights[seed], seed);
        }
        while (!open.isEmpty()) {
            int cell = open.pop();
            float spill = Math.max(filled[cell] + epsilon, Math.nextUp(filled[cell]));
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
                    if (closed[neighbour]) {
                        continue;
                    }
                    closed[neighbour] = true;
                    filled[neighbour] = Math.max(heights[neighbour], spill);
                    open.push(filled[neighbour], neighbour);
                }
            }
        }
        return filled;
    }
}
