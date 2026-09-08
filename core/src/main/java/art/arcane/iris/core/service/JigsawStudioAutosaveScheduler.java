package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioActivation;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBounds;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.service.JigsawStudioCapture.ChunkCaptureArea;
import art.arcane.iris.core.service.JigsawStudioCapture.ChunkSnapshot;
import art.arcane.iris.core.service.JigsawStudioCaptureScheduler.CaptureCoordinator;
import art.arcane.iris.core.service.JigsawStudioCaptureScheduler.CaptureTarget;
import art.arcane.iris.core.service.JigsawStudioCaptureScheduler.CaptureWork;
import art.arcane.iris.core.service.JigsawStudioMaterializer.VariantReloadRequest;
import art.arcane.iris.core.service.JigsawStudioSaveLifecycle.BayReadiness;
import art.arcane.iris.core.service.JigsawStudioSaveLifecycle.SaveAttempt;
import art.arcane.iris.core.service.JigsawStudioSaveLifecycle.SaveStart;
import art.arcane.iris.core.service.JigsawStudioService.ActiveStudio;
import art.arcane.iris.core.service.JigsawStudioService.CommandGraphMutationResult;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static art.arcane.iris.core.service.JigsawStudioCapture.captureChunkIntersection;
import static art.arcane.iris.core.service.JigsawStudioCapture.chunkIntersections;
import static art.arcane.iris.core.service.JigsawStudioCaptureScheduler.resolveCaptureTarget;
import static art.arcane.iris.core.service.JigsawStudioGraphMutations.loadMappedLayout;
import static art.arcane.iris.core.service.JigsawStudioProtection.authorizeOwner;
import static art.arcane.iris.core.service.JigsawStudioService.AUTOSAVE_RETRY_TICKS;
import static art.arcane.iris.core.service.JigsawStudioService.failureMessage;
import static art.arcane.iris.core.service.JigsawStudioService.message;
import static art.arcane.iris.core.service.JigsawStudioService.writeFailure;

final class JigsawStudioAutosaveScheduler {
    static final int AUTOSAVE_DEBOUNCE_TICKS = 40;

    private static final List<Integer> AUTOSAVE_PERSISTENT_RETRY_DELAYS =
            List.of(40, 80, 160, 320, 600);

    private final JigsawStudioService service;
    final Map<AutosaveKey, AutosaveTicket> autosaves = new ConcurrentHashMap<>();

    JigsawStudioAutosaveScheduler(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    boolean undoAutosave(Player player) {
        if (player == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> undoAutosave(player));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String reservationFailure = service.graphMutations.beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        Map<String, VariantReloadRequest> activeReloads = new HashMap<>();
        for (JigsawStudioBay workcell : session.layout().bays()) {
            session.activeVariant(workcell.stableId()).ifPresent(variant -> activeReloads.put(
                    variant.pieceKey(),
                    new VariantReloadRequest(
                            workcell.stableId(),
                            studio.generator().renderBay(workcell))));
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        message(player, "Restoring the previous Jigsaw Studio autosave iteration...");
        return service.graphMutations.scheduleGraphMutation(
                player,
                studio,
                () -> {
                    Path packRoot = request.source().getDataFolder().toPath();
                    JigsawStudioHistoryStore.UndoResult result = new JigsawStudioHistoryStore(
                            packRoot,
                            request.structureKey()).undoLatest();
                    if (!result.available()) {
                        return new CommandGraphMutationResult(
                                session.layout(),
                                "",
                                "",
                                "No earlier autosave iteration is available.");
                    }
                    if (!result.successful()) {
                        throw new IOException("Jigsaw Studio undo failed: "
                                + writeFailure(result.writeResult()));
                    }
                    request.source().invalidateStructureResources();
                    JigsawStudioLayout restoredLayout = loadMappedLayout(studio);
                    VariantReloadRequest reload = activeReloads.get(result.pieceKey());
                    Optional<VariantReloadRequest> activeReload = reload == null
                            || restoredLayout.get(reload.workcellId()) == null
                            ? Optional.empty()
                            : Optional.of(reload);
                    String warning = result.warning().isEmpty()
                            ? ""
                            : " History cleanup warning: " + result.warning();
                    return new CommandGraphMutationResult(
                            restoredLayout,
                            "",
                            "",
                            Map.of(),
                            activeReload,
                            "Restored the previous autosave iteration. "
                                    + result.remainingIterations() + " earlier iteration(s) remain."
                                    + warning);
                });
    }

    boolean flushAutosave(Player player, String workcellId) {
        if (player == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> flushAutosave(player, workcellId));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String targetId = workcellId == null || workcellId.isBlank()
                ? session.selectedBayId().orElse("")
                : workcellId;
        JigsawStudioBay bay = session.layout().get(targetId);
        if (bay == null) {
            message(player, "Select a Jigsaw Studio workcell before saving now.");
            return false;
        }
        UUID requestId = studio.generator().getRequest().requestId();
        service.tileWatcher.finalizeJigsawTileWatches(requestId);
        if (service.tileWatcher.hasJigsawTileWatch(requestId)) {
            message(player, "Wait for the open vanilla jigsaw-block editor to finish its final snapshot.");
            return false;
        }
        SaveAttempt attempt = service.saveLifecycle.startSave(studio, studio.world(), bay, player, true);
        if (attempt == SaveAttempt.STARTED) {
            return true;
        }
        if (attempt == SaveAttempt.PERSISTENT_FAILURE) {
            return false;
        }
        AutosaveTicket ticket = autosaves.get(new AutosaveKey(requestId, bay.stableId()));
        if (ticket != null) {
            expediteAutosave(
                    ticket,
                    attempt == SaveAttempt.RETRY
                            ? AUTOSAVE_RETRY_TICKS
                            : AUTOSAVE_DEBOUNCE_TICKS);
        }
        return false;
    }

    void scheduleAutosave(
            ActiveStudio studio,
            JigsawStudioBay bay,
            JigsawStudioSession.DirtyIdentity identity,
            int delayTicks
    ) {
        UUID requestId = studio.generator().getRequest().requestId();
        AutosaveKey key = new AutosaveKey(requestId, bay.stableId());
        AutosaveTicket ticket = new AutosaveTicket(
                key,
                studio,
                identity,
                new AtomicBoolean(false),
                new AtomicBoolean(false));
        autosaves.put(key, ticket);
        scheduleAutosave(ticket, delayTicks);
    }

    void scheduleAutosave(AutosaveTicket ticket, int delayTicks) {
        if (autosaves.get(ticket.key()) != ticket
                || !ticket.scheduled().compareAndSet(false, true)) {
            return;
        }
        JigsawStudioBay bay = resolveAutosaveBay(ticket);
        if (bay == null) {
            ticket.scheduled().set(false);
            return;
        }
        JigsawStudioBounds bounds = bay.bounds();
        boolean scheduled = J.runRegion(
                ticket.studio().world(),
                bounds.originX() >> 4,
                bounds.originZ() >> 4,
                () -> runAutosave(ticket),
                delayTicks);
        if (!scheduled) {
            ticket.scheduled().set(false);
            if (ticket.scheduleFailureLogged().compareAndSet(false, true)) {
                IrisLogging.warn("Jigsaw Studio autosave could not schedule workcell %s for request %s; Iris will keep retrying",
                        ticket.key().workcellId(), ticket.key().requestId());
            }
            scheduleAutosaveReconciliation(ticket);
        }
    }

    private JigsawStudioBay resolveAutosaveBay(AutosaveTicket ticket) {
        if (autosaves.get(ticket.key()) != ticket) {
            return null;
        }
        JigsawStudioBay bay = resolveCurrentAutosaveBay(
                ticket.studio().generator().getSession(),
                ticket.key().workcellId());
        if (bay != null) {
            return bay;
        }
        if (autosaves.remove(ticket.key(), ticket)) {
            reportAutosaveFailure(
                    ticket,
                    new AutosavePersistentFailure(
                            "workcell is absent from the current Studio layout; pending ticket retired",
                            null));
        }
        return null;
    }

    static JigsawStudioBay resolveCurrentAutosaveBay(
            JigsawStudioSession session,
            String workcellId
    ) {
        JigsawStudioSession activeSession = Objects.requireNonNull(
                session,
                "Jigsaw Studio autosave session");
        return activeSession.layout().get(Objects.requireNonNull(
                workcellId,
                "Jigsaw Studio autosave workcell ID"));
    }

    boolean retainCurrentAutosaveFailure(
            ActiveStudio studio,
            String workcellId,
            AutosavePersistentFailure failure
    ) {
        AutosaveKey key = new AutosaveKey(
                studio.generator().getRequest().requestId(),
                workcellId);
        AutosaveTicket ticket = autosaves.get(key);
        return ticket != null && retainPersistentAutosaveFailure(ticket, failure);
    }

    AutosaveFailureState autosaveFailureState(
            ActiveStudio studio,
            JigsawStudioSession.SaveIdentity saveIdentity
    ) {
        AutosaveKey key = new AutosaveKey(
                studio.generator().getRequest().requestId(),
                saveIdentity.workcellId());
        AutosaveTicket ticket = autosaves.get(key);
        return ticket != null && matchesSaveIdentity(ticket.identity(), saveIdentity)
                ? ticket.failureState()
                : new AutosaveFailureState();
    }

    private boolean retainPersistentAutosaveFailure(
            AutosaveTicket ticket,
            AutosavePersistentFailure failure
    ) {
        if (autosaves.get(ticket.key()) != ticket
                || !service.isCurrentRequest(ticket.studio(), ticket.key().requestId())
                || !ticket.studio().generator().getSession().isDirtyCurrent(ticket.identity())) {
            return false;
        }
        AutosaveFailureDecision decision = reportAutosaveFailure(ticket, failure);
        expediteAutosave(ticket, decision.retryTicks());
        return true;
    }

    private static AutosaveFailureDecision reportAutosaveFailure(
            AutosaveTicket ticket,
            AutosavePersistentFailure failure
    ) {
        JigsawStudioActivation.Request request = ticket.studio().generator().getRequest();
        return recordPersistentAutosaveFailure(
                ticket.failureState(),
                ticket.key().requestId(),
                request.structureKey(),
                ticket.key().workcellId(),
                ticket.identity().variantKey(),
                failure.detail(),
                failure.cause());
    }

    private static void reportPersistentSaveFailure(
            ActiveStudio studio,
            JigsawStudioSession.SaveIdentity saveIdentity,
            AutosaveFailureState failureState,
            AutosavePersistentFailure failure
    ) {
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        recordPersistentAutosaveFailure(
                failureState,
                request.requestId(),
                request.structureKey(),
                saveIdentity.workcellId(),
                saveIdentity.variantKey(),
                failure.detail(),
                failure.cause());
    }

    static AutosaveFailureDecision recordPersistentAutosaveFailure(
            AutosaveFailureState state,
            UUID requestId,
            String structureKey,
            String workcellId,
            String pieceKey,
            String detail,
            Throwable cause
    ) {
        AutosaveFailureState activeState = Objects.requireNonNull(
                state,
                "Jigsaw Studio autosave failure state");
        AutosaveFailureDecision decision = activeState.recordPersistentFailure();
        if (!decision.logFailure()) {
            return decision;
        }
        String context = autosaveFailureContext(
                requestId,
                structureKey,
                workcellId,
                pieceKey,
                detail);
        if (cause == null) {
            IrisLogging.warn("%s", context);
        } else {
            IrisLogging.reportError(context, cause);
        }
        return decision;
    }

    static String autosaveFailureContext(
            UUID requestId,
            String structureKey,
            String workcellId,
            String pieceKey,
            String detail
    ) {
        return "Jigsaw Studio save failure: request="
                + Objects.requireNonNull(requestId, "Jigsaw Studio autosave request ID")
                + " structure="
                + Objects.requireNonNull(structureKey, "Jigsaw Studio autosave structure key")
                + " workcell="
                + Objects.requireNonNull(workcellId, "Jigsaw Studio autosave workcell ID")
                + " piece="
                + Objects.requireNonNull(pieceKey, "Jigsaw Studio autosave piece key")
                + " failure="
                + Objects.requireNonNull(detail, "Jigsaw Studio autosave failure detail");
    }

    static int persistentAutosaveRetryTicks(int failureCount) {
        int index = Math.max(0, Math.min(
                failureCount - 1,
                AUTOSAVE_PERSISTENT_RETRY_DELAYS.size() - 1));
        return AUTOSAVE_PERSISTENT_RETRY_DELAYS.get(index);
    }

    private void scheduleAutosaveReconciliation(AutosaveTicket ticket) {
        try {
            J.s(() -> {
                if (autosaves.get(ticket.key()) == ticket
                        && !ticket.scheduled().get()) {
                    scheduleAutosave(ticket, AUTOSAVE_RETRY_TICKS);
                }
            }, AUTOSAVE_RETRY_TICKS);
        } catch (Throwable exception) {
            IrisLogging.reportError(exception);
        }
    }

    private void runAutosave(AutosaveTicket ticket) {
        if (autosaves.get(ticket.key()) != ticket) {
            return;
        }
        ticket.scheduled().set(false);
        ActiveStudio studio = ticket.studio();
        JigsawStudioSession session = studio.generator().getSession();
        if (!service.isCurrentRequest(studio, ticket.key().requestId())) {
            autosaves.remove(ticket.key(), ticket);
            return;
        }
        JigsawStudioBay bay = resolveAutosaveBay(ticket);
        if (bay == null) {
            return;
        }
        if (!session.isDirtyCurrent(ticket.identity())) {
            autosaves.remove(ticket.key(), ticket);
            return;
        }
        SaveAttempt attempt = service.saveLifecycle.startSave(studio, studio.world(), bay, null, false);
        if (attempt == SaveAttempt.STARTED || attempt == SaveAttempt.PERSISTENT_FAILURE) {
            return;
        }
        scheduleAutosave(
                ticket,
                attempt == SaveAttempt.RETRY
                        ? AUTOSAVE_RETRY_TICKS
                        : AUTOSAVE_DEBOUNCE_TICKS);
    }

    void completeAutosaveAttempt(
            ActiveStudio studio,
            JigsawStudioSession.SaveIdentity saveIdentity,
            AutosaveFailureState failureState,
            AutosavePersistentFailure persistentFailure
    ) {
        AutosaveKey key = new AutosaveKey(
                studio.generator().getRequest().requestId(),
                saveIdentity.workcellId());
        AutosaveTicket ticket = autosaves.get(key);
        if (ticket == null || !matchesSaveIdentity(ticket.identity(), saveIdentity)) {
            if (persistentFailure != null) {
                reportPersistentSaveFailure(studio, saveIdentity, failureState, persistentFailure);
            }
            return;
        }
        if (resolveAutosaveBay(ticket) == null) {
            return;
        }
        if (!studio.generator().getSession().isDirtyCurrent(ticket.identity())) {
            autosaves.remove(key, ticket);
            return;
        }
        if (persistentFailure != null) {
            retainPersistentAutosaveFailure(ticket, persistentFailure);
            return;
        }
        scheduleAutosave(ticket, AUTOSAVE_RETRY_TICKS);
    }

    private static boolean matchesSaveIdentity(
            JigsawStudioSession.DirtyIdentity dirtyIdentity,
            JigsawStudioSession.SaveIdentity saveIdentity
    ) {
        return dirtyIdentity.sessionId().equals(saveIdentity.sessionId())
                && dirtyIdentity.workcellId().equals(saveIdentity.workcellId())
                && dirtyIdentity.variantKey().equals(saveIdentity.variantKey())
                && dirtyIdentity.loadGeneration() == saveIdentity.loadGeneration()
                && dirtyIdentity.mutationGeneration() == saveIdentity.mutationGeneration();
    }

    void drainAutosavesBeforeDisable() {
        for (ActiveStudio studio : List.copyOf(service.studios.values())) {
            try {
                drainAutosavesBeforeRemoval(studio);
            } catch (Throwable exception) {
                IrisLogging.reportError(
                        "Failed to drain Jigsaw Studio autosaves in world "
                                + studio.worldId() + " during shutdown.",
                        exception);
            }
        }
        if (!autosaves.isEmpty()) {
            IrisLogging.warn("Jigsaw Studio disabled with %d autosave operation(s) still pending after the final drain attempt",
                    autosaves.size());
        }
    }

    void drainAutosavesBeforeRemoval(ActiveStudio studio) {
        if (studio == null) {
            return;
        }
        UUID requestId = studio.generator().getRequest().requestId();
        if (service.saveLifecycle.discardingRequest(requestId)) {
            return;
        }
        if (J.isFolia() || !J.isPrimaryThread()) {
            expediteAutosaves(requestId);
            return;
        }
        for (AutosaveTicket ticket : List.copyOf(autosaves.values())) {
            if (requestId.equals(ticket.key().requestId())) {
                drainAutosaveSynchronously(ticket);
            }
        }
    }

    private boolean drainAutosaveSynchronously(AutosaveTicket ticket) {
        if (autosaves.get(ticket.key()) != ticket) {
            return true;
        }
        ActiveStudio studio = ticket.studio();
        JigsawStudioSession session = studio.generator().getSession();
        if (!service.isCurrentRequest(studio, ticket.key().requestId())) {
            autosaves.remove(ticket.key(), ticket);
            return true;
        }
        JigsawStudioBay bay = resolveAutosaveBay(ticket);
        if (bay == null) {
            return false;
        }
        if (!session.isDirtyCurrent(ticket.identity())) {
            autosaves.remove(ticket.key(), ticket);
            return true;
        }
        JigsawStudioVariant activeVariant = session.activeVariant(bay.stableId()).orElse(null);
        if (activeVariant == null || !activeVariant.owned()) {
            return false;
        }
        BayReadiness readiness = studio.population(bay).readiness();
        if (!readiness.ready()) {
            return false;
        }
        CaptureTarget captureTarget;
        try {
            captureTarget = resolveCaptureTarget(studio, bay, activeVariant);
        } catch (IOException exception) {
            retainPersistentAutosaveFailure(
                    ticket,
                    new AutosavePersistentFailure(
                            "Jigsaw Studio cannot capture this workcell: " + failureMessage(exception),
                            exception));
            return false;
        }
        JigsawStudioSession.SaveStart sessionSave = session.beginSave(bay.stableId());
        if (sessionSave.status() != JigsawStudioSession.SaveStatus.STARTED) {
            return false;
        }
        JigsawStudioSession.SaveIdentity saveIdentity = sessionSave.identity().orElseThrow();
        if (service.saveLifecycle.tryBeginSave(ticket.key().requestId()) != SaveStart.STARTED) {
            session.abortSave(saveIdentity);
            return false;
        }
        List<ChunkCaptureArea> areas = chunkIntersections(captureTarget.bounds());
        CaptureWork work = new CaptureWork(
                studio,
                studio.world(),
                bay,
                captureTarget,
                null,
                ticket.key().requestId(),
                saveIdentity,
                ticket.failureState());
        CaptureCoordinator coordinator =
                service.captureScheduler.coordinator(work, areas);
        List<ChunkSnapshot> snapshots = new ArrayList<>(areas.size());
        try {
            for (ChunkCaptureArea area : areas) {
                if (!studio.world().isChunkLoaded(area.chunkX(), area.chunkZ())) {
                    throw new IOException("Workcell chunk " + area.chunkX() + "," + area.chunkZ()
                            + " is not loaded during the final autosave drain");
                }
                snapshots.add(captureChunkIntersection(
                        studio.world(),
                        captureTarget.bounds(),
                        captureTarget.piece(),
                        captureTarget.object(),
                        area,
                        captureTarget.displayRotationQuarterTurns(),
                        captureTarget.connectorsVisible()));
            }
            service.captureScheduler.assembleAndPersist(coordinator, snapshots);
        } catch (Throwable exception) {
            coordinator.fail("Jigsaw Studio final autosave drain failed: "
                    + failureMessage(exception), exception);
        }
        return !session.isDirtyCurrent(ticket.identity());
    }

    void expediteAutosaves(UUID requestId) {
        if (requestId == null) {
            return;
        }
        for (AutosaveTicket current : List.copyOf(autosaves.values())) {
            if (!requestId.equals(current.key().requestId())) {
                continue;
            }
            expediteAutosave(current, 0);
        }
    }

    private void expediteAutosave(AutosaveTicket current, int delayTicks) {
        AutosaveTicket expedited = new AutosaveTicket(
                current.key(),
                current.studio(),
                current.identity(),
                new AtomicBoolean(false),
                current.scheduleFailureLogged(),
                current.failureState());
        if (autosaves.replace(current.key(), current, expedited)) {
            scheduleAutosave(expedited, delayTicks);
        }
    }

    void clearAutosaves(UUID requestId) {
        if (requestId != null) {
            autosaves.keySet().removeIf(key -> key.requestId().equals(requestId));
        }
    }

    record AutosaveKey(UUID requestId, String workcellId) {
        AutosaveKey {
            Objects.requireNonNull(requestId, "Jigsaw Studio autosave request ID");
            Objects.requireNonNull(workcellId, "Jigsaw Studio autosave workcell ID");
        }
    }

    static final class AutosaveFailureState {
        private final AtomicInteger persistentFailures = new AtomicInteger();
        private final AtomicBoolean failureLogged = new AtomicBoolean(false);

        AutosaveFailureDecision recordPersistentFailure() {
            int failureCount = persistentFailures.incrementAndGet();
            return new AutosaveFailureDecision(
                    persistentAutosaveRetryTicks(failureCount),
                    failureLogged.compareAndSet(false, true));
        }
    }

    record AutosaveFailureDecision(int retryTicks, boolean logFailure) {
    }

    record AutosavePersistentFailure(String detail, Throwable cause) {
        AutosavePersistentFailure {
            Objects.requireNonNull(detail, "Jigsaw Studio persistent autosave failure detail");
        }
    }

    private record AutosaveTicket(
            AutosaveKey key,
            ActiveStudio studio,
            JigsawStudioSession.DirtyIdentity identity,
            AtomicBoolean scheduled,
            AtomicBoolean scheduleFailureLogged,
            AutosaveFailureState failureState
    ) {
        private AutosaveTicket(
                AutosaveKey key,
                ActiveStudio studio,
                JigsawStudioSession.DirtyIdentity identity,
                AtomicBoolean scheduled,
                AtomicBoolean scheduleFailureLogged
        ) {
            this(
                    key,
                    studio,
                    identity,
                    scheduled,
                    scheduleFailureLogged,
                    new AutosaveFailureState());
        }

        private AutosaveTicket {
            Objects.requireNonNull(key, "Jigsaw Studio autosave key");
            Objects.requireNonNull(studio, "Jigsaw Studio autosave studio");
            Objects.requireNonNull(identity, "Jigsaw Studio autosave dirty identity");
            Objects.requireNonNull(scheduled, "Jigsaw Studio autosave schedule state");
            Objects.requireNonNull(scheduleFailureLogged, "Jigsaw Studio autosave schedule warning state");
            Objects.requireNonNull(failureState, "Jigsaw Studio autosave failure state");
        }
    }
}
