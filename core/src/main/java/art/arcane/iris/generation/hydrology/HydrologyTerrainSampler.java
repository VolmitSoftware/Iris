package art.arcane.iris.generation.hydrology;

@FunctionalInterface
public interface HydrologyTerrainSampler {
    HydrologyTerrainSample sample(int blockX, int blockZ);

    default boolean receivingWater(int blockX, int blockZ, int seaLevel) {
        HydrologyTerrainSample terrain = sample(blockX, blockZ);
        return terrain != null && terrain.ocean() && terrain.naturalHeight() < seaLevel;
    }
}
