package art.arcane.iris.modules.carve.field;

import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.modules.carve.Cave;
import com.volmit.noise.NoiseFactory;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.function.IntPredicate;

/**
 * The blocks a cave carves in a square of chunks under a flat surface, in a world from Y 0 to 256, with measurements of
 * their shape: shares, runs along an axis and connected pieces. Blocks are indexed by {@link #index}.
 */
final class CarvedArea {
    static final int WORLD_BOTTOM_Y = 0;
    static final int WORLD_HEIGHT = 256;
    static final int SURFACE_Y = 160;
    static final int LAYERS = SURFACE_Y + 1;

    final int size;
    final BitSet carved;

    private CarvedArea(int size, BitSet carved) {
        this.size = size;
        this.carved = carved;
    }

    static CarvedArea carve(NoiseFactory noise, String caveJson, long seed, int chunks) {
        return carveAt(noise, caveJson, seed, chunks, 0, 0);
    }

    /** {@link #carve} of the square whose first block is {@code offsetX}, {@code offsetZ}; blocks are indexed from that corner. */
    static CarvedArea carveAt(NoiseFactory noise, String caveJson, long seed, int chunks, int offsetX, int offsetZ) {
        CaveCarver carver = new CaveCarver(TestSettings.of(Cave.class, caveJson), noise, seed, WORLD_BOTTOM_Y, WORLD_HEIGHT);
        int size = chunks * 16;
        int[] surfaceY = new int[256];
        Arrays.fill(surfaceY, SURFACE_Y);
        double[] weight = new double[256];
        Arrays.fill(weight, 1D);
        boolean[] sealed = new boolean[256];
        BitSet carved = new BitSet(size * size * LAYERS);
        for (int chunkX = 0; chunkX < chunks; chunkX++) {
            for (int chunkZ = 0; chunkZ < chunks; chunkZ++) {
                int originX = chunkX * 16;
                int originZ = chunkZ * 16;
                carver.carve(offsetX + originX, offsetZ + originZ, surfaceY, weight, sealed,
                        (localX, y, localZ) -> carved.set((y * size * size) + ((originZ + localZ) * size) + originX + localX));
            }
        }
        return new CarvedArea(size, carved);
    }

    int index(int x, int y, int z) {
        return (y * size * size) + (z * size) + x;
    }

    int x(int block) {
        return block % size;
    }

    int z(int block) {
        return (block / size) % size;
    }

    int y(int block) {
        return block / (size * size);
    }

    /** {@code where}, tested once for every block of the area, for tests that ask it about every block more than once. */
    IntPredicate cached(IntPredicate where) {
        BitSet holds = new BitSet(size * size * LAYERS);
        for (int block = 0; block < size * size * LAYERS; block++) {
            if (where.test(block)) {
                holds.set(block);
            }
        }
        return holds::get;
    }

    /** Carved blocks among the blocks {@code where} takes, as a share of them. */
    double carvedShare(IntPredicate where) {
        long inside = 0;
        long carvedInside = 0;
        for (int block = 0; block < size * size * LAYERS; block++) {
            if (!where.test(block)) {
                continue;
            }
            inside++;
            if (carved.get(block)) {
                carvedInside++;
            }
        }
        return carvedInside / (double) Math.max(1, inside);
    }

    /** Mean length of the unbroken runs of carved blocks along one axis, counting runs that start where {@code where} holds. */
    double meanRun(int stepX, int stepY, int stepZ, IntPredicate where) {
        long blocks = 0;
        long runs = 0;
        for (int block = carved.nextSetBit(0); block >= 0; block = carved.nextSetBit(block + 1)) {
            if (!where.test(block)) {
                continue;
            }
            blocks++;
            int previousX = x(block) - stepX;
            int previousY = y(block) - stepY;
            int previousZ = z(block) - stepZ;
            boolean previousInside = previousX >= 0 && previousY >= 0 && previousZ >= 0;
            if (!previousInside || !carved.get(index(previousX, previousY, previousZ))) {
                runs++;
            }
        }
        return blocks / (double) Math.max(1, runs);
    }

    double horizontalMeanRun(IntPredicate where) {
        return (meanRun(1, 0, 0, where) + meanRun(0, 0, 1, where)) / 2D;
    }

    /** Share of the blocks either area carves that only one of them carves, among blocks {@code where} takes. */
    double differenceShare(CarvedArea other, IntPredicate where) {
        long either = 0;
        long onlyOne = 0;
        for (int block = 0; block < size * size * LAYERS; block++) {
            if (!where.test(block)) {
                continue;
            }
            boolean mine = carved.get(block);
            boolean theirs = other.carved.get(block);
            if (mine || theirs) {
                either++;
            }
            if (mine != theirs) {
                onlyOne++;
            }
        }
        return onlyOne / (double) Math.max(1, either);
    }

    /** Pieces of carved blocks joined face to face, smaller than {@code blocks}. */
    int carvedPiecesSmallerThan(int blocks) {
        return piecesSmallerThan(carved, blocks, false);
    }

    /** Pieces of rock that touch no edge of the area and are smaller than {@code blocks}: rock left floating in the caves. */
    int floatingRockSmallerThan(int blocks) {
        BitSet rock = (BitSet) carved.clone();
        rock.flip(0, size * size * LAYERS);
        return piecesSmallerThan(rock, blocks, true);
    }

    double largestPieceShare() {
        int largest = 0;
        for (int piece : pieceSizes(carved, false)) {
            largest = Math.max(largest, piece);
        }
        return largest / (double) Math.max(1, carved.cardinality());
    }

    private int piecesSmallerThan(BitSet blocks, int limit, boolean onlyEnclosed) {
        int count = 0;
        for (int piece : pieceSizes(blocks, onlyEnclosed)) {
            if (piece < limit) {
                count++;
            }
        }
        return count;
    }

    /** Sizes of the face-joined pieces of {@code blocks}; with {@code onlyEnclosed}, of those touching no edge of the area. */
    private int[] pieceSizes(BitSet blocks, boolean onlyEnclosed) {
        BitSet seen = new BitSet(blocks.size());
        ArrayDeque<Integer> open = new ArrayDeque<>();
        int[] sizes = new int[0];
        int count = 0;
        for (int root = blocks.nextSetBit(0); root >= 0; root = blocks.nextSetBit(root + 1)) {
            if (seen.get(root)) {
                continue;
            }
            int pieceSize = 0;
            boolean touchesEdge = false;
            seen.set(root);
            open.add(root);
            while (!open.isEmpty()) {
                int block = open.poll();
                pieceSize++;
                int x = x(block);
                int z = z(block);
                int y = y(block);
                touchesEdge |= x == 0 || z == 0 || y == 0 || x == size - 1 || z == size - 1 || y == LAYERS - 1;
                visit(blocks, seen, open, x + 1, y, z);
                visit(blocks, seen, open, x - 1, y, z);
                visit(blocks, seen, open, x, y + 1, z);
                visit(blocks, seen, open, x, y - 1, z);
                visit(blocks, seen, open, x, y, z + 1);
                visit(blocks, seen, open, x, y, z - 1);
            }
            if (onlyEnclosed && touchesEdge) {
                continue;
            }
            if (count == sizes.length) {
                sizes = Arrays.copyOf(sizes, Math.max(16, count * 2));
            }
            sizes[count++] = pieceSize;
        }
        return Arrays.copyOf(sizes, count);
    }

    private void visit(BitSet blocks, BitSet seen, ArrayDeque<Integer> open, int x, int y, int z) {
        if (x < 0 || z < 0 || y < 0 || x >= size || z >= size || y >= LAYERS) {
            return;
        }
        int block = index(x, y, z);
        if (blocks.get(block) && !seen.get(block)) {
            seen.set(block);
            open.add(block);
        }
    }
}
