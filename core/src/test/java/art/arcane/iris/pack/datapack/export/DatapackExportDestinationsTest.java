package art.arcane.iris.pack.datapack.export;

import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DatapackExportDestinationsTest {
    private static final String NAME_MESSAGE =
            "Export output must be one file name using letters, numbers, '.', '_', or '-'.";

    @Test
    public void parsesFormatAliases() {
        assertEquals(DatapackExportFormat.ZIP, DatapackExportDestinations.parseFormat("zip"));
        assertEquals(DatapackExportFormat.ZIP, DatapackExportDestinations.parseFormat(" ZIP "));
        assertEquals(DatapackExportFormat.DIRECTORY, DatapackExportDestinations.parseFormat("directory"));
        assertEquals(DatapackExportFormat.DIRECTORY, DatapackExportDestinations.parseFormat("dir"));
        assertEquals(DatapackExportFormat.DIRECTORY, DatapackExportDestinations.parseFormat("folder"));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> DatapackExportDestinations.parseFormat("tar"));
        assertEquals("Export format must be directory or zip.", failure.getMessage());
    }

    @Test
    public void resolvesOneArtifactInsideExportRoot() {
        Path root = Path.of("build", "datapack-exports").toAbsolutePath().normalize();

        assertEquals(root.resolve("village.zip"),
                DatapackExportDestinations.resolve(root, "village", DatapackExportFormat.ZIP));
        assertEquals(root.resolve("village.zip"),
                DatapackExportDestinations.resolve(root, "village.zip", DatapackExportFormat.ZIP));
        assertEquals(root.resolve("village"),
                DatapackExportDestinations.resolve(root, "village", DatapackExportFormat.DIRECTORY));
        assertThrows(IllegalArgumentException.class,
                () -> DatapackExportDestinations.resolve(root, "", DatapackExportFormat.DIRECTORY));
        assertThrows(IllegalArgumentException.class,
                () -> DatapackExportDestinations.resolve(root, ".", DatapackExportFormat.DIRECTORY));
        assertThrows(IllegalArgumentException.class,
                () -> DatapackExportDestinations.resolve(root, " village", DatapackExportFormat.DIRECTORY));
    }

    @Test
    public void rejectsPathSeparatorsInOutput() {
        Path root = Path.of("build", "datapack-exports").toAbsolutePath().normalize();

        IllegalArgumentException nested = assertThrows(IllegalArgumentException.class,
                () -> DatapackExportDestinations.resolve(root, "nested/export", DatapackExportFormat.ZIP));
        IllegalArgumentException parent = assertThrows(IllegalArgumentException.class,
                () -> DatapackExportDestinations.resolve(root, "../all-exports", DatapackExportFormat.DIRECTORY));
        IllegalArgumentException windows = assertThrows(IllegalArgumentException.class,
                () -> DatapackExportDestinations.resolve(root, "nested\\export", DatapackExportFormat.ZIP));

        assertEquals(NAME_MESSAGE, nested.getMessage());
        assertEquals(NAME_MESSAGE, parent.getMessage());
        assertEquals(NAME_MESSAGE, windows.getMessage());
    }

    @Test
    public void secondExportToSamePathIsRejected() {
        Path destination = Path.of("build", "datapack-exports", "claimed.zip");
        Path sameDestination = Path.of("build", "datapack-exports", ".", "claimed.zip").toAbsolutePath();

        assertTrue(DatapackExportDestinations.claim(destination));
        try {
            assertFalse(DatapackExportDestinations.claim(destination));
            assertFalse(DatapackExportDestinations.claim(sameDestination));
        } finally {
            DatapackExportDestinations.release(destination);
        }

        assertTrue(DatapackExportDestinations.claim(sameDestination));
        DatapackExportDestinations.release(sameDestination);
    }
}
