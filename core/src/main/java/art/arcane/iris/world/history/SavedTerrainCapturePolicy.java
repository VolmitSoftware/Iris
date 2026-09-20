package art.arcane.iris.world.history;

import art.arcane.volmlib.nativelib.terrain.NativeTerrainReceiptStorage;

import art.arcane.volmlib.nativelib.terrain.TerrainSnapshotPolicy;

import java.io.IOException;
import java.nio.file.Path;

public class SavedTerrainCapturePolicy implements TerrainSnapshotPolicy<SavedTerrainChunk> {
    public static final SavedTerrainCapturePolicy INSTANCE = new SavedTerrainCapturePolicy();

    protected SavedTerrainCapturePolicy() {
    }

    @Override
    public boolean hasTerrain(String status) {
        return SavedTerrainChunk.hasTerrain(status);
    }

    @Override
    public String receiptKey() {
        return NativeTerrainReceiptStorage.NBT_KEY;
    }

    @Override
    public String activationKey() {
        return NativeTerrainReceiptStorage.STRUCTURE_ACTIVATION_KEY;
    }

    @Override
    public SavedTerrainChunk readPersisted(CaptureTarget target) throws IOException {
        return SavedTerrainChunk.read(target.worldFolder(), target.chunkX(), target.chunkZ(),
                target.minimumY(), target.height());
    }

    @Override
    public SavedTerrainChunk decodeNbt(byte[] bytes, CaptureTarget target) throws IOException {
        return SavedTerrainChunk.readNbt(bytes, target.chunkX(), target.chunkZ(), target.minimumY(), target.height());
    }

    @Override
    public void verifyCheckpoint(Path worldFolder, CheckpointData checkpoint) throws IOException {
        SavedTerrainChunk.verifyCheckpoint(worldFolder, checkpoint.chunkX(), checkpoint.chunkZ(),
                checkpoint.status(), checkpoint.receipt(), checkpoint.activation());
    }
}
