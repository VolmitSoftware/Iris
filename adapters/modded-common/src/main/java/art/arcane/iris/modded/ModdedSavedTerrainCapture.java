package art.arcane.iris.modded;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.iris.world.history.SavedTerrainCapturePolicy;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedTerrainSnapshots;
import art.arcane.volmlib.nativelib.terrain.TerrainSnapshotPolicy;

import java.util.concurrent.CompletableFuture;

public final class ModdedSavedTerrainCapture {
    private ModdedSavedTerrainCapture() {
    }

    public static CompletableFuture<SavedTerrainChunk> capture(Engine engine, int chunkX, int chunkZ) {
        TerrainSnapshotPolicy.CaptureTarget target = new TerrainSnapshotPolicy.CaptureTarget(
                engine.getWorld().worldFolder().toPath(), chunkX, chunkZ, engine.getMinHeight(), engine.getHeight());
        return ModdedTerrainSnapshots.forWorld(engine.getWorld().platformWorld())
                .capture(target, SavedTerrainCapturePolicy.INSTANCE);
    }

    public static CompletableFuture<Void> flush(Engine engine) {
        return ModdedTerrainSnapshots.forWorld(engine.getWorld().platformWorld())
                .flush(engine.getWorld().worldFolder().toPath(), SavedTerrainCapturePolicy.INSTANCE);
    }
}
