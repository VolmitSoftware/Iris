package art.arcane.iris.core.service;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariantCatalog;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Which Jigsaw Studio requests are active for each pack, which one is being opened, and the staged replacement of an
 * active one. One registry belongs to one studio service; it holds no platform state.
 */
public final class JigsawStudioRegistry {
    private final Map<String, ActiveRequest> active = new ConcurrentHashMap<>();
    private final AtomicReference<UUID> openingOwner = new AtomicReference<>();
    private final AtomicReference<StagedState> staged = new AtomicReference<>();

    public JigsawStudioRequest activate(
            String packKey,
            String structureKey,
            JigsawStudioMode mode,
            JigsawStudioCompatibilityTarget compatibilityTarget,
            JigsawStudioCellDimensions cellDimensions,
            IrisData source
    ) {
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                mode,
                cellDimensions,
                JigsawStudioVariantCatalog.empty());
        return activate(
                packKey,
                structureKey,
                mode,
                compatibilityTarget,
                cellDimensions,
                source,
                layout,
                null
        );
    }

    public JigsawStudioRequest activate(
            String packKey,
            String structureKey,
            JigsawStudioMode mode,
            JigsawStudioCompatibilityTarget compatibilityTarget,
            JigsawStudioCellDimensions cellDimensions,
            IrisData source,
            JigsawStudioLayout initialLayout
    ) {
        return activate(
                packKey,
                structureKey,
                mode,
                compatibilityTarget,
                cellDimensions,
                source,
                initialLayout,
                null
        );
    }

    public synchronized JigsawStudioRequest activate(
            String packKey,
            String structureKey,
            JigsawStudioMode mode,
            JigsawStudioCompatibilityTarget compatibilityTarget,
            JigsawStudioCellDimensions cellDimensions,
            IrisData source,
            JigsawStudioLayout initialLayout,
            UUID ownerId
    ) {
        if (staged.get() != null) {
            throw new IllegalStateException("A Jigsaw Studio replacement is already staged");
        }
        if (ownerId != null && !ownerId.equals(openingOwner.get())) {
            throw new IllegalStateException("Jigsaw Studio activation does not own the active opening lease");
        }
        UUID activeOwnerId = activeOwnerId();
        if (ownerId != null && activeOwnerId != null && !ownerId.equals(activeOwnerId)) {
            throw new IllegalStateException("Jigsaw Studio is owned by another player session");
        }
        ActiveRequest activeRequest = createActiveRequest(
                packKey,
                structureKey,
                mode,
                compatibilityTarget,
                cellDimensions,
                source,
                initialLayout,
                ownerId);
        active.put(normalize(packKey), activeRequest);
        return activeRequest.request();
    }

    public synchronized StagedActivation stage(
            String packKey,
            String structureKey,
            JigsawStudioMode mode,
            JigsawStudioCompatibilityTarget compatibilityTarget,
            JigsawStudioCellDimensions cellDimensions,
            IrisData source,
            JigsawStudioLayout initialLayout,
            UUID ownerId,
            UUID preservedRequestId
    ) {
        UUID opening = openingOwner.get();
        UUID activeOwner = activeOwnerId();
        UUID requestedOwner = Objects.requireNonNull(ownerId, "Jigsaw Studio staged owner ID");
        if (!requestedOwner.equals(opening)) {
            throw new IllegalStateException("Jigsaw Studio staging does not own the active opening lease");
        }
        if (activeOwner != null && !requestedOwner.equals(activeOwner)) {
            throw new IllegalStateException("Jigsaw Studio is owned by another player session");
        }
        if (staged.get() != null) {
            throw new IllegalStateException("A Jigsaw Studio replacement is already staged");
        }
        ActiveRequest preserved = preservedRequestId == null ? null : active(preservedRequestId);
        if (preservedRequestId != null && preserved == null) {
            throw new IllegalStateException("The Jigsaw Studio being replaced is no longer active");
        }
        if (activeOwner != null && preserved == null) {
            throw new IllegalStateException("The active Jigsaw Studio must be preserved during replacement");
        }
        if (preserved != null && preserved.request().ownerId() != null
                && !requestedOwner.equals(preserved.request().ownerId())) {
            throw new IllegalStateException("The Jigsaw Studio being replaced belongs to another player session");
        }
        ActiveRequest candidate = createActiveRequest(
                packKey,
                structureKey,
                mode,
                compatibilityTarget,
                cellDimensions,
                source,
                initialLayout,
                requestedOwner);
        StagedActivation activation = new StagedActivation(
                UUID.randomUUID(), candidate.request(), candidate.session());
        staged.set(new StagedState(activation, candidate, preserved, false));
        return activation;
    }

    public synchronized boolean beginStagedGeneration(StagedActivation expected) {
        StagedState state = stagedState(expected);
        if (state == null || state.generationVisible()) {
            return false;
        }
        staged.set(new StagedState(state.staged(), state.candidate(), state.preserved(), true));
        return true;
    }

    public synchronized boolean commit(StagedActivation expected) {
        StagedState state = stagedState(expected);
        if (state == null || !state.generationVisible()) {
            return false;
        }
        if (state.preserved() != null) {
            active.remove(normalize(state.preserved().request().packKey()), state.preserved());
        }
        active.put(normalize(state.candidate().request().packKey()), state.candidate());
        staged.set(null);
        return true;
    }

    public synchronized boolean rollback(StagedActivation expected) {
        StagedState state = stagedState(expected);
        if (state == null) {
            return false;
        }
        staged.set(null);
        return true;
    }

    public boolean tryBeginOpen(UUID ownerId) {
        UUID requestedOwner = Objects.requireNonNull(ownerId, "Jigsaw Studio opening owner ID");
        UUID activeOwnerId = activeOwnerId();
        if (activeOwnerId != null && !requestedOwner.equals(activeOwnerId)) {
            return false;
        }
        return openingOwner.compareAndSet(null, requestedOwner);
    }

    public void finishOpen(UUID ownerId) {
        if (ownerId != null) {
            openingOwner.compareAndSet(ownerId, null);
        }
    }

    public UUID openingOwnerId() {
        return openingOwner.get();
    }

    public UUID activeOwnerId() {
        for (ActiveRequest activeRequest : active.values()) {
            UUID ownerId = activeRequest.request().ownerId();
            if (ownerId != null) {
                return ownerId;
            }
        }
        return null;
    }

    public void deactivate(String packKey) {
        if (packKey != null) {
            active.remove(normalize(packKey));
            StagedState state = staged.get();
            if (state != null && (normalize(state.candidate().request().packKey()).equals(normalize(packKey))
                    || state.preserved() != null && normalize(state.preserved().request().packKey())
                    .equals(normalize(packKey)))) {
                staged.compareAndSet(state, null);
            }
        }
    }

    public boolean deactivate(String packKey, UUID requestId) {
        if (packKey == null || requestId == null) {
            return false;
        }
        StagedState stagedState = staged.get();
        if (stagedState != null && stagedState.preserved() != null
                && requestId.equals(stagedState.preserved().request().requestId())
                && normalize(packKey).equals(normalize(stagedState.preserved().request().packKey()))) {
            return false;
        }
        AtomicBoolean removed = new AtomicBoolean(false);
        active.computeIfPresent(normalize(packKey), (key, activeRequest) -> {
            if (!requestId.equals(activeRequest.request().requestId())) {
                return activeRequest;
            }
            removed.set(true);
            return null;
        });
        return removed.get();
    }

    public boolean isActive(String packKey) {
        return getRequest(packKey) != null;
    }

    public JigsawStudioRequest getRequest(String packKey) {
        ActiveRequest activeRequest = active(packKey);
        return activeRequest == null ? null : activeRequest.request();
    }

    public JigsawStudioSession getSession(String packKey) {
        ActiveRequest activeRequest = active(packKey);
        return activeRequest == null ? null : activeRequest.session();
    }

    public JigsawStudioLayout getLayout(String packKey) {
        JigsawStudioSession session = getSession(packKey);
        return session == null ? null : session.layout();
    }

    public JigsawStudioRequest getRequest(UUID requestId) {
        ActiveRequest activeRequest = active(requestId);
        return activeRequest == null ? null : activeRequest.request();
    }

    public JigsawStudioSession getSession(UUID requestId) {
        ActiveRequest activeRequest = active(requestId);
        return activeRequest == null ? null : activeRequest.session();
    }

    private ActiveRequest active(String packKey) {
        if (packKey == null) {
            return null;
        }
        return active.get(normalize(packKey));
    }

    private ActiveRequest active(UUID requestId) {
        if (requestId == null) {
            return null;
        }
        for (ActiveRequest activeRequest : active.values()) {
            if (requestId.equals(activeRequest.request().requestId())) {
                return activeRequest;
            }
        }
        return null;
    }

    private static ActiveRequest createActiveRequest(
            String packKey,
            String structureKey,
            JigsawStudioMode mode,
            JigsawStudioCompatibilityTarget compatibilityTarget,
            JigsawStudioCellDimensions cellDimensions,
            IrisData source,
            JigsawStudioLayout initialLayout,
            UUID ownerId
    ) {
        JigsawStudioRequest request = new JigsawStudioRequest(
                UUID.randomUUID(),
                packKey,
                structureKey,
                mode,
                compatibilityTarget,
                cellDimensions,
                source,
                ownerId);
        JigsawStudioLayout layout = Objects.requireNonNull(initialLayout, "Initial Jigsaw Studio layout");
        if (layout.mode() != mode) {
            throw new IllegalArgumentException("Initial Jigsaw Studio layout mode does not match the request");
        }
        if (!layout.cellDimensions().equals(cellDimensions)) {
            throw new IllegalArgumentException("Initial Jigsaw Studio layout cell dimensions do not match the request");
        }
        JigsawStudioSession session = new JigsawStudioSession(
                request.requestId(),
                request.packKey(),
                request.structureKey(),
                layout);
        return new ActiveRequest(request, session);
    }

    private StagedState stagedState(StagedActivation expected) {
        StagedActivation activation = Objects.requireNonNull(expected, "Staged Jigsaw Studio activation");
        StagedState state = staged.get();
        return state != null && state.staged().stageId().equals(activation.stageId()) ? state : null;
    }

    private static String normalize(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }

    private record ActiveRequest(JigsawStudioRequest request, JigsawStudioSession session) {
    }

    private record StagedState(
            StagedActivation staged,
            ActiveRequest candidate,
            ActiveRequest preserved,
            boolean generationVisible
    ) {
    }

    public record StagedActivation(
            UUID stageId,
            JigsawStudioRequest request,
            JigsawStudioSession session
    ) {
        public StagedActivation {
            Objects.requireNonNull(stageId, "Jigsaw Studio stage ID");
            Objects.requireNonNull(request, "Jigsaw Studio staged request");
            Objects.requireNonNull(session, "Jigsaw Studio staged session");
            if (!request.requestId().equals(session.sessionId())) {
                throw new IllegalArgumentException("Staged Jigsaw Studio request and session IDs do not match");
            }
        }
    }
}
