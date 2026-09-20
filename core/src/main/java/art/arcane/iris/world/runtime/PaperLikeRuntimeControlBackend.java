package art.arcane.iris.world.runtime;

import art.arcane.iris.world.lifecycle.CapabilitySnapshot;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.nativelib.terrain.NativeWorldClock;
import io.papermc.lib.PaperLib;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

final class PaperLikeRuntimeControlBackend implements WorldRuntimeControlBackend {
    private final CapabilitySnapshot capabilities;
    private final NativeWorldClock clock;

    PaperLikeRuntimeControlBackend(CapabilitySnapshot capabilities) {
        this.capabilities = capabilities;
        clock = capabilities.nativeRuntime() == null ? null : capabilities.nativeRuntime().clock();
    }

    public String backendName() {
        return "paper_like_runtime";
    }

    public String describeCapabilities() {
        String chunkAsync = capabilities.chunkAtAsyncMethod() != null ? "world#getChunkAtAsync" : "paperlib";
        return "time=" + (clock == null ? "unsupported" : clock.description())
                + ", chunkAsync=" + chunkAsync + ", teleport=entity_scheduler";
    }

    public OptionalLong readDayTime(World world) {
        if (clock == null || world == null) {
            return OptionalLong.empty();
        }
        try {
            return clock.readDayTime(world);
        } catch (Throwable failure) {
            IrisLogging.reportError("Failed to read the runtime day time of world \"" + world.getName()
                    + "\" through " + clock.description() + ".", failure);
            return OptionalLong.empty();
        }
    }

    public boolean writeDayTime(World world, long dayTime) throws ReflectiveOperationException {
        return clock != null && clock.writeDayTime(world, dayTime);
    }

    public void syncTime(World world) {
        if (clock == null) {
            return;
        }
        try {
            clock.syncTime(world);
        } catch (Throwable failure) {
            IrisLogging.reportError("Failed to push the runtime day time of world \""
                    + (world == null ? "unknown" : world.getName()) + "\" to its players.", failure);
        }
    }

    @Override
    public CompletableFuture<Chunk> requestChunkAsync(World world, int chunkX, int chunkZ, boolean generate) {
        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("World is null."));
        }

        if (capabilities.chunkAtAsyncMethod() != null) {
            try {
                Object result = capabilities.chunkAtAsyncMethod().invoke(world, chunkX, chunkZ, generate);
                if (result instanceof CompletableFuture<?>) {
                    @SuppressWarnings("unchecked")
                    CompletableFuture<Chunk> future = (CompletableFuture<Chunk>) result;
                    return future;
                }
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        CompletableFuture<Chunk> future = PaperLib.getChunkAtAsync(world, chunkX, chunkZ, generate);
        if (future == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("PaperLib did not return a chunk future."));
        }

        return future;
    }

}
