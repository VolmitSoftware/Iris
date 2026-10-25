package art.arcane.iris.generation.hydrology.grid;

public final class D8Router {
    private static final float DIAGONAL = (float) (1.0D / Math.sqrt(2.0D));

    private D8Router() {
    }

    public static int[] receivers(DemGrid grid, float[] filled) {
        int count = grid.width() * grid.depth();
        if (filled.length != count) {
            throw new IllegalArgumentException("Filled surface has " + filled.length + " cells, grid has " + count);
        }
        int[] receivers = new int[count];
        for (int cell = 0; cell < count; cell++) {
            receivers[cell] = receiver(grid, filled, cell);
        }
        return receivers;
    }

    static int receiver(DemGrid grid, float[] filled, int cell) {
        int width = grid.width();
        int depth = grid.depth();
        int i = cell % width;
        int j = cell / width;
        float level = filled[cell];
        int best = -1;
        float bestSlope = 0f;
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
                float drop = level - filled[neighbour];
                if (!(drop > 0f)) {
                    continue;
                }
                float slope = dx != 0 && dz != 0 ? drop * DIAGONAL : drop;
                if (best < 0 || slope > bestSlope) {
                    best = neighbour;
                    bestSlope = slope;
                }
            }
        }
        return best;
    }
}
