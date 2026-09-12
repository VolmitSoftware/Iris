package art.arcane.iris.platform.bukkit.terrain;

import art.arcane.iris.api.terrain.IrisColumnQuery;

public final class IrisSampleLimits {
    public static final int MINIMUM_CHUNKS = 64;
    public static final int CACHE_SHARE_DIVISOR = 4;

    private IrisSampleLimits() {
    }

    public static int maxChunks(int noiseCacheChunks) {
        return Math.max(MINIMUM_CHUNKS, noiseCacheChunks / CACHE_SHARE_DIVISOR);
    }

    public static int maxColumns(int noiseCacheChunks) {
        long columns = (long) maxChunks(noiseCacheChunks) * 256L;
        return (int) Math.min(columns, Integer.MAX_VALUE);
    }

    public static boolean withinLimits(IrisColumnQuery query, int maxColumns, int maxChunks) {
        return query.columnCount() <= maxColumns && query.chunkCount() <= maxChunks;
    }
}
