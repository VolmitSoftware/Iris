package art.arcane.iris.generation.hydrology;

@FunctionalInterface
public interface HydrologyTerrainSampler {
    int MAXIMUM_BATCH_SIZE = 4096;

    HydrologyTerrainSample sample(int blockX, int blockZ);

    default HydrologyTerrainSample[] sampleBatch(long[] coordinates, int count) {
        HydrologyTerrainSample[] samples = new HydrologyTerrainSample[count];
        for (int index = 0; index < count; index++) {
            samples[index] = sample(RiverFootprint.unpackX(coordinates[index]), RiverFootprint.unpackZ(coordinates[index]));
        }
        return samples;
    }

    default boolean receivingWater(int blockX, int blockZ, int seaLevel) {
        HydrologyTerrainSample terrain = sample(blockX, blockZ);
        return terrain != null && terrain.ocean() && terrain.naturalHeight() < seaLevel;
    }
}
