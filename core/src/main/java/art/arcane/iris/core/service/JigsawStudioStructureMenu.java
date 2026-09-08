package art.arcane.iris.core.service;

import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.List;
import java.util.Objects;

import static art.arcane.iris.core.service.JigsawStudioMenuController.GRID_POSITIONS;
import static art.arcane.iris.core.service.JigsawStudioMenuController.THEME_SETS_PER_PAGE;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.clampPage;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.compatibilityName;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.element;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.evaluationElement;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.nextThemeSetKey;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.page;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.pageCount;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.safe;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.themeSelectionPercent;

final class JigsawStudioStructureMenu {
    private final JigsawStudioMenuController controller;

    JigsawStudioStructureMenu(JigsawStudioMenuController controller) {
        this.controller = Objects.requireNonNull(controller, "Jigsaw Studio menu controller");
    }

    void render(
            UIWindow window,
            JigsawStudioMenuState state,
            String selectedWorkcellId,
            int requestedPage
    ) {
        int page = clampPage(requestedPage, state.themeSets().size(), THEME_SETS_PER_PAGE);
        controller.purgeExpiredConfirmations(window.getViewer().getUniqueId());
        window.batch(() -> {
            window.clearElements();

            UIElement back = element("structure-back", Material.ARROW, ChatColor.YELLOW + "Back to Variants");
            back.onLeftClick(clicked -> controller.refreshMain(
                    window.getViewer(), state.requestId(), selectedWorkcellId, 0));
            window.setElement(-4, 0, back);

            UIElement identity = element(
                    "structure-identity",
                    Material.JIGSAW,
                    ChatColor.LIGHT_PURPLE + "Structure Themes & Caps");
            identity.addLore(ChatColor.DARK_GRAY + safe(state.structureKey()));
            identity.addLore(ChatColor.GRAY + "Compatibility: " + compatibilityName(state.compatibilityTarget()));
            identity.addLore(ChatColor.GRAY + "Theme sets select one coherent variant family per assembly.");
            identity.addLore(ChatColor.GRAY + "Empty piece themes remain available to every family.");
            window.setElement(0, 0, identity);
            window.setElement(4, 0, evaluationElement(state.evaluation()));

            UIElement requireCaps = element(
                    "require-caps",
                    state.irisExtended()
                            ? state.requireCaps() ? Material.IRON_BARS : Material.TRIPWIRE_HOOK
                            : Material.BARRIER,
                    !state.irisExtended()
                            ? ChatColor.GRAY + "Mandatory Caps Require Iris Compatibility"
                            : state.requireCaps()
                            ? ChatColor.GREEN + "Mandatory Caps Enabled"
                            : ChatColor.YELLOW + "Mandatory Caps Disabled");
            requireCaps.addLore(!state.irisExtended()
                    ? ChatColor.GRAY + "Vanilla-portable export rejects Iris mandatory-cap metadata."
                    : state.requireCaps()
                    ? ChatColor.GRAY + "Every open connector must close with a terminal piece."
                    : ChatColor.GRAY + "Assembly may leave an open connector when no cap can fit.");
            if (state.irisExtended()) {
                requireCaps.addLore(ChatColor.YELLOW + "Left-click to toggle");
                requireCaps.onLeftClick(clicked -> controller.setRequireCaps(
                        window.getViewer(), state.requestId(), !state.requireCaps()));
            }
            window.setElement(-3, 1, requireCaps);

            String nextThemeKey = nextThemeSetKey(state.themeSets());
            boolean themeEditingAvailable = state.irisExtended();
            UIElement createTheme = element(
                    "new-theme-set",
                    themeEditingAvailable ? Material.NETHER_STAR : Material.BARRIER,
                    themeEditingAvailable
                            ? ChatColor.GREEN + "Duplicate All Enabled Cells as Family: " + safe(nextThemeKey)
                            : ChatColor.GRAY + "Theme Sets Require Iris Compatibility");
            if (themeEditingAvailable) {
                createTheme.addLore(ChatColor.GRAY + "Creates one new owned variant for every enabled workcell.");
                createTheme.addLore(ChatColor.GRAY + "All created variants join " + safe(nextThemeKey) + ".");
                createTheme.addLore(ChatColor.GRAY + "One family is selected for the complete assembly.");
                createTheme.addLore(ChatColor.YELLOW + "Left-click to create the complete set");
                createTheme.onLeftClick(clicked -> controller.duplicateActiveFamily(
                        window.getViewer(), state.requestId(), nextThemeKey));
            } else {
                createTheme.addLore(ChatColor.GRAY + "Vanilla jigsaw resources cannot encode Iris theme metadata.");
            }
            window.setElement(0, 1, createTheme);

            boolean deleteConfirmed = controller.isProjectDeleteConfirmed(
                    window.getViewer().getUniqueId(), state.requestId());
            UIElement deleteProject = element(
                    "delete-project",
                    deleteConfirmed ? Material.REDSTONE : Material.LAVA_BUCKET,
                    deleteConfirmed
                            ? ChatColor.RED + "Confirm Delete Jigsaw"
                            : ChatColor.RED + "Delete Jigsaw")
                    .setEnchanted(deleteConfirmed);
            deleteProject.addLore(deleteConfirmed
                    ? ChatColor.RED + "Click again to delete this entire managed project."
                    : ChatColor.RED + "Click twice within 10 seconds to confirm.");
            deleteProject.onLeftClick(clicked -> controller.confirmOrDeleteProject(
                    window.getViewer(), state.requestId(), selectedWorkcellId, page));
            window.setElement(3, 1, deleteProject);

            addThemeSets(window, state, page);

            UIElement footerBack = element("structure-footer-back", Material.ARROW, ChatColor.YELLOW + "Back");
            footerBack.onLeftClick(clicked -> controller.refreshMain(
                    window.getViewer(), state.requestId(), selectedWorkcellId, 0));
            window.setElement(-4, 5, footerBack);

            int pageCount = pageCount(state.themeSets().size(), THEME_SETS_PER_PAGE);
            if (page > 0) {
                UIElement previous = element(
                        "theme-previous",
                        Material.ARROW,
                        ChatColor.YELLOW + "Previous Page");
                previous.onLeftClick(clicked -> controller.refreshStructureSettings(
                        window.getViewer(), state.requestId(), selectedWorkcellId, page - 1));
                window.setElement(-1, 5, previous);
            }
            UIElement indicator = element(
                    "theme-page",
                    Material.BOOK,
                    ChatColor.WHITE + "Page " + (page + 1) + "/" + pageCount);
            indicator.addLore(ChatColor.GRAY + "" + state.themeSets().size() + " theme sets");
            window.setElement(0, 5, indicator);
            if (page + 1 < pageCount) {
                UIElement next = element("theme-next", Material.ARROW, ChatColor.YELLOW + "Next Page");
                next.onLeftClick(clicked -> controller.refreshStructureSettings(
                        window.getViewer(), state.requestId(), selectedWorkcellId, page + 1));
                window.setElement(1, 5, next);
            }
            window.setElement(4, 5, evaluationElement(state.evaluation()));
        });
    }

    private void addThemeSets(
            UIWindow window,
            JigsawStudioMenuState state,
            int page
    ) {
        List<JigsawStudioMenuState.ThemeSet> themeSets = page(
                state.themeSets(), page, THEME_SETS_PER_PAGE);
        if (themeSets.isEmpty()) {
            UIElement empty = element(
                    "no-theme-sets",
                    Material.GRAY_DYE,
                    ChatColor.GRAY + "Implicit Unthemed Assembly");
            empty.addLore(ChatColor.GRAY + "Create variant-1 to begin coherent theme selection.");
            window.setElement(0, 3, empty);
            return;
        }
        for (int index = 0; index < themeSets.size(); index++) {
            JigsawStudioMenuState.ThemeSet themeSet = themeSets.get(index);
            int position = GRID_POSITIONS[index % GRID_POSITIONS.length];
            int row = 2 + index / GRID_POSITIONS.length;
            UIElement element = element(
                    "theme-set-" + index,
                    state.irisExtended() ? Material.PURPLE_DYE : Material.GRAY_DYE,
                    (state.irisExtended() ? ChatColor.LIGHT_PURPLE : ChatColor.GRAY) + safe(themeSet.key()));
            element.addLore(ChatColor.WHITE + "Selection weight: " + themeSet.weight());
            element.addLore(ChatColor.WHITE + "Whole-assembly chance: "
                    + themeSelectionPercent(state.themeSets(), themeSet));
            if (state.irisExtended()) {
                element.addLore(ChatColor.GREEN + "Left-click: weight +1");
                element.addLore(ChatColor.YELLOW + "Right-click: weight -1");
                element.addLore(ChatColor.GREEN + "Shift-left: weight +8");
                element.addLore(ChatColor.YELLOW + "Shift-right: weight -8");
                element.onLeftClick(clicked -> controller.adjustThemeSetWeight(
                        window.getViewer(), state.requestId(), themeSet, 1));
                element.onRightClick(clicked -> controller.adjustThemeSetWeight(
                        window.getViewer(), state.requestId(), themeSet, -1));
                element.onShiftLeftClick(clicked -> controller.adjustThemeSetWeight(
                        window.getViewer(), state.requestId(), themeSet, 8));
                element.onShiftRightClick(clicked -> controller.adjustThemeSetWeight(
                        window.getViewer(), state.requestId(), themeSet, -8));
            } else {
                element.addLore(ChatColor.GRAY + "Theme metadata is unavailable for vanilla-portable graphs.");
            }
            window.setElement(position, row, element);
        }
    }
}
