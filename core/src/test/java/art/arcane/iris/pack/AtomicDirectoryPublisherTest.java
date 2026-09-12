package art.arcane.iris.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AtomicDirectoryPublisherTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void commitPublishesStagedDirectoryAndRemovesBackup() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path target = Files.createDirectory(root.resolve("target"));
        Files.writeString(target.resolve("value.txt"), "old");
        Path staged = Files.createDirectory(root.resolve("stage"));
        Files.writeString(staged.resolve("value.txt"), "new");

        try (AtomicDirectoryPublisher.Publication publication = AtomicDirectoryPublisher.publish(staged, target)) {
            assertEquals("new", Files.readString(target.resolve("value.txt")));
            publication.commit();
            publication.cleanupBackup();
        }

        assertEquals("new", Files.readString(target.resolve("value.txt")));
        assertFalse(Files.exists(staged));
        try (Stream<Path> stream = Files.list(root)) {
            assertFalse(stream.anyMatch(path -> path.getFileName().toString().contains("backup-")));
        }
    }

    @Test
    public void closeWithoutCommitRestoresOriginalDirectory() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path target = Files.createDirectory(root.resolve("target"));
        Files.writeString(target.resolve("value.txt"), "old");
        Path staged = Files.createDirectory(root.resolve("stage"));
        Files.writeString(staged.resolve("value.txt"), "new");

        try (AtomicDirectoryPublisher.Publication ignored = AtomicDirectoryPublisher.publish(staged, target)) {
            assertEquals("new", Files.readString(target.resolve("value.txt")));
        }

        assertTrue(Files.isDirectory(target));
        assertEquals("old", Files.readString(target.resolve("value.txt")));
    }

    @Test
    public void absentPublicationRefusesAnExistingTargetWithoutMovingEitherDirectory() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path target = Files.createDirectory(root.resolve("existing-target"));
        Files.writeString(target.resolve("value.txt"), "old");
        Path staged = Files.createDirectory(root.resolve("absent-stage"));
        Files.writeString(staged.resolve("value.txt"), "new");

        assertThrows(
                FileAlreadyExistsException.class,
                () -> AtomicDirectoryPublisher.publishAbsent(staged, target)
        );

        assertEquals("old", Files.readString(target.resolve("value.txt")));
        assertEquals("new", Files.readString(staged.resolve("value.txt")));
    }
}
