package art.arcane.iris.platform.bukkit.terrain;

import art.arcane.iris.api.terrain.IrisColumnQuery;

public final class IrisColumnWalk {
    private IrisColumnWalk() {
    }

    public static long walk(IrisColumnQuery query, ColumnVisitor visitor) {
        int stride = query.strideBlocks();
        int minChunkX = query.minBlockX() >> 4;
        int maxChunkX = query.maxBlockX() >> 4;
        int minChunkZ = query.minBlockZ() >> 4;
        int maxChunkZ = query.maxBlockZ() >> 4;
        long visited = 0L;

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            int chunkMinBlockZ = Math.max(query.minBlockZ(), chunkZ << 4);
            int chunkMaxBlockZ = Math.min(query.maxBlockZ(), (chunkZ << 4) + 15);

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                int chunkMinBlockX = Math.max(query.minBlockX(), chunkX << 4);
                int chunkMaxBlockX = Math.min(query.maxBlockX(), (chunkX << 4) + 15);

                for (long blockZ = align(query.minBlockZ(), chunkMinBlockZ, stride); blockZ <= chunkMaxBlockZ; blockZ += stride) {
                    for (long blockX = align(query.minBlockX(), chunkMinBlockX, stride); blockX <= chunkMaxBlockX; blockX += stride) {
                        if (!visitor.visit((int) blockX, (int) blockZ)) {
                            return visited;
                        }
                        visited++;
                    }
                }
            }
        }

        return visited;
    }

    private static long align(int origin, int lowerBound, int stride) {
        long offset = (long) lowerBound - (long) origin;
        long steps = (offset + stride - 1L) / stride;
        return origin + steps * stride;
    }

    @FunctionalInterface
    public interface ColumnVisitor {
        boolean visit(int blockX, int blockZ);
    }
}
