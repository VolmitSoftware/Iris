package art.arcane.iris.world.lifecycle;

import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.iris.world.WorldSlotKey;
import art.arcane.iris.generation.runtime.IrisEngineMantle;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Moves a hot-deleted Iris world's husk out of the dimensions tree before any level is created.
 * <p>
 * Deleting a loaded world's folder and letting the server save the level back leaves a directory holding
 * nothing but the server's own {@code data/} skeleton: no frozen pack snapshot, no regions, no mantle. The
 * server still enumerates it, and both outcomes stop startup - Iris refuses to boot over a directory it
 * cannot generate, and excluding it from the dimension registry instead trips Paper's interactive
 * world-migration gate, which consumes no console input and hangs the boot forever.
 * <p>
 * The husk is moved, never deleted: it is recoverable, and the move alone is enough to let the server start.
 * The test is deliberately narrow. Anything with chunk data, an {@code iris/} directory holding anything of
 * Iris' own, a symbolic link anywhere on the path, or a directory that cannot be read is left exactly where it
 * is, for {@link BukkitWorldConfiguration#auditIrisWorldStorage} to fail closed on.
 */
public final class HuskWorldQuarantine {
    private static final String IRIS_NAMESPACE = "iris";
    private static final Pattern MANAGED_KEY = Pattern.compile("[a-z0-9_-]+");
    private static final String[] CHUNK_DATA_DIRECTORIES = {
            "region", "entities", "poi", IrisEngineMantle.STORAGE_FOLDER_NAME
    };
    private static final Set<String> OS_METADATA_FILES = Set.of(".DS_Store", "Thumbs.db", "desktop.ini");
    private static final String APPLE_DOUBLE_PREFIX = "._";
    private static final DateTimeFormatter STAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss")
            .withZone(ZoneOffset.UTC);
    private static final int MAX_DESTINATION_ATTEMPTS = 64;

    private HuskWorldQuarantine() {
    }

    public static List<Quarantine> quarantineWorthlessHusks(Path levelRoot, Consumer<String> warn) {
        return quarantineWorthlessHusks(levelRoot, warn, Instant.now());
    }

    static List<Quarantine> quarantineWorthlessHusks(Path levelRoot, Consumer<String> warn, Instant at) {
        Path root = Objects.requireNonNull(levelRoot, "levelRoot").toAbsolutePath().normalize();
        Consumer<String> log = Objects.requireNonNull(warn, "warn");
        Instant stamp = Objects.requireNonNull(at, "at");
        String levelName = root.getFileName() == null ? null : root.getFileName().toString();
        if (levelName == null || levelName.isBlank()) {
            return List.of();
        }
        Path namespace = root.resolve("dimensions").resolve(IRIS_NAMESPACE);
        if (Files.isSymbolicLink(namespace) || !Files.isDirectory(namespace, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }

        List<Path> candidates;
        try (Stream<Path> entries = Files.list(namespace)) {
            candidates = entries.sorted().toList();
        } catch (IOException unreadable) {
            // An unreadable namespace is not proof that there is nothing in it to lose.
            return List.of();
        }

        List<Quarantine> quarantined = new ArrayList<>();
        for (Path candidate : candidates) {
            String key = candidate.getFileName().toString();
            if (!MANAGED_KEY.matcher(key).matches() || !isWorthlessHusk(candidate)) {
                continue;
            }
            String worldName;
            try {
                worldName = IrisWorldStorage.configuredWorldName(
                        new WorldSlotKey(IRIS_NAMESPACE, key),
                        levelName);
            } catch (RuntimeException notAManagedWorld) {
                continue;
            }
            Path destination;
            try {
                destination = move(root, key, candidate, stamp);
            } catch (IOException failure) {
                log.accept("Iris husk world " + worldName + " at " + candidate
                        + " could not be moved aside (" + describe(failure)
                        + "); startup will stop until it is moved or deleted by hand.");
                continue;
            }
            quarantined.add(new Quarantine(key, candidate, destination));
            log.accept("Iris husk world " + worldName + ": no pack snapshot and no world data; moved "
                    + candidate + " -> " + destination
                    + ". Drop the entry with /iris remove world=" + worldName + " delete=true.");
        }
        return List.copyOf(quarantined);
    }

    /**
     * True when nothing in this directory can be lost: no frozen pack snapshot, nothing of Iris' own under
     * {@code iris/}, and no stored chunk data. Every uncertain answer - a symbolic link, an unreadable
     * directory, an entry that is not the type it should be - is false, because uncertainty is not proof of
     * worthlessness.
     */
    private static boolean isWorthlessHusk(Path dimensionRoot) {
        if (!isReadableDirectory(dimensionRoot)) {
            return false;
        }
        if (holdsIrisContent(dimensionRoot.resolve("iris"))) {
            return false;
        }
        for (String directory : CHUNK_DATA_DIRECTORIES) {
            if (hasEntries(dimensionRoot.resolve(directory))) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when the {@code iris/} entry holds anything of Iris' own.
     * <p>
     * The desktop file managers recreate a directory to hold their own metadata while a delete is still in
     * flight - on macOS a world folder can come back holding nothing but {@code iris/.DS_Store} - and a folder
     * that holds only that is exactly as worthless as one with no {@code iris/} entry at all. Only files are
     * ever treated as metadata: a directory, a symbolic link, an unreadable entry and anything that is not on
     * the list all count as content.
     */
    private static boolean holdsIrisContent(Path irisRoot) {
        if (Files.isSymbolicLink(irisRoot)) {
            return true;
        }
        if (!Files.exists(irisRoot, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!Files.isDirectory(irisRoot, LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        try (Stream<Path> entries = Files.list(irisRoot)) {
            return entries.anyMatch(entry -> !isOperatingSystemMetadata(entry));
        } catch (IOException unreadable) {
            return true;
        }
    }

    private static boolean isOperatingSystemMetadata(Path entry) {
        if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        Path name = entry.getFileName();
        if (name == null) {
            return false;
        }
        String fileName = name.toString();
        return OS_METADATA_FILES.contains(fileName) || fileName.startsWith(APPLE_DOUBLE_PREFIX);
    }

    /**
     * True when the path is a real directory that can actually be listed. The permission bits alone are not
     * the answer: what matters is whether Iris can see everything in it before deciding it holds nothing.
     */
    private static boolean isReadableDirectory(Path directory) {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            entries.findAny();
            return true;
        } catch (IOException unreadable) {
            return false;
        }
    }

    private static boolean hasEntries(Path directory) {
        if (Files.isSymbolicLink(directory)) {
            return true;
        }
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isPresent();
        } catch (IOException unreadable) {
            return true;
        }
    }

    private static Path move(Path levelRoot, String key, Path husk, Instant at) throws IOException {
        Path huskRoot = requireDirectoryPath(levelRoot, levelRoot.resolve(IRIS_NAMESPACE).resolve("husks"));
        String base = key + "-" + STAMP.format(at);
        for (int attempt = 1; attempt <= MAX_DESTINATION_ATTEMPTS; attempt++) {
            Path destination = huskRoot.resolve(attempt == 1 ? base : base + "-" + attempt);
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try {
                Files.move(husk, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(husk, destination);
            }
            DirectoryDurability.forceDirectoryAfterCommit(huskRoot, "An Iris husk world quarantine");
            return destination;
        }
        throw new IOException("No free husk destination under " + huskRoot + " for " + key);
    }

    /**
     * Creates the quarantine directory, refusing when any segment below the level root is a symbolic link so
     * a hostile or accidental link cannot redirect a world folder out of the level root. Segments above the
     * level root are the server's own installation path and are not Iris' to judge.
     */
    private static Path requireDirectoryPath(Path levelRoot, Path directory) throws IOException {
        Path current = levelRoot;
        for (Path segment : levelRoot.relativize(directory)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Husk quarantine path contains a symbolic link: " + current);
            }
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Husk quarantine path is not a directory: " + current);
            }
        }
        Files.createDirectories(directory);
        return directory;
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    public record Quarantine(String worldKey, Path husk, Path destination) {
        public Quarantine {
            Objects.requireNonNull(worldKey, "worldKey");
            Objects.requireNonNull(husk, "husk");
            Objects.requireNonNull(destination, "destination");
        }
    }
}
