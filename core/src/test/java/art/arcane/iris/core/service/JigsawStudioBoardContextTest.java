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

public class JigsawStudioBoardContextTest {
    @Test
    public void rendersWorkcellIdentityWithoutModeOrOrientationData() {
        JigsawStudioBoardContext context = context(
                JigsawStudioMode.PLANAR_JIGSAW,
                "Corner",
                "mossy",
                JigsawStudioBoardState.UNSAVED,
                "Right-click the chest for variants"
        );

        List<String> lines = context.sidebarLines();

        assertEquals(List.of(
                "-------------------",
                "Jigsaw Studio",
                "Structure: village",
                "Workcell: Corner",
                "Variant: mossy",
                "State: Unsaved",
                "-------------------",
                "Right-click the chest for variants",
                "-------------------"
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
                "-------------------",
                "Jigsaw Studio",
                "Structure: village",
                "Mode: Spatial",
                "-------------------",
                "Walk into a workcell",
                "Use the workcell chest to choose a variant",
                "-------------------"
        ), context.sidebarLines());
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

        List<String> lines = context.sidebarLines();

        assertTrue(lines.contains("Workcell: Village Entrances"));
        assertTrue(lines.contains("Role: End Cap"));
        assertTrue(lines.contains("Variant: Grand Longhouse"));
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

        assertEquals(previous.sidebarLines(), equal.sidebarLines());
        assertFalse(previous.sidebarLines().equals(changedVariant.sidebarLines()));
        assertFalse(previous.sidebarLines().equals(changedState.sidebarLines()));
        assertEquals(previous, equal);
        assertFalse(previous.equals(changedVariant));
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
