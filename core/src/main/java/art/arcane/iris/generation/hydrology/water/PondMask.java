package art.arcane.iris.generation.hydrology.water;

import java.util.Arrays;

public record PondMask(int originX, int originZ, int step, int width, int depth, long[] bits) {
    public PondMask {
        if (step <= 0 || width < 0 || depth < 0) {
            throw new IllegalArgumentException("Invalid pond mask step=" + step + " width=" + width + " depth=" + depth);
        }
        long cells = (long) width * depth;
        if (bits.length < (cells + 63L) >>> 6) {
            throw new IllegalArgumentException("Pond mask has " + bits.length + " words for " + cells + " cells");
        }
    }

    public boolean node(int i, int j) {
        if (i < 0 || j < 0 || i >= width || j >= depth) {
            return false;
        }
        int index = j * width + i;
        return (bits[index >>> 6] & (1L << index)) != 0L;
    }

    public boolean grownNode(int i, int j) {
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                if (node(i + di, j + dj)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean contains(int x, int z) {
        return node(Math.floorDiv(x - originX + (step >> 1), step), Math.floorDiv(z - originZ + (step >> 1), step));
    }

    public boolean grownContains(int x, int z) {
        return grownNode(Math.floorDiv(x - originX + (step >> 1), step), Math.floorDiv(z - originZ + (step >> 1), step));
    }

    public boolean overlaps(PondMask other) {
        int half = step >> 1;
        int otherHalf = other.step >> 1;
        if (originX - half > other.originX + (other.width - 1) * other.step + otherHalf
                || other.originX - otherHalf > originX + (width - 1) * step + half
                || originZ - half > other.originZ + (other.depth - 1) * other.step + otherHalf
                || other.originZ - otherHalf > originZ + (depth - 1) * step + half) {
            return false;
        }
        for (int j = 0; j < depth; j++) {
            for (int i = 0; i < width; i++) {
                if (node(i, j) && other.contains(originX + i * step, originZ + j * step)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PondMask mask && originX == mask.originX && originZ == mask.originZ && step == mask.step
                && width == mask.width && depth == mask.depth && Arrays.equals(bits, mask.bits);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * (31 * (31 * originX + originZ) + step) + width) + depth) + Arrays.hashCode(bits);
    }
}
