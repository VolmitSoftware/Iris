package art.arcane.iris.generation.hydrology.grid;

public final class D8Router {
    private static final float DIAGONAL = (float) (1.0D / Math.sqrt(2.0D));
    private static final long SALT_TIE = 0x4438544945L;

    private D8Router() {
    }

    public static int[] receivers(DemGrid grid, float[] filled, long seed) {
        int count = grid.width() * grid.depth();
        if (filled.length != count) {
            throw new IllegalArgumentException("Filled surface has " + filled.length + " cells, grid has " + count);
        }
        int[] receivers = new int[count];
        for (int cell = 0; cell < count; cell++) {
            receivers[cell] = steepest(grid, filled, cell, true, seed);
        }
        return receivers;
    }

    static int receiver(DemGrid grid, float[] filled, int cell) {
        return steepest(grid, filled, cell, false, 0L);
    }

    private static int steepest(DemGrid grid, float[] filled, int cell, boolean hashTies, long seed) {
        int width = grid.width();
        int depth = grid.depth();
        int i = cell % width;
        int j = cell / width;
        float level = filled[cell];
        int best = -1;
        float bestSlope = 0f;
        int bestDirection = -1;
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
                int direction = (dz + 1) * 3 + dx + 1;
                boolean wins = best < 0 || slope > bestSlope
                        || hashTies && slope == bestSlope && tie(grid, cell, seed, direction) < tie(grid, cell, seed, bestDirection);
                if (wins) {
                    best = neighbour;
                    bestSlope = slope;
                    bestDirection = direction;
                }
            }
        }
        return best;
    }

    private static long tie(DemGrid grid, int cell, long seed, int direction) {
        return CellHash.mix(seed, SALT_TIE + direction, grid.blockX(cell), grid.blockZ(cell));
    }
}
