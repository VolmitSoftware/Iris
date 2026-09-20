package art.arcane.iris.world.lifecycle;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.volmlib.nativelib.terrain.WorldRuntimeOptions;

import java.util.concurrent.CompletableFuture;

final class PaperLikeRuntimeBackend implements WorldLifecycleBackend {
    private final CapabilitySnapshot capabilities;

    PaperLikeRuntimeBackend(CapabilitySnapshot capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public boolean supports(WorldLifecycleRequest request, CapabilitySnapshot capabilities) {
        if (!capabilities.serverFamily().isPaperLike() || !capabilities.hasPaperLikeRuntime()) {
            return false;
        }

        if (request.studio()) {
            return true;
        }

        return capabilities.serverFamily() == ServerFamily.FOLIA || capabilities.regionizedRuntime();
    }

    @Override
    public CompletableFuture<World> create(WorldLifecycleRequest request) {
        try {
            World existing = WorldIdentity.resolve(request.worldKey()).orElse(null);
            if (existing != null) {
                return CompletableFuture.completedFuture(existing);
            }

            if (request.generator() == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Runtime world creation requires a non-null chunk generator."));
            }

            WorldLifecycleStaging.stageGenerator(request.worldName(), request.generator(), request.biomeProvider());
            NamespacedKey dimensionTypeKey = request.generator() instanceof PlatformChunkGenerator generator
                    ? new NamespacedKey("iris", generator.getTarget().getDimension().getDimensionTypeKey()) : null;
            IrisLogging.debug("WorldLifecycle runtime LevelStem: world=" + request.worldName()
                    + ", backend=paper_like_runtime, flavor=" + capabilities.nativeRuntime().flavor()
                    + ", registrySource=" + (dimensionTypeKey == null ? "datapack_level_stem_registry" : "full_server_registry"));
            WorldRuntimeOptions options = new WorldRuntimeOptions(request.worldName(), request.worldKey(),
                    request.environment(), dimensionTypeKey, "Iris:runtime", !request.studio(), request.seed(),
                    WorldLifecycleSupport.hasExistingWorldData(request.worldKey()), IrisWorldStorage.levelRoot());
            World loadedWorld = capabilities.nativeRuntime().create(options, WorldLifecycleSupport.EXECUTION);

            return CompletableFuture.completedFuture(loadedWorld);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(WorldLifecycleSupport.unwrap(e));
        } finally {
            WorldLifecycleStaging.clearGenerator(request.worldName());
        }
    }

    @Override
    public CompletableFuture<Boolean> unloadAsync(World world, boolean save) {
        return WorldLifecycleSupport.unloadWorldAsync(capabilities, world, save);
    }

    @Override
    public String backendName() {
        return "paper_like_runtime";
    }
}
