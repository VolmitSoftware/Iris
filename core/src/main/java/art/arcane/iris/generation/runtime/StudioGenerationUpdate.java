package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.AtomicDirectoryPublisher;
import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationEpochContractFactory;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationPackRepository;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.iris.world.history.GenerationRegistryContractFactory;
import art.arcane.iris.world.history.PackTreeState;
import art.arcane.iris.world.history.VerifiedPackTree;
import art.arcane.iris.generation.terrain.IrisDimension;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

/**
 * A Studio pack edit captured as one read-only snapshot inside the world's generation folder. The snapshot is
 * hashed once and later moved into the epoch repository as it is.
 */
record StudioGenerationUpdate(
        Path stagingRoot,
        VerifiedPackTree pack,
        PackTreeState sourceState,
        GenerationEpoch.DimensionContract dimensionContract,
        GenerationRegistryContract registryContract
) implements AutoCloseable {
    private static final String TRIAL_MANTLE_DIRECTORY = ".trial-mantle";

    /**
     * @param previous the source the active epoch came from, when this engine activated it; without it every file
     *                 is compared by content against the active epoch
     * @return null when the source still matches the active epoch
     */
    static StudioGenerationUpdate prepare(IrisEngine engine, GenerationHistory history,
                                          ActivatedStudioSource previous) throws IOException {
        Path source = engine.getStudioGenerationSource().toRealPath();
        IrisData.invalidateLoadedAuthoringResources(source.toFile());
        PackTreeState sourceState = PackTreeState.capture(source);
        PackTreeState activeSourceState = previous == null ? null
                : previous.sourceStateOf(history.activeEpoch()).orElse(null);
        if (sourceState.equals(activeSourceState)) {
            return null;
        }
        Path stagingRoot = Files.createTempDirectory(history.paths().generationRoot(), ".studio-");
        try {
            StudioGenerationUpdate update = prepareStaged(
                    engine, history, source, sourceState, activeSourceState, stagingRoot);
            if (update == null) {
                AtomicDirectoryPublisher.deleteTree(stagingRoot);
            }
            return update;
        } catch (Throwable failure) {
            try {
                AtomicDirectoryPublisher.deleteTree(stagingRoot);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static StudioGenerationUpdate prepareStaged(IrisEngine engine, GenerationHistory history, Path source,
                                                        PackTreeState sourceState, PackTreeState activeSourceState,
                                                        Path stagingRoot) throws IOException {
        Path snapshot = stagingRoot.resolve(source.getFileName());
        GenerationPackRepository.snapshotPackTree(
                source, snapshot, history.activePackLinkBasis().orElse(null), activeSourceState);
        VerifiedPackTree pack = VerifiedPackTree.hash(snapshot);
        if (isActive(history.activeEpoch(), pack)) {
            return null;
        }
        if (!sourceState.equals(PackTreeState.capture(source))) {
            throw new IOException("Studio pack changed while its update snapshot was being captured.");
        }
        return prepareSnapshot(engine, history, stagingRoot, pack, sourceState);
    }

    private static boolean isActive(GenerationEpoch active, VerifiedPackTree pack) {
        return active.packFingerprintVersion() == pack.fingerprintVersion()
                && active.packFingerprint().equals(pack.fingerprint());
    }

    private static StudioGenerationUpdate prepareSnapshot(IrisEngine engine, GenerationHistory history,
                                                           Path stagingRoot, VerifiedPackTree pack,
                                                           PackTreeState sourceState) throws IOException {
        GenerationEpoch active = history.activeEpoch();
        IrisData data = IrisData.openRuntime(pack.root().toFile());
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
            GenerationRegistryContract registry = GenerationRegistryContractFactory.create(data, replacement, pack.fingerprint());
            registry.requireDefinitionsAvailableIn(GenerationRegistryContractFactory.captureRequiredDefinitions(List.of(registry)));
            requireBuildableRuntime(engine, data, replacement, registry, stagingRoot.resolve(TRIAL_MANTLE_DIRECTORY));
            return new StudioGenerationUpdate(stagingRoot, pack, sourceState, contract, registry);
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

    ActivatedStudioSource activatedAs(String epochId) {
        return new ActivatedStudioSource(epochId, sourceState);
    }

    @Override
    public void close() throws IOException {
        AtomicDirectoryPublisher.deleteTree(stagingRoot);
    }
}
