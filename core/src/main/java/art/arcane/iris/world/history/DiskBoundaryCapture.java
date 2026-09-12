package art.arcane.iris.world.history;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class DiskBoundaryCapture implements TerrainBoundarySignatureStore.SignatureSampler {
    private static final int MAXIMUM_CACHED_CHUNKS = 32;

    private final Path dimensionRoot;
    private final int minimumY;
    private final int height;
    private final LinkedHashMap<Long, SavedTerrainChunk> chunks = new LinkedHashMap<>(32, 0.75F, true);

    public DiskBoundaryCapture(Path dimensionRoot, int minimumY, int height) {
        this.dimensionRoot = Objects.requireNonNull(dimensionRoot, "dimension root");
        this.minimumY = minimumY;
        this.height = height;
    }

    @Override
    public TerrainBoundarySignature sample(int blockX, int blockZ) throws IOException {
        int chunkX = Math.floorDiv(blockX, 16);
        int chunkZ = Math.floorDiv(blockZ, 16);
        long key = ChunkGenerationOwnership.packChunk(chunkX, chunkZ);
        SavedTerrainChunk chunk = chunks.get(key);
        if (chunk == null) {
            chunk = SavedTerrainChunk.read(dimensionRoot, chunkX, chunkZ, minimumY, height);
            chunks.put(key, chunk);
            if (chunks.size() > MAXIMUM_CACHED_CHUNKS) {
                Map.Entry<Long, SavedTerrainChunk> oldest = chunks.firstEntry();
                chunks.remove(oldest.getKey());
            }
        }
        return chunk.column(blockX, blockZ);
    }

    @Override
    public void close() {
        chunks.clear();
    }
}
