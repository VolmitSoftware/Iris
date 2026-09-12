package art.arcane.iris.world;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PendingWorldDeleteQueueTest {
    private static final String QUARANTINE_NAME = ".iris-delete-6a4fd7fd-8e75-4f2f-b9fa-523b90c41f45";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsOnlyCanonicalManagedNamesAndStrictQuarantines() {
        assertEquals("alpha", PendingWorldDeleteQueue.normalizeQueueEntry("alpha", "world"));
        assertEquals("alpha", PendingWorldDeleteQueue.normalizeQueueEntry("iris:alpha", "world"));
        assertEquals(QUARANTINE_NAME, PendingWorldDeleteQueue.normalizeQueueEntry(QUARANTINE_NAME, "world"));

        for (String rejected : List.of(
                "world",
                "world_nether",
                "world_the_end",
                "overworld",
                "the_nether",
                "the_end",
                "minecraft:overworld",
                "minecraft:the_nether",
                "minecraft:the_end",
                "Alpha",
                "alpha beta",
                "../alpha",
                "alpha/beta",
                ".iris-delete-6a4fd7fd-8e75-4f2f-b9fa-523b90c41f4",
                ".iris-delete-6A4FD7FD-8E75-4F2F-B9FA-523B90C41F45"
        )) {
            assertNull(rejected, PendingWorldDeleteQueue.normalizeQueueEntry(rejected, "world"));
        }
    }

    @Test
    public void loadFiltersUnsafeEntriesAndCanonicalizesDuplicates() throws IOException {
        File queueFile = temporaryFolder.newFile("pending-world-deletes.txt");
        Files.writeString(
                queueFile.toPath(),
                String.join("\n", "alpha", "iris:alpha", "world", "../escape", QUARANTINE_NAME),
                StandardCharsets.UTF_8
        );

        LinkedHashMap<String, String> queue = PendingWorldDeleteQueue.loadPendingWorldDeleteMap(queueFile, "world");

        assertEquals(List.of("alpha", QUARANTINE_NAME), List.copyOf(queue.values()));
    }

    @Test
    public void queueFileReplacementIsCompleteAndLeavesNoTemporaryFile() throws IOException {
        File queueFile = new File(temporaryFolder.getRoot(), "state/pending-world-deletes.txt");
        LinkedHashMap<String, String> first = new LinkedHashMap<>();
        first.put("alpha", "alpha");
        first.put(QUARANTINE_NAME, QUARANTINE_NAME);
        PendingWorldDeleteQueue.writePendingWorldDeleteMap(queueFile, first);

        assertEquals("alpha\n" + QUARANTINE_NAME + "\n", Files.readString(queueFile.toPath()));

        LinkedHashMap<String, String> replacement = new LinkedHashMap<>();
        replacement.put("beta", "beta");
        PendingWorldDeleteQueue.writePendingWorldDeleteMap(queueFile, replacement);

        assertEquals("beta\n", Files.readString(queueFile.toPath()));
        try (Stream<Path> files = Files.list(queueFile.toPath().getParent())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().contains(".tmp-")));
        }
    }

    @Test
    public void emptyQueueIsDurablyRepresentedByAnEmptyFile() throws IOException {
        File queueFile = new File(temporaryFolder.getRoot(), "pending-world-deletes.txt");

        PendingWorldDeleteQueue.writePendingWorldDeleteMap(queueFile, new LinkedHashMap<>());

        assertTrue(queueFile.isFile());
        assertEquals(0L, queueFile.length());
    }

    @Test
    public void writeFailuresPropagateToTheCaller() throws IOException {
        File parentFile = temporaryFolder.newFile("not-a-directory");
        File queueFile = new File(parentFile, "pending-world-deletes.txt");

        assertThrows(
                IOException.class,
                () -> PendingWorldDeleteQueue.writePendingWorldDeleteMap(queueFile, new LinkedHashMap<>())
        );
    }

    @Test
    public void discoversOnlyDirectNonSymlinkStartupDirectories() throws IOException {
        File levelRoot = temporaryFolder.newFolder("world");
        Path irisNamespace = levelRoot.toPath().resolve("dimensions/iris");
        Files.createDirectories(irisNamespace);
        Files.createDirectory(irisNamespace.resolve(QUARANTINE_NAME));
        Files.createDirectory(irisNamespace.resolve(".iris-delete-not-a-uuid"));
        String transientName = "iris-45ba411e-bf7c-493a-bf41-aa020754990b";
        Files.createDirectory(irisNamespace.resolve(transientName + "_nether"));
        Path nested = irisNamespace.resolve("ordinary/nested");
        Files.createDirectories(nested);
        Files.createDirectory(nested.resolve(".iris-delete-f70b8c21-9174-43a2-b7b7-a84fc0b2fe4a"));
        Path symlinkTarget = temporaryFolder.newFolder("quarantine-target").toPath();
        Files.createSymbolicLink(
                irisNamespace.resolve(".iris-delete-b8c7ff2d-2efd-410d-b228-6da5d5a46c36"),
                symlinkTarget
        );

        LinkedHashSet<String> discovered = PendingWorldDeleteQueue.discoverStartupWorldNames(levelRoot);

        assertEquals(Set.of(QUARANTINE_NAME, transientName), discovered);
    }

    @Test
    public void refusesSymlinkedIrisNamespace() throws IOException {
        File levelRoot = temporaryFolder.newFolder("world");
        Path dimensions = levelRoot.toPath().resolve("dimensions");
        Files.createDirectories(dimensions);
        Path external = temporaryFolder.newFolder("external-iris").toPath();
        Files.createSymbolicLink(dimensions.resolve("iris"), external);

        assertThrows(
                IOException.class,
                () -> PendingWorldDeleteQueue.discoverStartupWorldNames(levelRoot)
        );
    }

    @Test
    public void quarantineEntriesResolveToOnlyTheirExactDirectory() throws IOException {
        File levelRoot = temporaryFolder.newFolder("world");

        List<Path> paths = PendingWorldDeleteQueue.resolveQueueEntryPaths(levelRoot, QUARANTINE_NAME);

        assertEquals(List.of(
                levelRoot.toPath().resolve("dimensions/iris").resolve(QUARANTINE_NAME).toAbsolutePath()
        ), paths);
    }

    @Test
    public void exactLogicalEntriesDoNotExpandIntoDimensionFamilies() throws IOException {
        File levelRoot = temporaryFolder.newFolder("world");

        List<Path> exact = PendingWorldDeleteQueue.resolveQueueEntryPaths(levelRoot, "exact:alpha");
        List<Path> family = PendingWorldDeleteQueue.resolveQueueEntryPaths(levelRoot, "alpha");

        assertEquals(List.of(
                levelRoot.toPath().resolve("dimensions/iris/alpha").toAbsolutePath()
        ), exact);
        assertEquals(List.of(
                levelRoot.toPath().resolve("dimensions/iris/alpha").toAbsolutePath(),
                levelRoot.toPath().resolve("dimensions/iris/alpha_nether").toAbsolutePath(),
                levelRoot.toPath().resolve("dimensions/iris/alpha_the_end").toAbsolutePath()
        ), family);
    }

    @Test
    public void exactLogicalEntryResolvesCurrentCraftBukkitLevelStorage() throws IOException {
        Path worldContainer = temporaryFolder.newFolder("configured-delete-server").toPath();
        File levelRoot = Files.createDirectory(worldContainer.resolve("world")).toFile();
        Path configuredLevelRoot = worldContainer.resolve("world_iris_alpha");
        Files.createDirectories(configuredLevelRoot.resolve("dimensions/iris/alpha"));

        assertEquals(
                List.of(configuredLevelRoot.toAbsolutePath().normalize()),
                PendingWorldDeleteQueue.resolveQueueEntryPaths(levelRoot, "exact:alpha")
        );
    }

    @Test
    public void failedSafeDeletionSignalsQueueRetentionAndSucceedsOnRetry() throws IOException {
        File levelRoot = temporaryFolder.newFolder("retry-world");
        Path quarantine = levelRoot.toPath().resolve("dimensions/iris").resolve(QUARANTINE_NAME);
        Files.createDirectories(quarantine);
        Path external = temporaryFolder.newFolder("retry-external").toPath();
        Path link = Files.createSymbolicLink(quarantine.resolve("unsafe-link"), external);

        PendingWorldDeleteQueue.EntryDeletionResult first = PendingWorldDeleteQueue.attemptEntry(
                levelRoot,
                QUARANTINE_NAME,
                (key, path) -> false
        );

        assertTrue(first.retainQueueEntry());
        assertEquals(1, first.failures().size());
        assertTrue(Files.isSymbolicLink(link));
        assertTrue(Files.exists(external));

        Files.delete(link);
        Files.writeString(quarantine.resolve("safe.dat"), "safe");

        PendingWorldDeleteQueue.EntryDeletionResult retry = PendingWorldDeleteQueue.attemptEntry(
                levelRoot,
                QUARANTINE_NAME,
                (key, path) -> false
        );

        assertFalse(retry.retainQueueEntry());
        assertTrue(retry.failures().isEmpty());
        assertFalse(Files.exists(quarantine));
    }
}
