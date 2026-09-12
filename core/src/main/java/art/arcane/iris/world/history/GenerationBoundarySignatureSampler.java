package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.IrisEngine;

import java.io.IOException;

@FunctionalInterface
public interface GenerationBoundarySignatureSampler {
    TerrainBoundarySignature sample(IrisEngine engine, int blockX, int blockZ) throws IOException;

    default TerrainBoundarySignatureStore.SignatureSampler open(IrisEngine engine) throws IOException {
        return (blockX, blockZ) -> sample(engine, blockX, blockZ);
    }
}
