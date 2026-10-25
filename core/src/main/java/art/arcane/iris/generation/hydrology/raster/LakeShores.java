package art.arcane.iris.generation.hydrology.raster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntBinaryOperator;

final class LakeShores {
    private static final int REACH = ChunkWaterRaster.MAX_SHORE_DISTANCE;
    private static final int SPAN = 16 + 2 * REACH;
    private static final int UNKNOWN = -2;
    private static final int DRY = -1;
    private static final int[] OFFSET_X;
    private static final int[] OFFSET_Z;
    private static final int[] OFFSET_SQUARED;

    static {
        List<int[]> offsets = new ArrayList<>();
        for (int dz = 1 - REACH; dz < REACH; dz++) {
            for (int dx = 1 - REACH; dx < REACH; dx++) {
                if (dx * dx + dz * dz < REACH * REACH) {
                    offsets.add(new int[]{dx, dz, dx * dx + dz * dz});
                }
            }
        }
        offsets.sort(Comparator.<int[]>comparingInt(offset -> offset[2]).thenComparingInt(offset -> offset[1])
                .thenComparingInt(offset -> offset[0]));
        OFFSET_X = new int[offsets.size()];
        OFFSET_Z = new int[offsets.size()];
        OFFSET_SQUARED = new int[offsets.size()];
        for (int index = 0; index < offsets.size(); index++) {
            OFFSET_X[index] = offsets.get(index)[0];
            OFFSET_Z[index] = offsets.get(index)[1];
            OFFSET_SQUARED[index] = offsets.get(index)[2];
        }
    }

    private final ChunkFeatures features;
    private final IntBinaryOperator naturalHeight;
    private final int originX;
    private final int originZ;
    private final int[] state = new int[SPAN * SPAN];
    private double foundDistance;
    private int foundLake;

    LakeShores(ChunkFeatures features, IntBinaryOperator naturalHeight, int chunkMinX, int chunkMinZ) {
        this.features = features;
        this.naturalHeight = naturalHeight;
        this.originX = chunkMinX - REACH;
        this.originZ = chunkMinZ - REACH;
        Arrays.fill(state, UNKNOWN);
    }

    boolean find(int x, int z, long lowerSquared, double upper) {
        double limit = Math.min(upper, REACH);
        double limitSquared = limit * limit;
        for (int offset = first(lowerSquared); offset < OFFSET_SQUARED.length && OFFSET_SQUARED[offset] < limitSquared; offset++) {
            int lake = state(x + OFFSET_X[offset], z + OFFSET_Z[offset]);
            if (lake >= 0) {
                foundDistance = Math.sqrt(OFFSET_SQUARED[offset]);
                foundLake = lake;
                return true;
            }
        }
        return false;
    }

    double foundDistance() {
        return foundDistance;
    }

    int foundLake() {
        return foundLake;
    }

    private int state(int x, int z) {
        int cell = (z - originZ) * SPAN + x - originX;
        int known = state[cell];
        if (known == UNKNOWN) {
            known = features.lakeCovers(x, z) ? Math.max(DRY, features.lakeAt(x, z, naturalHeight.applyAsInt(x, z))) : DRY;
            state[cell] = known;
        }
        return known;
    }

    private static int first(long lowerSquared) {
        int low = 0;
        int high = OFFSET_SQUARED.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (OFFSET_SQUARED[middle] < lowerSquared) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }
}
