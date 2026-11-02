package art.arcane.iris.generation.hydrology.raster;

public final class WaterHeads {
    private static final int STEP = 2;

    private WaterHeads() {
    }

    public static int snap(int planned, int seaLevel) {
        return planned - Math.floorMod(planned - seaLevel, STEP);
    }

    public static int lowestAboveSea(int seaLevel) {
        return seaLevel + STEP;
    }
}
