package art.arcane.iris.core.service;

import art.arcane.iris.core.service.JigsawStudioMenuFormat.RuleField;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static art.arcane.iris.core.service.JigsawStudioMenuController.GRID_POSITIONS;
import static art.arcane.iris.core.service.JigsawStudioMenuController.THEME_SETS_PER_PAGE;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.clampPage;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.element;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.evaluationElement;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.page;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.pageCount;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.safe;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.themes;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.withTerminal;

final class JigsawStudioVariantMenu {
    private final JigsawStudioMenuController controller;

    JigsawStudioVariantMenu(JigsawStudioMenuController controller) {
        this.controller = Objects.requireNonNull(controller, "Jigsaw Studio menu controller");
    }

    void render(
            UIWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            int requestedPage
    ) {
        int page = clampPage(requestedPage, state.themeSets().size(), THEME_SETS_PER_PAGE);
        window.batch(() -> {
            window.clearElements();

            UIElement back = element("variant-settings-back", Material.ARROW, ChatColor.YELLOW + "Back to Details");
            back.onLeftClick(clicked -> controller.openDetails(
                    window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), 0));
            window.setElement(-4, 0, back);

            UIElement identity = element(
                    "variant-settings-identity",
                    Material.COMPARATOR,
                    ChatColor.GOLD + safe(variant.displayName()) + " Rules");
            identity.addLore(ChatColor.DARK_GRAY + safe(variant.pieceKey()));
            identity.addLore(ChatColor.GRAY + "Theme memberships: " + themes(variant.themes()));
            identity.addLore(ChatColor.GRAY + "Use Duplicate All Enabled Cells as Family for coherent sets.");
            window.setElement(0, 0, identity);
            window.setElement(4, 0, evaluationElement(state.evaluation()));

            if (!variant.active() || !variant.owned() || !state.irisExtended()) {
                UIElement unavailable = element(
                        "variant-settings-unavailable",
                        Material.BARRIER,
                        state.irisExtended()
                                ? ChatColor.RED + "Load an Owned Variant First"
                                : ChatColor.GRAY + "Iris Metadata Unavailable");
                unavailable.addLore(state.irisExtended()
                        ? ChatColor.GRAY + "Rules and themes can only change for the loaded owned variant."
                        : ChatColor.GRAY + "Vanilla-portable pieces use vanilla placement behavior.");
                window.setElement(0, 2, unavailable);
            } else {
                window.setElement(-4, 1, ruleElement(
                        window, state, workcell, variant, RuleField.MINIMUM_DEPTH));
                window.setElement(-2, 1, ruleElement(
                        window, state, workcell, variant, RuleField.MAXIMUM_DEPTH));
                window.setElement(0, 1, ruleElement(
                        window, state, workcell, variant, RuleField.MINIMUM_PLACEMENTS));
                window.setElement(2, 1, ruleElement(
                        window, state, workcell, variant, RuleField.MAXIMUM_PLACEMENTS));

                UIElement terminal = element(
                        "rule-terminal",
                        variant.rules().terminal() ? Material.REDSTONE_TORCH : Material.LEVER,
                        variant.rules().terminal()
                                ? ChatColor.GREEN + "Terminal Piece: Yes"
                                : ChatColor.YELLOW + "Terminal Piece: No");
                terminal.addLore(ChatColor.GRAY + "Terminal pieces consume a connector without expanding.");
                terminal.addLore(ChatColor.YELLOW + "Left-click to toggle");
                terminal.onLeftClick(clicked -> controller.updateVariantRules(
                        window.getViewer(),
                        state.requestId(),
                        workcell.stableId(),
                        variant.pieceKey(),
                        withTerminal(variant.rules(), !variant.rules().terminal())));
                window.setElement(4, 1, terminal);

                addVariantThemeMemberships(window, state, workcell, variant, page);
            }

            UIElement footerBack = element("variant-rules-footer-back", Material.ARROW, ChatColor.YELLOW + "Back");
            footerBack.onLeftClick(clicked -> controller.openDetails(
                    window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), 0));
            window.setElement(-4, 5, footerBack);

            int pageCount = pageCount(state.themeSets().size(), THEME_SETS_PER_PAGE);
            if (page > 0) {
                UIElement previous = element(
                        "variant-theme-previous",
                        Material.ARROW,
                        ChatColor.YELLOW + "Previous Page");
                previous.onLeftClick(clicked -> controller.refreshVariantSettings(
                        window.getViewer(),
                        state.requestId(),
                        workcell.stableId(),
                        variant.pieceKey(),
                        page - 1));
                window.setElement(-1, 5, previous);
            }
            UIElement indicator = element(
                    "variant-theme-page",
                    Material.BOOK,
                    ChatColor.WHITE + "Theme Page " + (page + 1) + "/" + pageCount);
            indicator.addLore(ChatColor.GRAY + "" + state.themeSets().size() + " declared themes");
            window.setElement(0, 5, indicator);
            if (page + 1 < pageCount) {
                UIElement next = element(
                        "variant-theme-next",
                        Material.ARROW,
                        ChatColor.YELLOW + "Next Page");
                next.onLeftClick(clicked -> controller.refreshVariantSettings(
                        window.getViewer(),
                        state.requestId(),
                        workcell.stableId(),
                        variant.pieceKey(),
                        page + 1));
                window.setElement(1, 5, next);
            }
            window.setElement(4, 5, evaluationElement(state.evaluation()));
        });
    }

    private UIElement ruleElement(
            UIWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            RuleField field
    ) {
        int value = field.value(variant.rules());
        UIElement element = element(
                "rule-" + field.name().toLowerCase(Locale.ROOT),
                field.material(),
                ChatColor.AQUA + field.displayName() + ": " + field.displayValue(value));
        boolean unlimitedMaximum = field == RuleField.MAXIMUM_PLACEMENTS && value == 0;
        if (unlimitedMaximum) {
            element.addLore(ChatColor.GRAY + "Increase is already unlimited.");
        } else {
            element.addLore(ChatColor.GREEN + "Left-click: +1");
            element.addLore(ChatColor.GREEN + "Shift-left: +" + field.shiftStep());
            element.onLeftClick(clicked -> controller.adjustVariantRule(
                    window.getViewer(), state.requestId(), workcell, variant, field, 1));
            element.onShiftLeftClick(clicked -> controller.adjustVariantRule(
                    window.getViewer(), state.requestId(), workcell, variant, field, field.shiftStep()));
        }
        element.addLore(unlimitedMaximum
                ? ChatColor.YELLOW + "Right-click: set 512"
                : ChatColor.YELLOW + "Right-click: -1");
        element.addLore(unlimitedMaximum
                ? ChatColor.YELLOW + "Shift-right: set 497"
                : ChatColor.YELLOW + "Shift-right: -" + field.shiftStep());
        element.onRightClick(clicked -> controller.adjustVariantRule(
                window.getViewer(), state.requestId(), workcell, variant, field, -1));
        element.onShiftRightClick(clicked -> controller.adjustVariantRule(
                window.getViewer(), state.requestId(), workcell, variant, field, -field.shiftStep()));
        return element;
    }

    private void addVariantThemeMemberships(
            UIWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            int page
    ) {
        List<JigsawStudioMenuState.ThemeSet> themeSets = page(
                state.themeSets(), page, THEME_SETS_PER_PAGE);
        if (themeSets.isEmpty()) {
            UIElement empty = element(
                    "variant-no-themes",
                    Material.GRAY_DYE,
                    ChatColor.GRAY + "Available to Every Theme");
            empty.addLore(ChatColor.GRAY + "Create a coherent theme set from Structure Rules first.");
            window.setElement(0, 3, empty);
            return;
        }
        for (int index = 0; index < themeSets.size(); index++) {
            JigsawStudioMenuState.ThemeSet themeSet = themeSets.get(index);
            boolean member = variant.themes().contains(themeSet.key());
            int position = GRID_POSITIONS[index % GRID_POSITIONS.length];
            int row = 2 + index / GRID_POSITIONS.length;
            UIElement element = element(
                    "variant-theme-" + index,
                    member ? Material.LIME_DYE : Material.GRAY_DYE,
                    (member ? ChatColor.GREEN : ChatColor.GRAY) + safe(themeSet.key()))
                    .setEnchanted(member);
            element.addLore(member
                    ? ChatColor.GREEN + "This variant belongs to the theme."
                    : ChatColor.GRAY + "This variant does not belong to the theme.");
            element.addLore(ChatColor.GRAY + "Only variants in the selected family are eligible.");
            element.addLore(ChatColor.YELLOW + "Left-click to toggle membership");
            element.onLeftClick(clicked -> controller.toggleVariantTheme(
                    window.getViewer(), state.requestId(), workcell, variant, themeSet.key()));
            window.setElement(position, row, element);
        }
    }
}
