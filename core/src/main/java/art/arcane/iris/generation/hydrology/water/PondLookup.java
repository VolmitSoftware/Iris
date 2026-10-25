package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.grid.DemGrid;

import java.util.Arrays;
import java.util.List;

final class PondLookup {
    private static final int BUCKET = 32;
    private static final int[] NONE = new int[0];

    private final List<Pond> ponds;
    private final int[] bounds;
    private final int bucketX0;
    private final int bucketZ0;
    private final int bucketsWide;
    private final int bucketsDeep;
    private final int[][] buckets;

    PondLookup(List<Pond> ponds) {
        this.ponds = List.copyOf(ponds);
        this.bounds = new int[4 * ponds.size()];
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int index = 0; index < ponds.size(); index++) {
            PondMask mask = ponds.get(index).mask();
            int half = mask.step() >> 1;
            bounds[4 * index] = mask.originX() - half;
            bounds[4 * index + 1] = mask.originZ() - half;
            bounds[4 * index + 2] = mask.originX() + (mask.width() - 1) * mask.step() + half;
            bounds[4 * index + 3] = mask.originZ() + (mask.depth() - 1) * mask.step() + half;
            minX = Math.min(minX, bounds[4 * index]);
            minZ = Math.min(minZ, bounds[4 * index + 1]);
            maxX = Math.max(maxX, bounds[4 * index + 2]);
            maxZ = Math.max(maxZ, bounds[4 * index + 3]);
        }
        this.bucketX0 = ponds.isEmpty() ? 0 : Math.floorDiv(minX, BUCKET);
        this.bucketZ0 = ponds.isEmpty() ? 0 : Math.floorDiv(minZ, BUCKET);
        this.bucketsWide = ponds.isEmpty() ? 0 : Math.floorDiv(maxX, BUCKET) - bucketX0 + 1;
        this.bucketsDeep = ponds.isEmpty() ? 0 : Math.floorDiv(maxZ, BUCKET) - bucketZ0 + 1;
        this.buckets = index();
    }

    int size() {
        return ponds.size();
    }

    Pond pond(int index) {
        return ponds.get(index);
    }

    int at(int x, int z) {
        int bx = Math.floorDiv(x, BUCKET) - bucketX0;
        int bz = Math.floorDiv(z, BUCKET) - bucketZ0;
        if (bx < 0 || bz < 0 || bx >= bucketsWide || bz >= bucketsDeep) {
            return -1;
        }
        for (int index : buckets[bz * bucketsWide + bx]) {
            if (x >= bounds[4 * index] && z >= bounds[4 * index + 1] && x <= bounds[4 * index + 2] && z <= bounds[4 * index + 3]
                    && ponds.get(index).mask().contains(x, z)) {
                return index;
            }
        }
        return -1;
    }

    int[] cells(DemGrid grid) {
        int[] owners = new int[grid.width() * grid.depth()];
        Arrays.fill(owners, -1);
        int cell = grid.cell();
        for (int index = 0; index < ponds.size(); index++) {
            int minI = Math.max(0, Math.ceilDiv(bounds[4 * index] - grid.originX(), cell));
            int minJ = Math.max(0, Math.ceilDiv(bounds[4 * index + 1] - grid.originZ(), cell));
            int maxI = Math.min(grid.width() - 1, Math.floorDiv(bounds[4 * index + 2] - grid.originX(), cell));
            int maxJ = Math.min(grid.depth() - 1, Math.floorDiv(bounds[4 * index + 3] - grid.originZ(), cell));
            PondMask mask = ponds.get(index).mask();
            for (int j = minJ; j <= maxJ; j++) {
                for (int i = minI; i <= maxI; i++) {
                    int owner = j * grid.width() + i;
                    if (owners[owner] < 0 && mask.contains(grid.originX() + i * cell, grid.originZ() + j * cell)) {
                        owners[owner] = index;
                    }
                }
            }
        }
        return owners;
    }

    private int[][] index() {
        int[] sizes = new int[Math.multiplyExact(bucketsWide, bucketsDeep)];
        for (int index = 0; index < ponds.size(); index++) {
            for (int bucket : bucketsOf(index)) {
                sizes[bucket]++;
            }
        }
        int[][] index = new int[sizes.length][];
        for (int bucket = 0; bucket < sizes.length; bucket++) {
            index[bucket] = sizes[bucket] == 0 ? NONE : new int[sizes[bucket]];
        }
        int[] filled = new int[sizes.length];
        for (int pond = 0; pond < ponds.size(); pond++) {
            for (int bucket : bucketsOf(pond)) {
                index[bucket][filled[bucket]++] = pond;
            }
        }
        return index;
    }

    private int[] bucketsOf(int pond) {
        int minX = Math.floorDiv(bounds[4 * pond], BUCKET) - bucketX0;
        int minZ = Math.floorDiv(bounds[4 * pond + 1], BUCKET) - bucketZ0;
        int maxX = Math.floorDiv(bounds[4 * pond + 2], BUCKET) - bucketX0;
        int maxZ = Math.floorDiv(bounds[4 * pond + 3], BUCKET) - bucketZ0;
        int[] covered = new int[(maxX - minX + 1) * (maxZ - minZ + 1)];
        int count = 0;
        for (int bz = minZ; bz <= maxZ; bz++) {
            for (int bx = minX; bx <= maxX; bx++) {
                covered[count++] = bz * bucketsWide + bx;
            }
        }
        return covered;
    }
}
