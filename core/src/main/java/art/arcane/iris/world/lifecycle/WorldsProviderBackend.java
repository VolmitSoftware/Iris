package art.arcane.iris.world.lifecycle;

import art.arcane.iris.world.IrisWorldStorage;
import org.bukkit.World;
import org.bukkit.WorldType;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

final class WorldsProviderBackend implements WorldLifecycleBackend {
    private final CapabilitySnapshot capabilities;

    WorldsProviderBackend(CapabilitySnapshot capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public boolean supports(WorldLifecycleRequest request, CapabilitySnapshot capabilities) {
        return request.studio() && capabilities.hasWorldsProvider();
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<World> create(WorldLifecycleRequest request) {
        try {
            Path worldPath = IrisWorldStorage.dimensionRoot(request.worldKey()).toPath();
            Object builder = WorldLifecycleSupport.invokeNamed(capabilities.worldsProvider(), "levelBuilder", new Class[]{Path.class}, worldPath);
            builder = WorldLifecycleSupport.invokeNamed(builder, "name", new Class[]{String.class}, request.worldName());
            builder = WorldLifecycleSupport.invokeNamed(builder, "seed", new Class[]{long.class}, request.seed());
            builder = WorldLifecycleSupport.invokeNamed(builder, "levelStem", new Class[]{capabilities.worldsLevelStemClass()}, resolveLevelStem(request.environment()));
            builder = WorldLifecycleSupport.invokeNamed(builder, "chunkGenerator", new Class[]{org.bukkit.generator.ChunkGenerator.class}, request.generator());
            builder = WorldLifecycleSupport.invokeNamed(builder, "biomeProvider", new Class[]{org.bukkit.generator.BiomeProvider.class}, request.biomeProvider());
            builder = WorldLifecycleSupport.invokeNamed(builder, "generatorType", new Class[]{capabilities.worldsGeneratorTypeClass()}, resolveGeneratorType(request.worldType()));
            builder = WorldLifecycleSupport.invokeNamed(builder, "structures", new Class[]{boolean.class}, request.generateStructures());
            builder = WorldLifecycleSupport.invokeNamed(builder, "hardcore", new Class[]{boolean.class}, request.hardcore());
            Object levelBuilder = WorldLifecycleSupport.invokeNamed(builder, "build", new Class[0]);
            Object async = WorldLifecycleSupport.invokeNamed(levelBuilder, "createAsync", new Class[0]);
            if (async instanceof CompletableFuture<?> future) {
                return future.thenApply(world -> (World) world);
            }
            return CompletableFuture.failedFuture(new IllegalStateException("Worlds provider createAsync did not return CompletableFuture."));
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(WorldLifecycleSupport.unwrap(e));
        }
    }

    @Override
    public CompletableFuture<Boolean> unloadAsync(World world, boolean save) {
        return WorldLifecycleSupport.unloadWorldAsync(capabilities, world, save);
    }

    @Override
    public String backendName() {
        return "worlds_provider";
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object resolveLevelStem(World.Environment environment) {
        String key;
        if (environment == World.Environment.NETHER) {
            key = "NETHER";
        } else if (environment == World.Environment.THE_END) {
            key = "END";
        } else {
            key = "OVERWORLD";
        }
        Class<? extends Enum> enumClass = capabilities.worldsLevelStemClass().asSubclass(Enum.class);
        return Enum.valueOf(enumClass, key);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object resolveGeneratorType(WorldType worldType) {
        String typeName = worldType == null ? "NORMAL" : worldType.getName();
        String key;
        if ("FLAT".equalsIgnoreCase(typeName)) {
            key = "FLAT";
        } else if ("AMPLIFIED".equalsIgnoreCase(typeName)) {
            key = "AMPLIFIED";
        } else if ("LARGE_BIOMES".equalsIgnoreCase(typeName) || "LARGEBIOMES".equalsIgnoreCase(typeName)) {
            key = "LARGE_BIOMES";
        } else {
            key = "NORMAL";
        }
        Class<? extends Enum> enumClass = capabilities.worldsGeneratorTypeClass().asSubclass(Enum.class);
        return Enum.valueOf(enumClass, key.toUpperCase(Locale.ROOT));
    }
}
