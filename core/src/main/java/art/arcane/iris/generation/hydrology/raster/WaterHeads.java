package art.arcane.iris.generation.hydrology.raster;

public final class WaterHeads {
    private WaterHeads() {
    }

    public static int snap(int planned, int seaLevel) {
        return planned - Math.floorMod(planned - seaLevel, 2);
    }
}
