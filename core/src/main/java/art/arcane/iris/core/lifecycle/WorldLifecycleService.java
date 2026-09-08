package art.arcane.iris.core.lifecycle;

import art.arcane.iris.core.ServerConfigurator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class WorldLifecycleService {
    private static final long UNLOAD_TIMEOUT_SECONDS = 120L;
    private static volatile WorldLifecycleService instance;

    private final CapabilitySnapshot capabilities;
    private final WorldLifecycleBackend worldsProviderBackend;
    private final WorldLifecycleBackend paperLikeRuntimeBackend;
    private final WorldLifecycleBackend bukkitPublicBackend;
    private final List<WorldLifecycleBackend> backends;
    private final Map<String, String> worldBackendByKey;

    public WorldLifecycleService(CapabilitySnapshot capabilities) {
        this(
                capabilities,
                new WorldsProviderBackend(capabilities),
                new PaperLikeRuntimeBackend(capabilities),
                new BukkitPublicBackend(capabilities)
        );
    }

    WorldLifecycleService(
            CapabilitySnapshot capabilities,
            WorldLifecycleBackend worldsProviderBackend,
            WorldLifecycleBackend paperLikeRuntimeBackend,
            WorldLifecycleBackend bukkitPublicBackend
    ) {
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.worldsProviderBackend = Objects.requireNonNull(worldsProviderBackend, "worldsProviderBackend");
        this.paperLikeRuntimeBackend = Objects.requireNonNull(paperLikeRuntimeBackend, "paperLikeRuntimeBackend");
        this.bukkitPublicBackend = Objects.requireNonNull(bukkitPublicBackend, "bukkitPublicBackend");
        this.backends = List.of(worldsProviderBackend, paperLikeRuntimeBackend, bukkitPublicBackend);
        this.worldBackendByKey = new ConcurrentHashMap<>();
    }

    /**
     * Drops the cached snapshot so the next boot in this JVM builds one against the server it is running on.
     */
    public static void reset() {
        synchronized (WorldLifecycleService.class) {
            instance = null;
        }
    }

    public static WorldLifecycleService get() {
        WorldLifecycleService current = instance;
        if (current != null) {
            return current;
        }

        synchronized (WorldLifecycleService.class) {
            if (instance != null) {
                return instance;
            }

            CapabilitySnapshot capabilities = CapabilitySnapshot.probe();
            instance = new WorldLifecycleService(capabilities);
            IrisLogging.info("WorldLifecycle capabilities: %s", capabilities.describe());
            return instance;
        }
    }

    public CapabilitySnapshot capabilities() {
        return capabilities;
    }

    public CompletableFuture<World> create(WorldLifecycleRequest request) {
        WorldLifecycleBackend backend;
        try {
            if (request.callerKind() == WorldLifecycleCaller.FORCED_STUDIO) {
                ServerConfigurator.requireWorldCreationReady(true);
            } else {
                ServerConfigurator.requireWorldCreationReady(false);
            }
            backend = selectCreateBackend(request);
        } catch (Throwable e) {
            IrisLogging.reportError("WorldLifecycle create backend selection failed for world=\"" + request.worldName()
                    + "\", caller=" + request.callerKind().name().toLowerCase() + ".", e);
            return CompletableFuture.failedFuture(e);
        }
        IrisLogging.info("WorldLifecycle create: world=%s, caller=%s, backend=%s",
                request.worldName(),
                request.callerKind().name().toLowerCase(),
                backend.backendName());
        return backend.create(request).whenComplete((world, throwable) -> {
            if (throwable != null) {
                Throwable cause = WorldLifecycleSupport.unwrap(throwable);
                IrisLogging.reportError("WorldLifecycle create failed: world=\"" + request.worldName()
                        + "\", caller=" + request.callerKind().name().toLowerCase()
                        + ", backend=" + backend.backendName()
                        + ", family=" + capabilities.serverFamily().id() + ".", cause);
                return;
            }
            if (world != null) {
                worldBackendByKey.put(WorldIdentity.serialize(world), backend.backendName());
            }
        });
    }

    public World createBlocking(WorldLifecycleRequest request) {
        try {
            return create(request).join();
        } catch (CompletionException e) {
            throw new IllegalStateException(WorldLifecycleSupport.unwrap(e));
        }
    }

    public CompletableFuture<Boolean> unloadAsync(World world, boolean save) {
        World requiredWorld = Objects.requireNonNull(world, "world");
        String worldIdentity = WorldIdentity.serialize(requiredWorld);
        String worldName = requiredWorld.getName();
        WorldLifecycleBackend backend = selectUnloadBackend(worldIdentity);
        IrisLogging.info("WorldLifecycle unload: world=%s, backend=%s",
                worldName,
                backend.backendName());

        WorldUnloadBoundaryRegistry.Boundary rawBoundary;
        try {
            rawBoundary = WorldUnloadBoundaryRegistry.begin(worldIdentity);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }

        CompletableFuture<Boolean> unloadFuture;
        try {
            unloadFuture = backend.unloadAsync(requiredWorld, save);
            if (unloadFuture == null) {
                throw new IllegalStateException("World lifecycle backend returned no unload completion future.");
            }
        } catch (Throwable e) {
            unloadFuture = CompletableFuture.failedFuture(e);
        }
        unloadFuture.whenComplete((unloaded, throwable) ->
                WorldUnloadBoundaryRegistry.complete(rawBoundary, unloaded, throwable));

        CompletableFuture<Boolean> guardedFuture = guardUnloadCompletion(worldName, unloadFuture);
        return guardedFuture.whenComplete((unloaded, throwable) -> {
            if (throwable != null) {
                Throwable cause = WorldLifecycleSupport.unwrap(throwable);
                IrisLogging.reportError("WorldLifecycle unload failed: world=\"" + worldName
                        + "\", backend=" + backend.backendName()
                        + ", family=" + capabilities.serverFamily().id() + ".", cause);
                return;
            }
            if (Boolean.TRUE.equals(unloaded)) {
                worldBackendByKey.remove(worldIdentity, backend.backendName());
            }
        });
    }

    private CompletableFuture<Boolean> guardUnloadCompletion(
            String worldName,
            CompletableFuture<Boolean> unloadFuture
    ) {
        CompletableFuture<Boolean> guarded = new CompletableFuture<>();
        unloadFuture.whenComplete((unloaded, throwable) -> {
            if (throwable == null) {
                guarded.complete(Boolean.TRUE.equals(unloaded));
            } else {
                guarded.completeExceptionally(WorldLifecycleSupport.unwrap(throwable));
            }
        });
        if (!guarded.isDone()) {
            CompletableFuture.delayedExecutor(UNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
                TimeoutException timeout = new TimeoutException(
                        "World unload did not settle within " + UNLOAD_TIMEOUT_SECONDS + " seconds for \""
                                + worldName + "\".");
                if (!guarded.completeExceptionally(timeout) || IrisToolbelt.isServerStopping()) {
                    return;
                }
                ServerConfigurator.restart("World unload timed out for \"" + worldName + "\".");
            });
        }
        return guarded;
    }

    public boolean unload(World world, boolean save) {
        if (J.isPrimaryThread() || (J.isFolia() && WorldLifecycleSupport.isGlobalTickThread())) {
            throw new IllegalStateException("WorldLifecycle unload cannot block the primary/global tick thread; use unloadAsync instead.");
        }

        try {
            return Boolean.TRUE.equals(unloadAsync(world, save).join());
        } catch (CompletionException e) {
            Throwable cause = WorldLifecycleSupport.unwrap(e);
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }

    public String backendNameForWorld(NamespacedKey worldKey) {
        return selectUnloadBackend(worldKey.toString()).backendName();
    }

    WorldLifecycleBackend selectCreateBackend(WorldLifecycleRequest request) {
        if (worldsProviderBackend.supports(request, capabilities)) {
            return worldsProviderBackend;
        }

        if (capabilities.regionizedRuntime()
                || capabilities.serverFamily() == ServerFamily.FOLIA
                || (request.studio() && capabilities.serverFamily().isPaperLike())) {
            if (!paperLikeRuntimeBackend.supports(request, capabilities)) {
                throw new IllegalStateException("World lifecycle backend paper_like_runtime is unavailable for runtime create on "
                        + capabilities.serverFamily().id() + ": " + capabilities.paperLikeResolution());
            }
            return paperLikeRuntimeBackend;
        }

        for (WorldLifecycleBackend backend : backends) {
            if (backend.supports(request, capabilities)) {
                return backend;
            }
        }

        throw new IllegalStateException("No world lifecycle backend supports request for \"" + request.worldName() + "\".");
    }

    WorldLifecycleBackend selectUnloadBackend(String worldIdentity) {
        String backendName = worldBackendByKey.get(worldIdentity);
        if (backendName == null) {
            return bukkitPublicBackend;
        }

        for (WorldLifecycleBackend backend : backends) {
            if (backend.backendName().equals(backendName)) {
                return backend;
            }
        }

        return bukkitPublicBackend;
    }

    void rememberBackend(NamespacedKey worldKey, String backendName) {
        worldBackendByKey.put(worldKey.toString(), backendName);
    }
}
