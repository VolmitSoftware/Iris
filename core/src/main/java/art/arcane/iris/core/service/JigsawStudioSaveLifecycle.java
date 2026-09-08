package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioActivation;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.service.JigsawStudioAutosaveScheduler.AutosaveKey;
import art.arcane.iris.core.service.JigsawStudioAutosaveScheduler.AutosavePersistentFailure;
import art.arcane.iris.core.service.JigsawStudioCaptureScheduler.CaptureTarget;
import art.arcane.iris.core.service.JigsawStudioService.ActiveStudio;
import art.arcane.iris.core.service.JigsawStudioService.CloseStart;
import art.arcane.iris.core.service.JigsawStudioService.ExportStart;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.iris.core.service.JigsawStudioCaptureScheduler.resolveCaptureTarget;
import static art.arcane.iris.core.service.JigsawStudioProtection.authorizeOwner;
import static art.arcane.iris.core.service.JigsawStudioService.AUTOSAVE_RETRY_TICKS;
import static art.arcane.iris.core.service.JigsawStudioService.failureMessage;
import static art.arcane.iris.core.service.JigsawStudioService.findBay;
import static art.arcane.iris.core.service.JigsawStudioService.message;

final class JigsawStudioSaveLifecycle {
    private static final int REPLACEMENT_CLOSE_WAIT_TICKS = 2_400;

    private final JigsawStudioService service;
    final Set<UUID> savesInProgress = new HashSet<>();
    final Set<UUID> exportsInProgress = new HashSet<>();
    final Set<UUID> closingRequests = new HashSet<>();
    final Set<UUID> discardingRequests = new HashSet<>();
    final Set<UUID> reopenRequiredRequests = ConcurrentHashMap.newKeySet();

    JigsawStudioSaveLifecycle(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    CloseStart tryBeginClose(UUID requestId, UUID ownerId, boolean discard) {
        if (requestId == null) {
            return CloseStart.NOT_ACTIVE;
        }
        service.tileWatcher.finalizeJigsawTileWatches(requestId);
        synchronized (service.saveLifecycleLock) {
            JigsawStudioActivation.Request request = JigsawStudioActivation.getRequest(requestId);
            JigsawStudioSession session = JigsawStudioActivation.getSession(requestId);
            if (request == null || session == null) {
                return CloseStart.NOT_ACTIVE;
            }
            if (request.ownerId() != null && !request.ownerId().equals(ownerId)) {
                return CloseStart.NOT_OWNER;
            }
            if (savesInProgress.contains(requestId)) {
                return CloseStart.SAVE_IN_PROGRESS;
            }
            if (service.graphMutations.graphMutationsInProgress.contains(requestId)) {
                return CloseStart.OPERATION_IN_PROGRESS;
            }
            if (exportsInProgress.contains(requestId)) {
                return CloseStart.OPERATION_IN_PROGRESS;
            }
            if (service.materializer.materializationsInProgress.contains(requestId)) {
                return CloseStart.OPERATION_IN_PROGRESS;
            }
            if (session.operationInProgress()) {
                return CloseStart.OPERATION_IN_PROGRESS;
            }
            if (service.tileWatcher.hasJigsawTileWatch(requestId)) {
                return CloseStart.OPERATION_IN_PROGRESS;
            }
            if (session.isDirty() && !discard) {
                return CloseStart.DIRTY;
            }
            closingRequests.add(requestId);
            if (discard) {
                discardingRequests.add(requestId);
            } else {
                discardingRequests.remove(requestId);
            }
            return CloseStart.STARTED;
        }
    }

    String closeProtectionFailure(UUID requestId) {
        if (requestId == null) {
            return null;
        }
        synchronized (service.saveLifecycleLock) {
            if (closingRequests.contains(requestId) && !savesInProgress.contains(requestId)) {
                return null;
            }
            if (savesInProgress.contains(requestId)) {
                return "The active Jigsaw Studio is saving and cannot be closed or replaced yet.";
            }
            if (service.graphMutations.graphMutationsInProgress.contains(requestId)) {
                return "The active Jigsaw Studio is updating its graph and cannot be closed or replaced yet.";
            }
            if (exportsInProgress.contains(requestId)) {
                return "The active Jigsaw Studio is exporting and cannot be closed or replaced yet.";
            }
            if (service.materializer.materializationsInProgress.contains(requestId)) {
                return "The active Jigsaw Studio is loading or restoring a variant and cannot be closed yet.";
            }
            JigsawStudioSession session = JigsawStudioActivation.getSession(requestId);
            if (session != null && session.operationInProgress()) {
                return "The active Jigsaw Studio is loading a variant and cannot be closed or replaced yet.";
            }
            if (service.tileWatcher.hasJigsawTileWatch(requestId)) {
                return "The active Jigsaw Studio is finalizing an open vanilla jigsaw-block editor.";
            }
            if (session != null && session.isDirty()) {
                return "The active Jigsaw Studio is waiting for autosave. Let it finish before closing.";
            }
            return "The active Jigsaw Studio is owner-controlled. Close it with /iris jigsaw close.";
        }
    }

    CompletableFuture<Void> awaitCloseForReplacement(UUID requestId, UUID ownerId) {
        CompletableFuture<Void> readiness = new CompletableFuture<>();
        awaitCloseForReplacement(requestId, ownerId, readiness, 0);
        return readiness;
    }

    private void awaitCloseForReplacement(
            UUID requestId,
            UUID ownerId,
            CompletableFuture<Void> readiness,
            int waitedTicks
    ) {
        if (readiness.isDone()) {
            return;
        }
        CloseStart closeStart = tryBeginClose(requestId, ownerId, false);
        switch (closeStart) {
            case STARTED, NOT_ACTIVE -> readiness.complete(null);
            case NOT_OWNER -> readiness.completeExceptionally(new IllegalStateException(
                    "The active Jigsaw Studio is owned by another player session."));
            case DIRTY, SAVE_IN_PROGRESS, OPERATION_IN_PROGRESS -> {
                if (waitedTicks == 0) {
                    service.autosaveScheduler.expediteAutosaves(requestId);
                }
                if (waitedTicks >= REPLACEMENT_CLOSE_WAIT_TICKS) {
                    String failure = closeProtectionFailure(requestId);
                    readiness.completeExceptionally(new IllegalStateException(
                            failure == null
                                    ? "The active Jigsaw Studio did not become ready for replacement."
                                    : failure));
                    return;
                }
                try {
                    J.s(() -> awaitCloseForReplacement(
                            requestId,
                            ownerId,
                            readiness,
                            waitedTicks + AUTOSAVE_RETRY_TICKS), AUTOSAVE_RETRY_TICKS);
                } catch (Throwable exception) {
                    readiness.completeExceptionally(exception);
                }
            }
        }
    }

    void cancelClose(UUID requestId) {
        if (requestId == null) {
            return;
        }
        synchronized (service.saveLifecycleLock) {
            closingRequests.remove(requestId);
            discardingRequests.remove(requestId);
        }
    }

    SaveStart tryBeginSave(UUID requestId) {
        Objects.requireNonNull(requestId, "Jigsaw Studio save request ID");
        synchronized (service.saveLifecycleLock) {
            if (closingRequests.contains(requestId)) {
                return SaveStart.CLOSING;
            }
            if (service.graphMutations.graphMutationsInProgress.contains(requestId)) {
                return SaveStart.GRAPH_OPERATION;
            }
            if (exportsInProgress.contains(requestId)) {
                return SaveStart.EXPORT_OPERATION;
            }
            if (service.materializer.materializationsInProgress.contains(requestId)) {
                return SaveStart.VARIANT_OPERATION;
            }
            if (!savesInProgress.add(requestId)) {
                return SaveStart.IN_PROGRESS;
            }
            return SaveStart.STARTED;
        }
    }

    void finishSave(UUID requestId) {
        if (requestId == null) {
            return;
        }
        synchronized (service.saveLifecycleLock) {
            savesInProgress.remove(requestId);
        }
    }

    ExportStart tryBeginExport(UUID requestId, UUID ownerId) {
        Objects.requireNonNull(requestId, "Jigsaw Studio export request ID");
        Objects.requireNonNull(ownerId, "Jigsaw Studio export owner ID");
        service.tileWatcher.finalizeJigsawTileWatches(requestId);
        synchronized (service.saveLifecycleLock) {
            JigsawStudioActivation.Request request = JigsawStudioActivation.getRequest(requestId);
            JigsawStudioSession session = JigsawStudioActivation.getSession(requestId);
            if (request == null || session == null) {
                return ExportStart.NOT_ACTIVE;
            }
            if (request.ownerId() != null && !request.ownerId().equals(ownerId)) {
                return ExportStart.NOT_OWNER;
            }
            if (closingRequests.contains(requestId)) {
                return ExportStart.CLOSING;
            }
            if (savesInProgress.contains(requestId)) {
                return ExportStart.SAVE_IN_PROGRESS;
            }
            if (service.graphMutations.graphMutationsInProgress.contains(requestId)) {
                return ExportStart.OPERATION_IN_PROGRESS;
            }
            if (service.materializer.materializationsInProgress.contains(requestId) || session.operationInProgress()) {
                return ExportStart.OPERATION_IN_PROGRESS;
            }
            if (service.tileWatcher.hasJigsawTileWatch(requestId)) {
                return ExportStart.OPERATION_IN_PROGRESS;
            }
            if (session.isDirty()) {
                return ExportStart.DIRTY;
            }
            if (!exportsInProgress.add(requestId)) {
                return ExportStart.IN_PROGRESS;
            }
            return ExportStart.STARTED;
        }
    }

    void finishExport(UUID requestId) {
        if (requestId == null) {
            return;
        }
        synchronized (service.saveLifecycleLock) {
            exportsInProgress.remove(requestId);
        }
    }

    boolean saveSelected(Player player) {
        if (player == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> saveSelected(player));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        if (!authorizeOwner(player, studio)) {
            return false;
        }
        if (reopenRequiredRequests.contains(studio.generator().getRequest().requestId())) {
            message(player, "Close and reopen Jigsaw Studio before loading variants in the resized layout.");
            return false;
        }
        if (service.graphMutations.graphMutationInProgress(studio.generator().getRequest().requestId())) {
            message(player, "Wait for the current Jigsaw Studio graph update to finish.");
            return false;
        }
        UUID requestId = studio.generator().getRequest().requestId();
        service.tileWatcher.finalizeJigsawTileWatches(requestId);
        if (service.tileWatcher.hasJigsawTileWatch(requestId)) {
            message(player, "Wait for the open vanilla jigsaw-block editor to finish its final snapshot.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String selected = session.selectedBayId().orElse(null);
        if (selected == null) {
            Location location = player.getLocation();
            JigsawStudioBay underPlayer = session.layout().findAt(
                    location.getBlockX(), location.getBlockY(), location.getBlockZ());
            if (underPlayer != null) {
                selected = underPlayer.stableId();
                session.selectBay(selected);
            }
        }
        if (selected == null) {
            message(player, "Stand inside or select a Jigsaw Studio workcell before saving.");
            return false;
        }
        return saveBay(player, selected);
    }

    boolean saveBay(Player player, String bayId) {
        if (player == null || bayId == null || bayId.isBlank()) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> saveBay(player, bayId));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        if (!authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioBay bay = findBay(studio.generator().getSession().layout(), bayId);
        if (bay == null) {
            message(player, "No Jigsaw Studio bay matches '" + bayId + "'.");
            return false;
        }
        return startSave(studio, player.getWorld(), bay, player, true) == SaveAttempt.STARTED;
    }

    SaveAttempt startSave(
            ActiveStudio studio,
            World world,
            JigsawStudioBay bay,
            Player player,
            boolean report
    ) {
        UUID requestId = studio.generator().getRequest().requestId();
        if (reopenRequiredRequests.contains(requestId)) {
            JigsawStudioService.report(
                    player, report, "Close and reopen Jigsaw Studio before saving the resized layout.");
            return SaveAttempt.DEFERRED;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioVariant activeVariant = session.activeVariant(bay.stableId()).orElse(null);
        if (activeVariant == null) {
            JigsawStudioService.report(
                    player, report, "Workcell '" + bay.stableId() + "' has no active variant to save.");
            return SaveAttempt.DEFERRED;
        }
        if (!activeVariant.owned()) {
            JigsawStudioService.report(player, report, "Variant '" + activeVariant.pieceKey()
                    + "' is read-only. Adopt or clone its graph before editing it.");
            return SaveAttempt.DEFERRED;
        }
        BayReadiness readiness = studio.population(bay).readiness();
        if (!readiness.ready()) {
            JigsawStudioService.report(player, report, readinessMessage(bay, readiness));
            return readiness.failure().isEmpty() ? SaveAttempt.RETRY : SaveAttempt.DEFERRED;
        }
        CaptureTarget captureTarget;
        try {
            captureTarget = resolveCaptureTarget(studio, bay, activeVariant);
        } catch (IOException exception) {
            String failure = "Jigsaw Studio cannot capture this workcell: " + failureMessage(exception);
            JigsawStudioService.report(player, report, failure);
            return service.autosaveScheduler.retainCurrentAutosaveFailure(
                    studio,
                    bay.stableId(),
                    new AutosavePersistentFailure(failure, exception))
                    ? SaveAttempt.PERSISTENT_FAILURE
                    : SaveAttempt.DEFERRED;
        }
        JigsawStudioSession.SaveStart sessionSave = session.beginSave(bay.stableId());
        if (sessionSave.status() != JigsawStudioSession.SaveStatus.STARTED) {
            JigsawStudioService.report(player, report, switch (sessionSave.status()) {
                case SWITCH_IN_PROGRESS -> "This workcell is loading another variant.";
                case SAVE_IN_PROGRESS -> "This workcell is already saving.";
                case NO_ACTIVE_VARIANT -> "This workcell has no active variant.";
                case UNKNOWN_WORKCELL -> "This workcell is no longer active.";
                case STARTED -> "The workcell save could not start.";
            });
            return sessionSave.status() == JigsawStudioSession.SaveStatus.SAVE_IN_PROGRESS
                    || sessionSave.status() == JigsawStudioSession.SaveStatus.SWITCH_IN_PROGRESS
                    ? SaveAttempt.RETRY
                    : SaveAttempt.DEFERRED;
        }
        JigsawStudioSession.SaveIdentity saveIdentity = sessionSave.identity().orElseThrow();
        SaveStart saveStart = tryBeginSave(requestId);
        if (saveStart == SaveStart.CLOSING) {
            session.abortSave(saveIdentity);
            JigsawStudioService.report(player, report, "This Jigsaw Studio is closing and cannot start another save.");
            return SaveAttempt.DEFERRED;
        }
        if (saveStart == SaveStart.IN_PROGRESS) {
            session.abortSave(saveIdentity);
            JigsawStudioService.report(player, report, "A save is already running for this Jigsaw Studio.");
            return SaveAttempt.RETRY;
        }
        if (saveStart == SaveStart.GRAPH_OPERATION) {
            session.abortSave(saveIdentity);
            JigsawStudioService.report(player, report, "Wait for the current Jigsaw Studio graph update to finish.");
            return SaveAttempt.RETRY;
        }
        if (saveStart == SaveStart.EXPORT_OPERATION) {
            session.abortSave(saveIdentity);
            JigsawStudioService.report(player, report, "Wait for the current Jigsaw Studio export to finish.");
            return SaveAttempt.RETRY;
        }
        if (saveStart == SaveStart.VARIANT_OPERATION) {
            session.abortSave(saveIdentity);
            JigsawStudioService.report(
                    player, report, "Wait for the current Jigsaw Studio variant load or rollback to finish.");
            return SaveAttempt.RETRY;
        }
        if (report) {
            session.selectBay(bay.stableId());
        }
        service.playerContext.refreshWorkcellContext(studio.worldId(), bay.stableId());
        JigsawStudioService.report(player, report, "Capturing variant '" + activeVariant.pieceKey() + "' from "
                + bay.stableId() + "...");
        try {
            boolean scheduled = service.captureScheduler.scheduleCapture(
                    studio,
                    world,
                    bay,
                    captureTarget,
                    player,
                    requestId,
                    saveIdentity,
                    service.autosaveScheduler.autosaveFailureState(studio, saveIdentity));
            return scheduled ? SaveAttempt.STARTED : SaveAttempt.DEFERRED;
        } catch (Throwable exception) {
            session.abortSave(saveIdentity);
            finishSave(requestId);
            service.playerContext.refreshWorkcellContext(studio.worldId(), bay.stableId());
            IrisLogging.reportError(exception);
            JigsawStudioService.report(player, report, "Jigsaw Studio could not schedule the workcell capture: "
                    + failureMessage(exception));
            return SaveAttempt.DEFERRED;
        }
    }

    boolean requiresLifecycleDrain(ActiveStudio studio) {
        UUID requestId = studio.generator().getRequest().requestId();
        if (service.tileWatcher.hasJigsawTileWatch(requestId)
                || studio.generator().getSession().isDirty()
                || studio.generator().getSession().operationInProgress()) {
            return true;
        }
        for (AutosaveKey key : service.autosaveScheduler.autosaves.keySet()) {
            if (requestId.equals(key.requestId())) {
                return true;
            }
        }
        synchronized (service.saveLifecycleLock) {
            return savesInProgress.contains(requestId)
                    || service.graphMutations.graphMutationsInProgress.contains(requestId)
                    || service.materializer.materializationsInProgress.contains(requestId)
                    || exportsInProgress.contains(requestId);
        }
    }

    boolean discardingRequest(UUID requestId) {
        synchronized (service.saveLifecycleLock) {
            return discardingRequests.contains(requestId);
        }
    }

    boolean isCurrentSave(
            ActiveStudio studio,
            UUID requestId,
            JigsawStudioSession.SaveIdentity identity
    ) {
        return service.isCurrentRequest(studio, requestId)
                && studio.generator().getSession().isSaveCurrent(identity);
    }

    private static String readinessMessage(JigsawStudioBay bay, BayReadiness readiness) {
        if (!readiness.failure().isEmpty()) {
            return "Bay '" + bay.stableId() + "' is invalid and cannot be saved: " + readiness.failure();
        }
        return "Bay '" + bay.stableId() + "' is not ready to save: "
                + readiness.generatedChunks() + "/" + readiness.requiredChunks() + " chunk(s) populated, "
                + readiness.hydratedChunks() + "/" + readiness.requiredChunks()
                + " chunk(s) hydrated. Visit the whole bay and wait for its markers to finish loading.";
    }

    record BayReadiness(
            boolean ready,
            String failure,
            int requiredChunks,
            int generatedChunks,
            int hydratedChunks
    ) {
    }

    enum SaveStart {
        STARTED,
        IN_PROGRESS,
        CLOSING,
        GRAPH_OPERATION,
        EXPORT_OPERATION,
        VARIANT_OPERATION
    }

    enum SaveAttempt {
        STARTED,
        RETRY,
        DEFERRED,
        PERSISTENT_FAILURE
    }
}
