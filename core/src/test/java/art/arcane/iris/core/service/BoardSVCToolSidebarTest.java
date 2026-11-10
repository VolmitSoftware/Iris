package art.arcane.iris.core.service;

import com.volmit.tooling.world.TextColor;
import com.volmit.tooling.world.ToolText;
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
        List<ToolText> lines = new ArrayList<>(List.of(ToolText.plain("one")));
        BoardSVC.ToolSidebar sidebar = new BoardSVC.ToolSidebar(WORLD, ToolText.plain("Title"), lines);
        lines.add(ToolText.plain("two"));

        assertEquals(List.of(ToolText.plain("one")), sidebar.lines());
        assertThrows(NullPointerException.class, () -> new BoardSVC.ToolSidebar(null, ToolText.plain("Title"), List.of()));
        assertThrows(NullPointerException.class, () -> new BoardSVC.ToolSidebar(WORLD, null, List.of()));
    }

    @Test
    public void textOfASpanCannotCarryCodesOrMarkup() {
        BoardSVC.ToolSidebar sidebar = new BoardSVC.ToolSidebar(WORLD, ToolText.EMPTY,
                List.of(ToolText.plain("&cStructure§a<red>"), ToolText.plain("line\nbreak")));

        assertEquals(List.of("＆cStructure‹red›", "line break"), BoardSVC.toolLines(sidebar));
    }

    @Test
    public void spansBecomeTheBoardsCodesTheWayTheJigsawLinesWereWritten() {
        ToolText separator = ToolText.builder()
                .append(new ToolText.Span("-------------------", TextColor.GRAY, false, false, true))
                .build();
        ToolText value = ToolText.builder().aqua("Structure").gray(": village").build();
        ToolText mixed = ToolText.builder().yellow("Walk").append(" plain").build();

        assertEquals("&7&m-------------------", BoardSVC.boardLine(separator));
        assertEquals("&bStructure&7: village", BoardSVC.boardLine(value));
        assertEquals("&eWalk&r plain", BoardSVC.boardLine(mixed));
        assertEquals("", BoardSVC.boardLine(ToolText.EMPTY));
    }
}
