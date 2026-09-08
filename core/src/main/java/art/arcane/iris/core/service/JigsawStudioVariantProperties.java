package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioActivation;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioGraphEditor;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPieceRules;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPoolEditor;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPoolMembership;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.service.JigsawStudioMaterializer.VariantReloadRequest;
import art.arcane.iris.core.service.JigsawStudioService.ActiveStudio;
import art.arcane.iris.core.service.JigsawStudioService.CommandGraphMutationResult;
import art.arcane.iris.engine.object.IrisJigsawThemeSet;
import art.arcane.iris.engine.object.IrisStructure;
import art.arcane.iris.engine.platform.studio.generators.JigsawStudioGenerator;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static art.arcane.iris.core.service.JigsawStudioEvaluator.loadStudioStructure;
import static art.arcane.iris.core.service.JigsawStudioGraphMutations.loadMappedLayout;
import static art.arcane.iris.core.service.JigsawStudioProtection.authorizeOwner;
import static art.arcane.iris.core.service.JigsawStudioProtection.canToggleVariantRotation;
import static art.arcane.iris.core.service.JigsawStudioService.DEFAULT_PIECE_RULES;
import static art.arcane.iris.core.service.JigsawStudioService.message;
import static art.arcane.iris.core.service.JigsawStudioToolbelt.findMembership;

final class JigsawStudioVariantProperties {
    private final JigsawStudioService service;

    JigsawStudioVariantProperties(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    boolean updateVariantThemes(
            Player player,
            String workcellId,
            String pieceKey,
            List<String> themes
    ) {
        if (player == null || pieceKey == null || themes == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> updateVariantThemes(
                    player, workcellId, pieceKey, themes));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioVariant variant = service.toolbelt.activeOwnedVariant(player, studio, workcellId, pieceKey);
        if (variant == null) {
            return false;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        List<String> targetThemes = List.copyOf(themes);
        if (request.compatibilityTarget() == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                && !targetThemes.isEmpty()) {
            message(player, "Piece themes are Iris-only and cannot be added to a vanilla-portable graph.");
            return false;
        }
        IrisStructure structure = loadStudioStructure(studio);
        Set<String> declaredThemes = new HashSet<>();
        if (structure != null && structure.getThemeSets() != null) {
            for (IrisJigsawThemeSet theme : structure.getThemeSets()) {
                if (theme != null) {
                    declaredThemes.add(theme.getKey());
                }
            }
        }
        if (!declaredThemes.containsAll(targetThemes)) {
            message(player, "One or more selected theme sets no longer exist.");
            return false;
        }
        if (variant.themes().equals(targetThemes)) {
            message(player, "Variant theme membership is unchanged.");
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
                    JigsawStudioGraphEditor.updatePieceThemes(
                            request.source().getDataFolder().toPath(),
                            request.structureKey(),
                            variant.pieceKey(),
                            targetThemes);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Updated theme membership for '" + variant.pieceKey() + "'.");
                });
    }

    boolean updateVariantRules(
            Player player,
            String workcellId,
            String pieceKey,
            JigsawStudioPieceRules rules
    ) {
        if (player == null || pieceKey == null || rules == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> updateVariantRules(
                    player, workcellId, pieceKey, rules));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioVariant variant = service.toolbelt.activeOwnedVariant(player, studio, workcellId, pieceKey);
        if (variant == null) {
            return false;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        if (request.compatibilityTarget() == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                && !DEFAULT_PIECE_RULES.equals(rules)) {
            message(player, "Piece placement rules are Iris-only for a vanilla-portable graph.");
            return false;
        }
        if (variant.rules().equals(rules)) {
            message(player, "Variant piece rules are unchanged.");
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
                    JigsawStudioGraphEditor.updatePieceRules(
                            request.source().getDataFolder().toPath(),
                            request.structureKey(),
                            variant.pieceKey(),
                            rules);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Updated placement rules for '" + variant.pieceKey() + "'.");
                });
    }

    boolean toggleVariantRotatable(Player player, String workcellId) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> toggleVariantRotatable(player, workcellId));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio != null && !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioVariant variant = service.toolbelt.activeOwnedVariant(player, studio, workcellId);
        if (variant == null) {
            return false;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        if (!canToggleVariantRotation(request.compatibilityTarget(), variant)) {
            message(player, "Vanilla-portable variants must remain rotatable.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String reservationFailure = service.graphMutations.beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        boolean rotatable = !variant.rotatable();
        return service.graphMutations.scheduleGraphMutation(
                player,
                studio,
                () -> {
                    JigsawStudioGraphEditor.updateRotatable(
                            request.source().getDataFolder().toPath(),
                            request.structureKey(),
                            variant.pieceKey(),
                            rotatable);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Variant rotation is now " + (rotatable ? "enabled." : "disabled."));
                });
    }

    boolean expandVariantToCell(Player player, String workcellId) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> expandVariantToCell(player, workcellId));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio != null && !authorizeOwner(player, studio)) {
            return false;
        }
        if (studio == null) {
            message(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioVariant variant = service.toolbelt.activeOwnedVariant(player, studio, workcellId);
        JigsawStudioBay workcell = session.layout().get(workcellId);
        if (variant == null || workcell == null) {
            return false;
        }
        return resizeVariant(player, workcellId, variant.pieceKey(), workcell.capacity());
    }

    boolean resizeVariant(
            Player player,
            String workcellId,
            String pieceKey,
            JigsawStudioCellDimensions dimensions
    ) {
        if (player == null || workcellId == null || workcellId.isBlank()
                || pieceKey == null || pieceKey.isBlank() || dimensions == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> resizeVariant(player, workcellId, pieceKey, dimensions));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        JigsawStudioVariant variant = session.layout().variantCatalog().find(pieceKey).orElse(null);
        if (workcell == null || variant == null || !session.layout().accepts(workcell, variant)) {
            message(player, "Variant '" + pieceKey + "' does not belong to workcell '" + workcellId + "'.");
            return false;
        }
        if (!variant.owned()) {
            message(player, "Variant '" + pieceKey + "' is read-only. Duplicate it before resizing.");
            return false;
        }
        if (dimensions.width() > workcell.capacity().width()
                || dimensions.height() > workcell.capacity().height()
                || dimensions.depth() > workcell.capacity().depth()) {
            message(player, "Requested variant size " + dimensions.width() + "x" + dimensions.height() + "x"
                    + dimensions.depth() + " exceeds workcell capacity " + workcell.capacity().width() + "x"
                    + workcell.capacity().height() + "x" + workcell.capacity().depth()
                    + ". Increase the workcell capacity first.");
            return false;
        }
        if (variant.mode() == JigsawStudioMode.PLANAR_JIGSAW
                && (dimensions.width() < 3 || dimensions.depth() < 3)) {
            message(player, "Planar variant width and depth must each be at least 3 blocks.");
            return false;
        }
        if (variant.dimensions().filter(dimensions::equals).isPresent()) {
            message(player, "Variant '" + variant.resolvedDisplayName() + "' already uses "
                    + dimensions.width() + "x" + dimensions.height() + "x" + dimensions.depth() + ".");
            return false;
        }
        boolean active = session.activeVariant(workcellId)
                .map(activeVariant -> activeVariant.pieceKey().equals(pieceKey))
                .orElse(false);
        JigsawStudioGenerator.RenderedBay previous = active
                ? studio.generator().renderVariant(workcell, variant)
                : null;
        if (previous != null && !previous.valid()) {
            message(player, "The active variant cannot be retained for live resize rollback: " + previous.failure());
            return false;
        }
        String reservationFailure = service.graphMutations.beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        message(player, "Resizing variant '" + variant.resolvedDisplayName() + "' to "
                + dimensions.width() + "x" + dimensions.height() + "x" + dimensions.depth() + "...");
        return service.graphMutations.scheduleGraphMutation(
                player,
                studio,
                () -> {
                    JigsawStudioGraphEditor.VariantResizeResult resize =
                            JigsawStudioGraphEditor.resizePieceObject(
                            request.source().getDataFolder().toPath(),
                            request.structureKey(),
                            variant.pieceKey(),
                            dimensions);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            Map.of(),
                            active ? Optional.of(new VariantReloadRequest(workcellId, previous)) : Optional.empty(),
                            "Resized variant '" + variant.resolvedDisplayName() + "' from "
                                    + resize.previousDimensions().width() + "x"
                                    + resize.previousDimensions().height() + "x"
                                    + resize.previousDimensions().depth() + " to "
                                    + dimensions.width() + "x" + dimensions.height() + "x"
                                    + dimensions.depth() + ". " + resize.relocatedConnectors()
                                    + " connector(s) moved with the new bounds.");
                });
    }

    boolean updateVariantDisplayName(
            Player player,
            String workcellId,
            String pieceKey,
            String displayName
    ) {
        if (player == null || workcellId == null || pieceKey == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> updateVariantDisplayName(
                    player, workcellId, pieceKey, displayName));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio == null || !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().get(workcellId);
        JigsawStudioVariant variant = session.layout().variantCatalog().find(pieceKey).orElse(null);
        if (workcell == null || variant == null || !session.layout().accepts(workcell, variant)) {
            message(player, "Variant '" + pieceKey + "' does not belong to workcell '" + workcellId + "'.");
            return false;
        }
        if (!variant.owned()) {
            message(player, "Variant '" + pieceKey + "' is read-only. Duplicate it before renaming.");
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
        return service.graphMutations.scheduleGraphMutation(
                player,
                studio,
                () -> {
                    JigsawStudioGraphEditor.updatePieceDisplayName(
                            request.source().getDataFolder().toPath(),
                            request.structureKey(),
                            pieceKey,
                            normalizedName);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            normalizedName.isEmpty()
                                    ? "Reset the variant label to its resource-key fallback."
                                    : "Renamed the variant to '" + normalizedName + "'.");
                });
    }

    boolean adjustVariantWeight(
            Player player,
            String workcellId,
            String pieceKey,
            String poolKey,
            int entryIndex,
            int delta
    ) {
        if (delta == 0) {
            return false;
        }
        if (player == null || workcellId == null || pieceKey == null || poolKey == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> adjustVariantWeight(
                    player, workcellId, pieceKey, poolKey, entryIndex, delta));
        }
        ActiveStudio studio = service.studios.get(player.getWorld().getUID());
        if (studio != null && !authorizeOwner(player, studio)) {
            return false;
        }
        JigsawStudioVariant variant = service.toolbelt.activeOwnedVariant(player, studio, workcellId, pieceKey);
        if (variant == null) {
            return false;
        }
        JigsawStudioPoolMembership membership = findMembership(
                variant, poolKey, entryIndex);
        if (membership == null) {
            message(player, "That exact pool membership is no longer present.");
            return false;
        }
        int weight;
        try {
            weight = Math.addExact(membership.weight(), delta);
        } catch (ArithmeticException exception) {
            message(player, "The requested variant weight is outside the supported integer range.");
            return false;
        }
        if (weight < 1) {
            message(player, "Variant weights cannot be lower than 1; unlink the membership instead.");
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
                    JigsawStudioPoolEditor.updateWeightAtIndex(
                            request.source().getDataFolder().toPath(),
                            request.structureKey(),
                            membership.poolKey(),
                            membership.entryIndex(),
                            variant.pieceKey(),
                            weight);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Set '" + variant.pieceKey() + "' weight to " + weight
                                    + " in pool '" + membership.poolKey() + "'.");
                });
    }

    boolean adjustVariantChance(
            Player player,
            String workcellId,
            String pieceKey,
            String poolKey,
            int entryIndex,
            int deltaPercentagePoints
    ) {
        if (deltaPercentagePoints == 0 || player == null || workcellId == null
                || pieceKey == null || poolKey == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> adjustVariantChance(
                    player,
                    workcellId,
                    pieceKey,
                    poolKey,
                    entryIndex,
                    deltaPercentagePoints));
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
        double chance = membership.chance() + deltaPercentagePoints / 100.0D;
        if (!Double.isFinite(chance) || chance < 0.0D || chance > 1.0D) {
            message(player, "Variant chance must stay between 0% and 100%.");
            return false;
        }
        chance = Math.round(chance * 1_000_000.0D) / 1_000_000.0D;
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        if (request.compatibilityTarget() == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                && chance != 1.0D) {
            message(player, "Chance gates are Iris-only and cannot be added to a vanilla-portable graph.");
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        String reservationFailure = service.graphMutations.beginGraphMutation(studio, session);
        if (!reservationFailure.isEmpty()) {
            message(player, reservationFailure);
            return false;
        }
        double targetChance = chance;
        return service.graphMutations.scheduleGraphMutation(
                player,
                studio,
                () -> {
                    JigsawStudioPoolEditor.updateChanceAtIndex(
                            request.source().getDataFolder().toPath(),
                            request.structureKey(),
                            membership.poolKey(),
                            membership.entryIndex(),
                            variant.pieceKey(),
                            targetChance);
                    return new CommandGraphMutationResult(
                            loadMappedLayout(studio),
                            "",
                            "",
                            "Set '" + variant.pieceKey() + "' chance to "
                                    + Math.round(targetChance * 100.0D) + "% in pool '"
                                    + membership.poolKey() + "'.");
                });
    }

    static boolean canResizeVariantToCapacity(
            JigsawStudioBay workcell,
            JigsawStudioVariant variant,
            boolean active
    ) {
        if (!active || !variant.owned()) {
            return false;
        }
        JigsawStudioCellDimensions canonical = variant.dimensions().orElse(null);
        if (canonical == null) {
            return false;
        }
        JigsawStudioCellDimensions target = workcell.capacity();
        return canonical.width() <= target.width()
                && canonical.height() <= target.height()
                && canonical.depth() <= target.depth()
                && (canonical.width() < target.width()
                || canonical.height() < target.height()
                || canonical.depth() < target.depth());
    }
}
