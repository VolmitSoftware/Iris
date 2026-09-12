package art.arcane.iris.world.lifecycle;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldUnloadBoundaryRegistry {
    private static final ConcurrentHashMap<String, Boundary> ACTIVE = new ConcurrentHashMap<>();

    private WorldUnloadBoundaryRegistry() {
    }

    static Boundary begin(String worldIdentity) {
        String requiredIdentity = Objects.requireNonNull(worldIdentity, "world identity");
        Boundary boundary = new Boundary(requiredIdentity, new CompletableFuture<>());
        Boundary existing = ACTIVE.putIfAbsent(requiredIdentity, boundary);
        if (existing != null) {
            throw new IllegalStateException("World unload is already active for " + requiredIdentity + ".");
        }
        return boundary;
    }

    public static CompletionStage<Boolean> claim(String worldIdentity) {
        Boundary boundary = ACTIVE.remove(Objects.requireNonNull(worldIdentity, "world identity"));
        return boundary == null ? null : boundary.completion();
    }

    static void complete(Boundary boundary, Boolean unloaded, Throwable failure) {
        Objects.requireNonNull(boundary, "world unload boundary");
        ACTIVE.remove(boundary.worldIdentity(), boundary);
        if (failure == null) {
            boundary.completion().complete(Boolean.TRUE.equals(unloaded));
            return;
        }
        boundary.completion().completeExceptionally(WorldLifecycleSupport.unwrap(failure));
    }

    record Boundary(String worldIdentity, CompletableFuture<Boolean> completion) {
    }
}
