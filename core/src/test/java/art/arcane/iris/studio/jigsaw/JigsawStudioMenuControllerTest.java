package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.platform.bukkit.BoardSVC;

import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.task.J;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class JigsawStudioMenuControllerTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final UUID WORLD_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REQUEST_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final JigsawStudioPieceRules RULES = new JigsawStudioPieceRules(1, 12, 2, 8, false);

    @Test
    public void pagesVariantsAndClampsStalePageIndexes() {
        List<Integer> values = new ArrayList<>();
        for (int index = 0; index < 57; index++) {
            values.add(index);
        }

        assertEquals(1, JigsawStudioMenuFormat.pageCount(0, 28));
        assertEquals(1, JigsawStudioMenuFormat.pageCount(28, 28));
        assertEquals(2, JigsawStudioMenuFormat.pageCount(29, 28));
        assertEquals(List.of(0, 1, 2), JigsawStudioMenuFormat.page(values, -10, 3));
        assertEquals(List.of(54, 55, 56), JigsawStudioMenuFormat.page(values, 999, 3));
        assertThrows(IllegalArgumentException.class,
                () -> JigsawStudioMenuFormat.pageCount(1, 0));
    }

    @Test
    public void freshUntouchedWorkcellsRenderAsAutosaved() {
        JigsawStudioMenuState.Variant active = variant(
                "pieces/corner",
                true,
                List.of(),
                List.of());
        JigsawStudioMenuState.Workcell fresh = new JigsawStudioMenuState.Workcell(
                "workcell/corner",
                "L Junction",
                "L Junction",
                new JigsawStudioCellDimensions(16, 16, 16),
                true,
                active.pieceKey(),
                false,
                false,
                false,
                false,
                List.of(active));

        assertEquals(ChatColor.GREEN + "Autosaved", JigsawStudioMenuFormat.workcellStatus(fresh));
    }

    @Test
    public void activeEvaluationAndMinimumPlacementIconsAreUnambiguous() {
        JigsawStudioMenuState.Variant active = variant(
                "pieces/corner",
                true,
                List.of(),
                List.of());

        assertEquals(Material.JIGSAW, JigsawStudioMenuFormat.variantMaterial(active));
        assertEquals(Material.EMERALD,
                JigsawStudioMenuFormat.evaluationMaterial(JigsawStudioEvaluationState.VALID));
        assertEquals(Material.TARGET,
                JigsawStudioMenuFormat.RuleField.MINIMUM_PLACEMENTS.material());
    }

    @Test
    public void exposesImmutableWorkcellVariantAndEvaluationState() {
        List<String> themes = new ArrayList<>(List.of("variant-1", "ruined"));
        JigsawStudioGraphEvaluation graphEvaluation = new JigsawStudioGraphEvaluation(
                REQUEST_ID,
                4L,
                1337L,
                JigsawStudioEvaluationState.VALID,
                "variant-1",
                19,
                "Seed 1337 assembled 19 pieces",
                JigsawStudioPreviewRenderer.PreviewBounds.empty());
        JigsawStudioMenuState.Evaluation evaluation = JigsawStudioMenuState.Evaluation.from(graphEvaluation);
        JigsawStudioMenuState.Variant active = variant(
                "pieces/corner_mossy",
                true,
                themes,
                List.of(new JigsawStudioMenuState.Membership("pools/village", 3, 7, 0.35D)));
        JigsawStudioMenuState.Workcell corner = new JigsawStudioMenuState.Workcell(
                "workcell/corner",
                "L Junction",
                "Corner",
                new JigsawStudioCellDimensions(24, 12, 18),
                false,
                active.pieceKey(),
                true,
                false,
                false,
                true,
                List.of(active));
        JigsawStudioMenuState state = state(evaluation, corner);
        themes.add("late-theme");

        assertSame(corner, state.selectedWorkcell());
        assertSame(corner, state.workcell(corner.stableId()));
        assertSame(active, corner.activeVariant());
        assertEquals(new JigsawStudioCellDimensions(24, 12, 18), corner.capacity());
        assertFalse(corner.enabled());
        assertEquals(JigsawStudioEvaluationState.VALID, state.evaluation().state());
        assertEquals(4L, state.evaluation().generation());
        assertEquals("variant-1", state.evaluation().selectedTheme());
        assertEquals(19, state.evaluation().pieceCount());
        assertTrue(state.requireCaps());
        assertTrue(state.irisExtended());
        assertEquals(JigsawStudioCompatibilityTarget.IRIS_EXTENDED, state.compatibilityTarget());
        assertEquals(List.of(new JigsawStudioMenuState.ThemeSet("variant-1", 1)), state.themeSets());
        assertEquals(new JigsawStudioMenuState.ThemeSet("variant-1", 1), state.themeSet("variant-1"));
        assertEquals(List.of("variant-1", "ruined"), active.themes());
        assertTrue(active.resizableToCapacity());
        assertEquals(RULES, active.rules());
        assertEquals(0.35D, active.memberships().getFirst().chance(), 0D);
    }

    @Test
    public void rejectsAmbiguousOrInvalidMenuIdentity() {
        JigsawStudioMenuState.Variant active = variant("pieces/active", true, List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> new JigsawStudioMenuState.Workcell(
                "workcell/corner",
                "L Junction",
                "Corner",
                new JigsawStudioCellDimensions(16, 16, 16),
                true,
                "pieces/different",
                false,
                false,
                false,
                false,
                List.of(active)));
        assertThrows(IllegalArgumentException.class, () -> new JigsawStudioMenuState(
                WORLD_ID,
                REQUEST_ID,
                "village",
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                false,
                List.of(),
                "workcell/missing",
                JigsawStudioMenuState.Evaluation.pending(),
                List.of(workcell(active))));
        assertThrows(IllegalArgumentException.class, () -> variant(
                "pieces/duplicate-membership",
                false,
                List.of(),
                List.of(
                        new JigsawStudioMenuState.Membership("pools/village", 1, 1, 1D),
                        new JigsawStudioMenuState.Membership("pools/village", 1, 2, 0.5D))));
        assertThrows(IllegalArgumentException.class,
                () -> new JigsawStudioMenuState.Membership("pools/village", 0, 0, 1D));
        assertThrows(IllegalArgumentException.class,
                () -> new JigsawStudioMenuState.Membership("pools/village", 0, 1, 1.01D));
        assertThrows(IllegalArgumentException.class,
                () -> variant("pieces/blank-theme", false, List.of(" "), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> variant("pieces/duplicate-theme", false, List.of("variant-1", "variant-1"), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new JigsawStudioMenuState.ThemeSet("variant-1", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new JigsawStudioMenuState.Evaluation(
                        JigsawStudioEvaluationState.PENDING,
                        -1L,
                        1337L,
                        "",
                        0,
                        ""));
    }

    @Test
    public void adjustsEachWorkcellAxisAtNormalAndShiftSteps() {
        JigsawStudioCellDimensions dimensions = new JigsawStudioCellDimensions(24, 12, 18);

        assertEquals(
                new JigsawStudioCellDimensions(25, 12, 18),
                JigsawStudioMenuFormat.adjustedDimensions(
                        dimensions,
                        JigsawStudioMenuFormat.DimensionAxis.WIDTH,
                        1).orElseThrow());
        assertEquals(
                new JigsawStudioCellDimensions(24, 20, 18),
                JigsawStudioMenuFormat.adjustedDimensions(
                        dimensions,
                        JigsawStudioMenuFormat.DimensionAxis.HEIGHT,
                        8).orElseThrow());
        assertEquals(
                new JigsawStudioCellDimensions(24, 12, 10),
                JigsawStudioMenuFormat.adjustedDimensions(
                        dimensions,
                        JigsawStudioMenuFormat.DimensionAxis.DEPTH,
                        -8).orElseThrow());
        assertTrue(JigsawStudioMenuFormat.adjustedDimensions(
                new JigsawStudioCellDimensions(1, 1, 1),
                JigsawStudioMenuFormat.DimensionAxis.WIDTH,
                -1).isEmpty());
        assertTrue(JigsawStudioMenuFormat.adjustedDimensions(
                new JigsawStudioCellDimensions(128, 1, 1),
                JigsawStudioMenuFormat.DimensionAxis.WIDTH,
                1).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> JigsawStudioMenuFormat.adjustedDimensions(
                dimensions,
                JigsawStudioMenuFormat.DimensionAxis.WIDTH,
                0));
    }

    @Test
    public void stagesMultipleCellSizeEditsAndAppliesOneRelayoutWithoutClosing() throws Exception {
        UUID playerId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        Player player = mock(Player.class);
        JigsawStudioMenuActions actions = mock(JigsawStudioMenuActions.class);
        JigsawStudioMenuState.Variant active = variant("pieces/corner", true, List.of(), List.of());
        JigsawStudioMenuState.Workcell workcell = workcell(active);
        JigsawStudioMenuState state = state(JigsawStudioMenuState.Evaluation.pending(), workcell);
        JigsawStudioMenuController controller = new JigsawStudioMenuController(
                mock(JavaPlugin.class),
                actions);
        JigsawStudioWorkcellMenu workcellMenu = new JigsawStudioWorkcellMenu(controller);
        Method resize = JigsawStudioWorkcellMenu.class.getDeclaredMethod(
                "resizeWorkcell",
                Player.class,
                UUID.class,
                String.class,
                JigsawStudioMenuFormat.DimensionAxis.class,
                int.class);
        Method apply = JigsawStudioWorkcellMenu.class.getDeclaredMethod(
                "applyWorkcellResize",
                Player.class,
                UUID.class,
                String.class);
        resize.setAccessible(true);
        apply.setAccessible(true);
        when(player.getUniqueId()).thenReturn(playerId);
        when(actions.menuState(player)).thenReturn(Optional.of(state));
        when(actions.updateWorkcellDimensions(
                player,
                workcell.stableId(),
                new JigsawStudioCellDimensions(25, 20, 18))).thenReturn(true);

        resize.invoke(
                workcellMenu,
                player,
                REQUEST_ID,
                workcell.stableId(),
                JigsawStudioMenuFormat.DimensionAxis.WIDTH,
                1);
        resize.invoke(
                workcellMenu,
                player,
                REQUEST_ID,
                workcell.stableId(),
                JigsawStudioMenuFormat.DimensionAxis.HEIGHT,
                8);

        verify(actions, never()).updateWorkcellDimensions(
                player,
                workcell.stableId(),
                new JigsawStudioCellDimensions(25, 20, 18));
        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.runEntity(
                    same(player),
                    any(Runnable.class),
                    eq(2))).thenReturn(true);

            apply.invoke(workcellMenu, player, REQUEST_ID, workcell.stableId());

            verify(actions).updateWorkcellDimensions(
                    player,
                    workcell.stableId(),
                    new JigsawStudioCellDimensions(25, 20, 18));
        }
    }

    @Test
    public void stagedCapacityPreservesEveryOtherWorkcellProperty() {
        JigsawStudioMenuState.Variant active = variant("pieces/corner", true, List.of(), List.of());
        JigsawStudioMenuState.Workcell source = workcell(active);
        JigsawStudioCellDimensions capacity = new JigsawStudioCellDimensions(31, 19, 27);

        JigsawStudioMenuState.Workcell staged = JigsawStudioMenuFormat.withCapacity(source, capacity);

        assertEquals(capacity, staged.capacity());
        assertEquals(source.stableId(), staged.stableId());
        assertEquals(source.enabled(), staged.enabled());
        assertEquals(source.activeVariantKey(), staged.activeVariantKey());
        assertEquals(source.variants(), staged.variants());
    }

    @Test
    public void allocatesNumberedThemeSetsAndAdjustsPositiveWeights() {
        List<JigsawStudioMenuState.ThemeSet> themeSets = List.of(
                new JigsawStudioMenuState.ThemeSet("variant-1", 1),
                new JigsawStudioMenuState.ThemeSet("seasonal", 4),
                new JigsawStudioMenuState.ThemeSet("variant-2", 2));

        assertEquals("variant-3", JigsawStudioMenuFormat.nextThemeSetKey(themeSets));
        assertEquals("variant-1", JigsawStudioMenuFormat.nextThemeSetKey(List.of()));
        assertEquals(Integer.valueOf(12), JigsawStudioMenuFormat.adjustedPositiveValue(4, 8).orElseThrow());
        assertTrue(JigsawStudioMenuFormat.adjustedPositiveValue(4, -8).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> JigsawStudioMenuFormat.adjustedPositiveValue(4, 0));
    }

    @Test
    public void themeWeightsExposeWholeAssemblySelectionChance() {
        List<JigsawStudioMenuState.ThemeSet> themes = List.of(
                new JigsawStudioMenuState.ThemeSet("variant-1", 3),
                new JigsawStudioMenuState.ThemeSet("variant-2", 1));

        assertEquals("75.0%", JigsawStudioMenuFormat.themeSelectionPercent(
                themes,
                themes.getFirst()));
        assertEquals("25.0%", JigsawStudioMenuFormat.themeSelectionPercent(
                themes,
                themes.getLast()));
    }

    @Test
    public void editsPieceRuleFieldsWithinRuntimeBounds() {
        assertEquals(
                new JigsawStudioPieceRules(2, 12, 2, 8, false),
                JigsawStudioMenuFormat.adjustedRules(
                        RULES,
                        JigsawStudioMenuFormat.RuleField.MINIMUM_DEPTH,
                        1).orElseThrow());
        assertEquals(
                new JigsawStudioPieceRules(1, 17, 2, 8, false),
                JigsawStudioMenuFormat.adjustedRules(
                        RULES,
                        JigsawStudioMenuFormat.RuleField.MAXIMUM_DEPTH,
                        5).orElseThrow());
        assertEquals(
                new JigsawStudioPieceRules(1, 12, 3, 8, false),
                JigsawStudioMenuFormat.adjustedRules(
                        RULES,
                        JigsawStudioMenuFormat.RuleField.MINIMUM_PLACEMENTS,
                        1).orElseThrow());
        assertEquals(
                new JigsawStudioPieceRules(1, 12, 2, 24, false),
                JigsawStudioMenuFormat.adjustedRules(
                        RULES,
                        JigsawStudioMenuFormat.RuleField.MAXIMUM_PLACEMENTS,
                        16).orElseThrow());
        assertEquals(
                new JigsawStudioPieceRules(0, 30, 0, 0, false),
                JigsawStudioMenuFormat.adjustedRules(
                        new JigsawStudioPieceRules(0, 30, 0, 512, false),
                        JigsawStudioMenuFormat.RuleField.MAXIMUM_PLACEMENTS,
                        1).orElseThrow());
        assertEquals(
                new JigsawStudioPieceRules(0, 30, 0, 512, false),
                JigsawStudioMenuFormat.adjustedRules(
                        new JigsawStudioPieceRules(0, 30, 0, 0, false),
                        JigsawStudioMenuFormat.RuleField.MAXIMUM_PLACEMENTS,
                        -1).orElseThrow());
        assertTrue(JigsawStudioMenuFormat.adjustedRules(
                RULES,
                JigsawStudioMenuFormat.RuleField.MINIMUM_DEPTH,
                12).isEmpty());
        assertEquals(
                new JigsawStudioPieceRules(1, 12, 2, 8, true),
                JigsawStudioMenuFormat.withTerminal(RULES, true));
    }

    @Test
    public void toolboxBindsCurrentRequestWorkcellVariantAndMembership() {
        JigsawStudioMenuState.Membership membership = new JigsawStudioMenuState.Membership(
                "pools/village",
                3,
                7,
                0.35D);
        JigsawStudioMenuState.Variant active = variant(
                "pieces/corner_mossy",
                true,
                List.of("variant-1"),
                List.of(membership));
        JigsawStudioMenuState.Variant inactive = variant(
                "pieces/corner_ruined",
                false,
                List.of("variant-1"),
                List.of());
        JigsawStudioMenuState.Workcell corner = workcell(active, List.of(active, inactive));
        JigsawStudioMenuState state = state(JigsawStudioMenuState.Evaluation.pending(), corner);

        List<JigsawStudioToolboxMenu.ToolboxTool> tools = JigsawStudioToolboxMenu.toolboxTools(
                state,
                corner);
        JigsawStudioToolboxMenu.ToolboxTool preview = tool(tools, JigsawStudioToolAction.PREVIEW_GRAPH, 0);
        JigsawStudioToolboxMenu.ToolboxTool chanceIncrease = tool(
                tools,
                JigsawStudioToolAction.ADJUST_VARIANT_CHANCE,
                JigsawStudioMenuController.CHANCE_STEP_PERCENTAGE_POINTS);
        JigsawStudioToolboxMenu.ToolboxTool delete = tool(
                tools,
                JigsawStudioToolAction.DELETE_VARIANT,
                0);
        JigsawStudioToolboxMenu.ToolboxTool newThemeSet = tools.stream()
                .filter(tool -> tool.payload().action() == JigsawStudioToolAction.DUPLICATE_FAMILY)
                .findFirst()
                .orElseThrow();
        JigsawStudioToolboxMenu.ToolboxTool pieceRules = tools.stream()
                .filter(tool -> tool.payload().action() == JigsawStudioToolAction.SET_PIECE_RULES)
                .findFirst()
                .orElseThrow();

        assertEquals("Go to Preview", preview.displayName());
        assertEquals(REQUEST_ID, chanceIncrease.payload().requestId());
        assertEquals(corner.stableId(), chanceIncrease.payload().workcellId());
        assertEquals(active.pieceKey(), chanceIncrease.payload().pieceKey());
        assertEquals(membership.poolKey(), chanceIncrease.payload().poolKey());
        assertEquals(membership.entryIndex(), chanceIncrease.payload().entryIndex());
        assertEquals(inactive.pieceKey(), delete.payload().pieceKey());
        assertEquals("Duplicate All Enabled Cells as Family: variant-2", newThemeSet.displayName());
        assertEquals(active.pieceKey(), pieceRules.payload().pieceKey());
        assertTrue(tools.stream().noneMatch(tool -> tool.payload().action() == JigsawStudioToolAction.DELETE_VARIANT
                && tool.payload().pieceKey().equals(active.pieceKey())));
        assertTrue(tools.stream().anyMatch(tool -> tool.payload().action() == JigsawStudioToolAction.TOGGLE_WORKCELL));
        assertTrue(tools.stream().anyMatch(tool -> tool.payload().action() == JigsawStudioToolAction.RESIZE_WORKCELL));
        assertTrue(tools.stream().anyMatch(tool -> tool.payload().action() == JigsawStudioToolAction.RENAME_WORKCELL));
        assertTrue(tools.stream().anyMatch(tool -> tool.payload().action() == JigsawStudioToolAction.RENAME_VARIANT));
        assertTrue(tools.stream().anyMatch(tool -> tool.payload().action() == JigsawStudioToolAction.RESIZE_VARIANT));
        assertTrue(tools.stream().anyMatch(tool -> tool.payload().action()
                == JigsawStudioToolAction.EXPAND_TO_CELL));
        assertTrue(tools.stream().anyMatch(tool -> tool.payload().action()
                == JigsawStudioToolAction.TOGGLE_REQUIRE_CAPS));
        assertTrue(tools.stream().anyMatch(tool -> tool.payload().action()
                == JigsawStudioToolAction.DELETE_PROJECT));
    }

    @Test
    public void spatialToolboxStillProvidesSharedBoundsResize() {
        JigsawStudioMenuState.Variant active = variant(
                "pieces/room",
                true,
                List.of("variant-1"),
                List.of());
        JigsawStudioMenuState.Workcell room = workcell(active);
        JigsawStudioMenuState state = new JigsawStudioMenuState(
                WORLD_ID,
                REQUEST_ID,
                "stronghold",
                JigsawStudioMode.SPATIAL_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                false,
                List.of(new JigsawStudioMenuState.ThemeSet("variant-1", 1)),
                room.stableId(),
                JigsawStudioMenuState.Evaluation.pending(),
                List.of(room));

        List<JigsawStudioToolboxMenu.ToolboxTool> tools = JigsawStudioToolboxMenu.toolboxTools(state, room);

        assertTrue(tools.stream().anyMatch(tool -> tool.payload().action()
                == JigsawStudioToolAction.RESIZE_WORKCELL));
        assertTrue(tools.stream().anyMatch(tool -> tool.payload().action()
                == JigsawStudioToolAction.EXPAND_TO_CELL));
        assertTrue(tools.stream().noneMatch(tool -> tool.payload().action()
                == JigsawStudioToolAction.TOGGLE_WORKCELL));
    }

    @Test
    public void vanillaPortableToolboxOmitsIrisOnlyMetadataActions() {
        JigsawStudioMenuState.Variant active = variant(
                "pieces/corner",
                true,
                List.of(),
                List.of(new JigsawStudioMenuState.Membership("pools/village", 0, 1, 1D)));
        JigsawStudioMenuState.Workcell corner = workcell(active);
        JigsawStudioMenuState state = new JigsawStudioMenuState(
                WORLD_ID,
                REQUEST_ID,
                "village",
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.VANILLA_PORTABLE,
                true,
                List.of(),
                corner.stableId(),
                JigsawStudioMenuState.Evaluation.pending(),
                List.of(corner));

        List<JigsawStudioToolboxMenu.ToolboxTool> tools = JigsawStudioToolboxMenu.toolboxTools(state, corner);

        assertFalse(state.irisExtended());
        assertTrue(tools.stream().noneMatch(tool -> tool.payload().action() == JigsawStudioToolAction.SET_THEME));
        assertTrue(tools.stream().noneMatch(tool -> tool.payload().action()
                == JigsawStudioToolAction.SET_PIECE_RULES));
        assertTrue(tools.stream().noneMatch(tool -> tool.payload().action()
                == JigsawStudioToolAction.ADJUST_VARIANT_CHANCE));
        assertTrue(tools.stream().anyMatch(tool -> tool.payload().action()
                == JigsawStudioToolAction.ADJUST_VARIANT_WEIGHT));
        assertTrue(tools.stream().noneMatch(tool -> tool.payload().action()
                == JigsawStudioToolAction.TOGGLE_REQUIRE_CAPS));
    }

    @Test
    public void openMarshalsToThePlayerRegionBeforeReadingStudioState() {
        Player player = mock(Player.class);
        JigsawStudioMenuActions actions = mock(JigsawStudioMenuActions.class);
        JigsawStudioMenuController controller = new JigsawStudioMenuController(
                mock(JavaPlugin.class),
                actions);

        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(false);
            scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenReturn(true);

            assertTrue(controller.open(player));
            verifyNoInteractions(actions);
        }
    }

    @Test
    public void changedWorldClosesTheActiveMenuLease() throws ReflectiveOperationException {
        UUID playerId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID worldId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Player player = mock(Player.class);
        World world = mock(World.class);
        Location location = mock(Location.class);
        PlayerChangedWorldEvent event = mock(PlayerChangedWorldEvent.class);
        JigsawStudioMenuController menu = mock(JigsawStudioMenuController.class);
        BoardSVC board = mock(BoardSVC.class);
        JigsawStudioService service = new JigsawStudioService();
        BoardSVC previousBoard = IrisServices.getOrNull(BoardSVC.class);

        when(event.getPlayer()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        when(world.getUID()).thenReturn(worldId);
        when(location.getWorld()).thenReturn(world);

        Field menuField = JigsawStudioToolbelt.class.getDeclaredField("menuController");
        menuField.setAccessible(true);
        menuField.set(service.toolbelt, menu);
        IrisServices.register(BoardSVC.class, board);
        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);

            service.protectionListener.onPlayerChangedWorld(event);

            verify(menu).close(player);
            verify(board).clearJigsawContext(player);
        } finally {
            if (previousBoard == null) {
                IrisServices.remove(BoardSVC.class);
            } else {
                IrisServices.register(BoardSVC.class, previousBoard);
            }
        }
    }

    private JigsawStudioMenuState state(
            JigsawStudioMenuState.Evaluation evaluation,
            JigsawStudioMenuState.Workcell workcell
    ) {
        return new JigsawStudioMenuState(
                WORLD_ID,
                REQUEST_ID,
                "village",
                JigsawStudioMode.PLANAR_JIGSAW,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                true,
                List.of(new JigsawStudioMenuState.ThemeSet("variant-1", 1)),
                workcell.stableId(),
                evaluation,
                List.of(workcell));
    }

    private JigsawStudioMenuState.Workcell workcell(JigsawStudioMenuState.Variant active) {
        return workcell(active, List.of(active));
    }

    private JigsawStudioMenuState.Workcell workcell(
            JigsawStudioMenuState.Variant active,
            List<JigsawStudioMenuState.Variant> variants
    ) {
        return new JigsawStudioMenuState.Workcell(
                "workcell/corner",
                "L Junction",
                "Corner",
                new JigsawStudioCellDimensions(24, 12, 18),
                true,
                active.pieceKey(),
                true,
                false,
                false,
                false,
                variants);
    }

    private JigsawStudioMenuState.Variant variant(
            String pieceKey,
            boolean active,
            List<String> themes,
            List<JigsawStudioMenuState.Membership> memberships
    ) {
        return new JigsawStudioMenuState.Variant(
                pieceKey,
                pieceKey.substring(pieceKey.lastIndexOf('/') + 1),
                Optional.of(new JigsawStudioCellDimensions(16, 16, 16)),
                active,
                true,
                true,
                true,
                active,
                themes,
                RULES,
                memberships);
    }

    private JigsawStudioToolboxMenu.ToolboxTool tool(
            List<JigsawStudioToolboxMenu.ToolboxTool> tools,
            JigsawStudioToolAction action,
            int amount
    ) {
        Optional<JigsawStudioToolboxMenu.ToolboxTool> match = tools.stream()
                .filter(tool -> tool.payload().action() == action && tool.payload().amount() == amount)
                .findFirst();
        return match.orElseThrow();
    }
}
