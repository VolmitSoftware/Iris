package art.arcane.iris.world.history;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.IrisEngineMantle;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.generation.runtime.GenerationTransitionGate;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class GenerationHistoryRuntimeRouter implements AutoCloseable {
    public static final int DEFAULT_TRANSITION_WIDTH_BLOCKS = 256;

    private final IrisEngine engine;
    private final GenerationHistory history;
    private final SavedBiomeRuntime biomes;
    private final GenerationBoundarySignatureSampler signatureSampler;
    private final ActivationRuntimeFactory runtimeFactory;
    private final LinkedHashMap<Long, RuntimeCacheEntry> bindings;
    private final Map<Long, RuntimeRetirement> retiringBindings;
    private final Map<Long, SavedMantleEntry> savedMantles = new LinkedHashMap<>();
    private final ReentrantLock stateLock;
    private final Condition inactive;
    private final ThreadLocal<Integer> operationDepth;
    private final ThreadLocal<RuntimeRoute> scopedRoute;
    private int activeOperations;
    private int activeLoads;
    private boolean closed;
    private boolean closeComplete;
    private boolean closeCleanupInProgress;
    private Throwable closeFailure;

    private GenerationHistoryRuntimeRouter(
            IrisEngine engine,
            GenerationHistory history,
            GenerationBoundarySignatureSampler signatureSampler,
            ActivationRuntimeFactory runtimeFactory
    ) throws IOException {
        this.engine = Objects.requireNonNull(engine, "Iris engine");
        this.history = Objects.requireNonNull(history, "generation history");
        this.signatureSampler = Objects.requireNonNull(signatureSampler, "boundary signature sampler");
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "activation runtime factory");
        this.bindings = new LinkedHashMap<>();
        this.retiringBindings = new LinkedHashMap<>();
        this.stateLock = new ReentrantLock();
        this.inactive = stateLock.newCondition();
        this.operationDepth = ThreadLocal.withInitial(() -> 0);
        this.scopedRoute = new ThreadLocal<>();
        this.biomes = new SavedBiomeRuntime(engine, history);

        GenerationActivation active = history.activeActivation();
        GenerationEpoch epoch = requireEpoch(active);
        IrisEngine.GenerationRuntimeBinding base = engine.getActiveGenerationRuntimeBinding();
        runtimeFactory.validateBase(engine, history, active, epoch, base);
        if (history.usesCurrentGenerator()) {
            requireBindingContract(active, epoch, base);
        }
        RuntimeCacheEntry baseEntry = new RuntimeCacheEntry(active.activationId());
        baseEntry.binding = base;
        baseEntry.defaultRuntime = true;
        bindings.put(active.activationId(), baseEntry);
        try {
            engine.attachGenerationHistoryRuntimeRouter(this);
        } catch (Throwable failure) {
            stateLock.lock();
            try {
                bindings.clear();
                closed = true;
                closeComplete = true;
            } finally {
                stateLock.unlock();
            }
            throw propagate(failure, "Unable to attach the generation-history runtime router.");
        }
    }

    public SavedBiomeRuntime biomes() {
        return biomes;
    }

    public static GenerationHistoryRuntimeRouter attach(
            IrisEngine engine,
            GenerationHistory history,
            GenerationBoundarySignatureSampler signatureSampler
    ) throws IOException {
        return attach(engine, history, signatureSampler, new DefaultActivationRuntimeFactory());
    }

    public static GenerationHistoryRuntimeRouter attachAndPromotePending(
            IrisEngine engine,
            GenerationHistory history,
            GenerationBoundarySignatureSampler signatureSampler
    ) throws IOException {
        return attachAndPromotePending(
                engine,
                history,
                signatureSampler,
                DEFAULT_TRANSITION_WIDTH_BLOCKS
        );
    }

    public static GenerationHistoryRuntimeRouter attachAndPromotePending(
            IrisEngine engine,
            GenerationHistory history,
            GenerationBoundarySignatureSampler signatureSampler,
            int transitionWidthBlocks
    ) throws IOException {
        return attachAndPromotePending(
                engine,
                history,
                signatureSampler,
                transitionWidthBlocks,
                new DefaultActivationRuntimeFactory()
        );
    }

    static GenerationHistoryRuntimeRouter attachAndPromotePending(
            IrisEngine engine,
            GenerationHistory history,
            GenerationBoundarySignatureSampler signatureSampler,
            int transitionWidthBlocks,
            ActivationRuntimeFactory runtimeFactory
    ) throws IOException {
        GenerationHistoryRuntimeRouter router = attach(
                engine,
                history,
                signatureSampler,
                runtimeFactory
        );
        try {
            router.promotePendingAndReconcileCurrentKernel(transitionWidthBlocks);
            return router;
        } catch (Throwable failure) {
            try {
                router.close();
            } catch (Throwable closeFailure) {
                if (failure != closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw propagate(failure, "Unable to promote the pending generation activation.");
        }
    }

    static GenerationHistoryRuntimeRouter attach(
            IrisEngine engine,
            GenerationHistory history,
            GenerationBoundarySignatureSampler signatureSampler,
            ActivationRuntimeFactory runtimeFactory
    ) throws IOException {
        return new GenerationHistoryRuntimeRouter(
                engine,
                history,
                signatureSampler,
                runtimeFactory
        );
    }

    public IrisEngine engine() {
        return engine;
    }

    public GenerationHistory history() {
        return history;
    }

    public StudioCutover beginStudioCutover(long timeoutMillis) {
        if (!engine.isStudio()) {
            throw new IllegalStateException("Live generation cutovers require a Studio world.");
        }
        if (scopedRoute.get() != null || operationDepth.get() > 0) {
            throw new IllegalStateException("Cannot begin a Studio cutover inside another generation operation.");
        }
        enterOperation();
        GenerationTransitionGate.Transition transition = null;
        try {
            transition = engine.getGenerationSessions().transitionGate().beginTransition(timeoutMillis);
            return new StudioCutover(history.beginLiveCutover(), transition);
        } catch (Throwable failure) {
            if (transition != null) {
                transition.close();
            }
            leaveOperation();
            throw failure;
        }
    }

    public final class StudioCutover implements AutoCloseable {
        private final GenerationHistory.LiveCutover cutover;
        private final GenerationTransitionGate.Transition transition;
        private final Thread owner = Thread.currentThread();
        private boolean closed;

        private StudioCutover(GenerationHistory.LiveCutover cutover, GenerationTransitionGate.Transition transition) {
            this.cutover = cutover;
            this.transition = transition;
        }

        public GenerationActivation promotePending() throws IOException {
            requireOpen();
            GenerationActivation activated = cutover.promote(GenerationHistoryRuntimeRouter.this::captureBoundarySignatures);
            try (RuntimeLease lease = acquireRuntime(activated, requireEpoch(activated))) {
                engine.setDefaultGenerationRuntime(lease.binding());
                retireBindings(pinDefault(lease.entry));
            }
            return activated;
        }

        private void requireOpen() {
            if (closed || Thread.currentThread() != owner) {
                throw new IllegalStateException("Studio cutover is closed or belongs to another thread.");
            }
        }

        @Override
        public void close() {
            requireOpen();
            closed = true;
            try {
                cutover.close();
            } finally {
                try {
                    transition.close();
                } finally {
                    leaveOperation();
                }
            }
        }
    }

    public Optional<RuntimeOwnership> currentRuntimeOwnership() {
        RuntimeRoute current = scopedRoute.get();
        return current == null ? Optional.empty() : Optional.of(current.ownership);
    }

    public RuntimeRoute openRoute(int chunkX, int chunkZ) throws IOException {
        GenerationTransitionGate.Participation participation = engine.getGenerationSessions().transitionGate().enter();
        boolean operation = false;
        try {
            enterRouteOperation();
            operation = true;
            GenerationHistory.GenerationStage stage = history.openStage(chunkX, chunkZ);
            try {
                RuntimeLease lease = acquireRuntime(stage.activation(), stage.epoch());
                return new RuntimeRoute(this, stage, lease, participation);
            } catch (Throwable failure) {
                stage.close();
                throw failure;
            }
        } catch (Throwable failure) {
            if (operation) {
                leaveRouteOperation();
            }
            participation.close();
            throw propagate(failure, "Unable to open an Iris generation-history stage.");
        }
    }

    public SavedChunkMantle openSavedChunkMantle(int chunkX, int chunkZ) throws IOException {
        GenerationTransitionGate.Participation participation = engine.getGenerationSessions().transitionGate().enter();
        boolean operation = false;
        try {
            enterRouteOperation();
            operation = true;
            GenerationHistory.GenerationStage stage = history.openSavedChunkStage(chunkX, chunkZ);
            try {
                SavedMantleAccess access = acquireSavedMantle(stage.activation(), stage.epoch());
                return new SavedChunkMantle(this, new SavedChunkResources(stage, participation, access));
            } catch (Throwable failure) {
                stage.close();
                throw failure;
            }
        } catch (Throwable failure) {
            if (operation) {
                leaveRouteOperation();
            }
            participation.close();
            throw propagate(failure, "Unable to open saved Iris chunk mantle.");
        }
    }

    public RuntimeStage openStage(int chunkX, int chunkZ) throws IOException {
        RuntimeRoute route = openRoute(chunkX, chunkZ);
        try {
            RuntimeRoute.RuntimeScope runtimeScope = route.openRuntimeScope();
            return new RuntimeStage(route, runtimeScope, Thread.currentThread());
        } catch (Throwable failure) {
            route.close();
            throw propagate(failure, "Unable to scope an Iris generation-history stage.");
        }
    }

    public CoordinateScope openCoordinateScope(int blockX, int blockZ) throws IOException {
        int chunkX = Math.floorDiv(blockX, GenerationBoundary.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(blockZ, GenerationBoundary.CHUNK_SIZE);
        RuntimeRoute current = scopedRoute.get();
        if (current != null) {
            if (current.chunkX() == chunkX && current.chunkZ() == chunkZ) {
                return new CoordinateScope(blockX, blockZ, current, null);
            }
            RuntimeStage stage = openStage(chunkX, chunkZ);
            return new CoordinateScope(blockX, blockZ, stage.route, stage);
        }
        if (engine.hasGenerationRuntimeScope()) {
            return new CoordinateScope(blockX, blockZ, null, null);
        }
        RuntimeStage stage = openStage(chunkX, chunkZ);
        return new CoordinateScope(blockX, blockZ, stage.route, stage);
    }

    public void recordNaturalTerrain(SavedTerrainChunk terrain) {
        SavedTerrainChunk captured = Objects.requireNonNull(terrain, "natural terrain");
        RuntimeRoute route = scopedRoute.get();
        if (route == null || route.chunkX() != captured.chunkX() || route.chunkZ() != captured.chunkZ()) {
            throw new IllegalStateException("Natural terrain must be captured inside its chunk generation route.");
        }
        route.naturalTerrain = captured;
    }

    public void recordFloatingBiomes(int chunkX, int chunkZ, FloatingBiomeOverlay overlay) {
        RuntimeRoute route = scopedRoute.get();
        if (route == null || route.chunkX() != chunkX || route.chunkZ() != chunkZ) {
            throw new IllegalStateException("Floating biomes must be captured inside their chunk generation route.");
        }
        route.floatingBiomes = overlay;
    }

    public void preloadActiveRuntimes() throws IOException {
        enterOperation();
        try {
            GenerationActivation active = history.activeActivation();
            try (RuntimeLease ignored = acquireRuntime(active, requireEpoch(active))) {
            }
        } finally {
            leaveOperation();
        }
    }

    private GenerationActivation promotePending() throws IOException {
        enterOperation();
        try {
            GenerationActivation activated = history.promotePending(this::captureBoundarySignatures);
            if (!history.usesCurrentGenerator()) {
                return activated;
            }
            GenerationEpoch epoch = requireEpoch(activated);
            try (RuntimeLease lease = acquireRuntime(activated, epoch)) {
                engine.setDefaultGenerationRuntime(lease.binding());
                retireBindings(pinDefault(lease.entry));
                return activated;
            }
        } finally {
            leaveOperation();
        }
    }

    private GenerationActivation promotePendingAndReconcileCurrentKernel(
            int transitionWidthBlocks
    ) throws IOException {
        if (history.pendingActivation().isEmpty() && !history.usesCurrentGenerator()) {
            history.stageCurrentKernel(transitionWidthBlocks);
        }
        GenerationActivation activated = promotePending();
        if (!history.usesCurrentGenerator()) {
            history.stageCurrentKernel(transitionWidthBlocks);
            activated = promotePending();
        }
        return activated;
    }

    public boolean isClosed() {
        stateLock.lock();
        try {
            return closed;
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public void close() {
        if (operationDepth.get() > 0) {
            throw new IllegalStateException("Cannot close the generation-history router from an active route.");
        }
        List<RuntimeRetirement> retired = null;
        List<SavedMantleEntry> saved = null;
        stateLock.lock();
        try {
            closed = true;
            while (true) {
                if (closeComplete) {
                    rethrowCloseFailure(closeFailure);
                    return;
                }
                if (closeCleanupInProgress || activeOperations > 0 || activeLoads > 0) {
                    inactive.awaitUninterruptibly();
                    continue;
                }
                closeCleanupInProgress = true;
                retired = new ArrayList<>(bindings.size());
                for (RuntimeCacheEntry entry : bindings.values()) {
                    if (!entry.defaultRuntime && entry.binding != null) {
                        retired.add(scheduleRetirementLocked(entry));
                    }
                }
                bindings.clear();
                saved = new ArrayList<>(savedMantles.values());
                break;
            }
        } finally {
            stateLock.unlock();
        }

        Throwable failure = null;
        try {
            engine.detachGenerationHistoryRuntimeRouter(this);
        } catch (Throwable detachFailure) {
            failure = detachFailure;
        }
        try {
            biomes.close();
        } catch (Throwable biomeFailure) {
            failure = appendFailure(failure, biomeFailure);
        }
        try {
            retireBindings(retired);
        } catch (Throwable retirementFailure) {
            failure = appendFailure(failure, retirementFailure);
        }
        for (SavedMantleEntry entry : saved) {
            try {
                closeSavedMantle(entry);
            } catch (Throwable closeFailure) {
                failure = appendFailure(failure, closeFailure);
            }
        }

        stateLock.lock();
        try {
            closeFailure = failure;
            retiringBindings.clear();
            closeCleanupInProgress = false;
            closeComplete = true;
            inactive.signalAll();
        } finally {
            stateLock.unlock();
        }
        rethrowCloseFailure(failure);
    }

    private TerrainBoundarySignatureStore.SignatureSampler captureBoundarySignatures(
            GenerationBoundary boundary
    ) throws IOException {
        return signatureSampler.open(engine);
    }

    private SavedMantleAccess acquireSavedMantle(GenerationActivation activation, GenerationEpoch epoch) throws IOException {
        SavedMantleEntry saved = null;
        RuntimeCacheEntry runtime;
        boolean loader = false;
        stateLock.lock();
        try {
            while (true) {
                awaitRetirementLocked(activation.activationId());
                saved = savedMantles.get(activation.activationId());
                if (saved == null || !saved.retiring) {
                    break;
                }
                awaitSavedMantleChange(saved);
            }
            runtime = bindings.get(activation.activationId());
            if (runtime != null) {
                runtime.leases++;
            } else {
                if (saved == null) {
                    saved = new SavedMantleEntry(activation.activationId());
                    saved.loading = true;
                    savedMantles.put(activation.activationId(), saved);
                    activeLoads++;
                    loader = true;
                }
                saved.leases++;
            }
        } finally {
            stateLock.unlock();
        }
        if (runtime != null) {
            RuntimeLease lease = awaitRuntime(runtime, activation.activationId());
            try (IrisEngine.GenerationRuntimeScope ignored = engine.openGenerationRuntimeScope(lease.binding())) {
                return new SavedMantleAccess(engine.getMantle().getMantle(), lease::close);
            } catch (Throwable failure) {
                lease.close();
                throw failure;
            }
        }
        SavedMantleEntry entry = saved;
        if (loader) {
            loadSavedMantle(entry, activation, epoch);
        }
        stateLock.lock();
        try {
            while (entry.loading) {
                try {
                    inactive.await();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    entry.leases--;
                    inactive.signalAll();
                    throw new IOException("Interrupted while loading saved mantle for activation " + entry.activationId + ".", failure);
                }
            }
            if (entry.failure != null) {
                entry.leases--;
                throw propagate(entry.failure, "Unable to load saved mantle for activation " + entry.activationId + ".");
            }
            return new SavedMantleAccess(entry.mantle, () -> releaseSavedMantle(entry));
        } finally {
            stateLock.unlock();
        }
    }

    private void loadSavedMantle(SavedMantleEntry entry, GenerationActivation activation, GenerationEpoch epoch) {
        IrisData data = null;
        Mantle<Matter> mantle = null;
        Throwable failure = null;
        try {
            data = IrisData.openRuntime(history.packRoot(activation.activationId()).toFile());
            data.bindGenerationRegistryContract(epoch.registryContract());
            data.registerEngine(engine);
            IrisData savedData = data;
            mantle = IrisEngineMantle.createMantle(engine, history.paths().activationMantleRoot(activation.activationId()), () -> savedData);
        } catch (Throwable loadFailure) {
            failure = loadFailure;
            if (data != null) {
                try {
                    data.unregisterEngine(engine);
                    data.close();
                } catch (Throwable closeFailure) {
                    failure = appendFailure(failure, closeFailure);
                }
            }
        }
        stateLock.lock();
        try {
            entry.data = data;
            entry.mantle = mantle;
            entry.failure = failure;
            entry.loading = false;
            activeLoads--;
            if (failure != null) {
                savedMantles.remove(entry.activationId, entry);
            }
            inactive.signalAll();
        } finally {
            stateLock.unlock();
        }
    }

    private void releaseSavedMantle(SavedMantleEntry entry) {
        stateLock.lock();
        try {
            if (entry.leases <= 0) {
                throw new IllegalStateException("Saved mantle has no active lease.");
            }
            entry.leases--;
            if (entry.leases > 0) {
                return;
            }
            entry.retiring = true;
        } finally {
            stateLock.unlock();
        }
        closeSavedMantle(entry);
    }

    private void closeSavedMantle(SavedMantleEntry entry) {
        Throwable failure = null;
        try {
            entry.mantle.close();
            entry.data.unregisterEngine(engine);
            entry.data.close();
        } catch (Throwable closeFailure) {
            failure = closeFailure;
        }
        stateLock.lock();
        try {
            entry.failure = failure;
            if (failure == null) {
                savedMantles.remove(entry.activationId, entry);
            }
            inactive.signalAll();
        } finally {
            stateLock.unlock();
        }
        if (failure != null) {
            throw new IllegalStateException("Failed to close saved mantle for activation " + entry.activationId + ".", failure);
        }
    }

    private void awaitSavedMantleChange(SavedMantleEntry entry) throws IOException {
        if (entry.failure != null) {
            throw propagate(entry.failure, "Saved mantle for activation " + entry.activationId + " is unavailable.");
        }
        if (closed) {
            throw new IllegalStateException("Generation-history runtime router is closed.");
        }
        try {
            inactive.await();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while awaiting saved mantle for activation " + entry.activationId + ".", failure);
        }
    }

    private RuntimeLease acquireRuntime(
            GenerationActivation activation,
            GenerationEpoch epoch
    ) throws IOException {
        RuntimeCacheEntry entry;
        boolean loader = false;
        stateLock.lock();
        try {
            while (true) {
                awaitRetirementLocked(activation.activationId());
                SavedMantleEntry saved = savedMantles.get(activation.activationId());
                if (saved == null) {
                    break;
                }
                awaitSavedMantleChange(saved);
            }
            entry = bindings.get(activation.activationId());
            if (entry == null) {
                entry = new RuntimeCacheEntry(activation.activationId());
                entry.loading = true;
                bindings.put(activation.activationId(), entry);
                activeLoads++;
                loader = true;
            }
            entry.leases++;
            if (!entry.loading && entry.loadFailure == null && entry.binding != null) {
                return new RuntimeLease(this, entry, entry.binding);
            }
        } finally {
            stateLock.unlock();
        }

        if (loader) {
            loadRuntime(entry, activation, epoch);
        }
        return awaitRuntime(entry, activation.activationId());
    }

    private void awaitRetirementLocked(long activationId) throws IOException {
        while (true) {
            if (closed) {
                throw new IllegalStateException("Generation-history runtime router is closed.");
            }
            RuntimeRetirement retirement = retiringBindings.get(activationId);
            if (retirement == null) {
                return;
            }
            if (retirement.failure != null) {
                throw new IOException("Generation runtime for activation " + activationId
                        + " could not retire safely.", retirement.failure);
            }
            try {
                inactive.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while awaiting generation runtime retirement for activation "
                        + activationId + ".", interrupted);
            }
        }
    }

    private void loadRuntime(
            RuntimeCacheEntry entry,
            GenerationActivation activation,
            GenerationEpoch epoch
    ) {
        IrisEngine.GenerationRuntimeBinding loaded = null;
        Throwable failure = null;
        try {
            loaded = Objects.requireNonNull(
                    runtimeFactory.load(engine, history, activation, epoch),
                    "activation runtime binding"
            );
            requireBindingContract(activation, epoch, loaded);
        } catch (Throwable loadFailure) {
            failure = loadFailure;
        }
        if (failure != null && loaded != null) {
            try {
                engine.closeDetachedGenerationRuntime(loaded);
            } catch (Throwable closeFailure) {
                failure = appendFailure(failure, closeFailure);
            }
        }

        stateLock.lock();
        try {
            entry.binding = failure == null ? loaded : null;
            entry.loadFailure = failure;
            entry.loading = false;
            activeLoads--;
            if (failure != null && bindings.get(entry.activationId) == entry) {
                bindings.remove(entry.activationId);
            }
            inactive.signalAll();
        } finally {
            stateLock.unlock();
        }
    }

    private RuntimeLease awaitRuntime(RuntimeCacheEntry entry, long activationId) throws IOException {
        IrisEngine.GenerationRuntimeBinding binding;
        Throwable failure;
        stateLock.lock();
        try {
            while (entry.loading) {
                try {
                    inactive.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    entry.leases--;
                    inactive.signalAll();
                    throw new IOException("Interrupted while loading generation runtime for activation "
                            + activationId + ".", interrupted);
                }
            }
            failure = entry.loadFailure;
            binding = entry.binding;
            if (failure != null || binding == null) {
                entry.leases--;
                inactive.signalAll();
            }
        } finally {
            stateLock.unlock();
        }
        if (failure != null || binding == null) {
            throw propagate(failure == null
                            ? new IllegalStateException("Generation runtime load returned no binding.")
                            : failure,
                    "Unable to load generation runtime for activation " + activationId + ".");
        }
        return new RuntimeLease(this, entry, binding);
    }

    private List<RuntimeRetirement> pinDefault(RuntimeCacheEntry nextDefault) {
        stateLock.lock();
        try {
            for (RuntimeCacheEntry entry : bindings.values()) {
                entry.defaultRuntime = entry == nextDefault;
            }
            return collectEvictionsLocked();
        } finally {
            stateLock.unlock();
        }
    }

    private void releaseRuntime(RuntimeCacheEntry entry) {
        List<RuntimeRetirement> retired;
        stateLock.lock();
        try {
            if (entry.leases <= 0) {
                throw new IllegalStateException("Generation runtime cache entry has no active lease.");
            }
            entry.leases--;
            retired = collectEvictionsLocked();
            inactive.signalAll();
        } finally {
            stateLock.unlock();
        }
        retireBindings(retired);
    }

    private List<RuntimeRetirement> collectEvictionsLocked() {
        if (bindings.size() <= 1) {
            return List.of();
        }
        ArrayList<RuntimeRetirement> retired = new ArrayList<>();
        while (bindings.size() > 1) {
            RuntimeCacheEntry candidate = null;
            Iterator<Map.Entry<Long, RuntimeCacheEntry>> iterator = bindings.entrySet().iterator();
            while (iterator.hasNext()) {
                RuntimeCacheEntry entry = iterator.next().getValue();
                if (entry.defaultRuntime || entry.loading || entry.leases > 0 || entry.binding == null) {
                    continue;
                }
                candidate = entry;
                iterator.remove();
                break;
            }
            if (candidate == null) {
                break;
            }
            retired.add(scheduleRetirementLocked(candidate));
        }
        return retired;
    }

    private RuntimeRetirement scheduleRetirementLocked(RuntimeCacheEntry entry) {
        RuntimeRetirement retirement = new RuntimeRetirement(entry.activationId, entry.binding);
        if (retiringBindings.putIfAbsent(entry.activationId, retirement) != null) {
            throw new IllegalStateException("Generation runtime already retiring for activation "
                    + entry.activationId + ".");
        }
        return retirement;
    }

    private void retireBindings(List<RuntimeRetirement> retired) {
        Throwable failure = null;
        for (RuntimeRetirement retirement : retired) {
            Throwable retirementFailure = null;
            try {
                engine.closeDetachedGenerationRuntime(retirement.binding);
            } catch (Throwable closeFailure) {
                retirementFailure = closeFailure;
                failure = appendFailure(failure, closeFailure);
            } finally {
                stateLock.lock();
                try {
                    retirement.failure = retirementFailure;
                    if (retirementFailure == null) {
                        retiringBindings.remove(retirement.activationId, retirement);
                    }
                    inactive.signalAll();
                } finally {
                    stateLock.unlock();
                }
            }
        }
        if (failure != null) {
            throw new IllegalStateException("Failed to retire cached generation runtimes.", failure);
        }

    }

    private void requireBindingContract(
            GenerationActivation activation,
            GenerationEpoch epoch,
            IrisEngine.GenerationRuntimeBinding binding
    ) throws IOException {
        if (!epoch.kernelVersion().equals(binding.kernelVersion())) {
            throw new IOException("Generation runtime for activation " + activation.activationId()
                    + " uses kernel " + binding.kernelVersion() + " instead of " + epoch.kernelVersion() + ".");
        }
        GenerationKernelRegistry.RuntimeKernel runtimeKernel = binding.runtimeKernel();
        if (runtimeKernel == null || !epoch.kernelVersion().equals(runtimeKernel.version())) {
            throw new IOException("Generation runtime for activation " + activation.activationId()
                    + " has no matching executable kernel for " + epoch.kernelVersion() + ".");
        }
        if (!epoch.kernelImplementationFingerprint().equals(runtimeKernel.implementationFingerprint())) {
            throw new IOException("Generation runtime for activation " + activation.activationId()
                    + " uses kernel implementation " + runtimeKernel.implementationFingerprint()
                    + " instead of " + epoch.kernelImplementationFingerprint() + ".");
        }
        Path expectedMantle = history.paths().activationMantleRoot(activation.activationId());
        if (!expectedMantle.equals(binding.mantleStorageDirectory())) {
            throw new IOException("Generation runtime for activation " + activation.activationId()
                    + " uses mantle " + binding.mantleStorageDirectory() + " instead of " + expectedMantle + ".");
        }
        TransitionGenerationPlan actualPlan = binding.transitionPlan();
        if (activation.isInitial()) {
            if (actualPlan != null) {
                throw new IOException("Initial generation activation cannot use a transition plan.");
            }
            return;
        }
        TransitionGenerationPlan expectedPlan = history.transitionPlan(activation.activationId());
        if (actualPlan == null || !expectedPlan.specification().equals(actualPlan.specification())) {
            throw new IOException("Generation runtime transition plan does not match activation "
                    + activation.activationId() + ".");
        }
    }

    private GenerationEpoch requireEpoch(GenerationActivation activation) throws IOException {
        return history.manifest().epoch(activation.epochId()).orElseThrow(() -> new IOException(
                "Generation activation " + activation.activationId() + " references missing epoch "
                        + activation.epochId() + "."
        ));
    }

    private void enterOperation() {
        stateLock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("Generation-history runtime router is closed.");
            }
            activeOperations++;
            operationDepth.set(operationDepth.get() + 1);
        } finally {
            stateLock.unlock();
        }
    }

    private void enterRouteOperation() {
        stateLock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("Generation-history runtime router is closed.");
            }
            activeOperations++;
        } finally {
            stateLock.unlock();
        }
    }

    private void leaveOperation() {
        stateLock.lock();
        try {
            int depth = operationDepth.get();
            if (depth <= 0 || activeOperations <= 0) {
                throw new IllegalStateException("No generation-history route is active.");
            }
            if (depth == 1) {
                operationDepth.remove();
            } else {
                operationDepth.set(depth - 1);
            }
            activeOperations--;
            if (activeOperations == 0) {
                inactive.signalAll();
            }
        } finally {
            stateLock.unlock();
        }
    }

    private void leaveRouteOperation() {
        stateLock.lock();
        try {
            if (activeOperations <= 0) {
                throw new IllegalStateException("No generation-history route is active.");
            }
            activeOperations--;
            if (activeOperations == 0) {
                inactive.signalAll();
            }
        } finally {
            stateLock.unlock();
        }
    }

    private void enterRuntimeScope() {
        operationDepth.set(operationDepth.get() + 1);
    }

    private RuntimeRoute installScopedRoute(RuntimeRoute route) {
        RuntimeRoute previous = scopedRoute.get();
        scopedRoute.set(route);
        return previous;
    }

    private void restoreScopedRoute(RuntimeRoute route, RuntimeRoute previous) {
        if (scopedRoute.get() != route) {
            throw new IllegalStateException("Generation-history runtime scopes closed out of order.");
        }
        if (previous == null) {
            scopedRoute.remove();
        } else {
            scopedRoute.set(previous);
        }
    }

    private void leaveRuntimeScope() {
        int depth = operationDepth.get();
        if (depth <= 0) {
            throw new IllegalStateException("No generation-history runtime scope is active.");
        }
        if (depth == 1) {
            operationDepth.remove();
            return;
        }
        operationDepth.set(depth - 1);
    }

    private static IOException propagate(Throwable failure, String message) {
        if (failure instanceof IOException ioFailure) {
            return ioFailure;
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IOException(message, failure);
    }

    private static Throwable appendFailure(Throwable existing, Throwable added) {
        if (added == null) {
            return existing;
        }
        if (existing == null) {
            return added;
        }
        if (existing != added) {
            existing.addSuppressed(added);
        }
        return existing;
    }

    private static void rethrowCloseFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Failed to close the generation-history runtime router.", failure);
    }

    private static final class SavedMantleEntry {
        private final long activationId;
        private IrisData data;
        private Mantle<Matter> mantle;
        private Throwable failure;
        private int leases;
        private boolean loading;
        private boolean retiring;

        private SavedMantleEntry(long activationId) {
            this.activationId = activationId;
        }
    }

    private record SavedMantleAccess(Mantle<Matter> mantle, Runnable release) {
    }

    private record SavedChunkResources(GenerationHistory.GenerationStage stage,
                                       GenerationTransitionGate.Participation participation,
                                       SavedMantleAccess access) {
    }

    public static final class SavedChunkMantle implements AutoCloseable {
        private final GenerationHistoryRuntimeRouter router;
        private final SavedChunkResources resources;
        private int activeScopes;
        private boolean closed;

        private SavedChunkMantle(GenerationHistoryRuntimeRouter router, SavedChunkResources resources) {
            this.router = router;
            this.resources = resources;
        }

        public Mantle<Matter> mantle() {
            return resources.access().mantle();
        }

        public void detachThread() {
            resources.participation().detachThread();
        }

        public Scope openScope() {
            synchronized (this) {
                if (closed) {
                    throw new IllegalStateException("Saved chunk mantle is closed.");
                }
                activeScopes++;
            }
            try {
                GenerationTransitionGate.Participation participation = resources.participation().attach();
                router.enterRuntimeScope();
                return new Scope(this, participation);
            } catch (Throwable failure) {
                releaseScope();
                throw failure;
            }
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            if (activeScopes > 0) {
                throw new IllegalStateException("Saved chunk mantle still has active scopes.");
            }
            closed = true;
            Throwable failure = null;
            try {
                resources.access().release().run();
            } catch (Throwable closeFailure) {
                failure = closeFailure;
            }
            try {
                resources.stage().close();
            } catch (Throwable closeFailure) {
                failure = appendFailure(failure, closeFailure);
            }
            try {
                router.leaveRouteOperation();
            } catch (Throwable closeFailure) {
                failure = appendFailure(failure, closeFailure);
            }
            try {
                resources.participation().close();
            } catch (Throwable closeFailure) {
                failure = appendFailure(failure, closeFailure);
            }
            if (failure != null) {
                throw new IllegalStateException("Failed to close saved chunk mantle.", failure);
            }
        }

        private synchronized void releaseScope() {
            if (activeScopes <= 0) {
                throw new IllegalStateException("Saved chunk mantle has no active scope.");
            }
            activeScopes--;
        }

        public static final class Scope implements AutoCloseable {
            private final SavedChunkMantle handle;
            private final GenerationTransitionGate.Participation participation;
            private final Thread owner = Thread.currentThread();
            private boolean closed;

            private Scope(SavedChunkMantle handle, GenerationTransitionGate.Participation participation) {
                this.handle = handle;
                this.participation = participation;
            }

            @Override
            public void close() {
                if (closed) {
                    return;
                }
                if (Thread.currentThread() != owner) {
                    throw new IllegalStateException("Saved chunk mantle scope closed from a different thread.");
                }
                closed = true;
                try {
                    handle.router.leaveRuntimeScope();
                } finally {
                    try {
                        handle.releaseScope();
                    } finally {
                        participation.close();
                    }
                }
            }
        }
    }

    private static final class RuntimeCacheEntry {
        private final long activationId;
        private IrisEngine.GenerationRuntimeBinding binding;
        private Throwable loadFailure;
        private int leases;
        private boolean loading;
        private boolean defaultRuntime;

        private RuntimeCacheEntry(long activationId) {
            this.activationId = activationId;
        }
    }

    private static final class RuntimeRetirement {
        private final long activationId;
        private final IrisEngine.GenerationRuntimeBinding binding;
        private Throwable failure;

        private RuntimeRetirement(long activationId, IrisEngine.GenerationRuntimeBinding binding) {
            this.activationId = activationId;
            this.binding = binding;
        }
    }

    private static final class RuntimeLease implements AutoCloseable {
        private final GenerationHistoryRuntimeRouter router;
        private final RuntimeCacheEntry entry;
        private final IrisEngine.GenerationRuntimeBinding binding;
        private boolean closed;

        private RuntimeLease(
                GenerationHistoryRuntimeRouter router,
                RuntimeCacheEntry entry,
                IrisEngine.GenerationRuntimeBinding binding
        ) {
            this.router = router;
            this.entry = entry;
            this.binding = binding;
        }

        private IrisEngine.GenerationRuntimeBinding binding() {
            return binding;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            router.releaseRuntime(entry);
        }
    }

    interface ActivationRuntimeFactory {
        void validateBase(
                IrisEngine engine,
                GenerationHistory history,
                GenerationActivation activation,
                GenerationEpoch epoch,
                IrisEngine.GenerationRuntimeBinding binding
        ) throws IOException;

        IrisEngine.GenerationRuntimeBinding load(
                IrisEngine engine,
                GenerationHistory history,
                GenerationActivation activation,
                GenerationEpoch epoch
        ) throws IOException;
    }

    private static final class DefaultActivationRuntimeFactory implements ActivationRuntimeFactory {
        @Override
        public void validateBase(
                IrisEngine engine,
                GenerationHistory history,
                GenerationActivation activation,
                GenerationEpoch epoch,
                IrisEngine.GenerationRuntimeBinding binding
        ) throws IOException {
            requireWorldSeed(epoch, binding.target().getWorld().getRawWorldSeed());
            Path expectedPack = history.packRoot(activation.activationId());
            Path actualPack = binding.target().getData().getDataFolder().toPath().toAbsolutePath().normalize();
            if (!expectedPack.equals(actualPack)) {
                throw new IOException("Base Iris runtime does not use active activation "
                        + activation.activationId() + " pack " + expectedPack + ".");
            }
            binding.target().getData().bindGenerationRegistryContract(epoch.registryContract());
            requireDimensionContract(binding.target().getDimension(), epoch, actualPack);
        }

        @Override
        public IrisEngine.GenerationRuntimeBinding load(
                IrisEngine engine,
                GenerationHistory history,
                GenerationActivation activation,
                GenerationEpoch epoch
        ) throws IOException {
            requireWorldSeed(epoch, engine.getWorld().getRawWorldSeed());
            Path packRoot = history.packRoot(activation.activationId());
            IrisData data = IrisData.openRuntime(packRoot.toFile());
            try {
                data.bindGenerationRegistryContract(epoch.registryContract());
                IrisDimension dimension = data.getDimensionLoader().load(epoch.dimensionContract().dimensionKey());
                if (dimension == null) {
                    throw new IOException("Immutable generation pack does not contain dimension '"
                            + epoch.dimensionContract().dimensionKey() + "': " + packRoot);
                }
                requireDimensionContract(dimension, epoch, packRoot);
                TransitionGenerationPlan plan = activation.isInitial()
                        ? null
                        : history.transitionPlan(activation.activationId());
                EngineTarget target = new EngineTarget(engine.getWorld(), dimension, data);
                return engine.buildDetachedGenerationRuntime(
                        target,
                        history.paths().activationMantleRoot(activation.activationId()),
                        epoch.kernelVersion(),
                        plan
                );
            } catch (Throwable failure) {
                if (!data.isClosed()) {
                    data.unregisterEngine(engine);
                    data.close();
                }
                throw propagate(failure, "Unable to build generation runtime for activation "
                        + activation.activationId() + ".");
            }
        }

        private static void requireWorldSeed(GenerationEpoch epoch, long actualSeed) throws IOException {
            if (epoch.worldSeed() != actualSeed) {
                throw new IOException("Generation activation epoch requires world seed "
                        + epoch.worldSeed() + " but the Iris engine uses " + actualSeed + ".");
            }
        }

        private static void requireDimensionContract(
                IrisDimension dimension,
                GenerationEpoch epoch,
                Path packRoot
        ) throws IOException {
            GenerationEpoch.DimensionContract recorded = epoch.dimensionContract();
            GenerationEpoch.DimensionContract loaded;
            try {
                loaded = GenerationEpochContractFactory.createForEpoch(
                        dimension,
                        recorded.dimensionTypeKey(),
                        epoch
                );
            } catch (RuntimeException failure) {
                throw new IOException("Unable to validate immutable generation dimension at " + packRoot + ".", failure);
            }
            if (!recorded.equals(loaded)) {
                throw new IOException("Immutable generation pack no longer matches its dimension contract: "
                        + packRoot);
            }
        }
    }

    public static final class RuntimeRoute implements AutoCloseable {
        private final GenerationHistoryRuntimeRouter router;
        private final GenerationHistory.GenerationStage stage;
        private final RuntimeLease lease;
        private final IrisEngine.GenerationRuntimeBinding binding;
        private final RuntimeOwnership ownership;
        private final GenerationTransitionGate.Participation participation;
        private int activeScopes;
        private volatile SavedTerrainChunk naturalTerrain;
        private volatile FloatingBiomeOverlay floatingBiomes;
        private boolean closed;

        private RuntimeRoute(
                GenerationHistoryRuntimeRouter router,
                GenerationHistory.GenerationStage stage,
                RuntimeLease lease,
                GenerationTransitionGate.Participation participation
        ) {
            this.router = router;
            this.stage = stage;
            this.lease = lease;
            this.participation = participation;
            this.binding = lease.binding();
            this.ownership = new RuntimeOwnership(stage.activation().activationId(), binding);
        }

        public GenerationHistory.GenerationStage generationStage() {
            return stage;
        }

        public Optional<SavedTerrainChunk> naturalTerrain() {
            return Optional.ofNullable(naturalTerrain);
        }

        public int chunkX() {
            return stage.chunkX();
        }

        public int chunkZ() {
            return stage.chunkZ();
        }

        public GenerationActivation activation() {
            return stage.activation();
        }

        public GenerationEpoch epoch() {
            return stage.epoch();
        }

        public TransitionGenerationPlan transitionPlan() {
            return binding.transitionPlan();
        }

        public void detachThread() {
            participation.detachThread();
        }

        public boolean claimGeneratedSemantics() throws IOException {
            return claimGeneratedSemantics((x, y, z) -> true);
        }

        public boolean claimGeneratedSemantics(GenerationSemanticCapture.CaveSpace caveSpace) throws IOException {
            if (router.scopedRoute.get() != this) {
                throw new IllegalStateException(
                        "Generation semantics must be captured inside this route's runtime scope."
                );
            }
            Optional<ChunkGenerationSemantics> recorded = router.history.semantics(chunkX(), chunkZ());
            if (recorded.isPresent() && recorded.get().sealed()
                    || router.history.resolveActivation(chunkX(), chunkZ()).activationId() != stage.activation().activationId()) {
                return false;
            }
            ChunkGenerationSemantics semantics = GenerationSemanticCapture.capture(
                    router.engine,
                    stage,
                    caveSpace
            );
            router.biomes.capture(SavedBiomeCapture.capture(router.engine, stage, router.biomes, floatingBiomes));
            return router.history.claimGeneratedSemantics(stage, semantics);
        }

        public RuntimeScope openRuntimeScope() {
            synchronized (this) {
                if (closed) {
                    throw new IllegalStateException("Generation-history runtime route is closed.");
                }
                activeScopes++;
            }
            router.enterRuntimeScope();
            RuntimeRoute previousRoute = router.installScopedRoute(this);
            GenerationTransitionGate.Participation scopedParticipation = participation.attach();
            try {
                IrisEngine.GenerationRuntimeScope opened = router.engine.openGenerationRuntimeScope(binding);
                return new RuntimeScope(this, opened, Thread.currentThread(), previousRoute, scopedParticipation);
            } catch (Throwable failure) {
                scopedParticipation.close();
                try {
                    router.restoreScopedRoute(this, previousRoute);
                } catch (Throwable closeFailure) {
                    appendFailure(failure, closeFailure);
                }
                try {
                    router.leaveRuntimeScope();
                } catch (Throwable closeFailure) {
                    appendFailure(failure, closeFailure);
                }
                try {
                    releaseRuntimeScope();
                } catch (Throwable closeFailure) {
                    appendFailure(failure, closeFailure);
                }
                throw failure;
            }
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            if (activeScopes > 0) {
                throw new IllegalStateException("Generation-history runtime route still has active scopes.");
            }
            closed = true;
            Throwable failure = null;
            try {
                stage.close();
            } catch (Throwable closeFailure) {
                failure = closeFailure;
            }
            try {
                lease.close();
            } catch (Throwable closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else if (failure != closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            try {
                router.leaveRouteOperation();
            } catch (Throwable closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else if (failure != closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            participation.close();
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure != null) {
                throw new IllegalStateException("Failed to close generation-history runtime route.", failure);
            }
        }

        private synchronized void releaseRuntimeScope() {
            if (activeScopes <= 0) {
                throw new IllegalStateException("Generation-history runtime route has no active scope.");
            }
            activeScopes--;
        }

        public static final class RuntimeScope implements AutoCloseable {
            private final RuntimeRoute route;
            private final IrisEngine.GenerationRuntimeScope runtimeScope;
            private final Thread owner;
            private final RuntimeRoute previousRoute;
            private final GenerationTransitionGate.Participation participation;
            private boolean closed;

            private RuntimeScope(
                    RuntimeRoute route,
                    IrisEngine.GenerationRuntimeScope runtimeScope,
                    Thread owner,
                    RuntimeRoute previousRoute,
                    GenerationTransitionGate.Participation participation
            ) {
                this.route = route;
                this.runtimeScope = runtimeScope;
                this.owner = owner;
                this.previousRoute = previousRoute;
                this.participation = participation;
            }

            @Override
            public void close() {
                if (closed) {
                    return;
                }
                if (Thread.currentThread() != owner) {
                    throw new IllegalStateException("Generation-history runtime scope closed from a different thread.");
                }
                closed = true;
                Throwable failure = null;
                try {
                    runtimeScope.close();
                } catch (Throwable closeFailure) {
                    failure = closeFailure;
                }
                try {
                    route.router.restoreScopedRoute(route, previousRoute);
                } catch (Throwable closeFailure) {
                    if (failure == null) {
                        failure = closeFailure;
                    } else if (failure != closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                }
                try {
                    route.router.leaveRuntimeScope();
                } catch (Throwable closeFailure) {
                    failure = appendFailure(failure, closeFailure);
                }
                try {
                    route.releaseRuntimeScope();
                } catch (Throwable closeFailure) {
                    failure = appendFailure(failure, closeFailure);
                }
                participation.close();
                if (failure instanceof RuntimeException runtimeFailure) {
                    throw runtimeFailure;
                }
                if (failure instanceof Error error) {
                    throw error;
                }
                if (failure != null) {
                    throw new IllegalStateException("Failed to close generation-history runtime scope.", failure);
                }
            }
        }
    }

    public record RuntimeOwnership(
            long activationId,
            IrisEngine.GenerationRuntimeBinding binding
    ) {
        public RuntimeOwnership {
            if (activationId <= 0L) {
                throw new IllegalArgumentException("Generation activation ID must be positive.");
            }
            Objects.requireNonNull(binding, "generation runtime binding");
        }
    }

    public static final class RuntimeStage implements AutoCloseable {
        private final RuntimeRoute route;
        private final RuntimeRoute.RuntimeScope runtimeScope;
        private final Thread owner;
        private boolean closed;

        private RuntimeStage(
                RuntimeRoute route,
                RuntimeRoute.RuntimeScope runtimeScope,
                Thread owner
        ) {
            this.route = route;
            this.runtimeScope = runtimeScope;
            this.owner = owner;
        }

        public GenerationHistory.GenerationStage generationStage() {
            return route.generationStage();
        }

        public GenerationActivation activation() {
            return route.activation();
        }

        public GenerationEpoch epoch() {
            return route.epoch();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Generation-history stage closed from a different thread.");
            }
            Throwable failure = null;
            try {
                runtimeScope.close();
            } catch (Throwable closeFailure) {
                failure = closeFailure;
            }
            try {
                route.close();
            } catch (Throwable closeFailure) {
                failure = appendFailure(failure, closeFailure);
            } finally {
                closed = true;
            }
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure != null) {
                throw new IllegalStateException("Failed to close generation-history stage.", failure);
            }
        }
    }

    public static final class CoordinateScope implements AutoCloseable {
        private final int blockX;
        private final int blockZ;
        private final RuntimeRoute route;
        private final RuntimeStage stage;

        private CoordinateScope(int blockX, int blockZ, RuntimeRoute route, RuntimeStage stage) {
            this.blockX = blockX;
            this.blockZ = blockZ;
            this.route = route;
            this.stage = stage;
        }

        public int blockX() {
            return blockX;
        }

        public int blockZ() {
            return blockZ;
        }

        public int chunkX() {
            return route == null
                    ? Math.floorDiv(blockX, GenerationBoundary.CHUNK_SIZE)
                    : route.chunkX();
        }

        public int chunkZ() {
            return route == null
                    ? Math.floorDiv(blockZ, GenerationBoundary.CHUNK_SIZE)
                    : route.chunkZ();
        }

        public GenerationActivation activation() {
            if (route == null) {
                throw new IllegalStateException("Borrowed runtime coordinate scope has no history route metadata.");
            }
            return route.activation();
        }

        public GenerationEpoch epoch() {
            if (route == null) {
                throw new IllegalStateException("Borrowed runtime coordinate scope has no history route metadata.");
            }
            return route.epoch();
        }

        public boolean claimGeneratedSemantics() throws IOException {
            return stage != null && route.claimGeneratedSemantics();
        }

        @Override
        public void close() {
            if (stage != null) {
                stage.close();
            }
        }
    }
}
