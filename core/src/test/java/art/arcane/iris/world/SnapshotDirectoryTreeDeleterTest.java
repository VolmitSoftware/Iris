package art.arcane.iris.world;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SnapshotDirectoryTreeDeleterTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void deletesNestedDirectoryWithLargeDirectChildSnapshot() throws IOException {
        Path root = temporaryFolder.newFolder("large-tree").toPath();
        Path nested = Files.createDirectories(root.resolve("one/two/three"));
        for (int index = 0; index < 257; index++) {
            Files.writeString(nested.resolve("entry-" + index + ".dat"), "value-" + index);
        }
        for (int index = 0; index < 17; index++) {
            Path child = Files.createDirectory(nested.resolve("branch-" + index));
            Files.writeString(child.resolve("payload.dat"), "payload-" + index);
        }

        SnapshotDirectoryTreeDeleter.delete(root);

        assertFalse(Files.exists(root));
    }

    @Test
    public void rejectsDirectSymlinkBeforeMutatingItsDirectory() throws IOException {
        Path external = temporaryFolder.newFolder("external").toPath();
        Path externalFile = Files.writeString(external.resolve("preserved.dat"), "preserved");
        Path root = temporaryFolder.newFolder("symlink-tree").toPath();
        Path sibling = Files.writeString(root.resolve("sibling.dat"), "sibling");
        Files.createSymbolicLink(root.resolve("external-link"), external);

        IOException failure = assertThrows(
                IOException.class,
                () -> SnapshotDirectoryTreeDeleter.delete(root)
        );

        assertTrue(failure.getMessage().contains("symbolic link"));
        assertTrue(Files.exists(root));
        assertTrue(Files.exists(sibling));
        assertEquals("preserved", Files.readString(externalFile));
    }

    @Test
    public void rejectsNonDirectoryTarget() throws IOException {
        Path file = temporaryFolder.newFile("ordinary-file.dat").toPath();

        IOException failure = assertThrows(
                IOException.class,
                () -> SnapshotDirectoryTreeDeleter.delete(file)
        );

        assertTrue(failure.getMessage().contains("not a directory"));
        assertTrue(Files.exists(file));
    }
}
