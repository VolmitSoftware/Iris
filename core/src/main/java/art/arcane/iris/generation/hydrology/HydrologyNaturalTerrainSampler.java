package art.arcane.iris.generation.hydrology;

public interface HydrologyNaturalTerrainSampler extends HydrologyRoutingTerrainSampler {
    HydrologyTerrainSample sampleBasis(int blockX, int blockZ);

    default HydrologyTerrainSample sampleBasisWithoutSlope(int blockX, int blockZ) {
        return sampleBasis(blockX, blockZ);
    }

    default double sampleLandHeight(int blockX, int blockZ) {
        HydrologyTerrainSample terrain = sampleBasisWithoutSlope(blockX, blockZ);
        return terrain == null || terrain.ocean() ? Double.NaN : terrain.naturalHeight();
    }

    default HydrologyTerrainSample[] sampleBasisWithoutSlopeBatch(long[] coordinates, int count) {
        HydrologyTerrainSample[] samples = new HydrologyTerrainSample[count];
        for (int index = 0; index < count; index++) {
            samples[index] = sampleBasisWithoutSlope(RiverFootprint.unpackX(coordinates[index]), RiverFootprint.unpackZ(coordinates[index]));
        }
        return samples;
    }
}
