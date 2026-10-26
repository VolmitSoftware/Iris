package art.arcane.iris.generation.hydrology.raster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.function.IntBinaryOperator;

final class RiffleProfile {
    static final int RADIUS = 16;
    private static final int CHUNK = 16;
    private static final int PADDING = RADIUS + 1;
    private static final int GRID = CHUNK + 2 * PADDING;
    private static final int OUTPUT = CHUNK + 2;
    private static final int LIMIT = 10 * RADIUS;
    private static final int DRY = Integer.MIN_VALUE;
    private static final int[] SIDES = {1, -1, GRID, -GRID};

    private final List<Field> fields = new ArrayList<>();

    RiffleProfile(int chunkX, int chunkZ, IntBinaryOperator headAt) {
        int[] heads = new int[GRID * GRID];
        int[] crests = new int[GRID * GRID];
        int originX = (chunkX << 4) - PADDING;
        int originZ = (chunkZ << 4) - PADDING;
        Arrays.fill(crests, DRY);
        for (int cell = 0; cell < heads.length; cell++) {
            heads[cell] = headAt.applyAsInt(originX + cell % GRID, originZ + cell / GRID);
        }
        TreeSet<Integer> levels = new TreeSet<>();
        for (int z = 1; z < GRID - 1; z++) {
            for (int x = 1; x < GRID - 1; x++) {
                int cell = z * GRID + x;
                if (heads[cell] == DRY) {
                    continue;
                }
                for (int side : SIDES) {
                    if (heads[cell + side] != DRY && heads[cell + side] <= heads[cell] - 2) {
                        crests[cell] = heads[cell] - 1;
                        levels.add(crests[cell]);
                        break;
                    }
                }
            }
        }
        if (levels.isEmpty()) {
            return;
        }
        DistanceField distances = new DistanceField(heads);
        for (int crest : levels) {
            distances.reset();
            for (int cell = 0; cell < crests.length; cell++) {
                if (crests[cell] == crest) {
                    distances.offer(cell, 0);
                }
            }
            distances.spread();
            int[] output = distances.output();
            if (output != null) {
                fields.add(new Field(crest, output));
            }
        }
    }

    int bed(int localX, int localZ, int original, int head, double variation) {
        if (head == DRY) {
            return original;
        }
        int cell = (localZ + 1) * OUTPUT + localX + 1;
        double raised = original;
        for (Field field : fields) {
            int crest = Math.min(head - 1, field.crest());
            if (crest <= original) {
                continue;
            }
            double reach = 10D * Math.min(RADIUS, Math.max(4D, 2D * (crest - (double) original)))
                    * (0.925D + 0.075D * variation);
            if (field.distances()[cell] >= reach) {
                continue;
            }
            double u = field.distances()[cell] / reach;
            double blend = 1D - u * u * (3D - 2D * u);
            raised = Math.max(raised, original + (crest - (double) original) * blend);
        }
        return (int) Math.max(original, Math.min(head - 1L, Math.round(raised)));
    }

    private record Field(int crest, int[] distances) {
    }

    private static final class DistanceField {
        private final int[] heads;
        private final int[] distances = new int[GRID * GRID];
        private final int[] next = new int[GRID * GRID];
        private final int[] previous = new int[GRID * GRID];
        private final int[] first = new int[LIMIT];

        private DistanceField(int[] heads) {
            this.heads = heads;
        }

        private void reset() {
            Arrays.fill(distances, LIMIT);
            Arrays.fill(next, -1);
            Arrays.fill(previous, -1);
            Arrays.fill(first, -1);
        }

        private void offer(int cell, int distance) {
            if (distances[cell] < LIMIT) {
                remove(cell);
            }
            distances[cell] = distance;
            next[cell] = first[distance];
            previous[cell] = -1;
            if (next[cell] >= 0) {
                previous[next[cell]] = cell;
            }
            first[distance] = cell;
        }

        private void remove(int cell) {
            if (previous[cell] < 0) {
                first[distances[cell]] = next[cell];
            } else {
                next[previous[cell]] = next[cell];
            }
            if (next[cell] >= 0) {
                previous[next[cell]] = previous[cell];
            }
            next[cell] = -1;
            previous[cell] = -1;
        }

        private void spread() {
            for (int distance = 0; distance < LIMIT; distance++) {
                while (first[distance] >= 0) {
                    int cell = first[distance];
                    remove(cell);
                    int x = cell % GRID;
                    int z = cell / GRID;
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx;
                            int nz = z + dz;
                            if (dx == 0 && dz == 0 || nx < 0 || nx >= GRID || nz < 0 || nz >= GRID) {
                                continue;
                            }
                            int neighbour = nz * GRID + nx;
                            int candidate = distance + (dx != 0 && dz != 0 ? 14 : 10);
                            if (heads[neighbour] == DRY || candidate >= distances[neighbour]) {
                                continue;
                            }
                            if (dx != 0 && dz != 0 && (heads[z * GRID + nx] == DRY || heads[nz * GRID + x] == DRY)) {
                                continue;
                            }
                            offer(neighbour, candidate);
                        }
                    }
                }
            }
        }

        private int[] output() {
            int[] output = new int[OUTPUT * OUTPUT];
            boolean affected = false;
            for (int z = 0; z < OUTPUT; z++) {
                for (int x = 0; x < OUTPUT; x++) {
                    int distance = distances[(z + PADDING - 1) * GRID + x + PADDING - 1];
                    output[z * OUTPUT + x] = distance;
                    affected |= distance < LIMIT;
                }
            }
            return affected ? output : null;
        }
    }
}
