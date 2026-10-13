package art.arcane.iris.generation.hydrology.runtime;

import art.arcane.iris.generation.hydrology.RiverFootprint;

@FunctionalInterface
public interface IrisHydrologyNaturalHeightProvider {
    double sample(int blockX, int blockZ);

    default boolean supportsBatch() {
        return false;
    }

    default void sampleBatch(long[] coordinates, int count, double[] heights) {
        for (int index = 0; index < count; index++) {
            heights[index] = sample(RiverFootprint.unpackX(coordinates[index]), RiverFootprint.unpackZ(coordinates[index]));
        }
    }
}
