package art.arcane.iris.generation.noise;

import art.arcane.volmlib.util.noise.program.NoiseScratch;

import java.util.Arrays;

public final class TerrainBatchScratch {
    private static final int MINIMUM_BUFFER = 256;

    private final NoiseScratch noise = new NoiseScratch();
    private double[][] buffers = new double[32][];
    private int[][] indexBuffers = new int[8][];
    private int top;
    private int indexTop;
    private long[] latticeX = new long[64];
    private long[] latticeZ = new long[64];
    private int[] latticeSlots = new int[64];
    private int latticeCapacity = 64;
    private int latticeCount;

    public NoiseScratch noise() {
        return noise;
    }

    public int mark() {
        return top;
    }

    public int indexMark() {
        return indexTop;
    }

    public void reset(int mark) {
        top = mark;
    }

    public void resetIndices(int mark) {
        indexTop = mark;
    }

    public double[] buffer(int length) {
        if (top == buffers.length) {
            buffers = Arrays.copyOf(buffers, top * 2);
        }
        double[] buffer = buffers[top];
        if (buffer == null || buffer.length < length) {
            buffer = new double[Math.max(length, MINIMUM_BUFFER)];
            buffers[top] = buffer;
        }
        top++;
        return buffer;
    }

    public int[] indices(int length) {
        if (indexTop == indexBuffers.length) {
            indexBuffers = Arrays.copyOf(indexBuffers, indexTop * 2);
        }
        int[] buffer = indexBuffers[indexTop];
        if (buffer == null || buffer.length < length) {
            buffer = new int[Math.max(length, MINIMUM_BUFFER)];
            indexBuffers[indexTop] = buffer;
        }
        indexTop++;
        return buffer;
    }

    void clearLattice(int maximumEntries) {
        int capacity = Integer.highestOneBit(Math.max(16, maximumEntries * 2) - 1) << 1;
        if (latticeSlots.length < capacity) {
            latticeSlots = new int[capacity];
        } else {
            Arrays.fill(latticeSlots, 0, capacity, 0);
        }
        latticeCapacity = capacity;
        latticeCount = 0;
    }

    int lattice(double x, double z) {
        long xBits = Double.doubleToRawLongBits(x);
        long zBits = Double.doubleToRawLongBits(z);
        long mixed = xBits ^ Long.rotateLeft(zBits, 32);
        mixed = (mixed ^ (mixed >>> 33)) * 0xFF51AFD7ED558CCDL;
        mixed = (mixed ^ (mixed >>> 33)) * 0xC4CEB9FE1A85EC53L;
        mixed ^= mixed >>> 33;
        int mask = latticeCapacity - 1;
        int slot = (int) mixed & mask;
        while (true) {
            int stored = latticeSlots[slot];
            if (stored == 0) {
                if (latticeCount == latticeX.length) {
                    latticeX = Arrays.copyOf(latticeX, latticeCount * 2);
                    latticeZ = Arrays.copyOf(latticeZ, latticeCount * 2);
                }
                latticeX[latticeCount] = xBits;
                latticeZ[latticeCount] = zBits;
                latticeSlots[slot] = ++latticeCount;
                return latticeCount - 1;
            }
            if (latticeX[stored - 1] == xBits && latticeZ[stored - 1] == zBits) {
                return stored - 1;
            }
            slot = (slot + 1) & mask;
        }
    }

    int latticeCount() {
        return latticeCount;
    }

    double latticeX(int index) {
        return Double.longBitsToDouble(latticeX[index]);
    }

    double latticeZ(int index) {
        return Double.longBitsToDouble(latticeZ[index]);
    }
}
