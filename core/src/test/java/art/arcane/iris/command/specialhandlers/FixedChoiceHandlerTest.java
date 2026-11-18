package art.arcane.iris.command.specialhandlers;

import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class FixedChoiceHandlerTest {
    @Test
    public void exportModeSuggestsAndCanonicalizesEveryMode() throws Exception {
        ExportModeHandler handler = new ExportModeHandler();
        assertEquals(List.of("preset", "dimension", "overworld"), handler.getPossibilities());
        assertEquals("preset", handler.parse(" PRESET ", false));
        assertThrows(DirectorParsingException.class, () -> handler.parse("nether", false));
    }

    @Test
    public void exportFormatSuggestsAndCanonicalizesAliases() throws Exception {
        ExportFormatHandler handler = new ExportFormatHandler();
        assertEquals(List.of("zip", "directory"), handler.getPossibilities());
        assertEquals("directory", handler.parse("folder", false));
        assertThrows(DirectorParsingException.class, () -> handler.parse("tar", false));
    }

    @Test
    public void cleanupModeSuggestsAndRejectsUnknownModes() throws Exception {
        CleanupModeHandler handler = new CleanupModeHandler();
        assertEquals(List.of("preview", "apply"), handler.getPossibilities());
        assertEquals("apply", handler.parse("Apply", false));
        assertThrows(DirectorParsingException.class, () -> handler.parse("delete", false));
    }
}
