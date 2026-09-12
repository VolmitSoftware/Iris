package art.arcane.iris.command;

import art.arcane.volmlib.util.collection.KList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommandIrisPackDimensionTypeHandlerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void emptyPackDirectoryDoesNotAdvertiseDefaultSentinel() throws Exception {
        File packsFolder = temporaryFolder.newFolder("packs");

        KList<String> options = CommandIris.PackDimensionTypeHandler.packDimensionOptions(packsFolder);

        assertTrue(options.isEmpty());
        assertFalse(options.contains("default"));
    }

    @Test
    public void matchingPackAndDimensionUsesBarePackName() {
        assertEquals("overworld",
                CommandIris.PackDimensionTypeHandler.packDimensionOption("overworld", "overworld"));
        assertEquals("OverWorld",
                CommandIris.PackDimensionTypeHandler.packDimensionOption("OverWorld", "overworld"));
    }

    @Test
    public void differingDimensionUsesExplicitPackReference() {
        assertEquals("custom_pack:dimensions/sky",
                CommandIris.PackDimensionTypeHandler.packDimensionOption("custom_pack", "dimensions/sky"));
    }
}
