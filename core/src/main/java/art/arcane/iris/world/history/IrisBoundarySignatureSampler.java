package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.IrisEngine;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
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
        private final LinkedHashMap<Long, SavedTerrainChunk> chunks = new LinkedHashMap<>(32, 0.75F, true);

        private LiveCapture(IrisEngine engine) {
            this.engine = engine;
        }

        @Override
        public TerrainBoundarySignature sample(int blockX, int blockZ) throws IOException {
            int chunkX = Math.floorDiv(blockX, 16);
            int chunkZ = Math.floorDiv(blockZ, 16);
            long key = ChunkGenerationOwnership.packChunk(chunkX, chunkZ);
            SavedTerrainChunk terrain = chunks.get(key);
            if (terrain == null) {
                terrain = captureChunk(chunkX, chunkZ);
                chunks.put(key, terrain);
                if (chunks.size() > MAXIMUM_CACHED_CHUNKS) {
                    Map.Entry<Long, SavedTerrainChunk> oldest = chunks.firstEntry();
                    chunks.remove(oldest.getKey());
                }
            }
            return terrain.column(blockX, blockZ);
        }

        private SavedTerrainChunk captureChunk(int chunkX, int chunkZ) throws IOException {
            try {
                return engine.getPlatformHooks().captureSavedTerrainChunk(engine, chunkX, chunkZ)
                        .get(30, TimeUnit.SECONDS);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while capturing terrain at " + chunkX + "," + chunkZ, failure);
            } catch (ExecutionException | TimeoutException failure) {
                throw new IOException("Unable to capture terrain at " + chunkX + "," + chunkZ, failure);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                checkpoint(engine);
            } finally {
                chunks.clear();
            }
        }
    }
}
