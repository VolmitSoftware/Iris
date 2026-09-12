package art.arcane.iris.modded;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.iris.modded.mixin.ChunkMapTerrainReceiptAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class ModdedSavedTerrainCapture {
    private ModdedSavedTerrainCapture() {
    }

    public static CompletableFuture<SavedTerrainChunk> capture(Engine engine, int chunkX, int chunkZ) {
        ServerLevel level = requireLevel(engine);
        CompletableFuture<CompoundTag> snapshot = new CompletableFuture<>();
        runOwned(level, () -> {
            try {
                ChunkMap map = level.getChunkSource().chunkMap;
                ChunkHolder holder = map.getUpdatingChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
                ChunkAccess chunk = holder == null ? null : holder.getLatestChunk();
                if (chunk == null) {
                    map.read(new ChunkPos(chunkX, chunkZ)).whenComplete((data, failure) -> {
                        if (failure != null) {
                            snapshot.completeExceptionally(failure);
                        } else {
                            snapshot.complete(data.orElse(null));
                        }
                    });
                    return;
                }
                String status = BuiltInRegistries.CHUNK_STATUS.getKey(chunk.getPersistedStatus()).toString();
                if (!SavedTerrainChunk.hasTerrain(status)) {
                    throw new IOException("Native chunk " + chunkX + "," + chunkZ + " has no terrain at " + status);
                }
                CompoundTag data = SerializableChunkData.copyOf(level, chunk).write();
                if (chunk.isUnsaved()) {
                    ((ChunkMapTerrainReceiptAccess) map).iris$saveTerrainCheckpoint(chunk);
                }
                snapshot.complete(data);
            } catch (Throwable failure) {
                snapshot.completeExceptionally(failure);
            }
        });
        return snapshot.thenApplyAsync(data -> {
            try {
                if (data == null) {
                    throw new IOException("Saved native chunk is unavailable: " + chunkX + "," + chunkZ);
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (DataOutputStream output = new DataOutputStream(bytes)) {
                    NbtIo.write(data, output);
                }
                return SavedTerrainChunk.readNbt(bytes.toByteArray(), chunkX, chunkZ, engine.getMinHeight(), engine.getHeight());
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        });
    }

    public static CompletableFuture<Void> flush(Engine engine) {
        ServerLevel level = requireLevel(engine);
        CompletableFuture<Void> complete = new CompletableFuture<>();
        runOwned(level, () -> {
            try {
                ChunkMap map = level.getChunkSource().chunkMap;
                List<Checkpoint> checkpoints = new ArrayList<>();
                for (ChunkHolder holder : ((ChunkMapTerrainReceiptAccess) map).iris$updatingChunks().values()) {
                    ChunkAccess chunk = holder.getLatestChunk();
                    if (chunk == null) {
                        continue;
                    }
                    String status = BuiltInRegistries.CHUNK_STATUS.getKey(chunk.getPersistedStatus()).toString();
                    if (!SavedTerrainChunk.hasTerrain(status)
                            && ModdedNativeTerrainReceipts.structureActivation(chunk) == 0) {
                        continue;
                    }
                    checkpoints.add(new Checkpoint(chunk.getPos().x(), chunk.getPos().z(), status,
                            ModdedNativeTerrainReceipts.get(chunk), ModdedNativeTerrainReceipts.structureActivation(chunk)));
                    if (chunk.isUnsaved()) {
                        ((ChunkMapTerrainReceiptAccess) map).iris$saveTerrainCheckpoint(chunk);
                    }
                }
                map.synchronize(true).thenRunAsync(() -> verifyCheckpoints(engine, checkpoints))
                        .whenComplete((ignored, failure) -> {
                            if (failure != null) {
                                complete.completeExceptionally(failure);
                            } else {
                                complete.complete(null);
                            }
                        });
            } catch (Throwable failure) {
                complete.completeExceptionally(failure);
            }
        });
        return complete;
    }

    private static void verifyCheckpoints(Engine engine, List<Checkpoint> checkpoints) {
        try {
            for (Checkpoint checkpoint : checkpoints) {
                SavedTerrainChunk.verifyCheckpoint(engine.getWorld().worldFolder().toPath(),
                        checkpoint.chunkX(), checkpoint.chunkZ(), checkpoint.status(), checkpoint.receipt(),
                        checkpoint.structureActivation());
            }
        } catch (IOException failure) {
            throw new CompletionException(failure);
        }
    }

    private record Checkpoint(int chunkX, int chunkZ, String status, byte[] receipt, long structureActivation) {
    }

    private static ServerLevel requireLevel(Engine engine) {
        if (!(engine.getWorld().platformWorld().nativeHandle() instanceof ServerLevel level)) {
            throw new IllegalStateException("The level is unavailable for terrain capture.");
        }
        return level;
    }

    private static void runOwned(ServerLevel level, Runnable action) {
        if (level.getServer().isSameThread()) {
            action.run();
        } else {
            level.getServer().execute(action);
        }
    }
}
