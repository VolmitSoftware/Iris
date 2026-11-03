package art.arcane.iris.generation.hydrology.raster;

public final class WaterHeads {
    public static final int STANDING_STEP = 2;

    private WaterHeads() {
    }

    public static int snap(int planned, int seaLevel, int stepHeight) {
        return planned - Math.floorMod(planned - seaLevel, stepHeight);
    }

    public static int standing(int planned, int seaLevel) {
        return snap(planned, seaLevel, STANDING_STEP);
    }

    static int channelHead(ReachCursor.Choice choice, ReachGeometry geometry, int seaLevel) {
        return snap(choice.reach.grade()[geometry.point(choice)], seaLevel, choice.reach.tier().stepHeight());
    }

    public static int lowestAboveSea(int seaLevel, int stepHeight) {
        return seaLevel + stepHeight;
    }
}
