package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.studio.jigsaw.JigsawStudioService.ActiveStudio;
import art.arcane.iris.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.iris.studio.jigsaw.JigsawStudioEvaluator.loadStudioStructure;
import static art.arcane.iris.studio.jigsaw.JigsawStudioProtection.authorizeOwner;
import static art.arcane.iris.studio.jigsaw.JigsawStudioProtection.canToggleVariantRotation;
import static art.arcane.iris.studio.jigsaw.JigsawStudioProtection.ownerMatches;
import static art.arcane.iris.studio.jigsaw.JigsawStudioService.AUTOSAVE_RETRY_TICKS;
import static art.arcane.iris.studio.jigsaw.JigsawStudioService.message;
import static art.arcane.iris.studio.jigsaw.JigsawStudioVariantProperties.canResizeVariantToCapacity;

final class JigsawStudioToolbelt {
    private static final long TOOL_CONFIRM_NANOS = 10_000_000_000L;

    private final JigsawStudioService service;
    final Map<UUID, DeferredDuplication> deferredDuplications = new ConcurrentHashMap<>();
    final Map<UUID, ToolConfirmation> toolConfirmations = new ConcurrentHashMap<>();
    final JigsawStudioToolCodec toolCodec = new JigsawStudioToolCodec();
    volatile JigsawStudioMenuController menuController;

    JigsawStudioToolbelt(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    Optional<JigsawStudioMenuState> menuState(Player player) {
        if (player == null || !J.isOwnedByCurrentRegion(player)) {
            return Optional.empty();
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null) {
            return Optional.empty();
        }
        if (!ownerMatches(player, studio)) {
            return Optional.empty();
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioLayout layout = session.layout();
        IrisStructure structure = loadStudioStructure(studio);
        List<JigsawStudioMenuState.ThemeSet> themeSets = new ArrayList<>();
        if (structure != null && structure.getThemeSets() != null) {
            for (IrisJigsawThemeSet themeSet : structure.getThemeSets()) {
                if (themeSet != null) {
                    themeSets.add(new JigsawStudioMenuState.ThemeSet(
                            themeSet.getKey(),
                            themeSet.getWeight()));
                }
            }
        }
        List<JigsawStudioMenuState.Workcell> workcells = new ArrayList<>(layout.bays().size());
        for (JigsawStudioBay workcell : layout.bays()) {
            JigsawStudioSession.WorkcellSnapshot snapshot = session.workcellSnapshot(workcell.stableId());
            List<JigsawStudioMenuState.Variant> variants = new ArrayList<>();
            for (JigsawStudioVariant variant : layout.variants(workcell)) {
                boolean active = variant.pieceKey().equals(snapshot.activeVariantKey());
                List<JigsawStudioMenuState.Membership> memberships = new ArrayList<>(
                        variant.memberships().size());
                for (JigsawStudioPoolMembership membership : variant.memberships()) {
                    memberships.add(new JigsawStudioMenuState.Membership(
                            membership.poolKey(),
                            membership.entryIndex(),
                            membership.weight(),
                            membership.chance()));
                }
                variants.add(new JigsawStudioMenuState.Variant(
                        variant.pieceKey(),
                        variant.resolvedDisplayName(),
                        variant.dimensions(),
                        active,
                        variant.owned(),
                        variant.rotatable(),
                        canToggleVariantRotation(request.compatibilityTarget(), variant),
                        canResizeVariantToCapacity(workcell, variant, active),
                        variant.themes(),
                        variant.rules(),
                        memberships));
            }
            workcells.add(new JigsawStudioMenuState.Workcell(
                    workcell.stableId(),
                    workcell.canonicalDisplayName(),
                    workcell.displayName(),
                    workcell.capacity(),
                    workcell.enabled(),
                    snapshot.activeVariantKey(),
                    snapshot.dirty(),
                    snapshot.saveInProgress(),
                    snapshot.switchInProgress(),
                    snapshot.connectorsVisible(),
                    variants));
        }

        return Optional.of(new JigsawStudioMenuState(
                studio.worldId(),
                request.requestId(),
                request.structureKey(),
                layout.mode(),
                request.compatibilityTarget(),
                structure != null && structure.isRequireCaps(),
                themeSets,
                session.selectedBayId().orElse(""),
                Optional.ofNullable(service.evaluator.evaluations.get(request.requestId()))
                        .map(JigsawStudioMenuState.Evaluation::from)
                        .orElseGet(JigsawStudioMenuState.Evaluation::pending),
                workcells));
    }

    boolean selectWorkcell(Player player, String workcellId) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> selectWorkcell(player, workcellId));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        if (!authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        if (!session.selectBay(workcellId)) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return false;
        }
        service.visualization.ensureVisualizationLoop(player);
        return true;
    }

    boolean openControlMenu(Player player) {
        if (player != null && J.isOwnedByCurrentRegion(player)) {
            service.tileWatcher.finalizeJigsawTileWatchesForPlayer(player.getUniqueId());
            ActiveStudio studio = service.studios.get(player.getWorld().getUID());
            if (studio != null
                    && service.saveLifecycle.reopenRequiredRequests.contains(
                            studio.generator().getRequest().requestId())) {
                message(player, "Close and reopen Jigsaw Studio to apply the resized workcell layout.");
                return false;
            }
        }
        JigsawStudioMenuController activeMenuController = menuController;
        return activeMenuController != null && activeMenuController.open(player);
    }

    void closeMenu(Player player) {
        JigsawStudioMenuController activeMenuController = menuController;
        if (activeMenuController != null) {
            activeMenuController.close(player);
        }
    }

    boolean useTool(
            Player player,
            JigsawStudioToolPayload payload,
            ItemStack tool,
            boolean resetLabel
    ) {
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> useTool(player, payload, tool, resetLabel));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null
                || !studio.generator().getRequest().requestId().equals(payload.requestId())
                || !authorizeOwner(player, studio)) {
            message(player, "This Jigsaw Studio tool is stale or belongs to another session.");
            return false;
        }
        if (payload.action().destructive() && !confirmTool(player, payload)) {
            message(player, "Right-click the same tool again within 10 seconds to confirm "
                    + payload.action().displayName() + ".");
            return true;
        }
        return switch (payload.action()) {
            case OPEN_MENU -> openControlMenu(player);
            case SELECT_WORKCELL -> selectWorkcell(player, payload.workcellId());
            case LOAD_VARIANT -> service.variantEditor.switchVariant(
                    player,
                    payload.workcellId(),
                    payload.pieceKey(),
                    false);
            case CREATE_VARIANT -> service.variantEditor.createVariant(player, payload.workcellId(), false);
            case DUPLICATE_VARIANT -> service.variantEditor.createVariant(player, payload.workcellId(), true);
            case DUPLICATE_FAMILY -> duplicateFamilyFromTool(player);
            case PREVIEW_GRAPH -> service.goToPreview(player);
            case FLUSH_AUTOSAVE -> service.autosaveScheduler.flushAutosave(player, payload.workcellId());
            case TOGGLE_ROTATION -> service.variantProperties.toggleVariantRotatable(player, payload.workcellId());
            case EXPAND_TO_CELL -> service.variantProperties.expandVariantToCell(player, payload.workcellId());
            case RESIZE_VARIANT -> openVariantSizeSettingsFromTool(player, payload);
            case RENAME_VARIANT -> renameVariantFromTool(player, payload, tool, resetLabel);
            case ADJUST_VARIANT_WEIGHT -> service.variantProperties.adjustVariantWeight(
                    player,
                    payload.workcellId(),
                    payload.pieceKey(),
                    payload.poolKey(),
                    payload.entryIndex(),
                    payload.amount());
            case ADJUST_VARIANT_CHANCE -> service.variantProperties.adjustVariantChance(
                    player,
                    payload.workcellId(),
                    payload.pieceKey(),
                    payload.poolKey(),
                    payload.entryIndex(),
                    payload.amount());
            case UNLINK_MEMBERSHIP -> service.variantEditor.unlinkVariantMembership(
                    player,
                    payload.workcellId(),
                    payload.pieceKey(),
                    payload.poolKey(),
                    payload.entryIndex());
            case TOGGLE_WORKCELL -> toggleWorkcellFromTool(player, payload.workcellId());
            case RESIZE_WORKCELL -> openWorkcellSettingsFromTool(player, payload.workcellId());
            case RENAME_WORKCELL -> renameWorkcellFromTool(player, payload, tool, resetLabel);
            case SET_THEME -> useThemeTool(player, payload);
            case SET_PIECE_RULES -> openVariantSettingsFromTool(player, payload);
            case DELETE_VARIANT -> service.variantEditor.deleteVariant(
                    player, payload.workcellId(), payload.pieceKey());
            case DELETE_PROJECT -> service.variantEditor.deleteProject(player);
            case TOGGLE_REQUIRE_CAPS -> toggleRequireCapsFromTool(player);
        };
    }

    private boolean toggleWorkcellFromTool(Player player, String workcellId) {
        Optional<JigsawStudioMenuState> state = menuState(player);
        JigsawStudioMenuState.Workcell workcell = state
                .map(menu -> menu.workcell(workcellId))
                .orElse(null);
        if (workcell == null) {
            message(player, "This bound workcell no longer exists.");
            return false;
        }
        return service.workcellEditor.setWorkcellEnabled(player, workcellId, !workcell.enabled());
    }

    private boolean openWorkcellSettingsFromTool(Player player, String workcellId) {
        JigsawStudioMenuController activeMenuController = menuController;
        return activeMenuController != null
                && activeMenuController.openWorkcellSettings(player, workcellId);
    }

    private boolean openVariantSettingsFromTool(Player player, JigsawStudioToolPayload payload) {
        JigsawStudioMenuController activeMenuController = menuController;
        return activeMenuController != null
                && activeMenuController.openVariantSettings(
                player,
                payload.workcellId(),
                payload.pieceKey());
    }

    private boolean openVariantSizeSettingsFromTool(Player player, JigsawStudioToolPayload payload) {
        JigsawStudioMenuController activeMenuController = menuController;
        return activeMenuController != null
                && activeMenuController.openVariantSizeSettings(
                player,
                payload.workcellId(),
                payload.pieceKey());
    }

    private boolean renameVariantFromTool(
            Player player,
            JigsawStudioToolPayload payload,
            ItemStack tool,
            boolean resetLabel
    ) {
        Optional<String> displayName = toolDisplayName(player, payload, tool, resetLabel);
        return displayName.isPresent() && service.variantProperties.updateVariantDisplayName(
                player,
                payload.workcellId(),
                payload.pieceKey(),
                displayName.get());
    }

    private boolean renameWorkcellFromTool(
            Player player,
            JigsawStudioToolPayload payload,
            ItemStack tool,
            boolean resetLabel
    ) {
        Optional<String> displayName = toolDisplayName(player, payload, tool, resetLabel);
        return displayName.isPresent() && service.workcellEditor.updateWorkcellDisplayName(
                player,
                payload.workcellId(),
                displayName.get());
    }

    private static Optional<String> toolDisplayName(
            Player player,
            JigsawStudioToolPayload payload,
            ItemStack tool,
            boolean resetLabel
    ) {
        if (resetLabel) {
            return Optional.of("");
        }
        String displayName = tool == null || !tool.hasItemMeta() || tool.getItemMeta() == null
                ? ""
                : tool.getItemMeta().getDisplayName();
        String normalized = ChatColor.stripColor(displayName);
        normalized = normalized == null ? "" : normalized.trim();
        String defaultName = "Jigsaw Studio: " + payload.action().displayName();
        if (normalized.isEmpty() || normalized.equals(defaultName)) {
            message(player, "Rename this bound stick in an anvil to the desired label, then right-click it. "
                    + "Sneak-right-click resets the label.");
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    private boolean useThemeTool(Player player, JigsawStudioToolPayload payload) {
        if (!payload.pieceKey().isBlank()) {
            return openVariantSettingsFromTool(player, payload);
        }
        Optional<JigsawStudioMenuState> state = menuState(player);
        if (state.isEmpty()) {
            return false;
        }
        return service.variantEditor.duplicateActiveFamily(
                player,
                JigsawStudioMenuFormat.nextThemeSetKey(state.get().themeSets()));
    }

    private boolean duplicateFamilyFromTool(Player player) {
        Optional<JigsawStudioMenuState> state = menuState(player);
        return state.isPresent() && service.variantEditor.duplicateActiveFamily(
                player,
                JigsawStudioMenuFormat.nextThemeSetKey(state.get().themeSets()));
    }

    private boolean toggleRequireCapsFromTool(Player player) {
        Optional<JigsawStudioMenuState> state = menuState(player);
        return state.isPresent() && service.workcellEditor.setRequireCaps(player, !state.get().requireCaps());
    }

    private boolean confirmTool(Player player, JigsawStudioToolPayload payload) {
        long now = System.nanoTime();
        ToolConfirmation previous = toolConfirmations.get(player.getUniqueId());
        if (previous != null
                && previous.expiresAtNanos() >= now
                && previous.payload().equals(payload)) {
            toolConfirmations.remove(player.getUniqueId(), previous);
            return true;
        }
        toolConfirmations.put(
                player.getUniqueId(),
                new ToolConfirmation(payload, now + TOOL_CONFIRM_NANOS));
        return false;
    }

    JigsawStudioVariant activeOwnedVariant(
            Player player,
            ActiveStudio studio,
            String workcellId
    ) {
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return null;
        }
        JigsawStudioSession session = studio.generator().getSession();
        if (session.layout().get(workcellId) == null) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return null;
        }
        JigsawStudioVariant variant = session.activeVariant(workcellId).orElse(null);
        if (variant == null) {
            message(player, "This workcell has no active variant.");
            return null;
        }
        if (!variant.owned()) {
            message(player, "Variant '" + variant.pieceKey()
                    + "' is read-only. Adopt or clone its graph before editing it.");
            return null;
        }
        return variant;
    }

    JigsawStudioVariant activeOwnedVariant(
            Player player,
            ActiveStudio studio,
            String workcellId,
            String expectedPieceKey
    ) {
        JigsawStudioVariant variant = activeOwnedVariant(player, studio, workcellId);
        if (variant == null) {
            return null;
        }
        if (!variant.pieceKey().equals(expectedPieceKey)) {
            message(player, "The bound variant changed. Open the control menu or request a fresh tool.");
            return null;
        }
        return variant;
    }

    static JigsawStudioPoolMembership findMembership(
            JigsawStudioVariant variant,
            String poolKey,
            int entryIndex
    ) {
        for (JigsawStudioPoolMembership membership : variant.memberships()) {
            if (membership.poolKey().equals(poolKey) && membership.entryIndex() == entryIndex) {
                return membership;
            }
        }
        return null;
    }

    boolean deferDuplicationUntilAutosaved(
            Player player,
            ActiveStudio studio,
            DeferredDuplication requested
    ) {
        JigsawStudioSession session = studio.generator().getSession();
        UUID requestId = studio.generator().getRequest().requestId();
        boolean autosavePending;
        synchronized (service.saveLifecycleLock) {
            autosavePending = session.isDirty() || service.saveLifecycle.savesInProgress.contains(requestId);
        }
        if (!autosavePending) {
            return false;
        }
        DeferredDuplication existing = deferredDuplications.putIfAbsent(requestId, requested);
        if (existing == null) {
            message(player, requested.kind() == DeferredDuplicationKind.SINGLE
                    ? "Autosave is finishing first; this cell's variant will duplicate automatically."
                    : "Autosave is finishing every edited cell first; the coherent family will duplicate automatically.");
            service.autosaveScheduler.expediteAutosaves(requestId);
            scheduleDeferredDuplication(requested);
            return true;
        }
        message(player, existing.sameIntent(requested)
                ? "That duplicate action is already queued behind autosave."
                : "Another duplicate action is already queued behind autosave for this Studio.");
        return true;
    }

    private void scheduleDeferredDuplication(DeferredDuplication deferred) {
        if (deferredDuplications.get(deferred.requestId()) != deferred
                || !deferred.scheduled().compareAndSet(false, true)) {
            return;
        }
        try {
            J.s(() -> {
                deferred.scheduled().set(false);
                if (deferredDuplications.get(deferred.requestId()) != deferred) {
                    return;
                }
                Player player = Bukkit.getPlayer(deferred.playerId());
                if (player == null) {
                    deferredDuplications.remove(deferred.requestId(), deferred);
                    return;
                }
                boolean scheduled = J.runEntity(
                        player,
                        () -> resumeDeferredDuplication(player, deferred),
                        0,
                        () -> deferredDuplications.remove(deferred.requestId(), deferred));
                if (!scheduled) {
                    deferredDuplications.remove(deferred.requestId(), deferred);
                }
            }, AUTOSAVE_RETRY_TICKS);
        } catch (Throwable exception) {
            deferred.scheduled().set(false);
            deferredDuplications.remove(deferred.requestId(), deferred);
            IrisLogging.reportError(exception);
        }
    }

    private void resumeDeferredDuplication(Player player, DeferredDuplication deferred) {
        if (deferredDuplications.get(deferred.requestId()) != deferred) {
            return;
        }
        ActiveStudio studio = service.studios.get(deferred.worldId());
        JigsawStudioSession session = studio == null ? null : studio.generator().getSession();
        boolean currentRequest = studio != null
                && studio.generator().getRequest().requestId().equals(deferred.requestId())
                && session.sessionId().equals(deferred.sessionId())
                && player.getWorld().getUID().equals(deferred.worldId());
        boolean sourcesCurrent = currentRequest && sourceBindingsCurrent(session, deferred.sourcePieces());
        boolean autosavePending;
        boolean operationPending;
        boolean terminal;
        synchronized (service.saveLifecycleLock) {
            autosavePending = currentRequest
                    && (session.isDirty() || service.saveLifecycle.savesInProgress.contains(deferred.requestId()));
            operationPending = currentRequest
                    && (service.graphMutations.graphMutationsInProgress.contains(deferred.requestId())
                    || service.materializer.materializationsInProgress.contains(deferred.requestId())
                    || service.saveLifecycle.exportsInProgress.contains(deferred.requestId())
                    || session.operationInProgress()
                    || service.tileWatcher.hasJigsawTileWatch(deferred.requestId()));
            terminal = !currentRequest
                    || service.saveLifecycle.closingRequests.contains(deferred.requestId())
                    || service.saveLifecycle.reopenRequiredRequests.contains(deferred.requestId());
        }
        DeferredDuplicationReadiness readiness = deferredDuplicationReadiness(
                currentRequest,
                sourcesCurrent,
                autosavePending,
                operationPending,
                terminal);
        if (readiness == DeferredDuplicationReadiness.STALE) {
            deferredDuplications.remove(deferred.requestId(), deferred);
            message(player, "The queued duplicate was cancelled because its Studio or source variant changed.");
            return;
        }
        if (readiness == DeferredDuplicationReadiness.WAITING_FOR_AUTOSAVE) {
            service.autosaveScheduler.expediteAutosaves(deferred.requestId());
            scheduleDeferredDuplication(deferred);
            return;
        }
        if (readiness == DeferredDuplicationReadiness.WAITING_FOR_OPERATION) {
            scheduleDeferredDuplication(deferred);
            return;
        }
        boolean started = deferred.kind() == DeferredDuplicationKind.SINGLE
                ? service.variantEditor.createVariant(player, deferred.workcellId(), true)
                : service.variantEditor.duplicateActiveFamily(player, deferred.themeKey());
        if (started) {
            deferredDuplications.remove(deferred.requestId(), deferred);
            return;
        }
        if (service.isCurrentRequest(studio, deferred.requestId())
                && sourceBindingsCurrent(session, deferred.sourcePieces())) {
            scheduleDeferredDuplication(deferred);
        } else {
            deferredDuplications.remove(deferred.requestId(), deferred);
        }
    }

    static DeferredDuplicationReadiness deferredDuplicationReadiness(
            boolean currentRequest,
            boolean sourcesCurrent,
            boolean autosavePending,
            boolean operationPending,
            boolean terminal
    ) {
        if (!currentRequest || !sourcesCurrent || terminal) {
            return DeferredDuplicationReadiness.STALE;
        }
        if (autosavePending) {
            return DeferredDuplicationReadiness.WAITING_FOR_AUTOSAVE;
        }
        if (operationPending) {
            return DeferredDuplicationReadiness.WAITING_FOR_OPERATION;
        }
        return DeferredDuplicationReadiness.READY;
    }

    private static boolean sourceBindingsCurrent(
            JigsawStudioSession session,
            Map<String, String> expectedSources
    ) {
        if (session == null) {
            return false;
        }
        for (Map.Entry<String, String> source : expectedSources.entrySet()) {
            if (session.activeVariant(source.getKey())
                    .map(JigsawStudioVariant::pieceKey)
                    .filter(source.getValue()::equals)
                    .isEmpty()) {
                return false;
            }
        }
        return true;
    }

    enum DeferredDuplicationReadiness {
        READY,
        WAITING_FOR_AUTOSAVE,
        WAITING_FOR_OPERATION,
        STALE
    }

    private enum DeferredDuplicationKind {
        SINGLE,
        FAMILY
    }

    record DeferredDuplication(
            UUID requestId,
            UUID sessionId,
            UUID playerId,
            UUID worldId,
            DeferredDuplicationKind kind,
            String workcellId,
            Map<String, String> sourcePieces,
            String themeKey,
            AtomicBoolean scheduled
    ) {
        DeferredDuplication {
            Objects.requireNonNull(requestId, "Jigsaw Studio deferred duplicate request ID");
            Objects.requireNonNull(sessionId, "Jigsaw Studio deferred duplicate session ID");
            Objects.requireNonNull(playerId, "Jigsaw Studio deferred duplicate player ID");
            Objects.requireNonNull(worldId, "Jigsaw Studio deferred duplicate world ID");
            Objects.requireNonNull(kind, "Jigsaw Studio deferred duplicate kind");
            workcellId = workcellId == null ? "" : workcellId;
            sourcePieces = Map.copyOf(Objects.requireNonNull(
                    sourcePieces,
                    "Jigsaw Studio deferred duplicate source pieces"));
            themeKey = themeKey == null ? "" : themeKey;
            Objects.requireNonNull(scheduled, "Jigsaw Studio deferred duplicate schedule state");
            if (sourcePieces.isEmpty()) {
                throw new IllegalArgumentException("A deferred duplicate requires at least one source variant");
            }
            if (kind == DeferredDuplicationKind.SINGLE && workcellId.isBlank()) {
                throw new IllegalArgumentException("A deferred single duplicate requires a workcell");
            }
            if (kind == DeferredDuplicationKind.FAMILY && themeKey.isBlank()) {
                throw new IllegalArgumentException("A deferred family duplicate requires a theme key");
            }
        }

        static DeferredDuplication single(
                UUID requestId,
                UUID sessionId,
                Player player,
                UUID worldId,
                String workcellId,
                String sourcePiece
        ) {
            return new DeferredDuplication(
                    requestId,
                    sessionId,
                    player.getUniqueId(),
                    worldId,
                    DeferredDuplicationKind.SINGLE,
                    workcellId,
                    Map.of(workcellId, sourcePiece),
                    "",
                    new AtomicBoolean());
        }

        static DeferredDuplication family(
                UUID requestId,
                UUID sessionId,
                Player player,
                UUID worldId,
                Map<String, String> sourcePieces,
                String themeKey
        ) {
            return new DeferredDuplication(
                    requestId,
                    sessionId,
                    player.getUniqueId(),
                    worldId,
                    DeferredDuplicationKind.FAMILY,
                    "",
                    sourcePieces,
                    themeKey,
                    new AtomicBoolean());
        }

        private boolean sameIntent(DeferredDuplication other) {
            return other != null
                    && playerId.equals(other.playerId)
                    && kind == other.kind
                    && workcellId.equals(other.workcellId)
                    && sourcePieces.equals(other.sourcePieces)
                    && themeKey.equals(other.themeKey);
        }
    }

    record ToolConfirmation(JigsawStudioToolPayload payload, long expiresAtNanos) {
        ToolConfirmation {
            Objects.requireNonNull(payload, "Jigsaw Studio tool confirmation payload");
        }
    }
}
