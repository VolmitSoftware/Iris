package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import org.junit.Test;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BoardSVCJigsawContextTest {
    @Test
    public void rendersWorkcellIdentityWithoutModeOrOrientationData() {
        JigsawStudioBoardContext context = context(
                JigsawStudioMode.PLANAR_JIGSAW,
                "Corner",
                "mossy",
                JigsawStudioBoardState.UNSAVED,
                "Right-click the chest for variants"
        );

        List<String> lines = BoardSVC.jigsawLines(context);

        assertEquals(List.of(
                "&7&m-------------------",
                "&dJigsaw Studio",
                "&bStructure&7: village",
                "&bWorkcell&7: Corner",
                "&bVariant&7: mossy",
                "&bState&7: Unsaved",
                "&7&m-------------------",
                "&eRight-click the chest for variants",
                "&7&m-------------------"
        ), lines);
        String rendered = String.join("\n", lines).toLowerCase(Locale.ROOT);
        assertFalse(rendered.contains("mode"));
        assertFalse(rendered.contains("orientation"));
        assertFalse(rendered.contains("mask"));
        assertFalse(rendered.contains("north"));
        assertFalse(rendered.contains("south"));
        assertFalse(rendered.contains("east"));
        assertFalse(rendered.contains("west"));
    }

    @Test
    public void rendersModeAndEntryPromptOutsideWorkcells() {
        JigsawStudioBoardContext context = context(
                JigsawStudioMode.SPATIAL_JIGSAW,
                "",
                "",
                JigsawStudioBoardState.SAVED,
                "Use the workcell chest to choose a variant"
        );

        assertEquals(List.of(
                "&7&m-------------------",
                "&dJigsaw Studio",
                "&bStructure&7: village",
                "&bMode&7: Spatial",
                "&7&m-------------------",
                "&eWalk into a workcell",
                "&7Use the workcell chest to choose a variant",
                "&7&m-------------------"
        ), BoardSVC.jigsawLines(context));
    }

    @Test
    public void customWorkcellLabelsRetainTheCanonicalSolverRole() {
        JigsawStudioBoardContext context = new JigsawStudioBoardContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "village",
                JigsawStudioMode.PLANAR_JIGSAW,
                "End Cap",
                "Village Entrances",
                "Grand Longhouse",
                JigsawStudioBoardState.SAVED,
                "Triple-sneak for controls");

        List<String> lines = BoardSVC.jigsawLines(context);

        assertTrue(lines.contains("&bWorkcell&7: Village Entrances"));
        assertTrue(lines.contains("&bRole&7: End Cap"));
        assertTrue(lines.contains("&bVariant&7: Grand Longhouse"));
    }

    @Test
    public void escapesUntrustedBoardValues() {
        assertEquals(
                "＆cFort‹red› keep",
                BoardSVC.untrustedBoardValue("&cFort\u00a7a<red>\nkeep")
        );
    }

    @Test
    public void equalImmutableContextDoesNotRenderAgain() {
        UUID worldId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        JigsawStudioBoardContext previous = context(worldId, requestId, "mossy", JigsawStudioBoardState.SAVED);
        JigsawStudioBoardContext equal = context(worldId, requestId, "mossy", JigsawStudioBoardState.SAVED);
        JigsawStudioBoardContext changedVariant = context(worldId, requestId, "ruined", JigsawStudioBoardState.SAVED);
        JigsawStudioBoardContext changedState = context(worldId, requestId, "mossy", JigsawStudioBoardState.UNSAVED);

        assertFalse(BoardSVC.shouldRenderJigsaw(previous, equal));
        assertTrue(BoardSVC.shouldRenderJigsaw(previous, changedVariant));
        assertTrue(BoardSVC.shouldRenderJigsaw(previous, changedState));
        assertTrue(BoardSVC.shouldRenderJigsaw(null, previous));
    }

    @Test
    public void normalizesOptionalFieldsAndRejectsInvalidIdentity() {
        JigsawStudioBoardContext context = new JigsawStudioBoardContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                " village ",
                JigsawStudioMode.PLANAR_JIGSAW,
                null,
                null,
                null,
                JigsawStudioBoardState.LOADING,
                null
        );

        assertEquals("village", context.structureKey());
        assertEquals("", context.workcellName());
        assertEquals("", context.variantName());
        assertEquals("", context.controlHint());
        assertFalse(context.insideWorkcell());
        assertThrows(IllegalArgumentException.class, () -> new JigsawStudioBoardContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                " ",
                JigsawStudioMode.PLANAR_JIGSAW,
                "",
                "",
                "",
                JigsawStudioBoardState.LOADING,
                ""
        ));
        assertThrows(IllegalArgumentException.class, () -> new JigsawStudioBoardContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "village",
                JigsawStudioMode.PLANAR_JIGSAW,
                "",
                "",
                "mossy",
                JigsawStudioBoardState.LOADING,
                ""
        ));
    }

    private JigsawStudioBoardContext context(
            JigsawStudioMode mode,
            String workcell,
            String variant,
            JigsawStudioBoardState state,
            String hint
    ) {
        return new JigsawStudioBoardContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "village",
                mode,
                workcell,
                workcell,
                variant,
                state,
                hint
        );
    }

    private JigsawStudioBoardContext context(
            UUID worldId,
            UUID requestId,
            String variant,
            JigsawStudioBoardState state
    ) {
        return new JigsawStudioBoardContext(
                worldId,
                requestId,
                "village",
                JigsawStudioMode.PLANAR_JIGSAW,
                "Corner",
                "Corner",
                variant,
                state,
                "Right-click the chest for variants"
        );
    }
}
