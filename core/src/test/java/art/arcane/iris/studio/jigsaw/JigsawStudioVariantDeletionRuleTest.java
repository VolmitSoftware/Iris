package art.arcane.iris.studio.jigsaw;

import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JigsawStudioVariantDeletionRuleTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID REQUEST = UUID.randomUUID();

    @Test
    public void spatialVariantsCanBeDeletedWhileLoadedUnlessTheyAreTheLastOne() {
        JigsawStudioMenuState.Variant first = variant("demo/end", true, true);
        JigsawStudioMenuState.Variant second = variant("demo/cross", true, true);
        JigsawStudioMenuState.Workcell firstCell = workcell("workcell/spatial", first);
        JigsawStudioMenuState.Workcell secondCell = workcell("workcell/spatial/demo/cross", second);
        JigsawStudioMenuState twoCells = state(JigsawStudioMode.SPATIAL_JIGSAW, List.of(firstCell, secondCell));
        JigsawStudioMenuState oneCell = state(JigsawStudioMode.SPATIAL_JIGSAW, List.of(firstCell));

        assertTrue(twoCells.canDeleteVariant(firstCell, first));
        assertFalse(oneCell.canDeleteVariant(firstCell, first));
        List<JigsawStudioToolboxMenu.ToolboxTool> tools = JigsawStudioToolboxMenu.toolboxTools(twoCells, firstCell);
        assertTrue(tools.stream().anyMatch(tool -> tool.payload().action() == JigsawStudioToolAction.DELETE_VARIANT
                && tool.payload().pieceKey().equals("demo/end")));
    }

    @Test
    public void planarVariantsStillRequireAnotherLoadedVariantFirst() {
        JigsawStudioMenuState.Variant loaded = variant("demo/end", true, true);
        JigsawStudioMenuState.Variant spare = variant("demo/end-b", false, true);
        JigsawStudioMenuState.Variant readOnly = variant("demo/end-c", false, false);
        JigsawStudioMenuState.Workcell cell = new JigsawStudioMenuState.Workcell(
                "workcell/end",
                "End Cap",
                "End Cap",
                new JigsawStudioCellDimensions(16, 16, 16),
                true,
                loaded.pieceKey(),
                false,
                false,
                false,
                false,
                List.of(loaded, spare, readOnly));
        JigsawStudioMenuState state = state(JigsawStudioMode.PLANAR_JIGSAW, List.of(cell));

        assertFalse(state.canDeleteVariant(cell, loaded));
        assertTrue(state.canDeleteVariant(cell, spare));
        assertFalse(state.canDeleteVariant(cell, readOnly));
    }

    private static JigsawStudioMenuState state(JigsawStudioMode mode, List<JigsawStudioMenuState.Workcell> workcells) {
        return new JigsawStudioMenuState(
                WORLD,
                REQUEST,
                "demo",
                mode,
                JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                false,
                List.of(new JigsawStudioMenuState.ThemeSet("variant-1", 1)),
                workcells.getFirst().stableId(),
                JigsawStudioMenuState.Evaluation.pending(),
                workcells);
    }

    private static JigsawStudioMenuState.Workcell workcell(String stableId, JigsawStudioMenuState.Variant active) {
        return new JigsawStudioMenuState.Workcell(
                stableId,
                "Spatial",
                active.displayName(),
                new JigsawStudioCellDimensions(15, 15, 15),
                true,
                active.pieceKey(),
                false,
                false,
                false,
                false,
                List.of(active));
    }

    private static JigsawStudioMenuState.Variant variant(String pieceKey, boolean active, boolean owned) {
        return new JigsawStudioMenuState.Variant(
                pieceKey,
                pieceKey.substring(pieceKey.lastIndexOf('/') + 1),
                Optional.of(new JigsawStudioCellDimensions(15, 15, 15)),
                active,
                owned,
                true,
                owned,
                active,
                List.of(),
                new JigsawStudioPieceRules(0, 30, 0, 0, false),
                List.of());
    }
}
