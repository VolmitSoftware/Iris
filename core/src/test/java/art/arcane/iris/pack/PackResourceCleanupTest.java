package art.arcane.iris.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PackResourceCleanupTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void previewIsReadOnlyAndReturnsSortedCandidates() throws Exception {
        File pack = createPack("preview");
        write(pack, "dimensions/main.json", "{\"biome\":\"nested/used\"}");
        write(pack, "biomes/z-last.json", "{}");
        write(pack, "biomes/a-first.json", "{}");
        write(pack, "biomes/nested/used.json", "{}");

        PackResourceCleanup.Preview preview = PackResourceCleanup.preview(pack);

        assertTrue(preview.success());
        assertTrue(preview.hasCandidates());
        assertEquals(List.of("biomes/a-first.json", "biomes/z-last.json"), preview.candidatePaths());
        assertFalse(new File(pack, ".iris-trash").exists());
        assertTrue(new File(pack, "biomes/a-first.json").isFile());
    }

    @Test
    public void applyRunsFreshScanBeforeMutating() throws Exception {
        File pack = createPack("fresh-scan");
        write(pack, "dimensions/main.json", "{}");
        write(pack, "biomes/kept.json", "{}");

        PackResourceCleanup.Preview preview = PackResourceCleanup.preview(pack);
        assertEquals(List.of("biomes/kept.json"), preview.candidatePaths());

        write(pack, "dimensions/main.json", "{\"biome\":\"kept\"}");
        PackResourceCleanup.ApplyResult result = PackResourceCleanup.apply(pack);

        assertTrue(result.success());
        assertFalse(result.changed());
        assertNull(result.quarantinePath());
        assertTrue(new File(pack, "biomes/kept.json").isFile());
        assertFalse(new File(pack, ".iris-trash").exists());
    }

    @Test
    public void applyCreatesUniqueDumpsAndPreservesRelativePaths() throws Exception {
        File pack = createPack("unique-dumps");
        write(pack, "dimensions/main.json", "{}");
        write(pack, "biomes/nested/first.json", "{\"value\":1}");

        PackResourceCleanup.ApplyResult first = PackResourceCleanup.apply(pack);
        assertTrue(first.success());
        assertEquals(List.of("biomes/nested/first.json"), first.quarantinedPaths());
        assertTrue(new File(pack, first.quarantinePath() + "/biomes/nested/first.json").isFile());

        write(pack, "biomes/nested/second.json", "{\"value\":2}");
        PackResourceCleanup.ApplyResult second = PackResourceCleanup.apply(pack);

        assertTrue(second.success());
        assertNotEquals(first.quarantinePath(), second.quarantinePath());
        assertTrue(new File(pack, first.quarantinePath() + "/biomes/nested/first.json").isFile());
        assertTrue(new File(pack, second.quarantinePath() + "/biomes/nested/second.json").isFile());
    }

    @Test
    public void restorePreviewReportsLatestDumpAndAllConflictsWithoutMutation() throws Exception {
        File pack = createPack("restore-conflict");
        write(pack, "dimensions/main.json", "{}");
        write(pack, "biomes/first.json", "{\"value\":1}");
        PackResourceCleanup.ApplyResult first = PackResourceCleanup.apply(pack);
        write(pack, "biomes/second.json", "{\"value\":2}");
        PackResourceCleanup.ApplyResult second = PackResourceCleanup.apply(pack);
        write(pack, "biomes/second.json", "{\"replacement\":true}");

        PackResourceCleanup.RestorePreview preview = PackResourceCleanup.previewRestore(pack);

        assertTrue(preview.success());
        assertEquals(second.quarantinePath(), preview.dumpPath());
        assertEquals(List.of("biomes/second.json"), preview.filePaths());
        assertEquals(List.of("biomes/second.json"), preview.conflicts());
        assertTrue(preview.hasConflicts());
        assertFalse(preview.canRestore());
        assertTrue(new File(pack, second.quarantinePath() + "/biomes/second.json").isFile());
        assertTrue(new File(pack, first.quarantinePath() + "/biomes/first.json").isFile());

        PackResourceCleanup.RestoreResult result = PackResourceCleanup.restoreLatest(pack);

        assertFalse(result.success());
        assertEquals(List.of("biomes/second.json"), result.conflicts());
        assertTrue(new File(pack, second.quarantinePath() + "/biomes/second.json").isFile());
        assertEquals("{\"replacement\":true}", read(pack, "biomes/second.json"));
    }

    @Test
    public void restoreLatestRestoresOnlyNewestDump() throws Exception {
        File pack = createPack("restore-latest");
        write(pack, "dimensions/main.json", "{}");
        write(pack, "biomes/first.json", "{\"value\":1}");
        PackResourceCleanup.ApplyResult first = PackResourceCleanup.apply(pack);
        write(pack, "biomes/second.json", "{\"value\":2}");
        PackResourceCleanup.ApplyResult second = PackResourceCleanup.apply(pack);

        PackResourceCleanup.RestoreResult result = PackResourceCleanup.restoreLatest(pack);

        assertTrue(result.success());
        assertTrue(result.changed());
        assertEquals(second.quarantinePath(), result.dumpPath());
        assertEquals(List.of("biomes/second.json"), result.restoredPaths());
        assertEquals("{\"value\":2}", read(pack, "biomes/second.json"));
        assertFalse(new File(pack, "biomes/first.json").exists());
        assertTrue(new File(pack, first.quarantinePath() + "/biomes/first.json").isFile());
        assertFalse(new File(pack, second.quarantinePath()).exists());
    }

    @Test
    public void restoreCopyFailureRollsBackCreatedDestinations() throws Exception {
        File pack = createPack("restore-rollback");
        write(pack, "dimensions/main.json", "{}");
        write(pack, "biomes/a-readable.json", "{\"value\":1}");
        write(pack, "biomes/z-unreadable.json", "{\"value\":2}");
        PackResourceCleanup.ApplyResult applied = PackResourceCleanup.apply(pack);
        Path unreadable = new File(pack, applied.quarantinePath() + "/biomes/z-unreadable.json").toPath();
        Assume.assumeTrue(Files.getFileStore(unreadable).supportsFileAttributeView("posix"));
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(unreadable);
        Files.setPosixFilePermissions(unreadable, Set.of(PosixFilePermission.OWNER_WRITE));
        try {
            PackResourceCleanup.RestoreResult result = PackResourceCleanup.restoreLatest(pack);

            assertFalse(result.success());
            assertTrue(result.error().contains("rolled back"));
            assertFalse(new File(pack, "biomes/a-readable.json").exists());
            assertFalse(new File(pack, "biomes/z-unreadable.json").exists());
            assertTrue(new File(pack, applied.quarantinePath() + "/biomes/a-readable.json").isFile());
            assertTrue(Files.exists(unreadable));
        } finally {
            Files.setPosixFilePermissions(unreadable, originalPermissions);
        }
    }

    @Test
    public void restoreSourceRemovalFailureRollsBackDestinationsAndPreservesDump() throws Exception {
        File pack = createPack("restore-delete-rollback");
        write(pack, "dimensions/main.json", "{}");
        write(pack, "biomes/a.json", "{\"value\":1}");
        write(pack, "biomes/b.json", "{\"value\":2}");
        PackResourceCleanup.ApplyResult applied = PackResourceCleanup.apply(pack);
        Path quarantineBiomes = new File(pack, applied.quarantinePath() + "/biomes").toPath();
        Assume.assumeTrue(Files.getFileStore(quarantineBiomes).supportsFileAttributeView("posix"));
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(quarantineBiomes);
        Files.setPosixFilePermissions(quarantineBiomes, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE));
        try {
            PackResourceCleanup.RestoreResult result = PackResourceCleanup.restoreLatest(pack);

            assertFalse(result.success());
            assertTrue(result.error().contains("source removal failed"));
            assertFalse(new File(pack, "biomes/a.json").exists());
            assertFalse(new File(pack, "biomes/b.json").exists());
            assertTrue(new File(pack, applied.quarantinePath() + "/biomes/a.json").isFile());
            assertTrue(new File(pack, applied.quarantinePath() + "/biomes/b.json").isFile());
        } finally {
            Files.setPosixFilePermissions(quarantineBiomes, originalPermissions);
        }
    }

    @Test
    public void cleanupFailsClosedWhenManagedContentContainsSymbolicLink() throws Exception {
        File pack = createPack("symlink");
        write(pack, "dimensions/main.json", "{}");
        File outside = temporaryFolder.newFile("outside.json");
        Path link = new File(pack, "biomes/linked.json").toPath();
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, outside.toPath());
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assume.assumeNoException(e);
        }

        PackResourceCleanup.ApplyResult result = PackResourceCleanup.apply(pack);

        assertFalse(result.success());
        assertFalse(result.changed());
        assertTrue(Files.isSymbolicLink(link));
        assertFalse(new File(pack, ".iris-trash").exists());
    }

    @Test
    public void concurrentAppliesSerializePerPack() throws Exception {
        File pack = createPack("concurrent");
        write(pack, "dimensions/main.json", "{}");
        write(pack, "biomes/unused.json", "{}");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PackResourceCleanup.ApplyResult> first = executor.submit(() -> {
                start.await();
                return PackResourceCleanup.apply(pack);
            });
            Future<PackResourceCleanup.ApplyResult> second = executor.submit(() -> {
                start.await();
                return PackResourceCleanup.apply(pack);
            });
            start.countDown();

            List<PackResourceCleanup.ApplyResult> results = List.of(first.get(), second.get());
            assertTrue(results.stream().allMatch(PackResourceCleanup.ApplyResult::success));
            assertEquals(1L, results.stream().filter(PackResourceCleanup.ApplyResult::changed).count());
            assertFalse(new File(pack, "biomes/unused.json").exists());
        } finally {
            executor.shutdownNow();
        }
    }

    private File createPack(String name) throws Exception {
        return temporaryFolder.newFolder(name);
    }

    private void write(File pack, String relativePath, String content) throws Exception {
        Path path = new File(pack, relativePath).toPath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private String read(File pack, String relativePath) throws Exception {
        return Files.readString(new File(pack, relativePath).toPath(), StandardCharsets.UTF_8);
    }
}
