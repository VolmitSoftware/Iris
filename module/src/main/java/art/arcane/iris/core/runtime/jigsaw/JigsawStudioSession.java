package art.arcane.iris.core.runtime.jigsaw;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JigsawStudioSession {
    private final UUID sessionId;
    private final String packKey;
    private final String structureKey;
    private final Map<String, MutableWorkcellState> workcells = new LinkedHashMap<>();
    private JigsawStudioLayout layout;
    private String selectedBayId;
    private long nextLoadGeneration;
    private long nextMutationGeneration;
    private long nextOperationGeneration;
    private long revision;

    public JigsawStudioSession(String packKey, String structureKey, JigsawStudioLayout layout) {
        this(UUID.randomUUID(), packKey, structureKey, layout);
    }

    public JigsawStudioSession(
            UUID sessionId,
            String packKey,
            String structureKey,
            JigsawStudioLayout layout
    ) {
        this.sessionId = Objects.requireNonNull(sessionId, "Jigsaw Studio session ID");
        this.packKey = requireKey(packKey, "pack");
        this.structureKey = requireKey(structureKey, "structure");
        this.layout = Objects.requireNonNull(layout, "Jigsaw Studio session layout");
        initializeWorkcells(layout);
    }

    public synchronized UUID sessionId() {
        return sessionId;
    }

    public synchronized String packKey() {
        return packKey;
    }

    public synchronized String structureKey() {
        return structureKey;
    }

    public synchronized JigsawStudioLayout layout() {
        return layout;
    }

    public synchronized Optional<String> selectedBayId() {
        return Optional.ofNullable(selectedBayId);
    }

    public synchronized boolean selectBay(String stableId) {
        Objects.requireNonNull(stableId, "Jigsaw Studio selected workcell ID");
        if (layout.get(stableId) == null) {
            return false;
        }
        selectedBayId = stableId;
        return true;
    }

    public synchronized void clearSelection() {
        selectedBayId = null;
    }

    public synchronized Optional<JigsawStudioVariant> activeVariant(String workcellId) {
        MutableWorkcellState state = workcells.get(workcellId);
        if (state == null || state.activeVariantKey.isEmpty()) {
            return Optional.empty();
        }
        return layout.variantCatalog().find(state.activeVariantKey);
    }

    public synchronized WorkcellSnapshot workcellSnapshot(String workcellId) {
        MutableWorkcellState state = requireWorkcellState(workcellId);
        return state.snapshot(workcellId);
    }

    public synchronized boolean setConnectorsVisible(String workcellId, boolean visible) {
        MutableWorkcellState state = requireWorkcellState(workcellId);
        if (state.connectorsVisible == visible) {
            return false;
        }
        state.connectorsVisible = visible;
        revision++;
        return true;
    }

    public synchronized boolean replaceLayout(JigsawStudioLayout replacement) {
        JigsawStudioLayout nextLayout = Objects.requireNonNull(replacement, "Replacement Jigsaw Studio layout");
        if (layout.mode() != nextLayout.mode()) {
            throw new IllegalArgumentException("Replacement Jigsaw Studio layout mode does not match the session");
        }
        if (operationInProgress()) {
            throw new IllegalStateException("Jigsaw Studio layout cannot change during a save or variant switch");
        }
        if (layout == nextLayout) {
            return false;
        }

        Map<String, MutableWorkcellState> updated = new LinkedHashMap<>();
        for (JigsawStudioBay workcell : nextLayout.bays()) {
            MutableWorkcellState previous = workcells.get(workcell.stableId());
            String activeVariantKey = retainedVariantKey(nextLayout, workcell, previous);
            if (previous != null && previous.activeVariantKey.equals(activeVariantKey)) {
                updated.put(workcell.stableId(), previous.copy());
                continue;
            }
            updated.put(workcell.stableId(), new MutableWorkcellState(
                    activeVariantKey,
                    nextLoadGeneration(),
                    nextMutationGeneration(),
                    false,
                    previous != null && previous.connectorsVisible));
        }
        layout = nextLayout;
        workcells.clear();
        workcells.putAll(updated);
        if (selectedBayId != null && layout.get(selectedBayId) == null) {
            selectedBayId = null;
        }
        revision++;
        return true;
    }

    public synchronized boolean replaceLayoutAndRebind(
            JigsawStudioLayout replacement,
            Map<String, String> activeVariantsByWorkcell
    ) {
        JigsawStudioLayout nextLayout = Objects.requireNonNull(replacement, "Replacement Jigsaw Studio layout");
        Map<String, String> bindings = Map.copyOf(Objects.requireNonNull(
                activeVariantsByWorkcell,
                "Replacement Jigsaw Studio active variants"));
        if (layout.mode() != nextLayout.mode()) {
            throw new IllegalArgumentException("Replacement Jigsaw Studio layout mode does not match the session");
        }
        if (operationInProgress()) {
            throw new IllegalStateException("Jigsaw Studio layout cannot change during a save or variant switch");
        }
        for (Map.Entry<String, String> binding : bindings.entrySet()) {
            JigsawStudioBay workcell = nextLayout.get(binding.getKey());
            JigsawStudioVariant variant = nextLayout.variantCatalog().find(binding.getValue())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown replacement Jigsaw Studio variant " + binding.getValue()));
            if (workcell == null || !nextLayout.accepts(workcell, variant)) {
                throw new IllegalArgumentException("Replacement Jigsaw Studio variant " + binding.getValue()
                        + " does not belong to workcell " + binding.getKey());
            }
        }

        Map<String, MutableWorkcellState> updated = new LinkedHashMap<>();
        boolean changed = layout != nextLayout;
        for (JigsawStudioBay workcell : nextLayout.bays()) {
            MutableWorkcellState previous = workcells.get(workcell.stableId());
            String targetVariantKey = bindings.get(workcell.stableId());
            if (targetVariantKey == null) {
                targetVariantKey = retainedVariantKey(nextLayout, workcell, previous);
            }
            if (previous != null && previous.activeVariantKey.equals(targetVariantKey)) {
                updated.put(workcell.stableId(), previous.copy());
                continue;
            }
            changed = true;
            updated.put(workcell.stableId(), new MutableWorkcellState(
                    targetVariantKey,
                    nextLoadGeneration(),
                    nextMutationGeneration(),
                    false,
                    previous != null && previous.connectorsVisible));
        }
        layout = nextLayout;
        workcells.clear();
        workcells.putAll(updated);
        if (selectedBayId != null && layout.get(selectedBayId) == null) {
            selectedBayId = null;
        }
        if (changed) {
            revision++;
        }
        return changed;
    }

    public synchronized SwitchStart beginVariantSwitch(
            String workcellId,
            String targetPieceKey,
            boolean discardDirty
    ) {
        return beginVariantTransition(workcellId, targetPieceKey, discardDirty, false);
    }

    public synchronized SwitchStart beginVariantReload(String workcellId) {
        MutableWorkcellState state = workcells.get(workcellId);
        if (state == null) {
            return SwitchStart.failure(SwitchStatus.UNKNOWN_WORKCELL);
        }
        if (state.activeVariantKey.isEmpty()) {
            return SwitchStart.failure(SwitchStatus.UNKNOWN_VARIANT);
        }
        return beginVariantTransition(workcellId, state.activeVariantKey, false, true);
    }

    private SwitchStart beginVariantTransition(
            String workcellId,
            String targetPieceKey,
            boolean discardDirty,
            boolean allowActive
    ) {
        MutableWorkcellState state = workcells.get(workcellId);
        if (state == null) {
            return SwitchStart.failure(SwitchStatus.UNKNOWN_WORKCELL);
        }
        Optional<JigsawStudioVariant> target = layout.variantCatalog().find(targetPieceKey);
        if (target.isEmpty()) {
            return SwitchStart.failure(SwitchStatus.UNKNOWN_VARIANT);
        }
        JigsawStudioBay workcell = layout.get(workcellId);
        if (!layout.accepts(workcell, target.get())) {
            return SwitchStart.failure(SwitchStatus.WRONG_WORKCELL);
        }
        if (state.switchInProgress) {
            return SwitchStart.failure(SwitchStatus.SWITCH_IN_PROGRESS);
        }
        if (state.saveInProgress) {
            return SwitchStart.failure(SwitchStatus.SAVE_IN_PROGRESS);
        }
        if (state.activeVariantKey.equals(targetPieceKey) && !allowActive) {
            return SwitchStart.failure(SwitchStatus.ALREADY_ACTIVE);
        }
        if (state.dirty && !discardDirty) {
            return SwitchStart.failure(SwitchStatus.DIRTY);
        }

        JigsawStudioVariant previous = state.activeVariantKey.isEmpty()
                ? null
                : layout.variantCatalog().find(state.activeVariantKey).orElse(null);
        long switchGeneration = nextOperationGeneration();
        state.switchInProgress = true;
        state.switchGeneration = switchGeneration;
        VariantSwitchToken token = new VariantSwitchToken(
                sessionId,
                workcellId,
                Optional.ofNullable(previous),
                target.get(),
                state.loadGeneration,
                state.mutationGeneration,
                switchGeneration,
                discardDirty);
        revision++;
        return SwitchStart.started(token);
    }

    public synchronized boolean completeVariantSwitch(VariantSwitchToken expected) {
        VariantSwitchToken token = Objects.requireNonNull(expected, "Jigsaw Studio variant switch token");
        MutableWorkcellState state = workcells.get(token.workcellId());
        if (!validSwitchToken(state, token)) {
            return false;
        }
        state.activeVariantKey = token.targetVariant().pieceKey();
        state.loadGeneration = nextLoadGeneration();
        state.mutationGeneration = nextMutationGeneration();
        state.dirty = false;
        state.switchInProgress = false;
        state.switchGeneration = 0L;
        revision++;
        return true;
    }

    public synchronized boolean isVariantSwitchCurrent(VariantSwitchToken expected) {
        VariantSwitchToken token = Objects.requireNonNull(expected, "Jigsaw Studio variant switch token");
        return validSwitchToken(workcells.get(token.workcellId()), token);
    }

    public synchronized boolean abortVariantSwitch(VariantSwitchToken expected) {
        VariantSwitchToken token = Objects.requireNonNull(expected, "Jigsaw Studio variant switch token");
        MutableWorkcellState state = workcells.get(token.workcellId());
        if (!validSwitchToken(state, token)) {
            return false;
        }
        state.switchInProgress = false;
        state.switchGeneration = 0L;
        revision++;
        return true;
    }

    public synchronized DirtyMark markWorkcellDirty(String workcellId) {
        MutableWorkcellState state = workcells.get(workcellId);
        if (state == null) {
            return DirtyMark.failure(DirtyStatus.UNKNOWN_WORKCELL);
        }
        if (state.activeVariantKey.isEmpty()) {
            return DirtyMark.failure(DirtyStatus.NO_ACTIVE_VARIANT);
        }
        if (state.switchInProgress) {
            return DirtyMark.failure(DirtyStatus.SWITCH_IN_PROGRESS);
        }
        state.mutationGeneration = nextMutationGeneration();
        boolean newlyDirty = !state.dirty;
        state.dirty = true;
        revision++;
        return DirtyMark.marked(new DirtyIdentity(
                sessionId,
                workcellId,
                state.activeVariantKey,
                state.loadGeneration,
                state.mutationGeneration), newlyDirty);
    }

    public synchronized boolean isDirtyCurrent(DirtyIdentity expected) {
        DirtyIdentity identity = Objects.requireNonNull(expected, "Jigsaw Studio dirty identity");
        MutableWorkcellState state = workcells.get(identity.workcellId());
        return sessionId.equals(identity.sessionId())
                && state != null
                && state.dirty
                && state.activeVariantKey.equals(identity.variantKey())
                && state.loadGeneration == identity.loadGeneration()
                && state.mutationGeneration == identity.mutationGeneration();
    }

    public synchronized SaveStart beginSave(String workcellId) {
        MutableWorkcellState state = workcells.get(workcellId);
        if (state == null) {
            return SaveStart.failure(SaveStatus.UNKNOWN_WORKCELL);
        }
        if (state.activeVariantKey.isEmpty()) {
            return SaveStart.failure(SaveStatus.NO_ACTIVE_VARIANT);
        }
        if (state.switchInProgress) {
            return SaveStart.failure(SaveStatus.SWITCH_IN_PROGRESS);
        }
        if (state.saveInProgress) {
            return SaveStart.failure(SaveStatus.SAVE_IN_PROGRESS);
        }
        long saveGeneration = nextOperationGeneration();
        state.saveInProgress = true;
        state.saveGeneration = saveGeneration;
        SaveIdentity identity = new SaveIdentity(
                sessionId,
                workcellId,
                state.activeVariantKey,
                state.loadGeneration,
                state.mutationGeneration,
                saveGeneration);
        revision++;
        return SaveStart.started(identity);
    }

    public synchronized boolean markWorkcellSaved(SaveIdentity expected) {
        SaveIdentity identity = Objects.requireNonNull(expected, "Jigsaw Studio save identity");
        MutableWorkcellState state = workcells.get(identity.workcellId());
        if (!validSaveReservation(state, identity)) {
            return false;
        }
        boolean unchanged = state.activeVariantKey.equals(identity.variantKey())
                && state.loadGeneration == identity.loadGeneration()
                && state.mutationGeneration == identity.mutationGeneration();
        state.saveInProgress = false;
        state.saveGeneration = 0L;
        if (unchanged) {
            state.dirty = false;
        }
        revision++;
        return unchanged;
    }

    public synchronized boolean isSaveCurrent(SaveIdentity expected) {
        SaveIdentity identity = Objects.requireNonNull(expected, "Jigsaw Studio save identity");
        MutableWorkcellState state = workcells.get(identity.workcellId());
        return validSaveReservation(state, identity)
                && state.activeVariantKey.equals(identity.variantKey())
                && state.loadGeneration == identity.loadGeneration()
                && state.mutationGeneration == identity.mutationGeneration();
    }

    public synchronized boolean abortSave(SaveIdentity expected) {
        SaveIdentity identity = Objects.requireNonNull(expected, "Jigsaw Studio save identity");
        MutableWorkcellState state = workcells.get(identity.workcellId());
        if (!validSaveReservation(state, identity)) {
            return false;
        }
        state.saveInProgress = false;
        state.saveGeneration = 0L;
        revision++;
        return true;
    }

    public synchronized boolean isDirty() {
        for (MutableWorkcellState state : workcells.values()) {
            if (state.dirty) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<String> dirtyWorkcellIds() {
        List<String> dirty = new ArrayList<>();
        for (Map.Entry<String, MutableWorkcellState> entry : workcells.entrySet()) {
            if (entry.getValue().dirty) {
                dirty.add(entry.getKey());
            }
        }
        return List.copyOf(dirty);
    }

    public synchronized boolean operationInProgress() {
        for (MutableWorkcellState state : workcells.values()) {
            if (state.saveInProgress || state.switchInProgress) {
                return true;
            }
        }
        return false;
    }

    public synchronized long revision() {
        return revision;
    }

    private void initializeWorkcells(JigsawStudioLayout initialLayout) {
        for (JigsawStudioBay workcell : initialLayout.bays()) {
            String variantKey = initialLayout.defaultVariant(workcell)
                    .map(JigsawStudioVariant::pieceKey)
                    .orElse("");
            workcells.put(workcell.stableId(), new MutableWorkcellState(
                    variantKey,
                    nextLoadGeneration(),
                    nextMutationGeneration(),
                    false,
                    false));
        }
    }

    private String retainedVariantKey(
            JigsawStudioLayout nextLayout,
            JigsawStudioBay workcell,
            MutableWorkcellState previous
    ) {
        if (previous != null && previous.activeVariantKey.isEmpty()) {
            return "";
        }
        if (previous != null) {
            Optional<JigsawStudioVariant> retained = nextLayout.variantCatalog().find(previous.activeVariantKey);
            if (retained.isPresent() && nextLayout.accepts(workcell, retained.get())) {
                return previous.activeVariantKey;
            }
        }
        return nextLayout.defaultVariant(workcell).map(JigsawStudioVariant::pieceKey).orElse("");
    }

    private boolean validSwitchToken(MutableWorkcellState state, VariantSwitchToken token) {
        if (!sessionId.equals(token.sessionId()) || state == null || !state.switchInProgress) {
            return false;
        }
        String previousKey = token.previousVariant().map(JigsawStudioVariant::pieceKey).orElse("");
        return state.switchGeneration == token.switchGeneration()
                && state.activeVariantKey.equals(previousKey)
                && state.loadGeneration == token.loadGeneration()
                && state.mutationGeneration == token.mutationGeneration();
    }

    private boolean validSaveReservation(MutableWorkcellState state, SaveIdentity identity) {
        return sessionId.equals(identity.sessionId())
                && state != null
                && state.saveInProgress
                && state.saveGeneration == identity.saveGeneration();
    }

    private MutableWorkcellState requireWorkcellState(String workcellId) {
        MutableWorkcellState state = workcells.get(Objects.requireNonNull(workcellId, "Jigsaw Studio workcell ID"));
        if (state == null) {
            throw new IllegalArgumentException("Unknown Jigsaw Studio workcell " + workcellId);
        }
        return state;
    }

    private long nextLoadGeneration() {
        return nextLoadGeneration = Math.incrementExact(nextLoadGeneration);
    }

    private long nextMutationGeneration() {
        return nextMutationGeneration = Math.incrementExact(nextMutationGeneration);
    }

    private long nextOperationGeneration() {
        return nextOperationGeneration = Math.incrementExact(nextOperationGeneration);
    }

    private static String requireKey(String value, String name) {
        Objects.requireNonNull(value, "Jigsaw Studio " + name + " key");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Jigsaw Studio " + name + " key cannot be blank");
        }
        return normalized;
    }

    public enum SwitchStatus {
        STARTED,
        UNKNOWN_WORKCELL,
        UNKNOWN_VARIANT,
        WRONG_WORKCELL,
        ALREADY_ACTIVE,
        DIRTY,
        SAVE_IN_PROGRESS,
        SWITCH_IN_PROGRESS
    }

    public enum SaveStatus {
        STARTED,
        UNKNOWN_WORKCELL,
        NO_ACTIVE_VARIANT,
        SWITCH_IN_PROGRESS,
        SAVE_IN_PROGRESS
    }

    public enum DirtyStatus {
        MARKED,
        UNKNOWN_WORKCELL,
        NO_ACTIVE_VARIANT,
        SWITCH_IN_PROGRESS
    }

    public record WorkcellSnapshot(
            String workcellId,
            String activeVariantKey,
            long loadGeneration,
            long mutationGeneration,
            boolean dirty,
            boolean saveInProgress,
            boolean switchInProgress,
            boolean connectorsVisible
    ) {
    }

    public record DirtyIdentity(
            UUID sessionId,
            String workcellId,
            String variantKey,
            long loadGeneration,
            long mutationGeneration
    ) {
        public DirtyIdentity {
            Objects.requireNonNull(sessionId, "Jigsaw Studio dirty session ID");
            Objects.requireNonNull(workcellId, "Jigsaw Studio dirty workcell ID");
            Objects.requireNonNull(variantKey, "Jigsaw Studio dirty variant key");
        }
    }

    public record DirtyMark(
            DirtyStatus status,
            Optional<DirtyIdentity> identity,
            boolean newlyDirty
    ) {
        public DirtyMark {
            status = Objects.requireNonNull(status, "Jigsaw Studio dirty status");
            identity = Objects.requireNonNull(identity, "Jigsaw Studio dirty identity");
            if (status == DirtyStatus.MARKED && identity.isEmpty()) {
                throw new IllegalArgumentException("A marked Jigsaw Studio workcell requires a dirty identity");
            }
            if (status != DirtyStatus.MARKED && (identity.isPresent() || newlyDirty)) {
                throw new IllegalArgumentException("A rejected Jigsaw Studio dirty mark cannot carry an identity");
            }
        }

        private static DirtyMark marked(DirtyIdentity identity, boolean newlyDirty) {
            return new DirtyMark(DirtyStatus.MARKED, Optional.of(identity), newlyDirty);
        }

        private static DirtyMark failure(DirtyStatus status) {
            return new DirtyMark(status, Optional.empty(), false);
        }
    }

    public record VariantSwitchToken(
            UUID sessionId,
            String workcellId,
            Optional<JigsawStudioVariant> previousVariant,
            JigsawStudioVariant targetVariant,
            long loadGeneration,
            long mutationGeneration,
            long switchGeneration,
            boolean discardDirty
    ) {
        public VariantSwitchToken {
            Objects.requireNonNull(sessionId, "Jigsaw Studio switch session ID");
            Objects.requireNonNull(workcellId, "Jigsaw Studio switch workcell ID");
            previousVariant = Objects.requireNonNull(previousVariant, "Jigsaw Studio previous variant");
            targetVariant = Objects.requireNonNull(targetVariant, "Jigsaw Studio target variant");
        }
    }

    public record SwitchStart(SwitchStatus status, Optional<VariantSwitchToken> token) {
        public SwitchStart {
            status = Objects.requireNonNull(status, "Jigsaw Studio switch status");
            token = Objects.requireNonNull(token, "Jigsaw Studio switch token");
        }

        private static SwitchStart started(VariantSwitchToken token) {
            return new SwitchStart(SwitchStatus.STARTED, Optional.of(token));
        }

        private static SwitchStart failure(SwitchStatus status) {
            return new SwitchStart(status, Optional.empty());
        }
    }

    public record SaveIdentity(
            UUID sessionId,
            String workcellId,
            String variantKey,
            long loadGeneration,
            long mutationGeneration,
            long saveGeneration
    ) {
        public SaveIdentity {
            Objects.requireNonNull(sessionId, "Jigsaw Studio save session ID");
            Objects.requireNonNull(workcellId, "Jigsaw Studio save workcell ID");
            Objects.requireNonNull(variantKey, "Jigsaw Studio save variant key");
        }
    }

    public record SaveStart(SaveStatus status, Optional<SaveIdentity> identity) {
        public SaveStart {
            status = Objects.requireNonNull(status, "Jigsaw Studio save status");
            identity = Objects.requireNonNull(identity, "Jigsaw Studio save identity");
        }

        private static SaveStart started(SaveIdentity identity) {
            return new SaveStart(SaveStatus.STARTED, Optional.of(identity));
        }

        private static SaveStart failure(SaveStatus status) {
            return new SaveStart(status, Optional.empty());
        }
    }

    private static final class MutableWorkcellState {
        private String activeVariantKey;
        private long loadGeneration;
        private long mutationGeneration;
        private boolean dirty;
        private boolean saveInProgress;
        private long saveGeneration;
        private boolean switchInProgress;
        private long switchGeneration;
        private boolean connectorsVisible;

        private MutableWorkcellState(
                String activeVariantKey,
                long loadGeneration,
                long mutationGeneration,
                boolean dirty,
                boolean connectorsVisible
        ) {
            this.activeVariantKey = activeVariantKey;
            this.loadGeneration = loadGeneration;
            this.mutationGeneration = mutationGeneration;
            this.dirty = dirty;
            this.connectorsVisible = connectorsVisible;
        }

        private MutableWorkcellState copy() {
            return new MutableWorkcellState(
                    activeVariantKey,
                    loadGeneration,
                    mutationGeneration,
                    dirty,
                    connectorsVisible);
        }

        private WorkcellSnapshot snapshot(String workcellId) {
            return new WorkcellSnapshot(
                    workcellId,
                    activeVariantKey,
                    loadGeneration,
                    mutationGeneration,
                    dirty,
                    saveInProgress,
                    switchInProgress,
                    connectorsVisible);
        }
    }
}
