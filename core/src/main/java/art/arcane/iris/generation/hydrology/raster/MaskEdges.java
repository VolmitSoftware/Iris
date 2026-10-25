package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.water.PondMask;

import java.util.Arrays;

final class MaskEdges {
    private final Kind kind;
    private int[] squares = new int[64];
    private int[] starts = new int[4];
    private int count;
    private int slots;

    MaskEdges(Kind kind) {
        this.kind = kind;
    }

    void collect(PondMask mask, int minX, int minZ, int maxX, int maxZ) {
        if (slots + 2 > starts.length) {
            starts = Arrays.copyOf(starts, 2 * starts.length);
        }
        starts[slots] = count;
        int step = mask.step();
        int half = step >> 1;
        int margin = kind == Kind.WATER ? 0 : 1;
        int firstI = Math.max(-margin, Math.floorDiv(minX - mask.originX() + half, step));
        int lastI = Math.min(mask.width() - 1 + margin, Math.floorDiv(maxX - mask.originX() + half, step));
        int firstJ = Math.max(-margin, Math.floorDiv(minZ - mask.originZ() + half, step));
        int lastJ = Math.min(mask.depth() - 1 + margin, Math.floorDiv(maxZ - mask.originZ() + half, step));
        for (int j = firstJ; j <= lastJ; j++) {
            for (int i = firstI; i <= lastI; i++) {
                if (edge(mask, i, j)) {
                    int x = mask.originX() + i * step - half;
                    int z = mask.originZ() + j * step - half;
                    append(x, z, x + step - 1, z + step - 1);
                }
            }
        }
        slots++;
        starts[slots] = count;
    }

    double distance(int slot, int x, int z) {
        long squared = squared(slot, x, z);
        return squared == Long.MAX_VALUE ? Double.POSITIVE_INFINITY : Math.sqrt(squared);
    }

    long squared(int slot, int x, int z) {
        long best = Long.MAX_VALUE;
        for (int square = starts[slot]; square < starts[slot + 1]; square += 4) {
            long dx = Math.max(0, Math.max(squares[square] - x, x - squares[square + 2]));
            long dz = Math.max(0, Math.max(squares[square + 1] - z, z - squares[square + 3]));
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    private boolean edge(PondMask mask, int i, int j) {
        boolean set = set(mask, i, j);
        if (set == (kind == Kind.BANK)) {
            return false;
        }
        return set(mask, i + 1, j) != set || set(mask, i - 1, j) != set || set(mask, i, j + 1) != set || set(mask, i, j - 1) != set;
    }

    private boolean set(PondMask mask, int i, int j) {
        return kind == Kind.GROWN_WATER ? mask.grownNode(i, j) : mask.node(i, j);
    }

    private void append(int minX, int minZ, int maxX, int maxZ) {
        if (count + 4 > squares.length) {
            squares = Arrays.copyOf(squares, 2 * squares.length);
        }
        squares[count++] = minX;
        squares[count++] = minZ;
        squares[count++] = maxX;
        squares[count++] = maxZ;
    }

    enum Kind {
        WATER,
        BANK,
        GROWN_WATER
    }
}
