package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolAction;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolPayload;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.List;
import java.util.Objects;

import static art.arcane.iris.core.service.JigsawStudioMenuController.CHANCE_STEP_PERCENTAGE_POINTS;
import static art.arcane.iris.core.service.JigsawStudioMenuController.GRID_POSITIONS;
import static art.arcane.iris.core.service.JigsawStudioMenuController.MEMBERSHIPS_PER_PAGE;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.chance;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.clampPage;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.dimensions;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.displayKey;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.element;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.evaluationElement;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.maximumPlacements;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.page;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.pageCount;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.safe;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.themes;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.yesNo;

final class JigsawStudioDetailsMenu {
    private final JigsawStudioMenuController controller;

    JigsawStudioDetailsMenu(JigsawStudioMenuController controller) {
        this.controller = Objects.requireNonNull(controller, "Jigsaw Studio menu controller");
    }

    void render(
            UIWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            int requestedPage
    ) {
        int page = clampPage(requestedPage, variant.memberships().size(), MEMBERSHIPS_PER_PAGE);
        controller.purgeExpiredConfirmations(window.getViewer().getUniqueId());
        window.batch(() -> {
            window.clearElements();
            addDetailsHeader(window, state, workcell, variant, page);
            addMemberships(window, state, workcell, variant, page);
            addDetailsFooter(window, state, workcell, variant, page);
        });
    }

    private void addDetailsHeader(
            UIWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            int page
    ) {
        UIElement back = element("back", Material.ARROW, ChatColor.YELLOW + "Back to Variants");
        back.onLeftClick(clicked -> controller.refreshMain(
                window.getViewer(), state.requestId(), workcell.stableId(), 0));
        window.setElement(-4, 0, back);

        UIElement identity = element("identity", Material.NAME_TAG, ChatColor.AQUA + safe(variant.displayName()));
        identity.addLore(ChatColor.DARK_GRAY + safe(variant.pieceKey()));
        identity.addLore(variant.owned()
                ? ChatColor.GREEN + "Owned variant"
                : ChatColor.GRAY + "Read-only variant");
        identity.addLore(variant.active()
                ? ChatColor.GREEN + "Loaded in this workcell"
                : ChatColor.GRAY + "Not currently loaded");
        identity.addLore(ChatColor.GRAY + "Size: " + variant.dimensions()
                .map(JigsawStudioMenuFormat::dimensions)
                .orElse("Object missing"));
        identity.addLore(ChatColor.GRAY + "Themes: " + themes(variant.themes()));
        identity.addLore(ChatColor.GRAY + "Depth: " + variant.rules().minimumDepth()
                + "-" + variant.rules().maximumDepth());
        identity.addLore(ChatColor.GRAY + "Placements: " + variant.rules().minimumPlacements()
                + "-" + maximumPlacements(variant.rules().maximumPlacements()));
        identity.addLore(ChatColor.GRAY + "Terminal: " + yesNo(variant.rules().terminal()));
        identity.addLore(ChatColor.GRAY + "Pool entries: " + variant.memberships().size());
        if (variant.owned()) {
            identity.addLore(ChatColor.YELLOW + "Left-click for a rename stick");
            identity.addLore(ChatColor.GRAY + "Rename that stick in an anvil, then right-click it.");
            identity.addLore(ChatColor.GRAY + "Sneak-right-click the stick to reset this label.");
            identity.onLeftClick(clicked -> controller.actions.giveTool(
                    window.getViewer(),
                    JigsawStudioToolPayload.variant(
                            JigsawStudioToolAction.RENAME_VARIANT,
                            state.requestId(),
                            workcell.stableId(),
                            variant.pieceKey())));
        }
        window.setElement(0, 0, identity);

        boolean activeOwned = variant.active() && variant.owned();
        boolean irisRuleEditing = activeOwned && state.irisExtended();
        UIElement settings = element(
                "variant-settings",
                irisRuleEditing ? Material.COMPARATOR : Material.GRAY_DYE,
                irisRuleEditing
                        ? ChatColor.GOLD + "Themes & Piece Rules"
                        : ChatColor.GRAY + "Themes & Rules Unavailable");
        settings.addLore(irisRuleEditing
                ? ChatColor.GRAY + "Edit this loaded variant's theme membership and placement rules."
                : state.irisExtended()
                        ? ChatColor.GRAY + "Load an owned variant before editing its rules."
                        : ChatColor.GRAY + "Vanilla-portable pieces cannot encode Iris theme or rule metadata.");
        if (irisRuleEditing) {
            settings.addLore(ChatColor.YELLOW + "Left-click to edit");
            settings.onLeftClick(clicked -> controller.openVariantSettings(
                    window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), 0));
        }
        window.setElement(-2, 0, settings);

        boolean rotationEditable = variant.active() && variant.rotationEditable();
        UIElement rotation = element(
                "rotation",
                rotationEditable ? Material.REPEATER : Material.BARRIER,
                rotationEditable
                        ? ChatColor.LIGHT_PURPLE + "Toggle Rotation"
                        : ChatColor.GRAY + "Rotation is Read-only");
        rotation.addLore(ChatColor.GRAY + "Currently: " + (variant.rotatable() ? "Enabled" : "Disabled"));
        if (rotationEditable) {
            rotation.addLore(ChatColor.YELLOW + "Left-click to toggle");
            rotation.onLeftClick(clicked -> controller.toggleRotation(
                    window.getViewer(), state.requestId(), workcell.stableId()));
        } else if (!variant.active()) {
            rotation.addLore(ChatColor.GRAY + "Load this variant before editing rotation.");
        } else if (variant.owned() && variant.rotatable()) {
            rotation.addLore(ChatColor.GRAY + "Vanilla-portable variants must remain rotatable.");
        }
        window.setElement(3, 0, rotation);

        boolean sizeEditable = variant.owned() && variant.dimensions().isPresent();
        UIElement size = element(
                "variant-size",
                sizeEditable ? Material.SCAFFOLDING : Material.BARRIER,
                sizeEditable
                        ? ChatColor.GREEN + "Edit This Variant's Size"
                        : ChatColor.GRAY + "Variant Size is Read-only");
        size.addLore(ChatColor.GRAY + "Current: " + variant.dimensions()
                .map(JigsawStudioMenuFormat::dimensions)
                .orElse("Object missing"));
        size.addLore(ChatColor.GRAY + "Workcell capacity: " + dimensions(workcell.capacity()));
        if (sizeEditable) {
            size.addLore(ChatColor.YELLOW + "Left-click for width, height, and depth controls");
            size.onLeftClick(clicked -> controller.openVariantSizeSettings(
                    window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey()));
            if (variant.resizableToCapacity()) {
                size.addLore(ChatColor.YELLOW + "Shift-left: resize this variant to capacity");
                size.onShiftLeftClick(clicked -> controller.resizeVariant(
                        window.getViewer(),
                        state.requestId(),
                        workcell.stableId(),
                        variant.pieceKey(),
                        workcell.capacity()));
            }
        }
        window.setElement(2, 0, size);

        boolean lastVariant = workcell.variants().size() <= 1;
        boolean deletionAvailable = variant.owned() && !variant.active() && !lastVariant;
        boolean confirmed = deletionAvailable && controller.isDeleteConfirmed(
                window.getViewer().getUniqueId(),
                state.requestId(),
                workcell.stableId(),
                variant.pieceKey());
        UIElement delete = element(
                "delete-variant",
                deletionAvailable ? confirmed ? Material.REDSTONE : Material.LAVA_BUCKET : Material.BARRIER,
                deletionAvailable
                        ? confirmed ? ChatColor.RED + "Confirm Delete Variant" : ChatColor.RED + "Delete Variant"
                        : variant.active()
                                ? ChatColor.GRAY + "Loaded Variant Cannot Be Deleted"
                                : lastVariant
                                        ? ChatColor.GRAY + "Last Variant Cannot Be Deleted"
                                        : ChatColor.GRAY + "Deletion is Read-only")
                .setEnchanted(confirmed);
        if (deletionAvailable) {
            delete.addLore(confirmed
                    ? ChatColor.RED + "Click again to permanently delete this owned variant."
                    : ChatColor.RED + "Click twice within 10 seconds to confirm.");
            delete.onLeftClick(clicked -> controller.confirmOrDeleteVariant(
                    window.getViewer(),
                    state.requestId(),
                    workcell.stableId(),
                    variant.pieceKey(),
                    page));
        } else if (variant.active()) {
            delete.addLore(ChatColor.GRAY + "Load another variant in this workcell first.");
        } else if (lastVariant) {
            delete.addLore(ChatColor.GRAY + "Create another variant before deleting this one.");
        }
        window.setElement(4, 0, delete);
    }

    private void addMemberships(
            UIWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            int page
    ) {
        List<JigsawStudioMenuState.Membership> memberships = page(
                variant.memberships(), page, MEMBERSHIPS_PER_PAGE);
        if (memberships.isEmpty()) {
            UIElement empty = element("no-memberships", Material.BARRIER, ChatColor.RED + "No Pool Entries");
            empty.addLore(ChatColor.GRAY + "This variant is not linked from an owned pool.");
            window.setElement(0, 2, empty);
            return;
        }

        for (int index = 0; index < memberships.size(); index++) {
            JigsawStudioMenuState.Membership membership = memberships.get(index);
            int position = GRID_POSITIONS[index % GRID_POSITIONS.length];
            int row = 1 + index / GRID_POSITIONS.length;
            boolean confirmed = variant.active() && variant.owned() && controller.isUnlinkConfirmed(
                    window.getViewer().getUniqueId(),
                    state.requestId(),
                    workcell.stableId(),
                    variant.pieceKey(),
                    membership);
            UIElement element = element(
                    "membership-" + index,
                    confirmed ? Material.REDSTONE : Material.GOLD_NUGGET,
                    (confirmed ? ChatColor.RED : ChatColor.GOLD)
                            + safe(displayKey(membership.poolKey()))
                            + " [" + membership.entryIndex() + "]")
                    .setEnchanted(confirmed);
            element.addLore(ChatColor.DARK_GRAY + safe(membership.poolKey()));
            element.addLore(ChatColor.GRAY + "Exact entry: " + membership.entryIndex());
            element.addLore(ChatColor.WHITE + "Weight: " + membership.weight());
            element.addLore((state.irisExtended() ? ChatColor.WHITE : ChatColor.GRAY)
                    + "Chance: " + chance(membership.chance())
                    + (state.irisExtended() ? "" : " (Iris only)"));
            if (variant.active() && variant.owned()) {
                element.addLore(ChatColor.GREEN + "Left-click: weight +1");
                element.addLore(ChatColor.YELLOW + "Right-click: weight -1");
                if (state.irisExtended()) {
                    element.addLore(ChatColor.GREEN + "Shift-left: chance +"
                            + CHANCE_STEP_PERCENTAGE_POINTS + "%");
                    element.addLore(ChatColor.YELLOW + "Shift-right: chance -"
                            + CHANCE_STEP_PERCENTAGE_POINTS + "%");
                } else {
                    element.addLore(ChatColor.GRAY + "Vanilla-portable pool entries always have 100% chance.");
                }
                element.addLore(confirmed
                        ? ChatColor.RED + "Middle-click again to unlink"
                        : ChatColor.RED + "Middle-click twice to unlink");
                element.onLeftClick(clicked -> controller.adjustMembershipWeight(
                        window.getViewer(), state.requestId(), workcell.stableId(), membership, 1));
                element.onRightClick(clicked -> controller.adjustMembershipWeight(
                        window.getViewer(), state.requestId(), workcell.stableId(), membership, -1));
                if (state.irisExtended()) {
                    element.onShiftLeftClick(clicked -> controller.adjustMembershipChance(
                            window.getViewer(),
                            state.requestId(),
                            workcell.stableId(),
                            membership,
                            CHANCE_STEP_PERCENTAGE_POINTS));
                    element.onShiftRightClick(clicked -> controller.adjustMembershipChance(
                            window.getViewer(),
                            state.requestId(),
                            workcell.stableId(),
                            membership,
                            -CHANCE_STEP_PERCENTAGE_POINTS));
                }
                element.onMiddleClick(clicked -> controller.confirmOrUnlink(
                        window.getViewer(), state.requestId(), workcell.stableId(), membership, page));
            } else {
                element.addLore(variant.active()
                        ? ChatColor.GRAY + "Read-only pool entry"
                        : ChatColor.GRAY + "Load this variant to edit its pool entry");
            }
            window.setElement(position, row, element);
        }
    }

    private void addDetailsFooter(
            UIWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            int page
    ) {
        UIElement back = element("footer-back", Material.ARROW, ChatColor.YELLOW + "Back");
        back.onLeftClick(clicked -> controller.refreshMain(
                window.getViewer(), state.requestId(), workcell.stableId(), 0));
        window.setElement(-4, 5, back);

        int pageCount = pageCount(variant.memberships().size(), MEMBERSHIPS_PER_PAGE);
        if (page > 0) {
            UIElement previous = element("membership-previous", Material.ARROW, ChatColor.YELLOW + "Previous Page");
            previous.onLeftClick(clicked -> controller.refreshDetails(
                    window.getViewer(),
                    state.requestId(),
                    workcell.stableId(),
                    variant.pieceKey(),
                    page - 1));
            window.setElement(-1, 5, previous);
        }
        UIElement indicator = element("membership-page", Material.BOOK, ChatColor.WHITE + "Page "
                + (page + 1) + "/" + pageCount);
        indicator.addLore(ChatColor.GRAY + "" + variant.memberships().size() + " exact pool entries");
        window.setElement(0, 5, indicator);
        if (page + 1 < pageCount) {
            UIElement next = element("membership-next", Material.ARROW, ChatColor.YELLOW + "Next Page");
            next.onLeftClick(clicked -> controller.refreshDetails(
                    window.getViewer(),
                    state.requestId(),
                    workcell.stableId(),
                    variant.pieceKey(),
                    page + 1));
            window.setElement(1, 5, next);
        }
        window.setElement(2, 5, evaluationElement(state.evaluation()));

        UIElement toolbox = element("details-toolbox", Material.STICK, ChatColor.AQUA + "Toolbox");
        toolbox.onLeftClick(clicked -> controller.openToolbox(
                window.getViewer(), state.requestId(), workcell.stableId(), 0));
        window.setElement(4, 5, toolbox);
    }
}
