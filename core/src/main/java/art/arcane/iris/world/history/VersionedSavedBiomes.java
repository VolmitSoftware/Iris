package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.BiomeEnvironment;
import art.arcane.iris.generation.runtime.SavedBiomes;
import art.arcane.iris.generation.subterrain.SubterrainCell;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

final class VersionedSavedBiomes implements SavedBiomes {
    private final AttachedRouter router;

    VersionedSavedBiomes(AttachedRouter router) {
        this.router = router;
    }

    @Override
    public Optional<BiomeEnvironment> at(int blockX, int worldY, int blockZ, boolean surface) {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        return attached == null ? Optional.empty() : attached.biomes().resolve(blockX, worldY, blockZ, surface);
    }

    @Override
    public Optional<BiomeEnvironment> caveBaseAt(int blockX, int blockZ) {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        return attached == null ? Optional.empty() : attached.biomes().resolveCaveBase(blockX, blockZ);
    }

    @Override
    public Optional<SubterrainCell> subterrainCellAt(int blockX, int worldY, int blockZ) {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        return attached == null ? Optional.empty() : attached.biomes().subterrainCell(blockX, worldY, blockZ);
    }

    @Override
    public <T> T readSurface(int blockX, int blockZ, Function<Optional<BiomeEnvironment>, T> reader)
            throws InterruptedException {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        return attached == null ? reader.apply(Optional.empty())
                : attached.biomes().readSurfaceBiome(blockX, blockZ, reader);
    }

    @Override
    public CompletableFuture<Optional<SavedBiomeRuntime.ReadSession>> readChunk(int chunkX, int chunkZ) {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        if (attached == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return attached.biomes().readChunkAsync(chunkX, chunkZ).thenApply(Optional::of);
    }

    @Override
    public void prepareChunk(int chunkX, int chunkZ) {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        if (attached != null) {
            attached.biomes().prepareChunk(chunkX, chunkZ);
        }
    }

    @Override
    public boolean mayHaveBlockDropRules(String material) {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        return attached != null && attached.biomes().mayHaveBlockDropRules(material);
    }
}
