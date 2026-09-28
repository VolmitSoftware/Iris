package art.arcane.iris.generation.hydrology;

/**
 * The chunks a planned tile publishes at least one footprint column into. A chunk outside every
 * relevant tile's occupancy composes to no hydrology at all, so it can be answered without the
 * tiles being resident.
 */
final class HydrologyChunkOccupancy {
    static final HydrologyChunkOccupancy EMPTY = new HydrologyChunkOccupancy(0, 0, 0, 0, new long[0]);

    private final int minimumChunkX;
    private final int minimumChunkZ;
    private final int width;
    private final int depth;
    private final long[] bits;

    private HydrologyChunkOccupancy(int minimumChunkX, int minimumChunkZ, int width, int depth, long[] bits) {
        this.minimumChunkX = minimumChunkX;
        this.minimumChunkZ = minimumChunkZ;
        this.width = width;
        this.depth = depth;
        this.bits = bits;
    }

    static HydrologyChunkOccupancy of(RiverFootprint footprint) {
        if (footprint == null) {
            return EMPTY;
        }
        int minimumX = Integer.MAX_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (long packed : footprint.columns().keySet()) {
            int chunkX = RiverFootprint.unpackX(packed) >> 4;
            int chunkZ = RiverFootprint.unpackZ(packed) >> 4;
            minimumX = Math.min(minimumX, chunkX);
            minimumZ = Math.min(minimumZ, chunkZ);
            maximumX = Math.max(maximumX, chunkX);
            maximumZ = Math.max(maximumZ, chunkZ);
        }
        if (minimumX > maximumX) {
            return EMPTY;
        }
        int width = maximumX - minimumX + 1;
        int depth = maximumZ - minimumZ + 1;
        long[] bits = new long[Math.toIntExact(Math.ceilDiv(Math.multiplyExact((long) width, depth), 64L))];
        for (long packed : footprint.columns().keySet()) {
            int index = ((RiverFootprint.unpackZ(packed) >> 4) - minimumZ) * width
                    + (RiverFootprint.unpackX(packed) >> 4) - minimumX;
            bits[index >>> 6] |= 1L << index;
        }
        return new HydrologyChunkOccupancy(minimumX, minimumZ, width, depth, bits);
    }

    boolean occupies(int chunkX, int chunkZ) {
        int localX = chunkX - minimumChunkX;
        int localZ = chunkZ - minimumChunkZ;
        if (localX < 0 || localZ < 0 || localX >= width || localZ >= depth) {
            return false;
        }
        int index = localZ * width + localX;
        return (bits[index >>> 6] & 1L << index) != 0L;
    }
}
