package art.arcane.iris.core.service;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioActivation;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBounds;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.service.JigsawStudioAutosaveScheduler.AutosaveFailureState;
import art.arcane.iris.core.service.JigsawStudioAutosaveScheduler.AutosavePersistentFailure;
import art.arcane.iris.core.service.JigsawStudioCapture.Capture;
import art.arcane.iris.core.service.JigsawStudioCapture.ChunkCaptureArea;
import art.arcane.iris.core.service.JigsawStudioCapture.ChunkSnapshot;
import art.arcane.iris.core.service.JigsawStudioCapture.WorkcellTopologyException;
import art.arcane.iris.core.service.JigsawStudioService.ActiveStudio;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.core.structure.authoring.StructureWriteOptions;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import art.arcane.iris.engine.framework.structure.StructureResourceBundleGraphCompiler;
import art.arcane.iris.engine.object.IrisJigsawConnector;
import art.arcane.iris.engine.object.IrisJigsawPiece;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.platform.studio.generators.JigsawStudioGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static art.arcane.iris.core.service.JigsawStudioCapture.aggregateSnapshots;
import static art.arcane.iris.core.service.JigsawStudioCapture.captureChunkIntersection;
import static art.arcane.iris.core.service.JigsawStudioCapture.chunkIntersections;
import static art.arcane.iris.core.service.JigsawStudioCapture.preserveCapturedConnectorOrder;
import static art.arcane.iris.core.service.JigsawStudioCapture.requireWorkcellTopology;
import static art.arcane.iris.core.service.JigsawStudioService.chunkKey;
import static art.arcane.iris.core.service.JigsawStudioService.failureMessage;
import static art.arcane.iris.core.service.JigsawStudioService.message;
import static art.arcane.iris.core.service.JigsawStudioService.playSaveSound;
import static art.arcane.iris.core.service.JigsawStudioService.writeFailure;

final class JigsawStudioCaptureScheduler {
    private final JigsawStudioService service;

    JigsawStudioCaptureScheduler(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    boolean scheduleCapture(
            ActiveStudio studio,
            World world,
            JigsawStudioBay bay,
            CaptureTarget captureTarget,
            Player player,
            UUID requestId,
            JigsawStudioSession.SaveIdentity saveIdentity,
            AutosaveFailureState autosaveFailureState
    ) {
        List<ChunkCaptureArea> areas = chunkIntersections(captureTarget.bounds());
        CaptureWork work = new CaptureWork(
                studio,
                world,
                bay,
                captureTarget,
                player,
                requestId,
                saveIdentity,
                autosaveFailureState);
        CaptureCoordinator coordinator = new CaptureCoordinator(
                work,
                areas);
        for (ChunkCaptureArea area : areas) {
            if (coordinator.stopped()) {
                return false;
            }
            boolean scheduled = J.runRegion(
                    world,
                    area.chunkX(),
                    area.chunkZ(),
                    () -> captureChunk(coordinator, area));
            if (!scheduled) {
                coordinator.fail("Iris could not schedule bay chunk "
                        + area.chunkX() + "," + area.chunkZ() + " on its owning region.", null);
                return false;
            }
        }
        return !coordinator.stopped();
    }

    private void captureChunk(CaptureCoordinator coordinator, ChunkCaptureArea area) {
        if (coordinator.stopped()) {
            return;
        }
        ActiveStudio studio = coordinator.studio();
        World world = coordinator.world();
        if (!service.saveLifecycle.isCurrentSave(studio, coordinator.requestId(), coordinator.saveIdentity())) {
            coordinator.fail("Jigsaw Studio changed before every bay chunk could be captured; save cancelled.", null);
            return;
        }
        if (!world.isChunkLoaded(area.chunkX(), area.chunkZ())) {
            coordinator.fail("Bay chunk " + area.chunkX() + "," + area.chunkZ()
                    + " is not loaded. Visit the whole bay and try again.", null);
            return;
        }
        try {
            ChunkSnapshot snapshot = captureChunkIntersection(
                    world,
                    coordinator.captureTarget().bounds(),
                    coordinator.captureTarget().piece(),
                    coordinator.captureTarget().object(),
                    area,
                    coordinator.captureTarget().displayRotationQuarterTurns(),
                    coordinator.captureTarget().connectorsVisible());
            if (!service.saveLifecycle.isCurrentSave(studio, coordinator.requestId(), coordinator.saveIdentity())) {
                coordinator.fail("Jigsaw Studio changed while bay chunks were being captured; save cancelled.", null);
                return;
            }
            coordinator.accept(snapshot);
        } catch (Throwable exception) {
            coordinator.fail("Jigsaw Studio capture failed in chunk "
                    + area.chunkX() + "," + area.chunkZ() + ": " + failureMessage(exception), exception);
        }
    }

    void assembleAndPersist(CaptureCoordinator coordinator, List<ChunkSnapshot> snapshots) {
        if (coordinator.failed()) {
            return;
        }
        ActiveStudio studio = coordinator.studio();
        if (!service.saveLifecycle.isCurrentSave(studio, coordinator.requestId(), coordinator.saveIdentity())) {
            coordinator.fail("Jigsaw Studio changed before captured chunks could be assembled; save cancelled.", null);
            return;
        }
        try {
            Capture capture = aggregateSnapshots(
                    coordinator.captureTarget().bounds(),
                    coordinator.areas(),
                    snapshots,
                    coordinator.captureTarget().displayRotationQuarterTurns());
            List<IrisJigsawConnector> connectors = preserveCapturedConnectorOrder(
                    coordinator.captureTarget().piece(),
                    capture.connectors());
            requireWorkcellTopology(
                    coordinator.bay(),
                    connectors,
                    coordinator.captureTarget().displayRotationQuarterTurns());
            if (!service.saveLifecycle.isCurrentSave(studio, coordinator.requestId(), coordinator.saveIdentity())) {
                coordinator.fail("Jigsaw Studio changed before the captured bay could be written; save cancelled.", null);
                return;
            }
            message(coordinator.player(), "Captured " + connectors.size()
                    + " connector(s) from " + snapshots.size()
                    + " chunk(s); validating and writing the owned structure graph...");
            persistCapture(coordinator, capture, connectors);
        } catch (WorkcellTopologyException exception) {
            coordinator.failPersistent(
                    "Jigsaw Studio cannot autosave this planar workcell: "
                            + failureMessage(exception),
                    null);
        } catch (Throwable exception) {
            coordinator.failPersistent(
                    "Jigsaw Studio capture assembly failed: " + failureMessage(exception),
                    exception);
        }
    }

    private void persistCapture(
            CaptureCoordinator coordinator,
            Capture capture,
            List<IrisJigsawConnector> connectors
    ) {
        ActiveStudio studio = coordinator.studio();
        JigsawStudioBay bay = coordinator.bay();
        Player player = coordinator.player();
        try {
            if (!service.saveLifecycle.isCurrentSave(studio, coordinator.requestId(), coordinator.saveIdentity())) {
                message(player, "Jigsaw Studio changed before the captured bay could be written; save cancelled.");
                return;
            }
            JigsawStudioActivation.Request request = studio.generator().getRequest();
            IrisData source = request.source();
            Path packRoot = source.getDataFolder().toPath();
            JigsawStudioResourceBundleAssembler.Assembly assembly =
                    JigsawStudioResourceBundleAssembler.assemble(
                            packRoot,
                            request.structureKey(),
                            coordinator.saveIdentity().variantKey(),
                            capture.objectContent(),
                            connectors,
                            capture.hasBlockEntities());
            StructureResourceBundleGraphCompiler.requireViable(assembly.bundle());
            if (!service.saveLifecycle.isCurrentSave(studio, coordinator.requestId(), coordinator.saveIdentity())) {
                message(player, "Jigsaw Studio changed during validation; save cancelled before writing.");
                return;
            }
            JigsawStudioHistoryStore historyStore = new JigsawStudioHistoryStore(
                    packRoot,
                    request.structureKey());
            JigsawStudioHistoryStore.Snapshot previous = historyStore.snapshotCurrent(
                    coordinator.saveIdentity().variantKey());
            if (!previous.matches(assembly.bundle())) {
                historyStore.append(previous);
            }
            StructureWriteResult result;
            synchronized (service.saveLifecycleLock) {
                if (!service.saveLifecycle.isCurrentSave(studio, coordinator.requestId(), coordinator.saveIdentity())) {
                    message(player, "Jigsaw Studio changed before the validated save entered the writer; save cancelled.");
                    return;
                }
                result = new StructureTransactionWriter(packRoot)
                        .write(assembly.bundle(), StructureWriteOptions.overwriteExpected(
                                assembly.expectedManifestHash()));
            }
            if (!result.successful()) {
                String failure = writeFailure(result);
                coordinator.recordPersistentFailure(failure, result.failure().orElse(null));
                message(player, failure);
                return;
            }
            source.invalidateStructureResources();
            studio.generator().invalidateRender(bay.stableId(), coordinator.saveIdentity().variantKey());
            JigsawStudioSession session = studio.generator().getSession();
            boolean unchanged = session.markWorkcellSaved(coordinator.saveIdentity());
            try {
                service.reloadSessionLayout(studio);
            } catch (IOException | RuntimeException refreshFailure) {
                IrisLogging.reportError(refreshFailure);
                message(player, "The graph saved, but Studio could not refresh its variant catalog. "
                        + "Close and reopen this project before continuing: "
                        + failureMessage(refreshFailure));
            }
            String cleanup = result.status() == StructureWriteResult.Status.COMMITTED_CLEANUP_REQUIRED
                    ? " The graph committed, but transaction cleanup requires operator attention in the console."
                    : "";
            String mutationNotice = unchanged ? "" : " Newer edits remain unsaved.";
            message(player, "Saved piece '" + coordinator.saveIdentity().variantKey() + "' and object '"
                    + assembly.objectKey() + "' atomically." + mutationNotice + cleanup);
            playSaveSound(player);
            if (unchanged) {
                service.evaluator.scheduleEvaluation(studio);
            }
            IrisLogging.debug("Jigsaw Studio saved: structure=%s piece=%s object=%s connectors=%d status=%s",
                    request.structureKey(), coordinator.saveIdentity().variantKey(), assembly.objectKey(),
                    connectors.size(), result.status());
        } catch (Throwable exception) {
            String failure = "Jigsaw Studio save failed: " + failureMessage(exception);
            coordinator.recordPersistentFailure(failure, exception);
            message(player, failure);
        } finally {
            coordinator.complete();
        }
    }

    static Set<Long> requiredChunks(
            JigsawStudioBay bay,
            JigsawStudioGenerator.RenderedBay rendered
    ) {
        Set<Long> chunks = new HashSet<>();
        if (!rendered.valid()) {
            return chunks;
        }
        JigsawStudioBounds bounds = bay.bounds();
        int minimumChunkX = bounds.originX() >> 4;
        int maximumChunkX = bounds.maxX() >> 4;
        int minimumChunkZ = bounds.originZ() >> 4;
        int maximumChunkZ = bounds.maxZ() >> 4;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                chunks.add(chunkKey(chunkX, chunkZ));
            }
        }
        return Set.copyOf(chunks);
    }

    static CaptureTarget resolveCaptureTarget(
            ActiveStudio studio,
            JigsawStudioBay bay,
            JigsawStudioVariant variant
    )
            throws IOException {
        IrisData source = studio.generator().getRequest().source();
        JigsawStudioLayout currentLayout = studio.generator().getSession().layout();
        JigsawStudioBay currentBay = currentLayout.get(bay.stableId());
        if (currentBay == null) {
            throw new IOException("workcell '" + bay.stableId() + "' is absent from the current Studio layout");
        }
        JigsawStudioVariant activeVariant = Objects.requireNonNull(variant, "Jigsaw Studio capture variant");
        if (!currentLayout.accepts(currentBay, activeVariant)) {
            throw new IOException("variant '" + activeVariant.pieceKey()
                    + "' does not belong to workcell '" + currentBay.stableId() + "'");
        }
        IrisJigsawPiece piece = source.getJigsawPieceLoader().load(activeVariant.pieceKey(), false);
        if (piece == null || piece.getObject() == null || piece.getObject().isBlank()) {
            throw new IOException("piece '" + activeVariant.pieceKey() + "' is missing or has no object");
        }
        IrisObject object = source.getObjectLoader().load(piece.getObject(), false);
        if (object == null || object.getW() < 1 || object.getH() < 1 || object.getD() < 1) {
            throw new IOException("object '" + piece.getObject() + "' is missing or has invalid dimensions");
        }
        JigsawStudioBounds bayBounds = currentBay.bounds();
        JigsawStudioCellDimensions sourceDimensions = new JigsawStudioCellDimensions(
                object.getW(), object.getH(), object.getD());
        JigsawStudioCellDimensions canonicalDimensions = activeVariant.canonicalDimensions(sourceDimensions);
        if (canonicalDimensions.width() > bayBounds.dimensions().width()
                || canonicalDimensions.height() > bayBounds.dimensions().height()
                || canonicalDimensions.depth() > bayBounds.dimensions().depth()) {
            throw new IOException("object '" + piece.getObject() + "' is "
                    + canonicalDimensions.width() + "x" + canonicalDimensions.height() + "x"
                    + canonicalDimensions.depth() + " in its displayed orientation"
                    + " but bay '" + currentBay.stableId() + "' is only "
                    + bayBounds.dimensions().width() + "x" + bayBounds.dimensions().height() + "x"
                    + bayBounds.dimensions().depth());
        }
        return new CaptureTarget(
                new JigsawStudioBounds(
                        bayBounds.originX(),
                        bayBounds.originY(),
                        bayBounds.originZ(),
                        canonicalDimensions),
                piece,
                object,
                activeVariant.sourceToCanonicalQuarterTurns(),
                studio.generator().getSession().workcellSnapshot(currentBay.stableId()).connectorsVisible());
    }

    CaptureCoordinator coordinator(CaptureWork work, List<ChunkCaptureArea> areas) {
        return new CaptureCoordinator(work, areas);
    }

    final class CaptureCoordinator {
        private final CaptureWork work;
        private final List<ChunkCaptureArea> areas;
        private final Map<Long, ChunkSnapshot> snapshots = new ConcurrentHashMap<>();
        private final AtomicInteger remaining;
        private final AtomicBoolean failed = new AtomicBoolean(false);
        private final AtomicBoolean assemblyStarted = new AtomicBoolean(false);
        private final AtomicBoolean operationFinished = new AtomicBoolean(false);
        private volatile AutosavePersistentFailure persistentFailure;

        CaptureCoordinator(CaptureWork work, List<ChunkCaptureArea> areas) {
            this.work = Objects.requireNonNull(work, "Jigsaw Studio capture work");
            this.areas = List.copyOf(areas);
            this.remaining = new AtomicInteger(this.areas.size());
        }

        private ActiveStudio studio() {
            return work.studio();
        }

        private World world() {
            return work.world();
        }

        private JigsawStudioBay bay() {
            return work.bay();
        }

        private CaptureTarget captureTarget() {
            return work.captureTarget();
        }

        private Player player() {
            return work.player();
        }

        private UUID requestId() {
            return work.requestId();
        }

        private JigsawStudioSession.SaveIdentity saveIdentity() {
            return work.saveIdentity();
        }

        private List<ChunkCaptureArea> areas() {
            return areas;
        }

        private boolean stopped() {
            return failed.get();
        }

        private boolean failed() {
            return failed.get();
        }

        private void accept(ChunkSnapshot snapshot) {
            if (failed.get() || assemblyStarted.get()) {
                return;
            }
            ChunkCaptureArea area = snapshot.area();
            long key = chunkKey(area.chunkX(), area.chunkZ());
            if (snapshots.putIfAbsent(key, snapshot) != null) {
                fail("Jigsaw Studio received duplicate snapshot for bay chunk "
                        + area.chunkX() + "," + area.chunkZ() + ".", null);
                return;
            }
            int incomplete = remaining.decrementAndGet();
            if (incomplete < 0) {
                fail("Jigsaw Studio received more bay chunk snapshots than expected.", null);
                return;
            }
            if (incomplete == 0 && assemblyStarted.compareAndSet(false, true)) {
                List<ChunkSnapshot> completed = List.copyOf(snapshots.values());
                J.a(() -> assembleAndPersist(this, completed));
            }
        }

        void fail(String failure, Throwable exception) {
            if (!failed.compareAndSet(false, true)) {
                return;
            }
            if (exception != null) {
                IrisLogging.reportError(exception);
            }
            complete();
            message(player(), failure);
        }

        private void failPersistent(String failure, Throwable exception) {
            if (!failed.compareAndSet(false, true)) {
                return;
            }
            recordPersistentFailure(failure, exception);
            complete();
            message(player(), failure);
        }

        private void recordPersistentFailure(String failure, Throwable exception) {
            persistentFailure = new AutosavePersistentFailure(failure, exception);
        }

        private void complete() {
            if (operationFinished.compareAndSet(false, true)) {
                studio().generator().getSession().abortSave(saveIdentity());
                service.saveLifecycle.finishSave(requestId());
                service.autosaveScheduler.completeAutosaveAttempt(
                        studio(),
                        saveIdentity(),
                        work.autosaveFailureState(),
                        persistentFailure);
                service.playerContext.refreshWorkcellContext(studio().worldId(), saveIdentity().workcellId());
            }
        }
    }

    record CaptureWork(
            ActiveStudio studio,
            World world,
            JigsawStudioBay bay,
            CaptureTarget captureTarget,
            Player player,
            UUID requestId,
            JigsawStudioSession.SaveIdentity saveIdentity,
            AutosaveFailureState autosaveFailureState
    ) {
        CaptureWork {
            Objects.requireNonNull(studio, "Jigsaw Studio capture studio");
            Objects.requireNonNull(world, "Jigsaw Studio capture world");
            Objects.requireNonNull(bay, "Jigsaw Studio capture bay");
            Objects.requireNonNull(captureTarget, "Jigsaw Studio capture target");
            Objects.requireNonNull(requestId, "Jigsaw Studio capture request ID");
            Objects.requireNonNull(saveIdentity, "Jigsaw Studio capture save identity");
            Objects.requireNonNull(autosaveFailureState, "Jigsaw Studio capture autosave failure state");
        }
    }

    record CaptureTarget(
            JigsawStudioBounds bounds,
            IrisJigsawPiece piece,
            IrisObject object,
            int displayRotationQuarterTurns,
            boolean connectorsVisible
    ) {
        CaptureTarget {
            Objects.requireNonNull(bounds, "Jigsaw Studio capture bounds");
            Objects.requireNonNull(piece, "Jigsaw Studio capture piece");
            Objects.requireNonNull(object, "Jigsaw Studio capture object");
            displayRotationQuarterTurns = Math.floorMod(displayRotationQuarterTurns, 4);
        }
    }
}
