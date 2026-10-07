package art.arcane.iris.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

public class AtomicDirectoryPublisherTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void deletionRemovesOperatingSystemMetadataCreatedAfterTheWalkSnapshot() throws Exception {
        Path root = temporaryFolder.newFolder("late-metadata").toPath();
        Path pack = Files.createDirectory(root.resolve("pack"));
        List<Path> snapshot;
        try (Stream<Path> stream = Files.walk(root)) {
            snapshot = stream.toList();
        }
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.walk(eq(root))).thenAnswer(invocation -> {
                Files.writeString(pack.resolve(".DS_Store"), "finder");
                Files.writeString(pack.resolve("._pack"), "metadata");
                return snapshot.stream();
            });

            AtomicDirectoryPublisher.deleteTree(root);
        }

        assertFalse(Files.exists(root));
    }

    @Test
    public void lateRealPayloadIsNotDeletedDuringMetadataRecovery() throws Exception {
        Path root = temporaryFolder.newFolder("late-payload").toPath();
        List<Path> snapshot = List.of(root);
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.walk(eq(root))).thenAnswer(invocation -> {
                Files.writeString(root.resolve(".DS_Store"), "finder");
                Files.writeString(root.resolve("terrain.dat"), "keep");
                return snapshot.stream();
            });

            assertThrows(DirectoryNotEmptyException.class, () -> AtomicDirectoryPublisher.deleteTree(root));
        }

        assertEquals("keep", Files.readString(root.resolve("terrain.dat")));
        assertEquals("finder", Files.readString(root.resolve(".DS_Store")));
    }

    @Test
    public void lateMetadataSymlinkIsNotFollowedOrTreatedAsMetadata() throws Exception {
        Path root = temporaryFolder.newFolder("late-link").toPath();
        Path outside = temporaryFolder.getRoot().toPath().resolve("outside.txt");
        Files.writeString(outside, "keep");
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.walk(eq(root))).thenAnswer(invocation -> {
                Files.createSymbolicLink(root.resolve(".DS_Store"), outside);
                return Stream.of(root);
            });

            assertThrows(DirectoryNotEmptyException.class, () -> AtomicDirectoryPublisher.deleteTree(root));
        }

        assertTrue(Files.isSymbolicLink(root.resolve(".DS_Store")));
        assertEquals("keep", Files.readString(outside));
    }

    @Test
    public void continuouslyRecreatedMetadataCannotCauseAnUnboundedRetry() throws Exception {
        Path root = temporaryFolder.newFolder("persistent-metadata").toPath();
        AtomicInteger attempts = new AtomicInteger();
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.deleteIfExists(eq(root))).thenAnswer(invocation -> {
                attempts.incrementAndGet();
                Files.writeString(root.resolve(".DS_Store"), "finder");
                return invocation.callRealMethod();
            });

            assertThrows(DirectoryNotEmptyException.class, () -> AtomicDirectoryPublisher.deleteTree(root));
        }

        assertEquals(4, attempts.get());
        assertTrue(Files.exists(root));
    }

    @Test
    public void permissionFailuresKeepTheirOriginalCauseWithoutRetries() throws Exception {
        Path root = temporaryFolder.newFolder("denied-deletion").toPath();
        AccessDeniedException denied = new AccessDeniedException(root.toString());
        AtomicInteger attempts = new AtomicInteger();
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.deleteIfExists(eq(root))).thenAnswer(invocation -> {
                attempts.incrementAndGet();
                throw denied;
            });

            assertSame(denied, assertThrows(AccessDeniedException.class, () -> AtomicDirectoryPublisher.deleteTree(root)));
        }

        assertEquals(1, attempts.get());
    }

    @Test
    public void retainedBackupSurvivesCommitAndRemainsAvailableForRollback() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path target = Files.createDirectory(root.resolve("target"));
        Files.writeString(target.resolve("value.txt"), "old");
        Path staged = Files.createDirectory(root.resolve("stage"));
        Files.writeString(staged.resolve("value.txt"), "new");
        Path retained = root.resolve("retained");

        try (AtomicDirectoryPublisher.Publication publication = AtomicDirectoryPublisher.publish(staged, target)) {
            assertTrue(publication.retainBackup(retained));
        }

        assertEquals("old", Files.readString(target.resolve("value.txt")));
        assertFalse(Files.exists(retained));
        Files.createDirectory(staged);
        Files.writeString(staged.resolve("value.txt"), "new");
        try (AtomicDirectoryPublisher.Publication publication = AtomicDirectoryPublisher.publish(staged, target)) {
            assertTrue(publication.retainBackup(retained));
            publication.commit();
        }
        assertEquals("old", Files.readString(retained.resolve("value.txt")));
        assertEquals("new", Files.readString(target.resolve("value.txt")));
    }

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
