package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;

final class EffectiveWater {
    static final int DRY = Integer.MIN_VALUE;
    static final long NATURAL = Long.MIN_VALUE;

    private EffectiveWater() {
    }

    static long column(int ground, int head) {
        return ((long) ground << 32) | (head & 0xFFFFFFFFL);
    }

    static int ground(long column) {
        return (int) (column >> 32);
    }

    static int head(long column) {
        return (int) column;
    }

    static long settle(long column, long east, long west, long south, long north) {
        int ground = ground(column);
        int head = head(column);
        int highest = Math.max(Math.max(head(east), head(west)), Math.max(head(south), head(north)));
        if (head == DRY) {
            return highest == DRY ? column : column(Math.max(ground, highest + 1), DRY);
        }
        int oneAbove = oneAbove(head, east) + oneAbove(head, west) + oneAbove(head, south) + oneAbove(head, north);
        if (oneAbove >= 2) {
            return column(Math.max(ground, highest), DRY);
        }
        return dropsBelow(head, east, west, south, north) ? column(Math.max(ground, head - 1), head) : column;
    }

    static void settleGrid(int originX, int originZ, int width, int depth, Columns columns, long[] settled) {
        int paddedWidth = width + 2;
        int paddedDepth = depth + 2;
        long[] pre = new long[paddedWidth * paddedDepth];
        boolean[] rasterWet = new boolean[pre.length];
        for (int pz = 0; pz < paddedDepth; pz++) {
            for (int px = 0; px < paddedWidth; px++) {
                boolean corner = (px == 0 || px == paddedWidth - 1) && (pz == 0 || pz == paddedDepth - 1);
                int index = pz * paddedWidth + px;
                int x = originX - 1 + px;
                int z = originZ - 1 + pz;
                pre[index] = corner ? column(0, DRY) : columns.pre(x, z);
                rasterWet[index] = !corner && columns.rasterWet(x, z);
            }
        }
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                int index = (z + 1) * paddedWidth + x + 1;
                settled[z * width + x] = rasterWet[index]
                        ? settle(pre[index], pre[index + 1], pre[index - 1], pre[index + paddedWidth], pre[index - paddedWidth])
                        : pre[index];
            }
        }
    }

    private static int oneAbove(int head, long neighbour) {
        return head(neighbour) != DRY && head(neighbour) == head + 1 ? 1 : 0;
    }

    private static boolean dropsBelow(int head, long east, long west, long south, long north) {
        return dropsBelow(head, east) || dropsBelow(head, west) || dropsBelow(head, south) || dropsBelow(head, north);
    }

    private static boolean dropsBelow(int head, long neighbour) {
        return head(neighbour) != DRY && head(neighbour) <= head - 2;
    }

    interface Columns {
        long pre(int x, int z);

        boolean rasterWet(int x, int z);
    }

    static final class Chunk {
        static final Chunk EMPTY = new Chunk(null, null, null, null);

        private final long[] columns;
        private final WaterRole[] roles;
        private final boolean[] owned;
        private final ChunkWaterRaster raster;

        Chunk(long[] columns, WaterRole[] roles, boolean[] owned, ChunkWaterRaster raster) {
            this.columns = columns;
            this.roles = roles;
            this.owned = owned;
            this.raster = raster;
        }

        long column(int lx, int lz) {
            return columns == null ? NATURAL : columns[lz << 4 | lx];
        }

        WaterRole role(int lx, int lz) {
            return roles == null ? WaterRole.NONE : roles[lz << 4 | lx];
        }

        boolean owned(int lx, int lz) {
            return owned != null && owned[lz << 4 | lx];
        }

        ChunkWaterRaster raster() {
            return raster;
        }
    }

    static final class Chunks {
        private static final int SLOTS = 16;

        private final long[] keys = new long[SLOTS];
        private final Chunk[] chunks = new Chunk[SLOTS];

        Chunk get(int chunkX, int chunkZ) {
            int slot = slot(chunkX, chunkZ);
            return chunks[slot] != null && keys[slot] == key(chunkX, chunkZ) ? chunks[slot] : null;
        }

        void put(int chunkX, int chunkZ, Chunk chunk) {
            int slot = slot(chunkX, chunkZ);
            keys[slot] = key(chunkX, chunkZ);
            chunks[slot] = chunk;
        }

        private static int slot(int chunkX, int chunkZ) {
            return ((chunkZ & 3) << 2) | (chunkX & 3);
        }

        private static long key(int chunkX, int chunkZ) {
            return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        }
    }
}
