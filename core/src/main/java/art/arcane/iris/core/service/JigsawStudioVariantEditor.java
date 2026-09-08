package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioActivation;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioGraphEditor;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPoolEditor;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPoolMembership;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioProjectDeletionService;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.service.JigsawStudioMaterializer.MaterializationWork;
import art.arcane.iris.core.service.JigsawStudioMaterializer.VariantReloadRequest;
import art.arcane.iris.core.service.JigsawStudioService.ActiveStudio;
import art.arcane.iris.core.service.JigsawStudioService.CloseStart;
import art.arcane.iris.core.service.JigsawStudioService.CommandGraphMutationResult;
import art.arcane.iris.core.service.JigsawStudioToolbelt.DeferredDuplication;
import art.arcane.iris.engine.platform.studio.generators.JigsawStudioGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static art.arcane.iris.core.service.JigsawStudioChunkWriter.validateMaterialization;
import static art.arcane.iris.core.service.JigsawStudioGraphMutations.loadMappedLayout;
import static art.arcane.iris.core.service.JigsawStudioProtection.authorizeOwner;
import static art.arcane.iris.core.service.JigsawStudioProtection.canCreateVariants;
import static art.arcane.iris.core.service.JigsawStudioProtection.variantCreationSourceFailure;
import static art.arcane.iris.core.service.JigsawStudioService.clearAutosaveHistory;
import static art.arcane.iris.core.service.JigsawStudioService.failureMessage;
import static art.arcane.iris.core.service.JigsawStudioService.message;
import static art.arcane.iris.core.service.JigsawStudioToolbelt.findMembership;

final class JigsawStudioVariantEditor {
    private final JigsawStudioService service;

    JigsawStudioVariantEditor(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    boolean switchVariant(
            Player player,
            String workcellId,
            String targetPieceKey,
            boolean discardDirty
    ) {
        if (player == null || workcellId == null || targetPieceKey == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> switchVariant(
                    player, workcellId, targetPieceKey, discardDirty));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        if (!authorizeOwner(player, studio)) {
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
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (workcell == null) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return false;
        }
        JigsawStudioSession.SwitchStart start = session.beginVariantSwitch(
                workcell.stableId(), targetPieceKey, discardDirty);
        if (start.status() != JigsawStudioSession.SwitchStatus.STARTED) {
            message(player, switch (start.status()) {
                case DIRTY -> "Wait for the current variant to finish autosaving before switching.";
                case SAVE_IN_PROGRESS -> "Wait for the current variant save to finish.";
                case SWITCH_IN_PROGRESS -> "This workcell is already loading another variant.";
                case ALREADY_ACTIVE -> "Variant '" + targetPieceKey + "' is already active.";
                case WRONG_WORKCELL -> "Variant '" + targetPieceKey + "' belongs to another workcell.";
                case UNKNOWN_VARIANT -> "No variant '" + targetPieceKey + "' exists in this Studio catalog.";
                case UNKNOWN_WORKCELL -> "The selected workcell no longer exists.";
                case STARTED -> "The variant switch could not start.";
            });
            return false;
        }
        return materializeVariant(player, studio, workcell, start, null);
    }

    void reloadActiveVariant(
            Player player,
            ActiveStudio studio,
            VariantReloadRequest reload
    ) {
        if (!service.isCurrentRequest(studio, studio.generator().getRequest().requestId())) {
            return;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(reload.workcellId());
        if (workcell == null) {
            message(player, "The resized variant was saved, but its workcell is no longer active. Reopen Studio.");
            return;
        }
        JigsawStudioSession.SwitchStart start = session.beginVariantReload(workcell.stableId());
        if (start.status() != JigsawStudioSession.SwitchStatus.STARTED) {
            service.saveLifecycle.reopenRequiredRequests.add(studio.generator().getRequest().requestId());
            message(player, "The resized variant was saved, but Studio could not reload it: "
                    + start.status() + ". Close and reopen this project before editing that workcell.");
            return;
        }
        materializeVariant(player, studio, workcell, start, reload.previous());
    }

    private boolean materializeVariant(
            Player player,
            ActiveStudio studio,
            JigsawStudioBay workcell,
            JigsawStudioSession.SwitchStart start,
            JigsawStudioGenerator.RenderedBay previousOverride
    ) {
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioSession.VariantSwitchToken token = start.token().orElseThrow();
        String targetPieceKey = token.targetVariant().pieceKey();
        JigsawStudioGenerator.RenderedBay target = studio.generator().renderVariant(
                workcell,
                token.targetVariant());
        if (!target.valid()) {
            session.abortVariantSwitch(token);
            service.playerContext.refreshWorkcellContext(studio.worldId(), workcell.stableId());
            message(player, "Variant '" + targetPieceKey + "' cannot load: " + target.failure());
            return false;
        }
        JigsawStudioGenerator.RenderedBay previous = previousOverride == null
                ? token.previousVariant()
                .map(variant -> studio.generator().renderVariant(workcell, variant))
                .orElseGet(() -> JigsawStudioGenerator.RenderedBay.empty(workcell.bounds().dimensions()))
                : previousOverride;
        if (!previous.valid()) {
            session.abortVariantSwitch(token);
            service.playerContext.refreshWorkcellContext(studio.worldId(), workcell.stableId());
            message(player, "The current variant cannot be retained for rollback: " + previous.failure());
            return false;
        }
        JigsawStudioVariant previousVariant = token.previousVariant().orElse(null);
        if (previousVariant != null) {
            String rollbackFailure = validateMaterialization(previous);
            if (!rollbackFailure.isEmpty()) {
                session.abortVariantSwitch(token);
                service.playerContext.refreshWorkcellContext(studio.worldId(), workcell.stableId());
                message(player, "The current variant cannot be retained for rollback: " + rollbackFailure);
                return false;
            }
        }
        session.selectBay(workcell.stableId());
        service.playerContext.refreshWorkcellContext(studio.worldId(), workcell.stableId());
        message(player, "Loading variant '" + targetPieceKey + "' into " + workcell.stableId() + "...");
        return service.materializer.scheduleMaterialization(new MaterializationWork(
                studio,
                player.getWorld(),
                player,
                workcell,
                token,
                previous,
                target,
                session.workcellSnapshot(workcell.stableId()).connectorsVisible(),
                session.workcellSnapshot(workcell.stableId()).connectorsVisible(),
                false));
    }

    boolean createVariant(
            Player player,
            String workcellId,
            boolean duplicateActive
    ) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> createVariant(player, workcellId, duplicateActive));
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
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (workcell == null) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return false;
        }
        if (!canCreateVariants(session.layout())) {
            message(player, "This Jigsaw Studio graph is read-only. "
                    + "Adopt or clone it before creating variants.");
            return false;
        }
        JigsawStudioVariant activeVariant = session.activeVariant(workcellId).orElse(null);
        String sourceFailure = variantCreationSourceFailure(activeVariant, duplicateActive);
        if (!sourceFailure.isEmpty()) {
            message(player, sourceFailure);
            return false;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        if (duplicateActive && service.toolbelt.deferDuplicationUntilAutosaved(
                player,
                studio,
                DeferredDuplication.single(
                        request.requestId(),
                        session.sessionId(),
                        player,
                        studio.worldId(),
                        workcell.stableId(),
                        activeVariant.pieceKey()))) {
            return true;
        }
        String reservationFailure = service.graphMutations.beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        String archetypeKey = workcell.archetype()
                .map(archetype -> archetype.name().toLowerCase(Locale.ROOT))
                .orElse("spatial");
        String sourcePieceKey = activeVariant.pieceKey();
        message(player, duplicateActive ? "Duplicating the active variant..." : "Creating a new variant...");
        return service.graphMutations.scheduleGraphMutation(
                player,
                studio,
                () -> {
                    Path packRoot = request.source().getDataFolder().toPath();
                    String pieceKey = JigsawStudioGraphEditor.nextVariantKey(
                            packRoot, request.structureKey(), archetypeKey);
                    if (duplicateActive) {
                        JigsawStudioGraphEditor.duplicatePiece(
                                packRoot,
                                request.structureKey(),
                                sourcePieceKey,
                                pieceKey);
                    } else {
                        JigsawStudioGraphEditor.createBlankVariant(
                                packRoot,
                                request.structureKey(),
                                sourcePieceKey,
                                pieceKey);
                    }
                    JigsawStudioLayout updatedLayout = loadMappedLayout(studio);
                    String targetWorkcellId = updatedLayout.workcellForVariant(pieceKey)
                            .map(JigsawStudioBay::stableId)
                            .orElseThrow(() -> new IOException(
                                    "Created variant '" + pieceKey + "' has no Studio workcell"));
                    if (updatedLayout.mode() == JigsawStudioMode.SPATIAL_JIGSAW) {
                        return new CommandGraphMutationResult(
                                updatedLayout,
                                "",
                                "",
                                (duplicateActive ? "Duplicated" : "Created") + " variant '" + pieceKey
                                        + "' in " + targetWorkcellId + ".");
                    }
                    return new CommandGraphMutationResult(
                            updatedLayout,
                            targetWorkcellId,
                            pieceKey,
                            (duplicateActive ? "Duplicated" : "Created") + " variant '" + pieceKey + "'.");
                });
    }

    boolean duplicateActiveFamily(Player player, String themeKey) {
        if (player == null || themeKey == null || themeKey.isBlank()) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> duplicateActiveFamily(player, themeKey));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        if (request.compatibilityTarget() == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE) {
            message(player, "Coherent variant families are Iris-only and cannot be added to a vanilla-portable graph.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        Map<String, String> sourcePieces = new LinkedHashMap<>();
        for (JigsawStudioBay workcell : session.layout().bays()) {
            if (!workcell.enabled()) {
                continue;
            }
            JigsawStudioVariant active = session.activeVariant(workcell.stableId()).orElse(null);
            if (active == null || !active.owned()) {
                message(player, "Load an owned source variant in every enabled workcell before duplicating a family.");
                return false;
            }
            sourcePieces.put(workcell.stableId(), active.pieceKey());
        }
        if (service.toolbelt.deferDuplicationUntilAutosaved(
                player,
                studio,
                DeferredDuplication.family(
                        request.requestId(),
                        session.sessionId(),
                        player,
                        studio.worldId(),
                        sourcePieces,
                        themeKey))) {
            return true;
        }
        String reservationFailure = service.graphMutations.beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        return service.graphMutations.scheduleGraphMutation(
                player,
                studio,
                () -> {
                    JigsawStudioGraphEditor.VariantFamilyCreation creation =
                            JigsawStudioGraphEditor.duplicateActiveFamily(
                                    request.source().getDataFolder().toPath(),
                                    request.structureKey(),
                                    sourcePieces,
                                    themeKey);
                    if (!creation.writeResult().successful()) {
                        throw new IOException("Variant-family transaction failed with "
                                + creation.writeResult().status());
                    }
                    JigsawStudioLayout updatedLayout = loadMappedLayout(studio);
                    Map<String, String> rebinds = updatedLayout.mode() == JigsawStudioMode.SPATIAL_JIGSAW
                            ? Map.of()
                            : creation.pieceKeysByWorkcell();
                    return new CommandGraphMutationResult(
                            updatedLayout,
                            "",
                            "",
                            rebinds,
                            Optional.empty(),
                            "Duplicated every enabled workcell as coherent family '" + themeKey + "' with "
                                    + creation.pieceKeysByWorkcell().size() + " variant(s).");
                });
    }

    boolean deleteVariant(Player player, String workcellId, String pieceKey) {
        if (player == null || workcellId == null || workcellId.isBlank()
                || pieceKey == null || pieceKey.isBlank()) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> deleteVariant(player, workcellId, pieceKey));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        JigsawStudioVariant variant = session.layout().variantCatalog().find(pieceKey).orElse(null);
        if (workcell == null || variant == null || !session.layout().accepts(workcell, variant)) {
            message(player, "Variant '" + pieceKey + "' no longer belongs to workcell '" + workcellId + "'.");
            return false;
        }
        if (!variant.owned()) {
            message(player, "Variant '" + pieceKey + "' is read-only. Adopt or clone it before deletion.");
            return false;
        }
        String activePieceKey = session.activeVariant(workcellId)
                .map(JigsawStudioVariant::pieceKey)
                .orElse("");
        if (session.layout().mode() == JigsawStudioMode.PLANAR_JIGSAW
                && activePieceKey.equals(pieceKey)) {
            message(player, "Load another variant in this workcell before deleting '" + pieceKey + "'.");
            return false;
        }
        String reservationFailure = service.graphMutations.beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        return service.graphMutations.scheduleGraphMutation(
                player,
                studio,
                () -> {
                    JigsawStudioGraphEditor.PieceDeletionResult deletion =
                            JigsawStudioGraphEditor.deletePieceVariant(
                                    request.source().getDataFolder().toPath(),
                                    request.structureKey(),
                                    pieceKey);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Deleted variant '" + pieceKey + "', removed "
                                    + deletion.removedPoolMemberships() + " pool membership(s), and removed "
                                    + deletion.removedObjectResources() + " unshared object(s).");
                });
    }

    boolean deleteProject(Player player) {
        if (player == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> deleteProject(player));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        CloseStart closeStart = service.saveLifecycle.tryBeginClose(request.requestId(), player.getUniqueId(), false);
        if (closeStart != CloseStart.STARTED) {
            message(player, switch (closeStart) {
                case DIRTY -> "Wait for autosave to finish before deleting this project.";
                case SAVE_IN_PROGRESS -> "Wait for the active autosave to finish before deleting this project.";
                case OPERATION_IN_PROGRESS -> "Wait for the active Jigsaw Studio operation to finish.";
                case NOT_OWNER -> "This Jigsaw Studio is owned by another player session.";
                case NOT_ACTIVE -> "This Jigsaw Studio session is no longer active.";
                case STARTED -> "Project deletion could not start.";
            });
            return false;
        }
        Path packRoot = request.source().getDataFolder().toPath();
        message(player, "Inspecting owned resources and reverse references before project deletion...");
        J.a(() -> inspectProjectDeletion(player, studio, request, packRoot));
        return true;
    }

    private void inspectProjectDeletion(
            Player player,
            ActiveStudio studio,
            JigsawStudioActivation.Request request,
            Path packRoot
    ) {
        JigsawStudioProjectDeletionService.DeletionPlan plan;
        try {
            plan = JigsawStudioProjectDeletionService.inspect(packRoot, request.structureKey());
        } catch (Throwable exception) {
            service.saveLifecycle.cancelClose(request.requestId());
            IrisLogging.reportError(exception);
            message(player, "Project deletion inspection failed: " + failureMessage(exception));
            return;
        }
        if (!plan.deletable()) {
            service.saveLifecycle.cancelClose(request.requestId());
            message(player, "Project deletion is blocked by " + plan.blockers().size()
                    + " external reference(s). The first is "
                    + plan.blockers().getFirst().ownerPath() + " at "
                    + plan.blockers().getFirst().location() + ".");
            return;
        }
        boolean scheduled = J.runEntity(
                player,
                () -> closeAndDeleteProject(player, studio, request, plan),
                0,
                () -> service.saveLifecycle.cancelClose(request.requestId()));
        if (!scheduled) {
            service.saveLifecycle.cancelClose(request.requestId());
            message(player, "Project deletion stopped because the owner session ended.");
        }
    }

    private void closeAndDeleteProject(
            Player player,
            ActiveStudio studio,
            JigsawStudioActivation.Request request,
            JigsawStudioProjectDeletionService.DeletionPlan plan
    ) {
        if (!service.isCurrentRequest(studio, request.requestId())) {
            service.saveLifecycle.cancelClose(request.requestId());
            message(player, "Project deletion stopped because the Jigsaw Studio session changed.");
            return;
        }
        IrisServices.get(StudioSVC.class).close().whenComplete((result, throwable) -> {
            Throwable failure = throwable != null
                    ? throwable
                    : result == null ? new IllegalStateException("Studio close completed without a result")
                    : result.failureCause();
            if (failure != null) {
                service.saveLifecycle.cancelClose(request.requestId());
                IrisLogging.reportError(failure);
                message(player, "Project deletion stopped because Studio could not close: "
                        + failureMessage(failure));
                return;
            }
            J.a(() -> applyProjectDeletion(player, request, plan));
        });
    }

    private void applyProjectDeletion(
            Player player,
            JigsawStudioActivation.Request request,
            JigsawStudioProjectDeletionService.DeletionPlan plan
    ) {
        try {
            JigsawStudioProjectDeletionService.ProjectDeletionResult result =
                    JigsawStudioProjectDeletionService.delete(plan);
            try {
                clearAutosaveHistory(
                        request.source().getDataFolder().toPath(),
                        request.structureKey());
            } catch (IOException historyFailure) {
                IrisLogging.reportError(historyFailure);
                message(player, "The project graph was deleted, but its autosave history could not be removed: "
                        + failureMessage(historyFailure));
            }
            request.source().invalidateStructureResources();
            message(player, "Deleted Jigsaw project '" + request.structureKey() + "' and "
                    + result.removedResourceCount() + " owned resource(s).");
            IrisLogging.debug("Jigsaw Studio project deleted: structure=%s resources=%d",
                    request.structureKey(), result.removedResourceCount());
        } catch (Throwable exception) {
            IrisLogging.reportError(exception);
            message(player, "Studio closed, but the project was not deleted: "
                    + failureMessage(exception) + ". Its files remain recoverable on disk.");
        }
    }

    boolean unlinkVariantMembership(
            Player player,
            String workcellId,
            String pieceKey,
            String poolKey,
            int entryIndex
    ) {
        if (player == null || workcellId == null || pieceKey == null || poolKey == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> unlinkVariantMembership(
                    player, workcellId, pieceKey, poolKey, entryIndex));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio != null && !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioVariant variant = service.toolbelt.activeOwnedVariant(player, studio, workcellId, pieceKey);
        if (variant == null) {
            return false;
        }
        JigsawStudioPoolMembership membership = findMembership(variant, poolKey, entryIndex);
        if (membership == null) {
            message(player, "That exact pool membership is no longer present.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String reservationFailure = service.graphMutations.beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        return service.graphMutations.scheduleGraphMutation(
                player,
                studio,
                () -> {
                    JigsawStudioPoolEditor.removeEntry(
                            request.source().getDataFolder().toPath(),
                            request.structureKey(),
                            membership.poolKey(),
                            membership.entryIndex(),
                            variant.pieceKey());
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Unlinked '" + variant.pieceKey() + "' from pool '"
                                    + membership.poolKey() + "'.");
                });
    }
}
