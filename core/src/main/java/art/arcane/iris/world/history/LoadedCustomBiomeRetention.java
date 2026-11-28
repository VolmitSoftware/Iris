package art.arcane.iris.world.history;

import art.arcane.iris.pack.datapack.IDataFixer;
import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.iris.world.history.GenerationRegistryContractFactory.CustomBiomeDefinition;
import art.arcane.iris.world.history.GenerationRegistryContractFactory.CustomBiomeSelection;
import art.arcane.iris.world.history.GenerationRegistryContractFactory.SelectedCustomBiome;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Picks, for each custom biome of a Studio update, a definition the running server has loaded: the edited one
 * when the server already has it, otherwise the definition the active epoch recorded for the same biome id.
 * A biome with neither generates as its vanilla derivative until the server restarts and loads it.
 */
final class LoadedCustomBiomeRetention implements CustomBiomeSelection {
    private static final String GENERATED_RENDERER = "generated";

    private final GenerationRegistryContract loadedContract;
    private final IDataFixer fixer;
    private final PlatformGenerationRegistry registry;
    private final Map<String, BiomeIdentity> deferred = new TreeMap<>();
    private final TreeSet<String> standIns = new TreeSet<>();

    LoadedCustomBiomeRetention(
            GenerationRegistryContract loadedContract,
            IDataFixer fixer,
            PlatformGenerationRegistry registry
    ) {
        this.loadedContract = Objects.requireNonNull(loadedContract, "loadedContract");
        this.fixer = Objects.requireNonNull(fixer, "fixer");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public SelectedCustomBiome select(
            String dimensionKey,
            String customBiomeId,
            CustomBiomeDefinition current,
            String standInKey
    ) throws IOException {
        if (isLoaded(current)) {
            return SelectedCustomBiome.current(current);
        }
        BiomeIdentity identity = new BiomeIdentity(dimensionKey, customBiomeId);
        List<GenerationRegistryContract.PhysicalResourceKey> loadedKeys = recordedKeys(loadedContract, identity);
        if (loadedKeys.size() != 1) {
            return standIn(identity, standInKey);
        }
        GenerationRegistryContract.PhysicalResourceKey loadedKey = loadedKeys.getFirst();
        CustomBiomeDefinition loaded = loadedDefinition(loadedKey, identity);
        if (!isLoaded(loaded)) {
            return standIn(identity, standInKey);
        }
        deferred.put(identity.logicalKey(), identity);
        return SelectedCustomBiome.loaded(
                loaded,
                loadedContract.biomeTags().getOrDefault(loadedKey.resourceKey(), List.of())
        );
    }

    private SelectedCustomBiome standIn(BiomeIdentity identity, String standInKey) throws IOException {
        if (standInKey == null || standInKey.isBlank()) {
            throw new IOException("Custom biome '" + identity.logicalKey() + "' is not loaded and has no vanilla derivative.");
        }
        standIns.add(identity.label());
        return SelectedCustomBiome.standIn(platformBiomeKey(standInKey));
    }

    private static String platformBiomeKey(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return normalized.indexOf(':') < 0 ? "minecraft:" + normalized : normalized;
    }

    StudioRegistryCapture finish(GenerationRegistryContract contract) throws IOException {
        TreeSet<String> pending = new TreeSet<>(standIns);
        for (BiomeIdentity identity : deferred.values()) {
            if (recordedKeys(contract, identity).size() != 1) {
                throw new IOException("Custom biome '" + identity.logicalKey()
                        + "' has more than one definition in this Studio update.");
            }
            pending.add(identity.label());
        }
        return new StudioRegistryCapture(contract, List.copyOf(pending));
    }

    private List<GenerationRegistryContract.PhysicalResourceKey> recordedKeys(
            GenerationRegistryContract contract,
            BiomeIdentity identity
    ) {
        return GenerationRegistryContractFactory.recordedCustomBiomeKeys(
                contract, identity.dimensionKey(), identity.biomeId(), registry::customBiomeResourceKey);
    }

    private CustomBiomeDefinition loadedDefinition(
            GenerationRegistryContract.PhysicalResourceKey key,
            BiomeIdentity identity
    ) throws IOException {
        GenerationRegistryContract.GeneratedSource source = loadedContract.generatedSources().get(key);
        PlatformGenerationRegistry.Definition definition =
                GenerationRegistryContractFactory.canonicalGeneratedDefinition(key, source, fixer, registry);
        return new CustomBiomeDefinition(
                key,
                GenerationRegistryContractFactory.fingerprintCustomBiomeIdentity(
                        source.semanticSha256(), identity.dimensionKey(), identity.biomeId()),
                source.semanticSha256(),
                source.semanticJson(),
                GenerationRegistryContractFactory.fingerprintCustomBiomeDefinition(definition.value()),
                definition.value(),
                definition
        );
    }

    private boolean isLoaded(CustomBiomeDefinition definition) {
        GenerationRegistryContract.PhysicalResourceKey key = definition.physicalKey();
        PlatformGenerationRegistry.Definition live;
        try {
            live = registry.generatedDefinition(key.registryKey(), key.resourceKey());
        } catch (RuntimeException missing) {
            return false;
        }
        if (live == null || live.encoding() != PlatformGenerationRegistry.Encoding.JSON) {
            return false;
        }
        return GenerationRegistryContractFactory.fingerprintDefinition(key, live, GENERATED_RENDERER)
                .equals(GenerationRegistryContractFactory.fingerprintDefinition(
                        key, definition.definition(), GENERATED_RENDERER));
    }

    private record BiomeIdentity(String dimensionKey, String biomeId) {
        String logicalKey() {
            return dimensionKey.toLowerCase(Locale.ROOT) + ":" + biomeId.toLowerCase(Locale.ROOT);
        }

        String label() {
            return biomeId.toLowerCase(Locale.ROOT);
        }
    }
}
