package art.arcane.iris.core.nms.v26_2_R1;

import art.arcane.iris.engine.history.NativeTerrainReceipt;
import art.arcane.iris.engine.history.SavedTerrainChunk;
import art.arcane.iris.util.common.scheduling.J;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

final class NmsSavedTerrainCapture {
    private static final long WRITE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30L);

    private NmsSavedTerrainCapture() {
    }

    static CompletableFuture<SavedTerrainChunk> capture(World world, int chunkX, int chunkZ, int minimumY, int height) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        CompletableFuture<CompoundTag> snapshot = new CompletableFuture<>();
        J.runRegionFuture(world, chunkX, chunkZ, () -> {
            try {
                NewChunkHolder holder = level.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkX, chunkZ);
                ChunkAccess chunk = holder == null ? null : holder.getCurrentChunk();
                if (chunk == null) {
                    snapshot.complete(null);
                    return;
                }
                String status = BuiltInRegistries.CHUNK_STATUS.getKey(chunk.getPersistedStatus()).toString();
                if (!SavedTerrainChunk.hasTerrain(status)) {
                    throw new IOException("Native chunk " + chunkX + "," + chunkZ + " has no completed terrain at " + status);
                }
                snapshot.complete(saveSnapshot(level, chunk));
            } catch (Throwable failure) {
                snapshot.completeExceptionally(failure);
            }
        }).whenComplete((ignored, failure) -> {
            if (failure != null) {
                snapshot.completeExceptionally(failure);
            }
        });
        return snapshot.thenApplyAsync(data -> {
            try {
                Checkpoint checkpoint = checkpoint(chunkX, chunkZ, data);
                awaitWrite(level, checkpoint.chunk(), System.nanoTime() + WRITE_TIMEOUT_NANOS);
                if (data == null) {
                    return SavedTerrainChunk.read(world.getWorldFolder().toPath(), chunkX, chunkZ, minimumY, height);
                }
                verifyCheckpoint(world.getWorldFolder().toPath(), checkpoint);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (DataOutputStream output = new DataOutputStream(bytes)) {
                    NbtIo.write(data, output);
                }
                return SavedTerrainChunk.readNbt(bytes.toByteArray(), chunkX, chunkZ, minimumY, height);
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        });
    }

    static CompletableFuture<Void> flush(World world) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        List<NewChunkHolder> holders = level.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolders();
        List<CompletableFuture<Checkpoint>> saves = new ArrayList<>(holders.size());
        for (NewChunkHolder holder : holders) {
            CompletableFuture<Checkpoint> saved = new CompletableFuture<>();
            saves.add(saved);
            J.runRegionFuture(world, holder.chunkX, holder.chunkZ, () -> {
                try {
                    ChunkAccess chunk = holder.getCurrentChunk();
                    saved.complete(checkpoint(holder.chunkX, holder.chunkZ,
                            chunk != null && chunk.isUnsaved() ? saveSnapshot(level, chunk) : null));
                } catch (Throwable failure) {
                    saved.completeExceptionally(failure);
                }
            }).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    saved.completeExceptionally(failure);
                }
            });
        }
        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).thenRunAsync(() -> {
            try {
                List<Checkpoint> checkpoints = new ArrayList<>(saves.size());
                for (CompletableFuture<Checkpoint> future : saves) {
                    checkpoints.add(future.join());
                }
                finishCheckpoint(level, world.getWorldFolder().toPath(), checkpoints);
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        });
    }

    static void finishCheckpoint(ServerLevel level, Path worldFolder, List<Checkpoint> checkpoints) throws IOException {
        long deadline = System.nanoTime() + WRITE_TIMEOUT_NANOS;
        for (Checkpoint checkpoint : checkpoints) {
            awaitWrite(level, checkpoint.chunk(), deadline);
        }
        MoonriseRegionFileIO.flushRegionStorages(level, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA);
        for (Checkpoint checkpoint : checkpoints) {
            verifyCheckpoint(worldFolder, checkpoint);
        }
    }

    static Checkpoint checkpoint(int chunkX, int chunkZ, CompoundTag data) {
        if (data == null) {
            return new Checkpoint(new ChunkPos(chunkX, chunkZ), null);
        }
        byte[] receipt = data.getCompound("ChunkBukkitValues")
                .flatMap(values -> values.getByteArray(NativeTerrainReceipt.NBT_KEY)).orElse(null);
        long structureActivation = data.getCompound("ChunkBukkitValues").map(values -> values.getLongOr(
                NativeTerrainReceipt.STRUCTURE_ACTIVATION_KEY, 0)).orElse(0L);
        return new Checkpoint(new ChunkPos(chunkX, chunkZ), new Verification(
                data.getStringOr("Status", ""), receipt, structureActivation));
    }

    private static void verifyCheckpoint(Path worldFolder, Checkpoint checkpoint) throws IOException {
        Verification expected = checkpoint.verification();
        if (expected == null) {
            return;
        }
        SavedTerrainChunk.verifyCheckpoint(worldFolder, checkpoint.chunk().x(), checkpoint.chunk().z(),
                expected.status(), expected.receipt(), expected.structureActivation());
    }

    static void awaitWrite(ServerLevel level, ChunkPos chunk, long deadlineNanos) throws IOException {
        ChunkTaskScheduler scheduler = level.moonrise$getChunkTaskScheduler();
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Interrupted while saving terrain checkpoint chunk " + chunk);
            }
            if (System.nanoTime() - deadlineNanos >= 0L) {
                throw new IOException("Timed out saving terrain checkpoint chunk " + chunk);
            }
            if (MoonriseRegionFileIO.getPriority(level, chunk.x(), chunk.z(),
                    MoonriseRegionFileIO.RegionFileType.CHUNK_DATA) == Priority.COMPLETING) {
                return;
            }
            boolean helped = scheduler.saveExecutor.executeTask();
            helped |= scheduler.compressionExecutor.executeTask();
            if (!helped) {
                LockSupport.parkNanos(1_000_000L);
            }
        }
    }

    private static CompoundTag saveSnapshot(ServerLevel level, ChunkAccess chunk) {
        SerializableChunkData snapshot = SerializableChunkData.copyOf(level, chunk);
        PlatformHooks.get().chunkSyncSave(level, chunk, snapshot);
        CompoundTag data = snapshot.write();
        MoonriseRegionFileIO.scheduleSave(level, chunk.getPos().x(), chunk.getPos().z(), data,
                MoonriseRegionFileIO.RegionFileType.CHUNK_DATA);
        return data;
    }

    record Checkpoint(ChunkPos chunk, Verification verification) {
    }

    record Verification(String status, byte[] receipt, long structureActivation) {
    }
}
