package art.arcane.iris.core.service;

import art.arcane.iris.core.service.JigsawStudioMenuFormat.DimensionAxis;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolAction;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolPayload;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.iris.core.service.JigsawStudioMenuController.send;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.adjustedDimensions;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.dimensions;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.element;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.evaluationElement;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.safe;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.withCapacity;

final class JigsawStudioWorkcellMenu {
    private static final int WORKCELL_RESIZE_REFRESH_TICKS = 2;
    private static final int WORKCELL_RESIZE_REFRESH_ATTEMPTS = 200;
    private final Map<UUID, PendingWorkcellResize> pendingWorkcellResizes = new ConcurrentHashMap<>();

    private final JigsawStudioMenuController controller;

    JigsawStudioWorkcellMenu(JigsawStudioMenuController controller) {
        this.controller = Objects.requireNonNull(controller, "Jigsaw Studio menu controller");
    }

    void render(
            UIWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell
    ) {
        PendingWorkcellResize pendingResize = pendingWorkcellResize(
                window.getViewer().getUniqueId(),
                state.requestId(),
                workcell.stableId());
        JigsawStudioMenuState.Workcell renderedWorkcell = pendingResize == null
                ? workcell
                : withCapacity(workcell, pendingResize.dimensions());
        window.batch(() -> {
            window.clearElements();

            UIElement back = element("settings-back", Material.ARROW, ChatColor.YELLOW + "Back to Variants");
            back.onLeftClick(clicked -> leaveWorkcellSettings(
                    window.getViewer(), state.requestId(), renderedWorkcell.stableId()));
            window.setElement(-4, 0, back);

            UIElement identity = element(
                    "settings-identity",
                    renderedWorkcell.enabled() ? Material.LIME_WOOL : Material.GRAY_WOOL,
                    ChatColor.AQUA + safe(renderedWorkcell.displayName()));
            identity.addLore(ChatColor.DARK_GRAY + safe(renderedWorkcell.stableId()));
            identity.addLore(renderedWorkcell.enabled()
                    ? ChatColor.GREEN + "Enabled"
                    : ChatColor.RED + "Disabled for assembly and export");
            if (!renderedWorkcell.canonicalName().equals(renderedWorkcell.displayName())) {
                identity.addLore(ChatColor.GRAY + "Solver role: " + safe(renderedWorkcell.canonicalName()));
            }
            identity.addLore(ChatColor.GRAY + "Capacity: " + dimensions(renderedWorkcell.capacity()));
            if (pendingResize != null) {
                identity.addLore(pendingResize.applying()
                        ? ChatColor.YELLOW + "Applying one live relayout"
                        : ChatColor.GOLD + "Pending; apply when all dimensions are ready");
            }
            identity.addLore(ChatColor.YELLOW + "Left-click for a rename stick");
            identity.addLore(ChatColor.GRAY + "Rename that stick in an anvil, then right-click it.");
            identity.addLore(ChatColor.GRAY + "Sneak-right-click the stick to reset this label.");
            identity.onLeftClick(clicked -> controller.actions.giveTool(
                    window.getViewer(),
                    JigsawStudioToolPayload.workcell(
                            JigsawStudioToolAction.RENAME_WORKCELL,
                            state.requestId(),
                            renderedWorkcell.stableId())));
            window.setElement(0, 0, identity);
            window.setElement(4, 0, evaluationElement(state.evaluation()));

            if (state.mode() == JigsawStudioMode.PLANAR_JIGSAW) {
                UIElement enabled = element(
                        "settings-enabled",
                        renderedWorkcell.enabled() ? Material.LEVER : Material.REDSTONE_TORCH,
                        renderedWorkcell.enabled()
                                ? ChatColor.GREEN + "Workcell Enabled"
                                : ChatColor.RED + "Workcell Disabled");
                enabled.addLore(ChatColor.GRAY + "Disabled workcells remain editable and keep their size.");
                enabled.addLore(ChatColor.YELLOW + "Left-click to "
                        + (renderedWorkcell.enabled() ? "disable" : "enable"));
                enabled.onLeftClick(clicked -> controller.setWorkcellEnabled(
                        window.getViewer(),
                        state.requestId(),
                        renderedWorkcell.stableId(),
                        !renderedWorkcell.enabled()));
                window.setElement(0, 1, enabled);
            } else {
                UIElement spatial = element(
                        "spatial-bounds",
                        Material.SCAFFOLDING,
                        ChatColor.AQUA + "Spatial Workcell Bounds");
                spatial.addLore(ChatColor.GRAY + "Spatial project bounds are shared by the project.");
                spatial.addLore(ChatColor.GRAY + "Changing them regenerates the Studio layout.");
                window.setElement(0, 1, spatial);
            }

            UIElement connectors = element(
                    "settings-connectors",
                    renderedWorkcell.connectorsVisible() ? Material.JIGSAW : Material.STRUCTURE_VOID,
                    renderedWorkcell.connectorsVisible()
                            ? ChatColor.GREEN + "Connector Blocks Visible"
                            : ChatColor.YELLOW + "Connector Blocks Hidden");
            connectors.addLore(ChatColor.GRAY + "Hidden connectors retain their metadata and final block state.");
            connectors.addLore(ChatColor.YELLOW + "Left-click to "
                    + (renderedWorkcell.connectorsVisible() ? "hide" : "show") + " connector blocks");
            connectors.onLeftClick(clicked -> controller.toggleConnectorBlocks(
                    window.getViewer(),
                    state.requestId(),
                    renderedWorkcell.stableId(),
                    !renderedWorkcell.connectorsVisible()));
            window.setElement(2, 1, connectors);

            UIElement resetConnectors = element(
                    "settings-reset-connectors",
                    Material.RECOVERY_COMPASS,
                    ChatColor.AQUA + "Reset Connector Blocks");
            resetConnectors.addLore(ChatColor.GRAY + "Restore every connector in this workcell from disk.");
            resetConnectors.addLore(ChatColor.GRAY + "Other edited blocks are left unchanged.");
            resetConnectors.onLeftClick(clicked -> controller.resetConnectorBlocks(
                    window.getViewer(),
                    state.requestId(),
                    renderedWorkcell.stableId()));
            window.setElement(4, 1, resetConnectors);

            window.setElement(-2, 2, axisElement(
                    window,
                    state,
                    renderedWorkcell,
                    DimensionAxis.WIDTH,
                    Material.IRON_INGOT));
            window.setElement(0, 2, axisElement(
                    window,
                    state,
                    renderedWorkcell,
                    DimensionAxis.HEIGHT,
                    Material.GOLD_INGOT));
            window.setElement(2, 2, axisElement(
                    window,
                    state,
                    renderedWorkcell,
                    DimensionAxis.DEPTH,
                    Material.COPPER_INGOT));

            if (pendingResize != null) {
                UIElement apply = element(
                        "settings-apply-capacity",
                        pendingResize.applying() ? Material.CLOCK : Material.EMERALD_BLOCK,
                        pendingResize.applying()
                                ? ChatColor.YELLOW + "Applying Cell Size"
                                : ChatColor.GREEN + "Apply Cell Size");
                apply.addLore(ChatColor.WHITE + dimensions(pendingResize.dimensions()));
                apply.addLore(ChatColor.GRAY + "Regenerates the layout once after all size edits.");
                if (!pendingResize.applying()) {
                    apply.onLeftClick(clicked -> applyWorkcellResize(
                            window.getViewer(), state.requestId(), renderedWorkcell.stableId()));
                }
                window.setElement(0, 3, apply);

                if (!pendingResize.applying()) {
                    UIElement discard = element(
                            "settings-discard-capacity",
                            Material.BARRIER,
                            ChatColor.RED + "Discard Size Changes");
                    discard.onLeftClick(clicked -> discardWorkcellResize(
                            window.getViewer(), state.requestId(), renderedWorkcell.stableId()));
                    window.setElement(2, 3, discard);
                }
            }

            UIElement footerBack = element("settings-footer-back", Material.ARROW, ChatColor.YELLOW + "Back");
            footerBack.onLeftClick(clicked -> leaveWorkcellSettings(
                    window.getViewer(), state.requestId(), renderedWorkcell.stableId()));
            window.setElement(-4, 5, footerBack);

            UIElement undo = element(
                    "settings-undo",
                    Material.CLOCK,
                    ChatColor.LIGHT_PURPLE + "Undo Last Autosave");
            undo.addLore(ChatColor.GRAY + "Restore the previous owned graph iteration.");
            undo.addLore(ChatColor.GRAY + "Up to five autosave iterations are retained on disk.");
            undo.onLeftClick(clicked -> controller.undoAutosave(
                    window.getViewer(),
                    state.requestId()));
            window.setElement(0, 5, undo);

            if (renderedWorkcell.dirty() && !renderedWorkcell.saving()) {
                UIElement saveNow = element(
                        "save-now",
                        Material.EMERALD,
                        ChatColor.GOLD + "Flush Autosave Now");
                saveNow.addLore(ChatColor.GRAY + "Autosave is automatic.");
                saveNow.addLore(ChatColor.GRAY + "Use this only to flush pending work or recover immediately.");
                saveNow.onLeftClick(clicked -> controller.flushNow(
                        window.getViewer(), state.requestId(), renderedWorkcell.stableId()));
                window.setElement(2, 5, saveNow);
            }

            UIElement preview = element(
                    "settings-preview",
                    Material.ENDER_EYE,
                    ChatColor.LIGHT_PURPLE + "Go to Preview");
            preview.onLeftClick(clicked -> controller.goToPreview(window.getViewer(), state.requestId()));
            window.setElement(3, 5, preview);

            UIElement toolbox = element("settings-toolbox", Material.STICK, ChatColor.AQUA + "Toolbox");
            toolbox.onLeftClick(clicked -> controller.openToolbox(
                    window.getViewer(), state.requestId(), renderedWorkcell.stableId(), 0));
            window.setElement(4, 5, toolbox);
        });
    }

    void renderSize(
            UIWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant
    ) {
        JigsawStudioCellDimensions current = variant.dimensions().orElseThrow();
        window.batch(() -> {
            window.clearElements();

            UIElement back = element("variant-size-back", Material.ARROW, ChatColor.YELLOW + "Back to Details");
            back.onLeftClick(clicked -> controller.openDetails(
                    window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), 0));
            window.setElement(-4, 0, back);

            UIElement identity = element(
                    "variant-size-identity",
                    Material.SCAFFOLDING,
                    ChatColor.AQUA + safe(variant.displayName()) + " Size");
            identity.addLore(ChatColor.DARK_GRAY + safe(variant.pieceKey()));
            identity.addLore(ChatColor.WHITE + "Current: " + dimensions(current));
            identity.addLore(ChatColor.GRAY + "Workcell capacity: " + dimensions(workcell.capacity()));
            identity.addLore(ChatColor.GRAY + "Only this variant object and its edge connectors change.");
            window.setElement(0, 0, identity);
            window.setElement(4, 0, evaluationElement(state.evaluation()));

            window.setElement(-2, 2, variantAxisElement(
                    window, state, workcell, variant, DimensionAxis.WIDTH, Material.IRON_INGOT));
            window.setElement(0, 2, variantAxisElement(
                    window, state, workcell, variant, DimensionAxis.HEIGHT, Material.GOLD_INGOT));
            window.setElement(2, 2, variantAxisElement(
                    window, state, workcell, variant, DimensionAxis.DEPTH, Material.COPPER_INGOT));

            UIElement capacity = element(
                    "variant-size-capacity",
                    variant.resizableToCapacity() ? Material.NETHER_STAR : Material.GRAY_DYE,
                    variant.resizableToCapacity()
                            ? ChatColor.GREEN + "Resize This Variant to Capacity"
                            : ChatColor.GRAY + "Variant Already Matches Capacity");
            capacity.addLore(ChatColor.GRAY + dimensions(workcell.capacity()));
            if (variant.resizableToCapacity()) {
                capacity.addLore(ChatColor.YELLOW + "Left-click to resize only this variant");
                capacity.onLeftClick(clicked -> controller.resizeVariant(
                        window.getViewer(),
                        state.requestId(),
                        workcell.stableId(),
                        variant.pieceKey(),
                        workcell.capacity()));
            }
            window.setElement(0, 3, capacity);

            UIElement footerBack = element("variant-size-footer-back", Material.ARROW, ChatColor.YELLOW + "Back");
            footerBack.onLeftClick(clicked -> controller.openDetails(
                    window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), 0));
            window.setElement(-4, 5, footerBack);
        });
    }

    private UIElement variantAxisElement(
            UIWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            DimensionAxis axis,
            Material material
    ) {
        JigsawStudioCellDimensions current = variant.dimensions().orElseThrow();
        UIElement element = element(
                "variant-axis-" + axis.name().toLowerCase(Locale.ROOT),
                material,
                ChatColor.AQUA + axis.displayName() + ": " + axis.value(current));
        element.addLore(ChatColor.GREEN + "Left-click: +1");
        element.addLore(ChatColor.YELLOW + "Right-click: -1");
        element.addLore(ChatColor.GREEN + "Shift-left: +8");
        element.addLore(ChatColor.YELLOW + "Shift-right: -8");
        element.addLore(ChatColor.GRAY + "Maximum: " + axis.value(workcell.capacity()));
        element.onLeftClick(clicked -> controller.resizeVariantAxis(
                window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), axis, 1));
        element.onRightClick(clicked -> controller.resizeVariantAxis(
                window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), axis, -1));
        element.onShiftLeftClick(clicked -> controller.resizeVariantAxis(
                window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), axis, 8));
        element.onShiftRightClick(clicked -> controller.resizeVariantAxis(
                window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), axis, -8));
        return element;
    }

    private UIElement axisElement(
            UIWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            DimensionAxis axis,
            Material material
    ) {
        UIElement element = element(
                "axis-" + axis.name().toLowerCase(Locale.ROOT),
                material,
                ChatColor.AQUA + axis.displayName() + " Capacity: " + axis.value(workcell.capacity()));
        element.addLore(ChatColor.GREEN + "Left-click: +1");
        element.addLore(ChatColor.YELLOW + "Right-click: -1");
        element.addLore(ChatColor.GREEN + "Shift-left: +8");
        element.addLore(ChatColor.YELLOW + "Shift-right: -8");
        element.addLore(ChatColor.GRAY + "Changes stay in this menu until Apply Cell Size.");
        element.onLeftClick(clicked -> resizeWorkcell(
                window.getViewer(), state.requestId(), workcell.stableId(), axis, 1));
        element.onRightClick(clicked -> resizeWorkcell(
                window.getViewer(), state.requestId(), workcell.stableId(), axis, -1));
        element.onShiftLeftClick(clicked -> resizeWorkcell(
                window.getViewer(), state.requestId(), workcell.stableId(), axis, 8));
        element.onShiftRightClick(clicked -> resizeWorkcell(
                window.getViewer(), state.requestId(), workcell.stableId(), axis, -8));
        return element;
    }

    private void resizeWorkcell(
            Player player,
            UUID requestId,
            String workcellId,
            DimensionAxis axis,
            int delta
    ) {
        Optional<JigsawStudioMenuState> current = controller.matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        if (workcell == null) {
            controller.stale(player);
            return;
        }
        UUID playerId = player.getUniqueId();
        PendingWorkcellResize existing = pendingWorkcellResize(playerId, requestId, workcellId);
        if (existing != null && existing.applying()) {
            send(player, ChatColor.YELLOW + "That cell size is already being applied.");
            return;
        }
        JigsawStudioCellDimensions base = existing == null
                ? workcell.capacity()
                : existing.dimensions();
        Optional<JigsawStudioCellDimensions> adjusted = adjustedDimensions(
                base, axis, delta);
        if (adjusted.isEmpty()) {
            send(player, ChatColor.RED + "That workcell size is outside Iris limits.");
            return;
        }
        PendingWorkcellResize pending = new PendingWorkcellResize(
                requestId,
                workcellId,
                adjusted.get(),
                false);
        pendingWorkcellResizes.put(playerId, pending);
        UIWindow window = controller.windows.get(playerId);
        if (window != null) {
            render(window, current.get(), withCapacity(workcell, pending.dimensions()));
        }
    }

    private void applyWorkcellResize(Player player, UUID requestId, String workcellId) {
        Optional<JigsawStudioMenuState> current = controller.matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        PendingWorkcellResize pending = pendingWorkcellResize(playerId, requestId, workcellId);
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        if (pending == null || pending.applying() || workcell == null) {
            return;
        }
        if (workcell.capacity().equals(pending.dimensions())) {
            pendingWorkcellResizes.remove(playerId, pending);
            refreshWorkcellSettings(player, requestId, workcellId);
            return;
        }
        PendingWorkcellResize applying = new PendingWorkcellResize(
                requestId,
                workcellId,
                pending.dimensions(),
                true);
        pendingWorkcellResizes.put(playerId, applying);
        UIWindow window = controller.windows.get(playerId);
        if (window != null) {
            render(window, current.get(), withCapacity(workcell, applying.dimensions()));
        }
        if (!controller.actions.updateWorkcellDimensions(player, workcellId, applying.dimensions())) {
            pendingWorkcellResizes.replace(playerId, applying, pending);
            refreshWorkcellSettings(player, requestId, workcellId);
            return;
        }
        scheduleWorkcellResizeRefresh(player, applying, WORKCELL_RESIZE_REFRESH_ATTEMPTS);
    }

    private void scheduleWorkcellResizeRefresh(
            Player player,
            PendingWorkcellResize pending,
            int attemptsRemaining
    ) {
        boolean scheduled = J.runEntity(
                player,
                () -> refreshAppliedWorkcellResize(player, pending, attemptsRemaining),
                WORKCELL_RESIZE_REFRESH_TICKS);
        if (!scheduled) {
            pendingWorkcellResizes.remove(player.getUniqueId(), pending);
        }
    }

    private void refreshAppliedWorkcellResize(
            Player player,
            PendingWorkcellResize pending,
            int attemptsRemaining
    ) {
        UUID playerId = player.getUniqueId();
        if (!pending.equals(pendingWorkcellResizes.get(playerId))) {
            return;
        }
        Optional<JigsawStudioMenuState> current = controller.matchingState(player, pending.requestId(), false);
        JigsawStudioMenuState.Workcell workcell = current
                .map(state -> state.workcell(pending.workcellId()))
                .orElse(null);
        if (workcell == null) {
            pendingWorkcellResizes.remove(playerId, pending);
            return;
        }
        if (workcell.capacity().equals(pending.dimensions())) {
            pendingWorkcellResizes.remove(playerId, pending);
            UIWindow window = controller.windows.get(playerId);
            if (window != null) {
                render(window, current.orElseThrow(), workcell);
            }
            return;
        }
        if (attemptsRemaining > 0) {
            scheduleWorkcellResizeRefresh(player, pending, attemptsRemaining - 1);
            return;
        }
        PendingWorkcellResize retry = new PendingWorkcellResize(
                pending.requestId(),
                pending.workcellId(),
                pending.dimensions(),
                false);
        pendingWorkcellResizes.replace(playerId, pending, retry);
        UIWindow window = controller.windows.get(playerId);
        if (window != null) {
            render(window, current.orElseThrow(), withCapacity(workcell, retry.dimensions()));
        }
        send(player, ChatColor.YELLOW
                + "Cell resizing is still pending; use Apply Cell Size to retry after the current operation settles.");
    }

    private void discardWorkcellResize(Player player, UUID requestId, String workcellId) {
        PendingWorkcellResize pending = pendingWorkcellResize(player.getUniqueId(), requestId, workcellId);
        if (pending != null && !pending.applying()) {
            pendingWorkcellResizes.remove(player.getUniqueId(), pending);
        }
        refreshWorkcellSettings(player, requestId, workcellId);
    }

    private void leaveWorkcellSettings(Player player, UUID requestId, String workcellId) {
        PendingWorkcellResize pending = pendingWorkcellResize(player.getUniqueId(), requestId, workcellId);
        if (pending != null && !pending.applying()) {
            pendingWorkcellResizes.remove(player.getUniqueId(), pending);
        }
        controller.refreshMain(player, requestId, workcellId, 0);
    }

    private void refreshWorkcellSettings(Player player, UUID requestId, String workcellId) {
        Optional<JigsawStudioMenuState> current = controller.matchingState(player, requestId, true);
        UIWindow window = controller.windows.get(player.getUniqueId());
        JigsawStudioMenuState.Workcell workcell = current
                .map(state -> state.workcell(workcellId))
                .orElse(null);
        if (window == null || workcell == null) {
            return;
        }
        render(window, current.orElseThrow(), workcell);
    }

    private PendingWorkcellResize pendingWorkcellResize(UUID playerId, UUID requestId, String workcellId) {
        PendingWorkcellResize pending = pendingWorkcellResizes.get(playerId);
        if (pending == null
                || !pending.requestId().equals(requestId)
                || !pending.workcellId().equals(workcellId)) {
            return null;
        }
        return pending;
    }

    private record PendingWorkcellResize(
            UUID requestId,
            String workcellId,
            JigsawStudioCellDimensions dimensions,
            boolean applying
    ) {
        private PendingWorkcellResize {
            requestId = Objects.requireNonNull(requestId, "Jigsaw Studio resize request ID");
            workcellId = Objects.requireNonNull(workcellId, "Jigsaw Studio resize workcell ID");
            dimensions = Objects.requireNonNull(dimensions, "Jigsaw Studio resize dimensions");
        }
    }
    void clearStagedResize(UUID playerId) {
        pendingWorkcellResizes.remove(playerId);
    }

    void clearStagedResizes() {
        pendingWorkcellResizes.clear();
    }
}
