package art.arcane.iris.engine.history;

import art.arcane.iris.core.pack.AtomicDirectoryPublisher;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class GenerationHistory {
    private static final int MAXIMUM_CACHED_BOUNDARIES = 4;
    private static final int MAXIMUM_CACHED_TERRAIN_SIGNATURES = 4;

    private final GenerationHistoryPaths paths;
    private final GenerationPackRepository packs;
    private final GenerationHistoryStore store;
    private final ChunkGenerationOwnership ownership;
    private final GenerationBoundaryStore boundaries;
    private final TerrainBoundarySignatureStore terrainSignatures;
    private final GenerationSemanticIndex semantics;
    private final SavedBiomeStore savedBiomes;
    private final GenerationKernelRegistry kernels;
    private final GenerationAdmission admission;
    private final Map<Long, GenerationBoundary> boundaryCache;
    private final Map<Long, TerrainBoundarySignatureStore.Snapshot> terrainSignatureCache;

    private GenerationHistory(
            GenerationHistoryPaths paths,
            GenerationPackRepository packs,
            GenerationHistoryStore store,
            ChunkGenerationOwnership ownership,
            GenerationKernelRegistry kernels
    ) throws IOException {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.packs = Objects.requireNonNull(packs, "packs");
        this.store = Objects.requireNonNull(store, "store");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.kernels = Objects.requireNonNull(kernels, "kernels");
        this.boundaries = new GenerationBoundaryStore(paths.dimensionRoot());
        this.terrainSignatures = new TerrainBoundarySignatureStore(paths.dimensionRoot());
        this.semantics = GenerationSemanticIndex.loadRequired(paths.dimensionRoot());
        this.savedBiomes = SavedBiomeStore.open(paths.dimensionRoot());
        this.admission = new GenerationAdmission(paths.dimensionRoot());
        this.boundaryCache = boundedCache(MAXIMUM_CACHED_BOUNDARIES);
        this.terrainSignatureCache = boundedCache(MAXIMUM_CACHED_TERRAIN_SIGNATURES);
        validateReferencedState();
    }

    public GenerationAdmission.RuntimeLease retainRuntime() {
        return admission.retainRuntime();
    }

    public SavedBiomeStore savedBiomes() {
        return savedBiomes;
    }

    public static GenerationHistory create(
            Path dimensionRoot,
            Path packSource,
            String packFingerprint,
            long worldSeed,
            GenerationEpoch.DimensionContract dimensionContract,
            GenerationRegistryContract registryContract
    ) throws IOException {
        return create(
                dimensionRoot,
                packSource,
                packFingerprint,
                worldSeed,
                dimensionContract,
                registryContract,
                GenerationKernelRegistry.standard().current(),
                GenerationKernelRegistry.standard()
        );
    }

    static GenerationHistory create(
            Path dimensionRoot,
            Path packSource,
            String packFingerprint,
            long worldSeed,
            GenerationEpoch.DimensionContract dimensionContract,
            GenerationRegistryContract registryContract,
            GenerationKernelRegistry.Version version,
            GenerationKernelRegistry kernels
    ) throws IOException {
        GenerationHistoryPaths paths = GenerationHistoryPaths.forDimension(dimensionRoot);
        requireSafeStatePaths(paths);
        if (Files.exists(paths.manifest(), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(paths.manifest())) {
            throw new FileAlreadyExistsException(paths.manifest().toString());
        }

        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(paths.ownershipRoot());
        if (ownership.explicitChunkCount() != 0) {
            throw new IOException("Generation ownership exists without a generation manifest: "
                    + paths.ownershipRoot());
        }

        GenerationEpoch epoch = epoch(
                packFingerprint,
                worldSeed,
                dimensionContract,
                registryContract,
                version,
                kernels
        );
        requireSourceFingerprint(packSource, epoch.packFingerprint(), epoch.packFingerprintVersion());
        GenerationPackRepository packs = new GenerationPackRepository(paths.dimensionRoot());
        packs.publish(
                epoch.epochId(),
                epoch.packFingerprint(),
                epoch.packFingerprintVersion(),
                packSource
        );
        forceEpochPublication(paths, epoch.epochId());
        GenerationSemanticIndex.initialize(paths.dimensionRoot());
        GenerationHistoryStore store = GenerationHistoryStore.initialize(paths.generationRoot(), epoch);
        return new GenerationHistory(paths, packs, store, ownership, kernels);
    }

    public static GenerationHistory open(Path dimensionRoot) throws IOException {
        return open(dimensionRoot, GenerationKernelRegistry.standard());
    }

    public static GenerationHistory open(Path dimensionRoot, long expectedWorldSeed) throws IOException {
        GenerationHistory history = open(dimensionRoot, GenerationKernelRegistry.standard());
        history.requireWorldSeed(expectedWorldSeed);
        return history;
    }

    static GenerationHistory open(
            Path dimensionRoot,
            GenerationKernelRegistry kernels
    ) throws IOException {
        GenerationHistoryPaths paths = GenerationHistoryPaths.forDimension(dimensionRoot);
        requireSafeStatePaths(paths);
        GenerationPackRepository packs = new GenerationPackRepository(paths.dimensionRoot());
        GenerationHistoryStore store = GenerationHistoryStore.open(paths.generationRoot());
        ChunkGenerationOwnership ownership = ChunkGenerationOwnership.load(paths.ownershipRoot());
        return new GenerationHistory(paths, packs, store, ownership, kernels);
    }

    public static Optional<GenerationHistory> openIfPresent(Path dimensionRoot) throws IOException {
        GenerationHistoryPaths paths = GenerationHistoryPaths.forDimension(dimensionRoot);
        requireSafeStatePaths(paths);
        if (!Files.exists(paths.generationRoot(), LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(paths.generationRoot())) {
            return Optional.empty();
        }
        return Optional.of(open(paths.dimensionRoot()));
    }

    public static Optional<GenerationHistory> openIfPresent(
            Path dimensionRoot,
            long expectedWorldSeed
    ) throws IOException {
        Optional<GenerationHistory> history = openIfPresent(dimensionRoot);
        if (history.isPresent()) {
            history.get().requireWorldSeed(expectedWorldSeed);
        }
        return history;
    }

    public static GenerationHistory adoptLegacyPack(
            Path dimensionRoot,
            String packFingerprint,
            long worldSeed,
            GenerationEpoch.DimensionContract dimensionContract,
            GenerationRegistryContract registryContract
    ) throws IOException {
        GenerationHistoryPaths paths = GenerationHistoryPaths.forDimension(dimensionRoot);
        requireSafeStatePaths(paths);
        GenerationEpoch expectedEpoch = epoch(
                packFingerprint,
                worldSeed,
                dimensionContract,
                registryContract,
                GenerationKernelRegistry.standard().current(),
                GenerationKernelRegistry.standard()
        );
        boolean legacyExists = Files.exists(paths.legacyPackRoot(), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(paths.legacyPackRoot());

        GenerationHistory history;
        if (Files.exists(paths.manifest(), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(paths.manifest())) {
            history = open(paths.dimensionRoot());
        } else {
            if (!legacyExists) {
                throw new NoSuchFileException(paths.legacyPackRoot().toString());
            }
            requireSafeLegacyPack(paths.legacyPackRoot());
            requireSourceFingerprint(
                    paths.legacyPackRoot(),
                    expectedEpoch.packFingerprint(),
                    expectedEpoch.packFingerprintVersion()
            );
            history = create(
                    paths.dimensionRoot(),
                    paths.legacyPackRoot(),
                    expectedEpoch.packFingerprint(),
                    expectedEpoch.worldSeed(),
                    expectedEpoch.dimensionContract(),
                    expectedEpoch.registryContract()
            );
            history = open(paths.dimensionRoot());
        }

        requireInitialAdoptionState(history, expectedEpoch);
        if (legacyExists) {
            requireSafeLegacyPack(paths.legacyPackRoot());
            requireSourceFingerprint(
                    paths.legacyPackRoot(),
                    expectedEpoch.packFingerprint(),
                    expectedEpoch.packFingerprintVersion()
            );
            history.packs.requireExactPack(
                    expectedEpoch.epochId(),
                    expectedEpoch.packFingerprint(),
                    expectedEpoch.packFingerprintVersion()
            );
            forceEpochPublication(paths, expectedEpoch.epochId());
        }
        migrateLegacyMantle(paths, history.activeActivation().activationId());
        if (!legacyExists) {
            return history;
        }
        AtomicDirectoryPublisher.deleteTree(paths.legacyPackRoot());
        forceDirectory(paths.irisRoot());
        return history;
    }

    public synchronized GenerationActivation stageUpdate(
            Path packSource,
            String packFingerprint,
            GenerationEpoch.DimensionContract dimensionContract,
            GenerationRegistryContract registryContract,
            int transitionWidthBlocks
    ) throws IOException {
        return stageUpdate(
                packSource,
                packFingerprint,
                dimensionContract,
                registryContract,
                transitionWidthBlocks,
                kernels.current()
        );
    }

    synchronized GenerationActivation stageUpdate(
            Path packSource,
            String packFingerprint,
            GenerationEpoch.DimensionContract dimensionContract,
            GenerationRegistryContract registryContract,
            int transitionWidthBlocks,
            GenerationKernelRegistry.Version version
    ) throws IOException {
        return stageUpdate(
                packSource,
                packFingerprint,
                GenerationPackFingerprint.CURRENT_VERSION,
                dimensionContract,
                registryContract,
                transitionWidthBlocks,
                version
        );
    }

    private GenerationActivation stageUpdate(
            Path packSource,
            String packFingerprint,
            int packFingerprintVersion,
            GenerationEpoch.DimensionContract dimensionContract,
            GenerationRegistryContract registryContract,
            int transitionWidthBlocks,
            GenerationKernelRegistry.Version version
    ) throws IOException {
        GenerationEpoch candidate = epoch(
                packFingerprint,
                packFingerprintVersion,
                store.activeEpoch().worldSeed(),
                dimensionContract,
                registryContract,
                version,
                kernels
        );
        kernels.requireSupported(
                new GenerationKernelRegistry.Version(
                        candidate.generatorAbi(),
                        candidate.rngVersion(),
                        candidate.seedDerivationVersion()
                ),
                candidate.kernelImplementationFingerprint()
        );
        if (!store.activeEpoch().dimensionContract().hasSameLayout(candidate.dimensionContract())) {
            throw new IllegalArgumentException("Generation update changes the immutable dimension contract.");
        }

        Optional<GenerationActivation> pending = store.pendingActivation();
        if (pending.isPresent()) {
            GenerationActivation activation = pending.get();
            if (!activation.epochId().equals(candidate.epochId())) {
                throw new IllegalStateException("A different generation activation is already pending.");
            }
            packs.requireExactPack(
                    candidate.epochId(),
                    candidate.packFingerprint(),
                    candidate.packFingerprintVersion()
            );
            return activation;
        }

        if (store.activeEpoch().equals(candidate)) {
            requireSourceFingerprint(
                    packSource,
                    candidate.packFingerprint(),
                    candidate.packFingerprintVersion()
            );
            packs.requireExactPack(
                    candidate.epochId(),
                    candidate.packFingerprint(),
                    candidate.packFingerprintVersion()
            );
            return store.activeActivation();
        }

        requireSourceFingerprint(
                packSource,
                candidate.packFingerprint(),
                candidate.packFingerprintVersion()
        );
        packs.publish(
                candidate.epochId(),
                candidate.packFingerprint(),
                candidate.packFingerprintVersion(),
                packSource
        );
        forceEpochPublication(paths, candidate.epochId());
        return store.preparePendingActivation(candidate, transitionWidthBlocks);
    }

    GenerationActivation promotePending(
            Collection<TerrainBoundarySignature> signatures
    ) throws IOException {
        Collection<TerrainBoundarySignature> requiredSignatures = Objects.requireNonNull(
                signatures,
                "signatures"
        );
        return promotePending(boundary -> {
            boundary.requireCompleteTerrainSignatures(requiredSignatures);
            Long2ObjectOpenHashMap<TerrainBoundarySignature> indexed = new Long2ObjectOpenHashMap<>(
                    requiredSignatures.size()
            );
            for (TerrainBoundarySignature signature : requiredSignatures) {
                indexed.put(GenerationBoundary.packChunk(signature.blockX(), signature.blockZ()), signature);
            }
            return (blockX, blockZ) -> indexed.get(GenerationBoundary.packChunk(blockX, blockZ));
        });
    }

    GenerationActivation promotePending(
            BoundarySignatureCapture signatureCapture
    ) throws IOException {
        BoundarySignatureCapture requiredCapture = Objects.requireNonNull(
                signatureCapture,
                "signatureCapture"
        );
        try (GenerationAdmission.CutoverLease ignored = admission.beginStartupCutover()) {
            synchronized (this) {
                return promotePendingLocked(requiredCapture);
            }
        }
    }

    LiveCutover beginLiveCutover() {
        return new LiveCutover(admission.beginCutover());
    }

    final class LiveCutover implements AutoCloseable {
        private final GenerationAdmission.CutoverLease lease;
        private final Thread owner = Thread.currentThread();
        private boolean closed;

        private LiveCutover(GenerationAdmission.CutoverLease lease) {
            this.lease = lease;
        }

        GenerationActivation promote(BoundarySignatureCapture signatureCapture) throws IOException {
            requireOpen();
            synchronized (GenerationHistory.this) {
                return promotePendingLocked(Objects.requireNonNull(signatureCapture, "signature capture"));
            }
        }

        private void requireOpen() {
            if (closed || Thread.currentThread() != owner) {
                throw new IllegalStateException("Generation cutover is closed or belongs to another thread.");
            }
        }

        @Override
        public void close() {
            requireOpen();
            closed = true;
            lease.close();
        }
    }

    private GenerationActivation promotePendingLocked(
            BoundarySignatureCapture signatureCapture
    ) throws IOException {
        WorldChunkInventory inventory = recoverUnstoredClaims();
        validateReferencedState();
        Optional<GenerationActivation> pending = store.pendingActivation();
        if (pending.isEmpty()) {
            return store.activeActivation();
        }

        long outgoingActivationId = store.activeActivation().activationId();
        ownership.assignUnassigned(inventory, outgoingActivationId);
        ownership.persist();
        requireExplicitOwnership(inventory);
        GenerationBoundary boundary = boundaries.publishOwnership(
                pending.get().activationId(),
                ownership
        );
        TerrainBoundarySignatureStore.Snapshot terrainSnapshot;
        try (TerrainBoundarySignatureStore.SignatureSampler sampler = Objects.requireNonNull(
                signatureCapture.capture(boundary), "boundary signature sampler"
        )) {
            terrainSnapshot = terrainSignatures.publish(pending.get().activationId(), boundary, sampler);
        }
        GenerationActivation completed = store.completePendingTransition(
                pending.get().activationId(),
                boundary.identity(),
                terrainSnapshot.identity()
        );
        GenerationActivation activated = store.activatePending(completed.activationId());
        validateOwnershipReferences();
        return activated;
    }

    public GenerationHistoryPaths paths() {
        return paths;
    }

    public synchronized GenerationManifest manifest() {
        return store.manifest();
    }

    public synchronized GenerationActivation activeActivation() {
        return store.activeActivation();
    }

    public synchronized GenerationEpoch activeEpoch() {
        return store.activeEpoch();
    }

    public synchronized GenerationKernelRegistry.Version currentKernelVersion() {
        return kernels.current();
    }

    public synchronized boolean usesCurrentGenerator() throws IOException {
        GenerationEpoch active = store.activeEpoch();
        return active.kernelVersion().equals(kernels.current())
                && active.kernelImplementationFingerprint().equals(
                        kernels.requireSupported(kernels.current()).implementationFingerprint());
    }

    public synchronized GenerationActivation stageCurrentKernel(
            int transitionWidthBlocks
    ) throws IOException {
        GenerationEpoch active = store.activeEpoch();
        return stageUpdate(
                activePackRoot(),
                active.packFingerprint(),
                active.packFingerprintVersion(),
                active.dimensionContract(),
                active.registryContract(),
                transitionWidthBlocks,
                kernels.current()
        );
    }

    public void prepareCurrentGenerator(int transitionWidthBlocks) throws IOException {
        try (GenerationAdmission.CutoverLease ignored = admission.beginStartupCutover()) {
            synchronized (this) {
                if (store.pendingActivation().isEmpty() && usesCurrentGenerator()) {
                    recoverUnstoredClaims();
                    validateReferencedState();
                } else {
                    if (store.pendingActivation().isEmpty()) {
                        stageCurrentKernel(transitionWidthBlocks);
                    }
                    promoteSavedBoundary();
                    if (!usesCurrentGenerator()) {
                        stageCurrentKernel(transitionWidthBlocks);
                        promoteSavedBoundary();
                    }
                }
            }
        }
    }

    private void promoteSavedBoundary() throws IOException {
        GenerationEpoch.DimensionContract contract = activeEpoch().dimensionContract();
        promotePendingLocked(boundary -> new DiskBoundaryCapture(paths.dimensionRoot(), contract.minHeight(), contract.height()));
    }

    private WorldChunkInventory recoverUnstoredClaims() throws IOException {
        long outgoing = store.activeActivation().activationId();
        Set<Long> selected = store.pendingActivation()
                .map(pending -> Set.of(outgoing, pending.activationId()))
                .orElseGet(() -> Set.of(outgoing));
        WorldChunkInventory inventory = WorldChunkInventory.scan(paths.dimensionRoot()).filter((chunkX, chunkZ) -> {
            if (ownership.isExplicitlyAssigned(chunkX, chunkZ)
                    && !selected.contains(ownership.resolve(chunkX, chunkZ, outgoing))) {
                return true;
            }
            return SavedTerrainChunk.hasTerrain(SavedTerrainChunk.readStatus(paths.dimensionRoot(), chunkX, chunkZ));
        });
        semantics.discardUnstoredClaims(inventory, selected);
        savedBiomes.discardUnstoredClaims(inventory, selected);
        ownership.discardUnstoredClaims(inventory, selected);
        return inventory;
    }

    public synchronized Optional<GenerationActivation> pendingActivation() {
        return store.pendingActivation();
    }

    public synchronized Path activePackRoot() throws IOException {
        GenerationEpoch epoch = store.activeEpoch();
        return packs.requireExactPack(
                epoch.epochId(),
                epoch.packFingerprint(),
                epoch.packFingerprintVersion()
        );
    }

    public synchronized Path packRoot(long activationId) throws IOException {
        GenerationActivation activation = requireActivation(activationId);
        GenerationEpoch epoch = requireEpoch(activation.epochId());
        return packs.requireExactPack(
                epoch.epochId(),
                epoch.packFingerprint(),
                epoch.packFingerprintVersion()
        );
    }

    public GenerationStage openStage(int chunkX, int chunkZ) throws IOException {
        GenerationAdmission.StageLease lease = admission.enterStage();
        try {
            GenerationManifest manifest = store.manifest();
            GenerationActivation activation = manifest.activeActivation();
            GenerationEpoch epoch = manifest.activeEpoch();
            return new GenerationStage(
                    this,
                    chunkX,
                    chunkZ,
                    activation,
                    epoch,
                    paths.packRoot(epoch.epochId()),
                    lease
            );
        } catch (Throwable failure) {
            lease.close();
            if (failure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw new IOException("Unable to open Iris generation stage.", failure);
        }
    }

    public synchronized boolean claimGeneratedSemantics(
            GenerationStage stage,
            ChunkGenerationSemantics update
    ) throws IOException {
        GenerationStage requiredStage = Objects.requireNonNull(stage, "generation stage");
        ChunkGenerationSemantics requiredUpdate = Objects.requireNonNull(update, "update");
        requiredStage.requireOpen(this);
        if (!requiredUpdate.sealed()) {
            throw new IllegalArgumentException("Generated semantics must be sealed.");
        }
        if (requiredUpdate.chunkX() != requiredStage.chunkX()
                || requiredUpdate.chunkZ() != requiredStage.chunkZ()) {
            throw new IllegalArgumentException("Generated semantics coordinates do not match the open stage.");
        }
        if (requiredUpdate.activationId() != requiredStage.activation().activationId()) {
            throw new IllegalArgumentException("Generated semantics activation does not match the open stage.");
        }
        return semantics.claimAndPersist(requiredUpdate);
    }

    public synchronized void forEachRecordedSemantic(GenerationSemanticIndex.RecordConsumer consumer) throws IOException {
        semantics.forEachRecord(consumer);
    }

    public synchronized Optional<ChunkGenerationSemantics> semantics(int chunkX, int chunkZ) {
        return semantics.get(chunkX, chunkZ);
    }

    public synchronized Optional<GenerationSemanticIndex.Match> findRecorded(
            GenerationSemanticIndex.Query query
    ) {
        return semantics.findNearest(query, ChunkGenerationSemantics::sealed);
    }

    public synchronized Optional<GenerationSemanticIndex.RiverMatch> findRecordedRiver(
            GenerationSemanticIndex.RiverQuery query
    ) {
        return semantics.findNearestRiver(query, ChunkGenerationSemantics::sealed);
    }

    public synchronized boolean isActiveUnowned(int chunkX, int chunkZ) {
        return !ownership.isExplicitlyAssigned(chunkX, chunkZ);
    }

    public synchronized boolean isHistoricallyOwned(int chunkX, int chunkZ) {
        return ownership.isExplicitlyAssigned(chunkX, chunkZ);
    }

    public synchronized boolean hasHistoricalFallbackInSquare(int centerX, int centerZ, int radius) {
        return ownership.anyMatchingInSquare(centerX, centerZ, radius,
                (chunkX, chunkZ, activation) -> semantics.get(chunkX, chunkZ)
                        .filter(ChunkGenerationSemantics::sealed).isEmpty());
    }

    public synchronized void requireWorldSeed(long expectedWorldSeed) throws IOException {
        long recordedWorldSeed = store.activeEpoch().worldSeed();
        if (recordedWorldSeed != expectedWorldSeed) {
            throw new IOException("Iris generation history seed mismatch: expected "
                    + recordedWorldSeed + " but the platform supplied " + expectedWorldSeed + ".");
        }
    }

    public synchronized void requireRegistryDefinitions(GenerationRegistryContract available) throws IOException {
        GenerationRegistryContract requiredAvailable = Objects.requireNonNull(available, "available");
        for (GenerationEpoch epoch : store.manifest().epochs()) {
            epoch.registryContract().requireDefinitionsAvailableIn(requiredAvailable);
        }
    }

    public synchronized GenerationActivation resolveActivation(int chunkX, int chunkZ) {
        long activationId = ownership.resolve(
                chunkX,
                chunkZ,
                store.activeActivation().activationId()
        );
        return requireActivation(activationId);
    }

    public synchronized GenerationEpoch resolveEpoch(int chunkX, int chunkZ) {
        return requireEpoch(resolveActivation(chunkX, chunkZ).epochId());
    }

    public synchronized GenerationBoundary boundary(long activationId) throws IOException {
        GenerationActivation activation = requireActivation(activationId);
        if (activation.parentActivationId() == null) {
            throw new IllegalArgumentException("The initial generation activation has no transition boundary.");
        }
        GenerationBoundary cached = boundaryCache.get(activationId);
        if (cached != null) {
            return cached;
        }
        GenerationBoundary loaded = boundaries.load(activationId);
        boundaryCache.put(activationId, loaded);
        return loaded;
    }

    public synchronized TerrainBoundarySignatureStore.Snapshot terrainSignatures(
            long activationId
    ) throws IOException {
        GenerationActivation activation = requireActivation(activationId);
        if (activation.isInitial()) {
            throw new IllegalArgumentException("The initial generation activation has no terrain signatures.");
        }
        TerrainBoundarySignatureStore.Snapshot snapshot = terrainSignatureCache.get(activationId);
        if (snapshot == null) {
            snapshot = terrainSignatures.load(activationId);
            terrainSignatureCache.put(activationId, snapshot);
        }
        requireTransitionSnapshotIdentities(activation, boundary(activationId), snapshot);
        return snapshot;
    }

    public synchronized TransitionGenerationPlan transitionPlan(long activationId) throws IOException {
        GenerationActivation activation = requireActivation(activationId);
        if (activation.isInitial()) {
            throw new IllegalArgumentException("The initial generation activation has no transition plan.");
        }
        GenerationTransition transition = Objects.requireNonNull(
                activation.transition(),
                "activation transition"
        );
        if (!transition.isComplete()) {
            throw new IllegalStateException("Generation transition is not complete for activation "
                    + activationId + ".");
        }
        GenerationActivation parent = requireActivation(activation.parentActivationId());
        GenerationBoundary boundary = boundary(activationId);
        TerrainBoundarySignatureStore.Snapshot terrainSnapshot = terrainSignatures(activationId);
        requireTransitionSnapshotIdentities(activation, boundary, terrainSnapshot);
        return new TransitionGenerationPlan(
                new TransitionGenerationPlan.Specification(
                        activation.activationId(),
                        requireEpoch(parent.epochId()).epochId(),
                        requireEpoch(activation.epochId()).epochId(),
                        transition.algorithmVersion(),
                        transition.widthBlocks(),
                        transition.boundaryIdentity(),
                        transition.terrainSignatureIdentity()
                ),
                boundary,
                terrainSnapshot
        );
    }

    public synchronized int explicitChunkCount() {
        return ownership.explicitChunkCount();
    }

    private void validateReferencedState() throws IOException {
        validateRuntimeVersions();
        GenerationEpoch active = store.activeEpoch();
        packs.requireExactPack(active.epochId(), active.packFingerprint(), active.packFingerprintVersion());
        if (store.pendingEpoch().isPresent()) {
            GenerationEpoch pending = store.pendingEpoch().orElseThrow();
            packs.requireExactPack(pending.epochId(), pending.packFingerprint(), pending.packFingerprintVersion());
        }
        for (GenerationActivation activation : store.manifest().activations()) {
            requireSafeDirectoryIfPresent(paths.activationRoot(activation.activationId()));
            requireSafeDirectoryIfPresent(paths.activationMantleRoot(activation.activationId()));
            if (activation.parentActivationId() != null
                    && activation.activationId() <= store.activeActivation().activationId()) {
                GenerationBoundary boundary = boundary(activation.activationId());
                TerrainBoundarySignatureStore.Snapshot terrainSnapshot = terrainSignatures(
                        activation.activationId()
                );
                requireTransitionSnapshotIdentities(activation, boundary, terrainSnapshot);
            }
        }
        validateOwnershipReferences();
        validateSemanticReferences();
    }

    private static void requireTransitionSnapshotIdentities(
            GenerationActivation activation,
            GenerationBoundary boundary,
            TerrainBoundarySignatureStore.Snapshot terrainSnapshot
    ) throws IOException {
        GenerationTransition transition = activation.transition();
        if (transition == null || !transition.isComplete()) {
            throw new IOException("Generation activation " + activation.activationId()
                    + " has an incomplete transition recipe.");
        }
        if (!transition.boundaryIdentity().equals(boundary.identity())) {
            throw new IOException("Generation boundary identity does not match activation "
                    + activation.activationId() + ".");
        }
        if (!transition.terrainSignatureIdentity().equals(terrainSnapshot.identity())) {
            throw new IOException("Terrain signature identity does not match activation "
                    + activation.activationId() + ".");
        }
    }

    private void validateRuntimeVersions() throws IOException {
        for (GenerationEpoch epoch : store.manifest().epochs()) {
            GenerationPackFingerprint.requireSupported(epoch.packFingerprintVersion());
        }
    }

    private void validateOwnershipReferences() throws IOException {
        long activeActivationId = store.activeActivation().activationId();
        GenerationActivation active = store.activeActivation();
        GenerationBoundary expectedBoundary = active.isInitial() ? null : boundary(activeActivationId);
        boolean pendingCutover = store.pendingActivation().isPresent();
        int[] assignedCount = new int[1];
        ownership.forEachAssignment((chunkX, chunkZ, activationId) -> {
            assignedCount[0] = Math.addExact(assignedCount[0], 1);
            if (store.activation(activationId).isEmpty()) {
                throw new IOException("Chunk " + chunkX + "," + chunkZ
                        + " references missing generation activation " + activationId + ".");
            }
            if (activationId > activeActivationId) {
                throw new IOException("Chunk " + chunkX + "," + chunkZ
                        + " references unpublished generation activation " + activationId + ".");
            }
            if (expectedBoundary != null && expectedBoundary.isHistoricalChunk(chunkX, chunkZ)) {
                return;
            }
            if (!pendingCutover) {
                throw new IOException("Generation ownership contains chunk " + chunkX + "," + chunkZ
                        + " outside the active immutable boundary.");
            }
            if (activationId != activeActivationId) {
                throw new IOException("Pending generation cutover contains an invalid outgoing claim for chunk "
                        + chunkX + "," + chunkZ + ": expected activation "
                        + activeActivationId + " but found " + activationId + ".");
            }
        });

        int expectedCount = expectedBoundary == null ? 0 : expectedBoundary.historicalChunkCount();
        if (!pendingCutover && assignedCount[0] != expectedCount) {
            throw ownershipCoverageMismatch(expectedCount, assignedCount[0]);
        }
        if (expectedBoundary == null) {
            return;
        }
        expectedBoundary.forEachHistoricalChunk((chunkX, chunkZ) -> {
            if (!ownership.isExplicitlyAssigned(chunkX, chunkZ)) {
                throw new IOException("Generation ownership is missing historical chunk "
                        + chunkX + "," + chunkZ + ".");
            }
        });
    }

    private static IOException ownershipCoverageMismatch(int expectedCount, int actualCount) {
        return new IOException("Generation ownership coverage mismatch: expected "
                + expectedCount + " historical chunks but found " + actualCount + ".");
    }

    private void validateSemanticReferences() throws IOException {
        semantics.forEachRecord(record -> {
            if (store.activation(record.activationId()).isEmpty()) {
                throw new IOException("Chunk " + record.chunkX() + "," + record.chunkZ()
                        + " records semantics for missing generation activation "
                        + record.activationId() + ".");
            }
            GenerationActivation expected = resolveActivation(record.chunkX(), record.chunkZ());
            if (record.activationId() != expected.activationId()) {
                throw new IOException("Chunk " + record.chunkX() + "," + record.chunkZ()
                        + " records semantics for activation " + record.activationId()
                        + " but ownership resolves activation " + expected.activationId() + ".");
            }
        });
    }

    private void requireExplicitOwnership(WorldChunkInventory inventory) throws IOException {
        boolean[] missing = new boolean[1];
        long[] firstMissing = new long[1];
        inventory.forEach((chunkX, chunkZ) -> {
            if (!missing[0] && !ownership.isExplicitlyAssigned(chunkX, chunkZ)) {
                missing[0] = true;
                firstMissing[0] = ChunkGenerationOwnership.packChunk(chunkX, chunkZ);
            }
        });
        if (!missing[0]) {
            return;
        }
        long first = firstMissing[0];
        throw new IOException("Generated chunk " + ChunkGenerationOwnership.chunkX(first)
                + "," + ChunkGenerationOwnership.chunkZ(first)
                + " was not durably assigned before generation activation promotion.");
    }

    private GenerationActivation requireActivation(long activationId) {
        return store.activation(activationId).orElseThrow(() -> new IllegalStateException(
                "Generation ownership references missing activation " + activationId + "."
        ));
    }

    private GenerationEpoch requireEpoch(String epochId) {
        return store.epoch(epochId).orElseThrow(() -> new IllegalStateException(
                "Generation activation references missing epoch " + epochId + "."
        ));
    }

    private static GenerationEpoch epoch(
            String packFingerprint,
            long worldSeed,
            GenerationEpoch.DimensionContract dimensionContract,
            GenerationRegistryContract registryContract,
            GenerationKernelRegistry.Version version,
            GenerationKernelRegistry kernels
    ) throws IOException {
        return epoch(
                packFingerprint,
                GenerationPackFingerprint.CURRENT_VERSION,
                worldSeed,
                dimensionContract,
                registryContract,
                version,
                kernels
        );
    }

    private static GenerationEpoch epoch(
            String packFingerprint,
            int packFingerprintVersion,
            long worldSeed,
            GenerationEpoch.DimensionContract dimensionContract,
            GenerationRegistryContract registryContract,
            GenerationKernelRegistry.Version version,
            GenerationKernelRegistry kernels
    ) throws IOException {
        GenerationKernelRegistry.Version requiredVersion = Objects.requireNonNull(version, "version");
        String implementationFingerprint = Objects.requireNonNull(kernels, "kernels")
                .select(requiredVersion)
                .implementationFingerprint();
        return GenerationEpoch.create(new GenerationEpoch.Spec(
                packFingerprint,
                packFingerprintVersion,
                worldSeed,
                requiredVersion.seedDerivationVersion(),
                requiredVersion.generatorAbi(),
                requiredVersion.rngVersion(),
                implementationFingerprint,
                Objects.requireNonNull(dimensionContract, "dimensionContract"),
                Objects.requireNonNull(registryContract, "registryContract")
        ));
    }

    private static void requireInitialAdoptionState(
            GenerationHistory history,
            GenerationEpoch expectedEpoch
    ) throws IOException {
        GenerationManifest manifest = history.store.manifest();
        if (manifest.epochs().size() != 1
                || manifest.activations().size() != 1
                || manifest.pendingActivation().isPresent()
                || !manifest.activeEpoch().equals(expectedEpoch)) {
            throw new IOException("Legacy pack adoption does not match the initialized generation history.");
        }
    }

    private static void requireSourceFingerprint(
            Path source,
            String expected,
            int packFingerprintVersion
    ) throws IOException {
        Path requiredSource = Objects.requireNonNull(source, "packSource")
                .toAbsolutePath()
                .normalize();
        String actual = GenerationPackFingerprint.compute(requiredSource, packFingerprintVersion);
        if (!expected.equals(actual)) {
            throw new IOException("Generation pack fingerprint mismatch at " + requiredSource
                    + ": expected " + expected + " but found " + actual + ".");
        }
    }

    private static void requireSafeLegacyPack(Path legacyPack) throws IOException {
        if (!Files.exists(legacyPack, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException(legacyPack.toString());
        }
        BasicFileAttributes attributes = Files.readAttributes(
                legacyPack,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException("Legacy Iris pack is not a safe directory: " + legacyPack);
        }
    }

    private static void requireSafeStatePaths(GenerationHistoryPaths paths) throws IOException {
        requireSafeDirectoryIfPresent(paths.dimensionRoot());
        requireSafeDirectoryIfPresent(paths.irisRoot());
        requireSafeDirectoryIfPresent(paths.generationRoot());
        requireSafeDirectoryIfPresent(paths.epochsRoot());
        requireSafeDirectoryIfPresent(paths.activationsRoot());
        requireSafeDirectoryIfPresent(paths.ownershipRoot());
    }

    private static void migrateLegacyMantle(
            GenerationHistoryPaths paths,
            long activationId
    ) throws IOException {
        Path source = paths.legacyMantleRoot();
        Path target = paths.activationMantleRoot(activationId);
        boolean sourceExists = Files.exists(source, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(source);
        boolean targetExists = Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(target);
        if (!sourceExists && !targetExists) {
            return;
        }

        requireSafeMantleRootIfPresent(source, "Legacy");
        requireSafeMantleRootIfPresent(target, "Activation");
        if (sourceExists && targetExists) {
            String conflict = Files.isSameFile(source, target) ? "alias" : "conflict";
            throw new IOException("Legacy and activation Iris mantle roots " + conflict + ": "
                    + source + " and " + target + ".");
        }
        if (targetExists) {
            forceMantleMigration(paths, target.getParent());
            return;
        }

        Path activationRoot = target.getParent();
        Files.createDirectories(activationRoot);
        requireSafeDirectoryIfPresent(paths.activationsRoot());
        requireSafeDirectoryIfPresent(activationRoot);
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Legacy Iris mantle cannot be migrated atomically from "
                    + source + " to " + target + ".", exception);
        }
        forceMantleMigration(paths, activationRoot);
    }

    private static void requireSafeMantleRootIfPresent(Path root, String label) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root)) {
            return;
        }
        BasicFileAttributes attributes = Files.readAttributes(
                root,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException(label + " Iris mantle root is not a safe directory: " + root);
        }
    }

    private static void forceMantleMigration(
            GenerationHistoryPaths paths,
            Path activationRoot
    ) throws IOException {
        forceDirectory(activationRoot);
        forceDirectory(paths.activationsRoot());
        forceDirectory(paths.generationRoot());
        forceDirectory(paths.dimensionRoot());
    }

    private static void requireSafeDirectoryIfPresent(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(directory)) {
            return;
        }
        BasicFileAttributes attributes = Files.readAttributes(
                directory,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException("Generation history path is not a safe directory: " + directory);
        }
    }

    private static void forceEpochPublication(
            GenerationHistoryPaths paths,
            String epochId
    ) throws IOException {
        Path epochRoot = paths.epochRoot(epochId);
        if (!Files.isDirectory(epochRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(epochRoot)) {
            throw new IOException("Generation epoch is not a safe directory: " + epochRoot);
        }
        if (File.separatorChar != '\\') {
            List<Path> directories;
            try (Stream<Path> entries = Files.walk(epochRoot)) {
                directories = entries
                        .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                        .sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                        .toList();
            }
            for (Path directory : directories) {
                forceDirectory(directory);
            }
            forceDirectory(paths.epochsRoot());
            forceDirectory(paths.generationRoot());
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        if (File.separatorChar == '\\') {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException exception) {
            throw new IOException("Generation history directory cannot be durability-synced: "
                    + directory, exception);
        }
    }

    private static <K, V> Map<K, V> boundedCache(int maximumSize) {
        return new LinkedHashMap<>(maximumSize, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maximumSize;
            }
        };
    }

    public static final class GenerationStage implements AutoCloseable {
        private final GenerationHistory owner;
        private final int chunkX;
        private final int chunkZ;
        private final GenerationActivation activation;
        private final GenerationEpoch epoch;
        private final Path packRoot;
        private final GenerationAdmission.StageLease lease;
        private boolean closed;

        private GenerationStage(
                GenerationHistory owner,
                int chunkX,
                int chunkZ,
                GenerationActivation activation,
                GenerationEpoch epoch,
                Path packRoot,
                GenerationAdmission.StageLease lease
        ) {
            this.owner = owner;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.activation = activation;
            this.epoch = epoch;
            this.packRoot = packRoot;
            this.lease = lease;
        }

        public int chunkX() {
            return chunkX;
        }

        public int chunkZ() {
            return chunkZ;
        }

        public GenerationActivation activation() {
            return activation;
        }

        public GenerationEpoch epoch() {
            return epoch;
        }

        public Path packRoot() {
            return packRoot;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            lease.close();
        }

        private synchronized void requireOpen(GenerationHistory expectedOwner) {
            if (owner != expectedOwner) {
                throw new IllegalArgumentException("Generation stage belongs to a different history.");
            }
            if (closed) {
                throw new IllegalStateException("Generation stage is closed.");
            }
        }
    }

    @FunctionalInterface
    public interface BoundarySignatureCapture {
        TerrainBoundarySignatureStore.SignatureSampler capture(GenerationBoundary boundary) throws IOException;
    }
}
