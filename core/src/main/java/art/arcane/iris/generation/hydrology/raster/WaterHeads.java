package art.arcane.iris.generation.hydrology.raster;

public final class WaterHeads {
    private static final int STEP = 2;

    private WaterHeads() {
    }

    public static int snap(int planned, int seaLevel) {
        return planned - Math.floorMod(planned - seaLevel, STEP);
    }

    static int channelHead(ReachCursor.Choice choice, ReachGeometry geometry, int seaLevel) {
        return snap(choice.reach.grade()[geometry.point(choice)], seaLevel);
    }

    public static int lowestAboveSea(int seaLevel) {
        return seaLevel + STEP;
    }
}
