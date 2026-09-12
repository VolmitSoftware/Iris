package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.loading.IrisData;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class JigsawStudioActivation {
    private static final Map<String, ActiveRequest> ACTIVE = new ConcurrentHashMap<>();
    private static final AtomicReference<UUID> OPENING_OWNER = new AtomicReference<>();
    private static final AtomicReference<StagedState> STAGED = new AtomicReference<>();

    private JigsawStudioActivation() {
    }

    public static Request activate(
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

    public static Request activate(
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

    public static synchronized Request activate(
            String packKey,
            String structureKey,
            JigsawStudioMode mode,
            JigsawStudioCompatibilityTarget compatibilityTarget,
            JigsawStudioCellDimensions cellDimensions,
            IrisData source,
            JigsawStudioLayout initialLayout,
            UUID ownerId
    ) {
        if (STAGED.get() != null) {
            throw new IllegalStateException("A Jigsaw Studio replacement is already staged");
        }
        if (ownerId != null && !ownerId.equals(OPENING_OWNER.get())) {
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
        ACTIVE.put(normalize(packKey), activeRequest);
        return activeRequest.request();
    }

    public static synchronized StagedActivation stage(
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
        UUID openingOwner = OPENING_OWNER.get();
        UUID activeOwner = activeOwnerId();
        UUID requestedOwner = Objects.requireNonNull(ownerId, "Jigsaw Studio staged owner ID");
        if (!requestedOwner.equals(openingOwner)) {
            throw new IllegalStateException("Jigsaw Studio staging does not own the active opening lease");
        }
        if (activeOwner != null && !requestedOwner.equals(activeOwner)) {
            throw new IllegalStateException("Jigsaw Studio is owned by another player session");
        }
        if (STAGED.get() != null) {
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
        StagedActivation staged = new StagedActivation(
                UUID.randomUUID(), candidate.request(), candidate.session());
        STAGED.set(new StagedState(staged, candidate, preserved, false));
        return staged;
    }

    public static synchronized boolean beginStagedGeneration(StagedActivation expected) {
        StagedState state = stagedState(expected);
        if (state == null || state.generationVisible()) {
            return false;
        }
        STAGED.set(new StagedState(state.staged(), state.candidate(), state.preserved(), true));
        return true;
    }

    public static synchronized boolean commit(StagedActivation expected) {
        StagedState state = stagedState(expected);
        if (state == null || !state.generationVisible()) {
            return false;
        }
        if (state.preserved() != null) {
            ACTIVE.remove(normalize(state.preserved().request().packKey()), state.preserved());
        }
        ACTIVE.put(normalize(state.candidate().request().packKey()), state.candidate());
        STAGED.set(null);
        return true;
    }

    public static synchronized boolean rollback(StagedActivation expected) {
        StagedState state = stagedState(expected);
        if (state == null) {
            return false;
        }
        STAGED.set(null);
        return true;
    }

    public static Request getGeneratorRequest(String packKey) {
        StagedState state = STAGED.get();
        if (state != null && state.generationVisible()
                && normalize(state.candidate().request().packKey()).equals(normalize(packKey))) {
            return state.candidate().request();
        }
        return getRequest(packKey);
    }

    public static JigsawStudioSession getGeneratorSession(String packKey) {
        StagedState state = STAGED.get();
        if (state != null && state.generationVisible()
                && normalize(state.candidate().request().packKey()).equals(normalize(packKey))) {
            return state.candidate().session();
        }
        return getSession(packKey);
    }

    public static boolean tryBeginOpen(UUID ownerId) {
        UUID requestedOwner = Objects.requireNonNull(ownerId, "Jigsaw Studio opening owner ID");
        UUID activeOwnerId = activeOwnerId();
        if (activeOwnerId != null && !requestedOwner.equals(activeOwnerId)) {
            return false;
        }
        return OPENING_OWNER.compareAndSet(null, requestedOwner);
    }

    public static void finishOpen(UUID ownerId) {
        if (ownerId != null) {
            OPENING_OWNER.compareAndSet(ownerId, null);
        }
    }

    public static UUID openingOwnerId() {
        return OPENING_OWNER.get();
    }

    public static UUID activeOwnerId() {
        for (ActiveRequest activeRequest : ACTIVE.values()) {
            UUID ownerId = activeRequest.request().ownerId();
            if (ownerId != null) {
                return ownerId;
            }
        }
        return null;
    }

    public static void deactivate(String packKey) {
        if (packKey != null) {
            ACTIVE.remove(normalize(packKey));
            StagedState state = STAGED.get();
            if (state != null && (normalize(state.candidate().request().packKey()).equals(normalize(packKey))
                    || state.preserved() != null && normalize(state.preserved().request().packKey())
                    .equals(normalize(packKey)))) {
                STAGED.compareAndSet(state, null);
            }
        }
    }

    public static boolean deactivate(String packKey, UUID requestId) {
        if (packKey == null || requestId == null) {
            return false;
        }
        StagedState staged = STAGED.get();
        if (staged != null && staged.preserved() != null
                && requestId.equals(staged.preserved().request().requestId())
                && normalize(packKey).equals(normalize(staged.preserved().request().packKey()))) {
            return false;
        }
        AtomicBoolean removed = new AtomicBoolean(false);
        ACTIVE.computeIfPresent(normalize(packKey), (key, activeRequest) -> {
            if (!requestId.equals(activeRequest.request().requestId())) {
                return activeRequest;
            }
            removed.set(true);
            return null;
        });
        return removed.get();
    }

    public static boolean isActive(String packKey) {
        return getRequest(packKey) != null;
    }

    public static Request getRequest(String packKey) {
        ActiveRequest activeRequest = active(packKey);
        return activeRequest == null ? null : activeRequest.request();
    }

    public static JigsawStudioSession getSession(String packKey) {
        ActiveRequest activeRequest = active(packKey);
        return activeRequest == null ? null : activeRequest.session();
    }

    public static JigsawStudioLayout getLayout(String packKey) {
        JigsawStudioSession session = getSession(packKey);
        return session == null ? null : session.layout();
    }

    public static Request getRequest(UUID requestId) {
        ActiveRequest activeRequest = active(requestId);
        return activeRequest == null ? null : activeRequest.request();
    }

    public static JigsawStudioSession getSession(UUID requestId) {
        ActiveRequest activeRequest = active(requestId);
        return activeRequest == null ? null : activeRequest.session();
    }

    private static ActiveRequest active(String packKey) {
        if (packKey == null) {
            return null;
        }
        return ACTIVE.get(normalize(packKey));
    }

    private static ActiveRequest active(UUID requestId) {
        if (requestId == null) {
            return null;
        }
        for (ActiveRequest activeRequest : ACTIVE.values()) {
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
        Request request = new Request(
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

    private static StagedState stagedState(StagedActivation expected) {
        StagedActivation staged = Objects.requireNonNull(expected, "Staged Jigsaw Studio activation");
        StagedState state = STAGED.get();
        return state != null && state.staged().stageId().equals(staged.stageId()) ? state : null;
    }

    private static String normalize(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }

    private record ActiveRequest(Request request, JigsawStudioSession session) {
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
            Request request,
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

    public record Request(
            UUID requestId,
            String packKey,
            String structureKey,
            JigsawStudioMode mode,
            JigsawStudioCompatibilityTarget compatibilityTarget,
            JigsawStudioCellDimensions cellDimensions,
            IrisData source,
            UUID ownerId
    ) {
        public Request {
            requestId = Objects.requireNonNull(requestId, "Jigsaw Studio request ID");
            packKey = requireKey(packKey, "pack");
            structureKey = requireKey(structureKey, "structure");
            mode = Objects.requireNonNull(mode, "Jigsaw Studio mode");
            compatibilityTarget = Objects.requireNonNull(
                    compatibilityTarget,
                    "Jigsaw Studio compatibility target"
            );
            cellDimensions = Objects.requireNonNull(cellDimensions, "Jigsaw Studio cell dimensions");
            source = Objects.requireNonNull(source, "Jigsaw Studio source data");
        }

        private static String requireKey(String value, String name) {
            Objects.requireNonNull(value, "Jigsaw Studio " + name + " key");
            String normalized = value.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Jigsaw Studio " + name + " key cannot be blank");
            }
            return normalized;
        }
    }
}
