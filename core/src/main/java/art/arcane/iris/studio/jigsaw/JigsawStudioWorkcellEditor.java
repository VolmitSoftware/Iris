package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.studio.jigsaw.JigsawStudioMaterializer.MaterializationWork;
import art.arcane.iris.studio.jigsaw.JigsawStudioService.ActiveStudio;
import art.arcane.iris.studio.jigsaw.JigsawStudioService.CommandGraphMutationResult;
import art.arcane.iris.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.studio.generation.JigsawStudioGenerator;
import art.arcane.iris.world.task.J;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static art.arcane.iris.studio.jigsaw.JigsawStudioChunkWriter.validateMaterialization;
import static art.arcane.iris.studio.jigsaw.JigsawStudioEvaluator.loadStudioStructure;
import static art.arcane.iris.studio.jigsaw.JigsawStudioGraphMutations.loadMappedLayout;
import static art.arcane.iris.studio.jigsaw.JigsawStudioProtection.authorizeOwner;
import static art.arcane.iris.studio.jigsaw.JigsawStudioService.message;

final class JigsawStudioWorkcellEditor {
    private final JigsawStudioService service;

    JigsawStudioWorkcellEditor(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    boolean setConnectorBlocksVisible(Player player, String workcellId, boolean visible) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> setConnectorBlocksVisible(player, workcellId, visible));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (workcell == null) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return false;
        }
        JigsawStudioSession.WorkcellSnapshot snapshot = session.workcellSnapshot(workcellId);
        if (snapshot.connectorsVisible() == visible) {
            message(player, "Connector blocks are already " + (visible ? "visible." : "hidden."));
            return false;
        }
        JigsawStudioSession.SwitchStart start = session.beginVariantReload(workcellId);
        if (start.status() != JigsawStudioSession.SwitchStatus.STARTED) {
            message(player, switch (start.status()) {
                case DIRTY -> "Wait for this workcell to finish autosaving before changing connector visibility.";
                case SAVE_IN_PROGRESS -> "Wait for the current workcell save to finish.";
                case SWITCH_IN_PROGRESS -> "This workcell is already loading another view.";
                case UNKNOWN_WORKCELL -> "The selected workcell no longer exists.";
                case UNKNOWN_VARIANT -> "This workcell has no active variant.";
                case ALREADY_ACTIVE, WRONG_WORKCELL, STARTED ->
                        "Connector visibility could not change: " + start.status() + ".";
            });
            return false;
        }
        JigsawStudioSession.VariantSwitchToken token = start.token().orElseThrow();
        JigsawStudioGenerator.RenderedBay rendered = studio.generator().renderVariant(
                workcell,
                token.targetVariant());
        if (!rendered.valid()) {
            session.abortVariantSwitch(token);
            message(player, "Connector visibility cannot change: " + rendered.failure());
            return false;
        }
        message(player, (visible ? "Showing" : "Hiding") + " connector blocks in " + workcellId + "...");
        return service.materializer.scheduleMaterialization(new MaterializationWork(
                studio,
                player.getWorld(),
                player,
                workcell,
                token,
                rendered,
                rendered,
                snapshot.connectorsVisible(),
                visible,
                true));
    }

    boolean resetConnectorBlocks(Player player, String workcellId) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> resetConnectorBlocks(player, workcellId));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (workcell == null) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return false;
        }
        JigsawStudioVariant activeVariant = session.activeVariant(workcellId).orElse(null);
        if (activeVariant == null || !activeVariant.owned()) {
            message(player, "Load an owned variant before resetting its connector blocks.");
            return false;
        }
        JigsawStudioGenerator.RenderedBay rendered = studio.generator().renderVariant(
                workcell,
                activeVariant);
        String validationFailure = validateMaterialization(rendered);
        if (!validationFailure.isEmpty()) {
            message(player, "Connector blocks cannot reset: " + validationFailure);
            return false;
        }
        if (rendered.connectors().isEmpty()) {
            message(player, "The active variant has no saved connectors to reset.");
            return false;
        }
        String reservationFailure = service.materializer.beginConnectorRepair(studio);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        boolean connectorsVisible = session.workcellSnapshot(workcellId).connectorsVisible();
        message(player, "Resetting " + rendered.connectors().size()
                + " connector block(s) from the last saved iteration...");
        return service.materializer.scheduleConnectorRepair(
                player,
                studio,
                workcell,
                rendered,
                connectorsVisible);
    }

    boolean setWorkcellEnabled(Player player, String workcellId, boolean enabled) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> setWorkcellEnabled(player, workcellId, enabled));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (workcell == null || workcell.archetype().isEmpty()) {
            message(player, "Only planar Jigsaw Studio workcells can be enabled or disabled.");
            return false;
        }
        if (workcell.enabled() == enabled) {
            message(player, "Workcell '" + workcellId + "' is already "
                    + (enabled ? "enabled." : "disabled."));
            return false;
        }
        String reservationFailure = service.graphMutations.beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        JigsawPlanarArchetype archetype = workcell.archetype().orElseThrow();
        return service.graphMutations.scheduleGraphMutation(
                player,
                studio,
                () -> {
                    JigsawStudioStructureEditor.updateWorkcellEnabled(
                            request.source().getDataFolder().toPath(),
                            request.structureKey(),
                            archetype,
                            enabled);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Workcell '" + workcellId + "' is now "
                                    + (enabled ? "enabled." : "disabled for assembly and export."));
                });
    }

    boolean updateWorkcellDimensions(
            Player player,
            String workcellId,
            JigsawStudioCellDimensions dimensions
    ) {
        if (player == null || workcellId == null || workcellId.isBlank() || dimensions == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> updateWorkcellDimensions(player, workcellId, dimensions));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (workcell == null) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return false;
        }
        if (workcell.capacity().equals(dimensions)) {
            message(player, "Workcell '" + workcellId + "' already has capacity "
                    + dimensions.width() + "x" + dimensions.height() + "x" + dimensions.depth() + ".");
            return false;
        }
        if (workcell.archetype().isPresent()
                && (dimensions.width() < 3 || dimensions.depth() < 3)) {
            message(player, "Planar workcell width and depth must each be at least 3 blocks.");
            return false;
        }
        String reservationFailure = service.graphMutations.beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        JigsawPlanarArchetype archetype = workcell.archetype().orElse(null);
        return service.graphMutations.scheduleGraphMutation(
                player,
                studio,
                () -> {
                    JigsawStudioGraphEditor.WorkcellCapacityResult capacityResult = null;
                    if (archetype == null) {
                        JigsawStudioStructureEditor.updateCellSize(
                                request.source().getDataFolder().toPath(),
                                request.structureKey(),
                                dimensions);
                    } else {
                        capacityResult = JigsawStudioGraphEditor.updatePlanarWorkcellCapacity(
                                request.source().getDataFolder().toPath(),
                                request.structureKey(),
                                archetype,
                                dimensions);
                    }
                    request.source().invalidateStructureResources();
                    String resizeSummary = capacityResult == null
                            ? ""
                            : " Verified " + capacityResult.checkedVariants() + " existing variant"
                            + (capacityResult.checkedVariants() == 1 ? "" : "s") + "; no variant object was resized.";
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Updated '" + workcellId + "' capacity to "
                                    + dimensions.width() + "x" + dimensions.height() + "x"
                                    + dimensions.depth() + "." + resizeSummary
                                    + " The live workcell layout was regenerated.");
                });
    }

    boolean setRequireCaps(Player player, boolean requireCaps) {
        if (player == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> setRequireCaps(player, requireCaps));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        if (request.compatibilityTarget() == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                && requireCaps) {
            message(player, "Mandatory caps are Iris-only and cannot be enabled for a vanilla-portable graph.");
            return false;
        }
        IrisStructure structure = loadStudioStructure(studio);
        if (structure == null) {
            message(player, "The active jigsaw structure could not be loaded.");
            return false;
        }
        if (structure.isRequireCaps() == requireCaps) {
            message(player, "Mandatory caps are already " + (requireCaps ? "enabled." : "disabled."));
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String reservationFailure = service.graphMutations.beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        return service.graphMutations.scheduleGraphMutation(
                player,
                studio,
                () -> {
                    JigsawStudioStructureEditor.updateRequireCaps(
                            request.source().getDataFolder().toPath(),
                            request.structureKey(),
                            requireCaps);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Mandatory physical caps are now "
                                    + (requireCaps ? "enabled." : "disabled."));
                });
    }

    boolean updateThemeSetWeight(Player player, String themeKey, int weight) {
        if (player == null || themeKey == null || themeKey.isBlank() || weight < 1) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> updateThemeSetWeight(player, themeKey, weight));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        if (request.compatibilityTarget() == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE) {
            message(player, "Coherent theme sets are Iris-only.");
            return false;
        }
        IrisStructure structure = loadStudioStructure(studio);
        if (structure == null || structure.getThemeSets() == null) {
            message(player, "The active jigsaw structure has no theme sets.");
            return false;
        }
        List<IrisJigsawThemeSet> updated = new ArrayList<>(structure.getThemeSets().size());
        boolean found = false;
        for (IrisJigsawThemeSet theme : structure.getThemeSets()) {
            if (theme != null && themeKey.equals(theme.getKey())) {
                if (theme.getWeight() == weight) {
                    message(player, "Theme '" + themeKey + "' already has weight " + weight + ".");
                    return false;
                }
                updated.add(new IrisJigsawThemeSet(themeKey, weight));
                found = true;
            } else if (theme != null) {
                updated.add(new IrisJigsawThemeSet(theme.getKey(), theme.getWeight()));
            }
        }
        if (!found) {
            message(player, "Theme set '" + themeKey + "' no longer exists.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String reservationFailure = service.graphMutations.beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        return service.graphMutations.scheduleGraphMutation(
                player,
                studio,
                () -> {
                    JigsawStudioStructureEditor.updateThemeSets(
                            request.source().getDataFolder().toPath(),
                            request.structureKey(),
                            updated);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Set theme '" + themeKey + "' weight to " + weight + ".");
                });
    }

    boolean updateWorkcellDisplayName(
            Player player,
            String workcellId,
            String displayName
    ) {
        if (player == null || workcellId == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> updateWorkcellDisplayName(player, workcellId, displayName));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (workcell == null) {
            message(player, "Unknown Jigsaw Studio workcell '" + workcellId + "'.");
            return false;
        }
        String normalizedName;
        try {
            normalizedName = JigsawStudioGraphEditor.normalizeDisplayName(displayName);
        } catch (IllegalArgumentException exception) {
            message(player, exception.getMessage());
            return false;
        }
        String reservationFailure = service.graphMutations.beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        JigsawPlanarArchetype archetype = workcell.archetype().orElse(null);
        return service.graphMutations.scheduleGraphMutation(
                player,
                studio,
                () -> {
                    if (archetype == null) {
                        JigsawStudioStructureEditor.updateSpatialWorkcellDisplayName(
                                request.source().getDataFolder().toPath(),
                                request.structureKey(),
                                normalizedName);
                    } else {
                        JigsawStudioStructureEditor.updateWorkcellDisplayName(
                                request.source().getDataFolder().toPath(),
                                request.structureKey(),
                                archetype,
                                normalizedName);
                    }
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            normalizedName.isEmpty()
                                    ? "Reset the workcell label to '" + workcell.canonicalDisplayName() + "'."
                                    : "Renamed the workcell to '" + normalizedName + "'.");
                });
    }
}
