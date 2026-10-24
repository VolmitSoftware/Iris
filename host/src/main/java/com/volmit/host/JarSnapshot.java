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
    private static final String COPY_NAME_SEPARATOR = "-";

    /** A module jar to copy: its origin, which every message names, and the file. */
    record SourceJar(String origin, Path file) {
        SourceJar {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(file, "file");
        }
    }

    /** A module jar: its origin, which every message names, and its copy. */
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

    /** Copies {@code jars} into a new directory below {@code temporaryRoot}, each copy named by its place in the list so
     * jars of the same file name from different places do not collide. Leaves nothing behind if a copy fails.
     */
    static JarSnapshot take(List<SourceJar> jars, Path temporaryRoot) {
        var directory = createOwnedDirectory(temporaryRoot);
        var copies = new ArrayList<CopiedJar>(jars.size());
        try {
            for (int index = 0; index < jars.size(); index++) {
                copies.add(copy(jars.get(index), directory.resolve(index + COPY_NAME_SEPARATOR + jars.get(index).file().getFileName())));
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

    /** The origin of a copy, or {@code null} if {@code location} is not one of them. */
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
            throw new ModuleLoadException(null, null, "Cannot create a directory for module jar copies in " + temporaryRoot,
                    exception);
        }
    }

    /** What the owner file holds: a fixed header, then the id of the process that made the directory. */
    static String ownerRecord(long pid) {
        return OWNER_HEADER + "\n" + pid + "\n";
    }

    private static CopiedJar copy(SourceJar jar, Path copy) {
        try {
            Files.copy(jar.file(), copy);
            return new CopiedJar(jar.origin(), copy);
        } catch (IOException exception) {
            throw new ModuleLoadException(null, jar.origin(), "Cannot copy module jar " + jar.origin(), exception);
        }
    }
}
