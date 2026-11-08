package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPieceRules;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolAction;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolPayload;
import com.volmit.tooling.world.ToolPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class JigsawStudioMenuController {
    static final int VARIANTS_PER_PAGE = 28;
    static final int MEMBERSHIPS_PER_PAGE = 28;
    static final int THEME_SETS_PER_PAGE = 21;
    static final int TOOLS_PER_PAGE = 28;
    static final int CHANCE_STEP_PERCENTAGE_POINTS = 5;
    static final int RULE_SHIFT_STEP = 5;
    static final int PLACEMENT_RULE_SHIFT_STEP = 16;

    private static final long DESTRUCTIVE_CONFIRM_NANOS = 10_000_000_000L;
    private static final int WORKCELL_RESIZE_REFRESH_TICKS = 2;
    private static final int WORKCELL_RESIZE_REFRESH_ATTEMPTS = 200;
    private static final int[] GRID_POSITIONS = {-3, -2, -1, 0, 1, 2, 3};
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)\\u00a7[0-9A-FK-ORX]");
    private static final int CLOSE_AFTER_ACTION_TICKS = 1;
    private static final int RIGHT_CLICK_OPEN_DELAY_TICKS = 1;
    private static final int DETAILS_REFRESH_DELAY_TICKS = 1;

    private final Actions actions;
    private final JigsawStudioScheduler scheduler;
    private final Map<UUID, JigsawStudioMenuWindow> windows = new ConcurrentHashMap<>();
    private final Map<UUID, PendingUnlink> pendingUnlinks = new ConcurrentHashMap<>();
    private final Map<UUID, PendingDelete> pendingDeletes = new ConcurrentHashMap<>();
    private final Map<UUID, PendingProjectDelete> pendingProjectDeletes = new ConcurrentHashMap<>();
    private final Map<UUID, PendingWorkcellResize> pendingWorkcellResizes = new ConcurrentHashMap<>();

    public JigsawStudioMenuController(Actions actions, JigsawStudioScheduler scheduler) {
        this.actions = Objects.requireNonNull(actions, "Jigsaw Studio menu actions");
        this.scheduler = Objects.requireNonNull(scheduler, "Jigsaw Studio menu scheduler");
    }

    public boolean open(ToolPlayer player) {
        if (player == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> open(player));
        }

        Optional<JigsawStudioMenuState> available = actions.menuState(player);
        if (available.isEmpty()) {
            close(player);
            sendError(player, "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        JigsawStudioMenuState state = available.get();
        if (state.workcells().isEmpty()) {
            close(player);
            sendError(player, "This Jigsaw Studio has no workcells.");
            return false;
        }

        JigsawStudioMenuState.Workcell selected = state.selectedWorkcell();
        if (selected == null) {
            String firstWorkcellId = state.workcells().getFirst().stableId();
            if (!actions.selectWorkcell(player, firstWorkcellId)) {
                return false;
            }
            Optional<JigsawStudioMenuState> selectedState = matchingState(player, state.requestId(), false);
            if (selectedState.isEmpty()) {
                return false;
            }
            state = selectedState.get();
            selected = state.selectedWorkcell();
            if (selected == null) {
                return false;
            }
        }

        close(player);
        UUID playerId = player.id();
        JigsawStudioMenuWindow window = new JigsawStudioMenuWindow(player);
        window.setViewportHeight(6);
        window.setTitle(title(state.structureKey()));
        window.onClosed(() -> {
            windows.remove(playerId, window);
            pendingUnlinks.remove(playerId);
            pendingDeletes.remove(playerId);
            pendingProjectDeletes.remove(playerId);
            pendingWorkcellResizes.remove(playerId);
        });
        windows.put(playerId, window);
        pendingUnlinks.remove(playerId);
        pendingDeletes.remove(playerId);
        pendingProjectDeletes.remove(playerId);
        pendingWorkcellResizes.remove(playerId);
        renderMain(window, state, selected, 0);
        window.open();
        return true;
    }

    public boolean openWorkcellSettings(ToolPlayer player, String workcellId) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> openWorkcellSettings(player, workcellId));
        }
        if (!open(player)) {
            return false;
        }
        Optional<JigsawStudioMenuState> current = actions.menuState(player);
        if (current.isEmpty() || current.get().workcell(workcellId) == null) {
            stale(player);
            return false;
        }
        openWorkcellSettings(player, current.get().requestId(), workcellId);
        return true;
    }

    public boolean openVariantSettings(ToolPlayer player, String workcellId, String pieceKey) {
        if (player == null
                || workcellId == null
                || workcellId.isBlank()
                || pieceKey == null
                || pieceKey.isBlank()) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> openVariantSettings(player, workcellId, pieceKey));
        }
        if (!open(player)) {
            return false;
        }
        Optional<JigsawStudioMenuState> current = actions.menuState(player);
        if (current.isEmpty()) {
            stale(player);
            return false;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        JigsawStudioMenuState.Variant variant = variant(workcell, pieceKey);
        if (variant == null || !variant.active() || !variant.owned()) {
            stale(player);
            return false;
        }
        if (!current.get().irisExtended()) {
            send(player, "Vanilla-portable pieces cannot encode Iris theme or piece-rule metadata.");
            close(player);
            return false;
        }
        openVariantSettings(player, current.get().requestId(), workcellId, pieceKey, 0);
        return true;
    }

    public boolean openVariantSizeSettings(ToolPlayer player, String workcellId, String pieceKey) {
        if (player == null || workcellId == null || workcellId.isBlank()
                || pieceKey == null || pieceKey.isBlank()) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> openVariantSizeSettings(player, workcellId, pieceKey));
        }
        if (!open(player)) {
            return false;
        }
        Optional<JigsawStudioMenuState> current = actions.menuState(player);
        if (current.isEmpty()) {
            stale(player);
            return false;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        JigsawStudioMenuState.Variant variant = variant(workcell, pieceKey);
        if (workcell == null || variant == null || !variant.owned() || variant.dimensions().isEmpty()) {
            stale(player);
            return false;
        }
        JigsawStudioMenuWindow window = windows.get(player.id());
        if (window == null) {
            return false;
        }
        renderVariantSizeSettings(window, current.get(), workcell, variant);
        return true;
    }

    public boolean openStructureSettings(ToolPlayer player) {
        if (player == null) {
            return false;
        }
        if (!JigsawStudioThreads.owns(player)) {
            return JigsawStudioThreads.call(player, () -> openStructureSettings(player));
        }
        if (!open(player)) {
            return false;
        }
        Optional<JigsawStudioMenuState> current = actions.menuState(player);
        if (current.isEmpty() || current.get().selectedWorkcell() == null) {
            stale(player);
            return false;
        }
        openStructureSettings(
                player,
                current.get().requestId(),
                current.get().selectedWorkcellId(),
                0);
        return true;
    }

    public void close(ToolPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.id();
        JigsawStudioMenuWindow window = windows.remove(playerId);
        pendingUnlinks.remove(playerId);
        pendingDeletes.remove(playerId);
        pendingProjectDeletes.remove(playerId);
        if (window == null) {
            return;
        }
        JigsawStudioThreads.run(player, window::close);
    }

    public void closeAll() {
        List<JigsawStudioMenuWindow> activeWindows = new ArrayList<>(windows.values());
        windows.clear();
        pendingUnlinks.clear();
        pendingDeletes.clear();
        pendingProjectDeletes.clear();
        pendingWorkcellResizes.clear();
        for (JigsawStudioMenuWindow window : activeWindows) {
            JigsawStudioThreads.run(window.getViewer(), window::close);
        }
    }

    private void renderMain(
            JigsawStudioMenuWindow window,
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

    private void addFamilyControl(JigsawStudioMenuWindow window, JigsawStudioMenuState state) {
        String nextThemeKey = nextThemeSetKey(state.themeSets());
        boolean available = state.irisExtended() && state.workcells().stream()
                .filter(JigsawStudioMenuState.Workcell::enabled)
                .allMatch(workcell -> workcell.activeVariant() != null && workcell.activeVariant().owned());
        JigsawStudioMenuElement family = element(
                "duplicate-family",
                available ? JigsawStudioMenuIcon.PURPLE_DYE : JigsawStudioMenuIcon.GRAY_DYE,
                available
                        ? "Duplicate All Enabled Cells as Family"
                        : "Duplicate All Enabled Cells as Family");
        family.addLore("Copies each enabled cell's loaded variant into one coherent family.");
        family.addLore("New family: " + safe(nextThemeKey));
        if (available) {
            family.addLore("Left-click to duplicate the complete family atomically");
            family.onLeftClick(clicked -> duplicateActiveFamily(
                    window.getViewer(), state.requestId(), nextThemeKey));
        } else if (!state.irisExtended()) {
            family.addLore("Coherent families require Iris compatibility.");
        } else {
            family.addLore("Load an owned variant in every enabled workcell first.");
        }
        window.setElement(3, 0, family);
    }

    private void addStructureControl(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            String selectedWorkcellId
    ) {
        JigsawStudioMenuElement structure = element(
                "structure-rules",
                state.irisExtended()
                        ? state.requireCaps() ? JigsawStudioMenuIcon.IRON_BARS : JigsawStudioMenuIcon.TRIPWIRE_HOOK
                        : JigsawStudioMenuIcon.BARRIER,
                "Structure Rules");
        structure.addLore("Mandatory caps: " + yesNo(state.requireCaps()));
        structure.addLore("Theme sets: " + state.themeSets().size());
        structure.addLore("Compatibility: " + compatibilityName(state.compatibilityTarget()));
        structure.addLore("Left-click for themes and rules");
        structure.addLore(state.irisExtended()
                ? "Right-click to toggle mandatory caps"
                : "Mandatory caps and themes require Iris compatibility");
        structure.onLeftClick(clicked -> openStructureSettings(
                window.getViewer(), state.requestId(), selectedWorkcellId, 0));
        if (state.irisExtended()) {
            structure.onRightClick(clicked -> setRequireCaps(
                    window.getViewer(), state.requestId(), !state.requireCaps()));
        }
        window.setElement(4, 0, structure);
    }

    private void addWorkcells(JigsawStudioMenuWindow window, JigsawStudioMenuState state, String selectedWorkcellId) {
        int count = state.workcells().size();
        int start = count == 1 ? 0 : -(count / 2);
        for (int index = 0; index < count; index++) {
            JigsawStudioMenuState.Workcell workcell = state.workcells().get(index);
            boolean selected = workcell.stableId().equals(selectedWorkcellId);
            JigsawStudioMenuElement element = element(
                    "workcell-" + index,
                    workcellMaterial(workcell, selected),
                    safe(workcell.displayName()))
                    .setEnchanted(selected);
            element.addLore(workcell.enabled()
                    ? "Enabled"
                    : "Disabled for assembly and export");
            if (!workcell.canonicalName().equals(workcell.displayName())) {
                element.addLore("Solver role: " + safe(workcell.canonicalName()));
            }
            element.addLore("Capacity: " + dimensions(workcell.capacity()));
            element.addLore("Variants: " + workcell.variants().size());
            JigsawStudioMenuState.Variant active = workcell.activeVariant();
            element.addLore("Loaded: "
                    + (active == null ? "None" : safe(active.displayName())));
            element.addLore(workcellStatus(workcell));
            element.addLore("Connector blocks: "
                    + (workcell.connectorsVisible() ? "Visible" : "Hidden"));
            element.addLore("Left-click to select and teleport");
            element.addLore("Right-click for workcell settings");
            if (workcell.dirty() && !workcell.saving()) {
                element.addLore("Shift-left: Flush Autosave Now");
                element.onShiftLeftClick(clicked -> flushNow(
                        window.getViewer(), state.requestId(), workcell.stableId()));
            }
            element.onLeftClick(clicked -> selectWorkcell(
                    window.getViewer(),
                    state.requestId(),
                    workcell.stableId()));
            element.onRightClick(clicked -> scheduler.onPlayerLater(
                    window.getViewer(),
                    () -> openWorkcellSettings(
                            window.getViewer(),
                            state.requestId(),
                            workcell.stableId()),
                    RIGHT_CLICK_OPEN_DELAY_TICKS,
                    () -> { }));
            window.setElement(start + index, 0, element);
        }
    }

    private void addVariants(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            int page
    ) {
        List<JigsawStudioMenuState.Variant> variants = page(
                workcell.variants(), page, VARIANTS_PER_PAGE);
        if (variants.isEmpty()) {
            JigsawStudioMenuElement empty = element("no-variants", JigsawStudioMenuIcon.BARRIER, "No variants");
            empty.addLore("Create a variant to begin authoring this workcell.");
            window.setElement(0, 2, empty);
            return;
        }

        for (int index = 0; index < variants.size(); index++) {
            JigsawStudioMenuState.Variant variant = variants.get(index);
            int position = GRID_POSITIONS[index % GRID_POSITIONS.length];
            int row = 1 + index / GRID_POSITIONS.length;
            JigsawStudioMenuElement element = element(
                    "variant-" + index,
                    variantMaterial(variant),
                    safe(variant.displayName())
                            + (variant.active() ? " [Loaded]" : ""))
                    .setEnchanted(variant.active());
            element.addLore(safe(variant.pieceKey()));
            element.addLore(variant.owned()
                    ? "Owned"
                    : "Read-only");
            element.addLore("Rotation: " + (variant.rotatable() ? "Enabled" : "Disabled"));
            element.addLore("Size: " + variant.dimensions()
                    .map(JigsawStudioMenuController::dimensions)
                    .orElse("Object missing"));
            element.addLore("Themes: " + themes(variant.themes()));
            element.addLore("Pool entries: " + variant.memberships().size());
            element.addLore(variant.active()
                    ? "Loaded; right-click for details"
                    : "Left-click to load");
            if (!variant.active()) {
                element.addLore("Right-click for details or deletion");
            }
            element.onLeftClick(clicked -> switchVariant(
                    window.getViewer(),
                    state.requestId(),
                    workcell.stableId(),
                    variant.pieceKey()));
            element.onRightClick(clicked -> scheduler.onPlayerLater(
                    window.getViewer(),
                    () -> openDetails(
                            window.getViewer(),
                            state.requestId(),
                            workcell.stableId(),
                            variant.pieceKey(),
                            0),
                    RIGHT_CLICK_OPEN_DELAY_TICKS,
                    () -> { }));
            window.setElement(position, row, element);
        }
    }

    private void addMainActions(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            int page
    ) {
        JigsawStudioMenuElement create = element("create", JigsawStudioMenuIcon.NETHER_STAR, "New Blank Variant");
        create.addLore("Create an empty owned variant with this cell's current size.");
        create.onLeftClick(clicked -> createVariant(
                window.getViewer(), state.requestId(), workcell.stableId(), false));
        window.setElement(-4, 5, create);

        JigsawStudioMenuState.Variant active = workcell.activeVariant();
        boolean duplicateAvailable = active != null && active.owned();
        JigsawStudioMenuElement duplicate = element(
                "duplicate",
                duplicateAvailable ? JigsawStudioMenuIcon.PAPER : JigsawStudioMenuIcon.GRAY_DYE,
                duplicateAvailable
                        ? "Duplicate This Cell's Variant"
                        : active == null
                        ? "Duplicate This Cell's Variant"
                        : "Duplicate This Cell's Variant");
        duplicate.addLore(duplicateAvailable
                ? "Clone the loaded variant into an owned variant."
                : active == null
                        ? "Load a variant before duplicating it."
                        : "Adopt the read-only graph before duplicating it.");
        if (duplicateAvailable) {
            duplicate.onLeftClick(clicked -> createVariant(
                    window.getViewer(), state.requestId(), workcell.stableId(), true));
        }
        window.setElement(-3, 5, duplicate);

        JigsawStudioMenuElement settings = element("settings", JigsawStudioMenuIcon.CRAFTING_TABLE, "Workcell Settings");
        settings.addLore("Enabled: " + yesNo(workcell.enabled()));
        settings.addLore("Capacity: " + dimensions(workcell.capacity()));
        settings.onLeftClick(clicked -> openWorkcellSettings(
                window.getViewer(), state.requestId(), workcell.stableId()));
        window.setElement(-2, 5, settings);

        int pageCount = pageCount(workcell.variants().size(), VARIANTS_PER_PAGE);
        if (page > 0) {
            JigsawStudioMenuElement previous = element("previous", JigsawStudioMenuIcon.ARROW, "Previous Page");
            previous.onLeftClick(clicked -> refreshMain(
                    window.getViewer(), state.requestId(), workcell.stableId(), page - 1));
            window.setElement(-1, 5, previous);
        }

        JigsawStudioMenuElement pageIndicator = element("page", JigsawStudioMenuIcon.BOOK, "Page "
                + (page + 1) + "/" + pageCount);
        pageIndicator.addLore("" + workcell.variants().size() + " variants");
        window.setElement(0, 5, pageIndicator);

        if (page + 1 < pageCount) {
            JigsawStudioMenuElement next = element("next", JigsawStudioMenuIcon.ARROW, "Next Page");
            next.onLeftClick(clicked -> refreshMain(
                    window.getViewer(), state.requestId(), workcell.stableId(), page + 1));
            window.setElement(1, 5, next);
        }

        window.setElement(2, 5, evaluationElement(state.evaluation()));

        JigsawStudioMenuElement preview = element("preview", JigsawStudioMenuIcon.ENDER_EYE, "Go to Preview");
        preview.addLore("Open the deterministic assembly preview.");
        preview.onLeftClick(clicked -> goToPreview(window.getViewer(), state.requestId()));
        window.setElement(3, 5, preview);

        JigsawStudioMenuElement toolbox = element("toolbox", JigsawStudioMenuIcon.STICK, "Toolbox");
        toolbox.addLore("Take named sticks bound to this Studio session.");
        toolbox.onLeftClick(clicked -> openToolbox(
                window.getViewer(), state.requestId(), workcell.stableId(), 0));
        window.setElement(4, 5, toolbox);
    }

    private void renderWorkcellSettings(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell
    ) {
        PendingWorkcellResize pendingResize = pendingWorkcellResize(
                window.getViewer().id(),
                state.requestId(),
                workcell.stableId());
        JigsawStudioMenuState.Workcell renderedWorkcell = pendingResize == null
                ? workcell
                : withCapacity(workcell, pendingResize.dimensions());
        window.batch(() -> {
            window.clearElements();

            JigsawStudioMenuElement back = element("settings-back", JigsawStudioMenuIcon.ARROW, "Back to Variants");
            back.onLeftClick(clicked -> leaveWorkcellSettings(
                    window.getViewer(), state.requestId(), renderedWorkcell.stableId()));
            window.setElement(-4, 0, back);

            JigsawStudioMenuElement identity = element(
                    "settings-identity",
                    renderedWorkcell.enabled() ? JigsawStudioMenuIcon.LIME_WOOL : JigsawStudioMenuIcon.GRAY_WOOL,
                    safe(renderedWorkcell.displayName()));
            identity.addLore(safe(renderedWorkcell.stableId()));
            identity.addLore(renderedWorkcell.enabled()
                    ? "Enabled"
                    : "Disabled for assembly and export");
            if (!renderedWorkcell.canonicalName().equals(renderedWorkcell.displayName())) {
                identity.addLore("Solver role: " + safe(renderedWorkcell.canonicalName()));
            }
            identity.addLore("Capacity: " + dimensions(renderedWorkcell.capacity()));
            if (pendingResize != null) {
                identity.addLore(pendingResize.applying()
                        ? "Applying one live relayout"
                        : "Pending; apply when all dimensions are ready");
            }
            identity.addLore("Left-click for a rename stick");
            identity.addLore("Rename that stick in an anvil, then right-click it.");
            identity.addLore("Sneak-right-click the stick to reset this label.");
            identity.onLeftClick(clicked -> actions.giveTool(
                    window.getViewer(),
                    JigsawStudioToolPayload.workcell(
                            JigsawStudioToolAction.RENAME_WORKCELL,
                            state.requestId(),
                            renderedWorkcell.stableId())));
            window.setElement(0, 0, identity);
            window.setElement(4, 0, evaluationElement(state.evaluation()));

            if (state.mode() == JigsawStudioMode.PLANAR_JIGSAW) {
                JigsawStudioMenuElement enabled = element(
                        "settings-enabled",
                        renderedWorkcell.enabled() ? JigsawStudioMenuIcon.LEVER : JigsawStudioMenuIcon.REDSTONE_TORCH,
                        renderedWorkcell.enabled()
                                ? "Workcell Enabled"
                                : "Workcell Disabled");
                enabled.addLore("Disabled workcells remain editable and keep their size.");
                enabled.addLore("Left-click to "
                        + (renderedWorkcell.enabled() ? "disable" : "enable"));
                enabled.onLeftClick(clicked -> setWorkcellEnabled(
                        window.getViewer(),
                        state.requestId(),
                        renderedWorkcell.stableId(),
                        !renderedWorkcell.enabled()));
                window.setElement(0, 1, enabled);
            } else {
                JigsawStudioMenuElement spatial = element(
                        "spatial-bounds",
                        JigsawStudioMenuIcon.SCAFFOLDING,
                        "Spatial Workcell Bounds");
                spatial.addLore("Spatial project bounds are shared by the project.");
                spatial.addLore("Changing them regenerates the Studio layout.");
                window.setElement(0, 1, spatial);
            }

            JigsawStudioMenuElement connectors = element(
                    "settings-connectors",
                    renderedWorkcell.connectorsVisible() ? JigsawStudioMenuIcon.JIGSAW : JigsawStudioMenuIcon.STRUCTURE_VOID,
                    renderedWorkcell.connectorsVisible()
                            ? "Connector Blocks Visible"
                            : "Connector Blocks Hidden");
            connectors.addLore("Hidden connectors retain their metadata and final block state.");
            connectors.addLore("Left-click to "
                    + (renderedWorkcell.connectorsVisible() ? "hide" : "show") + " connector blocks");
            connectors.onLeftClick(clicked -> toggleConnectorBlocks(
                    window.getViewer(),
                    state.requestId(),
                    renderedWorkcell.stableId(),
                    !renderedWorkcell.connectorsVisible()));
            window.setElement(2, 1, connectors);

            JigsawStudioMenuElement resetConnectors = element(
                    "settings-reset-connectors",
                    JigsawStudioMenuIcon.RECOVERY_COMPASS,
                    "Reset Connector Blocks");
            resetConnectors.addLore("Restore every connector in this workcell from disk.");
            resetConnectors.addLore("Other edited blocks are left unchanged.");
            resetConnectors.onLeftClick(clicked -> resetConnectorBlocks(
                    window.getViewer(),
                    state.requestId(),
                    renderedWorkcell.stableId()));
            window.setElement(4, 1, resetConnectors);

            window.setElement(-2, 2, axisElement(
                    window,
                    state,
                    renderedWorkcell,
                    DimensionAxis.WIDTH,
                    JigsawStudioMenuIcon.IRON_INGOT));
            window.setElement(0, 2, axisElement(
                    window,
                    state,
                    renderedWorkcell,
                    DimensionAxis.HEIGHT,
                    JigsawStudioMenuIcon.GOLD_INGOT));
            window.setElement(2, 2, axisElement(
                    window,
                    state,
                    renderedWorkcell,
                    DimensionAxis.DEPTH,
                    JigsawStudioMenuIcon.COPPER_INGOT));

            if (pendingResize != null) {
                JigsawStudioMenuElement apply = element(
                        "settings-apply-capacity",
                        pendingResize.applying() ? JigsawStudioMenuIcon.CLOCK : JigsawStudioMenuIcon.EMERALD_BLOCK,
                        pendingResize.applying()
                                ? "Applying Cell Size"
                                : "Apply Cell Size");
                apply.addLore(dimensions(pendingResize.dimensions()));
                apply.addLore("Regenerates the layout once after all size edits.");
                if (!pendingResize.applying()) {
                    apply.onLeftClick(clicked -> applyWorkcellResize(
                            window.getViewer(), state.requestId(), renderedWorkcell.stableId()));
                }
                window.setElement(0, 3, apply);

                if (!pendingResize.applying()) {
                    JigsawStudioMenuElement discard = element(
                            "settings-discard-capacity",
                            JigsawStudioMenuIcon.BARRIER,
                            "Discard Size Changes");
                    discard.onLeftClick(clicked -> discardWorkcellResize(
                            window.getViewer(), state.requestId(), renderedWorkcell.stableId()));
                    window.setElement(2, 3, discard);
                }
            }

            JigsawStudioMenuElement footerBack = element("settings-footer-back", JigsawStudioMenuIcon.ARROW, "Back");
            footerBack.onLeftClick(clicked -> leaveWorkcellSettings(
                    window.getViewer(), state.requestId(), renderedWorkcell.stableId()));
            window.setElement(-4, 5, footerBack);

            JigsawStudioMenuElement undo = element(
                    "settings-undo",
                    JigsawStudioMenuIcon.CLOCK,
                    "Undo Last Autosave");
            undo.addLore("Restore the previous owned graph iteration.");
            undo.addLore("Up to five autosave iterations are retained on disk.");
            undo.onLeftClick(clicked -> undoAutosave(
                    window.getViewer(),
                    state.requestId()));
            window.setElement(0, 5, undo);

            if (renderedWorkcell.dirty() && !renderedWorkcell.saving()) {
                JigsawStudioMenuElement saveNow = element(
                        "save-now",
                        JigsawStudioMenuIcon.EMERALD,
                        "Flush Autosave Now");
                saveNow.addLore("Autosave is automatic.");
                saveNow.addLore("Use this only to flush pending work or recover immediately.");
                saveNow.onLeftClick(clicked -> flushNow(
                        window.getViewer(), state.requestId(), renderedWorkcell.stableId()));
                window.setElement(2, 5, saveNow);
            }

            JigsawStudioMenuElement preview = element(
                    "settings-preview",
                    JigsawStudioMenuIcon.ENDER_EYE,
                    "Go to Preview");
            preview.onLeftClick(clicked -> goToPreview(window.getViewer(), state.requestId()));
            window.setElement(3, 5, preview);

            JigsawStudioMenuElement toolbox = element("settings-toolbox", JigsawStudioMenuIcon.STICK, "Toolbox");
            toolbox.onLeftClick(clicked -> openToolbox(
                    window.getViewer(), state.requestId(), renderedWorkcell.stableId(), 0));
            window.setElement(4, 5, toolbox);
        });
    }

    private void renderVariantSizeSettings(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant
    ) {
        JigsawStudioCellDimensions current = variant.dimensions().orElseThrow();
        window.batch(() -> {
            window.clearElements();

            JigsawStudioMenuElement back = element("variant-size-back", JigsawStudioMenuIcon.ARROW, "Back to Details");
            back.onLeftClick(clicked -> openDetails(
                    window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), 0));
            window.setElement(-4, 0, back);

            JigsawStudioMenuElement identity = element(
                    "variant-size-identity",
                    JigsawStudioMenuIcon.SCAFFOLDING,
                    safe(variant.displayName()) + " Size");
            identity.addLore(safe(variant.pieceKey()));
            identity.addLore("Current: " + dimensions(current));
            identity.addLore("Workcell capacity: " + dimensions(workcell.capacity()));
            identity.addLore("Only this variant object and its edge connectors change.");
            window.setElement(0, 0, identity);
            window.setElement(4, 0, evaluationElement(state.evaluation()));

            window.setElement(-2, 2, variantAxisElement(
                    window, state, workcell, variant, DimensionAxis.WIDTH, JigsawStudioMenuIcon.IRON_INGOT));
            window.setElement(0, 2, variantAxisElement(
                    window, state, workcell, variant, DimensionAxis.HEIGHT, JigsawStudioMenuIcon.GOLD_INGOT));
            window.setElement(2, 2, variantAxisElement(
                    window, state, workcell, variant, DimensionAxis.DEPTH, JigsawStudioMenuIcon.COPPER_INGOT));

            JigsawStudioMenuElement capacity = element(
                    "variant-size-capacity",
                    variant.resizableToCapacity() ? JigsawStudioMenuIcon.NETHER_STAR : JigsawStudioMenuIcon.GRAY_DYE,
                    variant.resizableToCapacity()
                            ? "Resize This Variant to Capacity"
                            : "Variant Already Matches Capacity");
            capacity.addLore(dimensions(workcell.capacity()));
            if (variant.resizableToCapacity()) {
                capacity.addLore("Left-click to resize only this variant");
                capacity.onLeftClick(clicked -> resizeVariant(
                        window.getViewer(),
                        state.requestId(),
                        workcell.stableId(),
                        variant.pieceKey(),
                        workcell.capacity()));
            }
            window.setElement(0, 3, capacity);

            JigsawStudioMenuElement footerBack = element("variant-size-footer-back", JigsawStudioMenuIcon.ARROW, "Back");
            footerBack.onLeftClick(clicked -> openDetails(
                    window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), 0));
            window.setElement(-4, 5, footerBack);
        });
    }

    private JigsawStudioMenuElement variantAxisElement(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            DimensionAxis axis,
            JigsawStudioMenuIcon material
    ) {
        JigsawStudioCellDimensions current = variant.dimensions().orElseThrow();
        JigsawStudioMenuElement element = element(
                "variant-axis-" + axis.name().toLowerCase(Locale.ROOT),
                material,
                axis.displayName() + ": " + axis.value(current));
        element.addLore("Left-click: +1");
        element.addLore("Right-click: -1");
        element.addLore("Shift-left: +8");
        element.addLore("Shift-right: -8");
        element.addLore("Maximum: " + axis.value(workcell.capacity()));
        element.onLeftClick(clicked -> resizeVariantAxis(
                window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), axis, 1));
        element.onRightClick(clicked -> resizeVariantAxis(
                window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), axis, -1));
        element.onShiftLeftClick(clicked -> resizeVariantAxis(
                window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), axis, 8));
        element.onShiftRightClick(clicked -> resizeVariantAxis(
                window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), axis, -8));
        return element;
    }

    private void renderStructureSettings(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            String selectedWorkcellId,
            int requestedPage
    ) {
        int page = clampPage(requestedPage, state.themeSets().size(), THEME_SETS_PER_PAGE);
        purgeExpiredConfirmations(window.getViewer().id());
        window.batch(() -> {
            window.clearElements();

            JigsawStudioMenuElement back = element("structure-back", JigsawStudioMenuIcon.ARROW, "Back to Variants");
            back.onLeftClick(clicked -> refreshMain(
                    window.getViewer(), state.requestId(), selectedWorkcellId, 0));
            window.setElement(-4, 0, back);

            JigsawStudioMenuElement identity = element(
                    "structure-identity",
                    JigsawStudioMenuIcon.JIGSAW,
                    "Structure Themes & Caps");
            identity.addLore(safe(state.structureKey()));
            identity.addLore("Compatibility: " + compatibilityName(state.compatibilityTarget()));
            identity.addLore("Theme sets select one coherent variant family per assembly.");
            identity.addLore("Empty piece themes remain available to every family.");
            window.setElement(0, 0, identity);
            window.setElement(4, 0, evaluationElement(state.evaluation()));

            JigsawStudioMenuElement requireCaps = element(
                    "require-caps",
                    state.irisExtended()
                            ? state.requireCaps() ? JigsawStudioMenuIcon.IRON_BARS : JigsawStudioMenuIcon.TRIPWIRE_HOOK
                            : JigsawStudioMenuIcon.BARRIER,
                    !state.irisExtended()
                            ? "Mandatory Caps Require Iris Compatibility"
                            : state.requireCaps()
                            ? "Mandatory Caps Enabled"
                            : "Mandatory Caps Disabled");
            requireCaps.addLore(!state.irisExtended()
                    ? "Vanilla-portable export rejects Iris mandatory-cap metadata."
                    : state.requireCaps()
                    ? "Every open connector must close with a terminal piece."
                    : "Assembly may leave an open connector when no cap can fit.");
            if (state.irisExtended()) {
                requireCaps.addLore("Left-click to toggle");
                requireCaps.onLeftClick(clicked -> setRequireCaps(
                        window.getViewer(), state.requestId(), !state.requireCaps()));
            }
            window.setElement(-3, 1, requireCaps);

            String nextThemeKey = nextThemeSetKey(state.themeSets());
            boolean themeEditingAvailable = state.irisExtended();
            JigsawStudioMenuElement createTheme = element(
                    "new-theme-set",
                    themeEditingAvailable ? JigsawStudioMenuIcon.NETHER_STAR : JigsawStudioMenuIcon.BARRIER,
                    themeEditingAvailable
                            ? "Duplicate All Enabled Cells as Family: " + safe(nextThemeKey)
                            : "Theme Sets Require Iris Compatibility");
            if (themeEditingAvailable) {
                createTheme.addLore("Creates one new owned variant for every enabled workcell.");
                createTheme.addLore("All created variants join " + safe(nextThemeKey) + ".");
                createTheme.addLore("One family is selected for the complete assembly.");
                createTheme.addLore("Left-click to create the complete set");
                createTheme.onLeftClick(clicked -> duplicateActiveFamily(
                        window.getViewer(), state.requestId(), nextThemeKey));
            } else {
                createTheme.addLore("Vanilla jigsaw resources cannot encode Iris theme metadata.");
            }
            window.setElement(0, 1, createTheme);

            boolean deleteConfirmed = isProjectDeleteConfirmed(
                    window.getViewer().id(), state.requestId());
            JigsawStudioMenuElement deleteProject = element(
                    "delete-project",
                    deleteConfirmed ? JigsawStudioMenuIcon.REDSTONE : JigsawStudioMenuIcon.LAVA_BUCKET,
                    deleteConfirmed
                            ? "Confirm Delete Jigsaw"
                            : "Delete Jigsaw")
                    .setEnchanted(deleteConfirmed);
            deleteProject.addLore(deleteConfirmed
                    ? "Click again to delete this entire managed project."
                    : "Click twice within 10 seconds to confirm.");
            deleteProject.onLeftClick(clicked -> confirmOrDeleteProject(
                    window.getViewer(), state.requestId(), selectedWorkcellId, page));
            window.setElement(3, 1, deleteProject);

            addThemeSets(window, state, page);

            JigsawStudioMenuElement footerBack = element("structure-footer-back", JigsawStudioMenuIcon.ARROW, "Back");
            footerBack.onLeftClick(clicked -> refreshMain(
                    window.getViewer(), state.requestId(), selectedWorkcellId, 0));
            window.setElement(-4, 5, footerBack);

            int pageCount = pageCount(state.themeSets().size(), THEME_SETS_PER_PAGE);
            if (page > 0) {
                JigsawStudioMenuElement previous = element(
                        "theme-previous",
                        JigsawStudioMenuIcon.ARROW,
                        "Previous Page");
                previous.onLeftClick(clicked -> refreshStructureSettings(
                        window.getViewer(), state.requestId(), selectedWorkcellId, page - 1));
                window.setElement(-1, 5, previous);
            }
            JigsawStudioMenuElement indicator = element(
                    "theme-page",
                    JigsawStudioMenuIcon.BOOK,
                    "Page " + (page + 1) + "/" + pageCount);
            indicator.addLore("" + state.themeSets().size() + " theme sets");
            window.setElement(0, 5, indicator);
            if (page + 1 < pageCount) {
                JigsawStudioMenuElement next = element("theme-next", JigsawStudioMenuIcon.ARROW, "Next Page");
                next.onLeftClick(clicked -> refreshStructureSettings(
                        window.getViewer(), state.requestId(), selectedWorkcellId, page + 1));
                window.setElement(1, 5, next);
            }
            window.setElement(4, 5, evaluationElement(state.evaluation()));
        });
    }

    private void addThemeSets(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            int page
    ) {
        List<JigsawStudioMenuState.ThemeSet> themeSets = page(
                state.themeSets(), page, THEME_SETS_PER_PAGE);
        if (themeSets.isEmpty()) {
            JigsawStudioMenuElement empty = element(
                    "no-theme-sets",
                    JigsawStudioMenuIcon.GRAY_DYE,
                    "Implicit Unthemed Assembly");
            empty.addLore("Create variant-1 to begin coherent theme selection.");
            window.setElement(0, 3, empty);
            return;
        }
        for (int index = 0; index < themeSets.size(); index++) {
            JigsawStudioMenuState.ThemeSet themeSet = themeSets.get(index);
            int position = GRID_POSITIONS[index % GRID_POSITIONS.length];
            int row = 2 + index / GRID_POSITIONS.length;
            JigsawStudioMenuElement element = element(
                    "theme-set-" + index,
                    state.irisExtended() ? JigsawStudioMenuIcon.PURPLE_DYE : JigsawStudioMenuIcon.GRAY_DYE,
                    safe(themeSet.key()));
            element.addLore("Selection weight: " + themeSet.weight());
            element.addLore("Whole-assembly chance: "
                    + themeSelectionPercent(state.themeSets(), themeSet));
            if (state.irisExtended()) {
                element.addLore("Left-click: weight +1");
                element.addLore("Right-click: weight -1");
                element.addLore("Shift-left: weight +8");
                element.addLore("Shift-right: weight -8");
                element.onLeftClick(clicked -> adjustThemeSetWeight(
                        window.getViewer(), state.requestId(), themeSet, 1));
                element.onRightClick(clicked -> adjustThemeSetWeight(
                        window.getViewer(), state.requestId(), themeSet, -1));
                element.onShiftLeftClick(clicked -> adjustThemeSetWeight(
                        window.getViewer(), state.requestId(), themeSet, 8));
                element.onShiftRightClick(clicked -> adjustThemeSetWeight(
                        window.getViewer(), state.requestId(), themeSet, -8));
            } else {
                element.addLore("Theme metadata is unavailable for vanilla-portable graphs.");
            }
            window.setElement(position, row, element);
        }
    }

    private JigsawStudioMenuElement axisElement(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            DimensionAxis axis,
            JigsawStudioMenuIcon material
    ) {
        JigsawStudioMenuElement element = element(
                "axis-" + axis.name().toLowerCase(Locale.ROOT),
                material,
                axis.displayName() + " Capacity: " + axis.value(workcell.capacity()));
        element.addLore("Left-click: +1");
        element.addLore("Right-click: -1");
        element.addLore("Shift-left: +8");
        element.addLore("Shift-right: -8");
        element.addLore("Changes stay in this menu until Apply Cell Size.");
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

    private void renderDetails(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            int requestedPage
    ) {
        int page = clampPage(requestedPage, variant.memberships().size(), MEMBERSHIPS_PER_PAGE);
        purgeExpiredConfirmations(window.getViewer().id());
        window.batch(() -> {
            window.clearElements();
            addDetailsHeader(window, state, workcell, variant, page);
            addMemberships(window, state, workcell, variant, page);
            addDetailsFooter(window, state, workcell, variant, page);
        });
    }

    private void addDetailsHeader(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            int page
    ) {
        JigsawStudioMenuElement back = element("back", JigsawStudioMenuIcon.ARROW, "Back to Variants");
        back.onLeftClick(clicked -> refreshMain(
                window.getViewer(), state.requestId(), workcell.stableId(), 0));
        window.setElement(-4, 0, back);

        JigsawStudioMenuElement identity = element("identity", JigsawStudioMenuIcon.NAME_TAG, safe(variant.displayName()));
        identity.addLore(safe(variant.pieceKey()));
        identity.addLore(variant.owned()
                ? "Owned variant"
                : "Read-only variant");
        identity.addLore(variant.active()
                ? "Loaded in this workcell"
                : "Not currently loaded");
        identity.addLore("Size: " + variant.dimensions()
                .map(JigsawStudioMenuController::dimensions)
                .orElse("Object missing"));
        identity.addLore("Themes: " + themes(variant.themes()));
        identity.addLore("Depth: " + variant.rules().minimumDepth()
                + "-" + variant.rules().maximumDepth());
        identity.addLore("Placements: " + variant.rules().minimumPlacements()
                + "-" + maximumPlacements(variant.rules().maximumPlacements()));
        identity.addLore("Terminal: " + yesNo(variant.rules().terminal()));
        identity.addLore("Pool entries: " + variant.memberships().size());
        if (variant.owned()) {
            identity.addLore("Left-click for a rename stick");
            identity.addLore("Rename that stick in an anvil, then right-click it.");
            identity.addLore("Sneak-right-click the stick to reset this label.");
            identity.onLeftClick(clicked -> actions.giveTool(
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
        JigsawStudioMenuElement settings = element(
                "variant-settings",
                irisRuleEditing ? JigsawStudioMenuIcon.COMPARATOR : JigsawStudioMenuIcon.GRAY_DYE,
                irisRuleEditing
                        ? "Themes & Piece Rules"
                        : "Themes & Rules Unavailable");
        settings.addLore(irisRuleEditing
                ? "Edit this loaded variant's theme membership and placement rules."
                : state.irisExtended()
                        ? "Load an owned variant before editing its rules."
                        : "Vanilla-portable pieces cannot encode Iris theme or rule metadata.");
        if (irisRuleEditing) {
            settings.addLore("Left-click to edit");
            settings.onLeftClick(clicked -> openVariantSettings(
                    window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), 0));
        }
        window.setElement(-2, 0, settings);

        boolean rotationEditable = variant.active() && variant.rotationEditable();
        JigsawStudioMenuElement rotation = element(
                "rotation",
                rotationEditable ? JigsawStudioMenuIcon.REPEATER : JigsawStudioMenuIcon.BARRIER,
                rotationEditable
                        ? "Toggle Rotation"
                        : "Rotation is Read-only");
        rotation.addLore("Currently: " + (variant.rotatable() ? "Enabled" : "Disabled"));
        if (rotationEditable) {
            rotation.addLore("Left-click to toggle");
            rotation.onLeftClick(clicked -> toggleRotation(
                    window.getViewer(), state.requestId(), workcell.stableId()));
        } else if (!variant.active()) {
            rotation.addLore("Load this variant before editing rotation.");
        } else if (variant.owned() && variant.rotatable()) {
            rotation.addLore("Vanilla-portable variants must remain rotatable.");
        }
        window.setElement(3, 0, rotation);

        boolean sizeEditable = variant.owned() && variant.dimensions().isPresent();
        JigsawStudioMenuElement size = element(
                "variant-size",
                sizeEditable ? JigsawStudioMenuIcon.SCAFFOLDING : JigsawStudioMenuIcon.BARRIER,
                sizeEditable
                        ? "Edit This Variant's Size"
                        : "Variant Size is Read-only");
        size.addLore("Current: " + variant.dimensions()
                .map(JigsawStudioMenuController::dimensions)
                .orElse("Object missing"));
        size.addLore("Workcell capacity: " + dimensions(workcell.capacity()));
        if (sizeEditable) {
            size.addLore("Left-click for width, height, and depth controls");
            size.onLeftClick(clicked -> openVariantSizeSettings(
                    window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey()));
            if (variant.resizableToCapacity()) {
                size.addLore("Shift-left: resize this variant to capacity");
                size.onShiftLeftClick(clicked -> resizeVariant(
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
        boolean confirmed = deletionAvailable && isDeleteConfirmed(
                window.getViewer().id(),
                state.requestId(),
                workcell.stableId(),
                variant.pieceKey());
        JigsawStudioMenuElement delete = element(
                "delete-variant",
                deletionAvailable ? confirmed ? JigsawStudioMenuIcon.REDSTONE : JigsawStudioMenuIcon.LAVA_BUCKET : JigsawStudioMenuIcon.BARRIER,
                deletionAvailable
                        ? confirmed ? "Confirm Delete Variant" : "Delete Variant"
                        : variant.active()
                                ? "Loaded Variant Cannot Be Deleted"
                                : lastVariant
                                        ? "Last Variant Cannot Be Deleted"
                                        : "Deletion is Read-only")
                .setEnchanted(confirmed);
        if (deletionAvailable) {
            delete.addLore(confirmed
                    ? "Click again to permanently delete this owned variant."
                    : "Click twice within 10 seconds to confirm.");
            delete.onLeftClick(clicked -> confirmOrDeleteVariant(
                    window.getViewer(),
                    state.requestId(),
                    workcell.stableId(),
                    variant.pieceKey(),
                    page));
        } else if (variant.active()) {
            delete.addLore("Load another variant in this workcell first.");
        } else if (lastVariant) {
            delete.addLore("Create another variant before deleting this one.");
        }
        window.setElement(4, 0, delete);
    }

    private void addMemberships(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            int page
    ) {
        List<JigsawStudioMenuState.Membership> memberships = page(
                variant.memberships(), page, MEMBERSHIPS_PER_PAGE);
        if (memberships.isEmpty()) {
            JigsawStudioMenuElement empty = element("no-memberships", JigsawStudioMenuIcon.BARRIER, "No Pool Entries");
            empty.addLore("This variant is not linked from an owned pool.");
            window.setElement(0, 2, empty);
            return;
        }

        for (int index = 0; index < memberships.size(); index++) {
            JigsawStudioMenuState.Membership membership = memberships.get(index);
            int position = GRID_POSITIONS[index % GRID_POSITIONS.length];
            int row = 1 + index / GRID_POSITIONS.length;
            boolean confirmed = variant.active() && variant.owned() && isUnlinkConfirmed(
                    window.getViewer().id(),
                    state.requestId(),
                    workcell.stableId(),
                    variant.pieceKey(),
                    membership);
            JigsawStudioMenuElement element = element(
                    "membership-" + index,
                    confirmed ? JigsawStudioMenuIcon.REDSTONE : JigsawStudioMenuIcon.GOLD_NUGGET,
                    safe(displayKey(membership.poolKey()))
                            + " [" + membership.entryIndex() + "]")
                    .setEnchanted(confirmed);
            element.addLore(safe(membership.poolKey()));
            element.addLore("Exact entry: " + membership.entryIndex());
            element.addLore("Weight: " + membership.weight());
            element.addLore("Chance: " + chance(membership.chance())
                    + (state.irisExtended() ? "" : " (Iris only)"));
            if (variant.active() && variant.owned()) {
                element.addLore("Left-click: weight +1");
                element.addLore("Right-click: weight -1");
                if (state.irisExtended()) {
                    element.addLore("Shift-left: chance +"
                            + CHANCE_STEP_PERCENTAGE_POINTS + "%");
                    element.addLore("Shift-right: chance -"
                            + CHANCE_STEP_PERCENTAGE_POINTS + "%");
                } else {
                    element.addLore("Vanilla-portable pool entries always have 100% chance.");
                }
                element.addLore(confirmed
                        ? "Middle-click again to unlink"
                        : "Middle-click twice to unlink");
                element.onLeftClick(clicked -> adjustMembershipWeight(
                        window.getViewer(), state.requestId(), workcell.stableId(), membership, 1));
                element.onRightClick(clicked -> adjustMembershipWeight(
                        window.getViewer(), state.requestId(), workcell.stableId(), membership, -1));
                if (state.irisExtended()) {
                    element.onShiftLeftClick(clicked -> adjustMembershipChance(
                            window.getViewer(),
                            state.requestId(),
                            workcell.stableId(),
                            membership,
                            CHANCE_STEP_PERCENTAGE_POINTS));
                    element.onShiftRightClick(clicked -> adjustMembershipChance(
                            window.getViewer(),
                            state.requestId(),
                            workcell.stableId(),
                            membership,
                            -CHANCE_STEP_PERCENTAGE_POINTS));
                }
                element.onMiddleClick(clicked -> confirmOrUnlink(
                        window.getViewer(), state.requestId(), workcell.stableId(), membership, page));
            } else {
                element.addLore(variant.active()
                        ? "Read-only pool entry"
                        : "Load this variant to edit its pool entry");
            }
            window.setElement(position, row, element);
        }
    }

    private void addDetailsFooter(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            int page
    ) {
        JigsawStudioMenuElement back = element("footer-back", JigsawStudioMenuIcon.ARROW, "Back");
        back.onLeftClick(clicked -> refreshMain(
                window.getViewer(), state.requestId(), workcell.stableId(), 0));
        window.setElement(-4, 5, back);

        int pageCount = pageCount(variant.memberships().size(), MEMBERSHIPS_PER_PAGE);
        if (page > 0) {
            JigsawStudioMenuElement previous = element("membership-previous", JigsawStudioMenuIcon.ARROW, "Previous Page");
            previous.onLeftClick(clicked -> refreshDetails(
                    window.getViewer(),
                    state.requestId(),
                    workcell.stableId(),
                    variant.pieceKey(),
                    page - 1));
            window.setElement(-1, 5, previous);
        }
        JigsawStudioMenuElement indicator = element("membership-page", JigsawStudioMenuIcon.BOOK, "Page "
                + (page + 1) + "/" + pageCount);
        indicator.addLore("" + variant.memberships().size() + " exact pool entries");
        window.setElement(0, 5, indicator);
        if (page + 1 < pageCount) {
            JigsawStudioMenuElement next = element("membership-next", JigsawStudioMenuIcon.ARROW, "Next Page");
            next.onLeftClick(clicked -> refreshDetails(
                    window.getViewer(),
                    state.requestId(),
                    workcell.stableId(),
                    variant.pieceKey(),
                    page + 1));
            window.setElement(1, 5, next);
        }
        window.setElement(2, 5, evaluationElement(state.evaluation()));

        JigsawStudioMenuElement toolbox = element("details-toolbox", JigsawStudioMenuIcon.STICK, "Toolbox");
        toolbox.onLeftClick(clicked -> openToolbox(
                window.getViewer(), state.requestId(), workcell.stableId(), 0));
        window.setElement(4, 5, toolbox);
    }

    private void renderVariantSettings(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            int requestedPage
    ) {
        int page = clampPage(requestedPage, state.themeSets().size(), THEME_SETS_PER_PAGE);
        window.batch(() -> {
            window.clearElements();

            JigsawStudioMenuElement back = element("variant-settings-back", JigsawStudioMenuIcon.ARROW, "Back to Details");
            back.onLeftClick(clicked -> openDetails(
                    window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), 0));
            window.setElement(-4, 0, back);

            JigsawStudioMenuElement identity = element(
                    "variant-settings-identity",
                    JigsawStudioMenuIcon.COMPARATOR,
                    safe(variant.displayName()) + " Rules");
            identity.addLore(safe(variant.pieceKey()));
            identity.addLore("Theme memberships: " + themes(variant.themes()));
            identity.addLore("Use Duplicate All Enabled Cells as Family for coherent sets.");
            window.setElement(0, 0, identity);
            window.setElement(4, 0, evaluationElement(state.evaluation()));

            if (!variant.active() || !variant.owned() || !state.irisExtended()) {
                JigsawStudioMenuElement unavailable = element(
                        "variant-settings-unavailable",
                        JigsawStudioMenuIcon.BARRIER,
                        state.irisExtended()
                                ? "Load an Owned Variant First"
                                : "Iris Metadata Unavailable");
                unavailable.addLore(state.irisExtended()
                        ? "Rules and themes can only change for the loaded owned variant."
                        : "Vanilla-portable pieces use vanilla placement behavior.");
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

                JigsawStudioMenuElement terminal = element(
                        "rule-terminal",
                        variant.rules().terminal() ? JigsawStudioMenuIcon.REDSTONE_TORCH : JigsawStudioMenuIcon.LEVER,
                        variant.rules().terminal()
                                ? "Terminal Piece: Yes"
                                : "Terminal Piece: No");
                terminal.addLore("Terminal pieces consume a connector without expanding.");
                terminal.addLore("Left-click to toggle");
                terminal.onLeftClick(clicked -> updateVariantRules(
                        window.getViewer(),
                        state.requestId(),
                        workcell.stableId(),
                        variant.pieceKey(),
                        withTerminal(variant.rules(), !variant.rules().terminal())));
                window.setElement(4, 1, terminal);

                addVariantThemeMemberships(window, state, workcell, variant, page);
            }

            JigsawStudioMenuElement footerBack = element("variant-rules-footer-back", JigsawStudioMenuIcon.ARROW, "Back");
            footerBack.onLeftClick(clicked -> openDetails(
                    window.getViewer(), state.requestId(), workcell.stableId(), variant.pieceKey(), 0));
            window.setElement(-4, 5, footerBack);

            int pageCount = pageCount(state.themeSets().size(), THEME_SETS_PER_PAGE);
            if (page > 0) {
                JigsawStudioMenuElement previous = element(
                        "variant-theme-previous",
                        JigsawStudioMenuIcon.ARROW,
                        "Previous Page");
                previous.onLeftClick(clicked -> refreshVariantSettings(
                        window.getViewer(),
                        state.requestId(),
                        workcell.stableId(),
                        variant.pieceKey(),
                        page - 1));
                window.setElement(-1, 5, previous);
            }
            JigsawStudioMenuElement indicator = element(
                    "variant-theme-page",
                    JigsawStudioMenuIcon.BOOK,
                    "Theme Page " + (page + 1) + "/" + pageCount);
            indicator.addLore("" + state.themeSets().size() + " declared themes");
            window.setElement(0, 5, indicator);
            if (page + 1 < pageCount) {
                JigsawStudioMenuElement next = element(
                        "variant-theme-next",
                        JigsawStudioMenuIcon.ARROW,
                        "Next Page");
                next.onLeftClick(clicked -> refreshVariantSettings(
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

    private JigsawStudioMenuElement ruleElement(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            RuleField field
    ) {
        int value = field.value(variant.rules());
        JigsawStudioMenuElement element = element(
                "rule-" + field.name().toLowerCase(Locale.ROOT),
                field.material(),
                field.displayName() + ": " + field.displayValue(value));
        boolean unlimitedMaximum = field == RuleField.MAXIMUM_PLACEMENTS && value == 0;
        if (unlimitedMaximum) {
            element.addLore("Increase is already unlimited.");
        } else {
            element.addLore("Left-click: +1");
            element.addLore("Shift-left: +" + field.shiftStep());
            element.onLeftClick(clicked -> adjustVariantRule(
                    window.getViewer(), state.requestId(), workcell, variant, field, 1));
            element.onShiftLeftClick(clicked -> adjustVariantRule(
                    window.getViewer(), state.requestId(), workcell, variant, field, field.shiftStep()));
        }
        element.addLore(unlimitedMaximum
                ? "Right-click: set 512"
                : "Right-click: -1");
        element.addLore(unlimitedMaximum
                ? "Shift-right: set 497"
                : "Shift-right: -" + field.shiftStep());
        element.onRightClick(clicked -> adjustVariantRule(
                window.getViewer(), state.requestId(), workcell, variant, field, -1));
        element.onShiftRightClick(clicked -> adjustVariantRule(
                window.getViewer(), state.requestId(), workcell, variant, field, -field.shiftStep()));
        return element;
    }

    private void addVariantThemeMemberships(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant variant,
            int page
    ) {
        List<JigsawStudioMenuState.ThemeSet> themeSets = page(
                state.themeSets(), page, THEME_SETS_PER_PAGE);
        if (themeSets.isEmpty()) {
            JigsawStudioMenuElement empty = element(
                    "variant-no-themes",
                    JigsawStudioMenuIcon.GRAY_DYE,
                    "Available to Every Theme");
            empty.addLore("Create a coherent theme set from Structure Rules first.");
            window.setElement(0, 3, empty);
            return;
        }
        for (int index = 0; index < themeSets.size(); index++) {
            JigsawStudioMenuState.ThemeSet themeSet = themeSets.get(index);
            boolean member = variant.themes().contains(themeSet.key());
            int position = GRID_POSITIONS[index % GRID_POSITIONS.length];
            int row = 2 + index / GRID_POSITIONS.length;
            JigsawStudioMenuElement element = element(
                    "variant-theme-" + index,
                    member ? JigsawStudioMenuIcon.LIME_DYE : JigsawStudioMenuIcon.GRAY_DYE,
                    safe(themeSet.key()))
                    .setEnchanted(member);
            element.addLore(member
                    ? "This variant belongs to the theme."
                    : "This variant does not belong to the theme.");
            element.addLore("Only variants in the selected family are eligible.");
            element.addLore("Left-click to toggle membership");
            element.onLeftClick(clicked -> toggleVariantTheme(
                    window.getViewer(), state.requestId(), workcell, variant, themeSet.key()));
            window.setElement(position, row, element);
        }
    }

    private void renderToolbox(
            JigsawStudioMenuWindow window,
            JigsawStudioMenuState state,
            JigsawStudioMenuState.Workcell workcell,
            int requestedPage
    ) {
        List<ToolboxTool> allTools = toolboxTools(state, workcell);
        int page = clampPage(requestedPage, allTools.size(), TOOLS_PER_PAGE);
        List<ToolboxTool> tools = page(allTools, page, TOOLS_PER_PAGE);
        window.batch(() -> {
            window.clearElements();

            JigsawStudioMenuElement back = element("toolbox-back", JigsawStudioMenuIcon.ARROW, "Back to Variants");
            back.onLeftClick(clicked -> refreshMain(
                    window.getViewer(), state.requestId(), workcell.stableId(), 0));
            window.setElement(-4, 0, back);

            JigsawStudioMenuElement heading = element("toolbox-heading", JigsawStudioMenuIcon.CHEST, "Bound Tool Sticks");
            heading.addLore("Every stick is bound to request " + state.requestId());
            heading.addLore("Stale sticks stop working when this Studio closes.");
            window.setElement(0, 0, heading);
            window.setElement(4, 0, evaluationElement(state.evaluation()));

            for (int index = 0; index < tools.size(); index++) {
                ToolboxTool tool = tools.get(index);
                int position = GRID_POSITIONS[index % GRID_POSITIONS.length];
                int row = 1 + index / GRID_POSITIONS.length;
                JigsawStudioMenuElement element = element(
                        "tool-" + index,
                        JigsawStudioMenuIcon.STICK,
                        safe(tool.displayName()));
                element.addLore("Action: " + tool.payload().action().displayName());
                addToolBindingLore(element, tool.payload());
                element.addLore(tool.payload().action().destructive()
                        ? "Bound tools require confirmation when used."
                        : "Left-click to receive this named stick.");
                element.onLeftClick(clicked -> giveTool(
                        window.getViewer(), state.requestId(), tool.payload()));
                window.setElement(position, row, element);
            }

            JigsawStudioMenuElement footerBack = element("toolbox-footer-back", JigsawStudioMenuIcon.ARROW, "Back");
            footerBack.onLeftClick(clicked -> refreshMain(
                    window.getViewer(), state.requestId(), workcell.stableId(), 0));
            window.setElement(-4, 5, footerBack);

            int pageCount = pageCount(allTools.size(), TOOLS_PER_PAGE);
            if (page > 0) {
                JigsawStudioMenuElement previous = element("toolbox-previous", JigsawStudioMenuIcon.ARROW, "Previous Page");
                previous.onLeftClick(clicked -> refreshToolbox(
                        window.getViewer(), state.requestId(), workcell.stableId(), page - 1));
                window.setElement(-1, 5, previous);
            }
            JigsawStudioMenuElement indicator = element("toolbox-page", JigsawStudioMenuIcon.BOOK, "Page "
                    + (page + 1) + "/" + pageCount);
            indicator.addLore("" + allTools.size() + " bound tools");
            window.setElement(0, 5, indicator);
            if (page + 1 < pageCount) {
                JigsawStudioMenuElement next = element("toolbox-next", JigsawStudioMenuIcon.ARROW, "Next Page");
                next.onLeftClick(clicked -> refreshToolbox(
                        window.getViewer(), state.requestId(), workcell.stableId(), page + 1));
                window.setElement(1, 5, next);
            }
        });
    }

    private void selectWorkcell(ToolPlayer player, UUID requestId, String workcellId) {
        if (matchingState(player, requestId, true).isEmpty()) {
            return;
        }
        if (actions.teleportToWorkcell(player, workcellId)) {
            clearConfirmations(player.id());
            closeAfterAction(player);
        }
    }

    private void toggleConnectorBlocks(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            boolean visible
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty() || current.get().workcell(workcellId) == null) {
            return;
        }
        if (actions.setConnectorBlocksVisible(player, workcellId, visible)) {
            closeAfterAction(player);
        }
    }

    private void resetConnectorBlocks(ToolPlayer player, UUID requestId, String workcellId) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty() || current.get().workcell(workcellId) == null) {
            return;
        }
        if (actions.resetConnectorBlocks(player, workcellId)) {
            closeAfterAction(player);
        }
    }

    private void undoAutosave(ToolPlayer player, UUID requestId) {
        if (matchingState(player, requestId, true).isEmpty()) {
            return;
        }
        if (actions.undoAutosave(player)) {
            closeAfterAction(player);
        }
    }

    private void switchVariant(ToolPlayer player, UUID requestId, String workcellId, String pieceKey) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        JigsawStudioMenuState.Variant variant = variant(workcell, pieceKey);
        if (variant == null) {
            stale(player);
            return;
        }
        if (variant.active()) {
            send(player, "That variant is already loaded. Right-click it for details.");
            return;
        }
        if (actions.switchVariant(player, workcellId, pieceKey, false)) {
            closeAfterAction(player);
        }
    }

    private void openDetails(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            String pieceKey,
            int page
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        JigsawStudioMenuState.Variant variant = variant(workcell, pieceKey);
        if (variant == null) {
            stale(player);
            return;
        }
        JigsawStudioMenuWindow window = windows.get(player.id());
        if (window != null) {
            renderDetails(window, current.get(), workcell, variant, page);
        }
    }

    private void openWorkcellSettings(ToolPlayer player, UUID requestId, String workcellId) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        JigsawStudioMenuWindow window = windows.get(player.id());
        if (workcell == null || window == null) {
            stale(player);
            return;
        }
        renderWorkcellSettings(window, current.get(), workcell);
    }

    private void openStructureSettings(
            ToolPlayer player,
            UUID requestId,
            String selectedWorkcellId,
            int page
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuWindow window = windows.get(player.id());
        if (current.get().workcell(selectedWorkcellId) == null || window == null) {
            stale(player);
            return;
        }
        renderStructureSettings(window, current.get(), selectedWorkcellId, page);
    }

    private void openVariantSettings(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            String pieceKey,
            int page
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        JigsawStudioMenuState.Variant variant = variant(workcell, pieceKey);
        JigsawStudioMenuWindow window = windows.get(player.id());
        if (workcell == null || variant == null || window == null) {
            stale(player);
            return;
        }
        if (!variant.active() || !variant.owned()) {
            send(player, "Load an owned variant before editing its rules.");
            return;
        }
        if (!current.get().irisExtended()) {
            send(player, "Vanilla-portable pieces cannot encode Iris theme or piece-rule metadata.");
            return;
        }
        renderVariantSettings(window, current.get(), workcell, variant, page);
    }

    private void openVariantSizeSettings(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            String pieceKey
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        JigsawStudioMenuState.Variant variant = variant(workcell, pieceKey);
        JigsawStudioMenuWindow window = windows.get(player.id());
        if (workcell == null || variant == null || !variant.owned()
                || variant.dimensions().isEmpty() || window == null) {
            stale(player);
            return;
        }
        renderVariantSizeSettings(window, current.get(), workcell, variant);
    }

    private void openToolbox(ToolPlayer player, UUID requestId, String workcellId, int page) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        JigsawStudioMenuWindow window = windows.get(player.id());
        if (workcell == null || window == null) {
            stale(player);
            return;
        }
        renderToolbox(window, current.get(), workcell, page);
    }

    private void createVariant(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            boolean duplicateActive
    ) {
        if (matchingState(player, requestId, true).isEmpty()) {
            return;
        }
        if (actions.createVariant(player, workcellId, duplicateActive)) {
            closeAfterAction(player);
        }
    }

    private void setWorkcellEnabled(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            boolean enabled
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        if (current.get().workcell(workcellId) == null) {
            stale(player);
            return;
        }
        if (actions.setWorkcellEnabled(player, workcellId, enabled)) {
            closeAfterAction(player);
        }
    }

    private void resizeWorkcell(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            DimensionAxis axis,
            int delta
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        if (workcell == null) {
            stale(player);
            return;
        }
        UUID playerId = player.id();
        PendingWorkcellResize existing = pendingWorkcellResize(playerId, requestId, workcellId);
        if (existing != null && existing.applying()) {
            send(player, "That cell size is already being applied.");
            return;
        }
        JigsawStudioCellDimensions base = existing == null
                ? workcell.capacity()
                : existing.dimensions();
        Optional<JigsawStudioCellDimensions> adjusted = adjustedDimensions(
                base, axis, delta);
        if (adjusted.isEmpty()) {
            sendError(player, "That workcell size is outside Iris limits.");
            return;
        }
        PendingWorkcellResize pending = new PendingWorkcellResize(
                requestId,
                workcellId,
                adjusted.get(),
                false);
        pendingWorkcellResizes.put(playerId, pending);
        JigsawStudioMenuWindow window = windows.get(playerId);
        if (window != null) {
            renderWorkcellSettings(window, current.get(), withCapacity(workcell, pending.dimensions()));
        }
    }

    private void applyWorkcellResize(ToolPlayer player, UUID requestId, String workcellId) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        UUID playerId = player.id();
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
        JigsawStudioMenuWindow window = windows.get(playerId);
        if (window != null) {
            renderWorkcellSettings(window, current.get(), withCapacity(workcell, applying.dimensions()));
        }
        if (!actions.updateWorkcellDimensions(player, workcellId, applying.dimensions())) {
            pendingWorkcellResizes.replace(playerId, applying, pending);
            refreshWorkcellSettings(player, requestId, workcellId);
            return;
        }
        scheduleWorkcellResizeRefresh(player, applying, WORKCELL_RESIZE_REFRESH_ATTEMPTS);
    }

    private void scheduleWorkcellResizeRefresh(
            ToolPlayer player,
            PendingWorkcellResize pending,
            int attemptsRemaining
    ) {
        scheduler.onPlayerLater(
                player,
                () -> refreshAppliedWorkcellResize(player, pending, attemptsRemaining),
                WORKCELL_RESIZE_REFRESH_TICKS,
                () -> pendingWorkcellResizes.remove(player.id(), pending));
    }

    private void refreshAppliedWorkcellResize(
            ToolPlayer player,
            PendingWorkcellResize pending,
            int attemptsRemaining
    ) {
        UUID playerId = player.id();
        if (!pending.equals(pendingWorkcellResizes.get(playerId))) {
            return;
        }
        Optional<JigsawStudioMenuState> current = matchingState(player, pending.requestId(), false);
        JigsawStudioMenuState.Workcell workcell = current
                .map(state -> state.workcell(pending.workcellId()))
                .orElse(null);
        if (workcell == null) {
            pendingWorkcellResizes.remove(playerId, pending);
            return;
        }
        if (workcell.capacity().equals(pending.dimensions())) {
            pendingWorkcellResizes.remove(playerId, pending);
            JigsawStudioMenuWindow window = windows.get(playerId);
            if (window != null) {
                renderWorkcellSettings(window, current.orElseThrow(), workcell);
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
        JigsawStudioMenuWindow window = windows.get(playerId);
        if (window != null) {
            renderWorkcellSettings(window, current.orElseThrow(), withCapacity(workcell, retry.dimensions()));
        }
        send(player, "Cell resizing is still pending; use Apply Cell Size to retry after the current operation settles.");
    }

    private void discardWorkcellResize(ToolPlayer player, UUID requestId, String workcellId) {
        PendingWorkcellResize pending = pendingWorkcellResize(player.id(), requestId, workcellId);
        if (pending != null && !pending.applying()) {
            pendingWorkcellResizes.remove(player.id(), pending);
        }
        refreshWorkcellSettings(player, requestId, workcellId);
    }

    private void leaveWorkcellSettings(ToolPlayer player, UUID requestId, String workcellId) {
        PendingWorkcellResize pending = pendingWorkcellResize(player.id(), requestId, workcellId);
        if (pending != null && !pending.applying()) {
            pendingWorkcellResizes.remove(player.id(), pending);
        }
        refreshMain(player, requestId, workcellId, 0);
    }

    private void refreshWorkcellSettings(ToolPlayer player, UUID requestId, String workcellId) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        JigsawStudioMenuWindow window = windows.get(player.id());
        JigsawStudioMenuState.Workcell workcell = current
                .map(state -> state.workcell(workcellId))
                .orElse(null);
        if (window == null || workcell == null) {
            return;
        }
        renderWorkcellSettings(window, current.orElseThrow(), workcell);
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

    private void resizeVariantAxis(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            String pieceKey,
            DimensionAxis axis,
            int delta
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        JigsawStudioMenuState.Variant variant = variant(workcell, pieceKey);
        if (workcell == null || variant == null || !variant.owned() || variant.dimensions().isEmpty()) {
            stale(player);
            return;
        }
        Optional<JigsawStudioCellDimensions> adjusted = adjustedDimensions(
                variant.dimensions().orElseThrow(), axis, delta);
        if (adjusted.isEmpty()) {
            sendError(player, "That variant size is outside Iris limits.");
            return;
        }
        resizeVariant(player, requestId, workcellId, pieceKey, adjusted.get());
    }

    private void resizeVariant(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            String pieceKey,
            JigsawStudioCellDimensions dimensions
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        JigsawStudioMenuState.Variant variant = variant(workcell, pieceKey);
        if (workcell == null || variant == null || !variant.owned()) {
            stale(player);
            return;
        }
        if (dimensions.width() > workcell.capacity().width()
                || dimensions.height() > workcell.capacity().height()
                || dimensions.depth() > workcell.capacity().depth()) {
            sendError(player, "Increase this workcell's capacity before making the variant larger.");
            return;
        }
        if (actions.resizeVariant(player, workcellId, pieceKey, dimensions)) {
            closeAfterAction(player);
        }
    }

    private void setRequireCaps(ToolPlayer player, UUID requestId, boolean requireCaps) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        if (!current.get().irisExtended()) {
            send(player, "Mandatory caps require Iris compatibility.");
            return;
        }
        if (current.get().requireCaps() == requireCaps) {
            send(player, "Mandatory caps are already "
                    + (requireCaps ? "enabled." : "disabled."));
            return;
        }
        if (actions.setRequireCaps(player, requireCaps)) {
            clearConfirmations(player.id());
            closeAfterAction(player);
        }
    }

    private void duplicateActiveFamily(ToolPlayer player, UUID requestId, String themeKey) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        if (!current.get().irisExtended()) {
            send(player, "Theme sets require Iris compatibility.");
            return;
        }
        if (!nextThemeSetKey(current.get().themeSets()).equals(themeKey)) {
            stale(player);
            return;
        }
        if (actions.duplicateActiveFamily(player, themeKey)) {
            clearConfirmations(player.id());
            closeAfterAction(player);
        }
    }

    private void adjustThemeSetWeight(
            ToolPlayer player,
            UUID requestId,
            JigsawStudioMenuState.ThemeSet expected,
            int delta
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        if (!current.get().irisExtended()) {
            send(player, "Theme weights require Iris compatibility.");
            return;
        }
        JigsawStudioMenuState.ThemeSet themeSet = current.get().themeSet(expected.key());
        if (!expected.equals(themeSet)) {
            stale(player);
            return;
        }
        Optional<Integer> weight = adjustedPositiveValue(themeSet.weight(), delta);
        if (weight.isEmpty()) {
            sendError(player, "Theme weights must remain positive.");
            return;
        }
        if (actions.updateThemeSetWeight(player, themeSet.key(), weight.get())) {
            clearConfirmations(player.id());
            closeAfterAction(player);
        }
    }

    private void flushNow(ToolPlayer player, UUID requestId, String workcellId) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        if (workcell == null) {
            stale(player);
            return;
        }
        if (!workcell.dirty()) {
            send(player, "This workcell has no pending changes to flush.");
            return;
        }
        if (actions.flushAutosave(player, workcellId)) {
            closeAfterAction(player);
        }
    }

    private void goToPreview(ToolPlayer player, UUID requestId) {
        if (matchingState(player, requestId, true).isPresent()
                && actions.goToPreview(player)) {
            closeAfterAction(player);
        }
    }

    private void toggleRotation(ToolPlayer player, UUID requestId, String workcellId) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty() || activeVariant(current.get(), workcellId) == null) {
            return;
        }
        if (actions.toggleVariantRotatable(player, workcellId)) {
            closeAfterAction(player);
        }
    }

    private void adjustVariantRule(
            ToolPlayer player,
            UUID requestId,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant expected,
            RuleField field,
            int delta
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Variant variant = activeVariant(current.get(), workcell.stableId());
        if (variant == null || !variant.owned() || !variant.pieceKey().equals(expected.pieceKey())) {
            stale(player);
            return;
        }
        Optional<JigsawStudioPieceRules> rules = adjustedRules(variant.rules(), field, delta);
        if (rules.isEmpty()) {
            sendError(player, "That piece rule value is outside Iris limits.");
            return;
        }
        updateVariantRules(
                player,
                requestId,
                workcell.stableId(),
                variant.pieceKey(),
                rules.get());
    }

    private void updateVariantRules(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            String pieceKey,
            JigsawStudioPieceRules rules
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        if (!current.get().irisExtended()) {
            send(player, "Piece rules require Iris compatibility.");
            return;
        }
        JigsawStudioMenuState.Variant variant = activeVariant(current.get(), workcellId);
        if (variant == null || !variant.owned() || !variant.pieceKey().equals(pieceKey)) {
            stale(player);
            return;
        }
        if (actions.updateVariantRules(player, workcellId, pieceKey, rules)) {
            clearConfirmations(player.id());
            closeAfterAction(player);
        }
    }

    private void toggleVariantTheme(
            ToolPlayer player,
            UUID requestId,
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioMenuState.Variant expected,
            String themeKey
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        if (!current.get().irisExtended()) {
            send(player, "Theme membership requires Iris compatibility.");
            return;
        }
        JigsawStudioMenuState.Variant variant = activeVariant(current.get(), workcell.stableId());
        if (variant == null
                || !variant.owned()
                || !variant.pieceKey().equals(expected.pieceKey())
                || current.get().themeSet(themeKey) == null) {
            stale(player);
            return;
        }
        List<String> themes = new ArrayList<>(variant.themes());
        if (!themes.remove(themeKey)) {
            themes.add(themeKey);
        }
        if (actions.updateVariantThemes(
                player,
                workcell.stableId(),
                variant.pieceKey(),
                List.copyOf(themes))) {
            clearConfirmations(player.id());
            closeAfterAction(player);
        }
    }

    private void adjustMembershipWeight(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            JigsawStudioMenuState.Membership membership,
            int delta
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty() || !hasMembership(current.get(), workcellId, membership)) {
            stale(player);
            return;
        }
        JigsawStudioMenuState.Variant active = activeVariant(current.get(), workcellId);
        if (active == null) {
            stale(player);
            return;
        }
        if (actions.adjustVariantWeight(
                player,
                workcellId,
                active.pieceKey(),
                membership.poolKey(),
                membership.entryIndex(),
                delta)) {
            clearConfirmations(player.id());
            closeAfterAction(player);
        }
    }

    private void adjustMembershipChance(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            JigsawStudioMenuState.Membership membership,
            int deltaPercentagePoints
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty() || !hasMembership(current.get(), workcellId, membership)) {
            stale(player);
            return;
        }
        if (!current.get().irisExtended()) {
            send(player, "Per-entry chance requires Iris compatibility.");
            return;
        }
        JigsawStudioMenuState.Variant active = activeVariant(current.get(), workcellId);
        if (active == null) {
            stale(player);
            return;
        }
        if (actions.adjustVariantChance(
                player,
                workcellId,
                active.pieceKey(),
                membership.poolKey(),
                membership.entryIndex(),
                deltaPercentagePoints)) {
            clearConfirmations(player.id());
            closeAfterAction(player);
        }
    }

    private void confirmOrUnlink(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            JigsawStudioMenuState.Membership membership,
            int page
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty() || !hasMembership(current.get(), workcellId, membership)) {
            stale(player);
            return;
        }

        UUID playerId = player.id();
        JigsawStudioMenuState.Variant active = activeVariant(current.get(), workcellId);
        if (active == null || !active.owned()) {
            stale(player);
            return;
        }
        if (!isUnlinkConfirmed(playerId, requestId, workcellId, active.pieceKey(), membership)) {
            pendingUnlinks.put(playerId, new PendingUnlink(
                    requestId,
                    workcellId,
                    active.pieceKey(),
                    membership.poolKey(),
                    membership.entryIndex(),
                    System.nanoTime() + DESTRUCTIVE_CONFIRM_NANOS));
            scheduler.onPlayerLater(
                    player,
                    () -> refreshDetails(player, requestId, workcellId, page),
                    DETAILS_REFRESH_DELAY_TICKS,
                    () -> { });
            return;
        }

        if (actions.unlinkVariantMembership(
                player,
                workcellId,
                active.pieceKey(),
                membership.poolKey(),
                membership.entryIndex())) {
            clearConfirmations(playerId);
            closeAfterAction(player);
        }
    }

    private void confirmOrDeleteVariant(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            String pieceKey,
            int page
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        JigsawStudioMenuState.Variant target = variant(workcell, pieceKey);
        if (target == null || !target.owned() || target.active()) {
            stale(player);
            return;
        }

        UUID playerId = player.id();
        if (!isDeleteConfirmed(playerId, requestId, workcellId, pieceKey)) {
            pendingDeletes.put(playerId, new PendingDelete(
                    requestId,
                    workcellId,
                    pieceKey,
                    System.nanoTime() + DESTRUCTIVE_CONFIRM_NANOS));
            scheduler.onPlayerLater(
                    player,
                    () -> refreshDetails(player, requestId, workcellId, pieceKey, page),
                    DETAILS_REFRESH_DELAY_TICKS,
                    () -> { });
            return;
        }

        if (actions.deleteVariant(player, workcellId, pieceKey)) {
            clearConfirmations(playerId);
            closeAfterAction(player);
        }
    }

    private void confirmOrDeleteProject(
            ToolPlayer player,
            UUID requestId,
            String selectedWorkcellId,
            int page
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        UUID playerId = player.id();
        if (!isProjectDeleteConfirmed(playerId, requestId)) {
            pendingProjectDeletes.put(playerId, new PendingProjectDelete(
                    requestId,
                    System.nanoTime() + DESTRUCTIVE_CONFIRM_NANOS));
            scheduler.onPlayerLater(
                    player,
                    () -> refreshStructureSettings(player, requestId, selectedWorkcellId, page),
                    DETAILS_REFRESH_DELAY_TICKS,
                    () -> { });
            return;
        }
        if (actions.deleteProject(player)) {
            clearConfirmations(playerId);
            closeAfterAction(player);
        }
    }

    private void giveTool(ToolPlayer player, UUID requestId, JigsawStudioToolPayload payload) {
        if (matchingState(player, requestId, true).isEmpty()) {
            return;
        }
        if (!payload.requestId().equals(requestId)) {
            stale(player);
            return;
        }
        actions.giveTool(player, payload);
    }

    private void refreshMain(ToolPlayer player, UUID requestId, String workcellId, int page) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        JigsawStudioMenuWindow window = windows.get(player.id());
        if (workcell == null || window == null) {
            stale(player);
            return;
        }
        renderMain(window, current.get(), workcell, page);
    }

    private void refreshDetails(ToolPlayer player, UUID requestId, String workcellId, int page) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Variant active = activeVariant(current.get(), workcellId);
        if (active == null) {
            stale(player);
            return;
        }
        refreshDetails(player, requestId, workcellId, active.pieceKey(), page);
    }

    private void refreshDetails(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            String pieceKey,
            int page
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        JigsawStudioMenuState.Variant variant = variant(workcell, pieceKey);
        JigsawStudioMenuWindow window = windows.get(player.id());
        if (workcell == null || variant == null || window == null) {
            stale(player);
            return;
        }
        renderDetails(window, current.get(), workcell, variant, page);
    }

    private void refreshStructureSettings(
            ToolPlayer player,
            UUID requestId,
            String selectedWorkcellId,
            int page
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuWindow window = windows.get(player.id());
        if (current.get().workcell(selectedWorkcellId) == null || window == null) {
            stale(player);
            return;
        }
        renderStructureSettings(window, current.get(), selectedWorkcellId, page);
    }

    private void refreshVariantSettings(
            ToolPlayer player,
            UUID requestId,
            String workcellId,
            String pieceKey,
            int page
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        JigsawStudioMenuState.Variant variant = variant(workcell, pieceKey);
        JigsawStudioMenuWindow window = windows.get(player.id());
        if (workcell == null || variant == null || window == null) {
            stale(player);
            return;
        }
        renderVariantSettings(window, current.get(), workcell, variant, page);
    }

    private void refreshToolbox(ToolPlayer player, UUID requestId, String workcellId, int page) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        JigsawStudioMenuWindow window = windows.get(player.id());
        if (workcell == null || window == null) {
            stale(player);
            return;
        }
        renderToolbox(window, current.get(), workcell, page);
    }

    private Optional<JigsawStudioMenuState> matchingState(ToolPlayer player, UUID requestId, boolean closeStale) {
        Optional<JigsawStudioMenuState> current = actions.menuState(player);
        if (current.isPresent() && current.get().requestId().equals(requestId)) {
            return current;
        }
        if (closeStale) {
            stale(player);
        }
        return Optional.empty();
    }

    private void stale(ToolPlayer player) {
        closeAfterAction(player);
        sendError(player, "This Jigsaw Studio menu is stale. Open the control chest again.");
    }

    private static void send(ToolPlayer player, String message) {
        player.sender().info(message);
    }

    private static void sendError(ToolPlayer player, String message) {
        player.sender().error(message);
    }

    private void closeAfterAction(ToolPlayer player) {
        scheduler.onPlayerLater(player, () -> close(player), CLOSE_AFTER_ACTION_TICKS, () -> { });
    }

    private boolean isUnlinkConfirmed(
            UUID playerId,
            UUID requestId,
            String workcellId,
            String pieceKey,
            JigsawStudioMenuState.Membership membership
    ) {
        PendingUnlink pending = pendingUnlinks.get(playerId);
        if (pending == null) {
            return false;
        }
        if (pending.expiresAtNanos() < System.nanoTime()) {
            pendingUnlinks.remove(playerId, pending);
            return false;
        }
        return pending.requestId().equals(requestId)
                && pending.workcellId().equals(workcellId)
                && pending.pieceKey().equals(pieceKey)
                && pending.poolKey().equals(membership.poolKey())
                && pending.entryIndex() == membership.entryIndex();
    }

    private boolean isDeleteConfirmed(
            UUID playerId,
            UUID requestId,
            String workcellId,
            String pieceKey
    ) {
        PendingDelete pending = pendingDeletes.get(playerId);
        if (pending == null) {
            return false;
        }
        if (pending.expiresAtNanos() < System.nanoTime()) {
            pendingDeletes.remove(playerId, pending);
            return false;
        }
        return pending.requestId().equals(requestId)
                && pending.workcellId().equals(workcellId)
                && pending.pieceKey().equals(pieceKey);
    }

    private boolean isProjectDeleteConfirmed(UUID playerId, UUID requestId) {
        PendingProjectDelete pending = pendingProjectDeletes.get(playerId);
        if (pending == null) {
            return false;
        }
        if (pending.expiresAtNanos() < System.nanoTime()) {
            pendingProjectDeletes.remove(playerId, pending);
            return false;
        }
        return pending.requestId().equals(requestId);
    }

    private void purgeExpiredConfirmations(UUID playerId) {
        PendingUnlink unlink = pendingUnlinks.get(playerId);
        if (unlink != null && unlink.expiresAtNanos() < System.nanoTime()) {
            pendingUnlinks.remove(playerId, unlink);
        }
        PendingDelete delete = pendingDeletes.get(playerId);
        if (delete != null && delete.expiresAtNanos() < System.nanoTime()) {
            pendingDeletes.remove(playerId, delete);
        }
        PendingProjectDelete projectDelete = pendingProjectDeletes.get(playerId);
        if (projectDelete != null && projectDelete.expiresAtNanos() < System.nanoTime()) {
            pendingProjectDeletes.remove(playerId, projectDelete);
        }
    }

    private void clearConfirmations(UUID playerId) {
        pendingUnlinks.remove(playerId);
        pendingDeletes.remove(playerId);
        pendingProjectDeletes.remove(playerId);
    }

    private static boolean hasMembership(
            JigsawStudioMenuState state,
            String workcellId,
            JigsawStudioMenuState.Membership expected
    ) {
        JigsawStudioMenuState.Variant active = activeVariant(state, workcellId);
        if (active == null) {
            return false;
        }
        for (JigsawStudioMenuState.Membership membership : active.memberships()) {
            if (membership.poolKey().equals(expected.poolKey())
                    && membership.entryIndex() == expected.entryIndex()) {
                return true;
            }
        }
        return false;
    }

    private static JigsawStudioMenuState.Variant activeVariant(
            JigsawStudioMenuState state,
            String workcellId
    ) {
        JigsawStudioMenuState.Workcell workcell = state.workcell(workcellId);
        return workcell == null ? null : workcell.activeVariant();
    }

    private static JigsawStudioMenuState.Variant variant(
            JigsawStudioMenuState.Workcell workcell,
            String pieceKey
    ) {
        if (workcell == null || pieceKey == null) {
            return null;
        }
        for (JigsawStudioMenuState.Variant variant : workcell.variants()) {
            if (variant.pieceKey().equals(pieceKey)) {
                return variant;
            }
        }
        return null;
    }

    static String nextThemeSetKey(List<JigsawStudioMenuState.ThemeSet> themeSets) {
        List<JigsawStudioMenuState.ThemeSet> values = List.copyOf(Objects.requireNonNull(
                themeSets,
                "Jigsaw Studio theme sets"));
        int candidate = 1;
        while (candidate > 0) {
            String key = "variant-" + candidate;
            boolean present = false;
            for (JigsawStudioMenuState.ThemeSet themeSet : values) {
                if (Objects.requireNonNull(themeSet, "Jigsaw Studio theme set").key().equals(key)) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                return key;
            }
            candidate = Math.incrementExact(candidate);
        }
        throw new IllegalStateException("Jigsaw Studio cannot allocate another numbered theme set");
    }

    static String themeSelectionPercent(
            List<JigsawStudioMenuState.ThemeSet> themeSets,
            JigsawStudioMenuState.ThemeSet target
    ) {
        List<JigsawStudioMenuState.ThemeSet> activeThemeSets = Objects.requireNonNull(
                themeSets,
                "Jigsaw Studio theme sets");
        JigsawStudioMenuState.ThemeSet activeTarget = Objects.requireNonNull(
                target,
                "Jigsaw Studio target theme set");
        int totalWeight = 0;
        for (JigsawStudioMenuState.ThemeSet themeSet : activeThemeSets) {
            totalWeight = Math.addExact(totalWeight, Objects.requireNonNull(
                    themeSet,
                    "Jigsaw Studio theme set").weight());
        }
        if (totalWeight < 1) {
            return "0.0%";
        }
        return String.format(
                Locale.ROOT,
                "%.1f%%",
                activeTarget.weight() * 100.0D / totalWeight);
    }

    static Optional<Integer> adjustedPositiveValue(int value, int delta) {
        if (value < 1 || delta == 0) {
            throw new IllegalArgumentException("Jigsaw Studio positive value adjustment is invalid");
        }
        try {
            int adjusted = Math.addExact(value, delta);
            return adjusted < 1 ? Optional.empty() : Optional.of(adjusted);
        } catch (ArithmeticException exception) {
            return Optional.empty();
        }
    }

    static Optional<JigsawStudioPieceRules> adjustedRules(
            JigsawStudioPieceRules rules,
            RuleField field,
            int delta
    ) {
        JigsawStudioPieceRules current = Objects.requireNonNull(rules, "Jigsaw Studio piece rules");
        RuleField target = Objects.requireNonNull(field, "Jigsaw Studio piece rule field");
        if (delta == 0) {
            throw new IllegalArgumentException("Jigsaw Studio piece rule delta cannot be zero");
        }
        try {
            int minimumDepth = current.minimumDepth();
            int maximumDepth = current.maximumDepth();
            int minimumPlacements = current.minimumPlacements();
            int maximumPlacements = current.maximumPlacements();
            switch (target) {
                case MINIMUM_DEPTH -> minimumDepth = Math.addExact(minimumDepth, delta);
                case MAXIMUM_DEPTH -> maximumDepth = Math.addExact(maximumDepth, delta);
                case MINIMUM_PLACEMENTS -> minimumPlacements = Math.addExact(minimumPlacements, delta);
                case MAXIMUM_PLACEMENTS -> {
                    Optional<Integer> adjustedMaximum = adjustedMaximumPlacements(maximumPlacements, delta);
                    if (adjustedMaximum.isEmpty()) {
                        return Optional.empty();
                    }
                    maximumPlacements = adjustedMaximum.get();
                }
            }
            return Optional.of(new JigsawStudioPieceRules(
                    minimumDepth,
                    maximumDepth,
                    minimumPlacements,
                    maximumPlacements,
                    current.terminal()));
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    static JigsawStudioPieceRules withTerminal(JigsawStudioPieceRules rules, boolean terminal) {
        JigsawStudioPieceRules current = Objects.requireNonNull(rules, "Jigsaw Studio piece rules");
        return new JigsawStudioPieceRules(
                current.minimumDepth(),
                current.maximumDepth(),
                current.minimumPlacements(),
                current.maximumPlacements(),
                terminal);
    }

    private static Optional<Integer> adjustedMaximumPlacements(int value, int delta) {
        if (value == 0) {
            if (delta > 0) {
                return Optional.empty();
            }
            int adjusted = Math.addExact(513, delta);
            return adjusted < 1 ? Optional.empty() : Optional.of(adjusted);
        }
        int adjusted = Math.addExact(value, delta);
        if (adjusted > 512) {
            return Optional.of(0);
        }
        return adjusted < 1 ? Optional.empty() : Optional.of(adjusted);
    }

    static Optional<JigsawStudioCellDimensions> adjustedDimensions(
            JigsawStudioCellDimensions dimensions,
            DimensionAxis axis,
            int delta
    ) {
        JigsawStudioCellDimensions current = Objects.requireNonNull(
                dimensions,
                "Jigsaw Studio workcell dimensions");
        DimensionAxis target = Objects.requireNonNull(axis, "Jigsaw Studio workcell dimension axis");
        if (delta == 0) {
            throw new IllegalArgumentException("Jigsaw Studio workcell dimension delta cannot be zero");
        }
        try {
            int width = target == DimensionAxis.WIDTH
                    ? Math.addExact(current.width(), delta)
                    : current.width();
            int height = target == DimensionAxis.HEIGHT
                    ? Math.addExact(current.height(), delta)
                    : current.height();
            int depth = target == DimensionAxis.DEPTH
                    ? Math.addExact(current.depth(), delta)
                    : current.depth();
            return Optional.of(new JigsawStudioCellDimensions(width, height, depth));
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    static JigsawStudioMenuState.Workcell withCapacity(
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioCellDimensions capacity
    ) {
        JigsawStudioMenuState.Workcell source = Objects.requireNonNull(
                workcell,
                "Jigsaw Studio menu workcell");
        return new JigsawStudioMenuState.Workcell(
                source.stableId(),
                source.canonicalName(),
                source.displayName(),
                Objects.requireNonNull(capacity, "Jigsaw Studio staged workcell capacity"),
                source.enabled(),
                source.activeVariantKey(),
                source.dirty(),
                source.saving(),
                source.loading(),
                source.connectorsVisible(),
                source.variants());
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

    private static void addToolBindingLore(JigsawStudioMenuElement element, JigsawStudioToolPayload payload) {
        if (!payload.workcellId().isEmpty()) {
            element.addLore("Workcell: " + safe(payload.workcellId()));
        }
        if (!payload.pieceKey().isEmpty()) {
            element.addLore("Variant: " + safe(payload.pieceKey()));
        }
        if (!payload.poolKey().isEmpty()) {
            element.addLore("Pool: " + safe(payload.poolKey())
                    + (payload.entryIndex() < 0 ? "" : " [" + payload.entryIndex() + "]"));
        }
        if (payload.amount() != 0) {
            element.addLore("Amount: " + payload.amount());
        }
    }

    static int pageCount(int itemCount, int pageSize) {
        if (itemCount < 0 || pageSize < 1) {
            throw new IllegalArgumentException("Jigsaw Studio menu page bounds are invalid");
        }
        return Math.max(1, (itemCount + pageSize - 1) / pageSize);
    }

    static int clampPage(int requestedPage, int itemCount, int pageSize) {
        int maximum = pageCount(itemCount, pageSize) - 1;
        return Math.max(0, Math.min(requestedPage, maximum));
    }

    static <T> List<T> page(List<T> items, int requestedPage, int pageSize) {
        List<T> values = List.copyOf(Objects.requireNonNull(items, "Jigsaw Studio menu page items"));
        int page = clampPage(requestedPage, values.size(), pageSize);
        int start = page * pageSize;
        int end = Math.min(values.size(), start + pageSize);
        return values.subList(start, end);
    }

    private static JigsawStudioMenuElement evaluationElement(JigsawStudioMenuState.Evaluation evaluation) {
        JigsawStudioMenuElement element = element(
                "evaluation",
                evaluationMaterial(evaluation.state()),
                evaluationName(evaluation.state()));
        element.addLore("Evaluation updates automatically.");
        element.addLore("Seed: " + evaluation.seed());
        if (evaluation.generation() > 0L) {
            element.addLore("Generation: " + evaluation.generation());
        }
        if (!evaluation.selectedTheme().isEmpty()) {
            element.addLore("Theme: " + safe(evaluation.selectedTheme()));
        }
        element.addLore("Pieces: " + evaluation.pieceCount());
        if (!evaluation.detail().isEmpty()) {
            element.addLore(safe(evaluation.detail()));
        }
        return element;
    }

    static JigsawStudioMenuIcon evaluationMaterial(JigsawStudioEvaluationState state) {
        return switch (state) {
            case PENDING -> JigsawStudioMenuIcon.CLOCK;
            case VALID -> JigsawStudioMenuIcon.EMERALD;
            case WARNING -> JigsawStudioMenuIcon.YELLOW_DYE;
            case INVALID -> JigsawStudioMenuIcon.RED_DYE;
            case STALE -> JigsawStudioMenuIcon.GRAY_DYE;
        };
    }

    private static String evaluationName(JigsawStudioEvaluationState state) {
        return switch (state) {
            case PENDING -> "Evaluation Pending";
            case VALID -> "Ready to Assemble";
            case WARNING -> "Ready with Warnings";
            case INVALID -> "Assembly Blocked";
            case STALE -> "Evaluation Stale";
        };
    }

    private static JigsawStudioMenuIcon workcellMaterial(JigsawStudioMenuState.Workcell workcell, boolean selected) {
        if (!workcell.enabled()) {
            return JigsawStudioMenuIcon.GRAY_WOOL;
        }
        if (workcell.loading() || workcell.saving()) {
            return JigsawStudioMenuIcon.YELLOW_WOOL;
        }
        if (workcell.dirty()) {
            return JigsawStudioMenuIcon.ORANGE_WOOL;
        }
        return selected ? JigsawStudioMenuIcon.LIME_WOOL : JigsawStudioMenuIcon.LIGHT_GRAY_WOOL;
    }

    static JigsawStudioMenuIcon variantMaterial(JigsawStudioMenuState.Variant variant) {
        if (variant.active()) {
            return JigsawStudioMenuIcon.JIGSAW;
        }
        return variant.owned() ? JigsawStudioMenuIcon.PAPER : JigsawStudioMenuIcon.GRAY_DYE;
    }

    static String workcellStatus(JigsawStudioMenuState.Workcell workcell) {
        if (workcell.loading()) {
            return "Loading variant";
        }
        if (workcell.saving()) {
            return "Autosave in progress";
        }
        if (workcell.dirty()) {
            return "Autosave pending";
        }
        return "Autosaved";
    }

    private static String dimensions(JigsawStudioCellDimensions dimensions) {
        return dimensions.width() + "x" + dimensions.height() + "x" + dimensions.depth();
    }

    private static String themes(List<String> themes) {
        return themes.isEmpty() ? "None" : safe(String.join(", ", themes));
    }

    private static String maximumPlacements(int maximum) {
        return maximum == 0 ? "Unlimited" : Integer.toString(maximum);
    }

    private static String chance(double chance) {
        return String.format(Locale.ROOT, "%.1f%%", chance * 100D);
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private static String compatibilityName(JigsawStudioCompatibilityTarget target) {
        return switch (target) {
            case IRIS_EXTENDED -> "Iris Extended";
            case VANILLA_PORTABLE -> "Vanilla Portable";
        };
    }

    private static JigsawStudioMenuElement element(String id, JigsawStudioMenuIcon material, String name) {
        return new JigsawStudioMenuElement(id)
                .setIcon(material)
                .setName(name);
    }

    private static String title(String structureKey) {
        String value = "Iris Jigsaw: " + safe(displayKey(structureKey));
        return value.length() > 32 ? value.substring(0, 32) : value;
    }

    private static String displayKey(String resourceKey) {
        int separator = resourceKey.lastIndexOf('/');
        return separator < 0 ? resourceKey : resourceKey.substring(separator + 1);
    }

    private static String safe(String value) {
        String normalized = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        return LEGACY_COLOR.matcher(normalized).replaceAll("")
                .replace("&", "＆")
                .replace("<", "‹")
                .replace(">", "›");
    }

    public interface Actions {
        Optional<JigsawStudioMenuState> menuState(ToolPlayer player);

        boolean selectWorkcell(ToolPlayer player, String workcellId);

        boolean teleportToWorkcell(ToolPlayer player, String workcellId);

        boolean setConnectorBlocksVisible(ToolPlayer player, String workcellId, boolean visible);

        boolean resetConnectorBlocks(ToolPlayer player, String workcellId);

        boolean undoAutosave(ToolPlayer player);

        boolean switchVariant(ToolPlayer player, String workcellId, String pieceKey, boolean discardDirty);

        boolean createVariant(ToolPlayer player, String workcellId, boolean duplicateActive);

        boolean setWorkcellEnabled(ToolPlayer player, String workcellId, boolean enabled);

        boolean updateWorkcellDimensions(
                ToolPlayer player,
                String workcellId,
                JigsawStudioCellDimensions dimensions
        );

        boolean updateWorkcellDisplayName(ToolPlayer player, String workcellId, String displayName);

        boolean setRequireCaps(ToolPlayer player, boolean requireCaps);

        boolean duplicateActiveFamily(ToolPlayer player, String themeKey);

        boolean updateThemeSetWeight(ToolPlayer player, String themeKey, int weight);

        boolean flushAutosave(ToolPlayer player, String workcellId);

        boolean goToPreview(ToolPlayer player);

        boolean toggleVariantRotatable(ToolPlayer player, String workcellId);

        boolean expandVariantToCell(ToolPlayer player, String workcellId);

        boolean resizeVariant(
                ToolPlayer player,
                String workcellId,
                String pieceKey,
                JigsawStudioCellDimensions dimensions
        );

        boolean updateVariantDisplayName(
                ToolPlayer player,
                String workcellId,
                String pieceKey,
                String displayName
        );

        boolean updateVariantThemes(
                ToolPlayer player,
                String workcellId,
                String pieceKey,
                List<String> themes
        );

        boolean updateVariantRules(
                ToolPlayer player,
                String workcellId,
                String pieceKey,
                JigsawStudioPieceRules rules
        );

        boolean adjustVariantWeight(
                ToolPlayer player,
                String workcellId,
                String pieceKey,
                String poolKey,
                int entryIndex,
                int delta
        );

        boolean adjustVariantChance(
                ToolPlayer player,
                String workcellId,
                String pieceKey,
                String poolKey,
                int entryIndex,
                int deltaPercentagePoints
        );

        boolean unlinkVariantMembership(
                ToolPlayer player,
                String workcellId,
                String pieceKey,
                String poolKey,
                int entryIndex
        );

        boolean deleteVariant(ToolPlayer player, String workcellId, String pieceKey);

        boolean deleteProject(ToolPlayer player);

        boolean giveTool(ToolPlayer player, JigsawStudioToolPayload payload);
    }

    enum RuleField {
        MINIMUM_DEPTH("Minimum Depth", JigsawStudioMenuIcon.LIGHT_BLUE_DYE, RULE_SHIFT_STEP) {
            @Override
            int value(JigsawStudioPieceRules rules) {
                return rules.minimumDepth();
            }
        },
        MAXIMUM_DEPTH("Maximum Depth", JigsawStudioMenuIcon.BLUE_DYE, RULE_SHIFT_STEP) {
            @Override
            int value(JigsawStudioPieceRules rules) {
                return rules.maximumDepth();
            }
        },
        MINIMUM_PLACEMENTS("Minimum Placements", JigsawStudioMenuIcon.TARGET, PLACEMENT_RULE_SHIFT_STEP) {
            @Override
            int value(JigsawStudioPieceRules rules) {
                return rules.minimumPlacements();
            }
        },
        MAXIMUM_PLACEMENTS("Maximum Placements", JigsawStudioMenuIcon.GREEN_DYE, PLACEMENT_RULE_SHIFT_STEP) {
            @Override
            int value(JigsawStudioPieceRules rules) {
                return rules.maximumPlacements();
            }

            @Override
            String displayValue(int value) {
                return maximumPlacements(value);
            }
        };

        private final String displayName;
        private final JigsawStudioMenuIcon material;
        private final int shiftStep;

        RuleField(String displayName, JigsawStudioMenuIcon material, int shiftStep) {
            this.displayName = displayName;
            this.material = material;
            this.shiftStep = shiftStep;
        }

        String displayName() {
            return displayName;
        }

        JigsawStudioMenuIcon material() {
            return material;
        }

        int shiftStep() {
            return shiftStep;
        }

        String displayValue(int value) {
            return Integer.toString(value);
        }

        abstract int value(JigsawStudioPieceRules rules);
    }

    enum DimensionAxis {
        WIDTH("Width") {
            @Override
            int value(JigsawStudioCellDimensions dimensions) {
                return dimensions.width();
            }
        },
        HEIGHT("Height") {
            @Override
            int value(JigsawStudioCellDimensions dimensions) {
                return dimensions.height();
            }
        },
        DEPTH("Depth") {
            @Override
            int value(JigsawStudioCellDimensions dimensions) {
                return dimensions.depth();
            }
        };

        private final String displayName;

        DimensionAxis(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }

        abstract int value(JigsawStudioCellDimensions dimensions);
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

    private record PendingUnlink(
            UUID requestId,
            String workcellId,
            String pieceKey,
            String poolKey,
            int entryIndex,
            long expiresAtNanos
    ) {
    }

    private record PendingDelete(
            UUID requestId,
            String workcellId,
            String pieceKey,
            long expiresAtNanos
    ) {
    }

    private record PendingProjectDelete(UUID requestId, long expiresAtNanos) {
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
}
