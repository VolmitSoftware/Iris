package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.water.Reach;

public record ReachHit(Reach reach, int segment, float t, float distance, Coverage coverage) {
    public enum Coverage {
        CHANNEL,
        BANK,
        OUTSIDE
    }
}
