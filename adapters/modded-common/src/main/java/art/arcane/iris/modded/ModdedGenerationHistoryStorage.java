package art.arcane.iris.modded;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.BrokenPackException;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationEpochContractFactory;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationPackFingerprint;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.iris.world.history.GenerationRegistryContractFactory;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformGenerationRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class ModdedGenerationHistoryStorage {
    private ModdedGenerationHistoryStorage() {
    }

    static ActivePack createOrStage(
            MinecraftServer server,
            ResourceKey<Level> levelKey,
            String selectedPack,
            String selectedDimensionKey,
            long worldSeed
    ) {
        Path dimensionRoot = dimensionRoot(server, levelKey);
        try {
            Path source = selectedPackSource(selectedPack, selectedDimensionKey);
            Candidate candidate = captureCandidate(source, selectedDimensionKey);
            GenerationHistory history = createOrStage(
                    dimensionRoot,
                    source,
                    worldSeed,
                    candidate,
                    ModdedGenerationHistoryStorage::requireRegistryDefinitions,
                    IrisSettings.get().getGenerator().getGenerationTransitionWidthBlocks()
            );
            return resolveActive(history);
        } catch (IOException failure) {
            throw historyFailure(levelKey, failure);
        }
    }

    static ActivePack restoreOrAdopt(
            MinecraftServer server,
            ResourceKey<Level> levelKey,
            String selectedPack,
            String selectedDimensionKey,
            long worldSeed
    ) {
        Path dimensionRoot = dimensionRoot(server, levelKey);
        try {
            GenerationHistory history = restoreOrAdopt(
                    dimensionRoot,
                    worldSeed,
                    () -> {
                        Path source = selectedPackSource(selectedPack, selectedDimensionKey);
                        return new AdoptionSource(source, captureCandidate(
                                source,
                                selectedDimensionKey,
                                GenerationRegistryContractFactory.CustomBiomeAliasPolicy.RETAIN_LEGACY_ALIASES
                        ));
                    },
                    ModdedGenerationHistoryStorage::requireRegistryDefinitions
            );
            return resolveActive(history);
        } catch (IOException failure) {
            throw historyFailure(levelKey, failure);
        }
    }

    static ActivePack openOrAdopt(
            ServerLevel level,
            String selectedPack,
            String selectedDimensionKey,
            long worldSeed
    ) {
        return restoreOrAdopt(
                level.getServer(),
                level.dimension(),
                selectedPack,
                selectedDimensionKey,
                worldSeed
        );
    }

    static GenerationHistory createOrStage(
            Path dimensionRoot,
            Path source,
            long worldSeed,
            Candidate candidate,
            RegistryContractValidator validator,
            int transitionWidthBlocks
    ) throws IOException {
        Path requiredRoot = normalize(dimensionRoot);
        Path requiredSource = normalize(source);
        Candidate requiredCandidate = Objects.requireNonNull(candidate, "candidate");
        RegistryContractValidator requiredValidator = Objects.requireNonNull(validator, "validator");
        Optional<GenerationHistory> existing = GenerationHistory.openIfPresent(requiredRoot, worldSeed);
        if (existing.isEmpty()) {
            captureAndRequireRegistryDefinitions(
                    List.of(requiredCandidate.registryContract()),
                    requiredValidator
            );
            Files.createDirectories(requiredRoot);
            return GenerationHistory.create(
                    requiredRoot,
                    requiredSource,
                    requiredCandidate.packFingerprint(),
                    worldSeed,
                    requiredCandidate.dimensionContract(),
                    requiredCandidate.registryContract()
            );
        }

        GenerationHistory history = existing.get();
        captureAndRequireRegistryDefinitions(registryContracts(history), requiredValidator);
        history.stageUpdate(
                requiredSource,
                requiredCandidate.packFingerprint(),
                requiredCandidate.dimensionContract(),
                requiredCandidate.registryContract(),
                transitionWidthBlocks
        );
        return history;
    }

    static GenerationHistory restoreOrAdopt(
            Path dimensionRoot,
            long worldSeed,
            AdoptionSourceSupplier sourceSupplier,
            RegistryContractValidator validator
    ) throws IOException {
        Path requiredRoot = normalize(dimensionRoot);
        RegistryContractValidator requiredValidator = Objects.requireNonNull(validator, "validator");
        Optional<GenerationHistory> existing = GenerationHistory.openIfPresent(requiredRoot, worldSeed);
        if (existing.isPresent()) {
            requireRegistryDefinitions(existing.get(), requiredValidator);
            return existing.get();
        }

        AdoptionSource adoption = Objects.requireNonNull(sourceSupplier, "sourceSupplier").get();
        Candidate candidate = Objects.requireNonNull(adoption.candidate(), "candidate");
        captureAndRequireRegistryDefinitions(List.of(candidate.registryContract()), requiredValidator);
        Files.createDirectories(requiredRoot);
        return GenerationHistory.create(
                requiredRoot,
                normalize(adoption.packSource()),
                candidate.packFingerprint(),
                worldSeed,
                candidate.dimensionContract(),
                candidate.registryContract()
        );
    }

    static ActivePack resolveActive(GenerationHistory history) throws IOException {
        GenerationEpoch epoch = history.activeEpoch();
        Path packRoot = normalize(history.activePackRoot());
        validatePack(packRoot, epoch.packFingerprint());
        validateDimension(packRoot, epoch);
        return new ActivePack(
                packRoot,
                epoch.dimensionContract().dimensionKey(),
                epoch.dimensionContract(),
                history
        );
    }

    private static void validatePack(Path packRoot, String fingerprint) {
        PackValidationResult validation = PackValidator.validate(packRoot.toFile());
        PackValidationRegistry.publish(packRoot, validation, fingerprint);
        if (!validation.isLoadable()) {
            throw new BrokenPackException(packRoot.toString(), validation.getBlockingErrors());
        }
    }

    private static void validateDimension(
            Path packRoot,
            GenerationEpoch epoch
    ) throws IOException {
        GenerationEpoch.DimensionContract recordedContract = epoch.dimensionContract();
        IrisData data = IrisData.openDatapackCompiler(packRoot.toFile());
        try {
            IrisDimension dimension = data.getDimensionLoader().load(recordedContract.dimensionKey(), false);
            if (dimension == null) {
                throw new IOException("Immutable generation pack does not contain dimension '"
                        + recordedContract.dimensionKey() + "'.");
            }
            GenerationEpoch.DimensionContract actualContract = GenerationEpochContractFactory.createForEpoch(
                    dimension,
                    recordedContract.dimensionTypeKey(),
                    epoch
            );
            if (!recordedContract.equals(actualContract)) {
                throw new IOException("Immutable generation pack no longer matches its recorded dimension contract.");
            }
        } finally {
            data.close();
        }
    }

    private static Candidate captureCandidate(Path source, String dimensionKey) throws IOException {
        return captureCandidate(
                source,
                dimensionKey,
                GenerationRegistryContractFactory.CustomBiomeAliasPolicy.CONTENT_ADDRESSED_ONLY
        );
    }

    private static Candidate captureCandidate(
            Path source,
            String dimensionKey,
            GenerationRegistryContractFactory.CustomBiomeAliasPolicy aliasPolicy
    ) throws IOException {
        String fingerprint = GenerationPackFingerprint.compute(
                source,
                GenerationPackFingerprint.CURRENT_VERSION
        );
        IrisData data = IrisData.openDatapackCompiler(source.toFile());
        try {
            IrisDimension dimension = data.getDimensionLoader().load(dimensionKey, false);
            if (dimension == null) {
                throw new IOException("Selected Iris pack does not contain dimension '" + dimensionKey + "'.");
            }
            PlatformGenerationRegistry generationRegistry = IrisPlatforms.get()
                    .registries()
                    .generationRegistry();
            String dimensionTypeKey = generationRegistry.dimensionTypeResourceKey(
                    source.getFileName().toString(),
                    dimension.getLoadKey(),
                    dimension.getDimensionTypeKey()
            );
            GenerationEpoch.DimensionContract dimensionContract = GenerationEpochContractFactory.create(
                    dimension,
                    dimension.getLoadKey(),
                    dimensionTypeKey
            );
            GenerationRegistryContract registryContract = GenerationRegistryContractFactory.create(
                    data,
                    dimension,
                    fingerprint,
                    aliasPolicy
            );
            return new Candidate(fingerprint, dimensionContract, registryContract);
        } finally {
            data.close();
        }
    }

    private static Path selectedPackSource(String pack, String dimensionKey) {
        ModdedStartup.requirePackForWorldCreation(pack);
        return normalize(ModdedWorldEngines.resolvePack(pack, dimensionKey).toPath());
    }

    private static GenerationRegistryContract requireRegistryDefinitions(
            Collection<GenerationRegistryContract> contracts
    )
            throws IOException {
        return GenerationRegistryContractFactory.captureRequiredDefinitions(contracts);
    }

    private static void requireRegistryDefinitions(
            GenerationHistory history,
            RegistryContractValidator validator
    ) throws IOException {
        captureAndRequireRegistryDefinitions(registryContracts(history), validator);
    }

    private static void captureAndRequireRegistryDefinitions(
            Collection<GenerationRegistryContract> contracts,
            RegistryContractValidator validator
    ) throws IOException {
        GenerationRegistryContract available = validator.capture(contracts);
        for (GenerationRegistryContract contract : contracts) {
            contract.requireDefinitionsAvailableIn(available);
        }
    }

    private static List<GenerationRegistryContract> registryContracts(GenerationHistory history) {
        Collection<GenerationEpoch> epochs = history.manifest().epochs();
        List<GenerationRegistryContract> contracts = new ArrayList<>(epochs.size());
        for (GenerationEpoch epoch : epochs) {
            contracts.add(epoch.registryContract());
        }
        return contracts;
    }

    private static Path dimensionRoot(MinecraftServer server, ResourceKey<Level> levelKey) {
        return normalize(ModdedDimensionStorage.storageFolder(server, levelKey).toPath());
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    private static IllegalStateException historyFailure(ResourceKey<Level> levelKey, IOException failure) {
        return new IllegalStateException("Iris generation history is unusable for '"
                + levelKey.identifier() + "'.", failure);
    }

    record ActivePack(
            Path packRoot,
            String dimensionKey,
            GenerationEpoch.DimensionContract dimensionContract,
            GenerationHistory history
    ) {
        ActivePack {
            packRoot = normalize(packRoot);
            dimensionKey = Objects.requireNonNull(dimensionKey, "dimensionKey");
            dimensionContract = Objects.requireNonNull(dimensionContract, "dimensionContract");
            history = Objects.requireNonNull(history, "history");
        }
    }

    record Candidate(
            String packFingerprint,
            GenerationEpoch.DimensionContract dimensionContract,
            GenerationRegistryContract registryContract
    ) {
        Candidate {
            packFingerprint = Objects.requireNonNull(packFingerprint, "packFingerprint");
            dimensionContract = Objects.requireNonNull(dimensionContract, "dimensionContract");
            registryContract = Objects.requireNonNull(registryContract, "registryContract");
        }
    }

    record AdoptionSource(Path packSource, Candidate candidate) {
        AdoptionSource {
            packSource = normalize(packSource);
            candidate = Objects.requireNonNull(candidate, "candidate");
        }
    }

    @FunctionalInterface
    interface AdoptionSourceSupplier {
        AdoptionSource get() throws IOException;
    }

    @FunctionalInterface
    interface RegistryContractValidator {
        GenerationRegistryContract capture(Collection<GenerationRegistryContract> contracts) throws IOException;
    }

}
