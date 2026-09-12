package art.arcane.iris.world.lifecycle;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.World;

import java.util.Locale;
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
        Object legacyStorageAccess = null;
        try {
            World existing = WorldIdentity.resolve(request.worldKey()).orElse(null);
            if (existing != null) {
                return CompletableFuture.completedFuture(existing);
            }

            if (request.generator() == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Runtime world creation requires a non-null chunk generator."));
            }

            WorldLifecycleStaging.stageGenerator(request.worldName(), request.generator(), request.biomeProvider());
            WorldLifecycleSupport.stageRuntimeConfiguration(request.worldName());

            IrisLogging.debug("WorldLifecycle runtime LevelStem: world=" + request.worldName()
                    + ", backend=paper_like_runtime, flavor=" + capabilities.paperLikeFlavor().name().toLowerCase(Locale.ROOT)
                    + ", registrySource=" + WorldLifecycleSupport.runtimeLevelStemRegistrySource(request));
            Object levelStem = WorldLifecycleSupport.resolveRuntimeLevelStem(capabilities, request);
            Object stemKey = WorldLifecycleSupport.createRuntimeLevelStemKey(request.worldKey());

            if (capabilities.paperLikeFlavor() == CapabilitySnapshot.PaperLikeFlavor.CURRENT_INFO_AND_DATA) {
                Object dimensionKey = WorldLifecycleSupport.createDimensionKey(stemKey);
                Object loadedWorldData = capabilities.paperWorldDataMethod().invoke(null, capabilities.minecraftServer(), dimensionKey, request.worldName());
                Object worldLoadingInfo = capabilities.worldLoadingInfoConstructor().newInstance(request.environment(), stemKey, dimensionKey, !request.studio());
                Object worldLoadingInfoAndData = capabilities.worldLoadingInfoAndDataConstructor().newInstance(worldLoadingInfo, loadedWorldData);
                Object worldDataAndGenSettings = WorldLifecycleSupport.createCurrentWorldDataAndSettings(capabilities, request.worldName());
                if (!WorldLifecycleSupport.hasExistingWorldData(request.worldKey())) {
                    worldDataAndGenSettings = WorldLifecycleSupport.applySeedToWorldDataAndGenSettings(worldDataAndGenSettings, request.seed());
                }
                capabilities.createLevelMethod().invoke(capabilities.minecraftServer(), levelStem, worldLoadingInfoAndData, worldDataAndGenSettings);
            } else {
                legacyStorageAccess = WorldLifecycleSupport.createLegacyStorageAccess(capabilities);
                Object primaryLevelData = WorldLifecycleSupport.createLegacyPrimaryLevelData(capabilities, legacyStorageAccess, request.worldName());
                Object worldLoadingInfo = capabilities.worldLoadingInfoConstructor().newInstance(0, request.worldName(), request.environment().name().toLowerCase(Locale.ROOT), stemKey, !request.studio());
                capabilities.createLevelMethod().invoke(capabilities.minecraftServer(), levelStem, worldLoadingInfo, legacyStorageAccess, primaryLevelData);
            }

            World loadedWorld = WorldIdentity.resolve(request.worldKey()).orElse(null);
            if (loadedWorld == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Paper-like runtime backend did not load world \"" + request.worldName() + "\"."));
            }

            return CompletableFuture.completedFuture(loadedWorld);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(WorldLifecycleSupport.unwrap(e));
        } finally {
            WorldLifecycleStaging.clearGenerator(request.worldName());
            WorldLifecycleSupport.closeLevelStorageAccess(legacyStorageAccess);
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
