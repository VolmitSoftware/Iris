package art.arcane.iris.world.lifecycle;

import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldLifecycleStaging {
    private static final Map<String, ChunkGenerator> stagedGenerators = new ConcurrentHashMap<>();
    private static final Map<String, BiomeProvider> stagedBiomeProviders = new ConcurrentHashMap<>();
    private static final Map<String, ChunkGenerator> stagedStemGenerators = new ConcurrentHashMap<>();

    private WorldLifecycleStaging() {
    }

    public static void stageGenerator(@NotNull String worldName, @NotNull ChunkGenerator generator, @Nullable BiomeProvider biomeProvider) {
        stagedGenerators.put(worldName, generator);
        if (biomeProvider != null) {
            stagedBiomeProviders.put(worldName, biomeProvider);
        } else {
            stagedBiomeProviders.remove(worldName);
        }
    }

    public static void stageStemGenerator(@NotNull String worldName, @NotNull ChunkGenerator generator) {
        stagedStemGenerators.put(worldName, generator);
    }

    @Nullable
    public static ChunkGenerator consumeGenerator(@NotNull String worldName) {
        return stagedGenerators.remove(worldName);
    }

    @Nullable
    public static BiomeProvider consumeBiomeProvider(@NotNull String worldName) {
        return stagedBiomeProviders.remove(worldName);
    }

    @Nullable
    public static ChunkGenerator consumeStemGenerator(@NotNull String worldName) {
        return stagedStemGenerators.remove(worldName);
    }

    @Nullable
    public static ChunkGenerator peekStemGenerator(@NotNull String worldName) {
        return stagedStemGenerators.get(worldName);
    }

    public static void clearGenerator(@NotNull String worldName) {
        stagedGenerators.remove(worldName);
        stagedBiomeProviders.remove(worldName);
    }

    public static void clearStem(@NotNull String worldName) {
        stagedStemGenerators.remove(worldName);
    }

    public static void clearAll(@NotNull String worldName) {
        clearGenerator(worldName);
        clearStem(worldName);
    }
}
