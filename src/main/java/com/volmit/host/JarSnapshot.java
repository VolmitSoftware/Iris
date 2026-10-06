package com.volmit.host;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Copies of one pack load's module jars in a fresh temporary directory. The load reads, checks and
 * defines classes from the copies only, so it sees one consistent snapshot even if a jar is rebuilt
 * meanwhile, and the pack's class loader never holds the originals open: on Windows an open jar
 * cannot be replaced, which would stop a module author from rebuilding while studio has the pack
 * loaded.
 *
 * <p>The directory is named {@code iris-pack-modules-*} and holds the copies plus an owner file
 * naming the process that made it. {@link #delete()} removes it when the pack closes or its load
 * fails; {@link StaleJarSnapshots} removes those a crashed process left behind.
 */
final class JarSnapshot {
    static final String DIRECTORY_PREFIX = "iris-pack-modules-";
    static final String OWNER_FILE = "owner.txt";
    static final String OWNER_HEADER = "Iris pack module jar copies";

    /** A module jar: its path relative to the pack, which every message names, and its copy. */
    record CopiedJar(String origin, Path copy) {
        CopiedJar {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(copy, "copy");
        }
    }

    private final Path directory;
    private final List<CopiedJar> jars;

    private JarSnapshot(Path directory, List<CopiedJar> jars) {
        this.directory = directory;
        this.jars = List.copyOf(jars);
    }

    /** Copies {@code jars}, which lie under {@code packRoot}, into a new directory below
     * {@code temporaryRoot}. Leaves nothing behind if a copy fails.
     */
    static JarSnapshot take(Path packRoot, List<Path> jars, Path temporaryRoot) {
        var directory = createOwnedDirectory(temporaryRoot);
        var copies = new ArrayList<CopiedJar>(jars.size());
        try {
            for (var jar : jars) {
                copies.add(copy(packRoot, jar, directory));
            }
            return new JarSnapshot(directory, copies);
        } catch (RuntimeException | Error failure) {
            new JarSnapshot(directory, copies).deleteAfterFailure(failure);
            throw failure;
        }
    }

    List<CopiedJar> jars() {
        return jars;
    }

    /** The pack-relative origin of a copy, or {@code null} if {@code location} is not one of them. */
    String originOf(Path location) {
        return jars.stream()
                .filter(jar -> jar.copy().equals(location))
                .map(CopiedJar::origin)
                .findFirst()
                .orElse(null);
    }

    /** Deletes the copies, the owner file and the directory. Call only once the pack's class loader
     * is closed; on Windows an open copy cannot be deleted.
     */
    void delete() {
        try {
            for (var jar : jars) {
                Files.deleteIfExists(jar.copy());
            }
            Files.deleteIfExists(directory.resolve(OWNER_FILE));
            Files.deleteIfExists(directory);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot delete the module jar copies in " + directory, exception);
        }
    }

    void deleteAfterFailure(Throwable failure) {
        try {
            delete();
        } catch (UncheckedIOException exception) {
            failure.addSuppressed(exception);
        }
    }

    private static Path createOwnedDirectory(Path temporaryRoot) {
        try {
            Files.createDirectories(temporaryRoot);
            var directory = Files.createTempDirectory(temporaryRoot, DIRECTORY_PREFIX);
            Files.writeString(directory.resolve(OWNER_FILE), ownerRecord(ProcessHandle.current().pid()), StandardCharsets.UTF_8);
            return directory;
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot create a directory for module jar copies in " + temporaryRoot, exception);
        }
    }

    /** What the owner file holds: a fixed header, then the id of the process that made the directory. */
    static String ownerRecord(long pid) {
        return OWNER_HEADER + "\n" + pid + "\n";
    }

    private static CopiedJar copy(Path packRoot, Path jar, Path directory) {
        var origin = packRoot.relativize(jar).toString();
        var copy = directory.resolve(jar.getFileName().toString());
        try {
            Files.copy(jar, copy);
            return new CopiedJar(origin, copy);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot copy module jar " + origin, exception);
        }
    }
}
