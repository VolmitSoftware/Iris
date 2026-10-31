package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.water.HydrologyTier;

import java.util.Arrays;

public final class ChunkWaterRaster {
    public static final int MAX_SHORE_DISTANCE = 32;
    public static final ChunkWaterRaster PROVISIONAL = new ChunkWaterRaster(true);
    public static final ChunkWaterRaster EMPTY = new ChunkWaterRaster(false);
    static final int COLUMNS = 256;
    private static final WaterRole[] ROLES = WaterRole.values();

    private final boolean provisional;
    private final int[] terrain;
    private final int[] head;
    private final byte[] role;
    private final byte[] tier;
    private final HydrologyTier[] tiers;
    private final byte[] feature;
    private final long[] features;
    private final byte[] fluid;
    private final String[] fluids;
    private final boolean caveMask;
    private final byte[] shoreDistance;

    private ChunkWaterRaster(boolean provisional) {
        this.provisional = provisional;
        this.terrain = null;
        this.head = null;
        this.role = null;
        this.tier = null;
        this.tiers = null;
        this.feature = null;
        this.features = null;
        this.fluid = null;
        this.fluids = null;
        this.caveMask = false;
        this.shoreDistance = null;
    }

    ChunkWaterRaster(int[] terrain, int[] head, byte[] role, Palette<HydrologyTier> tiers, LongPalette features, Palette<String> fluids,
                     boolean caveMask, byte[] shoreDistance) {
        if (terrain.length != COLUMNS || head.length != COLUMNS || role.length != COLUMNS || tiers.indices.length != COLUMNS
                || features.indices.length != COLUMNS || fluids.indices.length != COLUMNS || shoreDistance.length != COLUMNS) {
            throw new IllegalArgumentException("A chunk water raster needs " + COLUMNS + " entries per array");
        }
        this.provisional = false;
        this.terrain = terrain;
        this.head = head;
        this.role = role;
        this.tier = tiers.indices;
        this.tiers = tiers.values();
        this.feature = features.indices;
        this.features = features.values();
        this.fluid = fluids.indices;
        this.fluids = fluids.values();
        this.caveMask = caveMask;
        this.shoreDistance = shoreDistance;
    }

    public int terrainHeight(int lx, int lz) {
        int index = index(lx, lz);
        if (terrain == null) {
            throw new IllegalStateException((provisional ? "A provisional" : "An empty") + " water raster has no terrain heights; use the natural height");
        }
        return terrain[index];
    }

    public int waterHead(int lx, int lz) {
        int index = index(lx, lz);
        return head == null ? Integer.MIN_VALUE : head[index];
    }

    public WaterRole role(int lx, int lz) {
        int index = index(lx, lz);
        return role == null ? WaterRole.NONE : ROLES[role[index]];
    }

    public HydrologyTier tier(int lx, int lz) {
        int index = index(lx, lz);
        return tier == null ? null : tiers[tier[index] & 0xFF];
    }

    public long featureId(int lx, int lz) {
        int index = index(lx, lz);
        return feature == null ? 0L : features[feature[index] & 0xFF];
    }

    public String fluid(int lx, int lz) {
        int index = index(lx, lz);
        return fluid == null ? null : fluids[fluid[index] & 0xFF];
    }

    public int caveCeiling(int lx, int lz) {
        int index = index(lx, lz);
        return caveMask && head[index] != Integer.MIN_VALUE ? terrain[index] - 1 : Integer.MAX_VALUE;
    }

    public int shoreDistance(int lx, int lz) {
        int index = index(lx, lz);
        return shoreDistance == null ? MAX_SHORE_DISTANCE : shoreDistance[index];
    }

    public boolean provisional() {
        return provisional;
    }

    public boolean empty() {
        return terrain == null;
    }

    private static int index(int lx, int lz) {
        if (((lx | lz) & ~15) != 0) {
            throw new IndexOutOfBoundsException("Chunk column " + lx + "," + lz + " is outside 0..15");
        }
        return lz << 4 | lx;
    }

    static final class Palette<T> {
        private final byte[] indices = new byte[COLUMNS];
        private final T[] seen;
        private int size;

        Palette(T[] seen) {
            if (seen.length != COLUMNS) {
                throw new IllegalArgumentException("A raster palette needs room for " + COLUMNS + " values");
            }
            this.seen = seen;
        }

        void set(int column, T value) {
            int found = 0;
            while (found < size && seen[found] != value) {
                found++;
            }
            if (found == size) {
                seen[size++] = value;
            }
            indices[column] = (byte) found;
        }

        private T[] values() {
            return Arrays.copyOf(seen, size);
        }
    }

    static final class LongPalette {
        private final byte[] indices = new byte[COLUMNS];
        private final long[] seen = new long[COLUMNS];
        private int size;

        void set(int column, long value) {
            int found = 0;
            while (found < size && seen[found] != value) {
                found++;
            }
            if (found == size) {
                seen[size++] = value;
            }
            indices[column] = (byte) found;
        }

        private long[] values() {
            return Arrays.copyOf(seen, size);
        }
    }
}
