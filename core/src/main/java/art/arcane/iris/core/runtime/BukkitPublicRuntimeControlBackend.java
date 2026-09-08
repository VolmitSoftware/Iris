package art.arcane.iris.core.runtime;

import art.arcane.iris.core.lifecycle.CapabilitySnapshot;
import art.arcane.iris.spi.CapabilityProbe;
import io.papermc.lib.PaperLib;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

final class BukkitPublicRuntimeControlBackend implements WorldRuntimeControlBackend {
    private final CapabilitySnapshot capabilities;

    BukkitPublicRuntimeControlBackend(CapabilitySnapshot capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public String backendName() {
        return "bukkit_public_runtime";
    }

    @Override
    public String describeCapabilities() {
        String chunkAsync = capabilities.chunkAtAsyncMethod() != null ? "world#getChunkAtAsync" : "paperlib";
        return "time=bukkit_world#setTime, chunkAsync=" + chunkAsync + ", teleport=entity_scheduler";
    }

    @Override
    public OptionalLong readDayTime(World world) {
        if (world == null) {
            return OptionalLong.empty();
        }

        return OptionalLong.of(world.getTime());
    }

    @Override
    public boolean writeDayTime(World world, long dayTime) {
        if (world == null) {
            return false;
        }

        world.setTime(dayTime);
        return true;
    }

    @Override
    public void syncTime(World world) {
    }

    @Override
    public CompletableFuture<Chunk> requestChunkAsync(World world, int chunkX, int chunkZ, boolean generate) {
        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("World is null."));
        }

        if (capabilities.chunkAtAsyncMethod() != null) {
            Object result = CapabilityProbe.attempt("world#getChunkAtAsync",
                    () -> capabilities.chunkAtAsyncMethod().invoke(world, chunkX, chunkZ, generate), null);
            if (result instanceof CompletableFuture<?>) {
                @SuppressWarnings("unchecked")
                CompletableFuture<Chunk> future = (CompletableFuture<Chunk>) result;
                return future;
            }
        }

        CompletableFuture<Chunk> future = PaperLib.getChunkAtAsync(world, chunkX, chunkZ, generate);
        if (future == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("PaperLib did not return a chunk future."));
        }

        return future;
    }
}
