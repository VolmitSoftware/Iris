package art.arcane.iris.world.lifecycle;

import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

public interface WorldLifecycleBackend {
    boolean supports(WorldLifecycleRequest request, CapabilitySnapshot capabilities);

    CompletableFuture<World> create(WorldLifecycleRequest request);

    CompletableFuture<Boolean> unloadAsync(World world, boolean save);

    String backendName();
}
