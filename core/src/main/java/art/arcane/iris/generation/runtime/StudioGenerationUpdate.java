package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.pack.AtomicDirectoryPublisher;
import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationEpochContractFactory;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationPackFingerprint;
import art.arcane.iris.world.history.GenerationPackRepository;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.iris.world.history.GenerationRegistryContractFactory;
import art.arcane.iris.generation.terrain.IrisDimension;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

record StudioGenerationUpdate(
        Path stagingRoot,
        Path source,
        String fingerprint,
        GenerationEpoch.DimensionContract dimensionContract,
        GenerationRegistryContract registryContract
) implements AutoCloseable {
    static StudioGenerationUpdate prepare(IrisEngine engine, GenerationHistory history) throws IOException {
        Path source = engine.getStudioGenerationSource().toRealPath();
        IrisData.invalidateLoadedAuthoringResources(source.toFile());
        String fingerprint = GenerationPackFingerprint.compute(source, GenerationPackFingerprint.CURRENT_VERSION);
        GenerationEpoch active = history.activeEpoch();
        if (active.packFingerprintVersion() == GenerationPackFingerprint.CURRENT_VERSION
                && fingerprint.equals(active.packFingerprint())) {
            return null;
        }
        Path stagingRoot = Files.createTempDirectory(history.paths().generationRoot(), ".studio-");
        Path snapshot = stagingRoot.resolve(source.getFileName());
        try {
            GenerationPackRepository.copyPackTree(source, snapshot);
            if (!fingerprint.equals(GenerationPackFingerprint.compute(snapshot, GenerationPackFingerprint.CURRENT_VERSION))) {
                throw new IOException("Studio pack changed while its update snapshot was being captured.");
            }
            return prepareSnapshot(engine, history, stagingRoot, snapshot, fingerprint);
        } catch (Throwable failure) {
            try {
                AtomicDirectoryPublisher.deleteTree(stagingRoot);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static StudioGenerationUpdate prepareSnapshot(IrisEngine engine, GenerationHistory history,
                                                           Path stagingRoot, Path source, String fingerprint)
            throws IOException {
        GenerationEpoch active = history.activeEpoch();
        PackValidationResult validation = PackValidator.validate(source.toFile());
        if (!validation.isLoadable()) {
            throw new IOException("Studio pack update is invalid: " + String.join("; ", validation.getBlockingErrors()));
        }
        IrisData data = IrisData.openRuntime(source.toFile());
        try {
            IrisDimension replacement = data.getDimensionLoader().load(active.dimensionContract().dimensionKey());
            if (replacement == null) {
                throw new IOException("Studio pack update is missing dimension '"
                        + active.dimensionContract().dimensionKey() + "'.");
            }
            engine.getPlatformHooks().validateDimensionHotload(engine, replacement);
            GenerationEpoch.DimensionContract contract = GenerationEpochContractFactory.create(
                    replacement, replacement.getLoadKey(), active.dimensionContract().dimensionTypeKey());
            if (!active.dimensionContract().hasSameLayout(contract)) {
                throw new IOException("Studio pack update changes the world's immutable dimension layout.");
            }
            GenerationRegistryContract registry = GenerationRegistryContractFactory.create(data, replacement, fingerprint);
            registry.requireDefinitionsAvailableIn(GenerationRegistryContractFactory.captureRequiredDefinitions(List.of(registry)));
            return new StudioGenerationUpdate(stagingRoot, source, fingerprint, contract, registry);
        } finally {
            data.close();
        }
    }

    @Override
    public void close() throws IOException {
        AtomicDirectoryPublisher.deleteTree(stagingRoot);
    }
}
