package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.IrisEngine;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class IrisBoundarySignatureSampler implements GenerationBoundarySignatureSampler {
    public static final IrisBoundarySignatureSampler INSTANCE = new IrisBoundarySignatureSampler();
    private static final int MAXIMUM_CACHED_CHUNKS = 32;

    private IrisBoundarySignatureSampler() {
    }

    public static void checkpoint(IrisEngine engine) throws IOException {
        if (engine.getPlatformHooks().isMainThread()) {
            throw new IOException("Terrain save checkpoints must run outside the platform tick thread.");
        }
        try {
            engine.getPlatformHooks().flushSavedTerrainCapture(engine).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while saving the terrain boundary.", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IOException("Unable to save the terrain boundary.", failure);
        }
    }

    @Override
    public TerrainBoundarySignature sample(IrisEngine engine, int blockX, int blockZ) throws IOException {
        try (TerrainBoundarySignatureStore.SignatureSampler capture = open(engine)) {
            return capture.sample(blockX, blockZ);
        }
    }

    @Override
    public TerrainBoundarySignatureStore.SignatureSampler open(IrisEngine engine) throws IOException {
        if (engine.getPlatformHooks().isMainThread()) {
            throw new IOException("Terrain boundary capture must run outside the platform tick thread.");
        }
        return new LiveCapture(engine);
    }

    private static final class LiveCapture implements TerrainBoundarySignatureStore.SignatureSampler {
        private final IrisEngine engine;
        private final LinkedHashMap<Long, CompletableFuture<SavedTerrainChunk>> chunks = new LinkedHashMap<>(32, 0.75F, true);

        private LiveCapture(IrisEngine engine) {
            this.engine = engine;
        }

        @Override
        public void prepareChunks(List<GenerationBoundary.ChunkCoordinate> coordinates) {
            if (coordinates.size() > MAXIMUM_CACHED_CHUNKS) {
                throw new IllegalArgumentException("Terrain capture batches cannot exceed 32 chunks.");
            }
            try {
                for (GenerationBoundary.ChunkCoordinate coordinate : coordinates) {
                    captureChunk(coordinate.chunkX(), coordinate.chunkZ());
                }
            } catch (RuntimeException | Error failure) {
                cancelPendingCaptures();
                throw failure;
            }
        }

        @Override
        public TerrainBoundarySignature sample(int blockX, int blockZ) throws IOException {
            int chunkX = Math.floorDiv(blockX, 16);
            int chunkZ = Math.floorDiv(blockZ, 16);
            try {
                return captureChunk(chunkX, chunkZ).get(30, TimeUnit.SECONDS).column(blockX, blockZ);
            } catch (InterruptedException failure) {
                cancelPendingCaptures();
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while capturing terrain at " + chunkX + "," + chunkZ, failure);
            } catch (ExecutionException | TimeoutException | CancellationException failure) {
                cancelPendingCaptures();
                throw new IOException("Unable to capture terrain at " + chunkX + "," + chunkZ, failure);
            }
        }

        private CompletableFuture<SavedTerrainChunk> captureChunk(int chunkX, int chunkZ) {
            long key = ChunkGenerationOwnership.packChunk(chunkX, chunkZ);
            CompletableFuture<SavedTerrainChunk> terrain = chunks.get(key);
            if (terrain != null) {
                return terrain;
            }
            terrain = Objects.requireNonNull(engine.getPlatformHooks().captureSavedTerrainChunk(engine, chunkX, chunkZ),
                    "Terrain capture future");
            chunks.put(key, terrain);
            if (chunks.size() > MAXIMUM_CACHED_CHUNKS) {
                Map.Entry<Long, CompletableFuture<SavedTerrainChunk>> oldest = chunks.firstEntry();
                chunks.remove(oldest.getKey());
                oldest.getValue().cancel(false);
            }
            return terrain;
        }

        private void cancelPendingCaptures() {
            for (CompletableFuture<SavedTerrainChunk> capture : chunks.values()) {
                capture.cancel(false);
            }
        }

        @Override
        public void close() throws IOException {
            cancelPendingCaptures();
            try {
                checkpoint(engine);
            } finally {
                chunks.clear();
            }
        }
    }
}
