package art.arcane.iris.studio.jigsaw;

import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuController.CHANCE_STEP_PERCENTAGE_POINTS;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuController.GRID_POSITIONS;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuController.TOOLS_PER_PAGE;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.clampPage;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.displayKey;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.element;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.evaluationElement;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.nextThemeSetKey;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.page;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.pageCount;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.safe;

final class JigsawStudioToolboxMenu {
    private final JigsawStudioMenuController controller;

    JigsawStudioToolboxMenu(JigsawStudioMenuController controller) {
        this.controller = Objects.requireNonNull(controller, "Jigsaw Studio menu controller");
    }

    void render(
            UIWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            int requestedPage
    ) {
        List<ToolboxTool> allTools = toolboxTools(state, workcell);
        int page = clampPage(requestedPage, allTools.size(), TOOLS_PER_PAGE);
        List<ToolboxTool> tools = page(allTools, page, TOOLS_PER_PAGE);
        window.batch(() -> {
            window.clearElements();

            UIElement back = element("toolbox-back", Material.ARROW, ChatColor.YELLOW + "Back to Variants");
            back.onLeftClick(clicked -> controller.refreshMain(
                    window.getViewer(), state.requestId(), workcell.stableId(), 0));
            window.setElement(-4, 0, back);

            UIElement heading = element("toolbox-heading", Material.CHEST, ChatColor.AQUA + "Bound Tool Sticks");
            heading.addLore(ChatColor.GRAY + "Every stick is bound to request " + state.requestId());
            heading.addLore(ChatColor.GRAY + "Stale sticks stop working when this Studio closes.");
            window.setElement(0, 0, heading);
            window.setElement(4, 0, evaluationElement(state.evaluation()));

            for (int index = 0; index < tools.size(); index++) {
                ToolboxTool tool = tools.get(index);
                int position = GRID_POSITIONS[index % GRID_POSITIONS.length];
                int row = 1 + index / GRID_POSITIONS.length;
                UIElement element = element(
                        "tool-" + index,
                        Material.STICK,
                        ChatColor.AQUA + safe(tool.displayName()));
                element.addLore(ChatColor.GRAY + "Action: " + tool.payload().action().displayName());
                addToolBindingLore(element, tool.payload());
                element.addLore(tool.payload().action().destructive()
                        ? ChatColor.RED + "Bound tools require confirmation when used."
                        : ChatColor.YELLOW + "Left-click to receive this named stick.");
                element.onLeftClick(clicked -> controller.giveTool(
                        window.getViewer(), state.requestId(), tool.payload()));
                window.setElement(position, row, element);
            }

            UIElement footerBack = element("toolbox-footer-back", Material.ARROW, ChatColor.YELLOW + "Back");
            footerBack.onLeftClick(clicked -> controller.refreshMain(
                    window.getViewer(), state.requestId(), workcell.stableId(), 0));
            window.setElement(-4, 5, footerBack);

            int pageCount = pageCount(allTools.size(), TOOLS_PER_PAGE);
            if (page > 0) {
                UIElement previous = element("toolbox-previous", Material.ARROW, ChatColor.YELLOW + "Previous Page");
                previous.onLeftClick(clicked -> controller.refreshToolbox(
                        window.getViewer(), state.requestId(), workcell.stableId(), page - 1));
                window.setElement(-1, 5, previous);
            }
            UIElement indicator = element("toolbox-page", Material.BOOK, ChatColor.WHITE + "Page "
                    + (page + 1) + "/" + pageCount);
            indicator.addLore(ChatColor.GRAY + "" + allTools.size() + " bound tools");
            window.setElement(0, 5, indicator);
            if (page + 1 < pageCount) {
                UIElement next = element("toolbox-next", Material.ARROW, ChatColor.YELLOW + "Next Page");
                next.onLeftClick(clicked -> controller.refreshToolbox(
                        window.getViewer(), state.requestId(), workcell.stableId(), page + 1));
                window.setElement(1, 5, next);
            }
        });
    }

    static List<ToolboxTool> toolboxTools(
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell
    ) {
        JigsawStudioMenuState menu = Objects.requireNonNull(state, "Jigsaw Studio toolbox state");
        JigsawStudioMenuState.Workcell selected = Objects.requireNonNull(
                workcell,
                "Jigsaw Studio toolbox workcell");
        UUID requestId = menu.requestId();
        List<ToolboxTool> tools = new ArrayList<>();
        tools.add(new ToolboxTool(
                "Open Control Menu",
                JigsawStudioToolPayload.request(JigsawStudioToolAction.OPEN_MENU, requestId)));
        tools.add(new ToolboxTool(
                "Select " + selected.displayName(),
                JigsawStudioToolPayload.workcell(
                        JigsawStudioToolAction.SELECT_WORKCELL,
                        requestId,
                        selected.stableId())));
        if (menu.mode() == JigsawStudioMode.PLANAR_JIGSAW) {
            tools.add(new ToolboxTool(
                    "Toggle " + selected.displayName(),
                    JigsawStudioToolPayload.workcell(
                            JigsawStudioToolAction.TOGGLE_WORKCELL,
                            requestId,
                            selected.stableId())));
        }
        tools.add(new ToolboxTool(
                "Resize " + selected.displayName() + " Capacity",
                JigsawStudioToolPayload.workcell(
                        JigsawStudioToolAction.RESIZE_WORKCELL,
                        requestId,
                        selected.stableId())));
        tools.add(new ToolboxTool(
                "Rename " + selected.displayName(),
                JigsawStudioToolPayload.workcell(
                        JigsawStudioToolAction.RENAME_WORKCELL,
                        requestId,
                        selected.stableId())));
        tools.add(new ToolboxTool(
                "New Blank " + selected.displayName() + " Variant",
                JigsawStudioToolPayload.workcell(
                        JigsawStudioToolAction.CREATE_VARIANT,
                        requestId,
                        selected.stableId())));
        tools.add(new ToolboxTool(
                "Go to Preview",
                JigsawStudioToolPayload.request(JigsawStudioToolAction.PREVIEW_GRAPH, requestId)));
        tools.add(new ToolboxTool(
                "Flush " + selected.displayName() + " Autosave",
                JigsawStudioToolPayload.workcell(
                        JigsawStudioToolAction.FLUSH_AUTOSAVE,
                        requestId,
                        selected.stableId())));
        if (menu.irisExtended()) {
            tools.add(new ToolboxTool(
                    "Duplicate All Enabled Cells as Family: " + nextThemeSetKey(menu.themeSets()),
                    JigsawStudioToolPayload.request(JigsawStudioToolAction.DUPLICATE_FAMILY, requestId)));
        }

        JigsawStudioMenuState.Variant active = selected.activeVariant();
        if (active != null) {
            if (active.owned()) {
                tools.add(new ToolboxTool(
                        "Duplicate This Cell's Variant: " + active.displayName(),
                        JigsawStudioToolPayload.variant(
                                JigsawStudioToolAction.DUPLICATE_VARIANT,
                                requestId,
                                selected.stableId(),
                                active.pieceKey())));
            }
            if (active.owned()) {
                tools.add(new ToolboxTool(
                        "Rename This Variant: " + active.displayName(),
                        JigsawStudioToolPayload.variant(
                                JigsawStudioToolAction.RENAME_VARIANT,
                                requestId,
                                selected.stableId(),
                                active.pieceKey())));
                tools.add(new ToolboxTool(
                        "Resize This Variant: " + active.displayName(),
                        JigsawStudioToolPayload.variant(
                                JigsawStudioToolAction.RESIZE_VARIANT,
                                requestId,
                                selected.stableId(),
                                active.pieceKey())));
            }
            if (active.rotationEditable()) {
                tools.add(new ToolboxTool(
                        "Toggle Rotation: " + active.displayName(),
                        JigsawStudioToolPayload.variant(
                                JigsawStudioToolAction.TOGGLE_ROTATION,
                                requestId,
                                selected.stableId(),
                                active.pieceKey())));
            }
            if (active.owned() && active.resizableToCapacity()) {
                tools.add(new ToolboxTool(
                        "Resize " + active.displayName() + " to Capacity",
                        JigsawStudioToolPayload.variant(
                                JigsawStudioToolAction.EXPAND_TO_CELL,
                                requestId,
                                selected.stableId(),
                                active.pieceKey())));
            }
            if (active.owned() && menu.irisExtended()) {
                tools.add(new ToolboxTool(
                        "Open Theme Memberships: " + active.displayName(),
                        JigsawStudioToolPayload.variant(
                                JigsawStudioToolAction.SET_THEME,
                                requestId,
                                selected.stableId(),
                                active.pieceKey())));
                tools.add(new ToolboxTool(
                        "Open Piece Rules: " + active.displayName(),
                        JigsawStudioToolPayload.variant(
                                JigsawStudioToolAction.SET_PIECE_RULES,
                                requestId,
                                selected.stableId(),
                                active.pieceKey())));
            }
            if (active.owned()) {
                for (JigsawStudioMenuState.Membership membership : active.memberships()) {
                    addMembershipTools(
                            tools,
                            requestId,
                            selected,
                            active,
                            membership,
                            menu.irisExtended());
                }
            }
        }
        for (JigsawStudioMenuState.Variant variant : selected.variants()) {
            if (variant.active()) {
                continue;
            }
            tools.add(new ToolboxTool(
                    "Load " + variant.displayName(),
                    JigsawStudioToolPayload.variant(
                            JigsawStudioToolAction.LOAD_VARIANT,
                            requestId,
                            selected.stableId(),
                            variant.pieceKey())));
            if (variant.owned()) {
                tools.add(new ToolboxTool(
                        "Rename " + variant.displayName(),
                        JigsawStudioToolPayload.variant(
                                JigsawStudioToolAction.RENAME_VARIANT,
                                requestId,
                                selected.stableId(),
                                variant.pieceKey())));
                tools.add(new ToolboxTool(
                        "Resize " + variant.displayName(),
                        JigsawStudioToolPayload.variant(
                                JigsawStudioToolAction.RESIZE_VARIANT,
                                requestId,
                                selected.stableId(),
                                variant.pieceKey())));
            }
            if (variant.owned() && selected.variants().size() > 1) {
                tools.add(new ToolboxTool(
                        "Delete " + variant.displayName(),
                        JigsawStudioToolPayload.variant(
                                JigsawStudioToolAction.DELETE_VARIANT,
                                requestId,
                                selected.stableId(),
                                variant.pieceKey())));
            }
        }
        if (menu.irisExtended()) {
            tools.add(new ToolboxTool(
                    menu.requireCaps() ? "Disable Mandatory Caps" : "Enable Mandatory Caps",
                    JigsawStudioToolPayload.request(JigsawStudioToolAction.TOGGLE_REQUIRE_CAPS, requestId)));
        }
        tools.add(new ToolboxTool(
                "Delete Project",
                JigsawStudioToolPayload.request(JigsawStudioToolAction.DELETE_PROJECT, requestId)));
        return List.copyOf(tools);
    }

    private static void addMembershipTools(
            List<ToolboxTool> tools,
            UUID requestId,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            JigsawStudioMenuState.Membership membership,
            boolean chanceEditable
    ) {
        String membershipName = displayKey(membership.poolKey()) + " [" + membership.entryIndex() + "]";
        tools.add(new ToolboxTool(
                "Weight +1: " + membershipName,
                JigsawStudioToolPayload.membership(
                        JigsawStudioToolAction.ADJUST_VARIANT_WEIGHT,
                        requestId,
                        workcell.stableId(),
                        variant.pieceKey(),
                        membership.poolKey(),
                        membership.entryIndex(),
                        1)));
        tools.add(new ToolboxTool(
                "Weight -1: " + membershipName,
                JigsawStudioToolPayload.membership(
                        JigsawStudioToolAction.ADJUST_VARIANT_WEIGHT,
                        requestId,
                        workcell.stableId(),
                        variant.pieceKey(),
                        membership.poolKey(),
                        membership.entryIndex(),
                        -1)));
        if (chanceEditable) {
            tools.add(new ToolboxTool(
                    "Chance +" + CHANCE_STEP_PERCENTAGE_POINTS + "%: " + membershipName,
                    JigsawStudioToolPayload.membership(
                            JigsawStudioToolAction.ADJUST_VARIANT_CHANCE,
                            requestId,
                            workcell.stableId(),
                            variant.pieceKey(),
                            membership.poolKey(),
                            membership.entryIndex(),
                            CHANCE_STEP_PERCENTAGE_POINTS)));
            tools.add(new ToolboxTool(
                    "Chance -" + CHANCE_STEP_PERCENTAGE_POINTS + "%: " + membershipName,
                    JigsawStudioToolPayload.membership(
                            JigsawStudioToolAction.ADJUST_VARIANT_CHANCE,
                            requestId,
                            workcell.stableId(),
                            variant.pieceKey(),
                            membership.poolKey(),
                            membership.entryIndex(),
                            -CHANCE_STEP_PERCENTAGE_POINTS)));
        }
        tools.add(new ToolboxTool(
                "Unlink " + membershipName,
                JigsawStudioToolPayload.membership(
                        JigsawStudioToolAction.UNLINK_MEMBERSHIP,
                        requestId,
                        workcell.stableId(),
                        variant.pieceKey(),
                        membership.poolKey(),
                        membership.entryIndex(),
                        0)));
    }

    private static void addToolBindingLore(UIElement element, JigsawStudioToolPayload payload) {
        if (!payload.workcellId().isEmpty()) {
            element.addLore(ChatColor.GRAY + "Workcell: " + safe(payload.workcellId()));
        }
        if (!payload.pieceKey().isEmpty()) {
            element.addLore(ChatColor.GRAY + "Variant: " + safe(payload.pieceKey()));
        }
        if (!payload.poolKey().isEmpty()) {
            element.addLore(ChatColor.GRAY + "Pool: " + safe(payload.poolKey())
                    + (payload.entryIndex() < 0 ? "" : " [" + payload.entryIndex() + "]"));
        }
        if (payload.amount() != 0) {
            element.addLore(ChatColor.GRAY + "Amount: " + payload.amount());
        }
    }

    record ToolboxTool(String displayName, JigsawStudioToolPayload payload) {
        ToolboxTool {
            displayName = Objects.requireNonNull(displayName, "Jigsaw Studio toolbox display name").trim();
            if (displayName.isEmpty()) {
                throw new IllegalArgumentException("Jigsaw Studio toolbox display name cannot be blank");
            }
            payload = Objects.requireNonNull(payload, "Jigsaw Studio toolbox payload");
        }
    }
}
