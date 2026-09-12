package art.arcane.iris.generation.hydrology;

public interface HydrologyRoutingTerrainSampler {
    HydrologyTerrainSample[] sampleGrid(GridRequest request);

    NaturalClassification classifyNatural(int blockX, int blockZ);

    enum NaturalClassification {
        LAND,
        OCEAN,
        UNAVAILABLE
    }

    record GridRequest(int minimumX, int minimumZ, int width, int spacing) {
        public GridRequest {
            if (width < 1 || spacing < 1) {
                throw new IllegalArgumentException("Hydrology routing grid dimensions must be positive");
            }
        }
    }
}
