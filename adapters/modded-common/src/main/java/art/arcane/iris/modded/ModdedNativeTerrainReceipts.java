package art.arcane.iris.modded;

import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.NativeTerrainReceipt;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;

import java.io.IOException;

public final class ModdedNativeTerrainReceipts {
    private ModdedNativeTerrainReceipts() {
    }

    public static void persist(ChunkAccess chunk, GenerationHistoryRuntimeRouter.RuntimeRoute route) throws IOException {
        if (route == null || route.naturalTerrain().isEmpty()) {
            return;
        }
        set(chunk, NativeTerrainReceipt.encode(route.naturalTerrain().orElseThrow(),
                route.activation().activationId(), route.epoch().epochId()));
        chunk.markUnsaved();
    }

    public static long structureActivation(ChunkAccess chunk) {
        return holder(chunk).iris$getStructureActivation();
    }

    public static void setStructureActivation(ChunkAccess chunk, long activation) {
        holder(chunk).iris$setStructureActivation(activation);
    }

    public static void persistStructureActivation(ChunkAccess chunk, GenerationHistoryRuntimeRouter.RuntimeRoute route) {
        if (route != null) {
            setStructureActivation(chunk, route.activation().activationId());
            chunk.markUnsaved();
        }
    }

    public static byte[] get(ChunkAccess chunk) {
        return holder(chunk).iris$getNaturalTerrain();
    }

    public static void set(ChunkAccess chunk, byte[] receipt) {
        holder(chunk).iris$setNaturalTerrain(receipt);
    }

    private static NativeTerrainReceiptHolder holder(ChunkAccess chunk) {
        ChunkAccess target = chunk instanceof ImposterProtoChunk imposter ? imposter.getWrapped() : chunk;
        if (!(target instanceof NativeTerrainReceiptHolder holder)) {
            throw new IllegalStateException("Native terrain receipt storage is unavailable for " + chunk.getPos());
        }
        return holder;
    }
}
