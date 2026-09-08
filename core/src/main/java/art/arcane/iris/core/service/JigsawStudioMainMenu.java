package art.arcane.iris.core.service;

import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.List;
import java.util.Objects;

import static art.arcane.iris.core.service.JigsawStudioMenuController.GRID_POSITIONS;
import static art.arcane.iris.core.service.JigsawStudioMenuController.VARIANTS_PER_PAGE;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.clampPage;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.compatibilityName;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.dimensions;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.element;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.evaluationElement;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.nextThemeSetKey;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.page;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.pageCount;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.safe;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.themes;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.variantMaterial;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.workcellMaterial;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.workcellStatus;
import static art.arcane.iris.core.service.JigsawStudioMenuFormat.yesNo;

final class JigsawStudioMainMenu {
    private final JigsawStudioMenuController controller;

    JigsawStudioMainMenu(JigsawStudioMenuController controller) {
        this.controller = Objects.requireNonNull(controller, "Jigsaw Studio menu controller");
    }

    void render(
            UIWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell selected,
            int requestedPage
    ) {
        int page = clampPage(requestedPage, selected.variants().size(), VARIANTS_PER_PAGE);
        window.batch(() -> {
            window.clearElements();
            addWorkcells(window, state, selected.stableId());
            addFamilyControl(window, state);
            addStructureControl(window, state, selected.stableId());
            addVariants(window, state, selected, page);
            addMainActions(window, state, selected, page);
        });
    }

    private void addFamilyControl(UIWindow window, JigsawStudioMenuState state) {
        String nextThemeKey = nextThemeSetKey(state.themeSets());
        boolean available = state.irisExtended() && state.workcells().stream()
                .filter(JigsawStudioMenuState.Workcell::enabled)
                .allMatch(workcell -> workcell.activeVariant() != null && workcell.activeVariant().owned());
        UIElement family = element(
                "duplicate-family",
                available ? Material.PURPLE_DYE : Material.GRAY_DYE,
                available
                        ? ChatColor.LIGHT_PURPLE + "Duplicate All Enabled Cells as Family"
                        : ChatColor.GRAY + "Duplicate All Enabled Cells as Family");
        family.addLore(ChatColor.GRAY + "Copies each enabled cell's loaded variant into one coherent family.");
        family.addLore(ChatColor.GRAY + "New family: " + safe(nextThemeKey));
        if (available) {
            family.addLore(ChatColor.YELLOW + "Left-click to duplicate the complete family atomically");
            family.onLeftClick(clicked -> controller.duplicateActiveFamily(
                    window.getViewer(), state.requestId(), nextThemeKey));
        } else if (!state.irisExtended()) {
            family.addLore(ChatColor.GRAY + "Coherent families require Iris compatibility.");
        } else {
            family.addLore(ChatColor.GRAY + "Load an owned variant in every enabled workcell first.");
        }
        window.setElement(3, 0, family);
    }

    private void addStructureControl(
            UIWindow window,
            JigsawStudioMenuState state,
            String selectedWorkcellId
    ) {
        UIElement structure = element(
                "structure-rules",
                state.irisExtended()
                        ? state.requireCaps() ? Material.IRON_BARS : Material.TRIPWIRE_HOOK
                        : Material.BARRIER,
                ChatColor.LIGHT_PURPLE + "Structure Rules");
        structure.addLore(ChatColor.GRAY + "Mandatory caps: " + yesNo(state.requireCaps()));
        structure.addLore(ChatColor.GRAY + "Theme sets: " + state.themeSets().size());
        structure.addLore(ChatColor.GRAY + "Compatibility: " + compatibilityName(state.compatibilityTarget()));
        structure.addLore(ChatColor.YELLOW + "Left-click for themes and rules");
        structure.addLore(state.irisExtended()
                ? ChatColor.YELLOW + "Right-click to toggle mandatory caps"
                : ChatColor.GRAY + "Mandatory caps and themes require Iris compatibility");
        structure.onLeftClick(clicked -> controller.openStructureSettings(
                window.getViewer(), state.requestId(), selectedWorkcellId, 0));
        if (state.irisExtended()) {
            structure.onRightClick(clicked -> controller.setRequireCaps(
                    window.getViewer(), state.requestId(), !state.requireCaps()));
        }
        window.setElement(4, 0, structure);
    }

    private void addWorkcells(UIWindow window, JigsawStudioMenuState state, String selectedWorkcellId) {
        int count = state.workcells().size();
        int start = count == 1 ? 0 : -(count / 2);
        for (int index = 0; index < count; index++) {
            JigsawStudioMenuState.Workcell workcell = state.workcells().get(index);
            boolean selected = workcell.stableId().equals(selectedWorkcellId);
            UIElement element = element(
                    "workcell-" + index,
                    workcellMaterial(workcell, selected),
                    (selected ? ChatColor.AQUA : workcell.enabled() ? ChatColor.WHITE : ChatColor.GRAY)
                            + safe(workcell.displayName()))
                    .setEnchanted(selected);
            element.addLore(workcell.enabled()
                    ? ChatColor.GREEN + "Enabled"
                    : ChatColor.RED + "Disabled for assembly and export");
            if (!workcell.canonicalName().equals(workcell.displayName())) {
                element.addLore(ChatColor.GRAY + "Solver role: " + safe(workcell.canonicalName()));
            }
            element.addLore(ChatColor.GRAY + "Capacity: " + dimensions(workcell.capacity()));
            element.addLore(ChatColor.GRAY + "Variants: " + workcell.variants().size());
            JigsawStudioMenuState.Variant active = workcell.activeVariant();
            element.addLore(ChatColor.GRAY + "Loaded: "
                    + (active == null ? "None" : safe(active.displayName())));
            element.addLore(workcellStatus(workcell));
            element.addLore(ChatColor.GRAY + "Connector blocks: "
                    + (workcell.connectorsVisible() ? "Visible" : "Hidden"));
            element.addLore(ChatColor.YELLOW + "Left-click to select and teleport");
            element.addLore(ChatColor.YELLOW + "Right-click for workcell settings");
            if (workcell.dirty() && !workcell.saving()) {
                element.addLore(ChatColor.GOLD + "Shift-left: Flush Autosave Now");
                element.onShiftLeftClick(clicked -> controller.flushNow(
                        window.getViewer(), state.requestId(), workcell.stableId()));
            }
            element.onLeftClick(clicked -> controller.selectWorkcell(
                    window.getViewer(),
                    state.requestId(),
                    workcell.stableId()));
            element.onRightClick(clicked -> J.runEntity(
                    window.getViewer(),
                    () -> controller.openWorkcellSettings(
                            window.getViewer(),
                            state.requestId(),
                            workcell.stableId()),
                    1));
            window.setElement(start + index, 0, element);
        }
    }

    private void addVariants(
            UIWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            int page
    ) {
        List<JigsawStudioMenuState.Variant> variants = page(
                workcell.variants(), page, VARIANTS_PER_PAGE);
        if (variants.isEmpty()) {
            UIElement empty = element("no-variants", Material.BARRIER, ChatColor.RED + "No variants");
            empty.addLore(ChatColor.GRAY + "Create a variant to begin authoring this workcell.");
            window.setElement(0, 2, empty);
            return;
        }

        for (int index = 0; index < variants.size(); index++) {
            JigsawStudioMenuState.Variant variant = variants.get(index);
            int position = GRID_POSITIONS[index % GRID_POSITIONS.length];
            int row = 1 + index / GRID_POSITIONS.length;
            UIElement element = element(
                    "variant-" + index,
                    variantMaterial(variant),
                    (variant.active() ? ChatColor.GREEN : ChatColor.WHITE)
                            + safe(variant.displayName())
                            + (variant.active() ? ChatColor.AQUA + " [Loaded]" : ""))
                    .setEnchanted(variant.active());
            element.addLore(ChatColor.DARK_GRAY + safe(variant.pieceKey()));
            element.addLore(variant.owned()
                    ? ChatColor.GREEN + "Owned"
                    : ChatColor.GRAY + "Read-only");
            element.addLore(ChatColor.GRAY + "Rotation: " + (variant.rotatable() ? "Enabled" : "Disabled"));
            element.addLore(ChatColor.GRAY + "Size: " + variant.dimensions()
                    .map(JigsawStudioMenuFormat::dimensions)
                    .orElse("Object missing"));
            element.addLore(ChatColor.GRAY + "Themes: " + themes(variant.themes()));
            element.addLore(ChatColor.GRAY + "Pool entries: " + variant.memberships().size());
            element.addLore(variant.active()
                    ? ChatColor.YELLOW + "Loaded; right-click for details"
                    : ChatColor.YELLOW + "Left-click to load");
            if (!variant.active()) {
                element.addLore(ChatColor.YELLOW + "Right-click for details or deletion");
            }
            element.onLeftClick(clicked -> controller.switchVariant(
                    window.getViewer(),
                    state.requestId(),
                    workcell.stableId(),
                    variant.pieceKey()));
            element.onRightClick(clicked -> J.runEntity(
                    window.getViewer(),
                    () -> controller.openDetails(
                            window.getViewer(),
                            state.requestId(),
                            workcell.stableId(),
                            variant.pieceKey(),
                            0),
                    1));
            window.setElement(position, row, element);
        }
    }

    private void addMainActions(
            UIWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            int page
    ) {
        UIElement create = element("create", Material.NETHER_STAR, ChatColor.GREEN + "New Blank Variant");
        create.addLore(ChatColor.GRAY + "Create an empty owned variant with this cell's current size.");
        create.onLeftClick(clicked -> controller.createVariant(
                window.getViewer(), state.requestId(), workcell.stableId(), false));
        window.setElement(-4, 5, create);

        JigsawStudioMenuState.Variant active = workcell.activeVariant();
        boolean duplicateAvailable = active != null && active.owned();
        UIElement duplicate = element(
                "duplicate",
                duplicateAvailable ? Material.PAPER : Material.GRAY_DYE,
                duplicateAvailable
                        ? ChatColor.AQUA + "Duplicate This Cell's Variant"
                        : active == null
                        ? ChatColor.GRAY + "Duplicate This Cell's Variant"
                        : ChatColor.GRAY + "Duplicate This Cell's Variant");
        duplicate.addLore(duplicateAvailable
                ? ChatColor.GRAY + "Clone the loaded variant into an owned variant."
                : active == null
                        ? ChatColor.GRAY + "Load a variant before duplicating it."
                        : ChatColor.GRAY + "Adopt the read-only graph before duplicating it.");
        if (duplicateAvailable) {
            duplicate.onLeftClick(clicked -> controller.createVariant(
                    window.getViewer(), state.requestId(), workcell.stableId(), true));
        }
        window.setElement(-3, 5, duplicate);

        UIElement settings = element("settings", Material.CRAFTING_TABLE, ChatColor.GOLD + "Workcell Settings");
        settings.addLore(ChatColor.GRAY + "Enabled: " + yesNo(workcell.enabled()));
        settings.addLore(ChatColor.GRAY + "Capacity: " + dimensions(workcell.capacity()));
        settings.onLeftClick(clicked -> controller.openWorkcellSettings(
                window.getViewer(), state.requestId(), workcell.stableId()));
        window.setElement(-2, 5, settings);

        int pageCount = pageCount(workcell.variants().size(), VARIANTS_PER_PAGE);
        if (page > 0) {
            UIElement previous = element("previous", Material.ARROW, ChatColor.YELLOW + "Previous Page");
            previous.onLeftClick(clicked -> controller.refreshMain(
                    window.getViewer(), state.requestId(), workcell.stableId(), page - 1));
            window.setElement(-1, 5, previous);
        }

        UIElement pageIndicator = element("page", Material.BOOK, ChatColor.WHITE + "Page "
                + (page + 1) + "/" + pageCount);
        pageIndicator.addLore(ChatColor.GRAY + "" + workcell.variants().size() + " variants");
        window.setElement(0, 5, pageIndicator);

        if (page + 1 < pageCount) {
            UIElement next = element("next", Material.ARROW, ChatColor.YELLOW + "Next Page");
            next.onLeftClick(clicked -> controller.refreshMain(
                    window.getViewer(), state.requestId(), workcell.stableId(), page + 1));
            window.setElement(1, 5, next);
        }

        window.setElement(2, 5, evaluationElement(state.evaluation()));

        UIElement preview = element("preview", Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "Go to Preview");
        preview.addLore(ChatColor.GRAY + "Open the deterministic assembly preview.");
        preview.onLeftClick(clicked -> controller.goToPreview(window.getViewer(), state.requestId()));
        window.setElement(3, 5, preview);

        UIElement toolbox = element("toolbox", Material.STICK, ChatColor.AQUA + "Toolbox");
        toolbox.addLore(ChatColor.GRAY + "Take named sticks bound to this Studio session.");
        toolbox.onLeftClick(clicked -> controller.openToolbox(
                window.getViewer(), state.requestId(), workcell.stableId(), 0));
        window.setElement(4, 5, toolbox);
    }
}
