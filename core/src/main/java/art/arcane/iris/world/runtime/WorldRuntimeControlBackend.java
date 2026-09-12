package art.arcane.iris.world.runtime;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

interface WorldRuntimeControlBackend {
    String backendName();

    String describeCapabilities();

    OptionalLong readDayTime(World world);

    boolean writeDayTime(World world, long dayTime) throws ReflectiveOperationException;

    void syncTime(World world);

    CompletableFuture<Chunk> requestChunkAsync(World world, int chunkX, int chunkZ, boolean generate);

    default CompletableFuture<Chunk> requestChunkAsync(
            World world,
            int chunkX,
            int chunkZ,
            boolean generate,
            boolean urgent
    ) {
        if (!urgent) {
            return requestChunkAsync(world, chunkX, chunkZ, generate);
        }
        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("World is null."));
        }
        try {
            Method method = World.class.getMethod(
                    "getChunkAtAsync",
                    int.class,
                    int.class,
                    boolean.class,
                    boolean.class);
            Object result = method.invoke(world, chunkX, chunkZ, generate, true);
            if (result instanceof CompletableFuture<?> future) {
                @SuppressWarnings("unchecked")
                CompletableFuture<Chunk> chunkFuture = (CompletableFuture<Chunk>) future;
                return chunkFuture;
            }
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Paper World#getChunkAtAsync returned a non-future result."));
        } catch (NoSuchMethodException exception) {
            return requestChunkAsync(world, chunkX, chunkZ, generate);
        } catch (Throwable exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
