package art.arcane.iris.generation.hydrology.grid;

import java.util.BitSet;
import java.util.Random;

final class GridFixtures {
    private GridFixtures() {
    }

    static DemGrid uniform(int width, int depth, float height) {
        DemGrid grid = new DemGrid(0, 0, 8, width, depth);
        float[] values = grid.values();
        for (int cell = 0; cell < values.length; cell++) {
            values[cell] = height;
        }
        return grid;
    }

    static DemGrid random(long seed, int width, int depth) {
        Random random = new Random(seed);
        DemGrid grid = new DemGrid(0, 0, 8, width, depth);
        float[] values = grid.values();
        for (int cell = 0; cell < values.length; cell++) {
            values[cell] = -64f + random.nextFloat() * 320f;
        }
        return grid;
    }

    static BitSet edgeSeeds(DemGrid grid) {
        BitSet seeds = new BitSet(grid.width() * grid.depth());
        for (int j = 0; j < grid.depth(); j++) {
            for (int i = 0; i < grid.width(); i++) {
                if (i == 0 || j == 0 || i == grid.width() - 1 || j == grid.depth() - 1) {
                    seeds.set(grid.index(i, j));
                }
            }
        }
        return seeds;
    }

    static boolean adjacent(DemGrid grid, int first, int second) {
        int dx = Math.abs(first % grid.width() - second % grid.width());
        int dz = Math.abs(first / grid.width() - second / grid.width());
        return first != second && dx <= 1 && dz <= 1;
    }

    static boolean hasLowerNeighbour(DemGrid grid, float[] filled, int cell) {
        int i = cell % grid.width();
        int j = cell / grid.width();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int ni = i + dx;
                int nj = j + dz;
                if ((dx == 0 && dz == 0) || ni < 0 || nj < 0 || ni >= grid.width() || nj >= grid.depth()) {
                    continue;
                }
                if (filled[grid.index(ni, nj)] < filled[cell]) {
                    return true;
                }
            }
        }
        return false;
    }
}
