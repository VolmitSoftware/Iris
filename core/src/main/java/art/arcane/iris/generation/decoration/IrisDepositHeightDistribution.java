package art.arcane.iris.generation.decoration;

import art.arcane.volmlib.util.documentation.Description;

@Description("Controls how a deposit chooses its origin height within the configured vertical band.")
public enum IrisDepositHeightDistribution {
    @Description("Samples uniformly after clipping the configured band to the terrain and build-height bounds.")
    CLIPPED_UNIFORM,
    @Description("Samples uniformly from the configured band, then rejects origins outside the terrain or build-height bounds.")
    UNIFORM,
    @Description("Samples toward the middle of the configured band with a triangular distribution, then rejects invalid origins.")
    TRIANGLE
}
