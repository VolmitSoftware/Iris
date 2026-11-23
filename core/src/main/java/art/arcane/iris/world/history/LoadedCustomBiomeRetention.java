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
 * Biomes with neither are collected and block the update.
 */
final class LoadedCustomBiomeRetention implements CustomBiomeSelection {
    private static final String GENERATED_RENDERER = "generated";

    private final GenerationRegistryContract loadedContract;
    private final IDataFixer fixer;
    private final PlatformGenerationRegistry registry;
    private final Map<String, BiomeIdentity> deferred = new TreeMap<>();
    private final TreeSet<String> blocking = new TreeSet<>();

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
    public SelectedCustomBiome select(String dimensionKey, String customBiomeId, CustomBiomeDefinition current)
            throws IOException {
        if (isLoaded(current)) {
            return SelectedCustomBiome.current(current);
        }
        BiomeIdentity identity = new BiomeIdentity(dimensionKey, customBiomeId);
        List<GenerationRegistryContract.PhysicalResourceKey> loadedKeys = recordedKeys(loadedContract, identity);
        if (loadedKeys.size() != 1) {
            blocking.add(identity.label());
            return SelectedCustomBiome.current(current);
        }
        GenerationRegistryContract.PhysicalResourceKey loadedKey = loadedKeys.getFirst();
        CustomBiomeDefinition loaded = loadedDefinition(loadedKey, identity);
        if (!isLoaded(loaded)) {
            blocking.add(identity.label());
            return SelectedCustomBiome.current(current);
        }
        deferred.put(identity.logicalKey(), identity);
        return SelectedCustomBiome.loaded(
                loaded,
                loadedContract.biomeTags().getOrDefault(loadedKey.resourceKey(), List.of())
        );
    }

    StudioRegistryCapture finish(GenerationRegistryContract contract) throws RegistryRestartRequiredException {
        TreeSet<String> deferredLabels = new TreeSet<>();
        for (BiomeIdentity identity : deferred.values()) {
            if (recordedKeys(contract, identity).size() == 1) {
                deferredLabels.add(identity.label());
            } else {
                blocking.add(identity.label());
            }
        }
        if (!blocking.isEmpty()) {
            throw new RegistryRestartRequiredException(List.copyOf(blocking), List.copyOf(deferredLabels));
        }
        return new StudioRegistryCapture(contract, List.copyOf(deferredLabels));
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
