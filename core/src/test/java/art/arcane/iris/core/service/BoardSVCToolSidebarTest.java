package art.arcane.iris.core.service;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BoardSVCToolSidebarTest {
    private static final UUID WORLD = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    public void aToolSidebarKeepsItsOwnCopyOfTheLines() {
        List<String> lines = new ArrayList<>(List.of("one"));
        BoardSVC.ToolSidebar sidebar = new BoardSVC.ToolSidebar(WORLD, "Title", lines);
        lines.add("two");

        assertEquals(List.of("one"), sidebar.lines());
        assertThrows(NullPointerException.class, () -> new BoardSVC.ToolSidebar(null, "Title", List.of()));
        assertThrows(NullPointerException.class, () -> new BoardSVC.ToolSidebar(WORLD, null, List.of()));
    }

    @Test
    public void toolLinesAreShownAsPlainTextWithoutColourOrMarkup() {
        BoardSVC.ToolSidebar sidebar = new BoardSVC.ToolSidebar(WORLD, "Title",
                List.of("&cStructure§a<red>", "line\nbreak", "plain"));

        assertEquals(List.of(BoardSVC.untrustedBoardValue("&cStructure§a<red>"), "line break", "plain"),
                BoardSVC.toolLines(sidebar));
        assertEquals("＆cStructure‹red›", BoardSVC.toolLines(sidebar).get(0));
    }
}
