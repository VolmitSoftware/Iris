package art.arcane.iris.probe;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.mockito.Mockito.mockStatic;

public final class ProbeWorkspaceCleanupTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void deletesNestedContentsAndAcceptsMissingRoots() throws Exception {
        Path root = temporaryFolder.newFolder("workspace").toPath();
        Files.writeString(Files.createDirectories(root.resolve("pack/regions")).resolve("region.json"), "{}");

        ProbeWorkspaceCleanup.deleteRecursively(root);
        ProbeWorkspaceCleanup.deleteRecursively(root);

        assertFalse(Files.exists(root));
    }

    @Test
    public void rescansWhenAnotherThreadCreatesMetadataAfterDirectoryTraversal() throws Exception {
        Path root = temporaryFolder.newFolder("late-metadata").toPath();
        Path nested = Files.createDirectory(root.resolve("pack"));
        CountDownLatch deletingDirectory = new CountDownLatch(1);
        CountDownLatch metadataWritten = new CountDownLatch(1);
        ExecutorService writer = Executors.newSingleThreadExecutor();
        AtomicInteger attempts = new AtomicInteger();
        try (MockedStatic<Files> files = mockStatic(Files.class, invocation -> {
            if (invocation.getMethod().getName().equals("deleteIfExists") && nested.equals(invocation.getArgument(0))) {
                if (attempts.incrementAndGet() == 1) {
                    deletingDirectory.countDown();
                    assertTrue(metadataWritten.await(5, TimeUnit.SECONDS));
                }
            }
            return invocation.callRealMethod();
        })) {
            Future<?> created = writer.submit(() -> {
                assertTrue(deletingDirectory.await(5, TimeUnit.SECONDS));
                Files.writeString(nested.resolve(".DS_Store"), "metadata");
                metadataWritten.countDown();
                return null;
            });

            ProbeWorkspaceCleanup.deleteRecursively(root);
            created.get(5, TimeUnit.SECONDS);
        } finally {
            deletingDirectory.countDown();
            writer.shutdownNow();
        }

        assertEquals(2, attempts.get());
        assertFalse(Files.exists(root));
    }

    @Test
    public void stopsRescanningWhenTheDirectoryKeepsChanging() throws Exception {
        Path root = temporaryFolder.newFolder("continuing-writes").toPath();
        AtomicInteger attempts = new AtomicInteger();
        try (MockedStatic<Files> files = mockStatic(Files.class, invocation -> {
            if (invocation.getMethod().getName().equals("deleteIfExists") && root.equals(invocation.getArgument(0))) {
                attempts.incrementAndGet();
                Files.writeString(root.resolve("late-file"), "content");
            }
            return invocation.callRealMethod();
        })) {
            assertThrows(DirectoryNotEmptyException.class, () -> ProbeWorkspaceCleanup.deleteRecursively(root));
        }

        assertEquals(4, attempts.get());
        assertTrue(Files.exists(root.resolve("late-file")));
    }

    @Test
    public void propagatesOtherIoFailuresWithoutRetrying() throws Exception {
        Path root = temporaryFolder.newFolder("denied").toPath();
        AccessDeniedException failure = new AccessDeniedException(root.toString());
        AtomicInteger attempts = new AtomicInteger();
        try (MockedStatic<Files> files = mockStatic(Files.class, invocation -> {
            if (invocation.getMethod().getName().equals("deleteIfExists") && root.equals(invocation.getArgument(0))) {
                attempts.incrementAndGet();
                throw failure;
            }
            return invocation.callRealMethod();
        })) {
            assertSame(failure, assertThrows(AccessDeniedException.class,
                    () -> ProbeWorkspaceCleanup.deleteRecursively(root)));
        }
        assertEquals(1, attempts.get());
    }

    @Test
    public void deletesLinksWithoutFollowingTheirTargets() throws Exception {
        Path outside = temporaryFolder.newFolder("outside").toPath();
        Path retained = Files.writeString(outside.resolve("retained.txt"), "retained");
        Path root = temporaryFolder.newFolder("linked-workspace").toPath();
        createSymbolicLink(root.resolve("outside"), outside);
        createSymbolicLink(root.resolve("missing"), outside.resolve("missing"));

        ProbeWorkspaceCleanup.deleteRecursively(root);

        assertEquals("retained", Files.readString(retained));
        assertFalse(Files.exists(root, LinkOption.NOFOLLOW_LINKS));
        Path rootLink = temporaryFolder.getRoot().toPath().resolve("root-link");
        createSymbolicLink(rootLink, outside);
        ProbeWorkspaceCleanup.deleteRecursively(rootLink);
        assertEquals("retained", Files.readString(retained));
        assertFalse(Files.exists(rootLink, LinkOption.NOFOLLOW_LINKS));
    }

    private static void createSymbolicLink(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | AccessDeniedException failure) {
            assumeNoException(failure);
        } catch (FileSystemException failure) {
            if (!System.getProperty("os.name").startsWith("Windows") || failure.getReason() == null
                    || !failure.getReason().toLowerCase(Locale.ROOT).contains("privilege")) {
                throw failure;
            }
            assumeNoException(failure);
        }
    }
}
