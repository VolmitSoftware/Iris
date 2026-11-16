package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
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
    private static final String TRIAL_MANTLE_DIRECTORY = "trial-mantle";

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
            GenerationPackRepository.snapshotPackTree(source, snapshot);
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
            requireBuildableRuntime(engine, data, replacement, registry, stagingRoot.resolve(TRIAL_MANTLE_DIRECTORY));
            return new StudioGenerationUpdate(stagingRoot, source, fingerprint, contract, registry);
        } finally {
            if (!data.isClosed()) {
                data.close();
            }
        }
    }

    /**
     * The activation builds its runtime only after the manifest already points at the update, so a pack that cannot
     * build there leaves the Studio engine failed. Building and discarding one here rejects such a pack first.
     */
    private static void requireBuildableRuntime(IrisEngine engine, IrisData data, IrisDimension dimension,
                                                GenerationRegistryContract registry, Path mantleDirectory)
            throws IOException {
        data.bindGenerationRegistryContract(registry);
        IrisEngine.GenerationRuntimeBinding trial;
        try {
            trial = engine.buildDetachedGenerationRuntime(
                    new EngineTarget(engine.getWorld(), dimension, data), mantleDirectory, null);
        } catch (RuntimeException failure) {
            throw new IOException("Studio pack update cannot build a generation runtime: " + rootCauseMessage(failure), failure);
        }
        engine.closeDetachedGenerationRuntime(trial);
    }

    private static String rootCauseMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    @Override
    public void close() throws IOException {
        AtomicDirectoryPublisher.deleteTree(stagingRoot);
    }
}
