package art.arcane.iris.generation.bake;

import art.arcane.iris.generation.terrain.Terrain3DColumn;

public final class ColumnBake {
    public static final int APRON_WIDTH = 18;

    public final int chunkX;
    public final int chunkZ;
    public final short[] regionId;
    public final short[] baseBiomeId;
    public final short[] trueBiomeId;
    public final short[] caveBiomeId;
    public final double[] baseHeight;
    public final double[] naturalHeight;
    public final double[] height;
    public final int[] roundedHeight;
    public final float[] fluidSurface;
    public final short[] rockState;
    public final short[] fluidState;
    public final Terrain3DColumn[] terrainColumns;
    public final double[] apronHeight;

    public ColumnBake(Planes planes) {
        this.chunkX = planes.chunkX();
        this.chunkZ = planes.chunkZ();
        this.regionId = planes.regionId();
        this.baseBiomeId = planes.baseBiomeId();
        this.trueBiomeId = planes.trueBiomeId();
        this.caveBiomeId = planes.caveBiomeId();
        this.baseHeight = planes.baseHeight();
        this.naturalHeight = planes.naturalHeight();
        this.height = planes.height();
        this.roundedHeight = planes.roundedHeight();
        this.fluidSurface = planes.fluidSurface();
        this.rockState = planes.rockState();
        this.fluidState = planes.fluidState();
        this.terrainColumns = planes.terrainColumns();
        this.apronHeight = planes.apronHeight();
    }

    public static int index(int localX, int localZ) {
        return (localZ << 4) | localX;
    }

    public static int apronIndex(int localX, int localZ) {
        return (localZ + 1) * APRON_WIDTH + localX + 1;
    }

    public record Planes(int chunkX, int chunkZ, short[] regionId, short[] baseBiomeId, short[] trueBiomeId,
                         short[] caveBiomeId, double[] baseHeight, double[] naturalHeight, double[] height,
                         int[] roundedHeight, float[] fluidSurface, short[] rockState, short[] fluidState,
                         Terrain3DColumn[] terrainColumns, double[] apronHeight) {
    }
}
