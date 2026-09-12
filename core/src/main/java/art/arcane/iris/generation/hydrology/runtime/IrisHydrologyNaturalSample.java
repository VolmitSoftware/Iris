package art.arcane.iris.generation.hydrology.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;

public record IrisHydrologyNaturalSample(
        double naturalHeight,
        boolean ocean,
        IrisBiome biome,
        IrisRegion region
) {
}
