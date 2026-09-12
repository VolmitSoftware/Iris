package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.DimensionAxis;
import art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.RuleField;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.inventorygui.UIPaneDecorator;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.WindowResolution;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.activeVariant;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.adjustedDimensions;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.adjustedPositiveValue;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.adjustedRules;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.hasMembership;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.nextThemeSetKey;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.title;
import static art.arcane.iris.studio.jigsaw.JigsawStudioMenuFormat.variant;

public final class JigsawStudioMenuController {
    static final int VARIANTS_PER_PAGE = 28;
    static final int MEMBERSHIPS_PER_PAGE = 28;
    static final int THEME_SETS_PER_PAGE = 21;
    static final int TOOLS_PER_PAGE = 28;
    static final int CHANCE_STEP_PERCENTAGE_POINTS = 5;
    static final int RULE_SHIFT_STEP = 5;
    static final int PLACEMENT_RULE_SHIFT_STEP = 16;

    private static final long DESTRUCTIVE_CONFIRM_NANOS = 10_000_000_000L;
    static final int[] GRID_POSITIONS = {-3, -2, -1, 0, 1, 2, 3};

    private final JavaPlugin plugin;
    final JigsawStudioMenuActions actions;
    final Map<UUID, UIWindow> windows = new ConcurrentHashMap<>();
    private final Map<UUID, PendingUnlink> pendingUnlinks = new ConcurrentHashMap<>();
    private final Map<UUID, PendingDelete> pendingDeletes = new ConcurrentHashMap<>();
    private final Map<UUID, PendingProjectDelete> pendingProjectDeletes = new ConcurrentHashMap<>();
    private final JigsawStudioMainMenu mainMenu;
    private final JigsawStudioWorkcellMenu workcellMenu;
    private final JigsawStudioStructureMenu structureMenu;
    private final JigsawStudioDetailsMenu detailsMenu;
    private final JigsawStudioVariantMenu variantMenu;
    private final JigsawStudioToolboxMenu toolboxMenu;

    public JigsawStudioMenuController(JavaPlugin plugin, JigsawStudioMenuActions actions) {
        this.plugin = Objects.requireNonNull(plugin, "Jigsaw Studio menu plugin");
        this.actions = Objects.requireNonNull(actions, "Jigsaw Studio menu actions");
        mainMenu = new JigsawStudioMainMenu(this);
        workcellMenu = new JigsawStudioWorkcellMenu(this);
        structureMenu = new JigsawStudioStructureMenu(this);
        detailsMenu = new JigsawStudioDetailsMenu(this);
        variantMenu = new JigsawStudioVariantMenu(this);
        toolboxMenu = new JigsawStudioToolboxMenu(this);
    }

    public boolean open(Player player) {
        if (player == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> open(player));
        }

        Optional<JigsawStudioMenuState> available = actions.menuState(player);
        if (available.isEmpty()) {
            close(player);
            send(player, ChatColor.RED + "Iris Jigsaw Studio is not active in this world.");
            return false;
        }
        JigsawStudioMenuState state = available.get();
        if (state.workcells().isEmpty()) {
            close(player);
            send(player, ChatColor.RED + "This Jigsaw Studio has no workcells.");
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
        UUID playerId = player.getUniqueId();
        UIWindow window = new UIWindow(plugin, player);
        window.setResolution(WindowResolution.W9_H6);
        window.setViewportHeight(6);
        window.setDecorator(new UIPaneDecorator(Material.BLACK_STAINED_GLASS_PANE));
        window.setTitle(title(state.structureKey()));
        window.onClosed(closed -> {
            windows.remove(playerId, window);
            pendingUnlinks.remove(playerId);
            pendingDeletes.remove(playerId);
            pendingProjectDeletes.remove(playerId);
            workcellMenu.clearStagedResize(playerId);
        });
        windows.put(playerId, window);
        pendingUnlinks.remove(playerId);
        pendingDeletes.remove(playerId);
        pendingProjectDeletes.remove(playerId);
        workcellMenu.clearStagedResize(playerId);
        mainMenu.render(window, state, selected, 0);
        window.open();
        return true;
    }

    public boolean openWorkcellSettings(Player player, String workcellId) {
        if (player == null || workcellId == null || workcellId.isBlank()) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> openWorkcellSettings(player, workcellId));
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

    public boolean openVariantSettings(Player player, String workcellId, String pieceKey) {
        if (player == null
                || workcellId == null
                || workcellId.isBlank()
                || pieceKey == null
                || pieceKey.isBlank()) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> openVariantSettings(player, workcellId, pieceKey));
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
            send(player, ChatColor.YELLOW
                    + "Vanilla-portable pieces cannot encode Iris theme or piece-rule metadata.");
            close(player);
            return false;
        }
        openVariantSettings(player, current.get().requestId(), workcellId, pieceKey, 0);
        return true;
    }

    public boolean openVariantSizeSettings(Player player, String workcellId, String pieceKey) {
        if (player == null || workcellId == null || workcellId.isBlank()
                || pieceKey == null || pieceKey.isBlank()) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> openVariantSizeSettings(player, workcellId, pieceKey));
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
        UIWindow window = windows.get(player.getUniqueId());
        if (window == null) {
            return false;
        }
        workcellMenu.renderSize(window, current.get(), workcell, variant);
        return true;
    }

    public boolean openStructureSettings(Player player) {
        if (player == null) {
            return false;
        }
        if (!J.isOwnedByCurrentRegion(player)) {
            return J.runEntity(player, () -> openStructureSettings(player));
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

    public void close(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        UIWindow window = windows.remove(playerId);
        pendingUnlinks.remove(playerId);
        pendingDeletes.remove(playerId);
        pendingProjectDeletes.remove(playerId);
        if (window == null) {
            return;
        }
        if (J.isOwnedByCurrentRegion(player)) {
            window.close();
            return;
        }
        J.runEntity(player, window::close);
    }

    public void closeAll() {
        List<UIWindow> activeWindows = new ArrayList<>(windows.values());
        windows.clear();
        pendingUnlinks.clear();
        pendingDeletes.clear();
        pendingProjectDeletes.clear();
        workcellMenu.clearStagedResizes();
        for (UIWindow window : activeWindows) {
            Player player = window.getViewer();
            if (J.isOwnedByCurrentRegion(player)) {
                window.close();
            } else {
                J.runEntity(player, window::close);
            }
        }
    }

    void selectWorkcell(Player player, UUID requestId, String workcellId) {
        if (matchingState(player, requestId, true).isEmpty()) {
            return;
        }
        if (actions.teleportToWorkcell(player, workcellId)) {
            clearConfirmations(player.getUniqueId());
            closeAfterAction(player);
        }
    }

    void toggleConnectorBlocks(
            Player player,
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

    void resetConnectorBlocks(Player player, UUID requestId, String workcellId) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty() || current.get().workcell(workcellId) == null) {
            return;
        }
        if (actions.resetConnectorBlocks(player, workcellId)) {
            closeAfterAction(player);
        }
    }

    void undoAutosave(Player player, UUID requestId) {
        if (matchingState(player, requestId, true).isEmpty()) {
            return;
        }
        if (actions.undoAutosave(player)) {
            closeAfterAction(player);
        }
    }

    void switchVariant(Player player, UUID requestId, String workcellId, String pieceKey) {
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
            send(player, ChatColor.YELLOW + "That variant is already loaded. Right-click it for details.");
            return;
        }
        if (actions.switchVariant(player, workcellId, pieceKey, false)) {
            closeAfterAction(player);
        }
    }

    void openDetails(
            Player player,
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
        UIWindow window = windows.get(player.getUniqueId());
        if (window != null) {
            detailsMenu.render(window, current.get(), workcell, variant, page);
        }
    }

    void openWorkcellSettings(Player player, UUID requestId, String workcellId) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        UIWindow window = windows.get(player.getUniqueId());
        if (workcell == null || window == null) {
            stale(player);
            return;
        }
        workcellMenu.render(window, current.get(), workcell);
    }

    void openStructureSettings(
            Player player,
            UUID requestId,
            String selectedWorkcellId,
            int page
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        UIWindow window = windows.get(player.getUniqueId());
        if (current.get().workcell(selectedWorkcellId) == null || window == null) {
            stale(player);
            return;
        }
        structureMenu.render(window, current.get(), selectedWorkcellId, page);
    }

    void openVariantSettings(
            Player player,
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
        UIWindow window = windows.get(player.getUniqueId());
        if (workcell == null || variant == null || window == null) {
            stale(player);
            return;
        }
        if (!variant.active() || !variant.owned()) {
            send(player, ChatColor.YELLOW + "Load an owned variant before editing its rules.");
            return;
        }
        if (!current.get().irisExtended()) {
            send(player, ChatColor.YELLOW
                    + "Vanilla-portable pieces cannot encode Iris theme or piece-rule metadata.");
            return;
        }
        variantMenu.render(window, current.get(), workcell, variant, page);
    }

    void openVariantSizeSettings(
            Player player,
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
        UIWindow window = windows.get(player.getUniqueId());
        if (workcell == null || variant == null || !variant.owned()
                || variant.dimensions().isEmpty() || window == null) {
            stale(player);
            return;
        }
        workcellMenu.renderSize(window, current.get(), workcell, variant);
    }

    void openToolbox(Player player, UUID requestId, String workcellId, int page) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        UIWindow window = windows.get(player.getUniqueId());
        if (workcell == null || window == null) {
            stale(player);
            return;
        }
        toolboxMenu.render(window, current.get(), workcell, page);
    }

    void createVariant(
            Player player,
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

    void setWorkcellEnabled(
            Player player,
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

    void resizeVariantAxis(
            Player player,
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
            send(player, ChatColor.RED + "That variant size is outside Iris limits.");
            return;
        }
        resizeVariant(player, requestId, workcellId, pieceKey, adjusted.get());
    }

    void resizeVariant(
            Player player,
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
            send(player, ChatColor.RED + "Increase this workcell's capacity before making the variant larger.");
            return;
        }
        if (actions.resizeVariant(player, workcellId, pieceKey, dimensions)) {
            closeAfterAction(player);
        }
    }

    void setRequireCaps(Player player, UUID requestId, boolean requireCaps) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        if (!current.get().irisExtended()) {
            send(player, ChatColor.YELLOW + "Mandatory caps require Iris compatibility.");
            return;
        }
        if (current.get().requireCaps() == requireCaps) {
            send(player, ChatColor.YELLOW + "Mandatory caps are already "
                    + (requireCaps ? "enabled." : "disabled."));
            return;
        }
        if (actions.setRequireCaps(player, requireCaps)) {
            clearConfirmations(player.getUniqueId());
            closeAfterAction(player);
        }
    }

    void duplicateActiveFamily(Player player, UUID requestId, String themeKey) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        if (!current.get().irisExtended()) {
            send(player, ChatColor.YELLOW + "Theme sets require Iris compatibility.");
            return;
        }
        if (!nextThemeSetKey(current.get().themeSets()).equals(themeKey)) {
            stale(player);
            return;
        }
        if (actions.duplicateActiveFamily(player, themeKey)) {
            clearConfirmations(player.getUniqueId());
            closeAfterAction(player);
        }
    }

    void adjustThemeSetWeight(
            Player player,
            UUID requestId,
            JigsawStudioMenuState.ThemeSet expected,
            int delta
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        if (!current.get().irisExtended()) {
            send(player, ChatColor.YELLOW + "Theme weights require Iris compatibility.");
            return;
        }
        JigsawStudioMenuState.ThemeSet themeSet = current.get().themeSet(expected.key());
        if (!expected.equals(themeSet)) {
            stale(player);
            return;
        }
        Optional<Integer> weight = adjustedPositiveValue(themeSet.weight(), delta);
        if (weight.isEmpty()) {
            send(player, ChatColor.RED + "Theme weights must remain positive.");
            return;
        }
        if (actions.updateThemeSetWeight(player, themeSet.key(), weight.get())) {
            clearConfirmations(player.getUniqueId());
            closeAfterAction(player);
        }
    }

    void flushNow(Player player, UUID requestId, String workcellId) {
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
            send(player, ChatColor.YELLOW + "This workcell has no pending changes to flush.");
            return;
        }
        if (actions.flushAutosave(player, workcellId)) {
            closeAfterAction(player);
        }
    }

    void goToPreview(Player player, UUID requestId) {
        if (matchingState(player, requestId, true).isPresent()
                && actions.goToPreview(player)) {
            closeAfterAction(player);
        }
    }

    void toggleRotation(Player player, UUID requestId, String workcellId) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty() || activeVariant(current.get(), workcellId) == null) {
            return;
        }
        if (actions.toggleVariantRotatable(player, workcellId)) {
            closeAfterAction(player);
        }
    }

    void adjustVariantRule(
            Player player,
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
            send(player, ChatColor.RED + "That piece rule value is outside Iris limits.");
            return;
        }
        updateVariantRules(
                player,
                requestId,
                workcell.stableId(),
                variant.pieceKey(),
                rules.get());
    }

    void updateVariantRules(
            Player player,
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
            send(player, ChatColor.YELLOW + "Piece rules require Iris compatibility.");
            return;
        }
        JigsawStudioMenuState.Variant variant = activeVariant(current.get(), workcellId);
        if (variant == null || !variant.owned() || !variant.pieceKey().equals(pieceKey)) {
            stale(player);
            return;
        }
        if (actions.updateVariantRules(player, workcellId, pieceKey, rules)) {
            clearConfirmations(player.getUniqueId());
            closeAfterAction(player);
        }
    }

    void toggleVariantTheme(
            Player player,
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
            send(player, ChatColor.YELLOW + "Theme membership requires Iris compatibility.");
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
            clearConfirmations(player.getUniqueId());
            closeAfterAction(player);
        }
    }

    void adjustMembershipWeight(
            Player player,
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
            clearConfirmations(player.getUniqueId());
            closeAfterAction(player);
        }
    }

    void adjustMembershipChance(
            Player player,
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
            send(player, ChatColor.YELLOW + "Per-entry chance requires Iris compatibility.");
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
            clearConfirmations(player.getUniqueId());
            closeAfterAction(player);
        }
    }

    void confirmOrUnlink(
            Player player,
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

        UUID playerId = player.getUniqueId();
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
            J.runEntity(player, () -> refreshDetails(player, requestId, workcellId, page), 1);
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

    void confirmOrDeleteVariant(
            Player player,
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

        UUID playerId = player.getUniqueId();
        if (!isDeleteConfirmed(playerId, requestId, workcellId, pieceKey)) {
            pendingDeletes.put(playerId, new PendingDelete(
                    requestId,
                    workcellId,
                    pieceKey,
                    System.nanoTime() + DESTRUCTIVE_CONFIRM_NANOS));
            J.runEntity(player, () -> refreshDetails(
                    player, requestId, workcellId, pieceKey, page), 1);
            return;
        }

        if (actions.deleteVariant(player, workcellId, pieceKey)) {
            clearConfirmations(playerId);
            closeAfterAction(player);
        }
    }

    void confirmOrDeleteProject(
            Player player,
            UUID requestId,
            String selectedWorkcellId,
            int page
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!isProjectDeleteConfirmed(playerId, requestId)) {
            pendingProjectDeletes.put(playerId, new PendingProjectDelete(
                    requestId,
                    System.nanoTime() + DESTRUCTIVE_CONFIRM_NANOS));
            J.runEntity(player, () -> refreshStructureSettings(
                    player, requestId, selectedWorkcellId, page), 1);
            return;
        }
        if (actions.deleteProject(player)) {
            clearConfirmations(playerId);
            closeAfterAction(player);
        }
    }

    void giveTool(Player player, UUID requestId, JigsawStudioToolPayload payload) {
        if (matchingState(player, requestId, true).isEmpty()) {
            return;
        }
        if (!payload.requestId().equals(requestId)) {
            stale(player);
            return;
        }
        actions.giveTool(player, payload);
    }

    void refreshMain(Player player, UUID requestId, String workcellId, int page) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        UIWindow window = windows.get(player.getUniqueId());
        if (workcell == null || window == null) {
            stale(player);
            return;
        }
        mainMenu.render(window, current.get(), workcell, page);
    }

    void refreshDetails(Player player, UUID requestId, String workcellId, int page) {
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

    void refreshDetails(
            Player player,
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
        UIWindow window = windows.get(player.getUniqueId());
        if (workcell == null || variant == null || window == null) {
            stale(player);
            return;
        }
        detailsMenu.render(window, current.get(), workcell, variant, page);
    }

    void refreshStructureSettings(
            Player player,
            UUID requestId,
            String selectedWorkcellId,
            int page
    ) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        UIWindow window = windows.get(player.getUniqueId());
        if (current.get().workcell(selectedWorkcellId) == null || window == null) {
            stale(player);
            return;
        }
        structureMenu.render(window, current.get(), selectedWorkcellId, page);
    }

    void refreshVariantSettings(
            Player player,
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
        UIWindow window = windows.get(player.getUniqueId());
        if (workcell == null || variant == null || window == null) {
            stale(player);
            return;
        }
        variantMenu.render(window, current.get(), workcell, variant, page);
    }

    void refreshToolbox(Player player, UUID requestId, String workcellId, int page) {
        Optional<JigsawStudioMenuState> current = matchingState(player, requestId, true);
        if (current.isEmpty()) {
            return;
        }
        JigsawStudioMenuState.Workcell workcell = current.get().workcell(workcellId);
        UIWindow window = windows.get(player.getUniqueId());
        if (workcell == null || window == null) {
            stale(player);
            return;
        }
        toolboxMenu.render(window, current.get(), workcell, page);
    }

    Optional<JigsawStudioMenuState> matchingState(Player player, UUID requestId, boolean closeStale) {
        Optional<JigsawStudioMenuState> current = actions.menuState(player);
        if (current.isPresent() && current.get().requestId().equals(requestId)) {
            return current;
        }
        if (closeStale) {
            stale(player);
        }
        return Optional.empty();
    }

    void stale(Player player) {
        closeAfterAction(player);
        send(player, ChatColor.RED + "This Jigsaw Studio menu is stale. Open the control chest again.");
    }

    static void send(Player player, String message) {
        ComponentMessenger.sendSection(player, message);
    }

    private void closeAfterAction(Player player) {
        J.runEntity(player, () -> close(player), 1);
    }

    boolean isUnlinkConfirmed(
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

    boolean isDeleteConfirmed(
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

    boolean isProjectDeleteConfirmed(UUID playerId, UUID requestId) {
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

    void purgeExpiredConfirmations(UUID playerId) {
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

}
