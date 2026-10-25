package art.arcane.iris.generation.hydrology.water;

import java.util.Arrays;
import java.util.Objects;

public record LakeCandidate(
        int floorX,
        int floorZ,
        float floorHeight,
        int spillX,
        int spillZ,
        float spillHeight,
        int radius,
        int cell,
        long[] cells
) {
    public static final int MAX_EXTENT = 2048;
    public static final int BOUND_CELLS = 2;
    private static final int NEAR_CELLS = 5;

    public LakeCandidate {
        Objects.requireNonNull(cells, "cells");
        if (cell <= 0 || cells.length == 0) {
            throw new IllegalArgumentException("Lake candidate at " + floorX + "," + floorZ + " needs cells: cell=" + cell
                    + " count=" + cells.length);
        }
    }

    public static long pack(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    public boolean bounds(int x, int z) {
        int cellX = Math.floorDiv(x + (cell >> 1), cell);
        int cellZ = Math.floorDiv(z + (cell >> 1), cell);
        for (int dz = -BOUND_CELLS; dz <= BOUND_CELLS; dz++) {
            for (int dx = -BOUND_CELLS; dx <= BOUND_CELLS; dx++) {
                if (Arrays.binarySearch(cells, pack(cellX + dx, cellZ + dz)) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public int[] cellBox() {
        int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (long packed : cells) {
            int cellX = (int) (packed >> 32);
            int cellZ = (int) packed;
            box[0] = Math.min(box[0], cellX);
            box[1] = Math.min(box[1], cellZ);
            box[2] = Math.max(box[2], cellX);
            box[3] = Math.max(box[3], cellZ);
        }
        return box;
    }

    public boolean near(LakeCandidate other) {
        if (cell != other.cell) {
            throw new IllegalArgumentException("Lake candidates use different cells: " + cell + " and " + other.cell);
        }
        long reach = (long) radius + other.radius + (long) NEAR_CELLS * cell;
        if (Math.abs((long) floorX - other.floorX) > reach || Math.abs((long) floorZ - other.floorZ) > reach) {
            return false;
        }
        for (long packed : cells) {
            int cellX = (int) (packed >> 32);
            int cellZ = (int) packed;
            for (int dz = -NEAR_CELLS; dz <= NEAR_CELLS; dz++) {
                for (int dx = -NEAR_CELLS; dx <= NEAR_CELLS; dx++) {
                    if (Arrays.binarySearch(other.cells, pack(cellX + dx, cellZ + dz)) >= 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LakeCandidate candidate && floorX == candidate.floorX && floorZ == candidate.floorZ
                && Float.floatToIntBits(floorHeight) == Float.floatToIntBits(candidate.floorHeight) && spillX == candidate.spillX
                && spillZ == candidate.spillZ && Float.floatToIntBits(spillHeight) == Float.floatToIntBits(candidate.spillHeight)
                && radius == candidate.radius && cell == candidate.cell && Arrays.equals(cells, candidate.cells);
    }

    @Override
    public int hashCode() {
        int hash = 31 * floorX + floorZ;
        hash = 31 * hash + Float.floatToIntBits(floorHeight);
        hash = 31 * (31 * hash + spillX) + spillZ;
        hash = 31 * hash + Float.floatToIntBits(spillHeight);
        hash = 31 * (31 * hash + radius) + cell;
        return 31 * hash + Arrays.hashCode(cells);
    }

    @Override
    public String toString() {
        return "LakeCandidate[floor=" + floorX + "," + floorZ + "@" + floorHeight + ", spill=" + spillX + "," + spillZ + "@" + spillHeight
                + ", radius=" + radius + ", cells=" + cells.length + "x" + cell + "]";
    }
}
