package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.water.HydrologyTier;

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
    private final HydrologyTier[] tier;
    private final long[] featureId;
    private final String[] fluid;
    private final int[] caveCeiling;
    private final byte[] shoreDistance;

    private ChunkWaterRaster(boolean provisional) {
        this.provisional = provisional;
        this.terrain = null;
        this.head = null;
        this.role = null;
        this.tier = null;
        this.featureId = null;
        this.fluid = null;
        this.caveCeiling = null;
        this.shoreDistance = null;
    }

    ChunkWaterRaster(int[] terrain, int[] head, byte[] role, HydrologyTier[] tier, long[] featureId, String[] fluid, int[] caveCeiling,
                     byte[] shoreDistance) {
        if (terrain.length != COLUMNS || head.length != COLUMNS || role.length != COLUMNS || tier.length != COLUMNS
                || featureId.length != COLUMNS || fluid.length != COLUMNS || caveCeiling.length != COLUMNS || shoreDistance.length != COLUMNS) {
            throw new IllegalArgumentException("A chunk water raster needs " + COLUMNS + " entries per array");
        }
        this.provisional = false;
        this.terrain = terrain;
        this.head = head;
        this.role = role;
        this.tier = tier;
        this.featureId = featureId;
        this.fluid = fluid;
        this.caveCeiling = caveCeiling;
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
        return tier == null ? null : tier[index];
    }

    public long featureId(int lx, int lz) {
        int index = index(lx, lz);
        return featureId == null ? 0L : featureId[index];
    }

    public String fluid(int lx, int lz) {
        int index = index(lx, lz);
        return fluid == null ? null : fluid[index];
    }

    public int caveCeiling(int lx, int lz) {
        int index = index(lx, lz);
        return caveCeiling == null ? Integer.MAX_VALUE : caveCeiling[index];
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
}
