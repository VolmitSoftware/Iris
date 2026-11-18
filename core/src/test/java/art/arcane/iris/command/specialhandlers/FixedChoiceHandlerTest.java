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
    public void previewApplyModeSuggestsAndRejectsUnknownModes() throws Exception {
        PreviewApplyModeHandler handler = new PreviewApplyModeHandler();
        assertEquals(List.of("preview", "apply"), handler.getPossibilities());
        assertEquals("apply", handler.parse("Apply", false));
        assertThrows(DirectorParsingException.class, () -> handler.parse("delete", false));
    }

    @Test
    public void exportVersionKeepsBlankAsLatestAndRejectsOlderTargets() throws Exception {
        ExportVersionHandler handler = new ExportVersionHandler();
        assertEquals(List.of("26.3"), handler.getPossibilities());
        assertEquals("", handler.parse("", false));
        assertEquals("26.3", handler.parse("26.3.0", false));
        assertThrows(DirectorParsingException.class, () -> handler.parse("26.2", false));
    }
}
