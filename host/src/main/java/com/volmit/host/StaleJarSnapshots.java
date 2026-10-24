package com.volmit.host;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Removes module jar copies that a process left behind when it crashed before closing its packs.
 *
 * <p>Only directories this code provably created are touched: a directory named
 * {@code iris-pack-modules-*}, holding an owner file with the expected header, whose owner process
 * no longer runs, and holding nothing but that owner file and {@code .jar} files. Anything else is
 * left alone, including copies of a process that still runs (another server, or this one) and
 * directories whose owner file is missing because the crash came before it was written.
 */
final class StaleJarSnapshots {
    private static final System.Logger LOGGER = System.getLogger(PackModules.class.getName());

    private StaleJarSnapshots() {}

    static void remove(Path temporaryRoot) {
        for (var candidate : candidates(temporaryRoot)) {
            ownerOf(candidate)
                    .filter(StaleJarSnapshots::hasExited)
                    .ifPresent(owner -> removeIfOnlyCopies(candidate, owner));
        }
    }

    private static List<Path> candidates(Path temporaryRoot) {
        var candidates = new ArrayList<Path>();
        try (var entries = Files.newDirectoryStream(temporaryRoot, JarSnapshot.DIRECTORY_PREFIX + "*")) {
            entries.forEach(candidates::add);
        } catch (IOException | DirectoryIteratorException exception) {
            LOGGER.log(Level.DEBUG, "Cannot look for stale module jar copies in " + temporaryRoot, exception);
        }
        return candidates;
    }

    /** The owner process id, if {@code directory} is a real directory with a valid owner file. */
    private static Optional<Long> ownerOf(Path directory) {
        var ownerFile = directory.resolve(JarSnapshot.OWNER_FILE);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        if (!Files.isRegularFile(ownerFile, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        try {
            var lines = Files.readAllLines(ownerFile, StandardCharsets.UTF_8);
            if (lines.size() != 2 || !lines.getFirst().equals(JarSnapshot.OWNER_HEADER)) return Optional.empty();
            return parsePid(lines.get(1));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Long> parsePid(String text) {
        try {
            return Optional.of(Long.parseLong(text));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static boolean hasExited(long pid) {
        return ProcessHandle.of(pid).map(process -> !process.isAlive()).orElse(true);
    }

    private static void removeIfOnlyCopies(Path directory, long owner) {
        try {
            var entries = listEntries(directory);
            if (!entries.stream().allMatch(StaleJarSnapshots::isSnapshotFile)) return;
            for (var entry : entries) {
                Files.delete(entry);
            }
            Files.delete(directory);
            LOGGER.log(Level.INFO, () -> "Removed module jar copies left by exited process " + owner + " in " + directory);
        } catch (IOException exception) {
            LOGGER.log(Level.DEBUG, "Cannot remove stale module jar copies in " + directory, exception);
        }
    }

    private static List<Path> listEntries(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.toList();
        }
    }

    private static boolean isSnapshotFile(Path entry) {
        var name = entry.getFileName().toString();
        return Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
                && (name.equals(JarSnapshot.OWNER_FILE) || name.endsWith(PackModules.JAR_EXTENSION));
    }
}
