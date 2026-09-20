package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.world.history.SavedTerrainCapturePolicy;
import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.nativelib.terrain.SnapshotPolicy;

import java.util.concurrent.CompletableFuture;

public final class SavedTerrainSnapshotPolicy extends SavedTerrainCapturePolicy implements SnapshotPolicy<SavedTerrainChunk> {
    public static final SavedTerrainSnapshotPolicy INSTANCE = new SavedTerrainSnapshotPolicy();

    private SavedTerrainSnapshotPolicy() {
    }

    @Override
    public CompletableFuture<Void> runRegion(Region region, Runnable action) {
        return J.runRegionFuture(region.world(), region.chunkX(), region.chunkZ(), action);
    }
}
