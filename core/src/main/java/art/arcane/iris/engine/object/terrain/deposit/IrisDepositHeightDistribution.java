package art.arcane.iris.engine.object.terrain.deposit;

import art.arcane.iris.engine.object.annotations.Desc;

@Desc("Controls how a deposit chooses its origin height within the configured vertical band.")
public enum IrisDepositHeightDistribution {
    @Desc("Samples uniformly after clipping the configured band to the terrain and build-height bounds.")
    CLIPPED_UNIFORM,
    @Desc("Samples uniformly from the configured band, then rejects origins outside the terrain or build-height bounds.")
    UNIFORM,
    @Desc("Samples toward the middle of the configured band with a triangular distribution, then rejects invalid origins.")
    TRIANGLE
}
