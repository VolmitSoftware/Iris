package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.world.history.SavedBiomeRuntime;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Answers which biomes an earlier pack version saved for the chunks it generated.
 */
public interface SavedBiomes {
    Optional<BiomeEnvironment> at(int blockX, int worldY, int blockZ, boolean surface);

    Optional<BiomeEnvironment> caveBaseAt(int blockX, int blockZ);

    Optional<SubterrainCell> subterrainCellAt(int blockX, int worldY, int blockZ);

    <T> T readSurface(int blockX, int blockZ, Function<Optional<BiomeEnvironment>, T> reader)
            throws InterruptedException;

    CompletableFuture<Optional<SavedBiomeRuntime.ReadSession>> readChunk(int chunkX, int chunkZ);

    void prepareChunk(int chunkX, int chunkZ);

    boolean mayHaveBlockDropRules(String material);
}
